package net.ptcrys.topo.apiv2.material.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.data.MaterialDataUse;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A global material post-processing step. It runs once for every registered material after material
 * forms have registered their items and blocks. A material opts in by declaring the processor's
 * paired {@link ProcessorDataType} via {@link #activation()}; materials without that declaration
 * are skipped.
 */
public abstract class MaterialPostProcessor {

    private final Identifier id;
    private final List<MaterialForm> requiredForms;
    private ProcessorDataType dataType;

    protected MaterialPostProcessor(Identifier id, MaterialForm... requiredForms) {
        this.id = id;
        this.requiredForms = List.of(requiredForms);
    }

    public final Identifier id() {
        return id;
    }

    /** The paired data type; only available once the processor is registered. */
    public final ProcessorDataType dataType() {
        if (dataType == null) {
            throw new IllegalStateException("material post-processor " + id + " is not registered");
        }
        return dataType;
    }

    /** Declares this processor on a material: {@code .data(PROCESSOR.activation())}. */
    public final MaterialDataUse<MaterialPostProcessor> activation() {
        return dataType().activation();
    }

    final void bindDataType(ProcessorDataType type) {
        if (dataType != null) {
            throw new IllegalStateException("material post-processor " + id + " is already registered");
        }
        dataType = type;
    }

    /**
     * Fail-fast structural check, run before any {@link #process} call: a material activating this
     * processor must declare every required form. Override to add checks beyond declared forms.
     */
    public void validate(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        for (MaterialForm form : requiredForms) {
            require(material, form);
        }
    }

    public abstract void process(Material material);

    /** Whether {@code material} does not declare this processor's activation. */
    protected final boolean notDeclaredOn(Material material) {
        return material.strategy().data(dataType()).isEmpty();
    }

    /** Throws if {@code material} does not declare {@code form}. */
    protected final void require(Material material, MaterialForm form) {
        if (!material.strategy().forms().contains(form)) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + ": material " + material.id() + " is missing required form " + form.id());
        }
    }
}
