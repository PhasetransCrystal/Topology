package net.ptcrys.topo.datav2.material;

import net.ptcrys.topo.apiv2.material.form.FormDataUse;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.material.common.form.BlockForm;
import net.ptcrys.topo.datav2.material.common.form.FluidForm;
import net.ptcrys.topo.datav2.material.common.form.ItemForm;
import net.ptcrys.topo.datav2.material.common.form.NonPlaceableBlockItem;
import net.ptcrys.topo.datav2.material.common.render.GtmMaterialBlockRender;
import net.ptcrys.topo.datav2.material.common.render.TintedTemplate;
import net.ptcrys.topo.datav2.material.common.render.WireTierBlockRender;

import net.minecraft.world.level.block.Blocks;

import static net.ptcrys.topo.datav2.material.BuiltinOIFormDataTypes.AMOUNT;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes.PRIMARY_COLOR;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes.SECONDARY_COLOR;
import static net.ptcrys.topo.datav2.material.common.render.TintedTemplate.flat;
import static net.ptcrys.topo.datav2.material.common.render.TintedTemplate.opaque;
import static net.ptcrys.topo.datav2.material.common.render.TintedTemplate.tinted;
import static net.ptcrys.topo.datav2.material.common.render.TintedTemplate.translucent;

/** Material forms used by the current design document. */
public final class BuiltinOIMaterialForms {
    // ---- Ore block forms --------------------------------------------------------------------

    public static final MaterialForm ORE = OfficialOIPlugin.INSTANCE.material().form("ore")
            .lang("Ore", "矿石")
            .strategy(BlockForm.builder()
                    .registryPath("%s_ore")
                    .displayName("%s Ore", "%s矿石")
                    .commonTag("ores")
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .initialProperties(Blocks.STONE)
                    .render(TintedTemplate.block(
                            opaque(flat("block/material/ore_host_stone")),
                            translucent(tinted(PRIMARY_COLOR, "block/material/ore_overlay"))))
                    .build())
            .build();

    public static final MaterialForm DEEPSLATE_ORE = OfficialOIPlugin.INSTANCE.material().form("deepslate_ore")
            .lang("Deepslate Ore", "深板岩矿石")
            .strategy(BlockForm.builder()
                    .registryPath("deepslate_%s_ore")
                    .displayName("Deepslate %s Ore", "深板岩%s矿石")
                    .commonTag("ores")
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .initialProperties(Blocks.DEEPSLATE)
                    .render(TintedTemplate.block(
                            opaque(flat("block/material/ore_host_deepslate")),
                            translucent(tinted(PRIMARY_COLOR, "block/material/ore_overlay"))))
                    .build())
            .build();

    public static final MaterialForm NETHERRACK_ORE = OfficialOIPlugin.INSTANCE.material().form("netherrack_ore")
            .lang("Netherrack Ore", "地狱岩矿石")
            .strategy(BlockForm.builder()
                    .registryPath("netherrack_%s_ore")
                    .displayName("Netherrack %s Ore", "地狱岩%s矿石")
                    .commonTag("ores")
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .initialProperties(Blocks.NETHERRACK)
                    .render(TintedTemplate.block(
                            opaque(flat("block/material/ore_host_netherrack")),
                            translucent(tinted(PRIMARY_COLOR, "block/material/ore_overlay"))))
                    .build())
            .build();

    // ---- Powder and process item forms ------------------------------------------------------

    public static final MaterialForm DUST = dustForm(
            "dust",
            "%s Dust",
            "%s粉",
            "粉",
            "%s_dust",
            "item/material/dust",
            "item/material/dust_secondary");

    public static final MaterialForm CRUDE_DUST = dustForm(
            "crude_dust",
            "%s Crude Dust",
            "%s粗粉",
            "粗粉",
            "%s_crude_dust",
            "item/material/crude_dust",
            "item/material/crude_dust_secondary");

    public static final MaterialForm PURIFIED_DUST = dustForm(
            "purified_dust",
            "%s Purified Dust",
            "%s精粉",
            "精粉",
            "%s_purified_dust",
            "item/material/purified_dust",
            "item/material/purified_dust_secondary");

    public static final MaterialForm TINY_DUST = dustForm(
            "tiny_dust",
            "%s Pinch Dust",
            "%s小搓粉",
            "小搓粉",
            "tiny_%s_dust",
            "item/material/tiny_dust",
            "item/material/tiny_dust_secondary");

