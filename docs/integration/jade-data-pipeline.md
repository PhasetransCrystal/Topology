# Jade 服务端数据与客户端面板

面向框架开发者。说明 Jade 集成怎样从机器组件和管道运行时收集服务端数据，并在客户端显示；下游组件能力契约见 [`docs-content/machine/machine-component.md`](../../docs-content/machine/machine-component.md)。

## 注册与数据流

`TopoJadePlugin` 分别注册 common-side 数据 provider 和 client-side component provider：机器 provider 读取 `MachineBlockEntity`，管道 provider 读取 `PipeBlock`；客户端组件只消费 provider 附加的 payload 并构建 tooltip 面板。

```text
机器/管道运行时状态
      │ server provider 读取并编码 CompoundTag
      ▼
Jade 同步 payload
      │ client component 解析并渲染
      ▼
tooltip 面板
```

该通路用于展示，不是资源 I/O 或业务状态写入入口。provider 不应为了显示而改变库存、调用分发策略或重建网络。

## 机器数据

`MachineDataProvider` 输出两类可选信息：

- 标量资源条通过 `ResourceBarProvider` service 由组件贡献名称、颜色、存量和容量。Jade 不识别具体端口实现，也不要求所有资源都变成槽位条。
- 性能信息来自机器已有的近期采样快照，包括宿主、组件累计时间和可选子项。读取时可激活限定 tick 数的性能监测；快照过期或为空时不发送面板数据。

因此资源条只读 service 视图，性能面板读取已记录快照；不要在 provider 里同步执行一次机器工作来“测量”性能。

## 管道数据

`PipeDataProvider` 从服务端维度运行时读取已完成 tick 的网络快照：节点角色、吞吐预算、各侧流量、提取策略摘要及网络统计。找不到节点记录、网络或节点索引时不发送 payload。客户端面板使用快照，而不是重新遍历网络或推导本 tick 的中间账本状态。

## 常见坑

- 把 provider 当作状态回调并写回机器/管道：它只应采集展示数据。
- 在 client component 直接读服务端运行时：只能使用已同步 payload。
- 在管道 tooltip 中自行遍历世界连接：provider 已提供网络运行时的已完成 tick 快照。
- 把所有机器资源都硬编码在 provider：新增资源条应由组件实现 `ResourceBarProvider`。
- 忽略采样新鲜度：性能面板应遵循机器的采样周期与允许年龄。

## 实现位置

- `integration/jade/TopoJadePlugin.java`：common/client provider 注册。
- `integration/jade/MachineDataProvider.java`、`MachineComponentProvider.java`：机器数据与面板。
- `integration/jade/ResourceBarProvider.java`：机器标量资源条窄契约。
- `integration/jade/PipeDataProvider.java`、`PipeComponentProvider.java`：管道快照与面板。
