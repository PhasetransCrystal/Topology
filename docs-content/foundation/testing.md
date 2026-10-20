---
sidebar_position: 9
---

# 测试与验证

```bash
./gradlew test --rerun spotlessCheck
./gradlew test --tests '*TopoPluginEngineIntegrationTest' --rerun
```

IDEA 可选择共享运行配置 `Tests (NeoForge via Gradle)`；使用测试方法旁的运行按钮时，
请在 Gradle 设置中将 `Run tests using` 设为 `Gradle`，并使用 Java 25。
Minecraft 类型需要 ModDevGradle 的 `neoForge.unitTest` 准备类路径、启动参数和 FML
初始化；普通 JVM 测试启动方式不能替代这一过程。若仍报类型加载错误，请保留完整异常
（包括最内层 `Caused by`）。报告位于 `build/reports/tests/test/index.html`，
游戏启动日志位于 `build/minecraft-junit/logs/latest.log`。

当前保留 JUnit 6，并通过 BOM 统一 Jupiter / Platform 版本。TestNG 不会触发 FML 的
JUnit Platform 启动监听器，不能直接替代现有集成测试。需要世界、方块和 tick 的测试可
另行采用 [NeoForge GameTest](https://docs.neoforged.net/docs/misc/gametest/)，但须迁移测试入口与断言。
加载配置参见 [ModDevGradle JUnit 文档](https://github.com/neoforged/ModDevGradle#unit-testing-with-junit)。


## 插件管线回归

编译与格式检查不会启动游戏：

```bash
./gradlew compileJava compileTestJava demoClasses spotlessCheck
```

运行测试会通过 NeoForge 加载实际 Mod；依照项目约定，由开发者手动执行并保留完整结果。
建议先运行插件测试，再运行全量测试：

```bash
./gradlew test --tests 'net.ptcrys.topo.api.api.plugin.internal.*' --rerun
./gradlew test --rerun spotlessCheck
```

测试覆盖：

- `RegistrationPlanTest`：乱序模块的确定性结果、缺失/环/跨阶段依赖、编译快照、未来领域的命名钩子、接口继承与组合、失败归因和未安装领域拒绝。
- `PluginLifecycleTest`：成功幂等、越序与重入拒绝、失败后终止、运行时冻结边界。
- `TopoPluginsOrderTest`：实际收集器的登记排列、重复身份拒绝、快照锁定及不可变性、跨命名空间排序与归属。
- `TopoPluginEngineOrderTest`：全部 5040 种内置模块排列的计划一致性，插件与领域模块交叉排列的完整阶段结果、冻结表及索引、失败结果、重复调用和引擎实例隔离。
- `TopoPluginEngineIntegrationTest`：真实宿主事件接线、重复身份校验、跨领域配方、翻译按归属导出、装备自动行为、插件运行时绑定与冻结。

ID 与资源引用的回归由 `OreFeatureKeysTest`、`RecipeNameTest`、`RecipeUiResourcesTest` 覆盖：
矿脉派生键与导入配方 ID 不发生路径碰撞，配方键保留注册表及所属命名空间，默认 UI 资源仍属于 Topo。

`LifecycleTestEntrypoint` 只位于测试源码集，提供测试插件及捕获 datagen 输出的 RegistryCore。
它与生产宿主一起走 NeoForge 生命周期；测试不靠手动调用引擎来代替首次加载。
实际客户端/服务端验证还应检查 TopoDemo 的材质形态、装备及加载日志；数据生成验证检查输出归属，
Topo 自身 datagen 不应夹带 TopoDemo 内容。不能用编译通过推断这些运行结果。
