# 生成资源与发布包

面向框架开发者。本页记录 Topology 仓库的验证方式。

发布 CI 在构建前运行客户端数据生成；代码 PR/push 由 Validate 工作流检查，文档 PR 由 Deploy Docs 构建。发布包内容（署名、语言资源、TopoDemo 隔离）仍需在本地检查。
本地首次构建或修改内容声明后，由开发者手动执行：

```bash
./gradlew runClientData
./gradlew build demoClasses
```

数据生成会写入 `src/generated/resources/<modid>/`；TopoDemo 的原版矿石替换还会写入
`src/generated/resources/topodemo/data/minecraft/worldgen/configured_feature/`，用于把原版矿石 target 指向 Topo 方块。
普通本地 `build` 不自动运行数据生成；
验证干净检出的发布流程时应使用独立目录，避免依赖已有生成文件。
