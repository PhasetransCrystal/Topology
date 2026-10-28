---
sidebar_position: 5
title: 兼容其他 TopoPlugin
---

# 复用与扩展其他 TopoPlugin 的内容

跨 TopoPlugin 的依赖通过**所属模组之间的依赖关系隐式确认**，不需要给插件单独配置依赖图。
但依赖关系不保证被使用的内容已经初始化：对方模组存在、插件已经登记、领域句柄完成声明、
游戏条目绑定以及自定义运行时就绪，是不同的时点。

本页以基础模组 `basemetals` 和扩展模组 `myaddon` 为例。
前者公开材料数据类型、材料形态和配方类型，后者复用这些句柄，注册新材料或为既有材料生成新内容。

## 1. 声明模组依赖，确认内容就绪时点

在 `myaddon` 的模组元数据中声明依赖，版本范围按对方公开 API 的支持版本填写：

```toml
[[dependencies.myaddon]]
modId="basemetals"
type="required"
ordering="AFTER"
side="BOTH"
```

`myaddon` 的插件仍只需在自己的构造期 `TopoPlugins.register(...)`。
若是可选兼容，使用 optional 依赖并在加载检查通过后登记兼容插件，做法见[兼容其他模组](other-mods.md)。

判断能否访问对方内容时，先确认它在哪个钩子初始化：

| 对方提供什么 | 一般在哪声明 | 消费者适合何时使用 |
|---|---|---|
| 配方资源能力 | `registerRecipeFoundation` | `registerRecipeTypes` 及后续内容钩子 |
| 材料数据类型、形态 | `registerMaterialFoundation` | `registerMaterials`；形态规则可在求值时读取共享数据类型 |
| 材料句柄 | `registerMaterials` | `registerMaterialFollowUps`、装备、机器、矿脉、具体配方等后续钩子 |
| 最终匹配形态 | 材料声明全部结束后统一计算 | `registerMaterialCreativeTabs` 及后续钩子 |
| 机器定义 | `registerMachines` | `registerMachineFollowUps`、`registerRecipes` 等明确位于其后的钩子 |
| 真实物品、方块等游戏对象 | 对应游戏注册表绑定时 | `registerRuntime` 或允许读取对象的 provider 回调 |
| 对方自定义运行时状态 | 由对方约定 | 按对方提供的就绪协议访问，不能仅凭 `registerRuntime` 推断 |

框架先完成全部插件的某个钩子，再进行该步骤的校验／冻结并进入后续依赖步骤。
这使“基础类型先声明、具体内容后引用”成为可靠的用法。

当前引擎按 Mod ID 与插件类名稳定遍历插件，并不把模组依赖排序转换成同一钩子内的执行保证。
因此 `ordering="AFTER"` 和已经登记插件都不能证明对方的同名钩子已执行。
同一模组的多个插件也不能靠 `TopoPlugins.register` 的先后顺序保证初始化。

## 2. 使用对方公开的同一个句柄

共享类型应引用对方公开字段或查询接口，不要重新创建同 ID 的实例。
例如以下片段假设基础模组公开：

| API 名称（示例） | 所表示的内容 |
|---|---|
| `BaseMaterialTypes.METAL` | 已注册的布尔材料数据类型，提供 `enabled()` 工厂 |
| `BaseForms.INGOT` | 已注册的锭形态 |
| `BaseMaterials.COPPER` | 在对方 `registerMaterials` 中构建的材料 |
| `BaseRecipes.GRINDING` | 在对方 `registerRecipeTypes` 中声明的 `TopoRecipeType<TopoRecipe>` |
| `BaseRecipes.ITEMS` | 对方实现的配方能力，支持 Supplier 输入／输出 |

这些名称仅说明合作模组的 API 结构，不是 Topo 内置类型。
拿不到公开静态字段时，可在其注册阶段结束后按完整 ID 查询，查询不会触发声明：

```java
// 例如 registerMaterialFollowUps 或 registerRecipes 中。
Material copper = MaterialRegistry.require(
        Identifier.fromNamespaceAndPath("basemetals", "copper"));
MaterialForm ingot = MaterialFormRegistry.require(
        Identifier.fromNamespaceAndPath("basemetals", "ingot"));
```

同名的两个类型实例并不等价。框架的数据 use 会核对句柄归属，重复注册同 ID 也会报错。
若查询时报缺失，应核对对方功能是否启用、ID 和阶段，而不是创建同名条目来补位。

## 3. 用共享类型注册自己的材料

```java
@Override
public void registerMaterials(MaterialDomainRegistration material) {
    material.material("nickel").lang("Nickel", "镍")
            .data(BaseMaterialTypes.METAL.enabled())
            .build();
}
```

此时所有插件的 `registerMaterialFoundation` 已完成，共享数据类型可用。
若 `basemetals` 的锭形态根据 METAL 数据匹配材料，镍会自动获得该形态。
材料 ID 是 `myaddon:nickel`；生成物由 `myaddon` 的 RegistryCore 创建，例如 `myaddon:nickel_ingot`。
若形态需要额外颜色或质量数据，应同时提供它们；规则匹配成功后缺少必需数据会使校验失败。

复用公开策略时检查它是否通过 `context.core()` 生成内容。
如果策略把 RegistryCore、材质名单或输出命名空间写死，框架无法自动将它变成通用跨插件策略。

## 4. 给其他插件的材料增加新形态

```java
@Override
public void registerMaterialFoundation(MaterialDomainRegistration material) {
    material.form("plate")
            .lang("Plate", "板")
            .appliesTo(value -> value.strategy().data(BaseMaterialTypes.METAL).orElse(false))
            .strategy(new PlateForm())
            .build();
}
```

