---
sidebar_position: 2
---

# MaterialForm

面向需要定义材料形态及其生成内容的模组开发者。形态由 `MaterialFormStrategy` 实例和注册时的双语文案组成，可通过 `appliesTo(Predicate<Material>)` 自动选择材料。
每个匹配的材料只执行一次该形态策略；`appliesTo` 是选择形态的唯一来源。

本页是 [Material](../material.md) 模块的子页——概念与页面导航见 [Material](../material.md)。

## 基类

```java
public abstract class MaterialFormStrategy {

    protected MaterialFormStrategy(Collection<FormDataUse<?>> data);   // 类型化参数（如数量）

    public final <D> Optional<D> data(FormDataType<D> type);
    public final <D> D requireData(FormDataType<D> type);

    public abstract String registryPath();                             // "%s_ingot"——材质路径代入 %s
    public abstract MaterialFormPlan plan(Material material, MaterialForm form);

    protected final void requireMaterialData(Material material, MaterialForm form,
            List<MaterialDataType<?>> required);
}
```

`MaterialFormPlan` 是一个冻结后的执行计划：`outputs()` 声明注册输出，`validate()` 在冻结校验阶段运行，
`register(context)` 在内容阶段运行。计划创建时立即固化输出与外部 target，不会在后续阶段重新计算。
全部材料的形态匹配结果发布完成后，框架才统一构建 plan，因此 plan 构造期可以安全读取完整形态表。
`MaterialContentContext` 是 `record(RegistryCore core, Material material)`——
`register` 里用 `context.core().item(...)` / `context.core().block(...)` 铸造注册项。
流体形式同样由策略在这里调用 `context.core().fluid(...)`，再按需要配置方块和桶；
框架不把流体形式自动转成物品形式，查询源流体使用 `MaterialHelper.fluid`／`requireFluid`。

## 示例：Builder 模式

形态可以作为参数化模板，用静态 `builder()` 组织并在 `build()` 中校验必填项。
以下示例省略模型、标签、创造栏和外部覆盖处理；`MyDataTypes.MAGIC` 与后文的 `magicDataType` 指同一个已注册数据类型：

```java
public final class OrbForm extends MaterialFormStrategy {

    private final String registryPath;
    private final TemplateDisplayName displayName;

    private OrbForm(Builder builder) {
        super(builder.data);
        this.registryPath = builder.registryPath;
        this.displayName = builder.displayName;
    }

    public static Builder builder() { return new Builder(); }

    @Override public String registryPath() { return registryPath; }

    @Override
    public MaterialFormPlan plan(Material material, MaterialForm form) {
        String materialPath = material.id().getPath();
        String path = String.format(registryPath, materialPath);
        MaterialContentKey output = new MaterialContentKey(
                MaterialContentTypes.ITEM, material.id().withPath(path));
        return MaterialFormPlan.of(
                List.of(output),
                () -> requireMaterialData(material, form, List.of(MyDataTypes.MAGIC)),
                context -> {
                    var builder = context.core().item(path);
                    RegistryDisplayLang.applyItem(builder, context.core(), path,
                            displayName.resolve(context.material()));
                    builder.register();
                });
    }

    public static final class Builder {
        private final List<FormDataUse<?>> data = new ArrayList<>();
        private String registryPath;
        private TemplateDisplayName displayName;

        private Builder() {}

        public Builder data(FormDataUse<?> use) { this.data.add(use); return this; }
        public Builder registryPath(String pattern) { this.registryPath = pattern; return this; }
        public Builder displayName(String enPattern, String cnPattern) {
            this.displayName = DisplayNames.template(enPattern, cnPattern);
            return this;
        }

        public OrbForm build() {
            // build() 里做必填校验，缺一抛异常：
            Objects.requireNonNull(registryPath, "form requires registryPath");
            Objects.requireNonNull(displayName, "form requires displayName(en, cn)");
            return new OrbForm(this);
        }
    }
}
```

## 注册与自动匹配

```java
// registerMaterialFoundation（MaterialFormRegistry.freeze() 之前）：
orbForm = material.form("orb")
        .lang("Orb", "宝珠")
        .appliesTo(mat -> mat.data(magicDataType).isPresent())
        .strategy(OrbForm.builder()
                .registryPath("%s_orb")
                .displayName("%s Orb", "%s宝珠")
                .build())
        .build();          // 返回 MaterialForm 句柄

// registerMaterials：只声明材料属性，形态由规则选择
m.material("moonstone").lang("Moonstone", "月长石")
        .data(magicDataType.magic(100)).build();
```

`appliesTo` 接受任意 Lambda，不限定属性类型、材料命名空间或材料名单。扩展插件可用共享的
数据类型声明新材料，也可新增带规则的形态，已有材料无需修改声明。

所有插件完成 `registerMaterialContributions` 后，框架关闭数据写入，
先计算所有材料的最终形态，再统一校验，随后才生成内容。其他插件注入的数据同样参与规则判断。
规则必须无副作用，并基于材料的声明数据判断，不应读取其他规则推导的形态或尚未生成的注册项。
匹配期间全部材料的 `forms()` 均为空；全部规则求值完成后统一发布最终集合。

