package net.ptcrys.topo.api.machine.multiblock.ui;

import net.ptcrys.topo.api.api.visual.ConnectedTextureProperties;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.Cell;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 蓝图的标准搭建示例，由 JEI 多方块分类和控制器结构页共用。每个位置包含代表状态和可放置候选项。
 * 预览以控制器本体正向为坐标基准，先将声明坐标应用 frame 变换：优先固定状态，其次按数量下限补充角色部件，最后选择谓词首个候选方块。
 * 控制器朝向 SOUTH，遵循场景相机约定。此类属于展示层，只能在注册与物品组件绑定完成后生成物品栈候选；
 * 谓词和诊断本身只保存候选方块声明，不承担物品栈创建。
 */
public final class BlueprintPreview {

    /** 一个预览位置的控制器局部坐标、代表状态与可放置候选项。 */
    public record PreviewCell(
                              BlockPos localPos,
                              BlockState previewState,
                              List<Candidate> candidates) {

        public PreviewCell {
            Objects.requireNonNull(localPos, "preview cell pos");
            Objects.requireNonNull(previewState, "preview cell state");
            candidates = List.copyOf(candidates);
        }
    }

    /** 可放置候选。role 为空表示普通结构方块；否则 min/max 为蓝图范围内该角色的数量限制，无限制时为 0/Integer.MAX_VALUE。 */
    public record Candidate(ItemStack item, @Nullable PartRole role, int min, int max) {

        public Candidate {
            Objects.requireNonNull(item, "candidate item");
        }

        public boolean hasRole() {
            return role != null;
        }
    }

    private final List<PreviewCell> cells;
    private final Map<BlockPos, BlockState> blocks;
    private final Map<BlockPos, List<Candidate>> candidatesByCell;

    private BlueprintPreview(List<PreviewCell> cells) {
        this.cells = List.copyOf(cells);
        Map<BlockPos, BlockState> blockMap = new LinkedHashMap<>();
        Map<BlockPos, List<Candidate>> candidateMap = new LinkedHashMap<>();
        for (PreviewCell cell : this.cells) {
            blockMap.put(cell.localPos(), cell.previewState());
            candidateMap.put(cell.localPos(), cell.candidates());
        }
        this.blocks = Map.copyOf(blockMap);
        this.candidatesByCell = Map.copyOf(candidateMap);
    }

    public List<PreviewCell> cells() {
        return cells;
    }

    /** 包含控制器的规范局部位置到代表状态映射，返回不可变视图。 */
    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public Map<BlockPos, List<Candidate>> candidatesByCell() {
        return candidatesByCell;
    }

    /** 合并本体与叠加模块的展示位置。共享锚点与重叠位置只计一次，按声明顺序保留首个代表状态。 */
    public static BlueprintPreview combine(List<BlueprintPreview> previews) {
        Map<BlockPos, PreviewCell> merged = new LinkedHashMap<>();
        for (BlueprintPreview preview : previews) for (PreviewCell cell : preview.cells()) merged.putIfAbsent(cell.localPos(), cell);
        return new BlueprintPreview(new ArrayList<>(merged.values()));
    }

    /** 使用默认预览重复次数生成标准搭建示例，控制器朝向 SOUTH。 */
    public static BlueprintPreview of(Blueprint blueprint, MachineDefinition controller) {
        return of(blueprint, controller, previewRepeats(blueprint));
    }

    /** 按指定重复次数生成搭建示例，调用方须保证展示所需的物品组件已经绑定。 */
    public static BlueprintPreview of(Blueprint blueprint, MachineDefinition controller, RepeatResolution resolution) {
        return of(blueprint, controller, resolution, false);
    }

    /** 镜像属于整个结构，控制器始终朝 SOUTH；只变换结构坐标和状态，不镜像控制器本体。 */
    public static BlueprintPreview of(Blueprint blueprint, MachineDefinition controller, RepeatResolution resolution, boolean mirrored) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(controller, "controller definition");
        Objects.requireNonNull(resolution, "repeat resolution");

        Map<PartRole, Block> partBlocks = partBlocksByCapability();
        Map<PartRole, Blueprint.CountRequirement> requirements = blueprint.countRequirements();
        Map<PartRole, Integer> remaining = new LinkedHashMap<>();
        for (Blueprint.CountRequirement requirement : requirements.values()) {
            if (requirement.min() > 0) {
                remaining.put(requirement.role(), requirement.min());
            }
        }

