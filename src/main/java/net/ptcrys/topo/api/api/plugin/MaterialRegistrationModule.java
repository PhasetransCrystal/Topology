package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;
import net.ptcrys.topo.api.material.form.FormDataRegistry;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.api.material.process.MaterialPostProcessorRegistry;

import java.util.List;

/** Owns material registration steps and their freeze fences. */
final class MaterialRegistrationModule implements RegistrationModule {

    static final String CONTENT = "topo:material/generate";

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, MaterialDomainRegistration.FOUNDATION, List.of(RecipeDomainRegistration.TYPES.id()), context, () -> {
            MaterialDataRegistry.freeze();
            FormDataRegistry.freeze();
            MaterialFormRegistry.freeze();
            MaterialPostProcessorRegistry.freeze();
        });
        plan.point(Phase.CONTENT, MaterialDomainRegistration.MATERIALS, List.of(MaterialDomainRegistration.FOUNDATION.id()), context, MaterialRegistry::freeze);
        plan.point(Phase.CONTENT, MaterialDomainRegistration.CREATIVE_TABS, List.of(MaterialDomainRegistration.MATERIALS.id()), context, () -> {});
        plan.step(Phase.CONTENT, CONTENT, List.of(MaterialDomainRegistration.CREATIVE_TABS.id()), () -> {
            registerMaterialFormContent(context);
            runMaterialPostProcessors();
        });
        plan.point(Phase.CONTENT, MaterialDomainRegistration.FOLLOW_UPS, List.of(CONTENT), context, () -> {});
    }

    private static void registerMaterialFormContent(RegistrationContext context) {
        for (Material material : MaterialRegistry.registered()) {
            RegistryCore core = context.registry(material.id().getNamespace());
            MaterialContentContext materialContext = new MaterialContentContext(core, material);
            for (MaterialForm form : material.strategy().forms()) {
                form.strategy().register(materialContext, form);
            }
        }
    }

    private static void runMaterialPostProcessors() {
        for (Material material : MaterialRegistry.registered()) {
            for (MaterialPostProcessor processor : MaterialPostProcessorRegistry.registered()) {
                processor.validate(material);
            }
        }
        for (Material material : MaterialRegistry.registered()) {
            for (MaterialPostProcessor processor : MaterialPostProcessorRegistry.registered()) {
                processor.process(material);
            }
        }
    }
}
