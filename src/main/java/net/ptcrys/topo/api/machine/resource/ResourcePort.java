package net.ptcrys.topo.api.machine.resource;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.data.DataInt;
import net.ptcrys.topo.api.machine.data.DataManualDirtyField;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.machine.ui.MachineUiContribution;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.StacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public abstract class ResourcePort<S, R extends Resource> extends MachineComponent {

    private final StacksResourceHandler<S, R> handler;
    private final ResourcePortMetadata metadata;
    private final Runnable markPersistedContentsDirty;
    /** Runtime per-side capability IO (12-bit packed); only allocated for player-configurable ports. */
    private final @Nullable DataInt sideIo;
    /**
     * When true this port's handlers join the machine-level {@link RecipeSearchPoolSettings} pool
     * for input and output (items/fluids on the same block share one id). Scalars stay global.
     */
    private final boolean poolScoped;
    private @Nullable ResourceHandler<R> insertView;
    private @Nullable ResourceHandler<R> extractView;

    protected ResourcePort(
                           ComponentContext<? extends ResourcePort<S, R>> context,
                           MachineResourceType<R> resourceType,
                           StacksResourceHandler<S, R> handler,
                           ResourcePortMetadata metadata) {
        super(context);
        this.handler = Objects.requireNonNull(handler, "handler");
        this.metadata = validateMetadata(resourceType, metadata);
        DataManualDirtyField contents = resourceType
                .dataField(
                        data(),
                        "contents",
                        handler,
                        () -> machine().machineComponents().noteResourceContentChanged())
                .persisted()
                .done();
        this.markPersistedContentsDirty = contents::markDirty;
        PortAccess policy = metadata.policy();
        this.sideIo = policy.playerConfigurableSides() ? data().intField("side_io", policy.defaultPackedSideIo())
                .persisted()
                .syncNone()
                .done() : null;
        // Isolatable item/fluid (and similar) ports join the machine-level pool for INPUT and
        // OUTPUT so a recipe that hits pool P also emits into P. Scalars stay global.
        RecipeRole role = policy.recipeIo();
        this.poolScoped = metadata.recipePoolIsolatable() && (role.acceptsInput() || role.acceptsOutput());
    }

    /**
     * @deprecated Persistence cadence is no longer a field policy. The parameter is retained for
     *             source compatibility; persisted mutations enter one coalesced machine snapshot epoch.
     */
    @Deprecated(forRemoval = false)
    protected ResourcePort(
                           ComponentContext<? extends ResourcePort<S, R>> context,
                           MachineResourceType<R> resourceType,
                           StacksResourceHandler<S, R> handler,
                           ResourcePortMetadata metadata,
                           int contentsPersistIntervalTicks) {
        this(context, resourceType, handler, metadata);
    }

    @Override
    public RecipeSearchPoolId recipeSearchPoolId() {
        if (!poolScoped) {
            return RecipeSearchPoolId.DEFAULT;
        }
        return resolveMachinePoolId();
    }

    @Override
    public boolean recipePoolScoped() {
        // UNIVERSAL = join every concrete pool (unscoped contribution).
        return poolScoped && !recipeSearchPoolId().isUniversal();
    }

    /** Shared machine-level pool id (same for all isolatable ports on this block entity). */
    private RecipeSearchPoolId resolveMachinePoolId() {
        RecipeSearchPoolId fallback = metadata.recipeIo().acceptsInput() ? RecipeSearchPoolId.DEFAULT : RecipeSearchPoolId.UNIVERSAL;
        return RecipeSearchPoolSettings.resolve(
                machine().machineComponents(), fallback);
    }

    @SuppressWarnings("unchecked")
    public final MachineResourceType<R> resourceType() {
        return (MachineResourceType<R>) metadata.resourceType();
    }

    public final StacksResourceHandler<S, R> handler() {
        return handler;
    }

    public final ResourcePortMetadata metadata() {
        return metadata;
    }

    public final RecipeRole recipeIo() {
        return metadata.recipeIo();
    }

    public final AutomationIo automationIo() {
        return metadata.automationIo();
    }

    public final PlayerAccess playerSlotAccess() {
        return metadata.playerSlotAccess();
    }

    public final int resourceIndexCount() {
        return handler.size();
    }

    @Override
    protected final <Q extends Resource> @Nullable ResourceHandler<Q> transferHandler(
                                                                                      MachineResourceType<Q> requestedType,
                                                                                      @Nullable Direction side) {
        if (requestedType != resourceType()) {
            return null;
        }
        AutomationIo mode = effectiveTransferSideIo(side);
        if (mode == AutomationIo.NONE) {
            return null;
        }
        if (mode == AutomationIo.BOTH) {
            return requestedType.castHandler(handler);
        }
        return requestedType.castHandler(directionalView(mode));
    }

    /**
     * The capability IO this port answers on {@code side} right now. Configurable ports read the
     * runtime packed value (world side folded into the block-local frame); the {@code null} side and
     * every non-configurable port keep the declaration-time policy answer bit for bit.
     */
    private AutomationIo effectiveTransferSideIo(@Nullable Direction side) {
        DataInt runtime = sideIo;
        if (runtime != null && side != null) {
            Direction localSide = PortAccess.localFromWorld(side, blockFacing());
            return PortAccess.sideModeOf(runtime.value(), localSide);
        }
        AutomationIo declared = metadata.automationIo();
        if (declared == AutomationIo.NONE || !metadata.allowsTransferSide(side, blockFacing())) {
            return AutomationIo.NONE;
        }
        return declared;
    }

    /**
     * One lazily-created insert-only and one extract-only view shared by every side showing that
     * mode; the wrappers gate by mode only, so a runtime side change never stales them.
     */
    private ResourceHandler<R> directionalView(AutomationIo mode) {
        if (mode == AutomationIo.INSERT) {
            ResourceHandler<R> view = insertView;
            if (view == null) {
                view = new DirectionalResourceHandler<>(handler, mode);
                insertView = view;
            }
            return view;
        }
        ResourceHandler<R> view = extractView;
        if (view == null) {
            view = new DirectionalResourceHandler<>(handler, mode);
            extractView = view;
        }
        return view;
    }

    @Override
    protected final <Q extends Resource> @Nullable ResourceHandler<Q> recipeResourceHandler(
                                                                                            MachineResourceType<Q> requestedType,
                                                                                            RecipeRole requestedIo) {
        RecipeRole recipeIo = metadata.recipeIo();
        if (requestedType != resourceType() || requestedIo == RecipeRole.NONE || !recipeIo.allows(requestedIo)) {
            return null;
        }
        if (poolScoped && requestedIo.acceptsInput() && RecipeInputPortControl.suppressesInputs(machine().machineComponents())) {
            return null;
        }
        return requestedType.castHandler(handler);
    }

    /** Whether the player may retarget this port's capability sides at runtime. */
    public final boolean sideIoConfigurable() {
        return sideIo != null;
    }

    /**
     * The current 12-bit packed per-side capability IO (see {@link PortAccess#sideModeOf}).
     * Non-configurable ports report their declaration-time default. Server-side read.
     */
    public final int packedSideIo() {
        DataInt runtime = sideIo;
        return runtime == null ? metadata.policy().defaultPackedSideIo() : runtime.value();
    }

    /**
     * Sets one block-local side to {@code mode}. Rejected (returns {@code false}) when the port is
     * not configurable, the mode falls outside the allowed runtime modes, or nothing changes.
     * Server-side only.
     */
    public final boolean setSideIo(Direction localSide, AutomationIo mode) {
        Objects.requireNonNull(localSide, "local side");
        Objects.requireNonNull(mode, "capability IO mode");
        DataInt runtime = sideIo;
        if (runtime == null || !metadata.policy().allowsRuntimeMode(mode)) {
            return false;
        }
        return applySideIo(PortAccess.withSideMode(runtime.value(), localSide, mode));
    }

    /** Advances one block-local side to the next allowed mode (NONE → … in envelope order). */
    public final boolean cycleSideIo(Direction localSide) {
        Objects.requireNonNull(localSide, "local side");
        DataInt runtime = sideIo;
        if (runtime == null) {
            return false;
        }
        List<AutomationIo> modes = metadata.policy().allowedRuntimeModes();
        if (modes.size() <= 1) {
            return false;
        }
        AutomationIo current = PortAccess.sideModeOf(runtime.value(), localSide);
        int index = modes.indexOf(current);
        AutomationIo next = modes.get(index < 0 ? 0 : (index + 1) % modes.size());
        return applySideIo(PortAccess.withSideMode(runtime.value(), localSide, next));
    }

    /** Restores the declaration-time per-side default. */
    public final void resetSideIo() {
        if (sideIo != null) {
            applySideIo(metadata.policy().defaultPackedSideIo());
        }
    }

    /** Closes every side ({@code NONE} is inside every declared envelope). */
    public final void disableAllSideIo() {
        if (sideIo != null) {
            applySideIo(0);
        }
    }

    private boolean applySideIo(int packed) {
        DataInt runtime = Objects.requireNonNull(sideIo, "side IO field");
        if (runtime.value() == packed) {
            return false;
        }
        runtime.set(packed);
        onSideIoChanged();
        notifyNeighborsOfSideIoChange();
        return true;
    }

    /**
     * Layer four of the side-IO change protocol: pure-block neighbors (pipes) hold no
     * {@code BlockCapabilityCache} to invalidate and re-evaluate their connections only on vanilla
     * neighbor block updates. Kept out of {@link #onSideIoChanged()} because the load-time sanitize
     * path runs there too, and block updates during block-entity load may touch still-loading
     * neighbor chunks.
     */
    private void notifyNeighborsOfSideIoChange() {
        Level level = machine().getLevel();
        if (level != null && !level.isClientSide()) {
            level.updateNeighborsAt(machine().getBlockPos(), machine().getBlockState().getBlock());
        }
    }

    /**
     * Three-layer invalidation after a side-IO change: the trait's per-mode views stay valid (they
     * gate by mode only), but the machine-level combined handler cache and every neighbor
     * {@code BlockCapabilityCache} hold the old per-side answer and must re-resolve.
     */
    private void onSideIoChanged() {
        machine().machineComponents().invalidateTransferHandlers(resourceType());
        Level level = machine().getLevel();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(machine().getBlockPos());
        }
        data().markPersistedStateChanged();
    }

    /**
     * Clamps a persisted packed value back into the declared envelope (stale saves after a
     * definition change). Subclasses overriding this hook must call {@code super.onMachineLoad()}.
     */
    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        DataInt runtime = sideIo;
        if (runtime == null || data().domain().isClientSide()) {
            return;
        }
        int sanitized = metadata.policy().sanitizePackedSideIo(runtime.value());
        if (sanitized != runtime.value()) {
            runtime.set(sanitized);
            onSideIoChanged();
        }
    }

    /**
     * Configurable ports contribute one side-IO card on the LEFT column: an icon title bar (IO
     * direction arrow left, resource family glyph right, trait id in the hover tooltip) over the
     * 3×3 side grid. Subclasses overriding this hook must call
     * {@code super.collectMachineUi(contribution)} to keep the card.
     */
    @Override
    public void collectMachineUi(MachineUiContribution contribution) {
        super.collectMachineUi(contribution);
        if (sideIo != null) {
            contribution.leftPanel(
                    "topo_side_io_" + id().getPath(),
                    sideIoCardTitle(),
                    null,
                    MachineUiComponentTemplate.INSTANCE.createSideIoCardTitle(this),
                    MachineUiComponentTemplate.INSTANCE.createSideIoGrid(this));
        }
        // Search-pool chrome is machine-level (MachineSearchPoolConfig): one card for all input
        // resource types, not one card per port.
    }

    /**
     * Textual fallback identity for the icon title bar ("Input" / "Output" / "Storage"); rendered
     * only where element title bars are unsupported. Named ports (a registered
     * {@code trait.<ns>.<path>} lang entry, e.g. the die slot) show their proper name instead of
     * the bare role, mirroring the icon title bar's hover label.
     */
    private Component sideIoCardTitle() {
        return Component.translatableWithFallback(
                id().toLanguageKey("trait"),
                "%s",
                Component.translatable(sideIoTitleKey(metadata.recipeIo())));
    }

    private static String sideIoTitleKey(RecipeRole role) {
        return switch (role) {
            case INPUT -> "ui.topo.side_io.title.insert";
            case OUTPUT -> "ui.topo.side_io.title.extract";
            case BOTH, NONE -> "ui.topo.side_io.title.both";
        };
    }

    protected void onStorageChanged(int index, @NonNull S previousContents) {
        // Framework windows (persist NBT apply, client sync apply): handler commits are structural
        // fills, not business mutations — no dirty / recipe-cache side effects.
        if (data().domain().isMutationSuppressed()) {
            return;
        }
        if (data().domain().isClientSide()) {
            // LDLib2 menu slots maintain a client-side mirror of server inventory contents by
            // writing into the local ResourceHandler during container sync/click handling. That
            // mirror is display state, not authoritative machine state, so it must not request
            // persistence or end-of-tick save while the server remains the only owner of storage.
            machine().machineComponents().noteResourceContentChanged();
            return;
        }
        // Optimization safety: recipe search can skip repeated failed searches only while no
        // resource storage has changed. Bumping the machine-wide version here makes inserts,
        // extracts, automation, recipe consumption, and output emission all invalidate that cache.
        machine().machineComponents().noteResourceContentChanged();
        // The field's own persist policy decides when the chunk gets marked unsaved (dirty-tick for
        // items/fluids, periodic for scalars); a domain-wide immediate save request here would
        // collapse every policy back to per-tick marking.
        markPersistedContentsDirty.run();
    }

    /**
     * The owning block's {@code FACING} for the block-facing-relative capability side check, or
     * {@code null} when the block has no {@code FACING} property (world-absolute degradation).
     */
    private @Nullable Direction blockFacing() {
        BlockState state = machine().getBlockState();
        return state.hasProperty(DirectionalBlock.FACING) ? state.getValue(DirectionalBlock.FACING) : null;
    }

    private static ResourcePortMetadata validateMetadata(
                                                         MachineResourceType<? extends Resource> resourceType,
                                                         ResourcePortMetadata metadata) {
        ResourcePortMetadata checked = Objects.requireNonNull(metadata, "resource port metadata");
        if (checked.resourceType() != resourceType) {
            throw new IllegalArgumentException(
                    "Resource port metadata type " + checked.resourceType().id() + " does not match trait resource type " + resourceType.id());
        }
        return checked;
    }
}
