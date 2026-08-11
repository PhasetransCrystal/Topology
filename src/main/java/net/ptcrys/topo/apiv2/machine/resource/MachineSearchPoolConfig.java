package net.ptcrys.topo.apiv2.machine.resource;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.component.MachineComponent;
import net.ptcrys.topo.apiv2.machine.data.DataString;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContribution;

import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * Single machine-level search-pool configuration. All isolatable ports (item/fluid input and
 * output) on this block entity share {@link #recipeSearchPoolId()}: a recipe that hits this pool
 * also emits into it. {@link RecipeSearchPoolId#UNIVERSAL} means global membership (every pool).
 *
 * <p>
 * UI: multiblock parts expose reset (DEFAULT) + UNIVERSAL buttons + id field. Ordinary
 * single-block machines keep their implicit default routing and do not show this port-oriented
 * control. ME pattern separation ({@link SearchPoolUiControl}) also hides the card when per-pattern
 * pools own recipe search instead.
 */
public final class MachineSearchPoolConfig extends MachineComponent implements RecipeSearchPoolSettings {

    public static final ComponentKey<MachineSearchPoolConfig> RECIPE_SEARCH_POOL = ComponentKey.oi("recipe_search_pool", MachineSearchPoolConfig.class)
            .service(RecipeSearchPoolSettings.KEY, (trait, unused) -> trait);

    private final RecipeSearchPoolId fieldDefault;
    private final DataString poolIdRaw;
    private RecipeSearchPoolId resolvedPoolId;

    private MachineSearchPoolConfig(
                                    ComponentContext<MachineSearchPoolConfig> context, RecipeSearchPoolId fieldDefault) {
        super(context);
        this.fieldDefault = Objects.requireNonNull(fieldDefault, "field default");
        this.resolvedPoolId = fieldDefault;
        // Server-only machine data: open-menu chrome uses LDLib UI bindings / RPC, not field S2C.
        this.poolIdRaw = data().stringField("pool_id", fieldDefault.value())
                .persisted()
                .syncNone()
                .done();
    }

    /** Input-oriented machines: default pool id {@link RecipeSearchPoolId#DEFAULT}. */
    public static ComponentMount<MachineSearchPoolConfig> mount() {
        return mount(RecipeSearchPoolId.DEFAULT);
    }

    /**
     * @param fieldDefault initial / reset-target id ({@link RecipeSearchPoolId#DEFAULT} for
     *                     input hatches, {@link RecipeSearchPoolId#UNIVERSAL} for output-only export hatches)
     */
    public static ComponentMount<MachineSearchPoolConfig> mount(RecipeSearchPoolId fieldDefault) {
        return RECIPE_SEARCH_POOL.mount(context -> new MachineSearchPoolConfig(context, fieldDefault));
    }

    @Override
    public RecipeSearchPoolId recipeSearchPoolId() {
        refreshResolvedPoolId();
        return resolvedPoolId;
    }

    /** Raw field text for the UI. */
    public String configuredPoolIdRaw() {
        String raw = poolIdRaw.valueOrElse(fieldDefault.value());
        return raw.isBlank() ? fieldDefault.value() : raw;
    }

    /**
     * Sets the pool token. Blank becomes the field default. Invalid drafts are rejected without
     * changing persisted configuration or routing. This matters while a player types a
     * six-character id: the five intermediate drafts must not temporarily move the hatch into
     * {@link RecipeSearchPoolId#DEFAULT}, dirty the chunk, or rebuild controller routing.
     */
    public void setConfiguredPoolIdRaw(String raw) {
        if (!data().domain().isBusinessReady()) {
            return;
        }
        String value = normalizeStored(raw, fieldDefault);
        if (!RecipeSearchPoolId.isValidConfiguredToken(value)) {
            return;
        }
        if (poolIdRaw.valueOrElse(fieldDefault.value()).equals(value)) {
            return;
        }
        refreshResolvedPoolId();
        RecipeSearchPoolId previousPoolId = resolvedPoolId;
        poolIdRaw.set(value);
        refreshResolvedPoolId();
        if (!resolvedPoolId.equals(previousPoolId)) {
            machine().machineComponents().invalidateRecipeHandlers();
        }
        data().markPersistedStateChanged();
    }

    /** Reset button: field default ({@link RecipeSearchPoolId#DEFAULT} or {@link RecipeSearchPoolId#UNIVERSAL}). */
    public void resetToFieldDefault() {
        setConfiguredPoolIdRaw(fieldDefault.value());
    }

    /** UNIVERSAL button: global membership. */
    public void setUniversal() {
        setConfiguredPoolIdRaw(RecipeSearchPoolId.UNIVERSAL.value());
    }

    private static String normalizeStored(String raw, RecipeSearchPoolId fieldDefault) {
        if (raw == null || raw.isBlank()) {
            return fieldDefault.value();
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase(RecipeSearchPoolId.DEFAULT.value())) {
            return RecipeSearchPoolId.DEFAULT.value();
        }
        if (trimmed.equalsIgnoreCase(RecipeSearchPoolId.UNIVERSAL.value())) {
            return RecipeSearchPoolId.UNIVERSAL.value();
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void refreshResolvedPoolId() {
        resolvedPoolId = resolveConfiguredPoolId(
                poolIdRaw.valueOrElse(fieldDefault.value()), fieldDefault, resolvedPoolId);
    }

    static RecipeSearchPoolId resolveConfiguredPoolId(
                                                      String raw,
                                                      RecipeSearchPoolId fieldDefault,
                                                      RecipeSearchPoolId previousPoolId) {
        Objects.requireNonNull(fieldDefault, "field default");
        Objects.requireNonNull(previousPoolId, "previous pool id");
        if (raw.isBlank()) {
            return fieldDefault;
        }
        return RecipeSearchPoolId.isValidConfiguredToken(raw) ? RecipeSearchPoolId.parse(raw) : previousPoolId;
    }

    @Override
    public void collectMachineUi(MachineUiContribution contribution) {
        // Search-pool selection groups multiblock ports. A standalone machine has only its own
        // handlers, so exposing the implicit DEFAULT pool is redundant player-facing chrome.
        if (machine().definition().metadata(PartRoleAttachment.TYPE).isEmpty()) {
            return;
        }
        contribution.rightPanel(
                "oi_search_pool",
                Component.translatable("ui.topo.search_pool.panel"),
                null,
                null,
                MachineUiComponentTemplate.INSTANCE.createSearchPoolConfigPanel(
                        this::configuredPoolIdRaw,
                        this::setConfiguredPoolIdRaw,
                        this::resetToFieldDefault,
                        this::setUniversal,
                        fieldDefault.value()),
                () -> !suppressUi());
    }

    private boolean suppressUi() {
        return machine()
                .machineComponents()
                .anyServiceMatches(
                        SearchPoolUiControl.KEY,
                        null,
                        SearchPoolUiControl::suppressPortSearchPoolConfig);
    }
}
