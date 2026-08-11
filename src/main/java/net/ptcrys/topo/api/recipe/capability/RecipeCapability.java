package net.ptcrys.topo.api.recipe.capability;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.search.RecipeSearchKeyRegistry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Recipe content capability.
 *
 * <p>
 * The input type is a symbolic matcher; the output type is concrete emitted content. The
 * capability owns codecs and machine-side match/transfer behavior for that capability.
 */
public abstract class RecipeCapability<I, O> {

    /**
     * Machine-bound direct tick-I/O kernel. Implementations capture the already-resolved handlers
     * and immutable recipe amounts once when a run starts, so a working tick performs no router
     * lookup or content-list reduction. The two-phase check/apply split preserves recipe-wide
     * all-or-nothing semantics when several capabilities participate.
     */
    public interface DirectTickIoBinding {

        /** Re-check a backing handler whose direct capability may be a dynamic wrapper. */
        boolean directReady();

        boolean matchesInput();

        boolean matchesOutputAfterInputs();

        void applyInput();

        void applyOutput();

        /** Backing identity used to reject cross-lane aliasing before entering the direct path. */
        default @Nullable Object inputIdentity() {
            return null;
        }

        /** Backing identity used to reject cross-lane aliasing before entering the direct path. */
        default @Nullable Object outputIdentity() {
            return null;
        }
    }

    /** How repeated recipe inputs sharing one search key contribute to its amount gate. */
    public enum InputIndexAmountMode {
        /** Independent demands consume storage and therefore add together. */
        ADDITIVE,
        /** Presence-only demands share storage and reserve only the largest requirement. */
        MAX_RESERVATION
    }

    private final Identifier id;
    private final Class<I> inputType;
    private final Class<O> outputType;
    private final Codec<I> inputCodec;
    private final Codec<O> outputCodec;
    private final StreamCodec<RegistryFriendlyByteBuf, I> inputStreamCodec;
    private final StreamCodec<RegistryFriendlyByteBuf, O> outputStreamCodec;

    protected RecipeCapability(
                               Identifier id,
                               Class<I> inputType,
                               Class<O> outputType,
                               Codec<I> inputCodec,
                               Codec<O> outputCodec,
                               StreamCodec<RegistryFriendlyByteBuf, I> inputStreamCodec,
                               StreamCodec<RegistryFriendlyByteBuf, O> outputStreamCodec) {
        this.id = id;
        this.inputType = inputType;
        this.outputType = outputType;
        this.inputCodec = inputCodec;
        this.outputCodec = outputCodec;
        this.inputStreamCodec = inputStreamCodec;
        this.outputStreamCodec = outputStreamCodec;
    }

    public final Identifier id() {
        return id;
    }

    public final Class<I> inputType() {
        return inputType;
    }

    public final Class<O> outputType() {
        return outputType;
    }

    public final Codec<I> inputCodec() {
        return inputCodec;
    }

    public final Codec<O> outputCodec() {
        return outputCodec;
    }

    public final StreamCodec<RegistryFriendlyByteBuf, I> inputStreamCodec() {
        return inputStreamCodec;
    }

    public final StreamCodec<RegistryFriendlyByteBuf, O> outputStreamCodec() {
        return outputStreamCodec;
    }

    /**
     * KHSD 铸造入口：input 内容值只能由本 capability 铸成 use；{@code verifyOwned} 拦截野句柄
     * （同 id 不同实例），{@code validateInput} 让非法内容在声明行爆错。
     */
    protected final RecipeInputUse<I> inputUse(I content) {
        RecipeCapabilities.verifyOwned(id, this);
        validateInput(Objects.requireNonNull(content, "recipe input content"));
        return new RecipeInputUse<>(this, content);
    }

    /** KHSD 铸造入口（输出侧）；见 {@link #inputUse}。 */
    protected final RecipeOutputUse<O> outputUse(O content) {
        RecipeCapabilities.verifyOwned(id, this);
        validateOutput(Objects.requireNonNull(content, "recipe output content"));
        return new RecipeOutputUse<>(this, content);
    }

    public abstract MachineResourceType<? extends Resource> resourceType();

    public abstract boolean matchInput(@Nullable MachineBlockEntity machine, List<I> contents);

    /**
     * Returns the largest input-supported parallel factor in {@code [0, maxParallel]}.
     *
     * <p>
     * This is the planning contract used by maximum-parallel recipe modifiers. It must be a
     * side-effect-free read: implementations must not open a transaction, mutate a handler, or
     * create scaled recipe copies. All contents for this capability are supplied together so an
     * implementation can account for requirements that compete for the same stored resource.
     *
     * <p>
     * The default is deliberately conservative. Recipe search has already verified the base
     * recipe before dynamic parallel is planned, so an extension lane that has not implemented a
     * quantity-aware planner keeps normal 1x operation but never claims additional parallel.
     */
    public long maxParallelByInputs(
                                    @Nullable MachineBlockEntity machine,
                                    List<I> contents,
                                    long maxParallel) {
        if (maxParallel < 1) {
            throw new IllegalArgumentException("maxParallel must be >= 1 (was " + maxParallel + ")");
        }
        return 1;
    }

    public abstract boolean matchOutput(@Nullable MachineBlockEntity machine, List<O> contents);

    /**
     * Pure output feasibility after the supplied tick inputs have been removed from the same
     * machine snapshot.
     *
     * <p>
     * {@link TopoRecipe} calls this only after {@link #matchInput} accepted
     * all tick inputs for this capability. The default is correct when input and output storage do not
     * overlap. Capabilities whose input extraction can free output capacity must override this method and
     * model the sequential extract-then-insert order without opening a transaction or mutating a
     * handler.
     */
    public boolean matchOutputAfterInputs(
                                          @Nullable MachineBlockEntity machine,
                                          List<I> inputs,
                                          List<O> outputs) {
        return matchOutput(machine, outputs);
    }

