---
sidebar_position: 1
---

# Material

材料系统是 Topology 最基础的部分。声明一个材料，框架自动生成对应形态（锭、粉、板、流体）的物品和方块。

## 何时阅读

任何 Mod 添加新物品/方块时。几乎所有 Mod 都从这里开始。

## 完整示例：声明一个材料

```java

@Override
public void registerMaterials(MaterialDomainRegistration m) {
    m.material("copper")
            .lang("Copper", "铜")
            .form(ingotForm)
            .form(dustForm)
            .form(blockForm)
            .form(fluidForm)
            // MaterialData 通过 DataType 工厂方法获得 MaterialDataUse
            .data(rgbColorDataType.rgb(0xFFB873))
            .data(massDataType.mass(8))
            .data(toolStatsDataType.stats(/* durability */ 200, /* speed */ 4.5f,
                    /* attack */ 1.5f, /* enchant */ 10, /* incorrectFor */ ItemTags.PICKAXES))
            .build();
}
```

:::info MaterialData 通过 DataType 注册
`data()` 方法接受 `MaterialDataUse<?>`，需通过对应 `MaterialDataType` 的工厂方法创建，不能直接 `new`。
:::

## 批量声明

```java
m.material("tin").

lang("Tin","锡")
    .

forms(ingotForm, dustForm, blockForm)
    .

data(rgbColorDataType.rgb(0xD3D3D3))
        .

build();

m.

material("steel").

lang("Steel","钢")
    .

form(ingotForm)
    .

data(toolStatsDataType.stats(500, 7.0f,2.5f,12,ItemTags.PICKAXES))
        .

data(armorStatsDataType.stats(30, 3,6,5,2,2.0f,0.1f,12))
        .

build();
```

## 查询已注册材料

```java
Material copper = MaterialRegistry.require(Identifier.fromNamespaceAndPath("mymod", "copper"));

Component name = copper.displayName();
String en = copper.displayNameEn();   // "Copper"
String cn = copper.displayNameCn();   // "铜"

// 遍历所有已注册材料
for(Material mat :MaterialRegistry.registered()){
    System.out.println(mat.id() +" forms: "+mat.strategy().forms());
}
```

## 自定义 MaterialForm

当内置 forms 不够用时：

```java
// 在 registerMaterialFoundation 中注册
@Override
public void registerMaterialFoundation(MaterialDomainRegistration m) {
    m.form("gear")
            .lang("Gear", "齿轮")
            .strategy(new ItemForm())
            .build();
}
```

## 生命周期注意

- 自定义 `MaterialForm` / `MaterialDataType` 必须在 `registerMaterialFoundation()` 中注册
- 在 `registerMaterials()` 中不能注册新 Form，只能引用已注册的 Form
- `MaterialPostProcessor`（矿石处理线）在材料冻结后运行
