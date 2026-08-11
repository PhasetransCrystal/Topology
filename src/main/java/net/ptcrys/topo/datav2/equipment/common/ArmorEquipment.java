package net.ptcrys.topo.datav2.equipment.common;

import net.ptcrys.topo.apiv2.lang.DisplayNames;
import net.ptcrys.topo.apiv2.lang.TemplateDisplayName;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.render.MaterialItemRender;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes;
import net.ptcrys.topo.datav2.material.common.ArmorStatsData;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Standard armor plug: a material's ARMOR_STATS payload derives one vanilla {@link ArmorMaterial}
 * per material (equipment asset key = the material id, so each material ships pre-tinted wear
 * textures), baked in via {@code props.humanoidArmor(material, armorType)}. Every piece is a
 * plain {@link Item}.
 */
public final class ArmorEquipment extends StandardEquipmentBase {

    private final ArmorType armorType;

    private ArmorEquipment(Builder builder) {
        super(builder.registryPath, builder.displayName, builder.tags, builder.pattern,
                builder.render, builder.creativeTab, RecipeCategory.COMBAT, builder.tooltipLines);
        this.armorType = builder.armorType;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean appliesTo(Material material) {
        return material.strategy().data(BuiltinOIMaterialDataTypes.ARMOR_STATS).isPresent();
    }

    @Override
    protected Item.Properties applyStats(Item.Properties properties, Material material,
                                         TagKey<Item> repairItems) {
        ArmorStatsData stats = material.strategy().data(BuiltinOIMaterialDataTypes.ARMOR_STATS)
                .orElseThrow(() -> new IllegalStateException(
                        "material " + material.id() + " lost ARMOR_STATS between appliesTo and register"));
        ResourceKey<EquipmentAsset> assetId = ResourceKey.create(EquipmentAssets.ROOT_ID, material.id());
        return properties.humanoidArmor(armorMaterial(stats, repairItems, assetId), armorType);
    }

    /** Maps the stats record onto the vanilla armor material; tags and asset key pass through. */
    static ArmorMaterial armorMaterial(ArmorStatsData stats, TagKey<Item> repairIngredient,
                                       ResourceKey<EquipmentAsset> assetId) {
        return new ArmorMaterial(
                stats.durabilityMultiplier(),
                java.util.Map.of(
                        ArmorType.HELMET, stats.helmetDefense(),
                        ArmorType.CHESTPLATE, stats.chestplateDefense(),
                        ArmorType.LEGGINGS, stats.leggingsDefense(),
                        ArmorType.BOOTS, stats.bootsDefense()),
                stats.enchantmentValue(),
                SoundEvents.ARMOR_EQUIP_IRON,
                stats.toughness(),
                stats.knockbackResistance(),
                repairIngredient,
                assetId);
    }

    public static final class Builder {

        private String registryPath;
        private TemplateDisplayName displayName;
        private ArmorType armorType;
        private final List<net.ptcrys.topo.datav2.equipment.EquipmentTooltipLine> tooltipLines = new ArrayList<>();
        private final List<TagKey<Item>> tags = new ArrayList<>();
        private EquipmentPattern pattern;
        private MaterialItemRender render;
        private ResourceKey<CreativeModeTab> creativeTab;

        private Builder() {}

        public Builder registryPath(String pattern) {
            this.registryPath = pattern;
            return this;
        }

        public Builder displayName(String enPattern, String cnPattern) {
            this.displayName = DisplayNames.template(enPattern, cnPattern);
            return this;
        }

        /** Which humanoid slot this piece occupies (also picks the per-slot durability factor). */
        public Builder armorType(ArmorType armorType) {
            this.armorType = Objects.requireNonNull(armorType, "armorType");
            return this;
        }

        /** Item tags attached to every material's item of this kind (additive). */
        public Builder tag(TagKey<Item> tag) {
            this.tags.add(Objects.requireNonNull(tag, "tag"));
            return this;
        }

        /** A function-description line on the item's tooltip panel (registers name+desc handles). */
        public Builder tooltipLine(String langKey, String nameEn, String nameCn, String descEn, String descCn) {
            addTooltipLine(
                    tooltipLines,
                    net.ptcrys.topo.datav2.OfficialOIPlugin.INSTANCE.lang(),
                    langKey,
                    nameEn,
                    nameCn,
                    descEn,
                    descCn);
            return this;
        }

        public Builder pattern(EquipmentPattern pattern) {
            this.pattern = Objects.requireNonNull(pattern, "pattern");
            return this;
        }

        /** Optional: a kind without a render registers items with no generated model. */
        public Builder render(MaterialItemRender render) {
            this.render = render;
            return this;
        }

        public Builder creativeTab(ResourceKey<CreativeModeTab> creativeTab) {
            this.creativeTab = creativeTab;
            return this;
        }

        public ArmorEquipment build() {
            Objects.requireNonNull(registryPath, "armor equipment requires registryPath");
            if (!registryPath.contains("%s")) {
                throw new IllegalArgumentException("armor equipment registryPath must contain %s: " + registryPath);
            }
            Objects.requireNonNull(displayName, "armor equipment requires displayName(en, cn)");
            Objects.requireNonNull(armorType, "armor equipment requires armorType");
            Objects.requireNonNull(creativeTab, "armor equipment requires creativeTab");
            return new ArmorEquipment(this);
        }
    }
}
