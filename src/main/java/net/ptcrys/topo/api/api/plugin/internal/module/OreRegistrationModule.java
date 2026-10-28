package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.hook.OrePlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationContext;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationModule;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPlan;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPoint;
import net.ptcrys.topo.api.ore.OreVeins;
import net.ptcrys.topo.api.ore.display.OreVeinDisplays;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;
import net.ptcrys.topo.api.ore.mode.OreVeinModes;
import net.ptcrys.topo.api.ore.policy.OreAirExposurePolicies;
import net.ptcrys.topo.api.ore.policy.OreConflictPolicies;
import net.ptcrys.topo.api.ore.shape.OreVeinShapes;
import net.ptcrys.topo.helper.IdHelper;

import java.util.List;

/** Owns ore registration steps and their freeze fences. */
public final class OreRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<OrePlugin, OreDomainRegistration> FOUNDATION = RegistrationPoint.of(IdHelper.id("ore/foundation"), OrePlugin.class, OreDomainRegistration.DOMAIN, OrePlugin::registerOreFoundation);
    static final RegistrationPoint<OrePlugin, OreDomainRegistration> DISPLAYS = RegistrationPoint.of(IdHelper.id("ore/displays"), OrePlugin.class, OreDomainRegistration.DOMAIN, OrePlugin::registerOreDisplays);
    static final RegistrationPoint<OrePlugin, OreDomainRegistration> VEINS = RegistrationPoint.of(IdHelper.id("ore/veins"), OrePlugin.class, OreDomainRegistration.DOMAIN, OrePlugin::registerOreVeins);
    static final RegistrationPoint<OrePlugin, OreDomainRegistration> DATAGEN = RegistrationPoint.of(IdHelper.id("ore/datagen"), OrePlugin.class, OreDomainRegistration.DOMAIN, OrePlugin::registerOreDatagen);

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, OreRegistrationModule.FOUNDATION, List.of(MaterialRegistrationModule.CONTENT), context, () -> {
            OreVeinShapes.freeze();
            OreVeinModes.freeze();
            OreAirExposurePolicies.freeze();
            OreConflictPolicies.freeze();
        });
        plan.point(Phase.CONTENT, OreRegistrationModule.DISPLAYS, List.of(OreRegistrationModule.FOUNDATION.id()), context, () -> {
            OreVeinDisplays.freeze();
            for (OreVeinMode mode : OreVeinModes.view()) OreVeinDisplays.require(mode.id());
        });
        plan.point(Phase.CONTENT, OreRegistrationModule.VEINS, List.of(OreRegistrationModule.DISPLAYS.id()), context, OreVeins::freeze);
        plan.point(Phase.CONTENT, OreRegistrationModule.DATAGEN, List.of(OreRegistrationModule.VEINS.id()), context, () -> {});
    }
}