    public static final MaterialForm SLURRY = dustForm(
            "slurry",
            "%s Slurry",
            "%s矿浆",
            "矿浆",
            "%s_slurry",
            "item/material/slurry",
            "item/material/slurry_secondary");

    public static final MaterialForm NEUTRALIZED_SLURRY = dustForm(
            "neutralized_slurry",
            "%s Neutralized Slurry",
            "%s中和矿浆",
            "中和矿浆",
            "%s_neutralized_slurry",
            "item/material/neutralized_slurry",
            "item/material/neutralized_slurry_secondary");

    public static final MaterialForm HIGH_PURITY_DUST = dustForm(
            "high_purity_dust",
            "%s High-Purity Dust",
            "%s高纯粉",
            "高纯粉",
            "%s_high_purity_dust",
            "item/material/high_purity_dust",
            "item/material/high_purity_dust_secondary");

    public static final MaterialForm ACTIVATED_DUST = dustForm(
            "activated_dust",
            "%s Activated Dust",
            "%s活化矿粉",
            "活化矿粉",
            "%s_activated_dust",
            "item/material/activated_dust",
            "item/material/activated_dust_secondary");

    public static final MaterialForm RESONANT_DUST = dustForm(
            "resonant_dust",
            "%s Resonant Dust",
            "%s激活矿粉",
            "激活矿粉",
            "%s_resonant_dust",
            "item/material/resonant_dust",
            "item/material/resonant_dust_secondary");

    public static final MaterialForm RARE_ELEMENT = dustForm(
            "rare_element",
            "%s Rare Element",
            "%s稀有元素",
            "稀有元素",
            "%s_rare_element",
            "item/material/rare_element",
            "item/material/rare_element_secondary");

    /** Legacy Java handle: chemical powders now use the unified dust form. */
    public static final MaterialForm CHEMICAL_DUST = DUST;

    public static final MaterialForm SLAG_CHUNK = dustForm(
            "slag_chunk",
            "%s Slag Chunk",
            "%s渣块",
            "渣块",
            "%s_slag_chunk",
            "item/material/slag_chunk",
            "item/material/slag_chunk_secondary");

    public static final MaterialForm GEM = OfficialOIPlugin.INSTANCE.material().form("gem")
            .lang("Gem", "宝石")
            .strategy(ItemForm.builder()
                    .registryPath("%s_gem")
                    .displayName("%s Gem", "%s宝石")
                    .commonTag("gems")
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .render(TintedTemplate.item(
                            tinted(PRIMARY_COLOR, "item/material/gem"),
                            tinted(SECONDARY_COLOR, "item/material/gem_secondary"),
                            flat("item/material/gem_overlay")))
                    .build())
            .build();

    // ---- Base product forms -----------------------------------------------------------------

    public static final MaterialForm INGOT = OfficialOIPlugin.INSTANCE.material().form("ingot")
            .lang("Ingot", "锭")
            .strategy(ItemForm.builder()
                    .registryPath("%s_ingot")
                    .displayName("%s Ingot", "%s锭")
                    .commonTag("ingots")
                    .data(AMOUNT.ingots(1))
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .render(TintedTemplate.item(
                            tinted(PRIMARY_COLOR, "item/material/ingot"),
                            tinted(SECONDARY_COLOR, "item/material/ingot_secondary"),
                            flat("item/material/ingot_overlay")))
                    .build())
            .build();

    public static final MaterialForm NUGGET = OfficialOIPlugin.INSTANCE.material().form("nugget")
            .lang("Nugget", "粒")
            .strategy(ItemForm.builder()
                    .registryPath("%s_nugget")
                    .displayName("%s Nugget", "%s粒")
                    .commonTag("nuggets")
                    .data(AMOUNT.ingotFraction(9))
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .render(TintedTemplate.item(
                            tinted(PRIMARY_COLOR, "item/material/nugget"),
                            tinted(SECONDARY_COLOR, "item/material/nugget_secondary"),
                            flat("item/material/nugget_overlay")))
                    .build())
            .build();

    public static final MaterialForm BLOCK = OfficialOIPlugin.INSTANCE.material().form("block")
            .lang("Block", "块")
            .strategy(BlockForm.builder()
                    .registryPath("%s_block")
                    .displayName("Block of %s", "%s块")
                    .commonTag("storage_blocks")
                    .data(AMOUNT.ingots(9))
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .initialProperties(Blocks.IRON_BLOCK)
                    .render(new GtmMaterialBlockRender())
                    .build())
            .build();

    // ---- Machine component forms -----------------------------------------------------------

