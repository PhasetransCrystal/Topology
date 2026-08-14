---
sidebar_position: 2
---

# RecipeCapability

配方系统把「机器资源类型」与「配方 I/O 能力」配对。添加一种新资源（物品、流体、能量、魔力……）=
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
    // bindDirectTickIo / indexKeys / inputAmount / extractMachineKeys / contributesIndexKeys ...
}
```

## 两类形态

- **标量型**：`I = O = Long`，总量数字（能量、热量、魔力）。Codec 用 `Codec.LONG`。
- **槽位型**：继承 `SlottedRecipeCapability<I, O, R>`，配方里能数出格子（物品、流体）。额外实现：

```java
public abstract int countPreviewSlots(ResourcePortMetadata port);       // 端口折成几个预览槽
public abstract UIElement createPreviewInputSlotWidget(@Nullable I content);
public abstract UIElement createPreviewOutputSlotWidget(@Nullable O content);
```

:::caution
capability id 与资源类型 id **1:1**——`RecipeCapabilities.freeze()` 校验槽位型能力不得与另一能力共享资源类型。
:::

## 生产级结构：资源集成（类型 + 能力 + 名称 + 图标同点声明）

生产级实现把「资源类型 + 能力 + 显示名 + 图标」打包成一个**资源集成**在同一个声明点完成——
它们同生共死，拆开注册会留下半成品状态：

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
/** 资源集成：registerRecipeFoundation 里一次调用完成四件事。 */
private static BuiltinResourceIntegration<ManaResource, ManaRecipeCapability> mana() {
    MachineResourceType<ManaResource> resourceType = MACHINE.resourceTypeWithDataField(
            "mana", ManaResource.class, /* capability */ null, /* dataFieldFactory */);
    ManaRecipeCapability recipeCapability = RECIPE.capability(
            new ManaRecipeCapability(RECIPE.id("mana"), resourceType));
    // 名称与图标同点绑定——不留到后续阶段：
    MACHINE.bindResourceName(resourceType, "Mana", "魔力");
    MachineUiIcons.register(resourceType, /* icon */);
    return new BuiltinResourceIntegration<>(resourceType, recipeCapability);
}
```

## 注册

```java
// registerRecipeFoundation：
ManaRecipeCapability capability = recipe.capability(new ManaRecipeCapability(
        plugin.recipe().id("mana"), manaResourceType));
```

## 配方里使用

```java
// 构建配方：
new TopoRecipe.Builder<>(type, "mymod:spell")
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
| `matchInput` / `matchOutput`                                                    | 启动 I/O 匹配       |
| `handleInputChecked` / `handleOutputChecked`                                    | 事务性执行          |
| `scaleInput` / `scaleOutput`（+ ForParallel）                                   | 配方缩放            |
| `hasAnyContent(machine)`                                                        | 存量查询            |
| `inputUse(content)` / `outputUse(content)`                                      | 铸造权（protected） |
| `recipe.capability(cap)`                                                        | 领域注册（钩子 1）  |
| `SlottedRecipeCapability.countPreviewSlots(port)`                               | 槽位预览            |
