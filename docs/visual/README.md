# 视觉/CTM 模块内部

面向框架开发者。视觉模块提供连接纹理方块的模型族、属性、数据生成与宿主接线。

## 职责与边界

- 负责：CTM 方块/宿主、外观族/皮肤/属性、模型族、数据生成与烘焙。
- 不负责：方块/机器的注册归属；视觉只改变外观。
- 客户端渲染与数据生成分离，不能用客户端代码替代 datagen。

## 关键类型与数据流

- `ConnectedTextureBlock` / `ConnectedTextureHost`。
- `ConnectedTextureFamily` / `ConnectedTextureSkin` / `ConnectedTextureProperties`。
- `ConnectedTextureCasingModel` / `ConnectedTextureHatchModel` / `ConnectedTextureMachineModel`。
- `ConnectedTextureModelDatagen` / `CtmBakedParts`。

## 为什么这样设计

- 外观按族/皮肤组合，避免为每种机器复制模型逻辑。
- 模型数据生成与客户端烘焙分离，保证资源包/数据生成可验证。
- CTM 方块只声明宿主关系，不改变机器/管道的注册语义。

## 与其他模块的联动

- 机器/管道：方块通过 CTM 模板获得外观；注册仍归领域模块。
- 客户端：烘焙与渲染读取模型/属性。
- 数据生成：模型 JSON 由 datagen 输出。
- 语言：外观不涉及文本。

## 扩展规范

- 新外观优先扩展现有族/皮肤/属性，不复制整套模型。
- 新模型接线同时补 datagen 与客户端烘焙路径。
- 不在 CTM 代码中写机器/管道业务分支。
- 补 `docs-content/foundation/connected-textures.md` 的下游说明。

## 常见坑与防止

- 只写客户端模型没有 datagen：资源缺失。
- 每个方块复制一套 CTM 模型：族/皮肤应复用。
- 用 CTM 改变方块注册归属：外观与注册分离。
- 未验证模型绑定：需要客户端资源加载验收。

## 文档

公开用法见 [`docs-content/foundation/connected-textures.md`](../../docs-content/foundation/connected-textures.md)。
