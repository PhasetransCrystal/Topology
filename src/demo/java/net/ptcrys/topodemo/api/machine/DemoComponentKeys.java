package net.ptcrys.topodemo.api.machine;

import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;

/** Demo 组件的身份与语言声明，避免具体实现占用框架命名空间。 */
public final class DemoComponentKeys {

    private DemoComponentKeys() {}

    public static <T extends MachineComponent> ComponentKey<T> id(String path, Class<T> type) {
        return ComponentKey.of(TopoDemoApiPlugin.INSTANCE.machine().id(path), type);
    }

    public static <T extends MachineComponent> ComponentKey<T> id(
                                                                  String path, Class<T> type, String en, String cn) {
        ComponentKey<T> key = id(path, type);
        TopoDemoApiPlugin.INSTANCE.lang().resource(key.id(), "trait", en, cn);
        return key;
    }
}
