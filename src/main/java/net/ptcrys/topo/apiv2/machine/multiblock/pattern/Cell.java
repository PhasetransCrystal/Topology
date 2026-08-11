package net.ptcrys.topo.apiv2.machine.multiblock.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One normalized blueprint cell, with local position relative to the controller origin.
 *
 * <p>
 * {@code expectedState} is the cell's pinned block state in the <b>canonical (pre-orientation)
 * frame</b>: recognition pushes it forward through {@code rules} for the actual orientation before
 * comparing it to the world (see {@link StructureEngine}). {@code rules} is the cell's
 * {@link PropertyRule} set — covariant rules co-rotate their property with the orientation, and any
 * rule may mask properties out of the comparison. {@code maskedProperties} is the precomputed union
 * of every rule's masked properties, carried on the cell so the per-cell state compare never
 * re-derives (or allocates) it. A cell with no {@code expectedState} matches purely on its
 * {@link CellPredicate} (block identity / capability / controller), independent of orientation.
 */
public record Cell(
                   BlockPos localPos,
                   @Nullable BlockState expectedState,
                   Set<PropertyRule> rules,
                   CellPredicate predicate,
                   Set<Property<?>> maskedProperties) {

    /**
     * Properties the WORLD mutates in place without breaking or replacing the block (water flowing
     * into a waterloggable member, sponges drying it). Pinning them would unform structures as the
     * environment shifts — an underwater build would flicker with every water update — so any
     * state-pinning cell masks them automatically. A blueprint can still demand a specific value by
     * matching through a custom predicate.
     */
    public static final Set<Property<?>> ENVIRONMENT_MUTABLE_PROPERTIES = Set.of(BlockStateProperties.WATERLOGGED);

    public Cell {
        localPos = Objects.requireNonNull(localPos, "local position").immutable();
        rules = Set.copyOf(Objects.requireNonNull(rules, "property rules"));
        predicate = Objects.requireNonNull(predicate, "cell predicate");
        maskedProperties = Set.copyOf(Objects.requireNonNull(maskedProperties, "masked properties"));
    }

    /**
     * The union of every rule's masked properties — masking is a rule capability orthogonal to
     * {@link PropertyRule.Classification}, so the union is classification-blind — plus the automatic
     * {@link #ENVIRONMENT_MUTABLE_PROPERTIES} carried by the pinned state: a state-pinning cell
     * never compares world-mutated properties.
     */
    public static Set<Property<?>> maskedPropertiesOf(Set<PropertyRule> rules, @Nullable BlockState expectedState) {
        Objects.requireNonNull(rules, "property rules");
        Set<Property<?>> masked = new HashSet<>();
        for (PropertyRule rule : rules) {
            masked.addAll(rule.maskedProperties());
        }
        if (expectedState != null) {
            for (Property<?> property : ENVIRONMENT_MUTABLE_PROPERTIES) {
                if (expectedState.hasProperty(property)) {
                    masked.add(property);
                }
            }
        }
        return masked.isEmpty() ? Set.of() : Set.copyOf(masked);
    }
}
