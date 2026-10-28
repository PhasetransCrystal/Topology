# 项目知识

- 材料数据由内部 `MaterialStrategy` 持有，Builder 与构建后共用该实例；共享窗口内直接通过 `material.data(use)` 跨插件补充，重复类型拒绝覆盖。全部 `registerMaterials` 完成后关闭写入，统一匹配 `appliesTo`、校验和生成内容。契约见 [材料数据](docs-content/material/material-data.md) 与 [MaterialForm](docs-content/material/material-form.md)。
- `appliesTo` 是材料形态选择的唯一来源；材料不维护包含或排除名单，`.form(form, options)` 只配置局部数据和原版条目覆盖。契约见 [MaterialForm](docs-content/material/material-form.md)。
- 材料冻结统一发布匹配结果；`hasForm` 使用成员索引，后处理仅遍历缓存的激活列表（完整 ID 排序）。任意形态规则仍需 M×F 次求值，注册为单线程启动过程；见 [材料注册流程与性能边界](docs-content/material.md#注册流程与性能边界)。

- 插件身份和声明内容相同时，登记顺序不决定执行结果：`PluginCollection` 锁定并复用快照，`RegistrationPlan` 按依赖与阶段调度，每个引擎独立持有启动状态。钩子内部须使用确定输入；生产环境保持单宿主。来源与边界见 [插件生命周期](docs-content/foundation/lifecycle.md)。
- 跨 TopoPlugin 的依赖按所属模组依赖隐式确认，不单独配置插件依赖；依赖存在不保证句柄、静态内容或运行时已初始化，需按阶段取用。来源与边界见 [跨插件兼容](docs-content/compatibility/topo-plugins.md)。
