---
sidebar_position: 1
---

# Equipment

面向需要为材料生成装备物品的模组开发者。装备策略依据 `Material` 上的数据选择适用材料，并为每个匹配的“装备种类 × 材料”组合注册一个物品。工具、武器或护甲的具体行为由策略实现。

## 工作原理

```
Material "copper"  +  MyDataTypes.toolStats().stats(...)
                    ↓
Equipment "pickaxe"  +  Material "copper"  →  铜镐物品
Equipment "sword"    +  Material "copper"  →  铜剑物品
Equipment "helmet"   +  Material "copper"  →  铜头盔物品
```

## 页面导航

| 页面                                                  | 内容                                               | 何时阅读          |
|-------------------------------------------------------|----------------------------------------------------|-------------------|
| **Equipment（本页）**                                 | `EquipmentStrategy` 接口、builder 式种类注册、查询 | 添加工具/护甲种类 |
| [Equipment Behavior](equipment/equipment-behavior.md) | `MachineItemBehavior` 右键机器行为（扳手/调节器）  | 装备需要机器交互  |
| [Equipment Tooltips](equipment/equipment-tooltips.md) | 面板提供器与功能说明行 | 装备需要悬浮信息  |

## 接口

`EquipmentStrategy` 策略回答两个问题：**适用谁**、**怎么铸造**：

```java
public interface EquipmentStrategy {

    boolean appliesTo(Material material);
    // 依据材质数据判断，例如：
    // material.data(MyDataTypes.TOOL_STATS).isPresent()

    ItemEntry<Item> register(MaterialContentContext context, Equipment self);
    // 为 (材料, 本种类) 铸造并返回唯一物品：context.core().item(...).register()

    default @Nullable MachineItemBehavior machineBehavior() { return null; }
    // 可选：右键机器时的行为（注册细节见 Equipment Behavior 页）
}
```

## 示例：Builder 模式

装备种类可以使用静态 `builder()` 和参数化 Builder 组织。下例只展示装备策略的组织方式：
物品属性、名称、模型、工具行为和配方仍需由模组补充，示例中的倍率字段也不会自动生效。

```java
public final class ToolEquipment implements EquipmentStrategy {

    private final String registryPath;

    private ToolEquipment(Builder builder) {
        this.registryPath = builder.registryPath;
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean appliesTo(Material material) {
        return material.data(MyDataTypes.TOOL_STATS).isPresent();
    }

    @Override
    public ItemEntry<Item> register(MaterialContentContext context, Equipment self) {
        // 按 registryPath 模板（%s = 材质名）为每个 (材料, 种类) 铸造物品并返回 entry
        return context.core().item(registryPath.formatted(context.material().id().getPath())).register();
    }

    @Override
    public @Nullable MachineItemBehavior machineBehavior() { return null; }

    public static final class Builder {
        private String registryPath;                    // "%s_pickaxe" —— %s 代入材质路径
        private String displayNameEn, displayNameCn;    // 双语文案（%s 代入材质名）
        private float durabilityMultiplier = 1.0f;
        private float miningSpeedMultiplier = 1.0f;

        private Builder() {}

        public Builder registryPath(String pattern) { this.registryPath = pattern; return this; }
        public Builder displayName(String enPattern, String cnPattern) {
            this.displayNameEn = enPattern;
            this.displayNameCn = cnPattern;
            return this;
        }
        public Builder durabilityMultiplier(float factor) { this.durabilityMultiplier = factor; return this; }
        public Builder miningSpeedMultiplier(float factor) { this.miningSpeedMultiplier = factor; return this; }

        public ToolEquipment build() {
            // build() 里做必填校验（registryPath / displayName），缺一抛异常
            Objects.requireNonNull(registryPath, "registryPath required");
            Objects.requireNonNull(displayNameEn, "displayName(en, cn) required");
            Objects.requireNonNull(displayNameCn, "displayName(en, cn) required");
            return new ToolEquipment(this);
        }
    }
}
```

## 注册

注册时每种装备一个 builder 链——参数化复用，不写死九个类：

```java
// registerEquipmentKinds（EquipmentRegistry.freeze() 之前）：
equipment.kind("pickaxe",
        ToolEquipment.builder()
                .registryPath("%s_pickaxe")
                .displayName("%s Pickaxe", "%s镐")
                .durabilityMultiplier(2.0f)
                .build());

equipment.kind("axe",
        ToolEquipment.builder()
                .registryPath("%s_axe")
                .displayName("%s Axe", "%s斧")
                .durabilityMultiplier(1.5f)
                .build());
```

引擎随后冻结种类表，对每个 (材料 × 种类) 调用 `appliesTo`，仅在匹配时调用 `register`。策略只返回 item entry；
框架统一发布不可变记录，因此不会出现漏登记或重复登记。

生成策略应使用材料的 `context.core()`，这样扩展其他插件材料时保留其归属。跨插件用法见
[兼容其他 TopoPlugin](compatibility/topo-plugins.md)。

## 查询

下列代码中 `.get()` 需要等待物品绑定，适合放在 `registerRuntime`；此前保留 `record.entry()`：

```java
Equipment pickaxe = EquipmentRegistry.require(Identifier.fromNamespaceAndPath("mymod", "pickaxe"));
Material copper = MaterialRegistry.require(Identifier.fromNamespaceAndPath("mymod", "copper"));

List<EquipmentRegistry.EquipmentItemRecord> byKind = EquipmentRegistry.itemsFor(pickaxe);
List<EquipmentRegistry.EquipmentItemRecord> byMaterial = EquipmentRegistry.itemsFor(copper);

for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
    Equipment kind = record.equipment();
    Material mat = record.material();
    Item item = record.entry().get();
}
```

`itemRecords()`、`itemsFor(...)` 都是发布后的不可变视图；物品尚未绑定时继续使用 `record.entry()`。

## API 速查

| 类/方法                                            | 用途                 |
|----------------------------------------------------|----------------------|
| `EquipmentDomainRegistration.kind(path, strategy)` | 注册设备种类         |
| `EquipmentRegistry.require(id)`                    | 按 ID 查找设备种类   |
| `EquipmentRegistry.itemRecords()`                  | 所有已生成设备物品（不可变） |
| `EquipmentRegistry.itemFor(itemId)`                | 按物品 ID 查询生成记录       |
| `EquipmentRegistry.itemsFor(material/equipment)`   | 按材料或种类查询物品记录     |
| `EquipmentStrategy.appliesTo(material)`            | 判断该材料是否适用           |
| `EquipmentStrategy.register(context, self)`        | 为指定材料生成唯一物品并返回 entry |
| `EquipmentStrategy.machineBehavior()`              | 右键机器行为（可选） |
