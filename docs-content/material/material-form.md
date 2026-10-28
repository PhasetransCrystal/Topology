---
sidebar_position: 2
---

# MaterialForm

形态 = 一个 `MaterialFormStrategy` 实例 + 注册时的双语文案，可通过 `appliesTo(Predicate<Material>)` 自动选择材料。
每个匹配的材料只执行一次该形态策略；`appliesTo` 是选择形态的唯一来源。

本页是 [Material](../material.md) 模块的子页——概念与页面导航见 [Material](../material.md)。

## 基类

```java
public abstract class MaterialFormStrategy {

    protected MaterialFormStrategy(Collection<FormDataUse<?>> data);   // 类型化参数（如数量）

    public final <D> Optional<D> data(FormDataType<D> type);
    public final <D> D requireData(FormDataType<D> type);

    public abstract String registryPath();                             // "%s_ingot"——材质路径代入 %s
    public abstract void validateMaterial(Material material, MaterialForm form);   // 材质冻结时校验
    public abstract void register(MaterialContentContext context, MaterialForm form);  // 为当前材质铸造物品/方块

    protected final void requireMaterialData(Material material, MaterialForm form,
            List<MaterialDataType<?>> required);
}
```

`MaterialContentContext` 是 `record(RegistryCore core, Material material)`——`register` 里用
`context.core().item(...)` / `context.core().block(...)` 铸造注册项。
流体形式同样由策略在这里调用 `context.core().fluid(...)`，再按需要配置方块和桶；
框架不把流体形式自动转成物品形式，查询源流体使用 `MaterialHelper.fluid`／`requireFluid`。

## 生产级结构：Builder 模式

形态通常是**参数化模板**（锭/粉/宝石只是文案与标签不同）——用静态 `builder()` 组织，
build() 里做必填校验，与生产级物品形态实现同构：

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

    @Override public void validateMaterial(Material material, MaterialForm form) {
        // 例如：requireMaterialData(material, form, List.of(MyDataTypes.MAGIC));
    }

    @Override public void register(MaterialContentContext context, MaterialForm form) {
        String materialPath = context.material().id().getPath();
        context.core().item(String.format(registryPath, materialPath))
                /* builder 链（渲染/标签/创造栏） */
                .register();
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
        .appliesTo(mat -> mat.strategy().data(magicDataType).isPresent())
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

所有插件完成 `registerMaterials` 后，框架关闭数据写入，
先计算所有材料的最终形态，再统一校验，随后才生成内容。其他插件注入的数据同样参与规则判断。
规则必须无副作用，并基于材料的声明数据判断，不应读取其他规则推导的形态或尚未生成的注册项。
匹配期间全部材料的 `strategy().forms()` 均为空；全部规则求值完成后统一发布最终集合。

- 默认条件始终为 `false`，该形态不会被选中。调用 `.appliesToAll()` 可设置为始终 `true`，
  自动匹配所有插件的材料；这是 `.appliesTo(mat -> true)` 的 `@SyntaxSugar`。
- `appliesTo(...)` 与 `appliesToAll()` 都设置同一个条件，以最后一次调用为准。
- `.form(form, options)` 只配置局部属性和外部覆盖，不跳过规则、不强制选中形态；未匹配形态的选项不参与内容生成和局部数据校验。
- 材料侧没有额外包含或否决通道。原版缺少某个条目不表示材料不适用该形态；匹配后可由策略生成新条目。
- 全部匹配形态按完整 ID 排序，不依赖形态注册或材料配置的到达顺序。
- 生成内容仍归材料所属插件的 `RegistryCore`；扩展形态不会改变材料的命名空间。
- 自动匹配后同样执行策略校验。缺少必要数据会报错，不会静默跳过形态。

:::tip
**覆盖外部物品** — 形态可以挂到既有物品上（如原版钻石），通过 `core.existingItem` 附加标签而不重复注册。
注册时用 `form(form, options -> options.overrideItem(target))` 附加覆盖目标，目标用
`ExternalTargets.mcItem("diamond")` / `ExternalTargets.mcBlock("diamond_block")` 构造。
原版覆盖分支不能直接推广到尚未绑定的模组条目；按 ID 接入的做法见
[兼容其他模组](../compatibility/other-mods.md#3-将外部物品或方块作为材料形态)。
覆盖选项由具体策略读取并执行；仅在材料上声明选项不会自动跳过该策略的注册逻辑。
配置覆盖也不能绕过 `appliesTo`；先匹配成功，再由策略复用外部条目。
覆盖分支复用既有条目，不应重复注册或改写外部条目的名称。参见
[内容注册实作](../foundation/content-plugin.md) 所引用的 Demo `ItemForm` 与 `BlockForm`。
:::

## API 速查

| 类/方法                                                               | 用途                       |
|-----------------------------------------------------------------------|----------------------------|
| `MaterialFormStrategy(Collection<FormDataUse<?>>)`                    | 基类构造                   |
| `strategy.data(type)` / `.requireData(type)`                          | 表单参数读取               |
| `strategy.registryPath()`                                             | 注册 id 模板（`%s_ingot`） |
| `strategy.validateMaterial(material, form)`                           | 材质冻结时校验             |
| `strategy.register(context, form)`                                    | 每材质铸造一次             |
| `strategy.requireMaterialData(material, form, required)`              | 材质数据依赖校验           |
| `material.form(path).lang(en, cn).strategy(s).build()`                | 领域注册（钩子 3）         |
| `material.form(path).appliesTo(predicate)`                           | 按材料属性自动匹配         |
| `material.form(path).appliesToAll()`                                 | 匹配所有材料（默认不匹配） |
| `MaterialFormOptions.overrideItem(target)` / `.overrideBlock(target)` | 外部覆盖                   |
| `ExternalTargets.mcItem(path)` / `.mcBlock(path)`                     | 原版覆盖目标               |
