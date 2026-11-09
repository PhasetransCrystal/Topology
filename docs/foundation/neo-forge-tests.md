# NeoForge 测试启动

面向框架开发者。本仓库的测试启动方式如下。

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
