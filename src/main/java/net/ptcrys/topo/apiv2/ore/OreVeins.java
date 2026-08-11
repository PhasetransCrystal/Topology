package net.ptcrys.topo.apiv2.ore;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.ore.mode.OreVeinMode;
import net.ptcrys.topo.apiv2.ore.policy.OreAirExposurePolicy;
import net.ptcrys.topo.apiv2.ore.policy.OreConflictPolicy;
import net.ptcrys.topo.apiv2.ore.shape.OreVeinShape;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owner facade for the ore vein product table — the worldgen domain's single contribution path.
 *
 * <p>
 * Builder is charter-approved: sealed {@link OrePlacement} assembly, cross-field validation,
 * material×form membership checks, and a derived {@code byMaterial} index at freeze. {@code build()}
 * validates then self-registers; failure leaves no registry side effects.
 */
public final class OreVeins {

    private static final FreezableStrategyRegistry<Identifier, OreVein, OreVein> REGISTRY = FreezableStrategyRegistry.create("ore veins");

    private static volatile Snapshot frozenSnapshot;

    private OreVeins() {}

    /** Single write entry for {@link net.ptcrys.topo.apiv2.plugin.OreDomainRegistration#vein}. */
    public static Builder begin(Identifier id, LangDomainRegistration lang) {
        return new Builder(Objects.requireNonNull(id, "id"), Objects.requireNonNull(lang, "lang"));
    }

    public static List<OreVein> view() {
        return REGISTRY.handlesView();
    }

    public static List<OreEnvironment> environments() {
        return snapshot().environments();
    }

    public static List<OreVein> byMaterial(Material material) {
        return snapshot().byMaterial().getOrDefault(Objects.requireNonNull(material, "material"), List.of());
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        if (!REGISTRY.isFrozen()) {
            for (OreVein vein : REGISTRY.handlesView()) {
                validateMaterialForms(vein);
            }
            REGISTRY.freeze();
        }
        frozenSnapshot = buildSnapshot(REGISTRY.handlesView());
    }

    private static void validateMaterialForms(OreVein vein) {
        for (OreVeinEntry entry : vein.entries()) {
            Material material = entry.material();
            for (OreHostRule host : vein.environment().hostRules()) {
                MaterialForm form = host.generatedForm();
                if (!material.strategy().forms().contains(form)) {
                    throw new IllegalStateException(
                            "ore vein " + vein.id() + ": material " + material.id() + " does not declare form " + form.id() + " required by host rule " + host.replaceableTag().location());
                }
            }
        }
    }

    private static Snapshot snapshot() {
        Snapshot snapshot = frozenSnapshot;
        return snapshot != null ? snapshot : buildSnapshot(REGISTRY.handlesView());
    }

    private static Snapshot buildSnapshot(List<OreVein> veins) {
        var environments = new LinkedHashSet<OreEnvironment>();
        var byMaterial = new LinkedHashMap<Material, LinkedHashSet<OreVein>>();
        for (OreVein vein : veins) {
            environments.add(vein.environment());
            for (OreVeinEntry entry : vein.entries()) {
                byMaterial.computeIfAbsent(entry.material(), ignored -> new LinkedHashSet<>()).add(vein);
            }
        }
        var byMaterialCopy = new LinkedHashMap<Material, List<OreVein>>();
        byMaterial.forEach((material, list) -> byMaterialCopy.put(material, List.copyOf(list)));
        return new Snapshot(List.copyOf(environments), Collections.unmodifiableMap(byMaterialCopy));
    }

    private record Snapshot(List<OreEnvironment> environments, Map<Material, List<OreVein>> byMaterial) {}

    /**
     * Vein declaration builder. Exactly one of {@link #feature} or {@link #grid} must be chosen;
     * height is required; lang, environment, air exposure, and at least one entry are required.
     */
    public static final class Builder {

        private final Identifier id;
        private OreEnvironment environment;
        private OreVeinMode mode;
        private PlacementKind kind;
        // feature fields
        private int featureSize;
        private int attemptsPerChunk;
        private boolean triangularHeight;
        // grid fields
        private OreVeinShape shape;
        private int radiusBlocks;
        private int gridSizeChunks;
        private int randomOffsetBlocks;
        private double density = Double.NaN;
        private int weight = -1;
        private Integer priority;
        private OreConflictPolicy conflictPolicy;
        // shared
        private boolean heightSet;
        private int minY;
        private int maxY;
        private OreAirExposurePolicy airExposurePolicy;
        private double airExposureDiscardChance = Double.NaN;
        private final List<OreVeinEntry> entries = new ArrayList<>();
        private final LangDomainRegistration langApi;
        private LangKey nameLang;

        private Builder(Identifier id, LangDomainRegistration langApi) {
            this.id = Objects.requireNonNull(id, "id");
            this.langApi = langApi;
        }

        public Builder lang(String en, String cn) {
            this.nameLang = langApi.resource(id, "ore_vein", en, cn);
            return this;
        }

