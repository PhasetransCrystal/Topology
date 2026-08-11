package net.ptcrys.topo.api.recipe.search;

import net.ptcrys.topo.api.recipe.TopoRecipeTypes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Wires recipe-search indexes to the server recipe lifecycle. */
public final class TopoRecipeSearchEvents {

    private TopoRecipeSearchEvents() {}

    public static void register(IEventBus modEventBus) {
        // Optimization: build indexes at lifecycle boundaries, not inside machine ticks.
        // Principle: recipe sets change on server start/reload, while machines search every tick;
        // paying index construction once per recipe-set generation keeps the hot path stable.
        NeoForge.EVENT_BUS.addListener(TopoRecipeSearchEvents::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(TopoRecipeSearchEvents::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(TopoRecipeSearchEvents::onServerStarting);
        NeoForge.EVENT_BUS.addListener(TopoRecipeSearchEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TopoRecipeSearchEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TopoRecipeSearchEvents::onServerStopped);
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        // Optimization: rebuild only on all-player datapack sync, not individual player joins.
        // Principle: OnDatapackSyncEvent fires for both reload and login; login sends data to one
        // client but does not replace the server RecipeManager, so rebuilding every index there is
        // wasted work and can race with machines already using the current generation.
        TopoRecipeTypes.invalidateSearchIndexes();
        TopoRecipeTypes.buildSearchIndexes(event.getPlayerList().getServer());
    }

    private static void onTagsUpdated(TagsUpdatedEvent.ServerDataLoad event) {
        // Optimization safety: tags affect item-key fingerprints, so drop stale indexes as soon
        // as server data reload rebinding completes. The following datapack sync or lazy lookup
        // builds a fresh snapshot against the new RecipeManager/tag generation.
        TopoRecipeTypes.invalidateSearchIndexes();
    }

    private static void onServerStarting(ServerStartingEvent event) {
        TopoRecipeTypes.invalidateSearchIndexes();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        TopoRecipeTypes.buildSearchIndexes(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        TopoRecipeTypes.invalidateSearchIndexes();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        TopoRecipeTypes.invalidateSearchIndexes();
    }
}
