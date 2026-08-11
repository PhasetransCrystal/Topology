package net.ptcrys.topo.api.pipe;

import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.ui.PipePortConfigUi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Pure-Block pipe. The six tri-state visual properties only drive rendering and shape; gameplay
 * truth (player intent, port roles, network graph) lives in the per-dimension pipe saved data and
 * is maintained server-side through the hooks below. All pipes share one static 729-entry shape
 * cache.
 */
public class PipeBlock extends Block implements BlockUIMenuType.BlockUI {

    public static final EnumProperty<PipeSideVisual> DOWN = EnumProperty.create("down", PipeSideVisual.class);
    public static final EnumProperty<PipeSideVisual> UP = EnumProperty.create("up", PipeSideVisual.class);
    public static final EnumProperty<PipeSideVisual> NORTH = EnumProperty.create("north", PipeSideVisual.class);
    public static final EnumProperty<PipeSideVisual> SOUTH = EnumProperty.create("south", PipeSideVisual.class);
    public static final EnumProperty<PipeSideVisual> WEST = EnumProperty.create("west", PipeSideVisual.class);
    public static final EnumProperty<PipeSideVisual> EAST = EnumProperty.create("east", PipeSideVisual.class);

    /**
     * Pipe interaction accepts anything tagged {@code c:tools/wrench} (OI wrenches carry it; other
     * mods' wrenches work out of the box). The OI wrench item itself opts into sneak-bypass so the
     * shift-click side cycling below stays reachable; foreign tag-only wrenches keep the vanilla
     * sneak behavior and therefore only support the non-sneak interactions.
     */
    private static final TagKey<Item> WRENCH_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/wrench"));

    /** Indexed by {@link Direction#ordinal()}: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    private static final EnumProperty<PipeSideVisual>[] BY_DIRECTION = buildPropertyTable();
    private static final int[] POW3 = { 1, 3, 9, 27, 81, 243 };

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARMS = buildArmShapes();
    private static final VoxelShape[] EXTRACT_ARMS = buildExtractShapes();
    private static final AABB[] ARM_BOUNDS = buildBounds(ARMS);
    private static final AABB[] EXTRACT_ARM_BOUNDS = buildBounds(EXTRACT_ARMS);
    /** Lazily filled; element writes are idempotent so the benign race needs no lock. */
    private static final VoxelShape[] SHAPES = new VoxelShape[729];

    private final PipeDefinition definition;

    public PipeBlock(Properties properties, PipeDefinition definition) {
        super(properties);
        this.definition = Objects.requireNonNull(definition, "pipe definition");
        BlockState state = stateDefinition.any();
        for (EnumProperty<PipeSideVisual> property : BY_DIRECTION) {
            state = state.setValue(property, PipeSideVisual.NONE);
        }
        registerDefaultState(state);
    }

    public PipeDefinition definition() {
        return definition;
    }

    public static EnumProperty<PipeSideVisual> property(Direction direction) {
        return BY_DIRECTION[direction.ordinal()];
    }

    public static PipeSideVisual visual(BlockState state, Direction direction) {
        return state.getValue(BY_DIRECTION[direction.ordinal()]);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DOWN, UP, NORTH, SOUTH, WEST, EAST);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (oldState.is(this)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            PipeNetworkEngine.onPipePlaced(serverLevel, pos, definition);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        PipeNetworkEngine.onPipeRemoved(level, pos);
        level.invalidateCapabilities(pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected void neighborChanged(
                                   BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            PipeNetworkEngine.onNeighborChanged(serverLevel, pos);
        }
    }

    @Override
    protected InteractionResult useItemOn(
                                          ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !stack.is(WRENCH_TAG)) {
            return InteractionResult.PASS;
        }
        Direction side = targetSide(state, pos, hit);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel serverLevel && player.mayBuild()) {
                PipeNetworkEngine.cycleSideIntent(serverLevel, pos, side, player);
                // 扳手语义:每次成功的意图调整消耗 1 耐久;打开 UI 与 UI 内编辑不耗。
                stack.hurtAndBreak(1, player, hand);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        if (visual(state, side) == PipeSideVisual.EXTRACT) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel serverLevel && player.mayBuild() && PipeNetworkEngine.openPortConfig(serverLevel, pos, side, player)) {
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return PipePortConfigUi.build(definition, holder);
    }

