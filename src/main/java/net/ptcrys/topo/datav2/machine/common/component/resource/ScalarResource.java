package net.ptcrys.topo.datav2.machine.common.component.resource;

import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.datav2.OfficialOIPlugin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One scalar machine resource kind (energy, heat, ...) modelled as a first-class NeoForge transfer
 * {@link Resource} singleton. Amounts live in handlers/contents; the resource itself only carries
 * identity and display data, so handler/transaction/persistence plumbing is shared with item and
 * fluid ports unchanged.
 */
public final class ScalarResource implements Resource {

    private static final Map<Identifier, ScalarResource> BY_ID = new ConcurrentHashMap<>();

    /** Shared empty marker required by stack-backed handlers; never registered as a port resource. */
    public static final ScalarResource EMPTY = registerInternal(new ScalarResource(
            Identifier.parse("topo:empty_scalar"),
            OfficialOIPlugin.INSTANCE.lang().key("resource", "empty_scalar", "Empty", "空"),
            0x00000000,
            ScalarDestroyedBehavior.NONE,
            true));

    /**
     * Identifier-backed codec. Scalar resources are bootstrap-registered singletons, so decode
     * resolves to the exact registered instance (identity equality stays valid after reload).
     */
    public static final Codec<ScalarResource> CODEC = Identifier.CODEC.flatXmap(
            id -> {
                ScalarResource resource = BY_ID.get(id);
                return resource == null ? DataResult.error(() -> "Unknown scalar resource " + id) : DataResult.success(resource);
            },
            resource -> DataResult.success(resource.id));

    private final Identifier id;
    private final LangKey nameLang;
    private final int color;
    private final ScalarDestroyedBehavior destroyedBehavior;
    private final boolean empty;

    private ScalarResource(
                           Identifier id,
                           LangKey nameLang,
                           int color,
                           ScalarDestroyedBehavior destroyedBehavior,
                           boolean empty) {
        this.id = Objects.requireNonNull(id, "scalar resource id");
        this.nameLang = Objects.requireNonNull(nameLang, "scalar resource name lang");
        this.color = color;
        this.destroyedBehavior = Objects.requireNonNull(destroyedBehavior, "scalar destroyed behavior");
        this.empty = empty;
    }

    /**
     * Registers a scalar kind with its destroyed-machine behavior bound at the identity definition
     * point. Kinds without a hazard must pass {@link ScalarDestroyedBehavior#NONE} explicitly —
     * there is no silent default. {@code nameLang} is the handle minted at family registration.
     */
    public static ScalarResource register(
                                          Identifier id,
                                          LangKey nameLang,
                                          int color,
                                          ScalarDestroyedBehavior destroyedBehavior) {
        return registerInternal(new ScalarResource(id, nameLang, color, destroyedBehavior, false));
    }

    private static ScalarResource registerInternal(ScalarResource resource) {
        ScalarResource existing = BY_ID.putIfAbsent(resource.id, resource);
        if (existing != null) {
            throw new IllegalStateException("Scalar resource " + resource.id + " is already registered");
        }
        return resource;
    }

    public Identifier id() {
        return id;
    }

    public LangKey nameLang() {
        return nameLang;
    }

    /** @deprecated Prefer {@link #nameLang()} / {@link #displayName()}. */
    @Deprecated
    public String translationKey() {
        return nameLang.key();
    }

    /** ARGB display color used by UI accents (resource bar fill, name tint). */
    public int color() {
        return color;
    }

    /** World-side behavior when a machine holding this scalar kind is destroyed. */
    public ScalarDestroyedBehavior destroyedBehavior() {
        return destroyedBehavior;
    }

    public Component displayName() {
        return nameLang.getComponent();
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    @Override
    public String toString() {
        return "ScalarResource[" + id + "]";
    }
}
