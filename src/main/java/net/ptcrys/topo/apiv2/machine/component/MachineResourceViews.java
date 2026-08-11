package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePort;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** Scenario-oriented resource views for one mounted machine trait set. */
public final class MachineResourceViews {

    private final MachineComponents traits;
    private final TransferSide capabilitySide = new TransferSide();
    private final RecipeSide recipeSide = new RecipeSide();
    private final UiSide uiSide = new UiSide();
    private final RawSide rawSide = new RawSide();

    MachineResourceViews(MachineComponents traits) {
        this.traits = traits;
    }

    public TransferSide transferSide() {
        return capabilitySide;
    }

    public RecipeSide recipeSide() {
        return recipeSide;
    }

    public UiSide uiSide() {
        return uiSide;
    }

    public RawSide rawSide() {
        return rawSide;
    }

    public final class TransferSide {

        public <R extends Resource> @Nullable ResourceHandler<R> handler(
                                                                         MachineResourceType<R> resourceType,
                                                                         @Nullable Direction side) {
            return traits.transferHandler(resourceType, side);
        }
    }

    public final class RecipeSide {

        public <R extends Resource> @Nullable ResourceHandler<R> handler(
                                                                         MachineResourceType<R> resourceType,
                                                                         RecipeRole recipeIo) {
            return traits.recipeResourceHandler(resourceType, recipeIo);
        }
    }

    public final class UiSide {

        public <R extends Resource> List<ResourcePort<?, R>> ports(
                                                                   MachineResourceType<R> resourceType,
                                                                   RecipeRole recipeIo) {
            return traits.visibleResourcePorts(resourceType, recipeIo);
        }
    }

    public final class RawSide {

        public <R extends Resource> List<ResourcePort<?, R>> ports(MachineResourceType<R> resourceType) {
            return traits.resourcePorts(resourceType);
        }
    }
}
