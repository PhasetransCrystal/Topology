# 机器能力的声明式组合

面向框架开发者。说明机器定义如何组合行为组件，以及声明期元数据如何参与机器注册。下游组件构造与生命周期用法见 [`docs-content/machine/machine-component.md`](../../docs-content/machine/machine-component.md)。

## 组合边界

`MachineDefinition` 保存有序的 `ComponentMount<?>` 列表。一个 mount 同时给出：

- `ComponentKey<T>`：该组件在机器定义中的稳定身份，也用于运行时 typed lookup 和组件数据域。
- 工厂 `Function<ComponentContext<T>, T>`：每个 BlockEntity 实例创建自己的组件对象。
- `Attachment` 列表：注册期可读取的静态声明，例如资源端口元数据、配方逻辑元数据。

典型声明由 key、mount、builder 三步组成：

```java
ComponentKey<CompressorLogic> key = ComponentKey.id("compressor_logic", CompressorLogic.class);
ComponentMount<CompressorLogic> mount = key.mount(CompressorLogic::new, recipeLogicMetadata);

MachineDefinition definition = Machines.builder(id, registry)
        .component(mount)
        .build();
```

`MachineComponent` 构造器从 `ComponentContext` 取得 key、宿主与按 key 分区的数据域；不要在定义构建时直接 `new` 组件。

组件适合表达可独立复用的机器行为，例如配方逻辑、资源端口、渲染或 UI 贡献。它不是把任意参数塞进上下文的容器：能力应由组件负责，跨组件协作通过明确的 key、service 或其他窄契约完成。

## 声明数据流

1. `Machines.Builder.component(...)` 收集 `ComponentMount` 或可展开为 mount 的 `ComponentContribution`。builder 立即按 ID 拒绝重复 key；`ComponentContribution.mount()` 必须无副作用且立即返回具体 mount。
2. `MachineDefinition` 复制 mount 列表，保持声明顺序，并从 mount 的 `Attachment` 派生资源端口、配方逻辑及配方类型等注册期视图。
3. 注册流程使用定义中的元数据配置方块与注册表。例如，`MachineDefinition` 从 `ResourcePortMetadata` 导出端口目录，从 `RecipeLogicMetadata` 推导该机器支持的配方类型。声明阶段不创建每个世界方块实例的组件。
4. `MachineBlockEntity` 创建时才调用 mount 工厂，为该机器实例生成运行时组件。

这条边界使注册代码能检查声明信息，而实例状态只在拥有它的 BlockEntity 上创建。

## 为什么这样设计

- 不同机器可组合相同能力，而不需要为每种能力组合增加一个 BlockEntity 子类。
- 有序声明使创建、生命周期分发和聚合顺序可预测。
- `Attachment` 将注册期需要读取的静态信息与运行时组件实例分开，注册器无需提前构造游戏对象。
- 稳定 key 既用于机器实例内的查找，也参与组件数据域身份；重命名需考虑存档兼容。

## 联动与扩展

- 配方逻辑通过声明元数据让 `MachineDefinition` 暴露支持的配方类型，运行时执行由组件与配方能力协作完成。
- 资源端口通过端口元数据进入机器能力面；管道和自动化调用端口契约，不应依赖具体组件类。
- 渲染和 UI 通过组件贡献接入对应收集器，避免在 `MachineBlock` 或通用宿主中增加特定机器分支。
- 新增可组合能力时，先定义组件自身契约；只有注册期确实需要的信息才定义为 `Attachment`，运行时实例依赖放在组件内。

## 常见坑

- 在注册期运行组件工厂：工厂属于 BlockEntity 实例化阶段，注册表遍历不应创建世界实例状态。
- 把需要运行时变化的值放入 `Attachment`：声明元数据随定义共享，动态状态应由实例组件或其数据域保存。
- 用单一“大组件”承担机器所有行为：这会重新引入原本要消除的跨能力分支；按可独立复用职责拆分。
- 仅为了避免新增接口而把无关数据放进 `ComponentContext`：上下文只承载构造组件普遍需要的身份、宿主与数据域。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/machine/Machines.java`：机器声明 builder 与组件贡献收集。
- `src/main/java/net/ptcrys/topo/api/machine/MachineDefinition.java`：不可变 mount 列表及声明元数据派生。
- `src/main/java/net/ptcrys/topo/api/machine/component/ComponentMount.java`：组件工厂与 mount 元数据。
- `src/main/java/net/ptcrys/topo/api/machine/component/ComponentContribution.java`：面向定义的组合贡献。
- `src/main/java/net/ptcrys/topo/api/machine/component/Attachment.java`：声明期元数据契约。

`Machines.Builder.build()` 还会检查可隔离资源端口的 `RecipeRole`，并在用户未显式声明时自动挂载 `MachineSearchPoolConfig`：存在输入端口时默认 `DEFAULT`，纯输出端口默认 `UNIVERSAL`；没有可隔离输入/输出端口则不挂载。这是 builder 对声明元数据的组合规则，不能在组件工厂里补做。
