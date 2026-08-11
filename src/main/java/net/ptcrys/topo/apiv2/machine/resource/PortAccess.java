package net.ptcrys.topo.apiv2.machine.resource;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declaration-time access policy for one resource storage port.
 *
 * <h2>Capability side frame</h2>
 *
 * <p>
 * Declared capability sides are <b>block-facing-relative</b>: they name faces of the block in
 * its canonical (unrotated) frame, where the block front is {@code NORTH}. At query time the
 * world-absolute side handed to the capability lookup is folded back into that local frame using
 * the block's {@code FACING} value before the membership check — one precomputed table lookup,
 * zero allocation. Horizontal facings are pure yaw rotations ({@code UP}/{@code DOWN} are
 * unaffected); vertical facings pitch about the east-west axis holding {@code EAST} fixed.
 * Blocks without a {@code FACING} property pass {@code null} facing and degrade to world-absolute
 * side interpretation.
 */
public final class PortAccess {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final AutomationIo[] IO_MODES = AutomationIo.values();
    /** {@code LOCAL_FROM_WORLD[facing.ordinal()][worldSide.ordinal()]} = side in the block-local frame. */
    private static final Direction[][] LOCAL_FROM_WORLD = buildLocalFromWorld();
    private static final int SIDE_MODE_MASK = 0b11;

    private final RecipeRole recipeIo;
    private final AutomationIo automationIo;
    private final PlayerAccess playerSlotAccess;
    private final @Nullable Set<Direction> transferSides;
    private final boolean playerConfigurableSides;
    private final @Nullable AutomationIo defaultAutomationIoOverride;
    private final @Nullable List<AutomationIo> allowedRuntimeModesOverride;
    private final int defaultPackedSideIo;
    private final List<AutomationIo> allowedRuntimeModes;

    private PortAccess(
                       RecipeRole recipeIo,
                       AutomationIo automationIo,
                       PlayerAccess playerSlotAccess,
                       @Nullable Set<Direction> transferSides,
                       boolean playerConfigurableSides,
                       @Nullable AutomationIo defaultAutomationIoOverride,
                       @Nullable List<AutomationIo> allowedRuntimeModesOverride) {
        this.recipeIo = Objects.requireNonNull(recipeIo, "recipe IO");
        this.automationIo = Objects.requireNonNull(automationIo, "capability IO");
        this.playerSlotAccess = Objects.requireNonNull(playerSlotAccess, "player slot access");
        this.transferSides = transferSides == null ? null : Set.copyOf(transferSides);
        this.playerConfigurableSides = playerConfigurableSides;
        if (defaultAutomationIoOverride != null && !this.automationIo.allows(defaultAutomationIoOverride)) {
            throw new IllegalArgumentException("Default side IO " + defaultAutomationIoOverride + " falls outside the declared capability envelope " + this.automationIo);
        }
        this.defaultAutomationIoOverride = defaultAutomationIoOverride;
        this.allowedRuntimeModesOverride = validateAllowedRuntimeModes(automationIo, allowedRuntimeModesOverride);
        this.defaultPackedSideIo = computeDefaultPackedSideIo(
                defaultAutomationIoOverride == null ? this.automationIo : defaultAutomationIoOverride, this.transferSides);
        this.allowedRuntimeModes = this.allowedRuntimeModesOverride == null ? computeAllowedRuntimeModes(automationIo) : this.allowedRuntimeModesOverride;
        AutomationIo defaultMode = defaultAutomationIoOverride == null ? this.automationIo : defaultAutomationIoOverride;
        if (!this.allowedRuntimeModes.contains(defaultMode)) {
            throw new IllegalArgumentException("Default side IO " + defaultMode + " must be present in the allowed runtime modes " + this.allowedRuntimeModes);
        }
    }

    public RecipeRole recipeIo() {
        return recipeIo;
    }

    public AutomationIo automationIo() {
        return automationIo;
    }

    public PlayerAccess playerSlotAccess() {
        return playerSlotAccess;
    }

    /** Whether the player may retarget this port's capability sides at runtime (GUI side panel). */
    public boolean playerConfigurableSides() {
        return playerConfigurableSides;
    }

    /**
     * The 12-bit packed default of the per-side capability IO: 2 bits per local side at
     * {@code side.ordinal() * 2}, value {@link AutomationIo#ordinal()}. Declared sides carry
     * {@link #automationIo()} — or the {@link #withDefaultAutomationIo} override — all other sides
     * {@link AutomationIo#NONE}.
     */
    public int defaultPackedSideIo() {
        return defaultPackedSideIo;
    }

