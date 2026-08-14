---
sidebar_position: 1
---

# Material

材料系统是 Topology 最基础的部分。声明一个材料，框架自动生成对应形态（锭、粉、板、流体）的物品和方块。

## 关键概念

```
Material          ← 一个材料（铜、铁、硫酸……）
  ├── MaterialForm        ← 形态（锭/粉/板/流体/矿石……），每个形态 × 每个材料 = 一个物品或方块
  ├── MaterialDataType    ← 附加属性（颜色、质量、工具/护甲属性……），驱动装备与表单行为
  └── MaterialPostProcessor ← 批量派生配方链（矿石处理线等），按材质激活
```

- **两阶段注册**：形态与数据类型在 `registerMaterialFoundation` 声明（冻结前），材料在 `registerMaterials` 引用
- **库不内置任何形态/数据类型**——全部继承实现（见下方导航）
- 声明材料 = 框架按引用形态自动铸造物品/方块/配方

## 页面导航

| 页面                                                         | 内容                                   | 何时阅读                       |
|--------------------------------------------------------------|----------------------------------------|--------------------------------|
| [MaterialForm](material/material-form.md)                    | 继承 `MaterialFormStrategy` 自定义形态 | 需要锭/粉/板之外的新形态       |
| [MaterialDataType](material/material-data.md)                | 继承 `MaterialDataType<D>` 附加属性    | 需要颜色/质量/工具属性等新属性 |
| [MaterialPostProcessor](material/material-post-processor.md) | 批量派生配方链                         | 需要按材质批量生成配方         |
| **Material（本页）**                                         | 声明材料、两阶段注册、查询             | 添加新材料                     |

## 两阶段模式

库不内置任何形态/数据类型——**先在 `registerMaterialFoundation` 声明，再在 `registerMaterials` 引用**：

```java
// ① registerMaterialFoundation：声明形态与数据类型（此时冻结表尚未关闭）
@Override
public void registerMaterialFoundation(MaterialDomainRegistration m) {
    ingotForm = m.form("ingot")
            .lang("Ingot", "锭")
            .strategy(new IngotForm())        // 你的 MaterialFormStrategy 子类
            .build();

    toolStatsDataType = m.dataType("tool_stats",
            new ToolStatsDataType(plugin.material().id("tool_stats")));   // 你的 MaterialDataType 子类
}

// ② registerMaterials：声明材料，引用已注册的形态与数据
@Override
public void registerMaterials(MaterialDomainRegistration m) {
    m.material("copper")
            .lang("Copper", "铜")
            .form(ingotForm)
            .form(dustForm)
            .form(blockForm)
            // MaterialDataUse 通过 DataType 的公开工厂方法获得，不能直接 new：
            .data(rgbColorDataType.rgb(0xFFB873))
            .data(massDataType.mass(8))
            .data(toolStatsDataType.stats(/* durability */ 200, /* speed */ 4.5f, /* attack */ 1.5f))
            .build();
}
```
:::info
`data()` 接受 `MaterialDataUse<?>`，由对应 `MaterialDataType` 子类的公开工厂方法创建
（基类 `use(D)` 是 protected）。工厂模式见 [MaterialDataType](material/material-data.md)。
:::

## 批量声明

```java
m.material("tin").lang("Tin", "锡")
        .forms(ingotForm, dustForm, blockForm)
        .data(rgbColorDataType.rgb(0xD3D3D3))
        .build();

m.material("steel").lang("Steel", "钢")
        .form(ingotForm)
        .data(toolStatsDataType.stats(500, 7.0f, 2.5f))
        .data(armorStatsDataType.stats(30, 3, 6, 5))
        .build();
```

## 查询已注册材料

```java
Material copper = MaterialRegistry.require(Identifier.fromNamespaceAndPath("mymod", "copper"));

Component name = copper.displayName();
String en = copper.displayNameEn();   // "Copper"
String cn = copper.displayNameCn();   // "铜"

// 遍历所有已注册材料
for (Material mat : MaterialRegistry.registered()) {
    System.out.println(mat.id() + " forms: " + mat.strategy().forms());
}
```

## 自定义 MaterialForm

形态 = `MaterialFormStrategy` 子类（`registryPath()` 模板 + 每个材质一次 `register`）：

```java
// 在 registerMaterialFoundation 中注册
@Override
public void registerMaterialFoundation(MaterialDomainRegistration m) {
    m.form("gear")
            .lang("Gear", "齿轮")
            .strategy(new GearForm())
            .build();
}
```

完整策略签名见 [MaterialForm](material/material-form.md)。

## 生命周期注意

- 自定义 `MaterialForm` / `MaterialDataType` 必须在 `registerMaterialFoundation()` 中注册
- 在 `registerMaterials()` 中不能注册新 Form，只能引用已注册的 Form
- `MaterialPostProcessor` 在表单物品铸造后运行（先全部 validate，再全部 process）

## API 速查

| 类/方法                                                            | 用途                  |
|--------------------------------------------------------------------|-----------------------|
| `m.material(path).lang(en, cn)`                                    | 材料声明开始          |
| `.form(form)` / `.forms(...)`                                      | 引用已注册形态        |
| `.data(use)`                                                       | 附加材质数据          |
| `.build()`                                                         | 注册并返回 `Material` |
| `MaterialRegistry.require(id)` / `.registered()`                   | 查询                  |
| `material.displayName()` / `.displayNameEn()` / `.displayNameCn()` | 显示名                |
| `material.strategy().forms()` / `.data(type)`                      | 形态表 / 数据读取     |
