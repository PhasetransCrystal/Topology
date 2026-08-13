---
sidebar_position: 4
---

# MaterialPostProcessor

后处理器对**每一个声明激活它的材质**运行一次，用于批量派生配方链（矿石处理线、金属形态合成等）。

本页是 [Material](../material.md) 模块的子页——概念与页面导航见 [Material](../material.md)。

## 基类

```java
public abstract class MaterialPostProcessor {

    protected MaterialPostProcessor(Identifier id, MaterialForm... requiredForms);

    public final MaterialDataUse<MaterialPostProcessor> activation();   // 材质通过 .data(PROCESSOR.activation()) 激活

    public void validate(Material material);   // 结构性快速失败；基类已检查 requiredForms，覆写加自己的规则
    public abstract void process(Material material);   // 为单个材质发射配方；先 guard：

    protected final boolean notDeclaredOn(Material material);   // 未激活时 true → process 里直接 return
    protected final void require(Material material, MaterialForm form);
}
```

```java
public final class OreProcessingChain extends MaterialPostProcessor {

    public OreProcessingChain(Identifier id) { super(id, /* requiredForms */ DUST, INGOT); }

    @Override
    public void validate(Material material) {
        // 例如：require(material, DUST);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) return;
        // 为该材质发射配方。此刻表单物品已铸造，
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

引擎先对**全部材质**跑完 `validate`，再对**全部材质**跑 `process`——
`process` 里可以安全引用其他材质的表单物品（跨材质配方链可行）。

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `MaterialPostProcessor(id, requiredForms...)` | 基类构造            |
| `processor.activation()`               | 材质激活数据（`.data(...)` 用） |
| `processor.validate(material)`         | 结构校验（先于全部 process）    |
| `processor.process(material)`          | 每材质发射配方链                |
| `processor.notDeclaredOn(material)`    | 未激活判定（process 首行 guard）|
| `processor.require(material, form)`    | 表单存在断言                    |
| `material.postProcessor(path, processor)` | 领域注册（钩子 3，id 须匹配）|
