---
sidebar_position: 5
---

# Connected Textures (CTM)

面向需要为自定义方块接入连接纹理（CTM）的模组开发者。库提供可复用的 blockstate-model codec 和纹理族接口。

{/* 图片预留：同纹理族方块连接与不连接时的外观对比。 */}

## 模型 codec 注册

三种内置 codec：`ConnectedTextureCasingModel`（机壳）、`ConnectedTextureMachineModel`（机器）、
`ConnectedTextureHatchModel`（舱口）。在依赖 `topo` 宿主时可由 blockstate JSON 引用。宿主已经注册这些 codec；内容模组无需重复注册。

以下是注册接口的形态：

```java
// 客户端（RegisterBlockStateModels 事件）+ 数据生成引导共用同一注册清单：
CtmBlockStateModelCodecs.register((id, codec) -> event.register(id, codec));
```

无 `topo` 宿主的内嵌方式目前不受支持，见[集成宿主](../embedding.md)。

## 方块侧：ConnectedTextureHost

方块实现 `ConnectedTextureHost` 并使用兼容的连接纹理模型后，模型可按相邻方块的纹理族决定连接：

```java
public interface ConnectedTextureHost {
    ConnectedTextureFamily connectedTextureFamily();                    // 本方块所属纹理族
    default @Nullable ConnectedTextureFamily connectedTextureFamily(BlockState state) {
        return connectedTextureFamily();
    }
}
```

`ConnectedTextureFamily` 是 `record(Identifier id)`——同 id 的方块互相连接。

库提供两个现成基类：`ConnectedTextureOrientedMachineBlock`（朝向机器方块）与
`ConnectedTextureFormedMachineBlock`（成型多方块方块），均实现该接口，可直接在
`machine.machine(...).block(...)` 覆盖时继承。

## 运行期激活状态

支持该属性的多方块成员使用 `CTM_ACTIVE`（`ConnectedTextureProperties.CTM_ACTIVE`）区分激活状态；多方块控制器在成员接入或退出时更新它。
自定义方块若需要相同状态，应在方块状态中声明该属性并安排合适的更新时机；仅实现 `ConnectedTextureHost` 不会自动添加或维护此属性。模型的 blockstate JSON 可按该属性选择激活或未激活变体。

## API 速查

| 类/方法                                                    | 用途                       |
|------------------------------------------------------------|----------------------------|
| `CtmBlockStateModelCodecs.register(registrar)`             | 注册全部 CTM codec         |
| `CtmClientInit.register(modEventBus)`                      | 宿主客户端注册 |
| `ConnectedTextureHost.connectedTextureFamily()`            | 方块所属纹理族             |
| `ConnectedTextureFamily(id)`                               | 纹理族身份（同 id 连接）   |
| `ConnectedTextureProperties.CTM_ACTIVE`                    | 运行期激活状态方块属性     |
| `ConnectedTextureProperties.setActive(level, pos, active)` | 更新激活状态               |
