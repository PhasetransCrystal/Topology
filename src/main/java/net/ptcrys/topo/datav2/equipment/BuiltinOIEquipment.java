package net.ptcrys.topo.datav2.equipment;

import net.ptcrys.topo.apiv2.equipment.Equipment;
import net.ptcrys.topo.apiv2.machine.MachineBlock;
import net.ptcrys.topo.apiv2.plugin.EquipmentDomainRegistration;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.equipment.common.ArmorEquipment;
import net.ptcrys.topo.datav2.equipment.common.SurveyorEquipment;
import net.ptcrys.topo.datav2.equipment.common.ToolEquipment;
import net.ptcrys.topo.datav2.equipment.common.behavior.RegulatorBehavior;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.common.render.TintedTemplate;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topo.helper.TagHelper;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;

import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes.PRIMARY_COLOR;
import static net.ptcrys.topo.datav2.material.common.render.TintedTemplate.flat;
import static net.ptcrys.topo.datav2.material.common.render.TintedTemplate.tinted;

/**
 * Finished equipment product table: world tools, crafting-remainder tools (forge hammer / file /
 * wire cutter), armor, and surveyor. One item per (kind, material-with-stats) is stamped by the
 * equipment engine drive. Primitive crafting tools share {@code TOOL_STATS} with world tools.
 */
public final class BuiltinOIEquipment {

    public static final TagKey<Item> FORGE_HAMMERS = TagHelper.item("tools/forge_hammer");
    public static final TagKey<Item> FILES = TagHelper.item("tools/file");
    public static final TagKey<Item> WIRE_CUTTERS = TagHelper.item("tools/wire_cutter");

