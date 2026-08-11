package net.ptcrys.topo.data.bootstrap;

import net.ptcrys.topo.datav2.machine.BuiltinOIMachineCraftingRecipes;

final class ContentDataRegistrationBootstrap {

    private ContentDataRegistrationBootstrap() {}

    static void bootstrap() {
        // Forge-hammer tools: Equipment product table (BuiltinOIEquipment.FORGE_HAMMER).
        // Cold-start recipes: ManualForgeHammerProcessor (material post-processor).
        // Hull/part items only — machine crafting recipes run in registerMachineFollowUps
        // after machine catalogs exist (see code-style §3.11).
        BuiltinOIMachineCraftingRecipes.initItems();
    }
}
