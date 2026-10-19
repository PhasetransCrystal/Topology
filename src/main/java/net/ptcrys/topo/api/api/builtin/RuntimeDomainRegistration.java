package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.topo.api.api.plugin.RegistrationDomain;
import net.ptcrys.topo.api.api.plugin.RegistrationPoint;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.machine.MachineItemBehavior;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.machine.ui.tooltip.TopoTooltipUiProvider;

import net.minecraft.world.item.Item;

/** Plugin runtime contributions run centrally once RegistryLib has bound game objects. */
public final class RuntimeDomainRegistration {

    public static final RegistrationDomain<RuntimeDomainRegistration> DOMAIN = RegistrationDomain.of(RuntimeDomainRegistration::new);
    public static final RegistrationPoint<RuntimeDomainRegistration> BINDINGS = RegistrationPoint.of("topo:runtime/plugins", DOMAIN);

    private final TopoPlugin plugin;

    private RuntimeDomainRegistration(TopoPlugin plugin) {
        this.plugin = plugin;
    }

    public TopoPlugin plugin() {
        return plugin;
    }

    public void tooltip(Item item, TopoTooltipUiProvider provider) {
        ItemTooltipUis.register(item, provider);
    }

    public void itemBehavior(Item item, MachineItemBehavior behavior) {
        MachineItemBehaviors.register(item, behavior);
    }
}
