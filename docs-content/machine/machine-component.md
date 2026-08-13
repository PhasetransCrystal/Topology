---
sidebar_position: 2
---

# MachineComponent

机器的一切能力来自挂载在 `MachineDefinition` 上的组件。继承 `MachineComponent` 是自定义机器行为的主通道。

本页是 [Machine](../machine.md) 模块的子页——概念与页面导航见 [Machine](../machine.md)。

## ComponentKey — 稳定身份

每个组件实例必须有一个 key——它同时是重复检测、持久化和类型化查找的依据：

```java
// 第三方 Mod 必须用自己的插件命名空间铸造 key：
public static final ComponentKey<MyComponent> KEY =
        ComponentKey.of(MyModPlugin.INSTANCE.machine().id("my_component"), MyComponent.class);

// 挂载入口（声明时挂在 Machines.begin(...).component(...) 上）：
public static ComponentMount<MyComponent> mount() {
    return KEY.mount(MyComponent::new);
}
```

:::caution
`ComponentKey.id(path, Class)` 是 API 宿主保留的捷径（铸造 `topo:*` 命名空间）。第三方用
`ComponentKey.of(Identifier, Class)`。key id 会持久化进存档，**发布后不可改名**。
:::

## 生命周期

```
构造（super(context) 拿身份 + DataScope）
  → resolveDependencies(MachineComponents)   // 只做结构校验，禁止读持久化状态
  → onMachineLoad()                          // 进入世界：此时才能读数据
  → （每 tick，若实现 MachineTicker）
  → onMachineUnload()
  → onMachineDestroyed(level, pos, state)    // 破坏时
```

```java
public abstract class MachineComponent {
    protected MachineComponent(ComponentContext<? extends MachineComponent> context);
    protected final MachineBlockEntity machine();
    protected final DataScope data();

    public void resolveDependencies(MachineComponents traits) {}     // 兄弟组件查找：
                                                                     // traits.require(key) / traits.optional(key)
    public void collectMachineUi(MachineUiContribution contribution) {}  // mainPage / leftPanel / rightPanel / bottomStrip
    protected <R extends Resource> @Nullable ResourceHandler<R> transferHandler(
            MachineResourceType<R> resourceType, @Nullable Direction side);      // 自动化 I/O（管道、料斗）
    protected <R extends Resource> @Nullable ResourceHandler<R> recipeResourceHandler(
            MachineResourceType<R> resourceType, RecipeRole recipeIo);           // 配方 I/O（RecipeLogic 走这里）
    public RecipeSearchPoolId recipeSearchPoolId();                              // 默认 DEFAULT
    public void onMachineLoad() {}
    public void onMachineUnload() {}
    public void onMachineDestroyed(ServerLevel level, BlockPos pos, BlockState state) {}
}
```

覆写 `transferHandler` / `recipeResourceHandler` 后，`MachineComponents.resources()` 会聚合你的贡献——
`RecipeLogic`、管道连接、侧配置 UI 自动可见，无需额外接线。

## MachineTicker — 参与 tick

基类**不带** tick。需要每 tick 逻辑时继承 `MachineTicker`（框架自动识别并注册进 TickHub）：

```java
public abstract class MachineTicker extends MachineComponent implements TickHook {
    public TickKind kind() { return TickKind.SYNC; }   // 只能 SYNC——attachToHub 对 ASYNC 抛异常
    public int tickInterval() { return 1; }            // 20 = 每秒一次
    public abstract void tick(long gameTime, TickHandle handle);
}
```

## DataField — 持久化 & 同步

字段在构造函数里声明，声明顺序 = 网络同步槽位顺序（**必须稳定**）。两根轴都必填：

```java
// persist 轴：.persisted() 或 .saveNone()
// sync 轴：  .syncNone() 或 .syncToClientAtEndOfDirtyTick()
// 计算字段： .computedEveryInternalTick(Supplier) —— 只读，set() 抛异常

private final DataInt buffLevel = data().intField("buff_level", 0)
        .persisted()
        .syncToClientAtEndOfDirtyTick()
        .done();
```

可用类型：`intField / longField / booleanField / stringField / floatField / doubleField / enumField /
resourceKey / valueIoField / itemResourceHandler / fluidResourceHandler`。

:::caution
`syncToClientAtEndOfDirtyTick` 用于世界渲染（非菜单场景）。开菜单的 UI 走 LDLib2 自己的同步，不要用机器数据同步。
:::

## 服务 — capability 式绑定

需要「按需发现」而非 1:1 类型查找时，把组件绑到服务键：

```java
public static final ComponentKey<MyComponent> KEY =
        ComponentKey.of(/* id */, MyComponent.class)
                .service(MyService.KEY, (trait, unused) -> trait);

// 使用方（在 resolveDependencies 中）：
List<ServiceMatch<MyService>> matches = traits.services(MyService.KEY, null);
```

## 挂载到机器

```java
Machines.begin(id, registry)
        .displayName("My Machine", "我的机器")
        .component(MyComponent.mount())
        .build();
```

同一 key 挂两次会在声明期直接失败。声明式 UI 与注册细节见 [Machine](../machine.md)。

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `ComponentKey.of(id, type)`            | 第三方组件 key（发布后不可改名）|
| `ComponentKey.id(path, type)`          | 宿主保留捷径（topo 命名空间）   |
| `key.mount(factory)`                   | 组件挂载工厂 → `ComponentMount` |
| `key.service(serviceKey, provider)`    | capability 式服务绑定           |
| `MachineComponent.resolveDependencies(traits)` | 结构校验（禁读持久化） |
| `MachineComponent.collectMachineUi(contribution)` | 面板贡献              |
| `MachineComponent.transferHandler(type, side)` | 自动化 I/O 面            |
| `MachineComponent.recipeResourceHandler(type, io)` | 配方 I/O 面           |
| `MachineComponents.require(key)` / `.optional(key)` | 兄弟组件查找       |
| `MachineComponents.services(key, context)` | 服务发现                     |
| `MachineComponents.resources()`        | 资源视图聚合                    |
| `MachineTicker.kind()` / `.tickInterval()` / `.tick(time, handle)` | tick 通道 |
| `DataScope.intField(name, initial)` 等 | 声明数据字段（两轴必填）        |
