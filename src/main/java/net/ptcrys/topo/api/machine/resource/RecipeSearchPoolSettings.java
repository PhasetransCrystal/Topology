package net.ptcrys.topo.api.machine.resource;

import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.machine.component.ServiceKey;

import java.util.Objects;

/**
 * Machine-level recipe search-pool identity. One pool groups <em>all</em> isolatable handlers on
 * this machine (item/fluid inputs <em>and</em> outputs) so search, consume, and emit share the same
 * id — not one pool per resource type.
 */
public interface RecipeSearchPoolSettings {

    ServiceKey<RecipeSearchPoolSettings, Void> KEY = ServiceKey.oi("recipe_search_pool_settings", RecipeSearchPoolSettings.class, Void.class);

    /** Resolved id for every isolatable port on this machine. Always present. */
    RecipeSearchPoolId recipeSearchPoolId();

    /**
     * Resolves the single machine-level setting without allocating a service result on router
     * rebuilds. Components that work without the auto-mounted config provide their semantic
     * fallback explicitly (DEFAULT for inputs, UNIVERSAL for output-only buffers).
     */
    static RecipeSearchPoolId resolve(
                                      MachineComponents components,
                                      RecipeSearchPoolId fallback) {
        Objects.requireNonNull(components, "machine components");
        Objects.requireNonNull(fallback, "fallback pool id");
        var settings = components.servicesCached(KEY);
        return settings.isEmpty() ? fallback : settings.getFirst().value().recipeSearchPoolId();
    }
}
