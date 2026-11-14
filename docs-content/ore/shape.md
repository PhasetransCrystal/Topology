# 矿脉形状

面向需要自定义网格矿脉几何形状的模组开发者。`OreVeinShape.Strategy` 判断相对于矿脉中心的坐标是否位于形状内；半径由矿脉声明提供。

在 `registerOreFoundation` 中注册形状：

```java
OreVeinShape sphere = ore.shape("sphere", (dx, dy, dz, radius) ->
        dx * dx + dy * dy + dz * dz <= radius * radius);
```

在网格矿脉声明的 `.grid(mode, sphere)` 中使用返回的句柄。策略应对相同输入给出相同结果；它只判断几何范围，具体放置与冲突规则分别由[生成模式](mode.md)和[冲突策略](conflict.md)处理。

注册时机和其他扩展入口见[矿脉策略](ore-strategies.md)。
