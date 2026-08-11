package net.ptcrys.topo.data.recipe.productionline;

/** Fixed production-line recipes declared in {@code docs/design/production_line.md}. */
public final class BuiltinTopoProductionLineRecipes {

    private BuiltinTopoProductionLineRecipes() {}

    public static void init() {
        BuiltinTopoEnergyProductionLineRecipes.init();
        BuiltinTopoHeatProductionLineRecipes.init();
        BuiltinTopoAirProductionLineRecipes.init();
        BuiltinTopoSulfuricAcidProductionLineRecipes.init();
        BuiltinTopoSodiumHydroxideProductionLineRecipes.init();
        BuiltinTopoElectrolyteProductionLineRecipes.init();
        BuiltinTopoLuminiteProductionLineRecipes.init();
    }
}
