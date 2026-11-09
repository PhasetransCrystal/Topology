---
sidebar_position: 1
---

# Material

面向需要声明材料或复用材料形态的模组开发者。材料声明完成后，框架按已注册形态的规则匹配材料，并调用匹配形态的策略生成内容。

## 关键概念

```
Material          ← 一个材料（铜、铁、硫酸……）
  ├── MaterialForm        ← 形态（锭/粉/板/流体/矿石……），为匹配的材料生成内容
  ├── MaterialDataType    ← 附加属性（颜色、质量、工具/护甲属性……），驱动装备与形态行为
  └── MaterialPostProcessor ← 批量派生配方链（矿石处理线等），按材质激活
```

- **注册顺序**：`registerMaterialFoundation` 声明类型与形态 → 所有插件在 `registerMaterials` 构建材料 → `registerMaterialContributions` 统一注入能力数据 → 匹配形态并冻结
- **能力数据是核心**：新能力 = 新的 `MaterialDataType` + 向材料注入 `MaterialDataUse`；消费方用 `material.data(type)` 判断存在性与内容，形态用 `appliesTo(mat -> ...)` 消费
- **库不内置玩法形态/数据类型**——可以自己实现，也可以复用依赖模组的公开类型
- 形态只通过 `appliesTo(mat -> ...)` 按属性匹配材料；材料声明数据和外部条目覆盖，不直接指定形态集合

## 页面导航

| 页面                                                         | 内容                                   | 何时阅读                       |
|--------------------------------------------------------------|----------------------------------------|--------------------------------|
| [MaterialForm](material/material-form.md)                    | 继承 `MaterialFormStrategy` 自定义形态 | 需要锭/粉/板之外的新形态       |
| [MaterialDataType](material/material-data.md)                | 继承 `MaterialDataType<D>` 附加属性    | 需要颜色/质量/工具属性等新属性 |
| [MaterialPostProcessor](material/material-post-processor.md) | 批量派生配方链                         | 需要按材质批量生成配方         |
| **Material（本页）**                                         | 声明材料、注册顺序、查询               | 添加新材料                     |

## 声明与数据补充

库不内置任何形态/数据类型——**先在 `registerMaterialFoundation` 声明，再在 `registerMaterials` 引用**：

```java
// ① registerMaterialFoundation：声明形态与数据类型（此时冻结表尚未关闭）
@Override
public void registerMaterialFoundation(MaterialDomainRegistration m) {
    toolStatsDataType = m.dataType("tool_stats",
            new ToolStatsDataType(plugin.material().id("tool_stats")));   // 你的 MaterialDataType 子类

    ingotForm = m.form("ingot")
            .lang("Ingot", "锭")
            .appliesTo(mat -> mat.data(toolStatsDataType).isPresent())
            .strategy(new IngotForm())        // 你的 MaterialFormStrategy 子类
            .build();
}

// ② registerMaterials：声明材料，引用已注册的形态与数据
@Override
public void registerMaterials(MaterialDomainRegistration m) {
    m.material("copper")
            .lang("Copper", "铜")
            // MaterialDataUse 通过 DataType 的公开工厂方法获得，不能直接 new：
            .data(rgbColorDataType.rgb(0xFFB873))
            .data(massDataType.mass(8))
            .data(toolStatsDataType.stats(200, 4.5f, 1.5f, 14, BlockTags.INCORRECT_FOR_IRON_TOOL))
            .build();
}
```
:::info
`data()` 接受 `MaterialDataUse<?>`，由对应 `MaterialDataType` 子类的公开工厂方法创建
（基类 `use(D)` 是 protected）。工厂模式见 [MaterialDataType](material/material-data.md)。
:::

