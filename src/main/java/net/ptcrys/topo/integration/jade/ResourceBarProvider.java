package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.component.ServiceKey;

import java.util.function.Consumer;

/** 由组件提供资源条数据；Jade 集成不识别具体资源及端口实现。 */
public interface ResourceBarProvider {

    ServiceKey<ResourceBarProvider, Void> KEY = ServiceKey.topo("jade_resource_bars", ResourceBarProvider.class, Void.class);

    /** 在本次读取中按显示顺序提交资源条，不触发资源转移。 */
    void collect(Consumer<Bar> output);

    record Bar(LangKey name, int color, long amount, long capacity) {}
}
