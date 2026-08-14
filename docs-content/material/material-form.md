---
sidebar_position: 2
---

# MaterialForm

形态 = 一个 `MaterialFormStrategy` 实例 + 注册时的双语文案。每个材质声明引用该形态后，框架对每个材质调用一次策略。

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

## 注册

```java
// registerMaterialFoundation（MaterialFormRegistry.freeze() 之前）：
orbForm = material.form("orb")
        .lang("Orb", "宝珠")
        .strategy(OrbForm.builder()
                .registryPath("%s_orb")
                .displayName("%s Orb", "%s宝珠")
                .build())
        .build();          // 返回 MaterialForm 句柄

// registerMaterials：材质引用表单
m.material("moonstone").lang("Moonstone", "月长石").form(orbForm).build();
```

:::tip
**覆盖外部物品** — 形态可以挂到既有物品上（如原版钻石），通过 `core.existingItem` 附加标签而不重复注册。
注册时用 `form(form, options -> options.overrideItem(target))` 附加覆盖目标，目标用
`ExternalTargets.mcItem("diamond")` / `ExternalTargets.mcBlock("diamond_block")` 构造。
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
| `MaterialFormOptions.overrideItem(target)` / `.overrideBlock(target)` | 外部覆盖                   |
| `ExternalTargets.mcItem(path)` / `.mcBlock(path)`                     | 原版覆盖目标               |
