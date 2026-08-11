package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.apiv2.machine.component.AttachmentType;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

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

    public static final AttachmentType<ItemResourcePortMetadata> TYPE = OfficialOIPlugin.INSTANCE.machine().attachmentType("item_resource_port", ItemResourcePortMetadata.class);

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
        return BuiltinOIResourceIntegrations.ITEM.resourceType();
    }

    @Override
    public AttachmentType<ItemResourcePortMetadata> type() {
        return TYPE;
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}
}
