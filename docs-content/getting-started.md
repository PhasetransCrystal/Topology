---
sidebar_position: 2
---

# Getting Started

## 添加依赖

```gradle
// build.gradle
repositories { 
    maven {
        name = "ptcrysReleases"
        url = uri("https://maven.ptcrys.net/releases")
    }
}
dependencies {
    implementation "net.ptcrys:Topology:${topology_version}"
}
```

## 架构：纯库 + 你的内容

Topology 只分发框架 API，**不分发任何玩法内容**。你需要的全部功能通过继承框架基类实现：

```
net.ptcrys.topo.api.* → 库本体（全部继承扩展点都在这里）
你的 Mod               → 继承基类 + TopoPlugin 注册，与库解耦
```

内容注册的唯一通道是 `TopoPlugin`——没有隐藏的特权通道。

## TopoPlugin — 内容注册入口

继承 `TopoPlugin`，在构造器中传入你的 `RegistryCore`，只覆写需要的钩子。领域 API 由基类按插件持有，无需声明字段或实现 getter。

```java
public final class MyModPlugin extends TopoPlugin {
    public MyModPlugin() {
        super(MyMod.REGISTRY);
    }

    @Override
    public void registerMaterials(MaterialDomainRegistration material) {
        // ingotForm 在 registerMaterialFoundation 中声明
        material.material("copper").lang("Copper", "铜").form(ingotForm).build();
    }

    @Override
    public void registerRecipeTypes(RecipeDomainRegistration recipe) {
        recipe.recipeType("macerating").displayName("Macerating", "粉碎");
    }

    @Override
    public void registerRecipes(RecipeDomainRegistration recipe) {
        // 各内容领域的声明与生成完成后，在此组装具体配方
    }

    @Override
    public void registerRuntime(RuntimeDomainRegistration runtime) {
        // 此时 RegistryLib Entry 已绑定，可以取游戏对象
        // runtime.tooltip(myItem.get(), myPanelProvider);
        // runtime.itemBehavior(myTool.get(), myBehavior);
    }
}
```

`TopoPlugin` 位于 `api.api.plugin`，钩子接口按领域分组在 `plugin.hook`。
下游继承基类即可，不需要直接组合这些接口或使用 `plugin.internal` 下的实现。

## 接线：依赖方只注册插件

```java
@Mod("mymod")
public class MyMod {
    public static final RegistryCore REGISTRY = RegistryCore.create("mymod");

    public MyMod(IEventBus modEventBus) {
        TopoPlugins.register(new MyModPlugin());
    }
}
```

在你的 `neoforge.mods.toml` 声明对 `topo` 的必需依赖，并设置 `ordering="AFTER"`。
Topo 宿主负责接线 `TopoPlugins.attachHost(modEventBus)`，依赖方无需重复接线，也无需手工监听
`RegisterEvent` 或调用各个 bootstrap 方法。

物品实例可用后才能执行的绑定放在 `registerRuntime(runtime)`，由宿主在 common setup 的主线程任务中调用。
装备策略的 `machineBehavior()` 自动绑定到该策略生成并登记的装备物品，额外物品使用 `runtime.itemBehavior`。
共享表在 load complete 的主线程任务中统一冻结，插件无需自行冻结或接线运行时事件。
需要客户端专用事件的功能仍可在 client setup 中绑定；不同 Mod 的事件任务间没有先后承诺。

同一 Mod 可以提供多个 Plugin，但必须共用其 RegistryCore。注册时无需提供 Plugin ID，
也不声明 Plugin 之间的依赖：各阶段会遍历全部 Plugin，再统一冻结对应注册表。
因此 API Plugin 应在 foundation 钩子声明数据类型、形态等基础内容，具体内容 Plugin
在后续内容钩子使用这些声明。插件在相同钩子中的执行次序不是依赖契约；独立贡献才可安全组合。

```java
TopoPlugins.register(new MyApiPlugin());
TopoPlugins.register(new MyContentPlugin());
```

引擎按 Mod ID 和类名稳定遍历插件，使结果不受模组构造器到达顺序影响；此排序只为可复现性，
不应用于表达同一阶段内的先后依赖。

:::info
Topo 宿主还负责资源能力、管道引擎、网络、渲染、tick 心跳等框架运行时。
Plugin 基类负责缓存领域 API，并从 RegistryCore 推导命名空间。
:::

## 生命周期与冻结点

每个钩子先执行全部插件，再由领域模块校验、生成内容并冻结对应表。
全部内容领域结束后才执行 `registerRecipes`，随后导出翻译；游戏对象绑定由 RegistryLib 随后完成。
需要游戏对象的操作放在 `registerRuntime`。

冻结后不能再添加条目。例如 `MaterialForm` 必须在 `registerMaterialFoundation` 声明，
不能放在 `registerMaterials`。完整钩子、事件时序与内部模块扩展方式见
[Plugin Lifecycle](foundation/lifecycle.md)。

## 关键约定

- **领域 API 传裸路径**（如 `material("copper")`）；生成的 ID 带 Plugin 命名空间 `mymod:copper`
- **双语名称**：每个有显示名称的条目必须提供 `en` 和 `cn`
- **Topology 只提供基建**：不在创造模式物品栏、JEI 中添加内容
