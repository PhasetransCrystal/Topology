package net.ptcrys.topo.api.material;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.material.form.MaterialForm;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Executes frozen form plans. It never resolves form predicates or mutates material state. */
public final class MaterialContentExecutor {

    private MaterialContentExecutor() {}

    @ApiStatus.Internal
    public static void registerAll(List<Material> materials, Function<String, RegistryCore> cores) {
        Objects.requireNonNull(cores, "cores");
        for (Material material : materials) {
            RegistryCore core = Objects.requireNonNull(
                    cores.apply(material.id().getNamespace()), "registry core for " + material.id());
            MaterialContentContext context = new MaterialContentContext(core, material);
            for (MaterialForm form : material.forms()) {
                material.formPlan(form).register(context);
            }
        }
    }
}