    public static final EquipmentDomainRegistration EQUIPMENT = OfficialOIPlugin.INSTANCE.equipment();
    /**
     * Cold-start forge hammer: workbench crafting remainder drains durability; assembly is ingot +
     * stick. Tag {@link #FORGE_HAMMERS} is required by manual forge-hammer post-processor recipes.
     */
    public static final Equipment FORGE_HAMMER = EQUIPMENT.kind("forge_hammer",
            ToolEquipment.builder()
                    .registryPath("%s_forge_hammer")
                    .displayName("%s Forge Hammer", "%s锻锤")
                    .statsApplier((props, tm) -> props.durability(tm.durability()))
                    .durabilityMultiplier(1.5f)
                    .craftingRemainderTool()
                    .tag(FORGE_HAMMERS)
                    .extraRecipe((material, entry) -> {
                        if (!material.strategy().forms().contains(BuiltinOIMaterialForms.INGOT)) {
                            return;
                        }
                        // Deferred: RegisterEvent HIGHEST has not bound ItemEntry yet.
                        var ingot = MaterialHelper.materialItemSupplier(
                                material, BuiltinOIMaterialForms.INGOT);
                        BuiltinOIRecipeTypes.CRAFTING_SHAPED
                                .recipe("shape/equipment/" + entry.identifier().getPath(), entry)
                                .category(RecipeCategory.TOOLS)
                                .pattern("II", "SS")
                                .define('I', ingot)
                                .define('S', Items.STICK)
                                .unlockedBy(ingot)
                                .save();
                    })
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/forge_hammer_handle"),
                            tinted(PRIMARY_COLOR, "item/equipment/forge_hammer"),
                            flat("item/equipment/forge_hammer_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    /** File: crafting-remainder tool; no dedicated assembly recipe (product parity with prior table). */
    public static final Equipment FILE = EQUIPMENT.kind("file",
            ToolEquipment.builder()
                    .registryPath("%s_file")
                    .displayName("%s File", "%s锉刀")
                    .statsApplier((props, tm) -> props.durability(tm.durability()))
                    .craftingRemainderTool()
                    .tag(FILES)
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/file_handle"),
                            tinted(PRIMARY_COLOR, "item/equipment/file"),
                            flat("item/equipment/file_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    /** Wire cutter: crafting-remainder tool; no dedicated assembly recipe. */
    public static final Equipment WIRE_CUTTER = EQUIPMENT.kind("wire_cutter",
            ToolEquipment.builder()
                    .registryPath("%s_wire_cutter")
                    .displayName("%s Wire Cutter", "%s剪线钳")
                    .statsApplier((props, tm) -> props.durability(tm.durability()))
                    .durabilityMultiplier(0.75f)
                    .craftingRemainderTool()
                    .tag(WIRE_CUTTERS)
                    .render(TintedTemplate.handheldItem(
                            tinted(PRIMARY_COLOR, "item/equipment/wire_cutter_base"),
                            tinted(PRIMARY_COLOR, "item/equipment/wire_cutter"),
                            flat("item/equipment/wire_cutter_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    /** 左键专精挖机器(快于镐)、管道侧的扳手交互按 c:tools/wrench tag 识别本品。 */
    public static final Equipment WRENCH = EQUIPMENT.kind("wrench",
            ToolEquipment.builder()
                    .registryPath("%s_wrench")
                    .displayName("%s Wrench", "%s扳手")
                    .statsApplier((props, tm) -> props.tool(
                            tm, MachineBlock.MINEABLE_WITH_WRENCH, 1.0f, -2.4f, 0f))
                    .durabilityMultiplier(2.0f)
                    .miningSpeedMultiplier(1.5f)
                    .sneakBypassUse()
                    .tooltipLine(
                            "equipment.wrench.machines",
                            "Dismantle", "拆卸",
                            "Fast & safe", "快速且安全")
                    .tooltipLine(
                            "equipment.wrenchpes",
                            "Pipes", "管道",
                            "Sneak-click cycles ports", "潜行点击切换端口")
                    .tooltipLine(
                            "equipment.wrench.ports",
                            "Ports", "端口",
                            "Right-click to configure", "右键点击以配置")
                    .tag(TagHelper.item("tools/wrench"))
                    .render(TintedTemplate.handheldItem(
                            tinted(PRIMARY_COLOR, "item/equipment/wrench"),
                            flat("item/equipment/wrench_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    /** 右键机器切换配方运行/暂停(行为接线在机器域,Phase 5)。 */
    public static final Equipment REGULATOR = EQUIPMENT.kind("regulator",
            ToolEquipment.builder()
                    .registryPath("%s_regulator")
                    .displayName("%s Regulator", "%s调控器")
                    .statsApplier((props, tm) -> props.durability(tm.durability()))
                    .machineBehavior(RegulatorBehavior.INSTANCE)
                    .tooltipLine(
                            "equipment.regulator.toggle",
                            "Machines", "机器",
                            "Click to halt / resume", "点击以停止 / 恢复")
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/handle_hammer"),
                            tinted(PRIMARY_COLOR, "item/equipment/regulator"),
                            flat("item/equipment/regulator_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    /** 右键管道勘测网络(节点/占用/端口),潜行右键选端点看车道与瓶颈;范围按材质档。 */
    public static final Equipment PIPE_SURVEYOR = EQUIPMENT.kind("pipe_surveyor",
            SurveyorEquipment.builder()
                    .registryPath("%s_pipe_surveyor")
                    .displayName("%s Pipe Surveyor", "%s管网勘测仪")
                    .durabilityMultiplier(2.0f)
                    .tooltipLine(
                            "equipmentpe_surveyor.survey",
                            "Pipes", "管道",
                            "Click surveys the network", "点击勘测网络")
                    .tooltipLine(
                            "equipmentpe_surveyor.endpoints",
                            "Endpoints", "端点",
                            "Sneak-click picks A/B", "潜行点击选取 A/B")
                    // 放大镜镜框+手柄是材质上色层(铁灰/青铜棕可辨),玻璃透镜+高光是固定色覆盖层。
                    .render(TintedTemplate.handheldItem(
                            tinted(PRIMARY_COLOR, "item/equipment/pipe_surveyor"),
                            flat("item/equipment/pipe_surveyor_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment PICKAXE = EQUIPMENT.kind("pickaxe",
            ToolEquipment.builder()
                    .registryPath("%s_pickaxe")
                    .displayName("%s Pickaxe", "%s镐")
                    .statsApplier((props, tm) -> props.pickaxe(tm, 1.0f, -2.8f))
                    .tag(ItemTags.PICKAXES)
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/handle"),
                            tinted(PRIMARY_COLOR, "item/equipment/pickaxe"),
                            flat("item/equipment/pickaxe_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment AXE = EQUIPMENT.kind("axe",
            ToolEquipment.builder()
                    .registryPath("%s_axe")
                    .displayName("%s Axe", "%s斧")
                    .statsApplier((props, tm) -> props.axe(tm, 6.0f, -3.1f))
                    .tag(ItemTags.AXES)
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/handle"),
                            tinted(PRIMARY_COLOR, "item/equipment/axe"),
                            flat("item/equipment/axe_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment SHOVEL = EQUIPMENT.kind("shovel",
            ToolEquipment.builder()
                    .registryPath("%s_shovel")
                    .displayName("%s Shovel", "%s铲")
                    .statsApplier((props, tm) -> props.shovel(tm, 1.5f, -3.0f))
                    .tag(ItemTags.SHOVELS)
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/handle"),
                            tinted(PRIMARY_COLOR, "item/equipment/shovel"),
                            flat("item/equipment/shovel_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment SWORD = EQUIPMENT.kind("sword",
            ToolEquipment.builder()
                    .registryPath("%s_sword")
                    .displayName("%s Sword", "%s剑")
                    .statsApplier((props, tm) -> props.sword(tm, 3.0f, -2.4f))
                    .tag(ItemTags.SWORDS)
                    .recipeCategory(RecipeCategory.COMBAT)
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/sword_base"),
                            tinted(PRIMARY_COLOR, "item/equipment/sword"),
                            flat("item/equipment/sword_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment HOE = EQUIPMENT.kind("hoe",
            ToolEquipment.builder()
                    .registryPath("%s_hoe")
                    .displayName("%s Hoe", "%s锄")
                    .statsApplier((props, tm) -> props.hoe(tm, -2.0f, -1.0f))
                    .tag(ItemTags.HOES)
                    .render(TintedTemplate.handheldItem(
                            flat("item/equipment/handle"),
                            tinted(PRIMARY_COLOR, "item/equipment/hoe"),
                            flat("item/equipment/hoe_overlay")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment HELMET = EQUIPMENT.kind("helmet",
            ArmorEquipment.builder()
                    .registryPath("%s_helmet")
                    .displayName("%s Helmet", "%s头盔")
                    .armorType(ArmorType.HELMET)
                    .tag(ItemTags.HEAD_ARMOR)
                    .render(TintedTemplate.item(tinted(PRIMARY_COLOR, "item/equipment/helmet")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment CHESTPLATE = EQUIPMENT.kind("chestplate",
            ArmorEquipment.builder()
                    .registryPath("%s_chestplate")
                    .displayName("%s Chestplate", "%s胸甲")
                    .armorType(ArmorType.CHESTPLATE)
                    .tag(ItemTags.CHEST_ARMOR)
                    .render(TintedTemplate.item(tinted(PRIMARY_COLOR, "item/equipment/chestplate")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment LEGGINGS = EQUIPMENT.kind("leggings",
            ArmorEquipment.builder()
                    .registryPath("%s_leggings")
                    .displayName("%s Leggings", "%s护腿")
                    .armorType(ArmorType.LEGGINGS)
                    .tag(ItemTags.LEG_ARMOR)
                    .render(TintedTemplate.item(tinted(PRIMARY_COLOR, "item/equipment/leggings")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    public static final Equipment BOOTS = EQUIPMENT.kind("boots",
            ArmorEquipment.builder()
                    .registryPath("%s_boots")
                    .displayName("%s Boots", "%s靴子")
                    .armorType(ArmorType.BOOTS)
                    .tag(ItemTags.FOOT_ARMOR)
                    .render(TintedTemplate.item(tinted(PRIMARY_COLOR, "item/equipment/boots")))
                    .creativeTab(BuiltinOICreativeTabs.EQUIPMENT.getKey())
                    .build());

    private BuiltinOIEquipment() {}

    public static void init() {}
}
