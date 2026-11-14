# 配方类型内部职责

面向框架开发者，适用于当前配方类型的实现维护。

内部实现按职责拆分：`RecipeCatalog` 管理本地声明/去重/替换，`RecipeImports` 管理外部导入导出投影，
`RecipeSearchState` 管理运行时搜索索引；`TopoRecipeType` 只保留公共句柄与配置入口。

`vanilla.VanillaExportedRecipeAdapters` 提供原版有序、无序合成和烹饪的导出适配；其写出口、提示边界与导入互斥规则见[配方导出投影](export-projection.md)。

## 三块状态各自拥有的内容

- `RecipeCatalog` 是一个 `TopoRecipeType` 的声明期目录：按 recipe key 缓存工厂，创建游戏注册项的 recipe supplier，并记录哪些本地配方声明有权替换导入冲突。它不负责查询机器库存。
- `RecipeImports` 保存外部 `RecipeType` 来源和适配器。收集 holder 时先取得本类型原生配方，再把外部 holder 转成 Topo 配方 holder；不会复制或改写来源 JSON。导出配置也在此处进入对应的外部适配器。
- `RecipeSearchState` 只持有服务端运行时 `TopoRecipeSearchIndex` 的惰性缓存、所属 server token 和 revision。它通过 holder source 向 `TopoRecipeType` 请求当前投影结果，不拥有声明或导入配置。
- `TopoRecipeType` 是组合外观：对外提供类型句柄与配置入口，把声明、来源投影和搜索缓存委托给上述对象。

## 本地声明与重复 recipe key

`RecipeCatalog` 对同一 recipe key 只向 RegistryLib 注册一个 supplier，但允许多个贡献方向该 key 加 factory。解析时以首个 factory 产出的配方内容为基准；后续 factory 必须给出相同 duration、开始/逐 tick 输入输出及能力定义的内容身份，否则报冲突。生产线集合则合并去重。直接对象注册和延迟 `HolderLookup.Provider` 工厂最终进入同一目录。

`replacesImported()` 记的是本类型 recipe key 的冲突授权，不表示删除原版或来源模组的 recipe。holder 投影阶段仅用该配方的输入冲突键抑制相撞的导入配方；其他相同输入的本地配方仍由冲突规则检查。

## 外部投影和运行时索引

导入 adapter 可以跳过不支持的外部 recipe（返回 `null`），转换后的 holder ID 将本地类型 path 与来源 namespace/path 分段编码。来源类型本身不进入该 ID，因此不同导入类型若包含相同来源 ID 会在收集时触发 ID 冲突。转换后若与本地配方输入冲突且没有替换标记，收集阶段会失败并提示冲突键。导出则要求注册对应 adapter；vanilla-facing 类型缺 adapter 或 adapter 返回空 ID 都是硬错误。

运行时搜索索引第一次查询时按服务端构建；换 server、显式重建或 invalidate 都递增 revision。配方 holder 的来源是 `RecipeImports` 的投影，不应由索引类重新实现导入、替换和导出规则。

## 为什么拆开

声明表的去重策略、外部 holder 的转换语义和机器搜索优化有不同生命周期。拆开后，同一 `TopoRecipeType` 可以统一呈现三者，但修改搜索索引无需改变导入兼容，新增 adapter 也无需让目录持有服务端缓存。

## 常见坑

- 把 `replacesImported` 理解为全局删除来源 recipe：它仅对当前 Topo 类型、当前 recipe key 的输入冲突投影生效。
- 在搜索缓存里补做导入适配：holder 投影应先完成，再将结果交给搜索索引。
- 为每个重复贡献注册多个 recipe supplier：目录按稳定 recipe key 合并工厂，只注册一个 supplier。
- 将配方内容冲突和生产线贡献视为同一规则：内容必须一致，生产线可以合并。
- 将 import adapter 返回 `null` 与 export adapter 返回 `null` 等同：前者表示跳过来源内容，后者是声明过的导出失败。

## 实现位置

- `src/main/java/net/ptcrys/topo/api/recipe/TopoRecipeType.java`：对外句柄与三块状态的组合入口。
- `src/main/java/net/ptcrys/topo/api/recipe/RecipeCatalog.java`：本地声明、去重和替换标记。
- `src/main/java/net/ptcrys/topo/api/recipe/RecipeImports.java`：外部 holder 投影和导出委托。
- `src/main/java/net/ptcrys/topo/api/recipe/RecipeSearchState.java`：服务端搜索索引缓存与 revision。
