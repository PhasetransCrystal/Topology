package net.ptcrys.topo.data.material;

import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.material.common.ProcessDieItem;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.helper.TagHelper;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Objects;

/**
 * Process die items. Each die declaration owns its workbench shaped recipe (code-style §3.12
 * declaration ownership); shared tooltip lang is registered once with the first die.
 */
public final class BuiltinTopoProcessDies {

    public static final TagKey<Item> DIES = TagHelper.item("tools/die");

    private static boolean sharedTooltipLangRegistered;

    /** 4 iron + stick bottom-left. */
    public static final ItemEntry<ProcessDieItem> TEMPLATE_PLATE = die("template_plate", "Plate Template", "板材模具", "II", "II", "S ");
    /** 4 iron + stick bottom-right. */
    public static final ItemEntry<ProcessDieItem> TEMPLATE_ROD = die("template_rod", "Rod Template", "杆模具", "II", "II", " S");
    /** 4 iron + stick top-right. */
    public static final ItemEntry<ProcessDieItem> TEMPLATE_GEAR = die("template_gear", "Gear Template", "齿轮模具", " S", "II", "II");
    /** 4 iron + stick top-left. */
    public static final ItemEntry<ProcessDieItem> TEMPLATE_WIRE = die("template_wire", "Wire Template", "导线模具", "S ", "II", "II");
    /** Cross iron + center stick. */
    public static final ItemEntry<ProcessDieItem> TEMPLATE_BOLT = die("template_bolt", "Bolt Template", "紧固件模具", "I I", "ISI", " I ");

    private BuiltinTopoProcessDies() {}

    /**
     * Phase entry: force field initializers (items + owned recipes). No post-hoc recipe wiring.
     */
    public static void init() {
        Objects.requireNonNull(TEMPLATE_BOLT);
    }

    /**
     * Declares one die item and its workbench recipe together. Stick corner differs so shaped
     * recipes do not collide.
     */
    private static ItemEntry<ProcessDieItem> die(
                                                 String path, String displayName, String displayNameCn, String... pattern) {
        ensureSharedTooltipLang();
        var core = OfficialTopoPlugin.INSTANCE.registry();
        var builder = core.item(path, ProcessDieItem::new);
        RegistryDisplayLang.applyItem(builder, core, path, DisplayNames.fixed(displayName, displayNameCn));
        ItemEntry<ProcessDieItem> template = builder
                .addTab(BuiltinTopoCreativeTabs.MATERIALS.getKey())
                .addTag(DIES)
                .register();
        // Pass ItemEntry as Supplier — resolve only during datagen, not class init.
        BuiltinTopoRecipeTypes.CRAFTING_SHAPED
                .recipe("shape/template/" + path, template)
                .category(RecipeCategory.TOOLS)
                .pattern(pattern)
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STICK)
                .unlockedBy(Items.IRON_INGOT)
                .save();
        return template;
    }

    private static void ensureSharedTooltipLang() {
        if (sharedTooltipLangRegistered) {
            return;
        }
        sharedTooltipLangRegistered = true;
        OfficialTopoPlugin.INSTANCE
                .lang()
                .key(
                        "item",
                        "process_die.tooltip",
                        "Stays in the machine; never consumed.",
                        "放入机器模具槽；配方执行时不消耗。");
    }
}