`PlateForm` 是按[形态教程](../material/material-form.md) 实现的产品策略，输出模板例如 `%s_plate`。
Lambda 在全部材料声明结束后求值，因此可以把 `BaseMaterialTypes.METAL` 的读取放在规则内，
避免在自己的 foundation 钩子中立即读取尚未初始化的字段。
这不同于先把字段读入一个可能为 null 的局部变量，再在 Lambda 中引用该变量。

只要对方材料具备该共享数据，新形态会自动匹配，不需要逐个修改对方材料。
例如形态 ID 为 `myaddon:plate`，匹配到 `basemetals:copper` 后，
通过 `context.core()` 生成的物品归属 `basemetals:copper_plate`。
这要求两个模组都参与对应的内容与数据生成流程；扩展模组不会自动取得对方命名空间的独立输出目录。

注册新形态前还要检查输出模板：不同形态若都为同一材料生成 `copper_plate`，仍会发生游戏条目 ID 冲突。
形态 ID 不同并不会替策略自动隔离派生条目的路径。

## 5. 给已经构建的材料补数据

材料在 `build()` 后、全部插件完成 `registerMaterials` 之前，允许通过 `Material.data(use)` 补数据：

```java
// builtTarget 是合作方在本次材料声明窗口中已经 build() 的真实句柄。
// CORROSION 是 myaddon 在材料基础阶段注册的数据类型。
public void contributeCorrosion(Material builtTarget) {
    builtTarget.data(CORROSION.rating(3));
}
```

这里的关键前提是**调用时目标已经构建**。模组依赖不能证明 `BaseMaterials.COPPER` 已经赋值，
也不能保证自己的 `registerMaterials` 晚于对方。
直接读取对方静态字段可能得到 null，也可能意外触发整组注册；
把读取包进 Supplier，若仍在同一窗口过早调用，也没有解决问题。

需要此类兼容时，按合作方明确提供的初始化／回调约定取得已构建目标，再在窗口内调用。
当前框架没有“全部材料声明后、关闭写入前”的单独插件注入钩子；
不能先等到 `registerMaterialFollowUps` 再补数据，那时已经关闭写入。
若双方没有保证目标就绪的协作入口，优先让新形态读取对方已有数据，
或由材料提供方在构建材料时消费扩展数据类型。

补数据不改变材料归属，同一数据类型重复写入会报错，不提供后写覆盖。
形态匹配、校验和运行时均不能继续修改；也不能通过改写对方策略或重新注册材料绕过冻结。

## 6. 为共享内容添加配方

`registerRecipes` 位于所有内容领域之后，适合组合双方材料、机器与配方类型。
以下片段假设已取到 `copper`、`ingot` 和实际匹配的 `dust` 形态：

```java
@Override
public void registerRecipes(RecipeDomainRegistration recipe) {
    BaseRecipes.GRINDING.recipe("compat/myaddon/copper_to_dust")
            .input(BaseRecipes.ITEMS.in(MaterialHelper.materialItemSupplier(copper, ingot), 1))
            .output(BaseRecipes.ITEMS.out(MaterialHelper.materialItemSupplier(copper, dust), 1))
            .duration(40)
            .save();
}
```

使用对方配方类型，无需重新声明同名类型。局部配方路径带扩展模组名，减少与其他贡献者的冲突。
此路径会输出到类型拥有者的命名空间，例如
`basemetals:grinding/compat/myaddon/copper_to_dust`；配方 datagen 也由该类型绑定的 RegistryCore 提交。
生成资源时要让相应拥有者的 provider 参与，不能假定只选择 `myaddon` 就能导出这份配方。

遍历材料表做通用派生时，放在材料完成之后的钩子中，检查共享数据与最终形态：

```java
for (Material target : MaterialRegistry.registered()) {
    if (!target.strategy().hasForm(ingot) || !target.strategy().hasForm(dust)) continue;
    String path = "compat/myaddon/" + target.id().getNamespace() + "/" + target.id().getPath();
    // 在目标配方类型下使用该局部路径并保存配方。
}
```

这类读操作适合 `registerMaterialFollowUps` 或 `registerRecipes`，
不应在自己的 `registerMaterials` 中扫描并假定已经看到其他插件的全部材料。

## 7. 其他常见扩展

| 要扩展什么 | 做法 | 边界 |
|---|---|---|
| 为其他材料增加装备 | 在 `registerEquipmentKinds` 声明按共享数据匹配的策略 | 生成时使用材料的 `context.core()`，通过 `EquipmentRegistry.recordItem` 记录装备以接入自动行为 |
| 用对方组件搭自己的机器 | 在 `registerMachines` 引用已公开的组件工厂、资源类型与配方类型 | 组件 key、依赖结构和数据字段应遵守对方契约 |
| 让管道与对方资源体系兼容 | 复用公开的 `MachineResourceType` 与匹配 capability | 不要重新注册同名资源类型；实际传输还取决于 handler 兼容性 |
| 为对方物品添加工具交互／面板 | `registerRuntime` 中对已绑定物品登记 | 一个物品一个对应贡献，重复登记不会自动合并 |
| 修改已经构建的机器定义或材料形态选项 | 使用提供方公开且仍处于开放窗口的配置入口 | 已冻结定义没有通用覆盖或替换接口 |

验收时同时运行基础模组与扩展模组的数据生成，并检查：
派生条目归属是否正确、扩展材料是否被基础形态识别、基础材料是否被新形态识别，
以及缺少可选模组时能否启动。实际游戏与资源输出需按[测试与验证](../foundation/testing.md) 手动检查。
