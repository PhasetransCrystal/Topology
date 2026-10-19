package net.ptcrys.topo.api.api.plugin;

import java.util.List;

/** Built-in feature composition; the engine and plugin contract do not enumerate domain steps. */
final class TopoContentBootstrap {

    static final List<RegistrationModule> MODULES = List.of(
            new RecipeRegistrationModule(), new MaterialRegistrationModule(),
            new EquipmentRegistrationModule(), new MachineRegistrationModule(),
            new OreRegistrationModule(), new LangRegistrationModule(), new RuntimeRegistrationModule());

    private TopoContentBootstrap() {}

    static RegistrationPlan compile(TopoPlugins.Snapshot plugins, List<RegistrationModule> modules) {
        RegistrationContext context = new RegistrationContext(plugins);
        RegistrationPlan.Builder builder = new RegistrationPlan.Builder();
        for (RegistrationModule module : modules) module.define(builder, context);
        RegistrationPlan plan = builder.compile();
        context.configure(builder.points());
        return plan;
    }
}
