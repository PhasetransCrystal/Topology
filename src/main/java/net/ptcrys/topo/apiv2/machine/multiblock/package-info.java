/**
 * Multiblock runtime — the per-level services and the controller that drive forming (L3 + L4).
 *
 * <h2>Layer responsibility</h2>
 * Binds the pure {@link net.ptcrys.topo.apiv2.machine.multiblock.pattern recognition core} to the live world:
 * watches block changes, schedules rechecks, claims cells, and — once formed — aggregates member
 * traits into the controller's resource handlers. The controller is the only stateful orchestrator;
 * the engine stays world-free.
 *
 * <h2>Naming families</h2>
 * <ul>
 * <li>{@code Multiblock*} — per-level world plumbing and the state machine:
 * {@code MultiblockChangeWatcher} (sole owner of the {@code @SubscribeEvent} ingress),
 * {@code MultiblockLevelBinder} (pure per-level service holder),
 * {@code MultiblockClaimIndex} (cell ownership: claim/watch, check-then-commit, unclaim
 * returns freed cells to wake competing watchers), {@code MultiblockRecheckDirtySet} (the
 * per-level dirty set), {@code MultiblockController} (the state machine),
 * {@code MultiblockUi} (read-only diagnostics UI).
 * <li>{@code Formed*} — formed-state domain objects: {@code FormedRuntime} (member aggregation,
 * BE-free with server-thread re-resolution and member-epoch memoization).
 * </ul>
 *
 * <h2>Root entry for navigation</h2>
 * {@link net.ptcrys.topo.apiv2.machine.multiblock.MultiblockController}. Its {@code recheck} is a flat
 * five-step template — {@code snapshot} → {@code recognize} → {@code claim} → {@code apply} →
 * {@code publish} — wrapped fail-closed, so a broken recheck forces the unformed state and releases
 * claims rather than crashing the tick or leaving a stale {@code FORMED} block state. Correctness does
 * not depend on event coverage: a periodic per-controller backstop repairs any missed world change
 * (an intact formed structure pays only the single-capture {@code StructureEngine.verify} upkeep).
 *
 * <h2>Composition rule</h2>
 * Same-layer services never call each other: {@code MultiblockChangeWatcher} composes
 * {@code MultiblockRecheckDirtySet} with {@code MultiblockClaimIndex}, and the controller composes
 * claim release with watcher wake-ups — the dirty set holds no reference to the index. External
 * capability exposure is the only NeoForge {@code BlockCapability} surface; internal aggregation
 * goes through the member trait surface.
 */
package net.ptcrys.topo.apiv2.machine.multiblock;
