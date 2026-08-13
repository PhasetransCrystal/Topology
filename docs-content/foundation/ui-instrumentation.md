---
sidebar_position: 7
---

# UI Instrumentation

库的 UI 代码（工具提示、配方页等）在构建元素树时主动发出 `instrument(tag, root)` 钩子。你的 Mod
安装一次仪表器即可接入——**未安装时零开销**。

## API

```java
public final class ExternalUiInstrumentation {

    public interface Instrumenter { void instrument(String tag, UIElement root); }

    public static void install(Instrumenter instrumenter);   // 安装一次（开发期探针）
    public static void instrument(String tag, UIElement root);  // API 运行时构建 UI 树时调用
}
```

## 使用

```java
// 内容侧的调试探针（如性能剖析器）在 bootstrap 时安装：
ExternalUiInstrumentation.install((tag, root) -> {
    // 统计元素树、找布局振荡等
});
```

:::tip
正式玩法代码不需要碰这个 API。它只服务于开发期探针（工具提示面板、Jade 面板等框架 UI 的性能取证）。
:::

## API 速查

| 类/方法                                | 用途                            |
|----------------------------------------|---------------------------------|
| `ExternalUiInstrumentation.install(Instrumenter)` | 安装仪表器（一次）    |
| `ExternalUiInstrumentation.instrument(tag, root)` | API 运行时 UI 钩子     |
