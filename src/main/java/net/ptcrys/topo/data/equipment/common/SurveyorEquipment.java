package net.ptcrys.topo.data.equipment.common;

import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.TemplateDisplayName;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.render.MaterialItemRender;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes;
import net.ptcrys.topo.data.material.common.ToolStatsData;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 勘测仪装备插头:材质同时声明 TOOL_STATS(耐久来源 × 类型乘数)与 SURVEY_STATS
 * (观测范围档位)才存在;物品类固定 {@link PipeSurveyorItem},不烘焙挖掘属性。
 */
public final class SurveyorEquipment extends StandardEquipmentBase {

    private final float durabilityMultiplier;

    private SurveyorEquipment(Builder builder) {
        super(builder.registryPath, builder.displayName, builder.tags, builder.pattern,
                builder.render, builder.creativeTab, RecipeCategory.TOOLS, builder.tooltipLines);
        this.durabilityMultiplier = builder.durabilityMultiplier;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean appliesTo(Material material) {
        return material.strategy().data(BuiltinTopoMaterialDataTypes.TOOL_STATS).isPresent() && material.strategy().data(BuiltinTopoMaterialDataTypes.SURVEY_STATS).isPresent();
    }

    @Override
    protected Function<Item.Properties, Item> itemFactory() {
        return PipeSurveyorItem::new;
    }

    @Override
    protected Item.Properties applyStats(Item.Properties properties, Material material,
                                         TagKey<Item> repairItems) {
        ToolStatsData stats = material.strategy().data(BuiltinTopoMaterialDataTypes.TOOL_STATS)
                .orElseThrow(() -> new IllegalStateException(
                        "material " + material.id() + " lost TOOL_STATS between appliesTo and register"));
        return properties
                .durability(Math.round(stats.durability() * durabilityMultiplier))
                .repairable(repairItems);
    }

    public static final class Builder {

        private String registryPath;
        private TemplateDisplayName displayName;
        private float durabilityMultiplier = 1.0f;
        private final List<net.ptcrys.topo.data.equipment.EquipmentTooltipLine> tooltipLines = new ArrayList<>();
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

        /** 类型耐久乘数(基于材质 TOOL_STATS 基础耐久);默认 1。 */
        public Builder durabilityMultiplier(float multiplier) {
            this.durabilityMultiplier = multiplier;
            return this;
        }

        public Builder tooltipLine(String langKey, String nameEn, String nameCn, String descEn, String descCn) {
            addTooltipLine(
                    tooltipLines,
                    OfficialTopoPlugin.INSTANCE.lang(),
                    langKey,
                    nameEn,
                    nameCn,
                    descEn,
                    descCn);
            return this;
        }

        public Builder tag(TagKey<Item> tag) {
            this.tags.add(Objects.requireNonNull(tag, "tag"));
            return this;
        }

        public Builder pattern(EquipmentPattern pattern) {
            this.pattern = Objects.requireNonNull(pattern, "pattern");
            return this;
        }

        public Builder render(MaterialItemRender render) {
            this.render = render;
            return this;
        }

        public Builder creativeTab(ResourceKey<CreativeModeTab> creativeTab) {
            this.creativeTab = creativeTab;
            return this;
        }

        public SurveyorEquipment build() {
            Objects.requireNonNull(registryPath, "surveyor equipment requires registryPath");
            Objects.requireNonNull(displayName, "surveyor equipment requires displayName(en, cn)");
            Objects.requireNonNull(creativeTab, "surveyor equipment requires creativeTab");
            if (!registryPath.contains("%s")) {
                throw new IllegalArgumentException("surveyor equipment name patterns must contain %s");
            }
            if (durabilityMultiplier <= 0) {
                throw new IllegalArgumentException("equipment kind multipliers must be positive");
            }
            return new SurveyorEquipment(this);
        }
    }
}
