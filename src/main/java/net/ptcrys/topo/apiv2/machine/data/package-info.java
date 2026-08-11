/**
 * Machine data domain: typed fields, persist, client sync, and lifecycle gates.
 *
 * <p>
 * <b>Trait authors:</b> declare fields in the trait constructor via {@code data().scope(...)}
 * builders; use typed accessors at runtime. Do not call {@code lifecycle*} methods.
 *
 * <p>
 * <b>Naming on {@link net.ptcrys.topo.apiv2.machine.data.MachineDataScope}:</b>
 * <ul>
 * <li>{@code lifecycle*} — framework templates ({@link net.ptcrys.topo.apiv2.machine.MachineBlockEntity} only)</li>
 * <li>{@code is*} / {@code require*} — queries and fail-fast guards</li>
 * <li>{@code scope} / {@code enable*Sync} / {@code request*} / {@code notify*} — schema and dirty bookkeeping</li>
 * </ul>
 */
package net.ptcrys.topo.apiv2.machine.data;
