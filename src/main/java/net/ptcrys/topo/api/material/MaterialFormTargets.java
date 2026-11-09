package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.api.registration.ExternalBlockTarget;
import net.ptcrys.topo.api.api.registration.ExternalFluidTarget;
import net.ptcrys.topo.api.api.registration.ExternalItemTarget;

import java.util.Objects;
import java.util.Optional;

/** Immutable external-target set for one material form. */
public record MaterialFormTargets(
                                  Optional<ExternalItemTarget> item,
                                  Optional<ExternalBlockTarget> block,
                                  Optional<ExternalFluidTarget> fluid) {

    private static final MaterialFormTargets EMPTY = new MaterialFormTargets(
            Optional.empty(), Optional.empty(), Optional.empty());

    public MaterialFormTargets {
        Objects.requireNonNull(item, "item target");
        Objects.requireNonNull(block, "block target");
        Objects.requireNonNull(fluid, "fluid target");
    }

    public static MaterialFormTargets empty() {
        return EMPTY;
    }

    public MaterialFormTargets withItem(ExternalItemTarget target) {
        return new MaterialFormTargets(Optional.of(target), block, fluid);
    }

    public MaterialFormTargets withBlock(ExternalBlockTarget target) {
        return new MaterialFormTargets(item, Optional.of(target), fluid);
    }

    public MaterialFormTargets withFluid(ExternalFluidTarget target) {
        return new MaterialFormTargets(item, block, Optional.of(target));
    }
}
