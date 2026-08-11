package net.ptcrys.topo.client.machine.render;

import net.ptcrys.topo.api.machine.component.render.MachineRenderComponent;
import net.ptcrys.topo.api.machine.data.DataInt;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Client render unit for a machine, generically bound to the {@link MachineRenderComponent} it draws.
 *
 * <p>
 * A state is built from its trait and {@link #bind}s the synced data fields it reads; the framework
 * snapshots those fields on the main thread before {@link #submit}. Only
 * {@link net.ptcrys.topo.api.machine.data.DataField}s can be bound, and they must be client-synced, so a
 * render can never read un-synced live machine state — enforced structurally, with no string references.
 *
 * @param <T> the render trait this state is paired with
 */
public abstract class MachineRenderState<T extends MachineRenderComponent<?>> extends BlockEntityRenderState {

    private final T trait;
    private final List<Runnable> captures = new ArrayList<>();

    protected MachineRenderState(T trait) {
        this.trait = Objects.requireNonNull(trait, "trait");
    }

    /** The render trait this state draws, typed exactly. */
    protected final T trait() {
        return trait;
    }

    /** Bind a synced int field this render draws. The returned handle yields the last snapshotted value. */
    protected final IntValue bind(DataInt field) {
        Objects.requireNonNull(field, "field");
        if (!field.isClientSynced()) {
            throw new IllegalStateException("Render field must be client-synced before it can be drawn");
        }
        IntValue handle = new IntValue();
        captures.add(() -> handle.value = field.value());
        return handle;
    }

    final void capture() {
        for (Runnable capture : captures) {
            capture.run();
        }
    }

    /** Draw from the values snapshotted into the {@link #bind} handles. {@code lightCoords} is populated. */
    protected abstract void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState);

    /** Snapshot of a bound int field; {@link #get()} returns the value captured on the last extract. */
    public static final class IntValue {

        private int value;

        private IntValue() {}

        public int get() {
            return value;
        }
    }
}
