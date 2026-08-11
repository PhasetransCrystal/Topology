package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.component.AttachmentType;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public record FluidResourcePortMetadata(
                                        int tanks,
                                        int capacity,
                                        PortAccess policy,
                                        @Nullable Predicate<Resource> resourceFilter)
        implements ResourcePortMetadata {

    public static final AttachmentType<FluidResourcePortMetadata> TYPE = OfficialTopoPlugin.INSTANCE.machine().attachmentType("fluid_resource_port", FluidResourcePortMetadata.class);

    public FluidResourcePortMetadata {
        if (tanks <= 0) {
            throw new IllegalArgumentException("Fluid resource port tanks must be > 0 (was " + tanks + ")");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Fluid resource port capacity must be > 0 (was " + capacity + ")");
        }
        Objects.requireNonNull(policy, "resource port policy");
    }

    public FluidResourcePortMetadata(int tanks, int capacity, PortAccess policy) {
        this(tanks, capacity, policy, null);
    }

    @Override
    public MachineResourceType<FluidResource> resourceType() {
        return BuiltinTopoResourceIntegrations.FLUID.resourceType();
    }

    @Override
    public AttachmentType<FluidResourcePortMetadata> type() {
        return TYPE;
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}
}
