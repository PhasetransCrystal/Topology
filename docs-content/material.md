---
sidebar_position: 1
---

# Material

材料系统是 Topology 最基础的部分。声明一个材料，框架自动生成对应形态（锭、粉、板、流体）的物品和方块。

## 关键概念

```
Material          ← 一个材料（铜、铁、硫酸……）
  ├── MaterialForm        ← 形态（锭/粉/板/流体/矿石……），为匹配的材料生成内容
  ├── MaterialDataType    ← 附加属性（颜色、质量、工具/护甲属性……），驱动装备与表单行为
  └── MaterialPostProcessor ← 批量派生配方链（矿石处理线等），按材质激活
```

- **注册顺序**：`registerMaterialFoundation` 声明类型与形态 → 所有插件在 `registerMaterials` 构建材料并补充数据 → 匹配形态并冻结
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
            .appliesTo(mat -> mat.strategy().data(toolStatsDataType).isPresent())
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
            .data(toolStatsDataType.stats(/* durability */ 200, /* speed */ 4.5f, /* attack */ 1.5f))
            .build();
}
```
:::info
`data()` 接受 `MaterialDataUse<?>`，由对应 `MaterialDataType` 子类的公开工厂方法创建
（基类 `use(D)` 是 protected）。工厂模式见 [MaterialDataType](material/material-data.md)。
:::

`build()` 后需要给其他插件的材料补数据时，在 `registerMaterials` 的窗口中调用
`OtherMaterials.COPPER.data(dataUse)`；前提是目标已构建，模组依赖不保证同钩子内容已初始化。见 [跨插件补充数据](material/material-data.md#build-后跨插件注入)。
规则在所有插件完成材料声明和数据注入后统一求值，因此也覆盖扩展模组的材料和形态。
条件由产品定义，框架不内置“金属”等分类。匹配语义和外部条目覆盖规则见
[MaterialForm](material/material-form.md#注册与自动匹配)。

## 形态选项与外部条目覆盖

形态是否适用统一由形态的 `appliesTo` 决定，材料不维护形态包含或排除名单。
需要收窄匹配范围时应完善材料数据与规则，不应按材料 ID 编写黑名单。
`.form(form, options)` 只配置该形态的局部数据和外部条目覆盖，不使形态自动适用；规则返回 false 时不生成该形态。

```java
m.material("copper").lang("Copper", "铜")
        .data(toolStatsDataType.stats(200, 4.5f, 1.5f))
        .form(ingotForm, options -> options.overrideItem(ExternalTargets.mcItem("copper_ingot")))
        .build();
```

跨模组的现有条目复用见[兼容其他模组](compatibility/other-mods.md)，
共享类型、新形态与已构建材料的数据补充见[兼容其他 TopoPlugin](compatibility/topo-plugins.md)。

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
            .appliesTo(mat -> mat.strategy().data(toolStatsDataType).isPresent())
            .strategy(new GearForm())
            .build();
}
```

完整策略签名见 [MaterialForm](material/material-form.md)。

## 生命周期注意

- 自定义 `MaterialForm` / `MaterialDataType` 必须在 `registerMaterialFoundation()` 中注册
- 在 `registerMaterials()` 中不能注册新 Form，只能引用已注册的 Form
- `registerMaterials()` 的共享窗口内支持给已构建的材料补数据；重复类型会报错
- 所有插件完成注入后关闭写入，再统一匹配形态并校验；此后 `strategy().forms()` 才是完整且不可变的集合
- `MaterialPostProcessor` 在表单物品铸造后运行（先全部 validate，再全部 process）

## 注册流程与性能边界

| 阶段 | 输入与输出 | 扩展约束 |
|------|------------|----------|
| 基础声明 | 注册数据类型、形态规则和后处理器，然后冻结这些注册表 | 扩展插件在 `registerMaterialFoundation` 提供新类型与策略 |
| 材料声明 | Builder 与材料共享同一个策略，按数据类型存储属性 | `registerMaterials` 内允许补充不同类型，重复类型拒绝覆盖 |
| 关闭写入与匹配 | 固定材料快照，形态按完整 ID 排序，每个材料／形态组合求值一次 | 所有规则求值完毕后统一发布形态；保留的 Builder 和 options 引用也无法继续写入 |
| 校验与冻结 | 校验材料数据、匹配形态的局部数据及形态策略 | 未匹配形态的选项不参与校验；失败终止启动，不能继续修改后重试冻结 |
| 生成与后处理 | 使用材料所属插件的 RegistryCore 生成内容，再处理实际激活的处理器 | 全部材料内容生成后才运行后处理；先完成所有校验再执行处理 |

设材料数为 M、形态数为 F、激活的材料／处理器组合总数为 A。任意 Lambda 无法预先建立属性索引，
形态选择保持 M×F 次调用；框架只排序候选形态一次，规则不应在内部重复扫描全表。
匹配、校验期间复用材料快照，冻结后查询不重复复制材料列表。
后处理的两轮分派各遍历 A 个实际激活组合，不遍历 M×处理器总数。
每个材料的处理器列表在冻结阶段按完整 ID 排序并缓存。

`strategy().forms()` 提供有序不可变列表；只判断是否存在形态时使用 `strategy().hasForm(form)`，
以哈希集合查询替代线性扫描。数据声明不在 `build()` 时复制，内部写入口在统一冻结边界关闭。
注册仍是单线程启动过程，数据 payload 应使用不可变值（如 record）；框架不会深拷贝插件自定义对象。

## API 速查

| 类/方法                                                            | 用途                  |
|--------------------------------------------------------------------|-----------------------|
| `m.material(path).lang(en, cn)`                                    | 材料声明开始          |
| `.form(form, options)`                                            | 配置形态选项，不选择形态 |
| `.data(use)`                                                       | 附加材质数据          |
| `.build()`                                                         | 注册并返回 `Material` |
| `material.data(use)`                                               | 构建后直接补充数据    |
| `MaterialRegistry.require(id)` / `.registered()`                   | 查询                  |
| `material.displayName()` / `.displayNameEn()` / `.displayNameCn()` | 显示名                |
| `material.strategy().forms()` / `.data(type)`                      | 形态表 / 数据读取     |
| `material.strategy().hasForm(form)`                               | 已匹配形态的成员查询 |
| `material.strategy().postProcessors()`                            | 冻结后实际激活的处理器列表 |
