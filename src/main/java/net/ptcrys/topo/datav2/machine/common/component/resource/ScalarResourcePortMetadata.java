package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.apiv2.machine.component.AttachmentType;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;

import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/** Declaration metadata for one scalar buffer port: which scalar, how much, IO policy, optional filter. */
public record ScalarResourcePortMetadata(
                                         BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                         int capacity,
                                         PortAccess policy,
                                         @Nullable Predicate<Resource> resourceFilter)
        implements ResourcePortMetadata {

    public static final AttachmentType<ScalarResourcePortMetadata> TYPE = OfficialOIPlugin.INSTANCE.machine().attachmentType("scalar_resource_port", ScalarResourcePortMetadata.class);

    public ScalarResourcePortMetadata {
        Objects.requireNonNull(integration, "scalar resource integration");
        if (capacity <= 0) {
            throw new IllegalArgumentException("Scalar resource port capacity must be > 0 (was " + capacity + ")");
        }
        Objects.requireNonNull(policy, "resource port policy");
    }

    public ScalarResourcePortMetadata(
                                      BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                      int capacity,
                                      PortAccess policy) {
        this(integration, capacity, policy, null);
    }

    public ScalarResource resource() {
        return integration.recipeCapability().resource();
    }

    @Override
    public MachineResourceType<ScalarResource> resourceType() {
        return integration.resourceType();
    }

    @Override
    public boolean recipePoolIsolatable() {
        return false;
    }

    @Override
    public AttachmentType<ScalarResourcePortMetadata> type() {
        return TYPE;
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}
}
