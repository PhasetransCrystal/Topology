package net.ptcrys.topo.api.machine;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.machine.ui.ComponentCollector;
import net.ptcrys.topo.api.machine.ui.MachineUiFrameTemplate;
import net.ptcrys.topo.api.machine.ui.PageCollector;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * Block side of a registered machine. Stores only the machine id; all runtime behaviour comes from
 * the {@link MachineDefinition} resolved from that id. UI is assembled from mounted traits through
 * LDLib2.
 */
public class MachineBlock extends BaseEntityBlock implements BlockUIMenuType.BlockUI {

    public static final MapCodec<MachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Identifier.CODEC.fieldOf("machine").forGetter(block -> block.machineId)).apply(instance, MachineBlock::new));

    /**
     * Machines are the wrench's mining specialty: every machine block joins this tag alongside the
     * vanilla pickaxe mineable tag, so the wrench (whose tool rules target this tag with a speed
     * multiplier) out-mines the pickaxe fallback.
     */
    public static final TagKey<Block> MINEABLE_WITH_WRENCH = TagKey.create(
            Registries.BLOCK,
            OfficialTopoAPIPlugin.INSTANCE.machine().id("mineable_with_wrench"));

    private final Identifier machineId;
    private volatile @Nullable MachineDefinition definition;

    public MachineBlock(Properties properties, Identifier machineId) {
        super(properties);
        this.machineId = Objects.requireNonNull(machineId, "machine id");
    }

    public final Identifier machineId() {
        return machineId;
    }

    /**
     * Block-state properties that reflect machine runtime status (visual/working state), not block
     * identity. Multiblock change watching discards same-block state changes whose property delta
     * stays inside this set, so toggling them never schedules a structure recheck. Default: none.
     */
    public Set<Property<?>> runtimeStateProperties() {
        return Set.of();
    }

    /** The resolved definition for this block, cached after the first lookup. */
    public final MachineDefinition definition() {
        MachineDefinition resolved = definition;
        if (resolved == null) {
            resolved = Machines.require(machineId);
            definition = resolved;
        }
        return resolved;
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public MachineBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return Machines.createBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(
                                          net.minecraft.world.item.ItemStack stack,
                                          BlockState state,
                                          Level level,
                                          BlockPos pos,
                                          Player player,
                                          net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hitResult) {
        MachineItemBehavior behavior = MachineItemBehaviors.find(stack.getItem());
        if (behavior == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        InteractionResult result = behavior.useOnMachine(machine, player, stack, hand, hitResult);
        // PASS = the behavior declined; fall through to the machine's default interaction (UI).
        return result == InteractionResult.PASS ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
    }

    @Override
    protected InteractionResult useWithoutItem(
                                               BlockState state,
                                               Level level,
                                               BlockPos pos,
                                               Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockUIMenuType.openUI(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        BlockEntity blockEntity = holder.player.level().getBlockEntity(holder.pos);
        if (!(blockEntity instanceof MachineBlockEntity machine)) {
            throw new IllegalStateException("Machine UI opened for non-machine block entity at " + holder.pos);
        }

        PageCollector pages = new PageCollector();
        ComponentCollector components = new ComponentCollector();
        machine.collectMachineUi(pages, components);

        return ModularUI.of(
                UI.of(
                        MachineUiFrameTemplate.create(
                                machine.getBlockState().getBlock().getName(),
                                pages.entries(),
                                components.entries()),
                        StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)),
                holder.player);
    }
}
