package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.component.AttachmentType;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public record ItemResourcePortMetadata(
                                       int slots,
                                       PortAccess policy,
                                       @Nullable Predicate<Resource> resourceFilter)
        implements ResourcePortMetadata {

    public static final AttachmentType<ItemResourcePortMetadata> TYPE = OfficialTopoPlugin.INSTANCE.machine().attachmentType("item_resource_port", ItemResourcePortMetadata.class);

    public ItemResourcePortMetadata {
        if (slots <= 0) {
            throw new IllegalArgumentException("Item resource port slots must be > 0 (was " + slots + ")");
        }
        Objects.requireNonNull(policy, "resource port policy");
    }

    public ItemResourcePortMetadata(int slots, PortAccess policy) {
        this(slots, policy, null);
    }

    @Override
    public MachineResourceType<ItemResource> resourceType() {
        return BuiltinTopoResourceIntegrations.ITEM.resourceType();
    }

    @Override
    public AttachmentType<ItemResourcePortMetadata> type() {
        return TYPE;
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}
}
