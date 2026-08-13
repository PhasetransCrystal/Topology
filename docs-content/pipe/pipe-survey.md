---
sidebar_position: 3
---

# Pipe Surveyor

管道网络勘测器——玩家手持勘测物品右键管道，锚定网络并测量吞吐。

本页是 [Pipe](../pipe.md) 模块的子页——概念与页面导航见 [Pipe](../pipe.md)。

## PipeSurveyTool — 标记接口

```java
public interface PipeSurveyTool {}
```

你的物品实现该接口即成为勘测器。客户端渲染器自动识别主/副手持有的勘测物品并绘制网络覆盖层
（`PipeSurveyClientRenderer`，默认宿主已注册）。

## PipeSurveyManager — 行为驱动

```java
PipeSurveyManager.activate(player, pos, range);   // 锚定勘测（pos = 管道节点，range 格范围）
PipeSurveyManager.selectPoint(player, pos);       // 设置端点 A（首次）/ B（第二次）
```

调查数据（`PipeSurveySnapshot`：锚点、双端点、6 通道上限 `MAX_LANES`）经
`PipeSurveyNetworking` 自动同步客户端。

## 接线

默认 `@Mod("topo")` 宿主已注册网络通道（`PipeSurveyNetworking.register`）与客户端渲染器
（`PipeSurveyClientRenderer.register`）。纯库嵌入需自行复制这两项——清单见
[Embedding](../embedding.md)。

管道注册与交互见 [Pipe](../pipe.md)。

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `PipeSurveyTool`                       | 勘测器标记接口                  |
| `PipeSurveyManager.activate(player, pos, range)` | 锚定勘测             |
| `PipeSurveyManager.selectPoint(player, pos)` | 端点 A/B 设置              |
| `PipeSurveyManager.MAX_LANES`          | 快照通道上限（6）               |
| `PipeSurveyNetworking.register(modEventBus)` | 网络通道（宿主已注册）   |
