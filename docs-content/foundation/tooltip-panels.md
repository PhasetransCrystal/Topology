---
sidebar_position: 4
---

# Tooltip Panels

物品悬浮面板（LDLib2 元素树渲染进物品 tooltip）。注册一个「物品 → 面板提供器」的映射，框架处理缓存、渲染与
错误隔离。

## TopoTooltipUiProvider — 提供器

```kotlin
fun interface TopoTooltipUiProvider {
    fun build(stack: ItemStack): UIElement              // 构建面板元素树
    fun cacheKey(stack: ItemStack): Any = stack.item    // 缓存键：同键复用已构建组件
}
```

```kotlin
ItemTooltipUis.register(myItem) { stack ->
    // 返回你的 LDLib2 面板
    buildMyPanel(stack)
}
```

## 注册与冻结

```java
// 在你的 Mod 构造器内接线；游戏注册表绑定后在主线程执行：
modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
    ItemTooltipUis.register(MY_ITEM.get(), MyTooltipProvider.INSTANCE);
}));
```

共享表在 load complete 由 `TopoPluginEngine` 统一冻结，插件不要自行调用 `freeze()`。
common / client setup 的 `enqueueWork` 注册均在冻结窗口内；无需争抢管道模块的监听顺序。
不同 Mod 的任务间无顺序承诺；语言键须在内容阶段提前声明。

## 缓存语义

- 键 = `provider.cacheKey(stack)`；同键复用已构建的 `TooltipComponent`
- 缓存是 LRU（容量 4）；逐出时框架调用 `MachineUiTooltipTemplate.dispose` 释放元素树
- 构建失败按物品去重记日志并抑制该面板，不打断游戏
- 渲染线程专用（`componentFor` 仅限客户端渲染线程）

## API 速查

| 类/方法                                   | 用途                               |
|-------------------------------------------|------------------------------------|
| `ItemTooltipUis.register(item, provider)` | 按物品注册面板                     |
| `ItemTooltipUis.freeze()`                 | 冻结（引擎在 load complete 触发） |
| `ItemTooltipUis.find(stack)`              | 按物品栈查找提供器                 |
| `ItemTooltipUis.componentFor(stack)`      | 构建缓存组件（渲染线程专用）       |