    /**
     * The player cycle list. By default this is every mode inside the declared
     * {@link #automationIo()} envelope ({@link AutomationIo#allows(AutomationIo)}), in enum
     * declaration order starting at {@code NONE}. Policies may narrow it with
     * {@link #withAllowedRuntimeModes}; a single-entry list means the port is not cyclable.
     */
    public List<AutomationIo> allowedRuntimeModes() {
        return allowedRuntimeModes;
    }

    /**
     * Clamps a persisted packed side-IO value back into the allowed runtime modes: any side whose
     * mode is not currently player-selectable falls back to that side's default. Guards stale saves
     * against definition changes.
     */
    public int sanitizePackedSideIo(int packed) {
        int sanitized = packed;
        for (Direction side : DIRECTIONS) {
            AutomationIo mode = sideModeOf(sanitized, side);
            if (!allowedRuntimeModes.contains(mode)) {
                sanitized = withSideMode(sanitized, side, sideModeOf(defaultPackedSideIo, side));
            }
        }
        return sanitized;
    }

    public boolean allowsRuntimeMode(AutomationIo mode) {
        return allowedRuntimeModes.contains(Objects.requireNonNull(mode, "capability IO mode"));
    }

    /** Reads one local side's mode out of a packed side-IO value. */
    public static AutomationIo sideModeOf(int packed, Direction localSide) {
        return IO_MODES[(packed >>> (localSide.ordinal() * 2)) & SIDE_MODE_MASK];
    }

    /** Returns {@code packed} with one local side's mode replaced. */
    public static int withSideMode(int packed, Direction localSide, AutomationIo mode) {
        int shift = localSide.ordinal() * 2;
        return (packed & ~(SIDE_MODE_MASK << shift)) | (mode.ordinal() << shift);
    }

    /**
     * Folds a world-absolute side into the block-local frame for {@code facing} (one table lookup);
     * {@code null} facing degrades to world-absolute interpretation.
     */
    public static Direction localFromWorld(Direction worldSide, @Nullable Direction facing) {
        return facing == null ? worldSide : LOCAL_FROM_WORLD[facing.ordinal()][worldSide.ordinal()];
    }

    /**
     * Whether automation may attach on {@code side} (world-absolute) for a block whose current
     * {@code FACING} is {@code facing}. Declared sides are block-facing-relative (see class
     * javadoc); a {@code null} facing degrades to world-absolute interpretation.
     */
    public boolean allowsTransferSide(@Nullable Direction side, @Nullable Direction facing) {
        if (transferSides == null) {
            return true;
        }
        if (side == null) {
            return false;
        }
        Direction localSide = facing == null ? side : LOCAL_FROM_WORLD[facing.ordinal()][side.ordinal()];
        return transferSides.contains(localSide);
    }

    public static PortAccess input() {
        return new PortAccess(
                RecipeRole.INPUT, AutomationIo.INSERT, PlayerAccess.FREE, null, false, null, null);
    }

    public static PortAccess input(Direction firstSide, Direction... moreSides) {
        return input().withTransferSides(firstSide, moreSides);
    }

    public static PortAccess output() {
        return new PortAccess(
                RecipeRole.OUTPUT, AutomationIo.EXTRACT, PlayerAccess.EXTRACT_ONLY, null, false, null, null);
    }

    public static PortAccess output(Direction firstSide, Direction... moreSides) {
        return output().withTransferSides(firstSide, moreSides);
    }

    public static PortAccess storage() {
        return new PortAccess(
                RecipeRole.BOTH, AutomationIo.BOTH, PlayerAccess.FREE, null, false, null, null);
    }

    public static PortAccess storage(Direction firstSide, Direction... moreSides) {
        return storage().withTransferSides(firstSide, moreSides);
    }

    /**
     * Recipe input that is closed to automation by default on every side; the player may reconfigure
     * sides at runtime. Typical for catalyst / tool / template slots that must not be pipe-fed.
     *
     * @see #dieSlot() 模具槽专用：可配输入/输出/双向，默认全关
     */
    public static PortAccess manualRecipeInput() {
        return dieSlot();
    }

