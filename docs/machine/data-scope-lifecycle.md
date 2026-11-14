# 机器数据域与字段生命周期

面向框架开发者。说明 `MachineDataScope`、组件 `DataScope` 与字段的所有权、模式转换、存档持久化和客户端同步。下游字段声明方法见 [`docs-content/machine/machine-component.md`](../../docs-content/machine/machine-component.md)。

## 所有权与 schema

每台 `MachineBlockEntity` 拥有一个 `MachineDataScope`。组件构造时通过组件 key 取得自己的顶层 `DataScope`；组件可在其中用 `group(key)` 创建嵌套 scope。字段归属某个 scope，key 在该 scope 内唯一。`MachineDataScope` 不定义玩法规则，只集中管理 schema、字段访问门控、序列化和同步。

字段必须在 schema 开放期间声明，并明确选择持久化策略；可同步字段还必须明确选择客户端同步策略。字段构建完成后策略不可变。`setLevel`、磁盘加载或进入世界会封闭 schema 并建立计算字段缓存，因此不能把字段注册放到 `onMachineLoad` 或之后。

持久化字段由 key 路径读写；客户端增量同步按声明遍历顺序编码槽位。因此，已发布字段的 key 和同步字段声明顺序都属于兼容边界：重命名 key 会影响旧存档读取，重排同步字段可能使旧/新客户端解释不同槽位。

## 字段策略与访问

- `persisted()` / `saveNone()` 决定字段是否进入机器完整存档快照。
- `syncNone()` / `syncToClientAtEndOfDirtyTick()` 决定是否进入机器数据客户端同步；它与持久化是独立策略轴。
- `computedEveryInternalTick(...)` 将字段设为只读派生值，由服务端在机器生命周期重算；直接 `set`、`inc` 或 `dec` 会失败。
- `valueField(DataKey<T>)`、资源 handler 字段和普通标量字段都注册在其所有者 scope。已有字段 setter 会自动标记变化；外部可变状态需要通过 `markPersistedStateChanged()` 通知持久化域。

业务字段读写要求机器处于可访问阶段。服务端 `SERVER_LIVE` 才是权威值；客户端在收到完整基线前为 `CLIENT_WAITING`，此阶段只能提供默认值，不能依赖机器数据字段构建需要真实状态的 UI。开菜单编辑/显示应使用 LDLib2 UI 同步与 RPC；机器数据 S2C 通道主要用于世界渲染等始终开启的镜像。

## 生命周期与传输

1. 组件构造期间创建 scope 和字段，机器处于 `BOOTSTRAP`。
2. BlockEntity 的 `loadAdditional` 调用组件自定义加载钩子，再应用持久化数据；应用期间抑制业务 dirty、配方失效等副作用。schema 随后封闭。可选 update-tag 基线也可在这一加载入口应用。
3. 方块实体进入世界后进入 `SERVER_LIVE` 或客户端等待/可用阶段；服务端在进入世界后重算 computed fields。旧版或不支持的存档格式会忽略该域数据并保留字段默认值。
4. 服务端字段变化时，持久化代数进入待保存状态；`MachineDataPersistCoordinator` 按 chunk 汇总待确认的完整快照，并在 chunk 保存事件确认后清除对应代数。移除宿主时会先冲刷待处理持久化标记。
5. 同步字段变更将机器加入 `MachineDataSyncBatcher`。服务端 tick 末按追踪玩家合并增量包；新追踪客户端依赖完整 update-tag 基线，而不是历史增量。

存档持久化和客户端同步是两条独立通道：chunk NBT 保存全量持久化字段；客户端 update-tag 建立同步基线，后续自定义 S2C payload 只发 dirty 字段。

## 为什么这样设计

- 机器宿主统一管理字段策略和生命周期，组件只声明自己拥有的数据，不需要分别接入 NBT、同步或 chunk dirty 流程。
- schema 一次封闭后，序列化和同步可以使用稳定字段集合；加载期间抑制变更副作用，避免恢复存档被误识别为新的玩法操作。
- chunk 保存确认与机器字段 dirty 分开跟踪，避免把一次写入等同于已落盘，也避免每次 setter 都扫描全部字段。
- 完整客户端基线和增量同步分离，使新追踪客户端不依赖错过的历史 delta。

## 常见坑

- 在 `resolveDependencies` 里读取字段：组件构造/schema 期间数据尚不可供业务读取。
- 把 `syncToClientAtEndOfDirtyTick()` 当成菜单同步：菜单应使用 UI binding/RPC。
- 修改字段 key 或声明顺序却忽略存档/网络兼容：发布后的 schema 变更需要兼容处理。
- 外部集合、对象在字段之外变化却不调用 `markPersistedStateChanged()`：框架无法据此标脏。
- 在客户端基线到达前读取真实值：等待阶段字段可能只有默认值。
- 把 `onMachineUnload` 当成持久化钩子：卸载和实际 chunk 快照保存是不同事件。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/machine/data/MachineDataScope.java`：schema、阶段门控、持久化/同步入口与计算字段。
- `src/main/java/net/ptcrys/topo/api/machine/data/DataScope.java`、`DataField.java`、`DataFieldBuilder.java`：scope、字段与不可变策略。
- `src/main/java/net/ptcrys/topo/api/machine/data/MachineDataPersistCoordinator.java`：按 chunk 协调持久化 epoch。
- `src/main/java/net/ptcrys/topo/api/machine/data/MachineDataSyncBatcher.java`、`network/`：客户端增量批次与 payload。
- `src/main/java/net/ptcrys/topo/api/machine/MachineBlockEntity.java`：宿主回调顺序。
