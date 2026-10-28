---
sidebar_position: 2
title: 快速开始
---

# 注册第一项自定义内容

本例注册材料 `mymod:tin`，通过自定义形态 `mymod:dust` 自动生成物品 `mymod:tin_dust`。
数据类型、形态策略和材料声明都在自己的模组中实现，不依赖 TopoDemo。

## 1. 添加依赖并登记插件

```gradle
repositories {
    maven { url = uri("https://maven.ptcrys.net/releases") }
}
dependencies {
    implementation "net.ptcrys:Topology:${topology_version}"
    // 与所用 Topology 版本配套，供插件代码直接引用 RegistryCore：
    implementation "net.ptcrys:RegistryLib:${registry_lib_version}"
}
```

使用与你的 Minecraft / NeoForge 环境匹配的 Topology 版本，并将示例版本变量替换为项目使用的版本。
示例直接引用 RegistryLib 的 `RegistryCore`，因此显式声明其编译依赖。再在模组元数据中声明对 `topo` 的依赖；
下面是 `neoforge.mods.toml` 的依赖片段，`mymod` 应替换成自己的 Mod ID，版本范围按实际依赖填写。

```toml
[[dependencies.mymod]]
modId="topo"
type="required"
ordering="AFTER"
side="BOTH"
```

模组入口只创建本模组共用的 RegistryCore 并登记插件：

```java
import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("mymod")
public final class MyMod {
    public static final RegistryCore REGISTRY = RegistryCore.create("mymod");

    public MyMod(IEventBus modEventBus) {
        TopoPlugins.register(new MyContentPlugin(REGISTRY));
    }
}
```

Topo 宿主已经负责事件接线。内容模组无需调用 `attachHost`、引擎启动方法或各注册表的 `freeze`。
同一模组的多个插件共用这个 RegistryCore。

## 2. 在基础钩子声明类型，在内容钩子声明材料

以下是完整的 `MyContentPlugin.java`。示例只展示注册与名称；模型、纹理和创造栏在下一节补齐。

```java
import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormStrategy;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class MyContentPlugin extends TopoPlugin {
    private MetalDataType metal;

    public MyContentPlugin(RegistryCore registry) {
        super(registry);
    }

    @Override
    public void registerMaterialFoundation(MaterialDomainRegistration material) {
        metal = material.dataType("metal", new MetalDataType(material.id("metal")));
        material.form("dust")
                .lang("Dust", "粉")
                .appliesTo(value -> value.strategy().data(metal).orElse(false))
                .strategy(new DustForm())
                .build();
    }

    @Override
    public void registerMaterials(MaterialDomainRegistration material) {
        material.material("tin")
                .lang("Tin", "锡")
                .data(metal.enabled())
                .build();
    }

    public static final class MetalDataType extends MaterialDataType<Boolean> {
        public MetalDataType(Identifier id) {
            super(id);
        }

        public MaterialDataUse<Boolean> enabled() {
            return use(true);
        }
    }

    public static final class DustForm extends MaterialFormStrategy {
        public DustForm() {
            super(List.of());
        }

        @Override
        public String registryPath() {
            return "%s_dust";
        }

        @Override
        public void validateMaterial(Material material, MaterialForm form) {
            // 此形态没有额外的数据依赖。
        }

        @Override
        public void register(MaterialContentContext context, MaterialForm form) {
            Material material = context.material();
            String path = registryPath().formatted(material.id().getPath());
            var builder = context.core().item(path);
            RegistryDisplayLang.applyItem(builder, context.core(), path,
                    DisplayNames.fixed(material.displayNameEn() + " Dust",
                            material.displayNameCn() + "粉"));
            builder.register();
        }
    }
}
```

这段代码包含三种声明：

| 声明 | 标识 | 用途 |
|---|---|---|
| 数据类型 | `mymod:metal` | 材料通过类型的公开工厂方法取得数据 use |
| 形态 | `mymod:dust` | 在所有插件完成材料声明后，按数据匹配材料 |
| 材料 | `mymod:tin` | 附加 metal 数据，因此匹配 dust 并生成 `mymod:tin_dust` |

形态策略使用 `context.core()` 创建条目。这样同一形态匹配其他模组材料时，生成物仍归材料所属模组。
`.form(form, options)` 用来配置局部数据或复用外部条目，形态是否生成始终由 `appliesTo` 决定。

## 3. 补齐资源并验证结果

注册条目、生成资源和将条目加入创造栏是三个独立动作。
上例没有提供模型或创造栏；补齐前可用 `/give @s mymod:tin_dust` 检查条目存在，但外观可能缺失。

- 在自己的策略中配置模型、标签和创造栏，或维护对应资源文件；[内容注册实作](foundation/content-plugin.md) 展示可复用的产品策略。
- 自有物品／方块名称通过 `RegistryDisplayLang` 提交给生成条目的 Builder；界面提示等独立文本使用 `lang.key`。
- 在自己的 datagen 配置中选中 `mymod`，把生成目录接入资源源码集。仓库 Demo 的命令与检查项见[测试与验证](foundation/testing.md)。

## 4. 继续注册其他领域

| 目标 | 在哪里声明 | 接下来阅读 |
|---|---|---|
| 新配方能力、资源族 | `registerRecipeFoundation` | [配方能力](recipe/recipe-capability.md)、[机器资源](machine/machine-resource.md) |
| 配方类型和生产线 | `registerRecipeTypes` | [配方类型](recipe/recipe-type.md) |
| 装备种类 | `registerEquipmentKinds` | [装备](equipment.md) |
| 机器基础类型、机器定义 | `registerMachineFoundation`、`registerMachines` | [机器](machine.md) |
| 管道与分发策略 | `registerMachineFollowUps` | [管道](pipe.md) |
| 矿脉基础类型、矿脉 | `registerOreFoundation`、`registerOreDisplays`、`registerOreVeins` | [矿脉](ore.md) |
| 为上述内容添加配方 | `registerRecipes` | [配方](recipe.md) |
| 对真实物品绑定面板或机器交互 | `registerRuntime` | [悬浮面板](foundation/tooltip-panels.md)、[装备行为](equipment/equipment-behavior.md) |

在内容声明钩子中传递句柄、ID 或 Supplier，不提前调用尚未绑定的 Entry 的 `.get()`。
必须取得游戏对象的绑定放到 `registerRuntime`；模型、配方的资源生成则在相应 provider 回调里取值。

接入外部内容时继续阅读[兼容其他模组](compatibility/other-mods.md)；
复用其他插件的材料、形态或配方类型时阅读[兼容其他 TopoPlugin](compatibility/topo-plugins.md)。
