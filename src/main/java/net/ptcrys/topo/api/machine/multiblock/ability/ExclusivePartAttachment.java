package net.ptcrys.topo.api.machine.multiblock.ability;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.machine.component.Attachment;
import net.ptcrys.topo.api.machine.component.AttachmentType;

/**
 * Declaration metadata marking a machine definition as an <b>exclusive part</b>.
 *
 * <p>
 * By default every multiblock cell and part is shareable: several formed machines may include the
 * same part (a shared input bus is read by every machine that declares it). An exclusive part is the
 * exception: once a formed machine holds it, another machine cannot use the same part until the
 * holder unforms.
 *
 * <p>
 * The marker is contributed by {@link ExclusivePartMount}, which is a sibling of the part mount and
 * can be combined with any part type. Member definitions expose it through
 * {@link net.ptcrys.topo.api.machine.MachineDefinition#metadata(AttachmentType)}.
 */
public final class ExclusivePartAttachment implements Attachment {

    public static final AttachmentType<ExclusivePartAttachment> TYPE = OfficialTopoAPIPlugin.INSTANCE
            .machine()
            .attachmentType("exclusive_part", ExclusivePartAttachment.class);

    public static final ExclusivePartAttachment INSTANCE = new ExclusivePartAttachment();

    private ExclusivePartAttachment() {}

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}

    @Override
    public AttachmentType<ExclusivePartAttachment> type() {
        return TYPE;
    }
}
