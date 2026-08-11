package net.ptcrys.topo.data.vanilla;

import net.ptcrys.registrylib.util.entry.RegistryEntry;
import net.ptcrys.topo.Topology;

import net.minecraft.world.item.CreativeModeTab;

import java.util.Map;

/**
 * Creative tabs owned by Odyssey Industrial, created through RegistryLib.
 *
 * <p>
 * The single {@link #MATERIALS} tab collects every native material item and ore block item. Tab
 * placement itself is local to item/block registration (the form strategies call
 * {@code builder.addTab(MATERIALS.getKey())}); this holder only owns tab creation.
 *
 * <p>
 * The tab must be created before the material registration driver runs so the tab key resolves
 * during item registration. {@link #init()} exists purely to force class-loading from the bootstrap
 * sequence; the field initializer performs the registration at class load.
 */
public final class BuiltinOICreativeTabs {

    /** The material creative tab; title lang key is {@code itemGroup.topo.materials}. */
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MATERIALS = Topology.REGISTRY.creativeTab(
            "materials",
            "Odyssey Industrial Materials",
            Map.of());

    /** The machine creative tab; title lang key is {@code itemGroup.topo.machines}. */
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MACHINES = Topology.REGISTRY.creativeTab(
            "machines",
            "Odyssey Industrial Machines",
            Map.of());

    /** The equipment creative tab; title lang key is {@code itemGroup.topo.equipment}. */
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> EQUIPMENT = Topology.REGISTRY.creativeTab(
            "equipment",
            "Odyssey Industrial Equipment",
            Map.of());

    /** The component creative tab; title lang key is {@code itemGroup.topo.components}. */
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> COMPONENTS = Topology.REGISTRY.creativeTab(
            "components",
            "Odyssey Industrial Components",
            Map.of());

    private BuiltinOICreativeTabs() {}

    /** Triggers class initialization so the tab is registered before the driver runs. */
    public static void init() {}
}
