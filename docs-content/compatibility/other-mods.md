---
sidebar_position: 4
title: 兼容其他模组
---

# 使用其他模组的内容

兼容不要求对方实现 TopoPlugin。先判断要共享的是现有游戏条目、公共标签、配方，还是资源能力，
再选择对应入口。对方本身提供 Topo API 句柄时，另见[兼容其他 TopoPlugin](topo-plugins.md)。

## 1. 在模组层确认依赖

必需兼容目标在 `neoforge.mods.toml` 中声明为 required；可选目标声明为 optional，
并在登记兼容插件前检查是否加载。下面是依赖片段，版本范围应按实际支持版本填写：

```toml
[[dependencies.myaddon]]
modId="othermod"
type="optional"
ordering="AFTER"
side="BOTH"
```

```java
// 放在 myaddon 的模组构造器中，REGISTRY 属于 myaddon。
if (ModList.get().isLoaded("othermod")) {
    TopoPlugins.register(new OtherModCompatPlugin(REGISTRY));
}
```

`ModList` 位于 `net.neoforged.fml`。直接引用对方类的代码集中到兼容插件及其实现类中，
在通过加载检查后才进入这些类；基础插件和静态初始化不要提前访问可选依赖的类或字段。
依赖关系确认加载条件，不能证明某个条目、Topo 句柄或静态内容已经初始化。

若兼容只使用字符串 ID 和可选标签条目，可以不引用对方 Java 类。
但条件配方、运行时对象查询仍须处理目标缺席的情况。

## 2. 引用现有条目，保留完整 ID

自己的声明用裸路径，外部内容用完整 `namespace:path`：

```java
Identifier otherIngotId = Identifier.fromNamespaceAndPath("othermod", "copper_ingot");
Supplier<Item> otherIngot = () -> BuiltInRegistries.ITEM.getOptional(otherIngotId)
        .orElseThrow(() -> new IllegalStateException("Missing item: " + otherIngotId));
```

这里 `Identifier`、`BuiltInRegistries`、`Item` 分别来自 Minecraft 的 resources、core.registries、world.item 包，
`Supplier` 来自 `java.util.function`。将 Supplier 交给支持延迟内容的配方能力或 provider，
在注册绑定完成之后解析；在声明钩子中立即调用 `otherIngot.get()` 仍然过早。
不要仅取 `getPath()` 判断外部条目，否则 `othermod:copper_ingot` 与 `mymod:copper_ingot` 会被混淆。

| 需要什么 | 声明阶段保留什么 | 何时读取对象 |
|---|---|---|
| 配方的外部物品／流体 | 完整 ID、Entry 或支持延迟解析的 Supplier | 对应配方／资源 provider 或运行时读取时 |
| 原版／外部方块作为形态 | `ExternalItemTarget` / `ExternalBlockTarget` | 由形态策略选择安全的解析方式 |
| 物品面板或机器交互 | ID 或外部模组公开的 Entry | `registerRuntime` 中 |
| 共享资源传输 | 对方提供的能力与兼容 handler | 游戏中查询能力时 |

Supplier 只延迟调用，不会自动初始化内容或修复错误的阶段。
例如当前 RegistryLib 的 `existingItem`、`existingBlock` 会立即查询对象，
`itemTags().addSuppliers` 也会立即调用 Supplier，不能用它们包装尚未绑定的外部条目。

## 3. 将外部物品或方块作为材料形态

若“我的铜锭形态”要复用别的物品，在材料 Builder 上配置目标：

```java
// INGOT、STORAGE_BLOCK 是已声明的形态，数据应满足它们的 appliesTo 规则。
material.material("copper").lang("Copper", "铜")
        .data(METAL.enabled())
        .form(INGOT, options -> options.overrideItem(new ExternalItemTarget(
                Identifier.fromNamespaceAndPath("othermod", "copper_ingot"))))
        .form(STORAGE_BLOCK, options -> options.overrideBlock(new ExternalBlockTarget(
                Identifier.fromNamespaceAndPath("othermod", "copper_block"))))
        .build();
```

`ExternalItemTarget`、`ExternalBlockTarget` 和 `ExternalTargets` 位于 `net.ptcrys.topo.api.api.registration`。
原版目标可简写为 `ExternalTargets.mcItem("copper_ingot")` 或 `mcBlock("copper_block")`。
这些类型只保存目标 ID；**你的形态策略必须读取并执行覆盖选项**，框架不会自动替策略完成覆盖分支。

实现覆盖分支时遵循以下分工：

- 命中覆盖后使用原 ID，补所需标签或创造栏贡献；外部条目的注册、原有名称和模型由其拥有者提供。
- 自定义形态的普通生成分支仍通过 `context.core()` 创建自己的条目。
- 后续配方使用 `MaterialHelper.itemId` / `materialItemSupplier`，让查询包含覆盖目标。
- 方块形态应使用 block target，并明确物品查询是否采用该方块的物品形式。

