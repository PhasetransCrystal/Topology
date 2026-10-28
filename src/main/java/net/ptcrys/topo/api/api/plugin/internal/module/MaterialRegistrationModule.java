package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.hook.MaterialPlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationContext;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationModule;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPlan;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPoint;
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
public final class MaterialRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> FOUNDATION = RegistrationPoint.of("topo:material/foundation", MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterialFoundation);
    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> MATERIALS = RegistrationPoint.of("topo:material/materials", MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterials);
    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> CREATIVE_TABS = RegistrationPoint.of("topo:material/creative_tabs", MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterialCreativeTabs);
    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> FOLLOW_UPS = RegistrationPoint.of("topo:material/follow_ups", MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterialFollowUps);

    static final String CONTENT = "topo:material/generate";

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, MaterialRegistrationModule.FOUNDATION, List.of(RecipeRegistrationModule.TYPES.id()), context, () -> {
            MaterialDataRegistry.freeze();
            FormDataRegistry.freeze();
            MaterialFormRegistry.freeze();
            MaterialPostProcessorRegistry.freeze();
        });
        plan.point(Phase.CONTENT, MaterialRegistrationModule.MATERIALS, List.of(MaterialRegistrationModule.FOUNDATION.id()), context, MaterialRegistry::freeze);
        plan.point(Phase.CONTENT, MaterialRegistrationModule.CREATIVE_TABS, List.of(MaterialRegistrationModule.MATERIALS.id()), context, () -> {});
        plan.step(Phase.CONTENT, CONTENT, List.of(MaterialRegistrationModule.CREATIVE_TABS.id()), () -> {
            registerMaterialFormContent(context);
            runMaterialPostProcessors();
        });
        plan.point(Phase.CONTENT, MaterialRegistrationModule.FOLLOW_UPS, List.of(CONTENT), context, () -> {});
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
            for (MaterialPostProcessor processor : material.strategy().postProcessors()) {
                processor.validate(material);
            }
        }
        for (Material material : MaterialRegistry.registered()) {
            for (MaterialPostProcessor processor : material.strategy().postProcessors()) {
                processor.process(material);
            }
        }
    }
}
