# 语言与显示名模块内部

面向框架开发者。语言模块负责键铸造、键族、中文转换与条目显示名来源。

## 职责与边界

- 负责：语言键注册与导出、键族、中文转换、条目显示名来源与模板。
- 不负责：生成条目的注册本身；条目名由生成它的 RegistryLib Builder 持有。
- 内部包：`api/api/lang`（键与注册表）、`api/lang`（显示名）。

## 关键类型与数据流

- `LangDomainRegistration`：`key` / `resource` / `absolute` 三种铸造形态。
- `LangKey` / `LangRegistry` / `LangKeyFamily`：键句柄、冻结注册表、枚举/派生分组。
- `DisplayNames` / `DisplayNameSource` / `TemplateDisplayName`：条目显示名来源。
- 数据流：领域在 `registerLang` 铸造键 → `LangRegistry` 冻结并导出。

## 为什么这样设计

- 键铸造集中在 `LangDomainRegistration`，调用方不自行拼 namespace，避免跨插件键污染。
- 生成条目名与独立 `LangKey` 冲突在声明期拒绝，避免同一条目两个来源。
- `--mod` 只导出声明者拥有的键，使资源包归属可预测。

## 与其他模块的联动

- 所有领域在 `registerLang` 提交自己的键；材料/机器/矿脉/管道的显示名通常由条目 Builder 持有。
- RegistryLib 负责条目显示名的语言数据；Topo 只导出独立 `LangKey`。
- Jade 等外部约定键通过 `absolute` 进入同一注册表。
- 数据生成按 RegistryCore 打包；跨插件引用不复制对方键。

## 扩展规范

- 新键优先用 `key(category, path, ...)`；从注册项派生用 `resource`；只有外部约定才用 `absolute`。
- `absolute` 必须归属插件：含 modId 路径段，或 `config.jade.plugin_<modId>.<path>` 且前缀后非空。
- 成组键用 `LangKeyFamily`；不要手工拼接 `category.modId.path`。
- 条目显示名跟随生成它的 Builder；不要另建同名 `LangKey`。

## 常见坑与防止

- 重复键或非法 absolute 归属：在声明期失败，不要靠运行时覆盖。
- 用 `absolute` 写其他插件命名空间：会被拒绝；外部键必须属于自己的 modId。
- 认为 `--mod` 会导出所有键：只导出声明者拥有的键。
- 条目名与 LangKey 双重声明：声明期冲突，保留一个来源。

## 文档

公开契约见 [`docs-content/localization.md`](../../docs-content/localization.md) 与 [`docs-content/localization/keys-and-families.md`](../../docs-content/localization/keys-and-families.md)。
