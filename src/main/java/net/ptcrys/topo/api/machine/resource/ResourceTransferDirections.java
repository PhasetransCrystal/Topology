package net.ptcrys.topo.api.machine.resource;

/**
 * Internal direction metadata for composed resource views. It prevents a combined handler from
 * probing a known-disallowed operation and accidentally poisoning the surrounding transaction.
 */
interface ResourceTransferDirections {

    boolean allowsInsert();

    boolean allowsExtract();
}
