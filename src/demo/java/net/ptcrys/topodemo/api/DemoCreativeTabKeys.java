package net.ptcrys.topodemo.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/** Demo 材料形态使用的创造模式页签身份。 */
public final class DemoCreativeTabKeys {

    public static final ResourceKey<CreativeModeTab> MATERIALS = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, TopoDemoApiPlugin.INSTANCE.material().id("materials"));
    public static final ResourceKey<CreativeModeTab> FLUIDS = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, TopoDemoApiPlugin.INSTANCE.material().id("fluids"));

    private DemoCreativeTabKeys() {}
}
