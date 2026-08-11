/**
 * Part-capability ability system — the structural tokens a multiblock counts and discovers.
 *
 * <h2>Layer responsibility</h2>
 * Defines what makes a block a structurally significant part (an input bus, an output hatch, &hellip;)
 * independent of the trait that backs it. A {@link net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRole}
 * is a flat value — id + display name + resource type + recipe-IO role — minted only by its registry;
 * a blueprint requires capabilities by count, and recognition tallies them per cell.
 *
 * <h2>Naming families &amp; surface</h2>
 * <ul>
 * <li>{@code PartRole} / {@code PartRoleRegistry} — the KHS token and its freezable
 * owner (only {@code register} / {@code require} / {@code freeze}); concrete handles live in
 * {@code data} ({@code BuiltinOIPartRoles}).
 * <li>{@code PartRoleAttachment} — the trait-mount metadata attaching one capability to a port.
 * Its canonical constructor is package-private, so a capability can only be attached through the
 * single validated path below.
 * <li>{@code PartRoleMount} — the data-side mount builder returned by the storage traits; its
 * {@code cap(...)} validates that the mount carries a matching resource port before stapling the
 * metadata, and it implements {@code ComponentContribution} so the machine builder accepts it
 * without this package leaking back into the machine core.
 * </ul>
 *
 * <h2>Root entry for navigation</h2>
 * {@link net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleMount#role} — the one place a part
 * capability is attached and validated. Recognition reads capabilities through the snapshot's
 * precomputed per-cell sets, never by string.
 */
package net.ptcrys.topo.apiv2.machine.multiblock.ability;
