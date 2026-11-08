# 矿脉冲突策略

面向需要控制网格矿脉重叠时的替换行为的模组开发者。`OreConflictPolicy.Strategy` 根据候选矿脉和已有矿脉的优先级，判断候选是否可以替换同一位置的已有规划。

在 `registerOreFoundation` 中注册策略：

```java
OreConflictPolicy replaceLowerOrEqual = ore.conflict("replace_lower_or_equal",
        (candidatePriority, existingPriority) -> candidatePriority >= existingPriority);
```

在网格矿脉声明中用 `.priority(...)` 设置优先级，并用 `.conflict(replaceLowerOrEqual)` 指定策略。示例允许替换相同优先级；只允许替换低优先级时应使用 `>`。

矿脉的其他声明见[矿脉注册](../ore.md)。
