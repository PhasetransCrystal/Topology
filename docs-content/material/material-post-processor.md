---
sidebar_position: 4
---

# MaterialPostProcessor

面向需要按材料派生配方或其他内容的模组开发者。后处理器对每个声明激活它的材料执行一次 `process`。

本页是 [Material](../material.md) 模块的子页——概念与页面导航见 [Material](../material.md)。

## 基类

```java
public abstract class MaterialPostProcessor {

    protected MaterialPostProcessor(Identifier id, MaterialForm... requiredForms);

    public final MaterialDataUse<MaterialPostProcessor> activation();   // 材质通过 .data(PROCESSOR.activation()) 激活

    public void validate(Material material);   // 结构性快速失败；基类已检查 requiredForms，覆写加自己的规则
    public abstract void process(Material material);   // 框架只传入声明激活的材质

    protected final boolean notDeclaredOn(Material material);   // 直接调用处理器时可作防御检查
    protected final void require(Material material, MaterialForm form);
}
```

```java
public final class OreProcessingChain extends MaterialPostProcessor {

    public OreProcessingChain(Identifier id) { super(id, /* requiredForms */ DUST, INGOT); }

    @Override
    public void validate(Material material) {
        super.validate(material); // 保留构造时声明的全部必需形态检查
        // 在此添加其他结构约束。
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) return;
        // 为该材质发射配方。此刻形态物品已铸造，
        // 可用 MaterialHelper 等延迟获取（datagen 时才解析为具体物品）
    }
}
```

## 注册与激活

```java
// registerMaterialFoundation（MaterialPostProcessorRegistry.freeze() 之前）：
OreProcessingChain processor = material.postProcessor("ore_processing",
        new OreProcessingChain(plugin.material().id("ore_processing")));
// processor.id() 必须与插件命名空间 path 一致，否则注册时抛异常

// registerMaterials：材质声明激活
m.material("copper").data(processor.activation()).build();
```

## 执行顺序

材料冻结时，对每个已激活的材料／处理器组合调用 `validate`，检查必需形态。
全部形态内容完成注册声明后，才对这些组合调用 `process`。校验不应产生副作用，
不能要求冻结阶段已有生成物品，也不能在 `validate` 内生成配方。覆写 `validate` 时调用
`super.validate(material)`，以保留构造器中 `requiredForms` 的检查。
`process` 里可以安全引用其他材质的形态物品（跨材质配方链可行）。

未激活的处理器不会收到框架的 `validate` 或 `process` 调用，产品无需自行过滤未激活材质。
引擎复用冻结时构建的激活列表，单个材料的处理器按完整 ID 排序，
不依赖处理器注册或激活数据的声明顺序。处理器之间不应依赖彼此生成的结果。

## API 速查

| 类/方法                                       | 用途                             |
|-----------------------------------------------|----------------------------------|
| `MaterialPostProcessor(id, requiredForms...)` | 基类构造                         |
| `processor.activation()`                      | 材质激活数据（`.data(...)` 用）  |
| `processor.validate(material)`                | 结构校验（先于全部 process）     |
| `processor.process(material)`                 | 每材质发射配方链                 |
| `processor.notDeclaredOn(material)`           | 未激活判定（process 首行 guard） |
| `processor.require(material, form)`           | 形态存在断言                     |
| `material.postProcessor(path, processor)`     | 领域注册（钩子 3，id 须匹配）    |
