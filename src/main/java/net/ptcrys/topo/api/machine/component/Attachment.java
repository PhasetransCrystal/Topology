package net.ptcrys.topo.api.machine.component;

/** Read-only declaration metadata attached to a {@link ComponentMount}. */
public interface Attachment {

    AttachmentType<? extends Attachment> type();

    /**
     * Structural invariant against the fully assembled host mount, invoked for every metadata entry
     * whenever a {@link ComponentMount} is constructed — the initial mount and every
     * {@link ComponentMount#withMetadata} re-attach alike. A metadata value harvested from one mount can
     * therefore never be re-stapled onto a host that violates its invariant: the forged combination
     * fails at the attach expression, before any registry or definition sees it. Default: no
     * constraint.
     */
    default void validateHost(ComponentMount<?> host) {}
}
