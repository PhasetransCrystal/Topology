# MachineComponents 运行时容器

面向框架开发者。说明一个机器实例中的 `MachineComponents` 如何持有已挂载组件并提供依赖查找、服务发现和资源聚合。组件公开契约见 [`docs-content/machine/machine-component.md`](../../docs-content/machine/machine-component.md)。

## 容器职责

`MachineComponents` 是单个 `MachineBlockEntity` 的运行时组件集合。它保留 mount 顺序，并建立三类派生索引：

- `byIdMap`：`Identifier → MachineComponent`，供 `ComponentKey<T>` 查找。
- `byServiceMap`：`ServiceKey → mounted provider 列表`，列表按声明顺序排列。
- ticker 列表：只收集实现 `MachineTicker` 的组件，供宿主按顺序驱动。

容器同时持有资源视图、资源处理器缓存、配方搜索池路由及变更通知状态。它不拥有机器定义，也不替组件保存其业务状态。

机器定义保存的是可复用声明，运行时容器保存的是某个世界方块实例的组件对象。二者的生命周期和数据所有权不可混用。

## 初始化与依赖解析

`MachineBlockEntity.initializeMachineRuntime()` 按定义顺序调用各 mount 工厂。每次工厂得到一个含稳定 key、宿主及 `machine.data().scope(key)` 的 `ComponentContext`。所有对象创建后，容器再构造 ID/service/ticker 索引并按 mount 顺序调用一次 `resolveDependencies`；解析和宿主性能计数初始化成功后，容器才安装到 BlockEntity。重复 ID 在 builder 阶段已拒绝，运行时创建仍做防御性重复检查；key 的 ID 匹配但类型不匹配时 typed cast 会抛出异常。

依赖解析发生在全部兄弟组件创建之后，因此可以检查必需组件、缓存结构依赖或验证组合合法性。该阶段只处理结构关系，不读取需要先恢复的持久化状态。

## 宿主生命周期时序

单个机器实例的顺序如下：

1. `MachineDefinition` 创建 BlockEntity，并立即创建全部组件、建立索引、解析依赖，再把容器安装到宿主。此时组件对象和数据域已存在。
2. BlockEntity 的 `loadAdditional` 先调用机器自定义加载钩子，再恢复各组件持有的持久化数据域，最后从 update-tag 形式应用可选的客户端基线。此阶段不触发 `onMachineLoad`。
3. 方块实体进入世界时，`onLoad` 调用 `data.lifecycleEnterWorld()`，然后依次运行机器钩子 `onMachineLoaded()`、按 mount 顺序分发组件 `onMachineLoad()`、执行一次数据重算，最后按 mount 顺序将 ticker 接入调度器。
4. 保存时，`saveAdditional` 先调用机器自定义保存钩子，再保存持久化数据域。移除时先准备数据域，再解除 ticker 并按 mount 顺序通知组件 `onMachineUnload()`，最后再次准备数据域并运行机器移除钩子。移除不表示磁盘保存，也不等于方块被破坏。

因此，构造函数/依赖解析不能假设磁盘数据已恢复；需要读取持久化值的工作放在加载阶段之后的 `onMachineLoad` 或相应运行时回调。保存与移除是不同事件，避免把清理逻辑错误地绑到保存钩子。

## 查询方式

- `require(ComponentKey<T>)`：必需组件缺失或 key 对应对象类型不符时抛出 `IllegalStateException`，适用于必须满足的结构约束。
- `optional(ComponentKey<T>)`：缺少组件返回 `Optional.empty()`；若 ID 命中但类型不符仍失败，适用于真正可选的能力。
- `services(ServiceKey<A, C>, context)`：容器按 `ComponentKey.service(...)` 注册的 provider 顺序查询上下文，返回非空 `ServiceMatch` 列表；上下文相关值在每次查询时求值。
- `servicesCached(ServiceKey<A, Void>)`：仅缓存无上下文 provider 的稳定 API 对象。动态运行状态应放入返回的 view 对象，而非每次生成不同 view。
- `resources().transferSide().handler(type, side)` 与 `recipeSide().handler(type, role)`：按自动化侧向或配方角色查找处理器；`uiSide()` / `rawSide()` 分别供可见 UI 端口与原始端口枚举使用。
- ticker 与生命周期遍历由框架持有的受限运行时访问入口完成；公开容器查询不等同于取得宿主生命周期控制权。

无上下文服务在容器构造时建立缓存，provider 必须对 `Void` 上下文保持纯且稳定；携带方向等动态上下文的服务不能使用该缓存假设。`anyServiceMatches` 面向只需判断是否命中的高频路径，短路遍历且不分配结果列表。

## 为什么这样设计

- 容器把组合内的发现与聚合限制在一个机器实例，调用方不必维护平行组件列表。
- key 查询表达对特定组件的结构依赖；service 查询表达对能力的依赖，降低对具体实现类的耦合。
- 不可变的 mount 顺序和索引让生命周期分发与资源聚合可预测；缓存只覆盖结构固定的查询。
- 运行时访问令牌将框架生命周期控制与普通能力查询分开，避免任何容器使用方都能伪造框架调用。

## 与其他模块的联动

- `MachineBlockEntity` 创建容器并驱动 load/unload、保存和 tick 生命周期。
- `MachineResourceViews` 把 `MachineComponent` 的资源贡献汇成 `ResourceHandler` 视图，并维护缓存失效与变化通知。
- `RecipeLogic` 从容器资源视图获取输入/输出能力；search pool 路由把局部配方资源与全局资源分开。容器的资源内容版本用于失效“无配方”缓存，路由 revision 用于结构性路由变化；普通库存数量变化只增加内容版本，不重建路由表。
- 多方块控制器通过组件/服务契约协作，不应把结构状态塞进通用容器。
- 客户端渲染与 UI 从组件贡献中收集展示数据，容器不直接承担渲染策略。

## 常见坑

- 把 `optional` 当作宽松的 `require`：必需依赖缺失应在解析阶段明确失败，避免运行到热路径才空值分支。
- 在 `resolveDependencies` 读取磁盘恢复值：该阶段负责组合结构；依赖持久化状态的工作放到数据加载完成后的生命周期。
- 在服务中暴露具体组件类型：优先定义窄 service 接口，使调用方只依赖所需能力。
- 修改资源处理器后遗漏失效/通知：通过资源视图和组件既有变更入口发布变化，避免陈旧缓存或机器未唤醒。
- 把服务 key 当成组件 key：组件 key 定位一个实例，服务 key 定位一种 API 能力；多个组件可实现同一服务。
- 把 `MachineComponents` 当作通用依赖注入容器：它只服务于机器组件组合，不承担跨机器或全局服务定位。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/machine/component/MachineComponents.java`：组件集合、索引、服务和资源视图入口。
- `src/main/java/net/ptcrys/topo/api/machine/component/MachineComponent.java`：组件生命周期及能力钩子。
- `src/main/java/net/ptcrys/topo/api/machine/component/ServiceKey.java`、`ServiceBinding.java`：能力服务契约。
- `src/main/java/net/ptcrys/topo/api/machine/component/MachineResourceViews.java`：资源视图聚合。
- `src/main/java/net/ptcrys/topo/api/machine/MachineBlockEntity.java`：实例初始化与生命周期编排。