TopoDemo 的 `ItemForm`／`BlockForm` 覆盖分支面向已经绑定的原版条目，不能直接据此推断任意模组目标都能在同一时刻解析。
对仍未绑定的模组条目，覆盖分支应优先按 ID 提交资源，例如：

```java
// 位于形态策略 register(context, form) 的覆盖分支中。
context.core().itemTags()
        .addIds(TagHelper.item("ingots"), target.id())
        .addIds(TagHelper.itemMaterial("ingots", context.material().id().getPath()), target.id());
```

这里 `target` 是从 `optionsFor(form)` 取得的 `ExternalItemTarget`，`TagHelper` 位于 `net.ptcrys.topo.helper`。
`addIds` 只提交标识，不取物品实例；创造栏等需要对象的贡献也应使用真正延迟到绑定之后的回调。
若需要修改对方物品自身的行为或资源，请使用对方公开的扩展点或资源／数据包机制。

## 4. 用公共标签表达可替代输入

希望接受所有模组的铜锭时，应使用共同约定的标签，而不是枚举各模组的物品 ID。
可以在自己提供的数据包的 `data/c/tags/item/ingots/copper.json` 中添加可选成员：

```json
{
  "replace": false,
  "values": [
    { "id": "othermod:copper_ingot", "required": false }
  ]
}
```

通过 RegistryLib 生成同样内容时可用
`itemTags().addOptionalIds(tag, id)`；必需成员用 `addIds`。

Topo 的 `TopoItemInput.tag(tag, amount)` 可以表达标签输入，
但 `RecipeCapability` 必须实现对应的匹配、消耗和序列化逻辑。
TopoDemo 的精确物品能力只接受 `TopoItemInput.Resource`，加入标签并不会自动让它接受任意铜锭。
资源共享是否成立，要同时核对标签成员和实际消费该标签的能力实现。

## 5. 兼容配方和容器能力

| 目标 | 做法 | 边界 |
|---|---|---|
| 为自己的机器添加外部原料配方 | 在 `registerRecipes` 中使用延迟物品／流体输入 | 能力工厂须支持该输入类型；实例在安全时点解析 |
| 让自己的机器使用原版或其他模组配方表 | 在类型声明时配置 `importRecipesFrom(adapterFactory, foreignTypes...)` | 需要自己的 `ImportedRecipeAdapter` 将来源配方转换成当前类型 |
| 将自己的配方导出成其他格式 | `exportRecipesTo` 加对应导出适配器 | 来源与目标语义应兼容，并在 datagen 中验证输出 |
| 让管道连接对方容器 | 使用与对方相符的 NeoForge block capability 和 handler | 对方必须提供可访问的能力，双方资源语义和事务行为须一致 |

例如导入原版冶炼表的类型声明结构如下，`MySmeltingAdapter` 是你实现的适配器：

```java
// registerRecipeTypes 中；RecipeType 来自 net.minecraft.world.item.crafting。
recipe.recipeType("electric_smelting")
        .displayName("Electric Smelting", "电力冶炼")
        .importRecipesFrom(type -> new MySmeltingAdapter(type), RecipeType.SMELTING);
```

导入在搜索与展示时投影来源表，不靠 datagen 复制来源 JSON；不支持的来源配方可由适配器返回 null 跳过。
来源若是模组延迟注册的 RecipeType，还需确认在配置导入时它已可用。
`.replacesImported()` 用于显式处理当前类型内手写配方与导入配方的冲突，不会删除对方的原始配方。

管道兼容同样不靠模组名自动识别容器。`PipeResourceProfile` 引用的资源类型必须提供 block capability；
外部容器若使用别的接口或资源语义，需要产品实现适配层。普通世界流体方块不等于流体能力容器。

## 6. 在运行时绑定外部物品

```java
@Override
public void registerRuntime(RuntimeDomainRegistration runtime) {
    Item wrench = BuiltInRegistries.ITEM.getOptional(
            Identifier.fromNamespaceAndPath("othermod", "wrench")).orElseThrow();
    runtime.itemBehavior(wrench, myWrenchBehavior);
    runtime.tooltip(wrench, myTooltipProvider);
}
```

此处假设兼容插件已通过目标模组加载检查，`myWrenchBehavior` 与 `myTooltipProvider` 是自己实现的行为和面板。
注册表按物品实例登记，一个物品已有贡献时再次登记会报重复；双方应确定由哪个兼容插件负责，而不是依赖先后覆盖。
common setup 的其他模组任务也可能尚未完成，引用游戏物品已绑定不代表其自定义运行时系统已经就绪。

验证至少包含：目标缺席时可启动，目标存在时不生成重复物品，标签与配方实际生效，
以及专用服务端不加载客户端专用类。手动命令见[测试与验证](../foundation/testing.md)。
