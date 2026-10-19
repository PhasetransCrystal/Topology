# Topo 作为依赖库的加载约定

适用范围：仓库当前实现的默认 `topo` 宿主模式；不代表完全移除 topo Mod 的嵌入模式受支持。

- 依赖方在 Mod 构造器中调用 `TopoPlugins.register`；只有宿主调用一次
  `TopoPluginEngine.register(modEventBus)`。领域 API 创建一次并保存在 Plugin 字段中。
- 首个 `RegisterEvent` 的 HIGHEST 阶段锁定插件表并运行完整内容管线，RegistryLib 在 LOW
  消费排队条目。内容声明时传 Entry / Supplier，不能提前读取未绑定的游戏条目。
- 每个钩子遍历全部插件后再冻结对应表。官方 API 插件优先；其他插件按注册处的 `after`
  拓扑排序，同等可执行的插件按注册 ID 字典序执行。默认 ID 为完整类名；可使用
  `TopoPlugins.register(id, plugin, after...)` 显式指定 ID 和前置插件，不增加 Plugin 接口方法。
- 运行时绑定沿用 common / sided setup deferred work。共享 tooltip 和物品行为表
  由引擎在 load complete 冻结，内容模块不能自行冻结。插件排序只控制内容钩子，
  不控制不同 Mod 的事件任务顺序，运行时绑定不能依赖隐含的跨 Mod 执行顺序。
- 引擎阶段只在成功后完成；越序、重入、缺失插件依赖和依赖环会报错。内容修改失败后
  保留原因并禁止继续启动，必须修正后重启，不能重试部分冻结的注册表。
- 语言键由所属 `LangDomainRegistration` 保存并通过其 RegistryCore 导出。新管道使用
  `machine.pipe(path)` 保留插件归属；旧 `Pipes.register(path)` 仍属于 topo。
- 共享机器 BE 仍属于 `Topology.REGISTRY`，完全嵌入还需要额外适配共享条目和客户端订阅。

- 引擎分为登记排序（`TopoPlugins`）、阶段状态（`PluginLifecycle`）、事件调度
  （`TopoPluginEngine`）、领域声明与冻结（`TopoContentBootstrap`）。新增领域先确定
  阶段依赖与冻结责任；当前不开放任意第三方步骤插入，也不支持热重载或多宿主。

实现来源：`Topology.java`、`TopoPluginEngine.java`、`PluginLifecycle.java`、`TopoPlugins.java`、`TopoContentBootstrap.java`、
`LangDomainRegistration.java`、`Pipes.java`；详细接入与迁移见 `docs-content/foundation/lifecycle.md`。

验证入口：`./gradlew test spotlessCheck`。NeoForge JUnit 在执行测试方法前完成 Mod 加载；
`LifecycleTestEntrypoint` 是仅存在于测试源码的 Mod 入口，用于在真实构造期登记回归测试插件，
不能在测试方法中重置或绕过已冻结的全局表。

本地开发 Mod `TopoDemo`（`topodemo`）使用独立 `demo` 源码集，只加入 client / server
运行配置。数据生成与 NeoForge 单元测试显式仅加载 Topo 源码 Mod；main 的 jar、sources jar
和 Maven 发布不包含 demo 输出。开发代码和资源位于 `src/demo/`，编译入口为 `demoClasses`。
配置来源：`gradle/scripts/moddevgradle.gradle`、`resources.gradle` 与 demo 的 Mod 元数据。

TopoDemo 的目录组织遵循同级 BreakdownKernel 项目：Mod 入口在根包，扩展实现按领域放入
`api/`，可复用实现放在对应领域的 `common/`，内容声明和内容 Plugin 放在 `data/`。
Plugin 只负责接线和生命周期转发；不要将具体策略、内容和运行时绑定集中到 Plugin 或根包。
来源：用户对 TopoDemo 目录及 Plugin 职责的明确要求；参考 `../BreakdownKernel/src/main/java/net/ptcrys/breakdown/`。

测试环境边界（NeoForge 26.1.2.107 / junit-fml 11.0.15）：FML 通过 JUnit Platform
`LauncherSessionListener` 初始化 Mod。该版本使用 Platform 6 的 `support.store.Namespace`，
不能照搬旧示例降级到 JUnit 5。JUnit 组件由 `dependencies.gradle` 的 6.0.1 BOM 对齐。
IDEA 共享配置 `.run/Tests.run.xml` 通过 Gradle 运行，Minecraft 类型测试应保留
`neoForge.unitTest.enable()` 生成的环境。TestNG 不触发此监听器；GameTest 迁移需要独立
测试入口，不能只替换依赖。来源：本地 junit-fml jar 的服务声明及 JUnitService 字节码、
[ModDevGradle 文档](https://github.com/neoforged/ModDevGradle#unit-testing-with-junit)、
[Namespace API](https://docs.junit.org/6.0.1/api/org.junit.platform.engine/org/junit/platform/engine/support/store/Namespace.html)。
