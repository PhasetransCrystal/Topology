package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.*;

/**
 * Fat content/API plugin contract. Domain registration only through {@link #material()},
 * {@link #equipment()}, {@link #recipe()}, {@link #machine()}, {@link #ore()}, {@link #lang()}.
 * Namespace is fixed by {@link #modId()}.
 */
public interface TopoPlugin {

    String modId();

    RegistryCore registry();

    MaterialDomainRegistration material();

    EquipmentDomainRegistration equipment();

    RecipeDomainRegistration recipe();

    MachineDomainRegistration machine();

    OreDomainRegistration ore();

    LangDomainRegistration lang();

    // ── recipe (must run before material post-processors that emit recipes) ──

    /**
     * Resource integrations that own recipe capabilities (and paired machine resource types),
     * before {@code RecipeCapabilities.freeze()}.
     */
    default void registerRecipeFoundation(RecipeDomainRegistration recipe) {}

    /** Recipe types and static production lines before {@code TopoRecipeTypes.freeze()}. */
    default void registerRecipeTypes(RecipeDomainRegistration recipe) {}

    /**
     * Hand-written recipes that depend on materials/forms already minted (production-line tables,
     * gametest recipes). Invoked from {@code bootstrapMaterial} after post-processors and material
     * follow-ups, still before {@code ProductionLines.freeze()} — not from {@code bootstrapRecipe}.
     */
    default void registerRecipes(RecipeDomainRegistration recipe) {}

    // ── material ─────────────────────────────────────────────────────────────

    default void registerMaterialFoundation(MaterialDomainRegistration material) {}

    default void registerMaterials(MaterialDomainRegistration material) {}

    default void registerMaterialCreativeTabs(MaterialDomainRegistration material) {}

    default void registerMaterialFollowUps(MaterialDomainRegistration material) {}

    // ── equipment ────────────────────────────────────────────────────────────

    default void registerEquipmentKinds(EquipmentDomainRegistration equipment) {}

    // ── machine ──────────────────────────────────────────────────────────────

    /**
     * Resource types, UI icons, render types, multiblock element tables, attachment metadata —
     * freeze points are applied by the engine between foundation and machine declaration.
     */
    default void registerMachineFoundation(MachineDomainRegistration machine) {}

    /** Machine catalogs (parts, controllers, single-block, …) before {@code Machines.freeze()}. */
    default void registerMachines(MachineDomainRegistration machine) {}

    /**
     * After machines freeze + block/BE mint: preview plans, pipe catalogs still hosted here until a
     * dedicated pipe domain exists, etc.
     */
    default void registerMachineFollowUps(MachineDomainRegistration machine) {}

    // ── ore ──────────────────────────────────────────────────────────────────

    default void registerOreFoundation(OreDomainRegistration ore) {}

    default void registerOreDisplays(OreDomainRegistration ore) {}

    default void registerOreVeins(OreDomainRegistration ore) {}

    default void registerOreDatagen(OreDomainRegistration ore) {}

    // ── lang ─────────────────────────────────────────────────────────────────

    /** Declare plugin-owned translation keys (before global lang freeze). */
    default void registerLang(LangDomainRegistration lang) {}
}