`build()` 后需要给其他插件的材料补数据时，优先在 `registerMaterialContributions` 中调用
`contributions.data(target, dataUse)`；该阶段在所有 `registerMaterials` 之后、形态匹配之前运行，
不依赖插件登记顺序。已知目标已构建时也可在 `registerMaterials` 窗口内直接 `target.data(dataUse)`；
前提仍是目标已构建，模组依赖不保证同钩子内容已初始化。见 [跨插件补充数据](material/material-data.md#跨插件注入能力数据)。
规则在所有插件完成材料声明和数据注入后统一求值，因此也覆盖扩展模组的材料和形态。
条件由产品定义，框架不内置“金属”等分类。匹配语义和外部条目覆盖规则见
[MaterialForm](material/material-form.md#注册与自动匹配)。

## 形态选项与外部条目覆盖

形态是否适用统一由形态的 `appliesTo` 决定，材料不维护形态包含或排除名单。
需要收窄匹配范围时应完善材料数据与规则，不应按材料 ID 编写黑名单。
`.form(form, options)` 只配置该形态的局部数据和外部条目覆盖，不使形态自动适用；规则返回 false 时不生成该形态。

```java
m.material("copper").lang("Copper", "铜")
        .data(toolStatsDataType.stats(200, 4.5f, 1.5f, 14, BlockTags.INCORRECT_FOR_IRON_TOOL))
        .form(ingotForm, options -> options.overrideItem(ExternalTargets.item(Items.COPPER_INGOT)))
        .build();
```

跨模组的现有条目复用见[兼容其他模组](compatibility/other-mods.md)，
共享类型、新形态与已构建材料的数据补充见[兼容其他 TopoPlugin](compatibility/topo-plugins.md)。

## 按兼容物品获取材料

`MaterialLookup` 是冻结后构建的 O(1) 内容索引，按 `MaterialContentType + 注册 ID` 定位材料：

```java
MaterialBinding binding = MaterialLookup.current()
        .byItem(otherModItem)
        .orElseThrow();
Material material = binding.material();
MaterialForm form = binding.form();

MaterialLookup.current().byItem(itemStack);
MaterialLookup.current().byContent(MaterialContentTypes.ITEM, itemId);
```

索引来自每个形态计划的输出以及外部 item/block/fluid target；运行时还会在游戏注册表绑定完成后重建一次。
同一个 `MaterialContentType + 注册 ID` 出现多个不同绑定会使索引构建失败。
`byItem(ItemStack)` 只按物品 ID 查找，不比较 data component；组件敏感的场景应使用完整的自定义 content type
或自定义查询策略。

## 查询已注册材料

```java
Material copper = MaterialRegistry.require(Identifier.fromNamespaceAndPath("mymod", "copper"));

Component name = copper.displayName();
String en = copper.displayNameEn();   // "Copper"
String cn = copper.displayNameCn();   // "铜"

// 遍历所有已注册材料
for (Material mat : MaterialRegistry.registered()) {
    System.out.println(mat.id() + " forms: " + mat.forms());
}
```

## 自定义 MaterialForm

形态 = `MaterialFormStrategy` 子类（`registryPath()` 模板 + 每个材质一份 `MaterialFormPlan`）：

```java
// 在 registerMaterialFoundation 中注册
@Override
public void registerMaterialFoundation(MaterialDomainRegistration m) {
    m.form("gear")
            .lang("Gear", "齿轮")
            .appliesTo(mat -> mat.data(toolStatsDataType).isPresent())
            .strategy(new GearForm())
            .build();
}
```

完整策略签名见 [MaterialForm](material/material-form.md)。

## 生命周期注意

- 自定义 `MaterialForm` / `MaterialDataType` 必须在 `registerMaterialFoundation()` 中注册
- 在 `registerMaterials()` 中不能注册新 Form，只能引用已注册的 Form
- `registerMaterials()` 的共享窗口内支持给已构建的材料补数据；重复类型会报错
- `registerMaterialContributions()` 在所有插件完成 `registerMaterials` 后运行，适合给全量材料或其他插件的材料注入能力；重复类型仍报错，也不能再创建新材料
- 所有插件完成注入后关闭写入，再统一匹配形态并校验；此后 `forms()` 才是完整且不可变的集合
- `MaterialPostProcessor` 在形态物品铸造后运行（先全部 validate，再全部 process）

`forms()` 返回有序不可变列表；只判断是否存在某种形态时可使用 `hasForm(form)`。插件自定义数据应使用不可变值，框架不会深拷贝数据对象。

## API 速查

| 类/方法                                                            | 用途                  |
|--------------------------------------------------------------------|-----------------------|
| `m.material(path).lang(en, cn)`                                    | 材料声明开始          |
| `.form(form, options)`                                            | 配置形态选项，不选择形态 |
| `.data(use)`                                                       | 附加材质数据          |
| `.build()`                                                         | 注册并返回 `Material` |
| `material.data(use)`                                               | 构建后直接补充数据    |
| `material.data(type)` / `.hasData(type)` / `.dataUses()`           | 能力查询              |
| `MaterialRegistry.require(id)` / `.registered()`                   | 查询                  |
| `material.displayName()` / `.displayNameEn()` / `.displayNameCn()` | 显示名                |
| `material.forms()` / `.data(type)`                      | 形态表 / 数据读取     |
| `material.hasForm(form)`                               | 已匹配形态的成员查询 |
| `material.postProcessors()`                            | 冻结后实际激活的处理器列表 |
| `contributions.data(material, use)` / `.form(material, form, ...)` | 贡献阶段注入          |
