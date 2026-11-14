package net.ptcrys.topo.api.recipe;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.recipe.search.TopoRecipeSearchIndex;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Runtime search-index state for one recipe type. The type supplies the projected holder list;
 * this class owns only server-bound index caching, lazy rebuild, and revision invalidation.
 */
final class RecipeSearchState<R extends TopoRecipe> {

    private final Function<MinecraftServer, List<RecipeHolder<R>>> holderSource;
    private volatile @Nullable TopoRecipeSearchIndex<R> index;
    private final AtomicLong revision = new AtomicLong();

    RecipeSearchState(Function<MinecraftServer, List<RecipeHolder<R>>> holderSource) {
        this.holderSource = Objects.requireNonNull(holderSource, "recipe holder source");
    }

    @Nullable
    RecipeHolder<R> findRecipe(MachineBlockEntity machine) {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return null;
        }
        return index(level.getServer()).findRecipe(machine);
    }

    boolean resourceVersionSearchCacheable(MachineBlockEntity machine) {
        Level level = machine.getLevel();
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return false;
        }
        return index(level.getServer()).resourceVersionSearchCacheable();
    }

    long revision() {
        return revision.get();
    }

    synchronized void rebuild(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        index = TopoRecipeSearchIndex.build(
                holderSource.apply(server), System.identityHashCode(server));
        revision.incrementAndGet();
    }

    void invalidate() {
        index = null;
        revision.incrementAndGet();
    }

    private TopoRecipeSearchIndex<R> index(MinecraftServer server) {
        int serverToken = System.identityHashCode(server);
        TopoRecipeSearchIndex<R> current = index;
        if (current != null && current.serverToken() == serverToken) {
            return current;
        }
        synchronized (this) {
            current = index;
            if (current == null || current.serverToken() != serverToken) {
                rebuild(server);
                current = Objects.requireNonNull(index, "rebuilt recipe search index");
            }
            return current;
        }
    }
}
