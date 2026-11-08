# 检测重构的当前实现

面向框架开发者；保留已有来源和适用范围。


- `CellPredicate<I>.evaluate(input, context, metadata)` 是无内容绑定的检测算法；`CellBinding` 绑定输入、候选、角色和字段声明，`Cell.binding()` 返回内容声明。
- 统一结果为 `CellResult` 三态、命名空间错误码、期望／实际数据及子错误；`RecognitionResult.CellIssue` 补充位置，失败与未知样本分别有界保存。诊断及工具不重新猜测失败原因。`StructureView` 中已加载空气应返回空气状态，`null` 表示状态事实缺失，不等同于空气。
- 控制器在已有组件数据域的 `structure_metadata` 分组声明通用 `DataValue<T>`。`DataKey<T>` 提供 Codec 与复制函数；工作数据使用 `DataTransaction`，发布使用 `DataSnapshot`，正式值仍由 `MachineDataScope` 保存。
- 已探测事实与声明默认值分开记录；失败 Cell、组合分支、增长试探及可选模块写入回滚。成型周期验证保留基线；显式新探测使用 `requestProbe()`，未知期间保留旧正式数据。
- `StructureDetection` 比较本体与全部模块的整体变换；主体对称时结合模块数量选择方向，不允许模块各自镜像。
- 机器预览以控制器本体正向为坐标基准，先应用声明 frame 并保留整体镜像，去除世界旋转；控制器本体固定朝 SOUTH。选中、问题定位和实建图沿同一坐标转换。
- 客户端预览不能依赖服务端专属的 `controller.repeatResolution()` 或打开界面时的最小布局。`MultiblockClientDiagnosis.Frame` 保留本次整体检测的本体及模块结果，各自的布局、视图与 frame 用于实建、所需图及点选；布局变化同步更新场景范围、镜头和切层。报告成型统计仍针对本体，可选模块缺失不使本体失败。
- 通用结构示例由 `MultiblockPreviewPage` 提供，JEI 只承载界面；示例模块／重复数选择与实际检测配置独立。材料表按候选、角色及其数量限制分组，共享位置只计一次。
- 多方块 JEI 接入属于 Topo：`TopoMultiblockJeiPlugin` 收集所有已注册且声明控制器元数据的机器，内容模组不重复注册结构展示。全部结构共用 `topo:multiblock` 分类，控制器按条目注册 OUTPUT／CRAFTING_STATION 查询索引，不能按机器拆分类或注册分类级控制器。Topo 的客户端 Mixin 仅对结构分类按当前可见预览生成原生工作方块栏，翻页、返回或改变查询时沿 JEI 布局刷新；多条可见预览时显示其去重控制器。分类、语言键和注入配置随框架发布，Demo 只维护自身生产配方及矿脉 JEI 接入。

来源：本次检测重构的源码及 [多方块蓝图](../../docs-content/machine/multiblock.md)。事实适用于当前实现，运行结果仍以手动测试为准。

