package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.OfficialTopoPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BuiltinTopoProductionLines {

    private static final RecipeDomainRegistration RECIPE = OfficialTopoPlugin.INSTANCE.recipe();

    public static final ProductionLine ENERGY = RECIPE.productionLine("energy");
    public static final ProductionLine ADVANCED_ENERGY = RECIPE.productionLine("advanced_energy");
    public static final ProductionLine HEAT = RECIPE.productionLine("heat");
    public static final ProductionLine MACHINE_COMPONENT = RECIPE.productionLine("machine_component");
    public static final ProductionLine WIRE_TIER = RECIPE.productionLine("wire_tier");
    public static final ProductionLine FINE_GRINDING = RECIPE.productionLine("fine_grinding");
    public static final ProductionLine AIR = RECIPE.productionLine("air");
    public static final ProductionLine SULFURIC_ACID = RECIPE.productionLine("sulfuric_acid");
    public static final ProductionLine SODIUM_HYDROXIDE = RECIPE.productionLine("sodium_hydroxide");
    public static final ProductionLine ELECTROLYTE = RECIPE.productionLine("electrolyte");
    public static final ProductionLine LUMINITE = RECIPE.productionLine("luminite");

    private static final List<ProductionLine> SULFURIC_ACID_ORE_CONSUMERS = new ArrayList<>();
    private static final List<ProductionLine> SODIUM_HYDROXIDE_ORE_CONSUMERS = new ArrayList<>();
    private static final List<ProductionLine> ELECTROLYTE_ORE_CONSUMERS = new ArrayList<>();
    private static final List<ProductionLine> LUMINITE_ORE_CONSUMERS = new ArrayList<>();

    private BuiltinTopoProductionLines() {}

    public static void init() {}

    public static ProductionLine registerOreProcessing2x(Material material) {
        return registerOreProcessingLine("ore_processing_2x", material);
    }

    public static ProductionLine registerOreProcessing3x(Material material) {
        return registerOreProcessingLine("ore_processing_3x", material);
    }

    public static ProductionLine registerOreProcessing4x(Material material) {
        ProductionLine line = registerOreProcessingLine("ore_processing_4x", material);
        SULFURIC_ACID_ORE_CONSUMERS.add(line);
        return line;
    }

    public static ProductionLine registerOreProcessing6x(Material material) {
        ProductionLine line = registerOreProcessingLine("ore_processing_6x", material);
        SULFURIC_ACID_ORE_CONSUMERS.add(line);
        SODIUM_HYDROXIDE_ORE_CONSUMERS.add(line);
        ELECTROLYTE_ORE_CONSUMERS.add(line);
        return line;
    }

    public static ProductionLine registerOreProcessing12x(Material material) {
        ProductionLine line = registerOreProcessingLine("ore_processing_12x", material);
        SULFURIC_ACID_ORE_CONSUMERS.add(line);
        SODIUM_HYDROXIDE_ORE_CONSUMERS.add(line);
        ELECTROLYTE_ORE_CONSUMERS.add(line);
        LUMINITE_ORE_CONSUMERS.add(line);
        return line;
    }

    public static ProductionLine[] sulfuricAcidConsumers() {
        List<ProductionLine> lines = new ArrayList<>();
        lines.add(SULFURIC_ACID);
        lines.addAll(SULFURIC_ACID_ORE_CONSUMERS);
        lines.add(ELECTROLYTE);
        lines.add(LUMINITE);
        return lines.toArray(ProductionLine[]::new);
    }

    public static ProductionLine[] sodiumHydroxideConsumers() {
        List<ProductionLine> lines = new ArrayList<>();
        lines.add(SODIUM_HYDROXIDE);
        lines.addAll(SODIUM_HYDROXIDE_ORE_CONSUMERS);
        lines.add(LUMINITE);
        return lines.toArray(ProductionLine[]::new);
    }

    public static ProductionLine[] electrolyteConsumers() {
        List<ProductionLine> lines = new ArrayList<>();
        lines.add(ELECTROLYTE);
        lines.addAll(ELECTROLYTE_ORE_CONSUMERS);
        lines.add(LUMINITE);
        return lines.toArray(ProductionLine[]::new);
    }

    public static ProductionLine[] luminiteConsumers() {
        List<ProductionLine> lines = new ArrayList<>();
        lines.add(LUMINITE);
        lines.addAll(LUMINITE_ORE_CONSUMERS);
        return lines.toArray(ProductionLine[]::new);
    }

    private static ProductionLine registerOreProcessingLine(String prefix, Material material) {
        Objects.requireNonNull(material, "material");
        // Namespace + path so cross-mod materials with the same path never collide.
        String ns = material.id().getNamespace();
        String path = material.id().getPath();
        String linePath = ns.equals(RECIPE.modId()) ? prefix + "_" + path : prefix + "_" + ns + "_" + path;
        return RECIPE.productionLine(linePath);
    }
}
