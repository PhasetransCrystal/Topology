# Topology 使用文档

面向依赖 Topology 的模组开发者。本目录只回答“下游如何使用框架”：注册什么、覆写哪个钩子、需要提供哪些策略，以及与其他模组或 TopoPlugin 的兼容边界。

- 入门：[使用 Topology 注册内容](intro.md)、[快速开始](getting-started.md)、[插件生命周期](foundation/lifecycle.md)。
- 内容领域：[材料](material.md)、[装备](equipment.md)、[机器](machine.md)、[管道](pipe.md)、[配方](recipe.md)、[矿脉](ore.md)。
- 支撑：[本地化](localization.md)、[内容注册实作](foundation/content-plugin.md)、[兼容其他模组](compatibility/other-mods.md)、[兼容其他 TopoPlugin](compatibility/topo-plugins.md)、[内嵌](embedding.md)。

框架内部实现与设计依据在 [`docs/`](../docs/README.md)；Demo 如何组合这些能力在 [`demo-docs/`](../demo-docs/README.md)。

## 通用注意

- 先在各 foundation 钩子声明类型与策略，再在对应内容钩子引用；`build()` 只发布句柄，不是冻结点。
- 游戏对象绑定前不要 `.get()`；运行时绑定放在 `registerRuntime` 或对应能力查询回调。
- 能力/形态/策略声明了类型不等于实现匹配、消耗或传输；输入表达式写了标签也不会自动支持标签。
- 修改内容声明后先手动 `runClientData`，再构建；编译通过不等于注册、生成或游戏行为正确。
