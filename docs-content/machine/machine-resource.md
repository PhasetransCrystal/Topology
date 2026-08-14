---
sidebar_position: 4
---

# Machine Resource Ports

:::caution
库**不内置任何现成的物品/流体/能量端口**——端口是内容级组件。本页示例按生产级实现的结构组织：
元数据 record + 预置 key + 静态 mount 工厂 + 语义工厂门面。
:::

本页是 [Machine](../machine.md) 模块的子页——概念与页面导航见 [Machine](../machine.md)。

## 资源类型 — MachineResourceTypes

一种资源类型定义「机器能存取什么资源 + 如何持久化」。在 `registerRecipeFoundation`（或 `registerMachineFoundation`）声明：

```java
MachineResourceType<R> type = MachineResourceTypes.begin(
        id,                       // 资源类型 id（与配方能力 1:1）
        R.class,                  // Resource 类型 token
        /* @Nullable */ blockCapability,   // 原版方块能力（无则 null）
        dataFieldFactory);        // ResourceDataFieldFactory<R>：如何为该资源建存储字段
```

标量型资源（能量/热量等）可用现成工厂：`MachineResourceTypes.valueIoDataFieldFactory()`。
名称与图标在同一声明点绑定：`machine.bindResourceName(type, en, cn)` + `MachineUiIcons.register(type, icon)`。

## ResourcePort 泛型基类 — 端口组件

```java
public abstract class ResourcePort<S, R extends Resource> extends MachineComponent {

    protected ResourcePort(
            ComponentContext<? extends ResourcePort<S, R>> context,
            MachineResourceType<R> resourceType,
            StacksResourceHandler<S, R> handler,   // 槽位存储实现
            ResourcePortMetadata metadata);        // 端口几何 + PortAccess 策略（input/output/storage）

    // 玩家可配置侧面的端口自动获得 packed side-io 字段：
    public final boolean sideIoConfigurable();
    public final boolean setSideIo(Direction localSide, AutomationIo mode);
    public final boolean cycleSideIo(Direction localSide);
    public final void resetSideIo();
}
```

基类已 final 实现 `transferHandler` / `recipeResourceHandler`（`protected final`，子类不可覆写）——
子类只需提供存储与元数据，端口自动进入 `MachineComponents.resources()` 聚合视图。
元数据接口 `ResourcePortMetadata extends Attachment` 声明端口的几何、访问策略
（`PortAccess.input()/output()/storage()`，携带 `recipeIo`/`automationIo`）与可选资源过滤。

## 生产级结构：元数据 record + 预置 key + 静态 mount

端口类三件套——按此结构写你的 `MyPort`：

```java
/** ① 元数据 record：声明期几何 + 访问策略，实现 ResourcePortMetadata。 */
public record MyPortMetadata(
        int slots,
        PortAccess policy,
        @Nullable Predicate<Resource> resourceFilter)
        implements ResourcePortMetadata {

    public MyPortMetadata {
        if (slots <= 0) throw new IllegalArgumentException("port slots must be > 0");
        Objects.requireNonNull(policy, "resource port policy");
    }

    public MyPortMetadata(int slots, PortAccess policy) { this(slots, policy, null); }

    @Override
    public MachineResourceType<MyResource> resourceType() { return MyResources.MY.resourceType(); }
}
```

```java
/** ② 端口组件：预置 key + 静态 mount 工厂 + 便捷工厂。 */
public final class MyPort extends ResourcePort<MyStack, MyResource> {

    // 预置 key 按槽位编号（复用同一 key 挂到不同机器不冲突）：
    public static final ComponentKey<MyPort> INPUT_1 = ComponentKey.id("my_input_1", MyPort.class);

    private MyPort(ComponentContext<MyPort> context, MyPortMetadata metadata) {
        super(context, MyResources.MY.resourceType(), /* handler */, metadata);
    }

    /** mount 工厂：metadata 作为 Attachment 挂到 ComponentMount 上。 */
    public static PartRoleMount<MyPort> mount(ComponentKey<MyPort> key, int slots, PortAccess policy) {
        MyPortMetadata metadata = new MyPortMetadata(slots, policy);
        return new PartRoleMount<>(key.mount(context -> new MyPort(context, metadata), metadata));
    }

    /** 便捷工厂：常用角色一步到位。 */
    public static PartRoleMount<MyPort> input(ComponentKey<MyPort> key, int slots) {
        return mount(key, slots, PortAccess.input());
    }

    public static PartRoleMount<MyPort> output(ComponentKey<MyPort> key, int slots) {
        return mount(key, slots, PortAccess.output());
    }
}
```

