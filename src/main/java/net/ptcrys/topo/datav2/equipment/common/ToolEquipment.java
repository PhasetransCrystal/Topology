package net.ptcrys.topo.datav2.equipment.common;

import net.ptcrys.topo.apiv2.lang.DisplayNames;
import net.ptcrys.topo.apiv2.lang.TemplateDisplayName;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.render.MaterialItemRender;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes;
import net.ptcrys.topo.datav2.material.common.ToolStatsData;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Unified tool plug: material {@code TOOL_STATS} plus kind multipliers derive one vanilla
 * {@link ToolMaterial}; {@code statsApplier} bakes mining/combat/durability properties.
 * Opt-in {@link Builder#craftingRemainderTool()} uses {@link PrimitiveToolItem} (workbench
 * durability drain). World tools stay plain {@link Item} / {@link SneakBypassToolItem}.
 */
public final class ToolEquipment extends StandardEquipmentBase {

    private final BiFunction<Item.Properties, ToolMaterial, Item.Properties> statsApplier;
    private final float durabilityMultiplier;
    private final float miningSpeedMultiplier;
    private final boolean sneakBypassUse;
    private final boolean craftingRemainderTool;
    private final net.ptcrys.topo.apiv2.machine.MachineItemBehavior machineBehavior;

    private ToolEquipment(Builder builder) {
        super(builder.registryPath, builder.displayName, builder.tags, builder.pattern,
                builder.render, builder.creativeTab, builder.recipeCategory, builder.tooltipLines,
                builder.extraRecipe);
        this.statsApplier = builder.statsApplier;
        this.durabilityMultiplier = builder.durabilityMultiplier;
        this.miningSpeedMultiplier = builder.miningSpeedMultiplier;
        this.sneakBypassUse = builder.sneakBypassUse;
        this.craftingRemainderTool = builder.craftingRemainderTool;
        this.machineBehavior = builder.machineBehavior;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean appliesTo(Material material) {
        return material.strategy().data(BuiltinOIMaterialDataTypes.TOOL_STATS).isPresent();
    }

    @Override
    protected java.util.function.Function<Item.Properties, Item> itemFactory() {
        if (craftingRemainderTool) {
            return PrimitiveToolItem::new;
        }
        return sneakBypassUse ? SneakBypassToolItem::new : Item::new;
    }

    @Override
    public net.ptcrys.topo.apiv2.machine.MachineItemBehavior machineBehavior() {
        return machineBehavior;
    }

    @Override
    protected Item.Properties applyStats(Item.Properties properties, Material material,
                                         TagKey<Item> repairItems) {
        ToolStatsData stats = material.strategy().data(BuiltinOIMaterialDataTypes.TOOL_STATS)
                .orElseThrow(() -> new IllegalStateException(
                        "material " + material.id() + " lost TOOL_STATS between appliesTo and register"));
        ToolMaterial toolMaterial = toolMaterial(stats, durabilityMultiplier, miningSpeedMultiplier, repairItems);
        return statsApplier.apply(properties, toolMaterial).repairable(repairItems);
    }

    /** Folds the kind multipliers into the material stats; tags pass through untouched. */
    static ToolMaterial toolMaterial(ToolStatsData stats, float durabilityMultiplier,
                                     float miningSpeedMultiplier, TagKey<Item> repairItems) {
        return new ToolMaterial(
                stats.incorrectForDrops(),
                Math.round(stats.durability() * durabilityMultiplier),
                stats.miningSpeed() * miningSpeedMultiplier,
                stats.attackBonus(),
                stats.enchantmentValue(),
                repairItems);
    }

    public static final class Builder {

        private String registryPath;
        private TemplateDisplayName displayName;
        private BiFunction<Item.Properties, ToolMaterial, Item.Properties> statsApplier;
        private float durabilityMultiplier = 1.0f;
        private float miningSpeedMultiplier = 1.0f;
        private boolean sneakBypassUse;
        private boolean craftingRemainderTool;
        private net.ptcrys.topo.apiv2.machine.MachineItemBehavior machineBehavior;
        private final List<net.ptcrys.topo.datav2.equipment.EquipmentTooltipLine> tooltipLines = new ArrayList<>();
        private final List<TagKey<Item>> tags = new ArrayList<>();
        private EquipmentPattern pattern;
        private EquipmentExtraRecipe extraRecipe;
        private MaterialItemRender render;
        private ResourceKey<CreativeModeTab> creativeTab;
        private RecipeCategory recipeCategory = RecipeCategory.TOOLS;

        private Builder() {}

        public Builder registryPath(String pattern) {
            this.registryPath = pattern;
            return this;
        }

        /**
         * Unique product entry: both en/cn patterns required ({@code %s} = material name). Do not
         * pass constructed template objects (code-style §3.13).
         */
        public Builder displayName(String enPattern, String cnPattern) {
            this.displayName = DisplayNames.template(enPattern, cnPattern);
            return this;
        }

        /** Which vanilla properties recipe bakes the derived ToolMaterial into the item. */
        public Builder statsApplier(BiFunction<Item.Properties, ToolMaterial, Item.Properties> applier) {
            this.statsApplier = Objects.requireNonNull(applier, "applier");
            return this;
        }

        /** Kind durability factor over the material's base durability; defaults to 1. */
        public Builder durabilityMultiplier(float multiplier) {
            this.durabilityMultiplier = multiplier;
            return this;
        }

        /** Kind mining-speed factor over the material's base speed; defaults to 1. */
        public Builder miningSpeedMultiplier(float multiplier) {
            this.miningSpeedMultiplier = multiplier;
            return this;
        }

        /** Opt-in: shift-clicking a block still reaches the block's interaction (wrench on pipes). */
        public Builder sneakBypassUse() {
            this.sneakBypassUse = true;
            return this;
        }

        /**
         * Opt-in: item is a {@link PrimitiveToolItem} that loses 1 durability as crafting remainder
         * (forge hammer / file / wire cutter cold-start tools).
         */
        public Builder craftingRemainderTool() {
            this.craftingRemainderTool = true;
            return this;
        }

        /** The machine interaction of this kind's items (regulator: toggle run/halt). */
        public Builder machineBehavior(net.ptcrys.topo.apiv2.machine.MachineItemBehavior behavior) {
            this.machineBehavior = Objects.requireNonNull(behavior, "behavior");
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

        /** Item tags attached to every material's item of this kind (additive). */
        public Builder tag(TagKey<Item> tag) {
            this.tags.add(Objects.requireNonNull(tag, "tag"));
            return this;
        }

        public Builder pattern(EquipmentPattern pattern) {
            this.pattern = Objects.requireNonNull(pattern, "pattern");
            return this;
        }

        /**
         * Extra datagen recipe when {@link EquipmentPattern} cannot express ingredients (e.g. stick
         * + ingot). Runs in addition to pattern recipes if both are set.
         */
        public Builder extraRecipe(EquipmentExtraRecipe recipe) {
            this.extraRecipe = Objects.requireNonNull(recipe, "recipe");
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

        /** Recipe book category of the assembly recipe; defaults to TOOLS. */
        public Builder recipeCategory(RecipeCategory category) {
            this.recipeCategory = Objects.requireNonNull(category, "category");
            return this;
        }

        public ToolEquipment build() {
            requirePattern(registryPath, "registryPath");
            Objects.requireNonNull(displayName, "displayName(en, cn) required");
            Objects.requireNonNull(statsApplier, "tool equipment requires statsApplier");
            Objects.requireNonNull(creativeTab, "tool equipment requires creativeTab");
            if (durabilityMultiplier <= 0 || miningSpeedMultiplier <= 0) {
                throw new IllegalArgumentException("equipment kind multipliers must be positive");
            }
            if (craftingRemainderTool && sneakBypassUse) {
                throw new IllegalStateException(
                        "tool equipment cannot combine craftingRemainderTool with sneakBypassUse");
            }
            return new ToolEquipment(this);
        }

        private static void requirePattern(String value, String field) {
            Objects.requireNonNull(value, () -> "tool equipment requires " + field);
            if (!value.contains("%s")) {
                throw new IllegalArgumentException("tool equipment " + field + " must contain %s: " + value);
            }
        }
    }
}
