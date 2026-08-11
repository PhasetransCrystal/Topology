/**
 * Multiblock UI layer — the read-only player-facing surfaces over the recognition core.
 *
 * <h2>Layer responsibility</h2>
 * Turns blueprints and client-side diagnosis into screens: the controller's structure page (3D
 * scene, problem list, comparison table), the shared {@code BlueprintPreview} ideal-build model
 * (JEI category and structure page render the same example structure), and the typed
 * {@code PropertyDisplay} table that translates pinned block-state aspects into player-readable
 * expected-vs-current rows. Everything here is display-only: no claims, no rechecks, no writes.
 *
 * <h2>Naming families &amp; surface</h2>
 * <ul>
 * <li>{@code PropertyDisplay} / {@code PropertyDisplayRegistry} — the KHS display table (typed
 * {@code registerEnum}/{@code registerBoolean} doors, freeze-disciplined); the
 * {@code PropertyDisplays.compare} driver walks the frozen view. Builtin handles live in
 * {@code data} ({@code BuiltinOIPropertyDisplays}).
 * <li>{@code BlueprintPreview} — the ideal ("required") preview model: representative states for
 * the 3D scene plus per-cell candidate listings for the BOM dock.
 * <li>{@code Multiblock*} (Kotlin) — the page and scene widgets: {@code MultiblockStructurePage},
 * {@code MultiblockScenePanel}/{@code MultiblockScenePreview}, {@code MultiblockPreviewBlockList}.
 * </ul>
 *
 * <h2>Root entry for navigation</h2>
 * {@code MultiblockUi} (package above) mounts the structure page;
 * {@link net.ptcrys.topo.apiv2.machine.multiblock.ui.PropertyDisplays#compare} is the typed text root.
 */
package net.ptcrys.topo.apiv2.machine.multiblock.ui;
