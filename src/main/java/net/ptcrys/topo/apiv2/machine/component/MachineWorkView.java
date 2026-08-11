package net.ptcrys.topo.apiv2.machine.component;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

import org.jspecify.annotations.Nullable;

/** Semantic runtime view for traits that can make a machine visually work. */
public interface MachineWorkView {

    ServiceKey<MachineWorkView, Void> KEY = ServiceKey.oi("machine_work", MachineWorkView.class, Void.class);

    boolean isRunning();

    /**
     * Whether this view calls {@code MachineBlockEntity.requestActiveBlockStateRefresh()} whenever
     * {@link #isRunning()} changes. The default preserves compatibility by keeping framework
     * polling enabled for existing third-party views.
     */
    default boolean publishesRunningStateEdges() {
        return false;
    }

    boolean isBusy();

    int progress();

    int maxProgress();

    float progressPercent();

    @Nullable
    ResourceKey<Recipe<?>> activeRecipeId();
}
