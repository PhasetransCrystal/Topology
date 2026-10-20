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

    private BuiltinProcessors() {}

    public static void init() {}
}
