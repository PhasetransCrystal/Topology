package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.lang.ChineseConvert;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.LangRegistry;
import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;

import java.util.List;
import java.util.Map;

/** Owns lang registration steps and their freeze fences. */
final class LangRegistrationModule implements RegistrationModule {

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.LANGUAGE, LangDomainRegistration.KEYS, List.of(), context, () -> {
            LangRegistry.freeze();
            for (TopoPlugin plugin : context.plugins()) {
                RegistryCore core = plugin.registry();
                if (core.doDatagen()) {
                    for (LangKey key : plugin.lang().registeredKeys()) {
                        core.lang(key.key(), Map.of("en_us", key.en(), "zh_cn", key.cn(), "zh_tw", ChineseConvert.s2t(key.cn())));
                    }
                }
            }
        });
    }
}
