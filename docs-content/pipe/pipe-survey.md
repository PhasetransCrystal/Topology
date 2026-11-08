---
sidebar_position: 3
---

# Pipe Surveyor

面向需要提供管道网络勘测物品的模组开发者。本页说明标记接口、交互调用和显示范围。

本页是 [Pipe](../pipe.md) 模块的子页——概念与页面导航见 [Pipe](../pipe.md)。

## PipeSurveyTool — 标记接口

```java
public interface PipeSurveyTool {}
```

物品实现该接口后，客户端渲染器会识别主手或副手持有的物品并绘制已有勘测数据。
标记接口本身不处理右键；物品还须在服务端交互中调用下方的管理器方法。

## PipeSurveyManager — 行为驱动

```java
PipeSurveyManager.activate(player, pos, range);   // 锚定勘测（pos = 管道节点，range 格范围）
PipeSurveyManager.selectPoint(player, pos);       // 设置端点 A（首次）/ B（第二次）
```

例如，物品可在服务端普通右键时调用 `activate`，在潜行右键时调用 `selectPoint`；具体手势由物品实现决定。

调查数据（`PipeSurveySnapshot`：锚点、双端点、快照协议最多 16 条车道；服务端分析上限
`PipeSurveyManager.MAX_LANES` = 6）经
`PipeSurveyNetworking` 自动同步客户端。

每帧节点表最多展示 2048 个节点，每条 A/B 路径最多包含 2048 个节点。超长路径会整体省略，
不会裁断后冒充完整路径；其他合规路径仍会显示。节点表或路径超限时，锚点上方提示
“展示不完整：已省略超限节点或路径”。B 端浮签分别显示分析容量和实际显示路径数；
分析容量仍包含被省略路径的贡献，因此所有路径都被省略时也可能显示非零容量。

合法快照的字段顺序和编码保持不变。接收端会拒绝负数或超过协议上限的节点数、车道数及路径长度，
不会通过只读一部分坐标来兼容超限报文。新客户端无法修复旧服务端发出的非法超长报文，应同步更新两端。

## 接线

默认 `@Mod("topo")` 宿主已注册网络通道与客户端渲染器，内容模组无需重复接线。无宿主内嵌的支持范围见[集成宿主](../embedding.md)。

管道注册与交互见 [Pipe](../pipe.md)。

## API 速查

| 类/方法                                          | 用途                   |
|--------------------------------------------------|------------------------|
| `PipeSurveyTool`                                 | 勘测器标记接口         |
| `PipeSurveyManager.activate(player, pos, range)` | 锚定勘测               |
| `PipeSurveyManager.selectPoint(player, pos)`     | 端点 A/B 设置          |
| `PipeSurveyManager.MAX_LANES`                    | 服务端分析/渲染上限（6）；协议上限 16 |
| `PipeSurveyNetworking.register(modEventBus)`     | 网络通道（宿主已注册） |
