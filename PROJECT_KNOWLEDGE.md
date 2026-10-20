# 项目约定

适用范围：Topo 作为 NeoForge 26.1 依赖库，由 `topo` 单宿主加载。

- 下游只在 Mod 构造期登记 Plugin；事件接线、阶段依赖和冻结由宿主管理。
- Plugin 只描述贡献；领域 API 由框架持有，具体策略与内容放在对应领域目录。
- 内容声明期使用 Entry / Supplier；需要游戏对象的绑定放入运行时钩子。
- 局部路径用 String，完整资源标识用 Identifier；跨阶段引用游戏条目保留 ResourceKey 及其注册表身份。
- 游戏条目名称随 RegistryLib Builder 提交；Topo LangKey 只承担独立文本，二者不可重复持有同一键。
- 新领域通过内部注册模块接入，先定义依赖及冻结责任；启动失败后不重试。
- TopoDemo 仅供本地开发，不进入发布产物；`api/` 放扩展，`data/` 放内容。
- 数据生成输出与手写资源隔离；各模组及客户端/服务端使用独立输出目录，避免缓存清理误删。
- 编译与格式检查：`./gradlew compileJava compileTestJava demoClasses spotlessCheck`；运行测试按 AGENTS.md 交由用户手动执行。

来源与细节：[生命周期](docs-content/foundation/lifecycle.md)、[开发配置](gradle/scripts/moddevgradle.gradle)、[AGENTS.md](AGENTS.md)。