    public static final MaterialForm PLATE = componentForm(
            "plate",
            "Plate",
            "板材",
            "%s Plate",
            "%s板材",
            "%s_plate",
            "plates",
            AMOUNT.ingots(1),
            "item/material/plate",
            "item/material/plate_secondary",
            "item/material/plate_overlay");

    public static final MaterialForm ROD = componentForm(
            "rod",
            "Rod",
            "杆",
            "%s Rod",
            "%s杆",
            "%s_rod",
            "rods",
            AMOUNT.ingotFraction(2),
            "item/material/rod",
            "item/material/rod_secondary",
            null);

    public static final MaterialForm GEAR = componentForm(
            "gear",
            "Gear",
            "齿轮",
            "%s Gear",
            "%s齿轮",
            "%s_gear",
            "gears",
            AMOUNT.ingots(2),
            "item/material/gear",
            "item/material/gear_secondary",
            "item/material/gear_overlay");

    public static final MaterialForm BOLT = componentForm(
            "bolt",
            "Bolt",
            "紧固件",
            "%s Bolt",
            "%s紧固件",
            "%s_bolt",
            "bolts",
            AMOUNT.ingotFraction(4),
            "item/material/bolt",
            "item/material/bolt_secondary",
            "item/material/bolt_overlay");

    public static final MaterialForm FRAME = componentForm(
            "frame",
            "Frame",
            "框架",
            "%s Frame",
            "%s框架",
            "%s_frame",
            "frames",
            AMOUNT.ingots(3),
            "item/material/frame",
            "item/material/frame_secondary",
            null);

    public static final MaterialForm ROTOR = componentForm(
            "rotor",
            "Rotor",
            "转子",
            "%s Rotor",
            "%s转子",
            "%s_rotor",
            "rotors",
            AMOUNT.ingots(5),
            "item/material/rotor",
            "item/material/rotor_secondary",
            "item/material/rotor_overlay");

    public static final MaterialForm PLATE_DENSE = componentForm(
            "plate_dense",
            "Dense Plate",
            "致密板",
            "%s Dense Plate",
            "%s致密板",
            "%s_plate_dense",
            "plates/dense",
            AMOUNT.ingots(9),
            "item/material/plate_dense",
            "item/material/plate_dense_secondary",
            "item/material/plate_dense_overlay");

    public static final MaterialForm WIRE_1X = wireTierForm(
            "wire_1x", "1x Wire", "1x导线", "%s 1x Wire", "%s 1x导线",
            "%s_wire_1x", "wires/1x", AMOUNT.ingotFraction(2), 3.0f);
    public static final MaterialForm WIRE_2X = wireTierForm(
            "wire_2x", "2x Wire", "2x导线", "%s 2x Wire", "%s 2x导线",
            "%s_wire_2x", "wires/2x", AMOUNT.ingots(1), 5.0f);
    public static final MaterialForm WIRE_4X = wireTierForm(
            "wire_4x", "4x Wire", "4x导线", "%s 4x Wire", "%s 4x导线",
            "%s_wire_4x", "wires/4x", AMOUNT.ingots(2), 7.0f);
    public static final MaterialForm WIRE_8X = wireTierForm(
            "wire_8x", "8x Wire", "8x导线", "%s 8x Wire", "%s 8x导线",
            "%s_wire_8x", "wires/8x", AMOUNT.ingots(4), 9.0f);
    public static final MaterialForm WIRE_16X = wireTierForm(
            "wire_16x", "16x Wire", "16x导线", "%s 16x Wire", "%s 16x导线",
            "%s_wire_16x", "wires/16x", AMOUNT.ingots(8), 13.0f);

    // ---- Fluid forms ------------------------------------------------------------------------

    public static final MaterialForm SOLUTION = OfficialOIPlugin.INSTANCE.material().form("solution")
            .lang("Solution", "溶液")
            .strategy(FluidForm.builder()
                    .registryPath("%s_solution")
                    .displayName("%s Solution", "%s溶液")
                    .commonTag("solutions")
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .texture("block/material/fluid")
                    .tint(PRIMARY_COLOR)
                    .properties(properties -> properties
                            .density(1200)
                            .viscosity(1600)
                            .temperature(300)
                            .motionScale(0.010D)
                            .canExtinguish(true))
                    .fluidProperties(properties -> properties
                            .slopeFindDistance(3)
                            .levelDecreasePerBlock(2)
                            .tickRate(8)
                            .explosionResistance(100.0F))
                    .build())
            .build();

