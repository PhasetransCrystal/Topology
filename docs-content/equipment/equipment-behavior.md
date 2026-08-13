---
sidebar_position: 2
---

# Equipment Behavior

装备右键机器时的行为——扳手拆机、调节器启停都走这个插座。本页覆盖 `MachineItemBehavior` 的实现与注册。

本页是 [Equipment](../equipment.md) 模块的子页——概念与页面导航见 [Equipment](../equipment.md)。

## 接口

```java
@FunctionalInterface
public interface MachineItemBehavior {

    InteractionResult useOnMachine(MachineBlockEntity machine, Player player, ItemStack stack,
                                   InteractionHand hand, BlockHitResult hit);
}
```

**双端分派约定**：`MachineBlock.useItemOn` 是唯一分派点，客户端与服务端都会执行。实现必须在两端
做出相同决策：

- 客户端：判断「我会处理」→ 返回 `SUCCESS`，**不改状态**
- 服务端：执行变更，返回 `CONSUME`
- 不处理 → 返回 `PASS`，落回机器默认交互（打开 UI）

## 生产级实现：调节器行为

```java
public final class RegulatorBehavior implements MachineItemBehavior {

    public static final RegulatorBehavior INSTANCE = new RegulatorBehavior();

    @Override
    public InteractionResult useOnMachine(MachineBlockEntity machine, Player player, ItemStack stack,
                                          InteractionHand hand, BlockHitResult hit) {
        List<ServiceMatch<MachineWorkControl>> controls =
                machine.machineComponents().services(MachineWorkControl.KEY, null);
        if (controls.isEmpty()) {
            return InteractionResult.PASS;      // 无工作控制的机器不归我管
        }
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;   // 客户端：会处理，但不改状态
        }
        WorkMode result = WorkMode.RUNNING;
        for (ServiceMatch<MachineWorkControl> control : controls) {
            result = control.value().toggleWorkMode();
        }
        stack.hurtAndBreak(1, player, hand);
        player.sendOverlayMessage(
                (result == WorkMode.HALTED ? HALTED_KEY : RESUMED_KEY).getComponent());
        return InteractionResult.CONSUME;       // 服务端：变更完成
    }
}
```

## 注册

行为**不会自动注册**——物品绑定后（`FMLCommonSetup.enqueueWork`）遍历设备物品表：

```java
for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
    MachineItemBehavior behavior = record.equipment().strategy().machineBehavior();
    if (behavior != null) {
        MachineItemBehaviors.register(record.entry().get(), behavior);
    }
}
MachineItemBehaviors.freeze();
```

:::caution
`MachineItemBehaviors.freeze()` 后不可再注册——查找路径每次右键都走一次冻结表读取，必须零开销。
:::

## 反馈语言键

行为的玩家反馈键走 `equipment` 分类（不是 tooltip 分类）：

```java
// registerLang 或行为声明点：
HALTED_KEY = lang.key("equipment", "regulator.halted", "Machine halted", "机器已停止");
RESUMED_KEY = lang.key("equipment", "regulator.resumed", "Machine resumed", "机器已恢复");
```

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `MachineItemBehavior.useOnMachine(...)`| 右键机器行为（双端分派）        |
| `MachineItemBehaviors.register(item, behavior)` | 注册物品行为           |
| `MachineItemBehaviors.find(item)`      | 查询（每次右键一次冻结表读取）  |
| `MachineItemBehaviors.freeze()`        | 冻结（注册后调用）              |
