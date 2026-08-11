package net.ptcrys.topo.api.recipe.capability;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeCapabilities {

    private static final FreezableStrategyRegistry<Identifier, RecipeCapability<?, ?>, RecipeCapability<?, ?>> REGISTRY = FreezableStrategyRegistry.create("recipe capabilities");
    private static volatile RecipeCapability<?, ?>[] indexingSnapshot = new RecipeCapability<?, ?>[0];
    private static volatile Map<MachineResourceType<?>, SlottedRecipeCapability<?, ?, ?>> slottedByResourceType = Map.of();

    private RecipeCapabilities() {}

    /**
     * Internal write entry for {@link RecipeDomainRegistration#capability}.
     * Product code must not call this directly.
     */
    public static <C extends RecipeCapability<?, ?>> C begin(C capability) {
        REGISTRY.register(capability.id(), capability, capability);
        return capability;
    }

    public static RecipeCapability<?, ?> get(Identifier id) {
        return REGISTRY.get(id);
    }

    public static RecipeCapability<?, ?> require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<RecipeCapability<?, ?>> registered() {
        return REGISTRY.handlesView();
    }

    /**
     * Optimization: runtime recipe search iterates this frozen array instead of filtering every
     * registered capability on each lookup. Principle: registration is immutable after bootstrap,
     * so the hot path serves one stable snapshot with zero per-call allocation. Contract: callers
     * must treat the array as read-only — it is the single indexing accessor (the cloning twin
     * was deleted; general enumeration goes through {@link #registered()}).
     */
    public static RecipeCapability<?, ?>[] indexingArrayForSearch() {
        return indexingSnapshot;
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    /**
     * KHSD minting fence: a use may only be published from the exact capability instance
     * registered under its id. Wild handles (same id, foreign instance) are rejected here.
     */
    static void verifyOwned(Identifier id, RecipeCapability<?, ?> capability) {
        REGISTRY.verifyOwnedHandle(id, capability);
    }

    public static void freeze() {
        REGISTRY.freeze();
        List<RecipeCapability<?, ?>> capabilities = REGISTRY.handlesView();
        int indexingCount = 0;
        for (RecipeCapability<?, ?> capability : capabilities) {
            if (capability.contributesIndexKeys()) {
                indexingCount++;
            }
        }
        RecipeCapability<?, ?>[] indexing = new RecipeCapability<?, ?>[indexingCount];
        int index = 0;
        Map<MachineResourceType<?>, SlottedRecipeCapability<?, ?, ?>> slotted = new LinkedHashMap<>();
        for (RecipeCapability<?, ?> capability : capabilities) {
            if (capability.contributesIndexKeys()) {
                indexing[index++] = capability;
            }
            if (capability instanceof SlottedRecipeCapability<?, ?, ?> slottedCapability) {
                SlottedRecipeCapability<?, ?, ?> previous = slotted.putIfAbsent(slottedCapability.resourceType(), slottedCapability);
                if (previous != null) {
                    throw new IllegalStateException("recipe capabilities: resource type " + slottedCapability.resourceType().id() + " has two slotted capabilities (" + previous.id() + ", " + slottedCapability.id() + "); the pairing must be 1:1");
                }
            }
        }
        indexingSnapshot = indexing;
        slottedByResourceType = Map.copyOf(slotted);
    }

    /**
     * 资源类型 → SLOTTED capability 的冻结期反查（1:1 在 freeze 校验）。实况槽位与存储页 UI
     * 用它把端口资源类型派遣回家族的 {@link SlottedRecipeCapability#createLiveSlotWidget}。
     */
    public static SlottedRecipeCapability<?, ?, ?> slottedFor(MachineResourceType<?> resourceType) {
        SlottedRecipeCapability<?, ?, ?> capability = slottedByResourceType.get(resourceType);
        if (capability == null) {
            throw new IllegalStateException(
                    "no slotted recipe capability for resource type " + resourceType.id());
        }
        return capability;
    }
}
