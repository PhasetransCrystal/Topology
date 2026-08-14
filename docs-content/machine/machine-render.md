---
sidebar_position: 3
---

# MachineRenderComponent

渲染 trait 与逻辑组件分离：`MachineRenderComponent` 定义客户端渲染状态，`MachineRenderState` 承载每帧快照。
**无需注册渲染器**——框架的单例 `MachineBlockEntityRenderer` 自动发现所有渲染组件。

本页是 [Machine](../machine.md) 模块的子页——概念与页面导航见 [Machine](../machine.md)。

## trait — 数据源（服务端计算 + 同步）

```java
public abstract class MachineRenderComponent<S extends MachineRenderState<?>> extends MachineTicker {

    // renderKey 预绑定渲染发现服务：
    protected static <T extends MachineRenderComponent<?>> ComponentKey<T> renderKey(String path, Class<T> type);

    protected void resolveRenderDependencies(MachineComponents traits) {}  // 对应 resolveDependencies（sealed）

    public abstract S createRenderState();   // 客户端专用：trait→state 直接配对，无注册表
}
```

```java
public static final ComponentKey<ItemCountRender> KEY = renderKey("item_count", ItemCountRender.class);

private final DataInt count = data().intField("count", 0)
        .computedEveryInternalTick(() -> computeTotal())   // 渲染字段必须计算型
        .saveNone()
        .syncToClientAtEndOfDirtyTick()                    // 且必须客户端同步
        .done();

@Override
public ItemCountRenderState createRenderState() {
    return new ItemCountRenderState(this);
}
```

## state — 客户端快照

```java
public abstract class MachineRenderState<T extends MachineRenderComponent<?>> extends BlockEntityRenderState {
    protected MachineRenderState(T trait);
    protected final T trait();
    protected final IntValue bind(DataInt field);   // 只接受 DataField，快照在提交前一次性完成
    protected abstract void submit(PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState);
}
```

```java
public final class ItemCountRenderState extends MachineRenderState<ItemCountRender> {
    private final IntValue count;

    public ItemCountRenderState(ItemCountRender trait) {
        super(trait);
        this.count = bind(trait.count());
    }

    @Override
    protected void submit(PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        // 每帧用 count.get() 绘制
    }
}
```

:::tip
trait 放 common 包，state 放 client 包。渲染只读 `DataField` 快照，不直接触碰组件。
:::

## 自定义方块渲染类型 — MachineBlockRenderType

`.render(...)` 接受的不只是内置类型——你可以注册自己的机器方块渲染类型（如透明外壳、动态覆盖层）。
类型是 KHS 对：`MachineBlockRenderType<D>`（句柄 + 数据载体）配 `MachineBlockRenderStrategy<D>`（行为）。

```java
public abstract class MachineBlockRenderType<D> {
    protected MachineBlockRenderType(Identifier id);
    protected final MachineBlockRenderUse<D> use(D data);   // 挂到 builder 的渲染用途
}

public interface MachineBlockRenderStrategy<D> {
    void validate(D data);                                  // 注册/挂载期校验渲染数据
    void applyBlockModel(BlockBuilder<? extends MachineBlock, RegistryCore> builder,
            D data, MachineDefinition definition);          // 数据生成期应用方块模型
}
```

```java
public final class HolographicRenderType extends MachineBlockRenderType<HolographicRenderType.Data> {

    public HolographicRenderType(Identifier id) { super(id); }

    public MachineBlockRenderUse<Data> holographic(/* 参数 */) {
        return use(new Data(/* 参数 */));
    }

    public static final class Strategy implements MachineBlockRenderStrategy<Data> {
        @Override public void validate(Data data) { /* 校验 */ }
        @Override public void applyBlockModel(BlockBuilder<?, ?> builder, Data data,
                MachineDefinition definition) { /* 生成 blockstate/model */ }
    }
}

// registerMachineFoundation（MachineRenderRegistry.freeze() 之前）：
HolographicRenderType holo = machine.renderType("holographic",
        new HolographicRenderType(plugin.machine().id("holographic")),
        new HolographicRenderType.Strategy());

// registerMachines：
Machines.begin(id, registry)
        .render(holo.holographic(/* 参数 */))
        .build();
```

:::caution
渲染类型是冻结表——必须在 `registerMachineFoundation`（钩子 10）注册，`registerMachines` 只能引用。
:::

## API 速查

| 类/方法                                                          | 用途                               |
|------------------------------------------------------------------|------------------------------------|
| `MachineRenderComponent.renderKey(path, type)`                   | 渲染 key（预绑定发现服务）         |
| `MachineRenderComponent.createRenderState()`                     | trait→state 配对（客户端）         |
| `MachineRenderComponent.resolveRenderDependencies(traits)`       | 结构校验（sealed）                 |
| `MachineRenderState.bind(field)`                                 | 绑定 DataField 快照（仅 IntValue） |
| `MachineRenderState.submit(pose, collector, camera)`             | 每帧绘制                           |
| `MachineBlockRenderType.use(data)`                               | 渲染用途（挂 builder）             |
| `MachineBlockRenderStrategy.validate(data)`                      | 渲染数据校验                       |
| `MachineBlockRenderStrategy.applyBlockModel(builder, data, def)` | 数据生成期模型                     |
| `machine.renderType(path, handle, strategy)`                     | 注册渲染类型（钩子 10）            |
