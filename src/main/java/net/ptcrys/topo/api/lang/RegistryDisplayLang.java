package net.ptcrys.topo.api.lang;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.topo.api.api.lang.ChineseConvert;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Objects;

/**
 * Single bridge from {@link FixedDisplayName} onto RegistryLib item/block lang (en + zh_cn +
 * zh_tw via s2t). Strategies call this instead of hand-rolling {@code Map} + {@link ChineseConvert}.
 */
public final class RegistryDisplayLang {

    private RegistryDisplayLang() {}

    public static void applyItem(
                                 ItemBuilder<? extends Item, RegistryCore> builder,
                                 RegistryCore core,
                                 String entryPath,
                                 FixedDisplayName name) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(entryPath, "entryPath");
        Objects.requireNonNull(name, "name");
        builder.lang(name.en());
        if (core.doDatagen()) {
            core.lang(
                    "item." + core.getModid() + "." + entryPath,
                    Map.of(
                            "zh_cn", name.cn(),
                            "zh_tw", ChineseConvert.s2t(name.cn())));
        }
    }

    public static void applyBlock(
                                  BlockBuilder<? extends Block, RegistryCore> builder,
                                  RegistryCore core,
                                  String entryPath,
                                  FixedDisplayName name) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(entryPath, "entryPath");
        Objects.requireNonNull(name, "name");
        builder.lang(name.en());
        if (core.doDatagen()) {
            core.lang(
                    "block." + core.getModid() + "." + entryPath,
                    Map.of(
                            "zh_cn", name.cn(),
                            "zh_tw", ChineseConvert.s2t(name.cn())));
        }
    }
}
