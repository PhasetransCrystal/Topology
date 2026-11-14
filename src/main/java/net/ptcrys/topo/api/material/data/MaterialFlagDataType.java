package net.ptcrys.topo.api.material.data;

import net.ptcrys.topo.api.material.Material;

import net.minecraft.resources.Identifier;

import java.util.function.Predicate;

/**
 * Reusable boolean capability. A plugin declares one instance per orthogonal flag, e.g.
 * {@code PULVERIZABLE}, and injects {@link #enabled()}; forms can then select materials with
 * {@link #isEnabled()}. Adding a flag never requires editing a shared enum.
 */
public final class MaterialFlagDataType extends MaterialDataType<Boolean> {

    public MaterialFlagDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<Boolean> enabled() {
        return use(Boolean.TRUE);
    }

    public MaterialDataUse<Boolean> disabled() {
        return use(Boolean.FALSE);
    }

    public Predicate<Material> isEnabled() {
        return is(Boolean.TRUE);
    }
}
