package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;
import net.ptcrys.topo.api.ore.OreVeins;
import net.ptcrys.topo.api.ore.display.OreVeinDisplays;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;
import net.ptcrys.topo.api.ore.mode.OreVeinModes;
import net.ptcrys.topo.api.ore.policy.OreAirExposurePolicies;
import net.ptcrys.topo.api.ore.policy.OreConflictPolicies;
import net.ptcrys.topo.api.ore.shape.OreVeinShapes;

import java.util.List;

/** Owns ore registration steps and their freeze fences. */
final class OreRegistrationModule implements RegistrationModule {

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, OreDomainRegistration.FOUNDATION, List.of(MaterialRegistrationModule.CONTENT), context, () -> {
            OreVeinShapes.freeze();
            OreVeinModes.freeze();
            OreAirExposurePolicies.freeze();
            OreConflictPolicies.freeze();
        });
        plan.point(Phase.CONTENT, OreDomainRegistration.DISPLAYS, List.of(OreDomainRegistration.FOUNDATION.id()), context, () -> {
            OreVeinDisplays.freeze();
            for (OreVeinMode mode : OreVeinModes.view()) OreVeinDisplays.require(mode.id());
        });
        plan.point(Phase.CONTENT, OreDomainRegistration.VEINS, List.of(OreDomainRegistration.DISPLAYS.id()), context, OreVeins::freeze);
        plan.point(Phase.CONTENT, OreDomainRegistration.DATAGEN, List.of(OreDomainRegistration.VEINS.id()), context, () -> {});
    }
}