        List<PreviewCell> cells = new ArrayList<>();
        for (Cell cell : blueprint.cells(resolution)) {
            if (cell.localPos().equals(BlockPos.ZERO)) {
                BlockState state = controllerPreviewState(controller);
                cells.add(new PreviewCell(
                        cell.localPos(),
                        state,
                        List.of(new Candidate(new ItemStack(state.getBlock()), null, 0, Integer.MAX_VALUE))));
                continue;
            }
            BlockState state = representativeState(cell, partBlocks, remaining);
            if (state == null) {
                continue;
            }
            cells.add(new PreviewCell(previewPosition(blueprint, cell.localPos(), mirrored), previewState(blueprint, cell, state, mirrored), candidatesOf(cell, requirements)));
        }
        return new BlueprintPreview(cells);
    }

    /** 声明坐标到控制器本体正向坐标的纯变换。 */
    public static Orientation previewTransform(Blueprint blueprint, boolean mirrored) {
        return (mirrored ? Orientations.IDENTITY.mirrored() : Orientations.IDENTITY).compose(blueprint.frame());
    }

    public static BlockPos previewPosition(Blueprint blueprint, BlockPos declared, boolean mirrored) {
        return previewTransform(blueprint, mirrored).apply(declared, BlockPos.ZERO);
    }

    /** 固定状态使用与引擎相同的属性规则，普通示例候选仅作显示方向变换。 */
    public static BlockState previewState(Blueprint blueprint, Cell cell, BlockState state, boolean mirrored) {
        if (cell.localPos().equals(BlockPos.ZERO)) return state;
        Orientation machine = mirrored ? Orientations.IDENTITY.mirrored() : Orientations.IDENTITY;
        if (cell.expectedState() != null && state.getBlock() == cell.expectedState().getBlock()) {
            // 重复副本可能不在最小布局内；规则只依赖状态及变换，与副本坐标无关。
            BlockState transformed = state;
            for (var rule : cell.rules()) if (rule.classification() == net.ptcrys.topo.api.machine.multiblock.pattern.PropertyRule.Classification.COVARIANT) transformed = rule.apply(blueprint.effectiveOrientation(machine), transformed);
            return transformed;
        }
        return Orientations.rotated(previewTransform(blueprint, mirrored), state);
    }

    /** 预览采用各重复段的最小副本数，但可选段至少显示一份，便于展示可选位置。 */
    public static RepeatResolution previewRepeats(Blueprint blueprint) {
        int[] counts = new int[blueprint.sectionCount()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = Math.min(blueprint.section(i).maxCount(), Math.max(1, blueprint.section(i).minCount()));
        }
        return new RepeatResolution(counts);
    }

    /** 控制器按相机约定朝 SOUTH，成型外观沿用方块默认状态。 */
    public static BlockState controllerPreviewState(MachineDefinition controller) {
        return controllerPreviewStateFrom(controller.registeredBlock().getDefaultState());
    }

    public static BlockState controllerPreviewStateFrom(BlockState state) {
        if (state.hasProperty(OrientedMachineBlock.FACING)) {
            state = state.setValue(OrientedMachineBlock.FACING, Direction.SOUTH);
        }
        return state;
    }

    private static @Nullable BlockState representativeState(
                                                            Cell cell,
                                                            Map<PartRole, Block> partBlocks,
                                                            Map<PartRole, Integer> remaining) {
        // 优先补齐角色数量下限，使搭建示例满足自身蓝图。
        for (PartRole capability : cell.binding().partRoles()) {
            Integer left = remaining.get(capability);
            if (left == null || left <= 0) {
                continue;
            }
            Block block = partBlocks.get(capability);
            if (block == null) {
                continue;
            }
            remaining.put(capability, left - 1);
            BlockState state = block.defaultBlockState();
            // 示例展示成型外观，参与贴图连接的部件开启 CTM。
            if (state.hasProperty(ConnectedTextureProperties.CTM_ACTIVE)) {
                state = state.setValue(ConnectedTextureProperties.CTM_ACTIVE, true);
            }
            return state;
        }
        BlockState pinned = cell.expectedState();
        if (pinned != null) {
            return pinned;
        }
        List<Block> candidates = cell.binding().candidateBlocks();
        return candidates.isEmpty() ? null : candidates.getFirst().defaultBlockState();
    }

    /** 普通结构候选在前，带角色的部件候选在后；同一部件可按多个角色分别列出，并保留各自数量限制。 */
    private static List<Candidate> candidatesOf(
                                                Cell cell,
                                                Map<PartRole, Blueprint.CountRequirement> requirements) {
        List<Candidate> partCandidates = new ArrayList<>();
        for (PartRole capability : cell.binding().partRoles()) {
            Blueprint.CountRequirement requirement = requirements.get(capability);
            int min = requirement == null ? 0 : requirement.min();
            int max = requirement == null ? Integer.MAX_VALUE : requirement.max();
            for (MachineDefinition definition : Machines.registered()) {
                if (carriesCapability(definition, capability)) {
                    partCandidates.add(new Candidate(
                            new ItemStack(definition.registeredBlock().get()), capability, min, max));
                }
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Block block : cell.binding().candidateBlocks()) {
            ItemStack stack = new ItemStack(block);
            // 角色谓词也返回部件候选方块；下方携带角色的条目替代无角色的重复项。
            if (!containsItem(partCandidates, stack)) {
                candidates.add(new Candidate(stack, null, 0, Integer.MAX_VALUE));
            }
        }
        candidates.addAll(partCandidates);
        return candidates;
    }

    private static boolean carriesCapability(MachineDefinition definition, PartRole capability) {
        for (PartRoleAttachment metadata : definition.metadata(PartRoleAttachment.TYPE)) {
            if (metadata.role() == capability) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsItem(List<Candidate> candidates, ItemStack stack) {
        for (Candidate candidate : candidates) {
            if (candidate.item().getItem() == stack.getItem()) {
                return true;
            }
        }
        return false;
    }

    private static Map<PartRole, Block> partBlocksByCapability() {
        Map<PartRole, Block> result = new LinkedHashMap<>();
        for (MachineDefinition definition : Machines.registered()) {
            for (PartRoleAttachment metadata : definition.metadata(PartRoleAttachment.TYPE)) {
                result.putIfAbsent(metadata.role(), definition.registeredBlock().get());
            }
        }
        return result;
    }
}