        public Builder environment(OreEnvironment environment) {
            this.environment = Objects.requireNonNull(environment, "environment");
            return this;
        }

        public Builder airExposure(OreAirExposurePolicy policy, double discardChance) {
            this.airExposurePolicy = Objects.requireNonNull(policy, "policy");
            this.airExposureDiscardChance = discardChance;
            return this;
        }

        public Builder entry(Material material, int weight) {
            this.entries.add(new OreVeinEntry(material, weight));
            return this;
        }

        /**
         * Declares a vanilla-feature (small) vein. Only feature fields are accepted after this call.
         */
        public Builder feature(OreVeinMode mode, int size, int attemptsPerChunk) {
            requireKindUnset();
            this.mode = Objects.requireNonNull(mode, "mode");
            this.kind = PlacementKind.FEATURE;
            this.featureSize = size;
            this.attemptsPerChunk = attemptsPerChunk;
            return this;
        }

        /**
         * Declares a deterministic grid (large) vein. Only grid fields are accepted after this call.
         */
        public Builder grid(OreVeinMode mode, OreVeinShape shape) {
            requireKindUnset();
            this.mode = Objects.requireNonNull(mode, "mode");
            this.kind = PlacementKind.GRID;
            this.shape = Objects.requireNonNull(shape, "shape");
            return this;
        }

        public Builder height(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
            this.heightSet = true;
            this.triangularHeight = false;
            return this;
        }

        public Builder triangularHeight(int minY, int maxY) {
            requireFeature("triangularHeight");
            this.minY = minY;
            this.maxY = maxY;
            this.heightSet = true;
            this.triangularHeight = true;
            return this;
        }

        public Builder radius(int radiusBlocks) {
            requireGrid("radius");
            this.radiusBlocks = radiusBlocks;
            return this;
        }

        public Builder spacing(int gridSizeChunks, int randomOffsetBlocks) {
            requireGrid("spacing");
            this.gridSizeChunks = gridSizeChunks;
            this.randomOffsetBlocks = randomOffsetBlocks;
            return this;
        }

        public Builder density(double density) {
            requireGrid("density");
            this.density = density;
            return this;
        }

        public Builder weight(int weight) {
            requireGrid("weight");
            this.weight = weight;
            return this;
        }

        public Builder priority(int priority) {
            requireGrid("priority");
            this.priority = priority;
            return this;
        }

        public Builder conflict(OreConflictPolicy policy) {
            requireGrid("conflict");
            this.conflictPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public OreVein build() {
            require(nameLang != null, "lang");
            require(environment != null, "environment");
            require(airExposurePolicy != null, "airExposure");
            require(kind != null, "feature(...) or grid(...)");
            require(heightSet, "height");
            check(airExposureDiscardChance >= 0.0 && airExposureDiscardChance <= 1.0,
                    "air exposure discard chance must be 0..1");
            check(!entries.isEmpty(), "needs at least one material entry");

            OrePlacement placement = switch (kind) {
                case FEATURE -> new OrePlacement.Feature(
                        mode, featureSize, attemptsPerChunk, minY, maxY, triangularHeight);
                case GRID -> {
                    require(shape != null, "shape");
                    require(radiusBlocks > 0, "radius");
                    require(gridSizeChunks > 0, "spacing");
                    require(!Double.isNaN(density), "density");
                    require(weight > 0, "weight");
                    require(priority != null, "priority");
                    require(conflictPolicy != null, "conflict");
                    yield new OrePlacement.Grid(
                            mode,
                            shape,
                            radiusBlocks,
                            gridSizeChunks,
                            randomOffsetBlocks,
                            density,
                            weight,
                            priority,
                            conflictPolicy,
                            minY,
                            maxY);
                }
            };

            mode.strategy().validate(placement);

            OreVein vein = new OreVein(
                    id,
                    environment,
                    placement,
                    airExposurePolicy,
                    airExposureDiscardChance,
                    entries,
                    nameLang);
            validateMaterialForms(vein);
            return REGISTRY.register(id, vein, vein);
        }

        private void requireKindUnset() {
            if (kind != null) {
                throw new IllegalStateException(
                        "ore vein " + id + ": placement channel already set to " + kind);
            }
        }

        private void requireFeature(String field) {
            if (kind != PlacementKind.FEATURE) {
                throw new IllegalStateException(
                        "ore vein " + id + ": " + field + " is only valid after feature(...)");
            }
        }

        private void requireGrid(String field) {
            if (kind != PlacementKind.GRID) {
                throw new IllegalStateException(
                        "ore vein " + id + ": " + field + " is only valid after grid(...)");
            }
        }

        private void require(boolean condition, String field) {
            if (!condition) {
                throw new IllegalStateException("ore vein " + id + " is missing required field: " + field);
            }
        }

        private void check(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException("ore vein " + id + ": " + message);
            }
        }

        private enum PlacementKind {
            FEATURE,
            GRID
        }
    }
}