    /** The side being configured: a connected arm the crosshair touches, else the clicked face. */
    private static Direction targetSide(BlockState state, BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (Direction direction : Direction.values()) {
            PipeSideVisual sideVisual = visual(state, direction);
            AABB bounds = sideVisual == PipeSideVisual.EXTRACT ? EXTRACT_ARM_BOUNDS[direction.ordinal()] : ARM_BOUNDS[direction.ordinal()];
            if (sideVisual != PipeSideVisual.NONE && bounds.contains(local)) {
                return direction;
            }
        }
        return hit.getDirection();
    }

    private static VoxelShape shapeFor(BlockState state) {
        int index = 0;
        for (int i = 0; i < 6; i++) {
            index += state.getValue(BY_DIRECTION[i]).ordinal() * POW3[i];
        }
        VoxelShape shape = SHAPES[index];
        if (shape == null) {
            shape = composeShape(index);
            SHAPES[index] = shape;
        }
        return shape;
    }

    private static VoxelShape composeShape(int index) {
        int[] visuals = new int[6];
        int remaining = index;
        for (int i = 0; i < 6; i++) {
            visuals[i] = remaining % 3;
            remaining /= 3;
        }
        VoxelShape shape = CORE;
        for (int i = 0; i < 6; i++) {
            int visual = visuals[i];
            if (visual == PipeSideVisual.PIPE.ordinal()) {
                shape = Shapes.or(shape, ARMS[i]);
            } else if (visual == PipeSideVisual.EXTRACT.ordinal()) {
                shape = Shapes.or(shape, EXTRACT_ARMS[i]);
            }
        }
        return shape.optimize();
    }

    @SuppressWarnings("unchecked")
    private static EnumProperty<PipeSideVisual>[] buildPropertyTable() {
        EnumProperty<PipeSideVisual>[] table = new EnumProperty[6];
        table[Direction.DOWN.ordinal()] = DOWN;
        table[Direction.UP.ordinal()] = UP;
        table[Direction.NORTH.ordinal()] = NORTH;
        table[Direction.SOUTH.ordinal()] = SOUTH;
        table[Direction.WEST.ordinal()] = WEST;
        table[Direction.EAST.ordinal()] = EAST;
        return table;
    }

    private static VoxelShape[] buildArmShapes() {
        VoxelShape[] arms = new VoxelShape[6];
        arms[Direction.DOWN.ordinal()] = Block.box(5, 0, 5, 11, 5, 11);
        arms[Direction.UP.ordinal()] = Block.box(5, 11, 5, 11, 16, 11);
        arms[Direction.NORTH.ordinal()] = Block.box(5, 5, 0, 11, 11, 5);
        arms[Direction.SOUTH.ordinal()] = Block.box(5, 5, 11, 11, 11, 16);
        arms[Direction.WEST.ordinal()] = Block.box(0, 5, 5, 5, 11, 11);
        arms[Direction.EAST.ordinal()] = Block.box(11, 5, 5, 16, 11, 11);
        return arms;
    }

    private static VoxelShape[] buildExtractShapes() {
        VoxelShape[] extracts = buildArmShapes();
        extracts[Direction.DOWN.ordinal()] = Shapes.or(
                extracts[Direction.DOWN.ordinal()], Block.box(4, 0, 4, 12, 2, 12));
        extracts[Direction.UP.ordinal()] = Shapes.or(
                extracts[Direction.UP.ordinal()], Block.box(4, 14, 4, 12, 16, 12));
        extracts[Direction.NORTH.ordinal()] = Shapes.or(
                extracts[Direction.NORTH.ordinal()], Block.box(4, 4, 0, 12, 12, 2));
        extracts[Direction.SOUTH.ordinal()] = Shapes.or(
                extracts[Direction.SOUTH.ordinal()], Block.box(4, 4, 14, 12, 12, 16));
        extracts[Direction.WEST.ordinal()] = Shapes.or(
                extracts[Direction.WEST.ordinal()], Block.box(0, 4, 4, 2, 12, 12));
        extracts[Direction.EAST.ordinal()] = Shapes.or(
                extracts[Direction.EAST.ordinal()], Block.box(14, 4, 4, 16, 12, 12));
        return extracts;
    }

    private static AABB[] buildBounds(VoxelShape[] shapes) {
        AABB[] bounds = new AABB[6];
        for (int i = 0; i < 6; i++) {
            bounds[i] = shapes[i].bounds().inflate(0.001);
        }
        return bounds;
    }
}
