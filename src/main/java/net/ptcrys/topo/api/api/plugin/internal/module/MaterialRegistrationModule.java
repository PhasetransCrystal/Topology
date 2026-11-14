package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.hook.MaterialPlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationContext;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationModule;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPlan;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPoint;
import net.ptcrys.topo.api.material.MaterialContentExecutor;
import net.ptcrys.topo.api.material.MaterialLookup;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;
import net.ptcrys.topo.api.material.form.FormDataRegistry;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;
import net.ptcrys.topo.api.material.process.MaterialPostProcessorRegistry;
import net.ptcrys.topo.api.material.process.MaterialPostProcessorRunner;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.resources.Identifier;

import java.util.List;

/** Owns material registration steps and their freeze fences. */
public final class MaterialRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> FOUNDATION = RegistrationPoint.of(IdHelper.topo("material/foundation"), MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterialFoundation);
    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> MATERIALS = RegistrationPoint.of(IdHelper.topo("material/materials"), MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterials);
    static final Identifier MATERIALS_CLOSED = IdHelper.topo("material/materials_closed");
    static final RegistrationPoint<MaterialPlugin, MaterialContributionRegistration> CONTRIBUTIONS = RegistrationPoint.of(IdHelper.topo("material/contributions"), MaterialPlugin.class, MaterialContributionRegistration.DOMAIN, MaterialPlugin::registerMaterialContributions);
    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> CREATIVE_TABS = RegistrationPoint.of(IdHelper.topo("material/creative_tabs"), MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterialCreativeTabs);
    static final RegistrationPoint<MaterialPlugin, MaterialDomainRegistration> FOLLOW_UPS = RegistrationPoint.of(IdHelper.topo("material/follow_ups"), MaterialPlugin.class, MaterialDomainRegistration.DOMAIN, MaterialPlugin::registerMaterialFollowUps);

    static final Identifier CONTENT = IdHelper.topo("material/generate");
    static final Identifier LOOKUP = IdHelper.topo("material/lookup");

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, MaterialRegistrationModule.FOUNDATION, List.of(RecipeRegistrationModule.TYPES.id()), context, () -> {
            MaterialDataRegistry.freeze();
            FormDataRegistry.freeze();
            MaterialFormRegistry.freeze();
            MaterialPostProcessorRegistry.freeze();
        });
        plan.point(Phase.CONTENT, MaterialRegistrationModule.MATERIALS, List.of(MaterialRegistrationModule.FOUNDATION.id()), context, () -> {});
        plan.step(Phase.CONTENT, MATERIALS_CLOSED, List.of(MaterialRegistrationModule.MATERIALS.id()), MaterialRegistry::closeMaterialCreation);
        plan.point(Phase.CONTENT, MaterialRegistrationModule.CONTRIBUTIONS, List.of(MATERIALS_CLOSED), context, MaterialRegistry::freeze);
        plan.point(Phase.CONTENT, MaterialRegistrationModule.CREATIVE_TABS, List.of(MaterialRegistrationModule.CONTRIBUTIONS.id()), context, () -> {});
        plan.step(Phase.CONTENT, CONTENT, List.of(MaterialRegistrationModule.CREATIVE_TABS.id()), () -> {
            MaterialContentExecutor.registerAll(MaterialRegistry.registered(), context::registry);
            MaterialPostProcessorRunner.processAll(MaterialRegistry.registered());
        });
        plan.point(Phase.CONTENT, MaterialRegistrationModule.FOLLOW_UPS, List.of(CONTENT), context, () -> {});
        // Runtime bindings depend on this step, so the index is rebuilt after deferred registries
        // are available and before plugin runtime callbacks run.
        plan.step(Phase.RUNTIME, LOOKUP, List.of(), () -> MaterialLookup.rebuild(MaterialRegistry.registered()));
    }
}
