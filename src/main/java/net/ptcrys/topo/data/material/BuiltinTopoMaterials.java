package net.ptcrys.topo.data.material;

import net.ptcrys.topo.api.api.registration.ExternalTargets;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.data.OfficialTopoPlugin;

import net.minecraft.tags.BlockTags;

import static net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes.ARMOR_STATS;
import static net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes.MASS;
import static net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes.PRIMARY_COLOR;
import static net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes.SECONDARY_COLOR;
import static net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes.SURVEY_STATS;
import static net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes.TOOL_STATS;

/** Builtin material data declared by the current material/form design document. */
public final class BuiltinTopoMaterials {

    // ── form packs (compose, do not duplicate full metal matrices) ────────────

    /** Stone / deepslate host ores (overworld). */
    private static final MaterialForm[] OVERWORLD_ORES = {
            BuiltinTopoMaterialForms.ORE,
            BuiltinTopoMaterialForms.DEEPSLATE_ORE
    };

    /** Extra nether host ore (composes onto overworld metal set). */
    private static final MaterialForm[] NETHER_ORE = {
            BuiltinTopoMaterialForms.NETHERRACK_ORE
    };

    /** Ore-processing intermediates (dust / slurry ladder + rare element). */
    private static final MaterialForm[] ORE_PROCESSING = {
            BuiltinTopoMaterialForms.DUST,
            BuiltinTopoMaterialForms.CRUDE_DUST,
            BuiltinTopoMaterialForms.PURIFIED_DUST,
            BuiltinTopoMaterialForms.TINY_DUST,
            BuiltinTopoMaterialForms.SLURRY,
            BuiltinTopoMaterialForms.NEUTRALIZED_SLURRY,
            BuiltinTopoMaterialForms.HIGH_PURITY_DUST,
            BuiltinTopoMaterialForms.ACTIVATED_DUST,
            BuiltinTopoMaterialForms.RESONANT_DUST,
            BuiltinTopoMaterialForms.RARE_ELEMENT
    };

    /** Bulk metal stock. */
    private static final MaterialForm[] METAL_STOCK = {
            BuiltinTopoMaterialForms.INGOT,
            BuiltinTopoMaterialForms.NUGGET,
            BuiltinTopoMaterialForms.BLOCK
    };

    /** Cold-formed / machined metal parts. */
    private static final MaterialForm[] METAL_PARTS = {
            BuiltinTopoMaterialForms.PLATE,
            BuiltinTopoMaterialForms.ROD,
            BuiltinTopoMaterialForms.GEAR,
            BuiltinTopoMaterialForms.BOLT,
            BuiltinTopoMaterialForms.FRAME,
            BuiltinTopoMaterialForms.ROTOR,
            BuiltinTopoMaterialForms.PLATE_DENSE
    };

    /** Cable tier ladder. */
    private static final MaterialForm[] WIRE_TIERS = {
            BuiltinTopoMaterialForms.WIRE_1X,
            BuiltinTopoMaterialForms.WIRE_2X,
            BuiltinTopoMaterialForms.WIRE_4X,
            BuiltinTopoMaterialForms.WIRE_8X,
            BuiltinTopoMaterialForms.WIRE_16X
    };

    /** Full metal form set without nether ore. */
    private static final MaterialForm[] METAL_FORMS = forms(
            OVERWORLD_ORES, ORE_PROCESSING, METAL_STOCK, METAL_PARTS, WIRE_TIERS);

    /** Full metal form set including netherrack ore. */
    private static final MaterialForm[] NETHER_METAL_FORMS = forms(
            OVERWORLD_ORES, NETHER_ORE, ORE_PROCESSING, METAL_STOCK, METAL_PARTS, WIRE_TIERS);

    public static final Material IRON = material("iron", "Iron", "铁", 0xD8D8D8, 0xB8B8B8, 56)
            .data(TOOL_STATS.stats(250, 6.0f, 2.0f, 14, BlockTags.INCORRECT_FOR_IRON_TOOL))
            .data(ARMOR_STATS.stats(15, 2, 6, 5, 2, 0.0f, 0.0f, 9))
            .data(SURVEY_STATS.range(16))
            .data(BuiltinTopoMaterialPostProcessors.METAL_FORM_CONVERSIONS.activation())
            .data(BuiltinTopoMaterialPostProcessors.METAL_COMPONENT_FORMS.activation())
            .data(BuiltinTopoMaterialPostProcessors.MANUAL_FORGE_HAMMER.activation())
            .data(BuiltinTopoMaterialPostProcessors.WIRE_TIER_FORMS.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_2X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_3X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_4X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_6X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_12X.activation())
            .forms(METAL_FORMS)
            .form(BuiltinTopoMaterialForms.ORE, op -> op.overrideBlock(ExternalTargets.mcBlock("iron_ore")))
            .form(BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                    op -> op.overrideBlock(ExternalTargets.mcBlock("deepslate_iron_ore")))
            .form(BuiltinTopoMaterialForms.INGOT, op -> op.overrideItem(ExternalTargets.mcItem("iron_ingot")))
            .form(BuiltinTopoMaterialForms.NUGGET, op -> op.overrideItem(ExternalTargets.mcItem("iron_nugget")))
            .form(BuiltinTopoMaterialForms.BLOCK, op -> op.overrideBlock(ExternalTargets.mcBlock("iron_block")))
            .build();

