package net.ptcrys.topo.api.pipe;

import net.ptcrys.topo.api.api.lang.LangKey;

/** 管道配置的可选详情视图，避免集成界面识别具体距离或优先级配置。 */
public interface PipePortStrategyDetails extends PipePortStrategyConfig {

    LangKey detailLang();
}
