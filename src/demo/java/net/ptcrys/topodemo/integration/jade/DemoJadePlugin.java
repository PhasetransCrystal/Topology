package net.ptcrys.topodemo.integration.jade;

import snownee.jade.addon.harvest.HarvestToolProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Demo 的具体工具展示由内容模组注册，框架不约定扳手物品。 */
@WailaPlugin
public final class DemoJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        HarvestToolProvider.registerHandler(() -> WrenchToolHandler.INSTANCE);
    }
}
