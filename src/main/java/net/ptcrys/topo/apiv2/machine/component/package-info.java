/**
 * Machine composition spine: components mounted on a machine, their keys/mounts, and typed service
 * discovery.
 *
 * <h2>Vocabulary</h2>
 * <ul>
 * <li>{@link net.ptcrys.topo.apiv2.machine.component.MachineComponent} — runtime behaviour unit</li>
 * <li>{@link net.ptcrys.topo.apiv2.machine.component.ComponentKey} /
 * {@link net.ptcrys.topo.apiv2.machine.component.ComponentMount}
 * — identity + declaration-time factory</li>
 * <li>{@link net.ptcrys.topo.apiv2.machine.component.ServiceKey} — machine-local API discovery (not NeoForge,
 * not recipe lanes, not part roles)</li>
 * <li>{@link net.ptcrys.topo.apiv2.machine.component.RecipeModifier} /
 * {@link net.ptcrys.topo.apiv2.machine.component.RecipeCondition}
 * — recipe hooks exposed as services</li>
 * </ul>
 *
 * <p>
 * Builder entry: {@code Machines.Builder#component}. Runtime bag:
 * {@code MachineBlockEntity#machineComponents()} (not {@code components()}, which is BlockEntity data
 * components).
 */
@NullMarked
package net.ptcrys.topo.apiv2.machine.component;

import org.jspecify.annotations.NullMarked;
