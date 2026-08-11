/**
 * Recognition core — the pure, world-free structure engine (multiblock L1 + L2).
 *
 * <h2>Layer responsibility</h2>
 * Turns an immutable structure snapshot plus a {@link net.ptcrys.topo.apiv2.machine.multiblock.pattern.Blueprint}
 * and an {@link net.ptcrys.topo.apiv2.machine.multiblock.pattern.Orientation} into a
 * {@link net.ptcrys.topo.apiv2.machine.multiblock.pattern.RecognitionResult}. Nothing here touches a
 * {@code Level}, a trait, or a block entity — it consumes values and returns values, so it is unit
 * testable and safe to run off the server thread later.
 *
 * <h2>Naming families</h2>
 * <ul>
 * <li>{@code Structure*} — the engine and its views: {@code StructureEngine} (the shared grid walk
 * behind {@code recognize}/{@code verify}), the read-only {@code StructureView}, and the dense
 * {@code CompiledSnapshot} capture form.
 * <li>{@code Recognition*} — the result side: {@code RecognitionResult} (typed structural outputs,
 * bounded missing-cell sample).
 * <li>{@code Cell} / {@code Blueprint} / {@code CompiledBlueprint} / {@code RepeatResolution} — the
 * declared pattern, its per-orientation grid compilation, and the repeat expansion.
 * <li>{@code Orientation} / {@code Orientations} — the 48-element octahedral group math (24 proper
 * rotations + optional mirror), interned and value-equal.
 * <li>{@code PropertyRule} / {@code PropertyRules} (api parameterized factories) — how individual
 * block-state properties co-vary (COVARIANT), stay fixed (FIXED), or are masked (DERIVED).
 * <li>{@code CellPredicate(s)} — what a cell accepts; plain values, never registry entries.
 * </ul>
 *
 * <h2>Root entry for navigation</h2>
 * {@link net.ptcrys.topo.apiv2.machine.multiblock.pattern.StructureEngine#recognize}. From there the call
 * stack is direct and strongly typed: {@code recognize} → the shared grid walk → each compiled
 * definition's {@code CellPredicate#test} and acceptable-state scan. Identity always flows through
 * strong handles ({@code PropertyRule}, {@code PartRole}, {@code Orientation}), never strings —
 * the only string-level contract is a blueprint's local ASCII symbol map, which
 * {@code Blueprint.build()} validates as closed.
 *
 * <p>
 * Concrete builtin instances (the named property-rule and capability handles, the blueprints) live
 * in {@code data}; this package holds only the abstractions and the engine.
 */
package net.ptcrys.topo.apiv2.machine.multiblock.pattern;