    public static final MaterialForm GAS = OfficialOIPlugin.INSTANCE.material().form("gas")
            .lang("Gas", "气体")
            .strategy(FluidForm.builder()
                    .registryPath("%s_gas")
                    .displayName("%s Gas", "%s气体")
                    .commonTag("gases")
                    .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                    .texture("block/material/fluid")
                    .tint(PRIMARY_COLOR)
                    .properties(properties -> properties
                            .density(-100)
                            .viscosity(100)
                            .temperature(295)
                            .motionScale(0.004D)
                            .canPushEntity(false)
                            .canSwim(false)
                            .canDrown(false)
                            .fallDistanceModifier(1.0F)
                            .pathType(null)
                            .adjacentPathType(null))
                    .fluidProperties(properties -> properties
                            .slopeFindDistance(6)
                            .levelDecreasePerBlock(1)
                            .tickRate(2)
                            .explosionResistance(1.0F))
                    .build())
            .build();

    // ---- Legacy handles kept for source compatibility; they no longer register content. -----

    public static final MaterialForm DUST_SMALL = null;
    public static final MaterialForm DUST_TINY = TINY_DUST;
    public static final MaterialForm INGOT_HOT = null;
    public static final MaterialForm PLATE_DOUBLE = null;
    public static final MaterialForm FOIL = null;
    public static final MaterialForm ROD_LONG = null;
    public static final MaterialForm SCREW = null;
    public static final MaterialForm RING = null;
    public static final MaterialForm SPRING = null;
    public static final MaterialForm SPRING_SMALL = null;
    public static final MaterialForm GEAR_SMALL = null;

    private BuiltinOIMaterialForms() {}

    public static void init() {}

    private static MaterialForm dustForm(
                                         String id,
                                         String displayNameEn,
                                         String displayNameCn,
                                         String langCn,
                                         String registryPath,
                                         String primaryTexture,
                                         String secondaryTexture) {
        return OfficialOIPlugin.INSTANCE.material().form(id)
                .lang(displayNameEn.replace("%s ", ""), langCn)
                .strategy(ItemForm.builder()
                        .registryPath(registryPath)
                        .displayName(displayNameEn, displayNameCn)
                        .commonTag(id + "s")
                        .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                        .render(TintedTemplate.item(
                                tinted(PRIMARY_COLOR, primaryTexture),
                                tinted(SECONDARY_COLOR, secondaryTexture)))
                        .build())
                .build();
    }

    private static MaterialForm componentForm(
                                              String id,
                                              String langEn,
                                              String langCn,
                                              String displayNameEn,
                                              String displayNameCn,
                                              String registryPath,
                                              String commonTag,
                                              FormDataUse<Integer> amount,
                                              String primaryTexture,
                                              String secondaryTexture,
                                              String overlayTexture) {
        TintedTemplate.Layer primary = tinted(PRIMARY_COLOR, primaryTexture);
        TintedTemplate.Layer secondary = tinted(SECONDARY_COLOR, secondaryTexture);
        TintedTemplate.Layer[] layers = overlayTexture == null ? new TintedTemplate.Layer[] { primary, secondary } : new TintedTemplate.Layer[] { primary, secondary, flat(overlayTexture) };
        return OfficialOIPlugin.INSTANCE.material().form(id)
                .lang(langEn, langCn)
                .strategy(ItemForm.builder()
                        .registryPath(registryPath)
                        .displayName(displayNameEn, displayNameCn)
                        .commonTag(commonTag)
                        .data(amount)
                        .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                        .render(TintedTemplate.item(layers))
                        .build())
                .build();
    }

    private static MaterialForm wireTierForm(
                                             String id,
                                             String langEn,
                                             String langCn,
                                             String displayNameEn,
                                             String displayNameCn,
                                             String registryPath,
                                             String commonTag,
                                             FormDataUse<Integer> amount,
                                             float thicknessPixels) {
        return OfficialOIPlugin.INSTANCE.material().form(id)
                .lang(langEn, langCn)
                .strategy(BlockForm.builder()
                        .registryPath(registryPath)
                        .displayName(displayNameEn, displayNameCn)
                        .commonTag(commonTag)
                        .data(amount)
                        .creativeTab(BuiltinOICreativeTabs.MATERIALS.getKey())
                        .initialProperties(Blocks.COPPER_BLOCK)
                        .properties(properties -> properties.noOcclusion())
                        .blockItem(NonPlaceableBlockItem::new)
                        .render(new WireTierBlockRender(thicknessPixels))
                        .build())
                .build();
    }
}