    public abstract boolean handleInputChecked(
                                               @Nullable MachineBlockEntity machine,
                                               List<I> contents,
                                               Transaction transaction);

    public abstract boolean handleOutputChecked(
                                                @Nullable MachineBlockEntity machine,
                                                List<O> contents,
                                                Transaction transaction);

    public void validateInput(I content) {}

    public void validateOutput(O content) {}

    /**
     * Pure scale of one input content for parallel / batch / performance modifiers. Duration is not
     * involved. Must cover every content shape this capability admits; {@code factor == 1} is identity.
     * Unknown or unscaleable shapes must throw — never silently return the original content.
     */
    public abstract I scaleInput(I content, double factor);

    /**
     * Exact integer scaling used by dynamic parallel. This path must not convert {@code factor} to
     * {@code double}; resource quantities are long-valued and factors above 2^53 must remain exact.
     */
    public abstract I scaleInputForParallel(I content, long factor);

    /**
     * Largest integer parallel factor this input content can represent without overflow. Lanes
     * that support dynamic parallel must override this together with {@link #scaleInput}; the
     * conservative default prevents an extension content type from being scaled speculatively.
     */
    public long maxParallelScaleInput(I content) {
        return 1;
    }

    /**
     * Pure scale of one output content; same contract as {@link #scaleInput(Object, double)}.
     */
    public abstract O scaleOutput(O content, double factor);

    /** Output counterpart of {@link #scaleInputForParallel(Object, long)}. */
    public abstract O scaleOutputForParallel(O content, long factor);

    /** Output counterpart of {@link #maxParallelScaleInput(Object)}. */
    public long maxParallelScaleOutput(O content) {
        return 1;
    }

    /** Shared gate for scale implementations. */
    protected static void requirePositiveScaleFactor(double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0d) {
            throw new IllegalArgumentException("factor must be finite and > 0 (was " + factor + ")");
        }
    }

    public abstract boolean hasAnyContent(@Nullable MachineBlockEntity machine);

    /**
     * Whether per-tick IO for this capability can ever run through the direct capability (no root transaction).
     * The direct lane exists because {@code Transaction.openRoot()} performs a native stack walk per
     * call, which dominates a scalar tick-output machine's whole working tick. Only capabilities
     * whose check-then-apply is exactly equivalent to their transactional handler may opt in.
     */
    public boolean supportsDirectTickIo() {
        return false;
    }

    /**
     * Compiles this capability's tick I/O against one explicit recipe-search pool. Returning
     * {@code null} keeps the recipe on the generic transactional path. A binding must be safe to
     * reuse until the owning machine's recipe-routing revision changes.
     */
    public @Nullable DirectTickIoBinding bindDirectTickIo(
                                                          @Nullable MachineBlockEntity machine,
                                                          RecipeSearchPoolId poolId,
                                                          List<I> inputs,
                                                          List<O> outputs) {
        return null;
    }

    /**
     * Whether this capability's match/handle result changes only when machine resource contents
     * change.
     *
     * <p>
     * Optimization safety contract: recipe logic may reuse failed searches or blocked retry
     * checks across ticks only for recipes whose relevant capabilities return {@code true}. The
     * default is conservative because future implicit capabilities may depend on time, environment,
     * redstone, heat, structure, or other state that is not represented by resource storage
     * versioning.
     */
    public boolean isStableForResourceContentVersion() {
        return false;
    }

    /**
     * Optimization: capability-owned index keys let recipe search prune candidates before opening
     * transactional match checks. Principle: the generic index does not understand item tags,
     * fluid identity, or future resources; each capability publishes only keys that are safe and
     * selective for its own symbolic input model.
     *
     * <p>
     * Safety contract: every returned key must be a necessary start-input condition for this
     * content. If the content is an OR/AnyOf, wildcard, stateful, temporal, or otherwise cannot be
     * expressed as a necessary key, return an empty set so the recipe search downgrades to precise
     * verification. The search index may use these keys only to produce candidates; exact
     * capability matching remains the final semantic check.
     */
    public Set<?> indexKeys(I content) {
        return Set.of();
    }

    /**
     * Optimization: amount prechecks reject candidates with insufficient resources before the
     * expensive precise matcher runs. The search combines this lower-bound amount according to
     * {@link #inputIndexAmountMode(Object)} before comparing it with the machine fingerprint.
     *
     * <p>
     * Safety contract: the returned amount must be a lower-bound requirement for every key from
     * {@link #indexKeys(Object)}. Capabilities with temporal or otherwise non-monotonic amounts
     * should return no index keys and rely on exact matching.
     */
    public long inputAmount(I content) {
        return 1L;
    }

    /**
     * Declares how repeated inputs with the same index key are aggregated. Additive demand and a
     * max reservation may coexist for one key; the index requires their saturated sum.
     */
    public InputIndexAmountMode inputIndexAmountMode(I content) {
        return InputIndexAmountMode.ADDITIVE;
    }

    /**
     * Optimization: machine fingerprints are extracted once per search from live recipe-side
     * handlers. Principle: matching many recipes should pay one cheap resource scan, not one
     * transactional handler simulation per candidate.
     */
    public void extractMachineKeys(
                                   @Nullable MachineBlockEntity machine,
                                   RecipeSearchKeyRegistry registry,
                                   Int2LongMap out) {}

    /**
     * Optimization: frozen registries can publish a compact array of indexing capabilities.
     * Principle: hot search paths should not iterate every capability and branch on io style when
     * only a subset can contribute search keys.
     */
    public boolean contributesIndexKeys() {
        return false;
    }
}
