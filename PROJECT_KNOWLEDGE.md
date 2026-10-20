# 项目知识

- 插件身份和声明内容相同时，登记顺序不决定执行结果：`PluginCollection` 锁定并复用快照，`RegistrationPlan` 按依赖与阶段调度，每个引擎独立持有启动状态。钩子内部须使用确定输入；生产环境保持单宿主。来源与边界见 [插件生命周期](docs-content/foundation/lifecycle.md)。
