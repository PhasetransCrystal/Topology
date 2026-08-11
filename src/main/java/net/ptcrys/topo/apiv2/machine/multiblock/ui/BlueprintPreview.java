package net.ptcrys.topo.apiv2.machine.multiblock.ui;

import net.ptcrys.topo.api.visual.ConnectedTextureProperties;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.OrientedMachineBlock;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Cell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
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
 * The ideal ("required") preview model of a {@link Blueprint}: per cell, a representative
 * {@link BlockState} for the 3D scene plus the full candidate listing for the BOM dock and the
 * cell-detail inspector. Shared by the JEI multiblock category and the controller's structure page
 * so both always show the same example structure.
 *
 * <p>
 * Derivation per non-controller cell: a pinned expected state wins (the canonical frame is the
 * preview frame); otherwise the first eligible part machine is substituted while a count
 * requirement still wants one (so the example is actually formable); otherwise the predicate's
 * first block candidate. The controller cell renders the controller block facing
 * {@link Direction#SOUTH} — the fixed camera-facing convention.
 */
public final class BlueprintPreview {

    /** One cell's preview data: scene state plus everything the docks need to describe it. */
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

    /**
     * One placeable option for a cell. {@code role == null} marks a plain structural block;
     * otherwise the option fulfils that part role and {@code min}/{@code max} carry the
     * blueprint-wide count requirement ({@code 0}/{@code Integer.MAX_VALUE} when unconstrained).
     */
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

    /** Local position &rarr; representative state, controller included; insertion order preserved. */
    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public Map<BlockPos, List<Candidate>> candidatesByCell() {
        return candidatesByCell;
    }

    /** The ideal preview of {@code blueprint} for {@code controller} (its block, facing south). */
    public static BlueprintPreview of(Blueprint blueprint, MachineDefinition controller) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(controller, "controller definition");

        Map<PartRole, Block> partBlocks = partBlocksByCapability();
        Map<PartRole, Blueprint.CountRequirement> requirements = blueprint.countRequirements();
        Map<PartRole, Integer> remaining = new LinkedHashMap<>();
        for (Blueprint.CountRequirement requirement : requirements.values()) {
            if (requirement.min() > 0) {
                remaining.put(requirement.role(), requirement.min());
            }
        }

        List<PreviewCell> cells = new ArrayList<>();
        for (Cell cell : blueprint.cells()) {
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
            cells.add(new PreviewCell(cell.localPos(), state, candidatesOf(cell, requirements)));
        }
        return new BlueprintPreview(cells);
    }

    /** Controller block facing the camera convention (south), formed-look left to the block's default. */
    public static BlockState controllerPreviewState(MachineDefinition controller) {
        BlockState state = controller.registeredBlock().getDefaultState();
        if (state.hasProperty(OrientedMachineBlock.FACING)) {
            state = state.setValue(OrientedMachineBlock.FACING, Direction.SOUTH);
        }
        return state;
    }

    private static @Nullable BlockState representativeState(
                                                            Cell cell,
                                                            Map<PartRole, Block> partBlocks,
                                                            Map<PartRole, Integer> remaining) {
        // Substitute a part machine while a count requirement still wants one, so the rendered
        // example satisfies its own blueprint.
        for (PartRole capability : cell.predicate().partRoles()) {
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
            // The required preview shows the FORMED look: melted-in parts light their CTM flag.
            if (state.hasProperty(ConnectedTextureProperties.CTM_ACTIVE)) {
                state = state.setValue(ConnectedTextureProperties.CTM_ACTIVE, true);
            }
            return state;
        }
        BlockState pinned = cell.expectedState();
        if (pinned != null) {
            return pinned;
        }
        for (ItemStack candidate : cell.predicate().candidates()) {
            if (candidate.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        return null;
    }

    /**
     * All placeable options of one cell: plain structural blocks first (the cell's "default" face —
     * the BOM row is named after the first option), then every capability-backed part, one entry per
     * role it can fulfil (a dual hatch lists item-input and fluid-input separately) with its
     * blueprint-wide count requirement.
     */
    private static List<Candidate> candidatesOf(
                                                Cell cell,
                                                Map<PartRole, Blueprint.CountRequirement> requirements) {
        List<Candidate> partCandidates = new ArrayList<>();
        for (PartRole capability : cell.predicate().partRoles()) {
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
        for (ItemStack stack : cell.predicate().candidates()) {
            // Capability predicates surface part items through candidates() too; the role-carrying
            // entries below replace those role-less duplicates.
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
