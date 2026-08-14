---
sidebar_position: 3
---

# Ore Display

矿脉的 JEI 预览渲染器。每种模式必须有一个对应显示——引擎在 `OreVeinDisplays.freeze()` 后校验，
缺一个直接启动失败。

本页是 [Ore](../ore.md) 模块的子页——概念与页面导航见 [Ore](../ore.md)。

## OreVeinDisplay.Strategy

```java
public interface OreVeinDisplay.Strategy {
    UIElement buildPreview(OreVein vein);   // JEI 分类里的矿脉预览元素
}
```

## 注册（钩子 14 registerOreDisplays）

```java
@Override
public void registerOreDisplays(OreDomainRegistration ore) {
    ore.display(stoneReplaceMode.id(), vein -> {
        // 构建 LDLib2 预览元素：矿脉名称、成分表、高度范围等
        return buildMyPreview(vein);
    });
}
```

`modeId` 必须指向 `registerOreFoundation`（钩子 13）里已注册的模式；`OreVeinDisplays.require(modeId)`
按模式 id 反查。

矿脉声明与模式注册见 [Ore](../ore.md)。

## API 速查

| 类/方法                                      | 用途                            |
|----------------------------------------------|---------------------------------|
| `OreVeinDisplay.Strategy.buildPreview(vein)` | JEI 预览元素构建                |
| `ore.display(modeId, strategy)`              | 注册显示（钩子 14，每模式必配） |
| `OreVeinDisplays.require(modeId)`            | 按模式 id 反查                  |
