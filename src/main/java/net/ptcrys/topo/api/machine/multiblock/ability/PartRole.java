package net.ptcrys.topo.api.machine.multiblock.ability;

import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeRole;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Strong structural ability handle carried by a multiblock part.
 *
 * <p>
 * A part capability is the count key the structure engine tallies during recognition and the
 * structural tag a {@code CellPredicate} matches against; it never participates in string matching.
 * It is a flat value (no per-resource subclass) whose backend expectation is carried by the
 * {@link #resourceType()} family and {@link #recipeIo()} role.
 *
 * <p>
 * Identity is the registered {@link #id()}: a part capability minted under a given id is the one
 * canonical instance, so {@link #equals(Object)}/{@link #hashCode()} compare by id alone. Instances
 * are created only by {@link PartRoleRegistry} via the package-private constructor.
 */
public final class PartRole {

    private final Identifier id;
    private final Component displayName;
    private final MachineResourceType<?> resourceType;
    private final RecipeRole recipeIo;

    PartRole(
             Identifier id, Component displayName, MachineResourceType<?> resourceType, RecipeRole recipeIo) {
        this.id = Objects.requireNonNull(id, "part capability id");
        this.displayName = Objects.requireNonNull(displayName, "part capability display name");
        this.resourceType = Objects.requireNonNull(resourceType, "part capability resource type");
        this.recipeIo = Objects.requireNonNull(recipeIo, "part capability recipe IO mode");
    }

    public Identifier id() {
        return id;
    }

    /** Player-facing name, provided at registration — UI never derives text from {@link #id()}. */
    public Component displayName() {
        return displayName;
    }

    public MachineResourceType<?> resourceType() {
        return resourceType;
    }

    public RecipeRole recipeIo() {
        return recipeIo;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof PartRole other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PartRole[" + id + "]";
    }
}