```java
/** ③ 语义工厂门面（可选）：三轴正交声明——几何 × 访问 × 过滤。 */
public final class Ports {

    private Ports() {}

    public static PartRoleMount<MyPort> myIn(ComponentKey<MyPort> key, int slots) {
        return MyPort.input(key, slots);
    }

    public static PartRoleMount<MyPort> myFiltered(ComponentKey<MyPort> key, int slots,
            PortAccess access, @Nullable Predicate<Resource> filter) {
        return MyPort.mount(key, slots, access, filter);
    }
}
```

机器声明侧的用法：

```java
Machines.begin(id, registry)
        .component(Ports.myIn(MyPort.INPUT_1, 2))
        .build();
```

## PortAccess — 访问策略

`PortAccess` 是端口的完整访问策略载体：配方角色、自动化模式、玩家交互、可配置侧面。

```java
PortAccess.input()                    // INPUT 配方角色 + INSERT 自动化 + 玩家自由
PortAccess.input(Direction first, Direction... more)   // 限定可传输面
PortAccess.output()                   // OUTPUT 配方角色 + EXTRACT
PortAccess.output(Direction first, Direction... more)
PortAccess.storage()                  // 双向存储（配方 I/O 均可）
// 链式：.withRecipeIo(...) / .withAutomationIo(...) / .withPlayerSlotAccess(...) / .withPlayerConfigurableSides()

boolean playerConfigurableSides();    // true = 玩家可逐面配置（ResourcePort 自动获得 side-io 字段）
```

`AutomationIo` 枚举：`NONE` / `INSERT` / `EXTRACT` / `BOTH`。

玩家可配置端口的逐面模式用 `ResourcePort.setSideIo(localSide, mode)` / `cycleSideIo(localSide)` /
`resetSideIo()` 操作（局部面，框架按方块 FACING 折叠成世界面）。运行期玩家配置存进 packed side-io 字段。

## RecipeSearchPoolId — 配方搜索池

端口通过 `ResourcePortMetadata.recipePoolIsolatable()` 决定是否加入机器的配方搜索池：
槽位型资源默认加入（输入输出共享池 id），全局标量资源（能量/热量）覆盖为 false（每个池都可见）。
自定义池 id 见 [RecipeType](../recipe/recipe-type.md)。

## 注册

```java
// registerMachineFoundation：声明资源类型（无原版能力时用 resourceType，默认 valueIo 存储工厂）
MachineResourceType<MyResource> myType = machine.resourceType("my_resource", MyResource.class, null);
machine.bindResourceName(myType, "My Resource", "我的资源");
MachineUiIcons.register(myType, /* icon */);

// registerMachines：挂载自定义端口
Machines.begin(id, registry)
        .component(MyPort.mount(MyPort.INPUT_1, 2, PortAccess.input()))
        .build();
```

与配方能力的配对见 [RecipeCapability](../recipe/recipe-capability.md)。

## API 速查

| 类/方法                                                                        | 用途                     |
|--------------------------------------------------------------------------------|--------------------------|
| `MachineResourceTypes.begin(id, type, capability, factory)`                    | 注册资源类型             |
| `MachineResourceTypes.valueIoDataFieldFactory()`                               | 标量型存储工厂           |
| `machine.resourceType(path, type, capability)`                                 | 领域入口（valueIo 默认） |
| `machine.resourceTypeWithDataField(path, type, capability, factory)`           | 自定义工厂               |
| `machine.bindResourceName(type, en, cn)`                                       | 资源显示名（同点绑定）   |
| `MachineUiIcons.register(type, icon)`                                          | 资源图标（同点绑定）     |
| `ResourcePort.setSideIo(side, mode)` / `.cycleSideIo(side)` / `.resetSideIo()` | 逐面配置                 |
| `PortAccess.input()` / `.output()` / `.storage()`                              | 访问策略工厂             |
| `ResourcePortMetadata.recipePoolIsolatable()`                                  | 配方搜索池隔离声明       |
