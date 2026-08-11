package net.ptcrys.topo.data.recipe;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.data.DataScope;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.api.machine.resource.ResourceDataFieldFactory;
import net.ptcrys.topo.api.machine.ui.MachineUiIcons;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarDestroyedBehavior;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarDestroyedBehaviors;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.data.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Builtin resource-family product table. Machine resource types register only through
 * {@link OfficialTopoPlugin#machine()}; recipe capabilities only through
 * {@link OfficialTopoPlugin#recipe()}.
 */
public final class BuiltinTopoResourceIntegrations {

    private static final MachineDomainRegistration MACHINE = OfficialTopoPlugin.INSTANCE.machine();
    private static final RecipeDomainRegistration RECIPE = OfficialTopoPlugin.INSTANCE.recipe();

    /** 物品:SLOTTED 槽位形态,NeoForge 物品方块能力。 */
    public static final BuiltinResourceIntegration<ItemResource, ItemRecipeCapability> ITEM = item(
            "item",
            () -> BuiltinTopoMachineUiIcons.chest(0xFFC9A26B),
            "Item",
            "物品");

    /** 流体:SLOTTED 槽位形态,NeoForge 流体方块能力。 */
    public static final BuiltinResourceIntegration<FluidResource, FluidRecipeCapability> FLUID = fluid(
            "fluid",
            () -> BuiltinTopoMachineUiIcons.drop(0xFF4FA3E8),
            "Fluid",
            "流体");

    /** 能量:IMPLICIT 资源条形态;燃烧类发电机的产物,电力消耗机器的口粮。被破坏走电容放电(密度 1)。 */
    public static final BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> ENERGY = scalar(
            "energy", "Energy", "能量",
            0xFFE8C547, BuiltinTopoMachineUiIcons::bolt, ScalarDestroyedBehaviors.energyDischarge(1));

    /** 高级能量:IMPLICIT 资源条形态;由能量按 10:1 转换的高阶资源。被破坏走电容放电(密度 10)。 */
    public static final BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> ADVANCED_ENERGY = scalar(
            "advanced_energy", "Advanced Energy", "高级能量",
            0xFFB76EF0, BuiltinTopoMachineUiIcons::spark, ScalarDestroyedBehaviors.energyDischarge(10));

    /** 热量:IMPLICIT 资源条形态;锅炉类机器的产物。被破坏走破壳泄热(点燃不爆炸)。 */
    public static final BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> HEAT = scalar(
            "heat", "Heat", "热量",
            0xFFE2603B, BuiltinTopoMachineUiIcons::flame, ScalarDestroyedBehaviors.heatFlash());

    private BuiltinTopoResourceIntegrations() {}

    public static void init() {}

    private static BuiltinResourceIntegration<ItemResource, ItemRecipeCapability> item(
                                                                                       String path,
                                                                                       Supplier<IGuiTexture> icon,
                                                                                       String en,
                                                                                       String cn) {
        MachineResourceType<ItemResource> resourceType = MACHINE.resourceTypeWithDataField(
                path, ItemResource.class, Capabilities.Item.BLOCK, itemDataFieldFactory());
        ItemRecipeCapability recipeCapability = RECIPE.capability(new ItemRecipeCapability(resourceType));
        // Resource type + capability + name + icon declared together (resource family ownership).
        MACHINE.bindResourceName(resourceType, en, cn);
        MachineUiIcons.register(resourceType, Objects.requireNonNull(icon, "resource icon"));
        return new BuiltinResourceIntegration<>(resourceType, recipeCapability);
    }

    private static BuiltinResourceIntegration<FluidResource, FluidRecipeCapability> fluid(
                                                                                          String path,
                                                                                          Supplier<IGuiTexture> icon,
                                                                                          String en,
                                                                                          String cn) {
        MachineResourceType<FluidResource> resourceType = MACHINE.resourceTypeWithDataField(
                path, FluidResource.class, Capabilities.Fluid.BLOCK, fluidDataFieldFactory());
        FluidRecipeCapability recipeCapability = RECIPE.capability(new FluidRecipeCapability(resourceType));
        MACHINE.bindResourceName(resourceType, en, cn);
        MachineUiIcons.register(resourceType, Objects.requireNonNull(icon, "resource icon"));
        return new BuiltinResourceIntegration<>(resourceType, recipeCapability);
    }

    private static ResourceDataFieldFactory<ItemResource> itemDataFieldFactory() {
        return DataScope::itemResourceHandler;
    }

    private static ResourceDataFieldFactory<FluidResource> fluidDataFieldFactory() {
        return DataScope::fluidResourceHandler;
    }

    private static <R extends Resource, C extends RecipeCapability<?, ?>> BuiltinResourceIntegration<R, C> integrate(
                                                                                                                     String path,
                                                                                                                     Class<R> resourceClass,
                                                                                                                     @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability,
                                                                                                                     Function<MachineResourceType<R>, C> recipeCapabilityFactory,
                                                                                                                     Supplier<IGuiTexture> icon,
                                                                                                                     String en,
                                                                                                                     String cn) {
        return integrate(
                path,
                resourceClass,
                blockCapability,
                MachineResourceTypes.valueIoDataFieldFactory(),
                recipeCapabilityFactory,
                icon,
                en,
                cn);
    }

    private static <R extends Resource, C extends RecipeCapability<?, ?>> BuiltinResourceIntegration<R, C> integrate(
                                                                                                                     String path,
                                                                                                                     Class<R> resourceClass,
                                                                                                                     @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability,
                                                                                                                     ResourceDataFieldFactory<R> dataFieldFactory,
                                                                                                                     Function<MachineResourceType<R>, C> recipeCapabilityFactory,
                                                                                                                     Supplier<IGuiTexture> icon,
                                                                                                                     String en,
                                                                                                                     String cn) {
        MachineResourceType<R> resourceType = MACHINE.resourceTypeWithDataField(path, resourceClass, blockCapability, dataFieldFactory);
        C recipeCapability = RECIPE.capability(recipeCapabilityFactory.apply(resourceType));
        MACHINE.bindResourceName(resourceType, en, cn);
        MachineUiIcons.register(resourceType, Objects.requireNonNull(icon, "resource icon"));
        return new BuiltinResourceIntegration<>(resourceType, recipeCapability);
    }

    private static BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> scalar(
                                                                                             String path,
                                                                                             String en,
                                                                                             String cn,
                                                                                             int color,
                                                                                             IntFunction<IGuiTexture> iconRecipe,
                                                                                             ScalarDestroyedBehavior destroyedBehavior) {
        Identifier id = MACHINE.id(path);
        MachineResourceType<ScalarResource> resourceType = MACHINE.resourceTypeWithDataField(
                path,
                ScalarResource.class,
                BlockCapability.createSided(id, ResourceHandler.asClass()),
                MachineResourceTypes.valueIoDataFieldFactory());
        // Domain owns lang mint; same LangKey shared by type + scalar resource handle.
        LangKey nameLang = MACHINE.bindResourceName(resourceType, en, cn);
        ScalarResource resource = ScalarResource.register(id, nameLang, color, destroyedBehavior);
        ScalarRecipeCapability recipeCapability = RECIPE.capability(new ScalarRecipeCapability(id, resourceType, resource));
        MachineUiIcons.register(resourceType, () -> iconRecipe.apply(color));
        return new BuiltinResourceIntegration<>(resourceType, recipeCapability);
    }

    /**
     * One builtin resource family handle — the single integration shape shared by every family.
     */
    public record BuiltinResourceIntegration<R extends Resource, C extends RecipeCapability<?, ?>>(
                                                                                                   MachineResourceType<R> resourceType,
                                                                                                   C recipeCapability) {

        public BuiltinResourceIntegration {
            Objects.requireNonNull(resourceType, "resource type");
            Objects.requireNonNull(recipeCapability, "recipe capability");
        }

        public Identifier id() {
            return resourceType.id();
        }
    }
}