- 默认条件始终为 `false`，该形态不会被选中。调用 `.appliesToAll()` 可设置为始终 `true`，
  自动匹配所有插件的材料；这是 `.appliesTo(mat -> true)` 的 `@SyntaxSugar`。
- `.appliesToData(TYPE...)` 是可选匹配索引提示：匹配器先按材料级数据类型过滤候选，再调用 `appliesTo`。
  提示必须是由谓词隐含的必要条件，谓词仍是最终判定；不声明提示时回退到全量材料扫描。
- `appliesTo(...)` 与 `appliesToAll()` 都设置同一个条件，以最后一次调用为准。
- `.form(form, options)` 只配置局部属性和外部覆盖，不跳过规则、不强制选中形态；未匹配形态的选项不参与内容生成和局部数据校验。
- 材料侧没有额外包含或否决通道。原版缺少某个条目不表示材料不适用该形态；匹配后可由策略生成新条目。
- 全部匹配形态按完整 ID 排序，不依赖形态注册或材料配置的到达顺序。
- 生成内容仍归材料所属插件的 `RegistryCore`；扩展形态不会改变材料的命名空间。
- 自动匹配后同样执行策略校验。缺少必要数据会报错，不会静默跳过形态。

:::tip
**覆盖外部物品** — 形态可以挂到既有物品上（如原版钻石），通过 ID 标签附加而不重复注册。
注册时用 `form(form, options -> options.overrideItem(target))` 附加覆盖目标，目标用
`ExternalTargets.item(Items.DIAMOND)` / `block(Blocks.DIAMOND_BLOCK)` 或
`ExternalTargets.item(id, item)` / `block(id, block)` 构造。
对象/供应器目标会延迟解析注册 ID；尚未绑定或可选模组目标使用 `itemSupplier(...)` / `blockSupplier(...)`，
并按 [兼容其他模组](../compatibility/other-mods.md#3-将外部物品或方块作为材料形态) 的分工处理标签与创造栏。
覆盖选项由具体策略读取并执行；仅在材料上声明选项不会自动跳过该策略的注册逻辑。
配置覆盖也不能绕过 `appliesTo`；先匹配成功，再由策略复用外部条目。
每个形态的外部 item/block/fluid override 只能声明一次，重复写入会失败。
材料冻结时会检查已匹配形态声明的**输出 key**；同一 `MaterialContentType + ID` 被两个形态或材料声明会失败。
形态策略通过 `MaterialFormPlan.outputs()` 声明输出，只有返回的输出才参与检查。Demo 的物品、方块和流体形态提供了相应示例。
未解析的 supplier target 以及形态未声明的额外输出不在检查范围内。
流体形态使用 `options.overrideFluid(target)`；可同时提供外部桶物品的 supplier，
未提供时创造栏仍由对方拥有。覆盖分支复用既有条目，不应重复注册或改写外部条目的名称。参见
[内容注册实作](../foundation/content-plugin.md) 所引用的 Demo `ItemForm` 与 `BlockForm`。
:::

## API 速查

| 类/方法                                                               | 用途                       |
|-----------------------------------------------------------------------|----------------------------|
| `MaterialFormStrategy(Collection<FormDataUse<?>>)`                    | 基类构造                   |
| `strategy.data(type)` / `.requireData(type)`                          | 形态参数读取               |
| `strategy.registryPath()`                                             | 注册 id 模板（`%s_ingot`） |
| `strategy.plan(material, form)`                                       | 构建冻结后的执行计划       |
| `MaterialFormPlan.outputs()` / `.validate()` / `.register(context)`   | 输出声明、校验、内容执行   |
| `MaterialContentKey(type, id)`                                        | 开放类型 + 注册 ID 输出 key |
| `MaterialContentTypes.ITEM` / `.BLOCK` / `.FLUID`                     | 内置内容类型（可自定义）   |
| `strategy.requireMaterialData(material, form, required)`              | 材质数据依赖校验           |
| `material.form(path).lang(en, cn).strategy(s).build()`                | 领域注册（钩子 3）         |
| `material.form(path).appliesTo(predicate)`                           | 按材料属性自动匹配         |
| `material.form(path).appliesToData(TYPE...)`                         | 可选匹配索引提示           |
| `material.form(path).appliesToAll()`                                 | 匹配所有材料（默认不匹配） |
| `MaterialLookup.current().byItem(...)` / `.byContent(...)`           | 按兼容物品/内容获取材料    |
| `MaterialFormOptions.overrideItem(target)` / `.overrideBlock(target)` / `.overrideFluid(target)` | 外部覆盖                   |
| `ExternalTargets.mcItem(path)` / `.mcBlock(path)`                     | 原版 ID 覆盖目标           |
| `ExternalTargets.item(ItemLike)` / `.item(id, ItemLike)`              | 直接对象覆盖目标           |
| `ExternalTargets.itemSupplier(...)` / `.blockSupplier(...)`           | 延迟对象覆盖目标           |
| `ExternalTargets.block(Block)` / `.block(id, supplier, itemSupplier)` | 方块覆盖目标               |
| `ExternalTargets.fluid(...)` / `.fluidSupplier(...)`                  | 流体覆盖目标               |
| `MaterialItemRender.apply(builder, material, form)`                   | 物品渲染（可读形态局部数据）|
| `MaterialBlockRender.apply(builder, material, form)`                  | 方块渲染（可读形态局部数据）|
