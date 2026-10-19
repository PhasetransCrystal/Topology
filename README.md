# Topology

面向 [NeoForge](https://neoforged.net/) 的 **Minecraft 基础库 Mod**。为下游模组提供机器、材料、管道、配方、矿石生成等基础设施，**自身不添加任何玩法内容**——你声明"做什么"，框架负责"怎么做"。

## 协议与来源

本项目基于 [Odyssey Industrial](https://github.com/GregTech-Odyssey/OdysseyIndustrial)。

Topology 由 **PhasetransCrystal** 基于 Odyssey Industrial 重构为纯 API 库，并已获得原作者授权，允许改造与商用使用；作为授权条件，必须持续保留并传递原作者的署名。

### 许可证

本项目以 **Apache License 2.0** 许可，允许商用与再分发，可作为下游项目的内嵌包使用。完整协议文本见 [`LICENSE`](LICENSE)，署名信息见 [`NOTICE`](NOTICE)。

- **署名**：依据 Apache-2.0 第 4(d) 条，任何再分发、衍生作品均须保留 [`NOTICE`](NOTICE) 中的原作者署名与来源声明。
- 本项目已获原作者授权，可改造并用于开源 / 商用项目。
- 本项目的 GUI 纹理为作者自制，与上游项目无关，不包含上游的第三方纹理资产。
- 本项目与上游原作者无隶属或背书关系，除非另有说明。

## 功能概览

|     领域      | 能力                                                                                     |
|:-------------:|:-----------------------------------------------------------------------------------------|
|  **Machine**  | 机器定义、组件（trait）、资源端口（物品/流体/标量）、多方块蓝图匹配、渲染类型、声明式 UI |
| **Material**  | 材料 × 形态铸造物品/方块、材质数据、后处理器批量派生配方链                               |
|  **Recipe**   | 能力型配方 I/O、配方类型、生产线、搜索索引、导入/导出                                    |
|   **Pipe**    | 管道网络、分发策略、过滤器、网络勘测                                                     |
|    **Ore**    | 矿脉世界生成：形状 / 模式 / 冲突 / 暴露策略、JEI 预览                                    |
| **Equipment** | 装备种类 × 材料自动铸造工具 / 护甲                                                       |

所有内容通过 `TopoPlugin` 显式注册，官方内容与第三方内容走同一条路径。

## 技术栈

- Minecraft `26.1.2` / NeoForge `26.1.x`
- Java 25 + Kotlin 2.3.0
- RegistryLib、LDLib2、JEI、Jade、Applied Energistics 2、Guideme、opencc4j、Spark

## 构建

```bash
./gradlew build          # 构建
./gradlew runClient      # 客户端
./gradlew runServer      # 服务端
./gradlew runClientData  # 数据生成
```

`runClient` 和 `runServer` 额外加载本地开发 Mod **TopoDemo**（ID：`topodemo`）。
其 Java / Kotlin 代码放在 `src/demo/java/` / `src/demo/kotlin/`，资源放在 `src/demo/resources/`。
该源码集可访问 Topo API；后续内容插件在 TopoDemo 构造期通过 `TopoPlugins.register` 登记。
Topo 的数据生成和单元测试不加载它，发布的 jar、sources jar 和 Maven 产物也不包含它。
可用 `./gradlew demoClasses` 单独检查开发 Mod 的编译与资源处理。

### 测试

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

## 集成

```gradle
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

```java
TopoPlugins.register(new MyModPlugin()); // 实现 TopoPlugin，注册你的全部内容
```

详见 [Getting Started](docs-content/getting-started.md)。

## 文档

完整文档（中文）见 [`docs-content/`](docs-content/)，在线文档见 [Topology Wiki](https://topology.ptcrys.net)。

## 相关链接

- [Ptcrys](https://ptcrys.net)
- [Maven](https://maven.ptcrys.net)
- [PhasetransCrystal](https://github.com/PhasetransCrystal)
