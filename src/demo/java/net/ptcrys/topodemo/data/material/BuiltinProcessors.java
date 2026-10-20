package net.ptcrys.topodemo.data.material;

import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.api.material.common.GrindingProcessor;
import net.ptcrys.topodemo.api.recipe.BuiltinResources;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;

public final class BuiltinProcessors {

    public static final GrindingProcessor GRINDING = TopoDemoPlugin.INSTANCE.material()
            .postProcessor("grinding", new GrindingProcessor(TopoDemoPlugin.INSTANCE.material().id("grinding"),
                    BuiltinMaterialForms.INGOT,
                    BuiltinMaterialForms.DUST,
                    BuiltinRecipeTypes.GRINDING,
                    BuiltinRecipeTypes.METALLURGY,
                    BuiltinResources.ITEM));

    public static final GrindingProcessor FUEL_GRINDING = TopoDemoPlugin.INSTANCE.material()
            .postProcessor("fuel_grinding", new GrindingProcessor(TopoDemoPlugin.INSTANCE.material().id("fuel_grinding"),
                    BuiltinMaterialForms.FUEL,
                    BuiltinMaterialForms.DUST,
                    BuiltinRecipeTypes.GRINDING,
                    BuiltinRecipeTypes.METALLURGY,
                    BuiltinResources.ITEM));

    public static final GrindingProcessor GEM_GRINDING = TopoDemoPlugin.INSTANCE.material()
            .postProcessor("gem_grinding", new GrindingProcessor(TopoDemoPlugin.INSTANCE.material().id("gem_grinding"),
                    BuiltinMaterialForms.GEM,
                    BuiltinMaterialForms.DUST,
                    BuiltinRecipeTypes.GRINDING,
                    BuiltinRecipeTypes.METALLURGY,
                    BuiltinResources.ITEM));

    public static final GrindingProcessor SHARD_GRINDING = TopoDemoPlugin.INSTANCE.material()
            .postProcessor("shard_grinding", new GrindingProcessor(TopoDemoPlugin.INSTANCE.material().id("shard_grinding"),
                    BuiltinMaterialForms.SHARD,
                    BuiltinMaterialForms.DUST,
                    BuiltinRecipeTypes.GRINDING,
                    BuiltinRecipeTypes.METALLURGY,
                    BuiltinResources.ITEM));

    private BuiltinProcessors() {}

    public static void init() {}
}
