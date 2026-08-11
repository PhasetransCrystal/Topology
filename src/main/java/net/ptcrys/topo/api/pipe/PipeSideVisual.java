package net.ptcrys.topo.api.pipe;

import net.minecraft.util.StringRepresentable;

/**
 * Render-facing tri-state for one pipe side, stored as a blockstate property value.
 *
 * <p>
 * This is derived data: the server recomputes it from {@link PipeSideIntent} and neighbor
 * reality whenever placement, neighbor changes or wrench operations happen. It only drives the
 * baked multipart model and the voxel shape; gameplay truth lives in the pipe saved data.
 */
public enum PipeSideVisual implements StringRepresentable {

    NONE("none"),
    PIPE("pipe"),
    EXTRACT("extract");

    private final String serializedName;

    PipeSideVisual(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public boolean connected() {
        return this != NONE;
    }
}