    /**
     * 多方块输入舱侧向 IO 策略（物品/流体/能量输入仓）：
     * <ul>
     * <li>配方角色 {@link RecipeRole#INPUT}</li>
     * <li>自动化包络 {@link AutomationIo#BOTH}：玩家可把任一面配成输入 / 输出 / 禁止</li>
     * <li>默认仅正面（本地面 {@code NORTH}）{@link AutomationIo#INSERT}，其余面禁止</li>
     * <li>循环序：禁止 → 输入 → 输出</li>
     * </ul>
     */
    public static PortAccess inputHatch() {
        return input(Direction.NORTH)
                .withAutomationIo(AutomationIo.BOTH)
                .withDefaultAutomationIo(AutomationIo.INSERT)
                .withAllowedRuntimeModes(
                        AutomationIo.NONE,
                        AutomationIo.INSERT,
                        AutomationIo.EXTRACT)
                .withPlayerConfigurableSides();
    }

    /**
     * 模具 / 催化剂槽访问策略：
     * <ul>
     * <li>配方角色仅 {@link RecipeRole#INPUT}（在场匹配、不消耗）</li>
     * <li>自动化包络 {@link AutomationIo#BOTH}：玩家可把任一面配成输入 / 输出 / 双向</li>
     * <li>默认六面 {@link AutomationIo#NONE}（全关，管道碰不到）</li>
     * <li>循环序：关 → 输入 → 输出 → 输入+输出</li>
     * </ul>
     */
    public static PortAccess dieSlot() {
        return storage()
                .onAllSides()
                .withRecipeIo(RecipeRole.INPUT)
                .withAutomationIo(AutomationIo.BOTH)
                .withDefaultAutomationIo(AutomationIo.NONE)
                .withAllowedRuntimeModes(
                        AutomationIo.NONE,
                        AutomationIo.INSERT,
                        AutomationIo.EXTRACT,
                        AutomationIo.BOTH)
                .withPlayerConfigurableSides();
    }

    public PortAccess withRecipeIo(RecipeRole recipeIo) {
        return new PortAccess(
                recipeIo,
                automationIo,
                playerSlotAccess,
                transferSides,
                playerConfigurableSides,
                defaultAutomationIoOverride,
                allowedRuntimeModesOverride);
    }

    public PortAccess withAutomationIo(AutomationIo automationIo) {
        return new PortAccess(
                recipeIo,
                automationIo,
                playerSlotAccess,
                transferSides,
                playerConfigurableSides,
                defaultAutomationIoOverride,
                allowedRuntimeModesOverride);
    }

    public PortAccess withPlayerSlotAccess(PlayerAccess playerSlotAccess) {
        return new PortAccess(
                recipeIo,
                automationIo,
                playerSlotAccess,
                transferSides,
                playerConfigurableSides,
                defaultAutomationIoOverride,
                allowedRuntimeModesOverride);
    }

    /** Opts the port into runtime player-configurable capability sides (see class javadoc). */
    public PortAccess withPlayerConfigurableSides() {
        return new PortAccess(
                recipeIo,
                automationIo,
                playerSlotAccess,
                transferSides,
                true,
                defaultAutomationIoOverride,
                allowedRuntimeModesOverride);
    }

    public PortAccess withTransferSides(Direction firstSide, Direction... moreSides) {
        return new PortAccess(
                recipeIo,
                automationIo,
                playerSlotAccess,
                sideSet(firstSide, moreSides),
                playerConfigurableSides,
                defaultAutomationIoOverride,
                allowedRuntimeModesOverride);
    }