    public static final Material COPPER = material("copper", "Copper", "铜", 0xE77C56, 0xE4673E, 64)
            .data(BuiltinTopoMaterialPostProcessors.METAL_FORM_CONVERSIONS.activation())
            .data(BuiltinTopoMaterialPostProcessors.METAL_COMPONENT_FORMS.activation())
            .data(BuiltinTopoMaterialPostProcessors.MANUAL_FORGE_HAMMER.activation())
            .data(BuiltinTopoMaterialPostProcessors.WIRE_TIER_FORMS.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_2X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_3X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_4X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_6X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_12X.activation())
            .forms(METAL_FORMS)
            .form(BuiltinTopoMaterialForms.ORE, op -> op.overrideBlock(ExternalTargets.mcBlock("copper_ore")))
            .form(BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                    op -> op.overrideBlock(ExternalTargets.mcBlock("deepslate_copper_ore")))
            .form(BuiltinTopoMaterialForms.INGOT, op -> op.overrideItem(ExternalTargets.mcItem("copper_ingot")))
            .form(BuiltinTopoMaterialForms.BLOCK, op -> op.overrideBlock(ExternalTargets.mcBlock("copper_block")))
            .build();

    public static final Material GOLD = material("gold", "Gold", "金", 0xFFD84A, 0xB8860B, 197)
            .data(BuiltinTopoMaterialPostProcessors.METAL_FORM_CONVERSIONS.activation())
            .data(BuiltinTopoMaterialPostProcessors.METAL_COMPONENT_FORMS.activation())
            .data(BuiltinTopoMaterialPostProcessors.WIRE_TIER_FORMS.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_2X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_3X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_4X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_6X.activation())
            .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_12X.activation())
            .forms(NETHER_METAL_FORMS)
            .form(BuiltinTopoMaterialForms.ORE, op -> op.overrideBlock(ExternalTargets.mcBlock("gold_ore")))
            .form(BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                    op -> op.overrideBlock(ExternalTargets.mcBlock("deepslate_gold_ore")))
            .form(BuiltinTopoMaterialForms.NETHERRACK_ORE,
                    op -> op.overrideBlock(ExternalTargets.mcBlock("nether_gold_ore")))
            .form(BuiltinTopoMaterialForms.INGOT, op -> op.overrideItem(ExternalTargets.mcItem("gold_ingot")))
            .form(BuiltinTopoMaterialForms.NUGGET, op -> op.overrideItem(ExternalTargets.mcItem("gold_nugget")))
            .form(BuiltinTopoMaterialForms.BLOCK, op -> op.overrideBlock(ExternalTargets.mcBlock("gold_block")))
            .build();

    public static final Material DIAMOND = material("diamond", "Diamond", "钻石", 0x6DECEB, 0x2B8C92, 12)
            .forms(BuiltinTopoMaterialForms.ORE, BuiltinTopoMaterialForms.DEEPSLATE_ORE, BuiltinTopoMaterialForms.GEM)
            .form(BuiltinTopoMaterialForms.ORE, op -> op.overrideBlock(ExternalTargets.mcBlock("diamond_ore")))
            .form(BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                    op -> op.overrideBlock(ExternalTargets.mcBlock("deepslate_diamond_ore")))
            .form(BuiltinTopoMaterialForms.GEM, op -> op.overrideItem(ExternalTargets.mcItem("diamond")))
            .build();

    public static final Material REDSTONE = material("redstone", "Redstone", "红石", 0xD11B1B, 0x6A0707, 1)
            .forms(BuiltinTopoMaterialForms.ORE, BuiltinTopoMaterialForms.DEEPSLATE_ORE, BuiltinTopoMaterialForms.CRUDE_DUST)
            .form(BuiltinTopoMaterialForms.ORE, op -> op.overrideBlock(ExternalTargets.mcBlock("redstone_ore")))
            .form(BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                    op -> op.overrideBlock(ExternalTargets.mcBlock("deepslate_redstone_ore")))
            .form(BuiltinTopoMaterialForms.CRUDE_DUST, op -> op.overrideItem(ExternalTargets.mcItem("redstone")))
            .build();

    public static final Material COAL = material("coal", "Coal", "煤", 0x2B2B2B, 0x101010, 12)
            .forms(BuiltinTopoMaterialForms.ORE, BuiltinTopoMaterialForms.DEEPSLATE_ORE, BuiltinTopoMaterialForms.CRUDE_DUST)
            .form(BuiltinTopoMaterialForms.ORE, op -> op.overrideBlock(ExternalTargets.mcBlock("coal_ore")))
            .form(BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                    op -> op.overrideBlock(ExternalTargets.mcBlock("deepslate_coal_ore")))
            .build();

    public static final Material TIN = fullNetherMetal("tin", "Tin", "锡", 0xC9D3D8, 0x8EA0A8, 119);
    public static final Material LEAD = fullNetherMetal("lead", "Lead", "铅", 0x5E617B, 0x363A52, 207);
    public static final Material NICKEL = fullMetal("nickel", "Nickel", "镍", 0xD7C996, 0x9C8F63, 59);
    public static final Material ZINC = fullMetal("zinc", "Zinc", "锌", 0xC7D6D2, 0x8A9D98, 65);
    public static final Material URANIUM = fullNetherMetal("uranium", "Uranium", "铀", 0x61C853, 0x2D6B2E, 238);

    public static final Material SULFURIC_ACID = simple("sulfuric_acid", "Sulfuric Acid", "硫酸", 0xD8E6A8, 0x9AA85F, 98,
            BuiltinTopoMaterialForms.SOLUTION);
    public static final Material SULFUR = simple("sulfur", "Sulfur", "硫磺", 0xE6D64C, 0xA68F22, 32,
            BuiltinTopoMaterialForms.DUST);
    public static final Material SODIUM_HYDROXIDE = simple("sodium_hydroxide", "Sodium Hydroxide", "氢氧化钠", 0xE8F0F0, 0xAFC8C8, 40,
            BuiltinTopoMaterialForms.DUST, BuiltinTopoMaterialForms.SOLUTION);
    public static final Material CHLORINE = simple("chlorine", "Chlorine", "氯气", 0xC7E36B, 0x7D9B2F, 71, BuiltinTopoMaterialForms.GAS);
    public static final Material HYDROGEN = simple("hydrogen", "Hydrogen", "氢气", 0xDDEEFF, 0x9CB8D8, 2, BuiltinTopoMaterialForms.GAS);
    public static final Material SULFUR_DIOXIDE = simple("sulfur_dioxide", "Sulfur Dioxide", "二氧化硫", 0xE5D77A, 0x9F913A, 64,
            BuiltinTopoMaterialForms.GAS);
    public static final Material SULFUR_TRIOXIDE = simple("sulfur_trioxide", "Sulfur Trioxide", "三氧化硫", 0xEFE38B, 0xB5A84D, 80,
            BuiltinTopoMaterialForms.GAS);
    public static final Material SALT = simple("salt", "Salt", "食盐", 0xF2F2EA, 0xC7C7BA, 58, BuiltinTopoMaterialForms.DUST);
    public static final Material BRINE = simple("brine", "Brine", "饱和盐水", 0xB8D6E7, 0x6F98AA, 58, BuiltinTopoMaterialForms.SOLUTION);
    public static final Material ELECTROLYTE = simple("electrolyte", "Electrolyte", "电解液", 0x8BD5FF, 0x3B84B8, 1, BuiltinTopoMaterialForms.SOLUTION);
    public static final Material AIR = simple("air", "Air", "空气", 0xD4ECFF, 0xA7C8E5, 29, BuiltinTopoMaterialForms.GAS);
    public static final Material WASTE_SLAG = simple("slag_waste", "Waste Slag", "废渣", 0x4B4038, 0x231C18, 60, BuiltinTopoMaterialForms.SLAG_CHUNK);
    public static final Material WASTE_ACID_SOLUTION = simple("waste_acid", "Waste Acid", "废酸", 0x6F7F62, 0x36412D, 120,
            BuiltinTopoMaterialForms.SOLUTION);
    public static final Material SOLUBLE_SALT = simple("soluble_salt", "Soluble Salt", "可溶性盐", 0xECE6D3, 0xB8AE92, 60,
            BuiltinTopoMaterialForms.DUST);
    public static final Material COPPER_SULFATE = simple("copper_sulfate", "Copper Sulfate", "硫酸铜", 0x3996D2, 0x15527E, 160,
            BuiltinTopoMaterialForms.SOLUTION);
    public static final Material NICKEL_SULFATE = simple("nickel_sulfate", "Nickel Sulfate", "硫酸镍", 0x62C276, 0x2C6F3C, 155,
            BuiltinTopoMaterialForms.SOLUTION);
    public static final Material COPPER_NICKEL_SULFATE = simple("copper_nickel_sulfate", "Copper-Nickel Sulfate", "铜镍硫酸盐", 0x4FAE9D, 0x2B6B64, 315,
            BuiltinTopoMaterialForms.SOLUTION);
    public static final Material COPPER_NICKEL_HYDROXIDE = simple("copper_nickel_hydroxide", "Copper-Nickel Hydroxide", "铜镍氢氧化物", 0x6BCF91, 0x2F7D54, 157,
            BuiltinTopoMaterialForms.DUST);
    public static final Material COPPER_NICKEL_OXIDE = simple("copper_nickel_oxide", "Copper-Nickel Oxide", "铜镍氧化物", 0x38464A, 0x78A77D, 139,
            BuiltinTopoMaterialForms.DUST, BuiltinTopoMaterialForms.SOLUTION);
    public static final Material LUMINITE = simple("luminite", "Luminite", "辉光铜镍晶", 0x57F2C7, 0x7A5CFF, 256,
            BuiltinTopoMaterialForms.SOLUTION, BuiltinTopoMaterialForms.GEM);
    public static final Material SODIUM_SULFATE = simple("sodium_sulfate", "Sodium Sulfate", "硫酸钠", 0xDCE8F5, 0x9DB7CC, 142,
            BuiltinTopoMaterialForms.SOLUTION);
    public static final Material SPENT_CRYSTAL_LIQUOR = simple("spent_crystal_liquor", "Spent Crystal Liquor", "废晶余液", 0x5B6F83, 0x263342, 120,
            BuiltinTopoMaterialForms.SOLUTION);
    public static final Material WASTE_GAS = simple("waste_gas", "Waste Gas", "废气", 0x77776E, 0x3D3D38, 30, BuiltinTopoMaterialForms.GAS);
    public static final Material STEAM = simple("steam", "Steam", "水蒸气", 0xE8F4FF, 0xB9CCD8, 18, BuiltinTopoMaterialForms.GAS);

    public static final Material BRONZE = null;
    public static final Material STEEL = null;
    public static final Material BRASS = null;
    public static final Material INVAR = null;
    public static final Material CUPRONICKEL = null;

    private BuiltinTopoMaterials() {}

    public static void init() {}

    private static Material fullMetal(String id, String en, String cn, int primary, int secondary, int mass) {
        return material(id, en, cn, primary, secondary, mass)
                .data(BuiltinTopoMaterialPostProcessors.METAL_FORM_CONVERSIONS.activation())
                .data(BuiltinTopoMaterialPostProcessors.METAL_COMPONENT_FORMS.activation())
                .data(BuiltinTopoMaterialPostProcessors.WIRE_TIER_FORMS.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_2X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_3X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_4X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_6X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_12X.activation())
                .forms(METAL_FORMS)
                .build();
    }

    private static Material fullNetherMetal(String id, String en, String cn, int primary, int secondary, int mass) {
        return material(id, en, cn, primary, secondary, mass)
                .data(BuiltinTopoMaterialPostProcessors.METAL_FORM_CONVERSIONS.activation())
                .data(BuiltinTopoMaterialPostProcessors.METAL_COMPONENT_FORMS.activation())
                .data(BuiltinTopoMaterialPostProcessors.WIRE_TIER_FORMS.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_2X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_3X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_4X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_6X.activation())
                .data(BuiltinTopoMaterialPostProcessors.ORE_PROCESSING_12X.activation())
                .forms(NETHER_METAL_FORMS)
                .build();
    }

    private static Material simple(
                                   String id,
                                   String en,
                                   String cn,
                                   int primary,
                                   int secondary,
                                   int mass,
                                   MaterialForm... forms) {
        return material(id, en, cn, primary, secondary, mass)
                .forms(forms)
                .build();
    }

    /** Concatenate form packs in declaration order (single source packs, no duplicated matrices). */
    private static MaterialForm[] forms(MaterialForm[]... packs) {
        int total = 0;
        for (MaterialForm[] pack : packs) {
            total += pack.length;
        }
        MaterialForm[] out = new MaterialForm[total];
        int offset = 0;
        for (MaterialForm[] pack : packs) {
            System.arraycopy(pack, 0, out, offset, pack.length);
            offset += pack.length;
        }
        return out;
    }

    private static MaterialRegistry.Builder material(
                                                     String id,
                                                     String en,
                                                     String cn,
                                                     int primary,
                                                     int secondary,
                                                     int mass) {
        return OfficialTopoPlugin.INSTANCE.material().material(id)
                .lang(en, cn)
                .data(PRIMARY_COLOR.rgb(primary))
                .data(SECONDARY_COLOR.rgb(secondary))
                .data(MASS.mass(mass));
    }
}
