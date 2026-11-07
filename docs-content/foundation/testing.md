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
实际客户端/服务端验证还应检查 TopoDemo 的材质形态、装备及加载日志。
`runClientData` 同时加载 Topo 与 TopoDemo；验证时应检查生成目录和打包归属，
Topo 发布包不应夹带 TopoDemo 内容。不能用编译通过推断这些运行结果。

## 勘测快照边界回归

由开发者手动运行协议用例：

```bash
./gradlew test --tests 'net.ptcrys.topo.api.pipe.survey.PipeSurveySnapshotTest' --rerun
```

`overlongLaneDoesNotShiftFollowingLaneOrCapacity` 构造一条 2049 节点路径和紧随其后的正常路径，
检查后续路径、容量及报文消费位置；旧实现会将第 2049 个坐标当成后续字段。
其余用例覆盖空帧、0/2048 节点路径、2048 节点表、全部路径超限、车道数边界、
负数/超限长度、数据不足及合法帧编码兼容。仅编译测试源码不代表这些断言已运行。

运行世界验收前先执行 `./gradlew runClientData`，为“不完整”提示和容量标签生成语言资源。
在测试世界比较普通网络与超过 2048 节点的 A/B 路径，确认超长路径省略、其他路径仍显示，
锚点上方出现“不完整”提示，B 端分别显示分析容量和已显示路径数。
全部路径被省略时也应显示容量；缩短路径后提示应随新快照消失。保留客户端与专服日志。

## 生成资源与发布包

发布 CI 在构建前运行客户端数据生成；代码 PR/push 由 Validate 工作流检查，文档 PR 由 Deploy Docs 构建。发布包内容（署名、语言资源、TopoDemo 隔离）仍需在本地检查。
本地首次构建或修改内容声明后，由开发者手动执行：

```bash
./gradlew runClientData
./gradlew build demoClasses
```

数据生成会写入 `src/generated/resources/<modid>/`；TopoDemo 的原版矿石替换还会写入
`src/generated/resources/topodemo/data/minecraft/worldgen/configured_feature/`，用于把原版矿石 target 指向 Topo 方块。
普通本地 `build` 不自动运行数据生成；
验证干净检出的发布流程时应使用独立目录，避免依赖已有生成文件。
