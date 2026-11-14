# 注册内核

面向框架开发者。注册内核负责收集插件、按领域依赖执行钩子、冻结注册表并推进阶段。

## 职责与边界

- 负责：插件登记、领域模块组合、步骤依赖、注册点绑定、冻结与阶段状态。
- 不负责：具体材料/机器/配方/矿脉/管道的语义；这些由领域模块实现。
- 引擎和快照是包内实现；`api.api.plugin` 是对外插件基类与注册入口。

## 关键类型与数据流

- `TopoPluginEngine`：收集插件，驱动 `CONTENT → DERIVED → LANGUAGE → RUNTIME → READY`。
- `RegistrationPlan`：由各领域 `RegistrationModule` 的步骤与依赖构建；执行前拒绝缺失依赖、环、晚阶段依赖、重复步骤与未安装贡献接口。
- `RegistrationPoint`：把命名钩子接口绑定到领域 API。
- `RegistrationModule.define`：声明步骤依赖与贡献后的冻结动作。
- `TopoContentBootstrap`：领域模块组合清单。

## 为什么这样设计

- 显式依赖计划让钩子顺序可验证、可复现，不靠组合清单的书写顺序。
- 每个钩子先遍历全部插件，再冻结该阶段的表，避免后注册的插件看到半冻结状态。
- 内容声明与游戏对象绑定分离：声明期只排 Entry/Supplier，RegistryLib 绑定后才可 `.get()`。
- 冻结边界把“声明错误”提前到启动期，而不是运行期随机失败。

## 与其他模块的联动

- 所有领域通过 `RegistrationDomain` + `RegistrationModule` + `RegistrationPoint` 接入；新增领域不改引擎。
- 材料写入窗口横跨 `registerMaterials` 与 `registerMaterialContributions`；内核在贡献完成后关闭写入再匹配形态。
- 具体配方在 `DERIVED`，语言在 `LANGUAGE`，运行时绑定在 common setup 的 deferred work。
- 宿主接线（事件、能力、网络）由 [host-wiring.md](host-wiring.md) 记录，不在领域模块内重复。

## 扩展规范

1. 定义领域 API 与 `RegistrationDomain` 工厂。
2. 定义继承 `TopoContribution` 的钩子接口。
3. 用 `RegistrationPoint` 绑定接口方法与 API。
4. 用 `RegistrationModule.define` 声明真实依赖与冻结动作。
5. 加入 `TopoContentBootstrap`；补模块 README 与公开契约（如适用）。
6. 同步 [注册管线时序](registration-timeline.md) 与 `PROJECT_KNOWLEDGE` 入口。

## 常见坑与防止

- 构造期写领域表：插件表锁定不等于表已冻结，只在对应钩子内声明。
- 用 `build()` 当作冻结点：材料写入截止在贡献阶段之后；其他表按钩子冻结。
- 依赖同阶段其他插件先执行：排序只为可复现，不是 API 契约。
- 在 `resolveDependencies` 读持久化状态：那里只做结构校验。
- 静态字段不自动初始化：每个声明类都要在所属钩子内显式 `init()`。

## 文档

- [内部包与扩展边界](api-packages.md)
- [宿主接线](host-wiring.md)
- [注册管线时序](registration-timeline.md)
- [新增注册领域](registration-domain-extension.md)
- 下游视角见 [`docs-content/foundation/lifecycle.md`](../../docs-content/foundation/lifecycle.md)。
