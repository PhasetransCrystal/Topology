# 多方块蓝图

面向框架开发者；保留已有来源和适用范围。


- 用户确认现有蓝图功能基本成立，仅有少量问题。审查和重构以现有功能为基础，重点优化实现；具体取舍在对应方案中说明，不在通用文档或记忆中预设替代模型。
- 蓝图使用控制器相对局部坐标。现有 `aisle` 沿声明坐标 +Y 堆叠 XZ 字符层；`frame(up, front)` 提供 24 种声明旋转，有效旋转为控制器朝向与 frame 的组合。
- 镜像是结构检测流程的一部分，不是蓝图许可开关；它作用于整个多方块结构，本体与全部模块共享同一个镜像变换，不能分别镜像。运行时、客户端诊断与工具实际成型检测的朝向选择须遵守这一整体约束。
- 当前声明支持多层整体重复、可选段、后续内容随重复次数推移，以及带依赖关系的可选叠加模块。
- `@`、`#` 自动定义为控制器、空气，不允许 `where` 重定义；空格忽略位置。
- 组合回归与 Demo 蓝图最小尺寸至少为 4×4×6；主集成场景为 5×5×6，覆盖 24 向、重复组、空气内腔、方向状态及部件计数。小型单元用例用于边界定位。
- 占地索引不读取或持有世界；只对独占部件建立位置到所有者的稀疏索引，提交时校验全部冲突并整体替换预留。
- CellBinding 的候选和诊断图标仅保存方块声明，`ItemStack` 由展示层创建；NeoForge 单元测试完成注册并不代表物品组件已经绑定。检测器不再提供候选或角色内容，接口用法见多方块文档。
- 局部布局统一由 `BlueprintLayout` 处理；移动布局使用实时视图，稳定布局可采集不可变快照。固定次数重复段只产生常量位移，不会因后接内容或控制器而变成移动布局；可变次数段仍须检查后续位置及锚点。快照构建器发布后失效。

来源：用户于 2026-11-06 至 2026-11-07 的直接说明；现有 [Blueprint](../../src/main/java/net/ptcrys/topo/api/machine/multiblock/pattern/Blueprint.java)、[MultiblockModule](../../src/main/java/net/ptcrys/topo/api/machine/multiblock/MultiblockModule.java) 和 [MultiblockRecheck](../../src/main/java/net/ptcrys/topo/api/machine/multiblock/MultiblockRecheck.java)。适用于多方块模块维护；使用方式见 [多方块蓝图](../../docs-content/machine/multiblock.md)。

