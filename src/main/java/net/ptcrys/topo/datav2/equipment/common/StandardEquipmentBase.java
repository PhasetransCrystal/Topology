package net.ptcrys.topo.datav2.equipment.common;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.apiv2.equipment.Equipment;
import net.ptcrys.topo.apiv2.equipment.EquipmentRegistry;
import net.ptcrys.topo.apiv2.equipment.EquipmentStrategy;
import net.ptcrys.topo.apiv2.lang.RegistryDisplayLang;
import net.ptcrys.topo.apiv2.lang.TemplateDisplayName;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.MaterialContentContext;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.render.MaterialItemRender;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;
import net.ptcrys.topo.datav2.equipment.EquipmentTooltipLine;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topo.helper.TagHelper;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shared registration pipeline of the standard equipment plugs ({@link ToolEquipment},
 * {@link ArmorEquipment}): one item per applicable material with stats baked into the item
 * properties at registration, conventional tags, generated lang, a creative tab entry, and one
 * shaped assembly recipe stamped from the {@link EquipmentPattern}. Kind-specific stat baking is
 * the {@link #applyStats} hook; everything else is a builder knob on the concrete plug.
 */
public abstract class StandardEquipmentBase implements EquipmentStrategy {

    private final String registryPath;
    private final TemplateDisplayName displayName;
    private final List<TagKey<Item>> tags;
    private final EquipmentPattern pattern;
    private final MaterialItemRender render;
    private final ResourceKey<CreativeModeTab> creativeTab;
    private final RecipeCategory recipeCategory;
    private final List<EquipmentTooltipLine> tooltipLines;
    private final EquipmentExtraRecipe extraRecipe;

    StandardEquipmentBase(String registryPath, TemplateDisplayName displayName, List<TagKey<Item>> tags,
                          EquipmentPattern pattern, MaterialItemRender render,
                          ResourceKey<CreativeModeTab> creativeTab, RecipeCategory recipeCategory,
                          List<EquipmentTooltipLine> tooltipLines) {
        this(registryPath, displayName, tags, pattern, render, creativeTab, recipeCategory,
                tooltipLines, null);
    }

    StandardEquipmentBase(String registryPath, TemplateDisplayName displayName, List<TagKey<Item>> tags,
                          EquipmentPattern pattern, MaterialItemRender render,
                          ResourceKey<CreativeModeTab> creativeTab, RecipeCategory recipeCategory,
                          List<EquipmentTooltipLine> tooltipLines, EquipmentExtraRecipe extraRecipe) {
        this.registryPath = registryPath;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.tags = List.copyOf(tags);
        this.pattern = pattern;
        this.render = render;
        this.creativeTab = creativeTab;
        this.recipeCategory = recipeCategory;
        this.tooltipLines = List.copyOf(tooltipLines);
        this.extraRecipe = extraRecipe;
    }

    /** Bakes this kind's stats (tool/armor components) into the item properties. */
    protected abstract Item.Properties applyStats(Item.Properties properties, Material material,
                                                  TagKey<Item> repairItems);

    /** The item constructor; plain {@link Item} unless the kind needs an interaction quirk. */
    protected java.util.function.Function<Item.Properties, Item> itemFactory() {
        return Item::new;
    }

    public final EquipmentPattern pattern() {
        return pattern;
    }

    /** Function-description tooltip lines of this kind, in declaration order. */
    public final List<EquipmentTooltipLine> tooltipLines() {
        return tooltipLines;
    }

    /**
     * Mint tooltip keys on the owning plugin's lang domain (pass {@code plugin.lang()} from the
     * product table / equipment domain — do not hard-bind OfficialOIPlugin here).
     */
    static void addTooltipLine(
                               List<EquipmentTooltipLine> tooltipLines,
                               LangDomainRegistration lang,
                               String langKey,
                               String nameEn,
                               String nameCn,
                               String descEn,
                               String descCn) {
        // langKey is bare path under tooltip.<modid> (e.g. equipment.wrench.machines).
        String path = Objects.requireNonNull(langKey, "langKey");
        Objects.requireNonNull(lang, "lang");
        LangKey name = lang.key(
                "tooltip",
                path + ".name",
                Objects.requireNonNull(nameEn, "nameEn"),
                Objects.requireNonNull(nameCn, "nameCn"));
        LangKey description = lang.key(
                "tooltip",
                path + ".desc",
                Objects.requireNonNull(descEn, "descEn"),
                Objects.requireNonNull(descCn, "descCn"));
        tooltipLines.add(new EquipmentTooltipLine(name, description));
    }

    @Override
    public final void register(MaterialContentContext context, Equipment self) {
        Material material = context.material();
        if (pattern != null) {
            for (MaterialForm form : pattern.forms()) {
                if (!material.strategy().forms().contains(form)) {
                    throw new IllegalStateException(
                            "material " + material.id() + " applies to equipment " + self.id() + " but does not declare part form " + form.id());
                }
            }
        }
        String materialPath = material.id().getPath();
        TagKey<Item> repairItems = TagHelper.itemMaterial("ingots", materialPath);

        String entryPath = String.format(registryPath, materialPath);
        ItemBuilder<Item, RegistryCore> builder = context.core().item(entryPath, itemFactory());
        RegistryDisplayLang.applyItem(
                builder, context.core(), entryPath, displayName.resolve(material));
        builder.properties(props -> applyStats(props, material, repairItems));
        if (render != null) {
            render.apply(builder, material);
        }
        builder.addTab(creativeTab);
        if (!tags.isEmpty()) {
            @SuppressWarnings("unchecked")
            TagKey<Item>[] tagArray = tags.toArray(new TagKey[0]);
            builder.addTag(tagArray);
        }
        ItemEntry<Item> entry = builder.register();
        EquipmentRegistry.recordItem(self, material, entry);
        if (pattern != null) {
            emitAssemblyRecipe(material, entry);
        }
        if (extraRecipe != null) {
            extraRecipe.emit(material, entry);
        }
    }

    private void emitAssemblyRecipe(Material material, ItemEntry<Item> entry) {
        // Deferred suppliers only — item registry not bound during RegisterEvent HIGHEST.
        var shaped = BuiltinOIRecipeTypes.CRAFTING_SHAPED
                .recipe("shape/equipment/" + entry.identifier().getPath(), entry)
                .category(recipeCategory);
        for (String row : pattern.rows()) {
            shaped.pattern(row);
        }
        Supplier<? extends Item> unlockPart = null;
        for (Map.Entry<Character, MaterialForm> part : pattern.parts().entrySet()) {
            Supplier<Item> partItem = MaterialHelper.materialItemSupplier(material, part.getValue());
            if (unlockPart == null) {
                unlockPart = partItem;
            }
            shaped.define(part.getKey(), partItem);
        }
        if (unlockPart != null) {
            shaped.unlockedBy(unlockPart);
        }
        shaped.save();
    }
}
