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
