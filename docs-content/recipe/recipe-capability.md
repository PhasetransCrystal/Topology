---
sidebar_position: 2
---

# RecipeCapability

面向需要接入自定义配方资源的模组开发者。配方系统把「机器资源类型」与「配方 I/O 能力」配对。添加一种新资源（物品、流体、能量、魔力……）=
继承 `RecipeCapability` + 注册资源类型。库不内置任何资源集成。

本页是 [Recipe](../recipe.md) 模块的子页——概念与页面导航见 [Recipe](../recipe.md)。

## 基类契约

```java
public abstract class RecipeCapability<I, O> {

    protected RecipeCapability(
            Identifier id,
            Class<I> inputType, Class<O> outputType,
            Codec<I> inputCodec, Codec<O> outputCodec,
            StreamCodec<RegistryFriendlyByteBuf, I> inputStreamCodec,
            StreamCodec<RegistryFriendlyByteBuf, O> outputStreamCodec);

    // 必须实现：
    public abstract MachineResourceType<? extends Resource> resourceType();
    public abstract boolean matchInput(@Nullable MachineBlockEntity machine, List<I> contents);
    public abstract boolean matchOutput(@Nullable MachineBlockEntity machine, List<O> contents);
    public abstract boolean handleInputChecked(@Nullable MachineBlockEntity machine, List<I> contents,
            Transaction transaction);   // 事务性：失败必须回滚
    public abstract boolean handleOutputChecked(@Nullable MachineBlockEntity machine, List<O> contents,
            Transaction transaction);
    public abstract I scaleInput(I content, double factor);
    public abstract I scaleInputForParallel(I content, long factor);
    public abstract O scaleOutput(O content, double factor);
    public abstract O scaleOutputForParallel(O content, long factor);
    public abstract boolean hasAnyContent(@Nullable MachineBlockEntity machine);

    // 铸造权（protected）——子类公开自己的 in()/out() 包装：
    protected final RecipeInputUse<I> inputUse(I content);
    protected final RecipeOutputUse<O> outputUse(O content);

    // 可选覆盖：maxParallelByInputs / matchOutputAfterInputs / supportsDirectTickIo /
    // bindDirectTickIo / indexKeys / inputAmount / extractMachineKeys / contributesIndexKeys /
    // importConflictKeys / inputIdentity / outputIdentity ...
}
```

## 两类形态

- **标量型**：`I = O = Long`，总量数字（能量、热量、魔力）。Codec 用 `Codec.LONG`。
- **槽位型**：继承 `SlottedRecipeCapability<I, O, R>`，配方里能数出格子（物品、流体）。额外实现：

```java
public abstract int countPreviewSlots(ResourcePortMetadata port);       // 端口折成几个预览槽
public abstract UIElement createPreviewInputSlotWidget(@Nullable I content);
public abstract UIElement createPreviewOutputSlotWidget(@Nullable O content);
public abstract @NonNull UIElement createLiveSlotWidget(ResourcePort<?, R> storage, int slot);
```

:::caution
同一资源类型最多对应一个槽位型 capability——`RecipeCapabilities.freeze()` 会拒绝多个槽位型能力共享资源类型；
capability id 是独立的配方 I/O 标识，不要求等于资源类型 id。
:::

## 示例：组合资源类型与配方能力

下面的示例将资源类型、能力、显示名和图标放在一个初始化方法中。具体组织方式由模组决定；
机器端口和配方能力必须引用同一个资源类型。示例省略了能力方法和存储工厂的实现：

```java
/** 标量型能力：I = O = Long。 */
public final class ManaRecipeCapability extends RecipeCapability<Long, Long> {

    private final MachineResourceType<ManaResource> resourceType;

    public ManaRecipeCapability(Identifier id, MachineResourceType<ManaResource> resourceType) {
        super(id,
                Long.class, Long.class,
                Codec.LONG, Codec.LONG,
                AMOUNT_STREAM_CODEC, AMOUNT_STREAM_CODEC);
        this.resourceType = resourceType;
    }

    @Override
    public MachineResourceType<ManaResource> resourceType() { return resourceType; }

    // 铸造包装（基类 inputUse/outputUse 是 protected）：
    public RecipeInputUse<Long> in(long amount) { return inputUse(amount); }
    public RecipeOutputUse<Long> out(long amount) { return outputUse(amount); }

    // ……matchInput / matchOutput / handleInputChecked / handleOutputChecked /
    //    scaleInput / scaleInputForParallel / scaleOutput / scaleOutputForParallel /
    //    hasAnyContent 的完整实现（见基类契约）
}
```

```java
/** 页面示例用的集成记录；实际项目可替换为自己的类型。 */
private record ManaIntegration(MachineResourceType<ManaResource> resourceType,
        ManaRecipeCapability recipeCapability) {}

/** 示例：在 registerRecipeFoundation 期间调用。 */
private static ManaIntegration mana() {
    MachineResourceType<ManaResource> resourceType = MACHINE.resourceTypeWithDataField(
            "mana", ManaResource.class, /* capability */ null, /* dataFieldFactory */);
    ManaRecipeCapability recipeCapability = RECIPE.capability(
            new ManaRecipeCapability(RECIPE.id("mana"), resourceType));
    // 名称与图标同点绑定——不留到后续阶段：
    MACHINE.bindResourceName(resourceType, "Mana", "魔力");
    MachineUiIcons.register(resourceType, () -> icon);
    return new ManaIntegration(resourceType, recipeCapability);
}
```

## 注册

在 `registerRecipeFoundation` 中调用上述 `mana()`，并保存返回的资源类型和能力句柄。
方法内部已调用 `recipe.capability(...)`；不要再注册同一能力。

## 配方里使用

```java
// 构建配方：
type.recipe("spell")
        .input(manaCapability.in(100L))          // 启动消耗
        .tickInput(manaCapability.in(25L))       // 每 tick 消耗
        .output(itemCapability.out(RESULT, 1))
        .duration(80)
        .buildRecipe();
```

构建器完整链见 [RecipeType](recipe-type.md)。

## API 速查

| 类/方法                                                                         | 用途                |
|---------------------------------------------------------------------------------|---------------------|
| `RecipeCapability(id, inType, outType, inCodec, outCodec, inStream, outStream)` | 基类构造            |
| `resourceType()`                                                                | 配对资源类型        |
| `matchInput` / `matchOutput`                                                    | 输入匹配 / 输出空间检查 |
| `handleInputChecked` / `handleOutputChecked`                                    | 事务性执行          |
| `scaleInput` / `scaleOutput`（+ ForParallel）                                   | 配方缩放            |
| `hasAnyContent(machine)`                                                        | 存量查询            |
| `importConflictKeys(content)` / `inputIdentity` / `outputIdentity`             | 导入冲突与配方去重  |
| `inputUse(content)` / `outputUse(content)`                                      | 铸造权（protected） |
| `recipe.capability(cap)`                                                        | 领域注册（钩子 1）  |
| `SlottedRecipeCapability.countPreviewSlots(port)`                               | 槽位预览            |
