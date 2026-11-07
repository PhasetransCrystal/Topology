package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * The item form and count range dropped by a material's natural ore block when it is not mined with
 * silk touch. The referenced form must be resolved for the material before the material freezes.
 */
public final class OreDropDataType extends MaterialDataType<OreDropDataType.OreDrop> {

    public record OreDrop(MaterialForm form, int minCount, int maxCount) {

        public OreDrop {
            Objects.requireNonNull(form, "form");
            if (minCount < 1) {
                throw new IllegalArgumentException("ore drop minCount must be positive");
            }
            if (maxCount < minCount) {
                throw new IllegalArgumentException("ore drop maxCount must not be below minCount");
            }
        }
    }

    public OreDropDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<OreDrop> drop(MaterialForm form, int minCount, int maxCount) {
        return use(new OreDrop(form, minCount, maxCount));
    }

    @Override
    public void validate(Material material, OreDrop data) {
        if (!material.hasForm(data.form())) {
            throw new IllegalStateException(
                    "material " + material.id() + " declares ore drop form " + data.form().id() + " but does not resolve that form");
        }
    }
}
