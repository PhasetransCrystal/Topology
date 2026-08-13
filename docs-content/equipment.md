---
sidebar_position: 1
---

# Equipment

设备系统基于 Material 上声明的 **MaterialData** 自动生成工具、武器、护甲等物品。
一种设备种类 × 一种材料 = 一个物品，全自动。

## 工作原理

```
Material "copper"  +  MyDataTypes.toolStats().stats(...)
                    ↓
Equipment "pickaxe"  +  Material "copper"  →  铜镐物品
Equipment "sword"    +  Material "copper"  →  铜剑物品
Equipment "helmet"   +  Material "copper"  →  铜头盔物品
```

## 页面导航

| 页面 | 内容 | 何时阅读 |
|------|------|----------|
| **Equipment（本页）** | `EquipmentStrategy` 接口、builder 式种类注册、查询 | 添加工具/护甲种类 |
| [Equipment Behavior](equipment/equipment-behavior.md) | `MachineItemBehavior` 右键机器行为（扳手/调节器） | 装备需要机器交互 |
| [Equipment Tooltips](equipment/equipment-tooltips.md) | `tooltipLine` 功能说明行、悬浮面板注册、固定标签键 | 装备需要悬浮信息 |

## 接口

`EquipmentStrategy` 策略回答两个问题：**适用谁**、**怎么铸造**：

```java
public interface EquipmentStrategy {

    boolean appliesTo(Material material);
    // 依据材质数据判断，例如：
    // material.strategy().data(MyDataTypes.TOOL_STATS).isPresent()

    void register(MaterialContentContext context, Equipment self);
    // 为 (材料, 本种类) 铸造物品：context.core().item(...)

    default @Nullable MachineItemBehavior machineBehavior() { return null; }
    // 可选：右键机器时的行为（注册细节见 Equipment Behavior 页）
}
```

## 生产级结构：Builder 模式

装备种类通常是**可复用族**（镐/斧/锄只有参数不同）——用静态 `builder()` + 参数化 Builder 组织，
与生产级工具族实现同构：

```java
public final class ToolEquipment implements EquipmentStrategy {

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean appliesTo(Material material) {
        return material.strategy().data(MyDataTypes.TOOL_STATS).isPresent();
    }

    @Override
    public void register(MaterialContentContext context, Equipment self) {
        // 按 registryPath 模板（%s = 材质名）为每个 (材料, 种类) 铸造物品
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

引擎随后冻结种类表，并对每个 (材料 × 种类) 调用 `appliesTo` → `register`。

## 查询

```java
Equipment pickaxe = EquipmentRegistry.require(Identifier.fromNamespaceAndPath("mymod", "pickaxe"));

for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
    Equipment kind = record.equipment();
    Material mat = record.material();
    Item item = record.entry().get();
}
```

## API 速查

| 类/方法                                            | 用途               |
|----------------------------------------------------|--------------------|
| `EquipmentDomainRegistration.kind(path, strategy)` | 注册设备种类       |
| `EquipmentRegistry.require(id)`                    | 按 ID 查找设备种类 |
| `EquipmentRegistry.itemRecords()`                  | 所有已生成设备物品 |
| `EquipmentStrategy.appliesTo(material)`            | 判断该材料是否适用 |
| `EquipmentStrategy.register(context, self)`        | 为指定材料生成物品 |
| `EquipmentStrategy.machineBehavior()`              | 右键机器行为（可选） |
