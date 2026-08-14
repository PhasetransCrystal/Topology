---
sidebar_position: 5
---

# Connected Textures (CTM)

库提供一套连接纹理（CTM）blockstate-model codec，第三方方块可直接复用。

## 模型 codec 注册

三种内置 codec：`ConnectedTextureCasingModel`（机壳）、`ConnectedTextureMachineModel`（机器）、
`ConnectedTextureHatchModel`（舱口）。注册一次即可被 blockstate JSON 引用：

```java
// 客户端（RegisterBlockStateModels 事件）+ 数据生成引导共用同一注册清单：
CtmBlockStateModelCodecs.register((id, codec) -> event.register(id, codec));
```

库宿主 `@Mod("topo")` 已通过 `CtmClientInit.register(modEventBus)` 完成注册。**纯库嵌入**（不依赖 topo
宿主）时必须自己调用 `CtmClientInit.register(modEventBus)`。

## 方块侧：ConnectedTextureHost

方块实现 `ConnectedTextureHost` 后，与同族邻居接触面自动合并纹理：

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
`Machines.begin(...).block(...)` 覆盖时继承。

## 运行期激活状态

框架维护 `CTM_ACTIVE` 方块状态（`ConnectedTextureProperties.CTM_ACTIVE`），邻居变化时自动更新。
你的模型 blockstate JSON 以该属性分叉激活/未激活变体；放置/移除方块时无需手动维护。

## API 速查

| 类/方法                                                    | 用途                       |
|------------------------------------------------------------|----------------------------|
| `CtmBlockStateModelCodecs.register(registrar)`             | 注册全部 CTM codec         |
| `CtmClientInit.register(modEventBus)`                      | 客户端注册（纯库嵌入必调） |
| `ConnectedTextureHost.connectedTextureFamily()`            | 方块所属纹理族             |
| `ConnectedTextureFamily(id)`                               | 纹理族身份（同 id 连接）   |
| `ConnectedTextureProperties.CTM_ACTIVE`                    | 运行期激活状态方块属性     |
| `ConnectedTextureProperties.setActive(level, pos, active)` | 更新激活状态               |
