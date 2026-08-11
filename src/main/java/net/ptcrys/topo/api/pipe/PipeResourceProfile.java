package net.ptcrys.topo.api.pipe;

import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;

import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.LongFunction;

/**
 * Per-resource-kind pipe profile: a plain immutable value object, deliberately not a registry.
 *
 * <p>
 * It carries the strong {@link MachineResourceType} handle the whole transfer engine runs on,
 * the display-only amount formatter, and — for kinds whose resources have a registry identity
 * worth filtering (items, fluids) — the {@link PipeFilterAdapter} that turns white/black list
 * entries into fast predicates. Scalar kinds (energy, heat) declare {@code null}: they carry
 * exactly one resource each, so filtering is meaningless and {@link Pipes.Builder} rejects a
 * non-{@code NONE} {@link PipeFilterSettings} on them.
 *
 * <p>
 * It has no stable ID, no persistence and no runtime dispatch of its own, so per the
 * freezable-strategy-registry standard it stays a value object; one instance is declared per
 * resource kind in {@code BuiltinOIPipes} and shared by all tiers.
 */
public record PipeResourceProfile(
                                  MachineResourceType<?> resourceType,
                                  LongFunction<String> amountFormatter,
                                  @Nullable PipeFilterAdapter<?> filterAdapter) {

    public PipeResourceProfile {
        Objects.requireNonNull(resourceType, "resource type");
        Objects.requireNonNull(amountFormatter, "amount formatter");
        if (resourceType.blockCapability() == null) {
            throw new IllegalArgumentException(
                    "Resource type '" + resourceType.id() + "' exposes no block capability and cannot be piped");
        }
    }

    public String formatAmount(long amount) {
        return amountFormatter.apply(amount);
    }

    /** Convenience for transfer-engine generics; the capability is validated non-null above. */
    @SuppressWarnings("unchecked")
    public <R extends Resource> MachineResourceType<R> typedResourceType() {
        return (MachineResourceType<R>) resourceType;
    }

    /** Typed view of the filter adapter for engine/UI generics; {@code null} for scalar kinds. */
    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable PipeFilterAdapter<R> typedFilterAdapter() {
        return (PipeFilterAdapter<R>) filterAdapter;
    }
}