    /**
     * Declares every local side as a capability side. Unlike the unrestricted default ({@code null}
     * side set, which also accepts side-less capability queries), an explicit all-sides declaration
     * rejects a {@code null} query side.
     */
    public PortAccess onAllSides() {
        return withTransferSides(
                Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    }

    /**
     * Overrides the per-side default carried by declared sides in {@link #defaultPackedSideIo()}
     * (without an override they default to the full {@link #automationIo()} envelope). The override
     * must sit inside the declared envelope; {@link AutomationIo#NONE} yields a closed-by-default
     * port the player opens at runtime — pair it with {@link #withPlayerConfigurableSides}.
     */
    public PortAccess withDefaultAutomationIo(AutomationIo defaultAutomationIo) {
        return new PortAccess(
                recipeIo,
                automationIo,
                playerSlotAccess,
                transferSides,
                playerConfigurableSides,
                Objects.requireNonNull(defaultAutomationIo, "default side IO"),
                allowedRuntimeModesOverride);
    }

    /**
     * Narrows the player cycle list while leaving the declaration envelope intact. Every listed
     * mode must still sit inside {@link #automationIo()}. Without this override the cycle list is
     * already the full declared envelope, so listing every mode it allows is redundant.
     */
    public PortAccess withAllowedRuntimeModes(AutomationIo firstMode, AutomationIo... moreModes) {
        return new PortAccess(
                recipeIo,
                automationIo,
                playerSlotAccess,
                transferSides,
                playerConfigurableSides,
                defaultAutomationIoOverride,
                runtimeModeList(firstMode, moreModes));
    }

    private static int computeDefaultPackedSideIo(
                                                  AutomationIo automationIo,
                                                  @Nullable Set<Direction> transferSides) {
        int packed = 0;
        for (Direction side : DIRECTIONS) {
            if (transferSides == null || transferSides.contains(side)) {
                packed = withSideMode(packed, side, automationIo);
            }
        }
        return packed;
    }

    private static List<AutomationIo> computeAllowedRuntimeModes(AutomationIo automationIo) {
        List<AutomationIo> modes = new ArrayList<>(IO_MODES.length);
        for (AutomationIo mode : IO_MODES) {
            if (automationIo.allows(mode)) {
                modes.add(mode);
            }
        }
        return List.copyOf(modes);
    }

    private static @Nullable List<AutomationIo> validateAllowedRuntimeModes(
                                                                            AutomationIo automationIo,
                                                                            @Nullable List<AutomationIo> allowedRuntimeModesOverride) {
        if (allowedRuntimeModesOverride == null) {
            return null;
        }
        if (allowedRuntimeModesOverride.isEmpty()) {
            throw new IllegalArgumentException("Allowed runtime modes must not be empty");
        }
        List<AutomationIo> modes = new ArrayList<>(allowedRuntimeModesOverride.size());
        for (AutomationIo mode : allowedRuntimeModesOverride) {
            Objects.requireNonNull(mode, "allowed runtime mode");
            if (!automationIo.allows(mode)) {
                throw new IllegalArgumentException("Allowed runtime mode " + mode + " falls outside the declared capability envelope " + automationIo);
            }
            if (!modes.contains(mode)) {
                modes.add(mode);
            }
        }
        return List.copyOf(modes);
    }

    private static Set<Direction> sideSet(Direction firstSide, Direction... moreSides) {
        EnumSet<Direction> sides = EnumSet.of(Objects.requireNonNull(firstSide, "capability side"));
        for (Direction side : Objects.requireNonNull(moreSides, "more capability sides")) {
            sides.add(Objects.requireNonNull(side, "capability side"));
        }
        return sides;
    }

    private static List<AutomationIo> runtimeModeList(AutomationIo firstMode, AutomationIo... moreModes) {
        List<AutomationIo> modes = new ArrayList<>(
                1 + Objects.requireNonNull(moreModes, "more runtime modes").length);
        modes.add(Objects.requireNonNull(firstMode, "allowed runtime mode"));
        for (AutomationIo mode : moreModes) {
            modes.add(Objects.requireNonNull(mode, "allowed runtime mode"));
        }
        return List.copyOf(modes);
    }

    private static Direction[][] buildLocalFromWorld() {
        Direction[][] table = new Direction[DIRECTIONS.length][DIRECTIONS.length];
        for (Direction facing : DIRECTIONS) {
            for (Direction localSide : DIRECTIONS) {
                Direction worldSide = worldFromLocal(localSide, facing);
                table[facing.ordinal()][worldSide.ordinal()] = localSide;
            }
        }
        return table;
    }

    /**
     * The world-absolute face the local-frame face {@code localSide} ends up on after rotating the
     * canonical front ({@code NORTH}) onto {@code facing}. Horizontal facings are pure yaw;
     * {@code UP}/{@code DOWN} pitch about the east-west axis holding {@code EAST} fixed.
     */
    private static Direction worldFromLocal(Direction localSide, Direction facing) {
        return switch (facing) {
            case NORTH -> localSide;
            case SOUTH -> localSide.getAxis() == Direction.Axis.Y ? localSide : localSide.getOpposite();
            case EAST -> localSide.getAxis() == Direction.Axis.Y ? localSide : localSide.getClockWise();
            case WEST -> localSide.getAxis() == Direction.Axis.Y ? localSide : localSide.getCounterClockWise();
            case UP -> switch (localSide) {
                case NORTH -> Direction.UP;
                case UP -> Direction.SOUTH;
                case SOUTH -> Direction.DOWN;
                case DOWN -> Direction.NORTH;
                default -> localSide;
            };
            case DOWN -> switch (localSide) {
                case NORTH -> Direction.DOWN;
                case DOWN -> Direction.SOUTH;
                case SOUTH -> Direction.UP;
                case UP -> Direction.NORTH;
                default -> localSide;
            };
        };
    }
}
