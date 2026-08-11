package net.ptcrys.topo.datav2.recipe.productionline;

/** Fixed production-line recipes declared in {@code docs/design/production_line.md}. */
public final class BuiltinOIProductionLineRecipes {

    private BuiltinOIProductionLineRecipes() {}

    public static void init() {
        BuiltinOIEnergyProductionLineRecipes.init();
        BuiltinOIHeatProductionLineRecipes.init();
        BuiltinOIAirProductionLineRecipes.init();
        BuiltinOISulfuricAcidProductionLineRecipes.init();
        BuiltinOISodiumHydroxideProductionLineRecipes.init();
        BuiltinOIElectrolyteProductionLineRecipes.init();
        BuiltinOILuminiteProductionLineRecipes.init();
    }
}
