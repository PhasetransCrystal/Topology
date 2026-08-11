package net.ptcrys.topo.datav2.recipe.common;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.resource.DirectResourceAccess;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.ResourceHandlerLongOps;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapability;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeInputUse;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeOutputUse;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResource;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Long-amount recipe capability for one scalar resource kind (energy, advanced energy, heat).
 *
 * <p>
 * Contents are plain {@code Long} amounts; matching and transfer go through the machine's
 * scalar resource handlers. Implicit IO style: scalars never join the slot-plan pipeline — their
 * display form is the {@code ResourceBar} (machine UI bottom strip, Jade horizontal bars, JEI
 * vertical bars on the recipe's left).
 */
public final class ScalarRecipeCapability extends RecipeCapability<Long, Long> {

    private static final StreamCodec<RegistryFriendlyByteBuf, Long> AMOUNT_STREAM_CODEC = ByteBufCodecs.VAR_LONG.mapStream(buf -> buf);

    private final MachineResourceType<ScalarResource> resourceType;
    private final ScalarResource resource;

    public ScalarRecipeCapability(
                                  Identifier id,
                                  MachineResourceType<ScalarResource> resourceType,
                                  ScalarResource resource) {
        super(
                id,
                Long.class,
                Long.class,
                Codec.LONG,
                Codec.LONG,
                AMOUNT_STREAM_CODEC,
                AMOUNT_STREAM_CODEC);
        this.resourceType = Objects.requireNonNull(resourceType, "scalar resource type");
        this.resource = Objects.requireNonNull(resource, "scalar resource");
    }

    public ScalarResource resource() {
        return resource;
    }

    /** 铸造：标量消耗（开始或每刻通道由 builder 根方法决定）。 */
    public RecipeInputUse<Long> in(long amount) {
        return inputUse(amount);
    }

    /** 铸造：标量产出。 */
    public RecipeOutputUse<Long> out(long amount) {
        return outputUse(amount);
    }

    @Override
    public MachineResourceType<ScalarResource> resourceType() {
        return resourceType;
    }

    @Override
    public boolean matchInput(@Nullable MachineBlockEntity machine, List<Long> contents) {
        ResourceHandler<ScalarResource> handler = scalarHandler(machine, RecipeRole.INPUT);
        return maxParallelByInputs(handler, resource, contents, 1) > 0;
    }

    @Override
    public long maxParallelByInputs(
                                    @Nullable MachineBlockEntity machine,
                                    List<Long> contents,
                                    long maxParallel) {
        return maxParallelByInputs(
                scalarHandler(machine, RecipeRole.INPUT), resource, contents, maxParallel);
    }

    @Override
    public boolean matchOutput(@Nullable MachineBlockEntity machine, List<Long> contents) {
        ResourceHandler<ScalarResource> handler = scalarHandler(machine, RecipeRole.OUTPUT);
        if (handler == null) {
            return contents.isEmpty();
        }
        final long required;
        try {
            required = totalAmount(contents);
        } catch (ArithmeticException overflow) {
            return false;
        }
        long free = 0L;
        for (int slot = 0; slot < handler.size() && free < required; slot++) {
            ScalarResource stored = handler.getResource(slot);
            long amount = handler.getAmountAsLong(slot);
            long capacity = handler.getCapacityAsLong(slot, resource);
            if (amount < 0L || capacity < 0L) {
                return false;
            }
            if (handler.isValid(slot, resource) && (stored.isEmpty() || stored == resource)) {
                free = addUpTo(free, Math.max(0L, capacity - amount), required);
            }
        }
        return free >= required;
    }

    @Override
    public boolean matchOutputAfterInputs(
                                          @Nullable MachineBlockEntity machine,
                                          List<Long> inputs,
                                          List<Long> outputs) {
        if (outputs.isEmpty()) {
            return true;
        }
        ResourceHandler<ScalarResource> outputHandler = scalarHandler(machine, RecipeRole.OUTPUT);
        if (outputHandler == null) {
            return false;
        }
        if (inputs.isEmpty()) {
            return matchOutput(machine, outputs);
        }
        ResourceHandler<ScalarResource> inputHandler = scalarHandler(machine, RecipeRole.INPUT);
        if (inputHandler == null) {
            return false;
        }
        return matchOutputAfterInputs(inputHandler, inputs, outputHandler, outputs);
    }

    /** Pure handler-level form used by resource routers that already resolved both recipe roles. */
    public boolean matchOutputAfterInputs(
                                          ResourceHandler<ScalarResource> inputHandler,
                                          List<Long> inputs,
                                          ResourceHandler<ScalarResource> outputHandler,
                                          List<Long> outputs) {
        Objects.requireNonNull(inputHandler, "input handler");
        Objects.requireNonNull(inputs, "scalar tick inputs");
        Objects.requireNonNull(outputHandler, "output handler");
        Objects.requireNonNull(outputs, "scalar tick outputs");
        try {
            return canInsertAfterExtract(
                    inputHandler,
                    totalAmount(inputs),
                    outputHandler,
                    totalAmount(outputs));
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    @Override
    public boolean handleInputChecked(
                                      @Nullable MachineBlockEntity machine,
                                      List<Long> contents,
                                      Transaction transaction) {
        ResourceHandler<ScalarResource> handler = scalarHandler(machine, RecipeRole.INPUT);
        return handler != null && extractAll(handler, contents, transaction);
    }

    @Override
    public boolean handleOutputChecked(
                                       @Nullable MachineBlockEntity machine,
                                       List<Long> contents,
                                       Transaction transaction) {
        ResourceHandler<ScalarResource> handler = scalarHandler(machine, RecipeRole.OUTPUT);
        return handler != null && insertAll(handler, contents, transaction);
    }

    @Override
    public void validateInput(Long content) {
        validateAmount(content);
    }

    @Override
    public void validateOutput(Long content) {
        validateAmount(content);
    }

    @Override
    public Long scaleInput(Long content, double factor) {
        return scaleAmount(content, factor);
    }

    @Override
    public Long scaleOutput(Long content, double factor) {
        return scaleAmount(content, factor);
    }

    @Override
    public Long scaleInputForParallel(Long content, long factor) {
        return scaleAmountForParallel(content, factor);
    }

    @Override
    public Long scaleOutputForParallel(Long content, long factor) {
        return scaleAmountForParallel(content, factor);
    }

    @Override
    public long maxParallelScaleInput(Long content) {
        return maxParallelScale(content);
    }

    @Override
    public long maxParallelScaleOutput(Long content) {
        return maxParallelScale(content);
    }

    private static long maxParallelScale(Long content) {
        long amount = Objects.requireNonNull(content, "scalar content");
        return Long.MAX_VALUE / amount;
    }

    private Long scaleAmount(Long content, double factor) {
        Objects.requireNonNull(content, "scalar content");
        if (factor == 1.0d) {
            return content;
        }
        requirePositiveScaleFactor(factor);
        BigDecimal scaled = BigDecimal.valueOf(content)
                .multiply(BigDecimal.valueOf(factor))
                .setScale(0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
        try {
            return scaled.longValueExact();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Scaled scalar amount exceeds long range: " + scaled, overflow);
        }
    }

    private Long scaleAmountForParallel(Long content, long factor) {
        long amount = Objects.requireNonNull(content, "scalar content");
        if (factor < 1L) {
            throw new IllegalArgumentException("parallel factor must be >= 1 (was " + factor + ")");
        }
        if (factor == 1L) {
            return content;
        }
        if (amount > Long.MAX_VALUE / factor) {
            throw new IllegalArgumentException(
                    "Scaled scalar amount exceeds long range: " + amount + " * " + factor);
        }
        return amount * factor;
    }

    private void validateAmount(Long content) {
        long amount = Objects.requireNonNull(content, "scalar content");
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Scalar " + id() + " recipe amount must be > 0 (was " + amount + ")");
        }
    }

    @Override
    public boolean hasAnyContent(@Nullable MachineBlockEntity machine) {
        ResourceHandler<ScalarResource> handler = scalarHandler(machine, RecipeRole.INPUT);
        if (handler == null) {
            return false;
        }
        for (int index = 0; index < handler.size(); index++) {
            if (handler.getAmountAsLong(index) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isStableForResourceContentVersion() {
        // Scalar checks read and mutate only scalar resource handlers; ResourcePort bumps
        // the machine resource version on every committed change, so retry caches stay safe.
        return true;
    }

    @Override
    public boolean supportsDirectTickIo() {
        // Scalar amounts are exactly simulatable from one snapshot, including shared storage that
        // is extracted before output insertion. Direct apply follows the same mount order.
        return true;
    }

    @Override
    public @Nullable DirectTickIoBinding bindDirectTickIo(
                                                          @Nullable MachineBlockEntity machine,
                                                          RecipeSearchPoolId poolId,
                                                          List<Long> inputs,
                                                          List<Long> outputs) {
        if (machine == null) {
            return null;
        }
        final long inputAmount;
        final long outputAmount;
        try {
            inputAmount = totalAmount(inputs);
            outputAmount = totalAmount(outputs);
        } catch (ArithmeticException overflow) {
            return null;
        }

        ResourceHandler<ScalarResource> inputHandler = inputAmount == 0L ? null : machine.machineComponents().recipeResourceHandler(resourceType, RecipeRole.INPUT, poolId);
        ResourceHandler<ScalarResource> outputHandler = outputAmount == 0L ? null : machine.machineComponents().recipeResourceHandler(resourceType, RecipeRole.OUTPUT, poolId);
        DirectResourceAccess<ScalarResource> directInput = directAccess(inputHandler);
        DirectResourceAccess<ScalarResource> directOutput = directAccess(outputHandler);
        if ((inputAmount > 0L && directInput == null) || (outputAmount > 0L && directOutput == null)) {
            return null;
        }
        return new BoundScalarTickIo(
                inputHandler,
                directInput,
                inputAmount,
                outputHandler,
                directOutput,
                outputAmount);
    }

    private static long totalAmount(List<Long> contents) {
        long total = 0L;
        for (int i = 0; i < contents.size(); i++) {
            total = Math.addExact(total, contents.get(i));
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable DirectResourceAccess<ScalarResource> directAccess(
                                                                               @Nullable ResourceHandler<ScalarResource> handler) {
        if (!(handler instanceof DirectResourceAccess<?> direct) || !direct.directReady() || !direct.directExactTransfers()) {
            return null;
        }
        return (DirectResourceAccess<ScalarResource>) direct;
    }

    private boolean canInsertAfterExtract(
                                          ResourceHandler<ScalarResource> inputHandler,
                                          long inputAmount,
                                          ResourceHandler<ScalarResource> outputHandler,
                                          long outputAmount) {
        int inputSlots = inputHandler.size();
        int outputSlots = outputHandler.size();
        if (inputSlots < 0 || outputSlots < 0) {
            return false;
        }

        long free = 0L;
        for (int outputSlot = 0; outputSlot < outputSlots && free < outputAmount; outputSlot++) {
            ScalarResource stored = outputHandler.getResource(outputSlot);
            long storedAmount = outputHandler.getAmountAsLong(outputSlot);
            long capacity = outputHandler.getCapacityAsLong(outputSlot, resource);
            if (stored == null || storedAmount < 0L || capacity < 0L) {
                return false;
            }
            if (!outputHandler.isValid(outputSlot, resource) || (!stored.isEmpty() && stored != resource)) {
                continue;
            }
            long extracted = stored == resource ? extractedFromStorageSlot(
                    inputHandler, inputSlots, inputAmount, outputHandler, outputSlot) : 0L;
            if (extracted > storedAmount) {
                return false;
            }
            long postExtractAmount = storedAmount - extracted;
            free = addUpTo(free, Math.max(0L, capacity - postExtractAmount), outputAmount);
        }
        return free >= outputAmount;
    }

    private final class BoundScalarTickIo implements DirectTickIoBinding {

        private final @Nullable ResourceHandler<ScalarResource> inputHandler;
        private final @Nullable DirectResourceAccess<ScalarResource> directInput;
        private final long inputAmount;
        private final @Nullable ResourceHandler<ScalarResource> outputHandler;
        private final @Nullable DirectResourceAccess<ScalarResource> directOutput;
        private final long outputAmount;

        private BoundScalarTickIo(
                                  @Nullable ResourceHandler<ScalarResource> inputHandler,
                                  @Nullable DirectResourceAccess<ScalarResource> directInput,
                                  long inputAmount,
                                  @Nullable ResourceHandler<ScalarResource> outputHandler,
                                  @Nullable DirectResourceAccess<ScalarResource> directOutput,
                                  long outputAmount) {
            this.inputHandler = inputHandler;
            this.directInput = directInput;
            this.inputAmount = inputAmount;
            this.outputHandler = outputHandler;
            this.directOutput = directOutput;
            this.outputAmount = outputAmount;
        }

        @Override
        public boolean directReady() {
            return (directInput == null || directInput.directReady()) && (directOutput == null || directOutput.directReady());
        }

        @Override
        public boolean matchesInput() {
            if (inputAmount == 0L) {
                return true;
            }
            DirectResourceAccess<ScalarResource> input = Objects.requireNonNull(directInput);
            return input.directResource() == resource && input.directAmount() >= inputAmount;
        }

        @Override
        public boolean matchesOutputAfterInputs() {
            if (outputAmount == 0L) {
                return true;
            }
            DirectResourceAccess<ScalarResource> output = Objects.requireNonNull(directOutput);
            if (inputAmount == 0L) {
                return output.directFreeFor(resource) >= outputAmount;
            }
            return canInsertAfterExtract(
                    Objects.requireNonNull(inputHandler),
                    inputAmount,
                    Objects.requireNonNull(outputHandler),
                    outputAmount);
        }

        @Override
        public void applyInput() {
            if (inputAmount == 0L) {
                return;
            }
            long extracted = Objects.requireNonNull(directInput).directExtract(resource, inputAmount);
            if (extracted != inputAmount) {
                throw new IllegalStateException("Bound direct scalar tick input under-applied for " + id() + ": expected " + inputAmount + ", got " + extracted);
            }
        }

        @Override
        public void applyOutput() {
            if (outputAmount == 0L) {
                return;
            }
            long inserted = Objects.requireNonNull(directOutput).directInsert(resource, outputAmount);
            if (inserted != outputAmount) {
                throw new IllegalStateException("Bound direct scalar tick output under-applied for " + id() + ": expected " + outputAmount + ", got " + inserted);
            }
        }

        @Override
        public @Nullable Object inputIdentity() {
            return inputHandler;
        }

        @Override
        public @Nullable Object outputIdentity() {
            return outputHandler;
        }
    }

    private long extractedFromStorageSlot(
                                          ResourceHandler<ScalarResource> inputHandler,
                                          int inputSlots,
                                          long requested,
                                          ResourceHandler<ScalarResource> outputHandler,
                                          int outputSlot) {
        long remaining = requested;
        long extractedFromTarget = 0L;
        for (int inputSlot = 0; inputSlot < inputSlots && remaining > 0L; inputSlot++) {
            ScalarResource stored = inputHandler.getResource(inputSlot);
            long amount = inputHandler.getAmountAsLong(inputSlot);
            if (stored != resource || amount <= 0L) {
                continue;
            }
            long extracted = Math.min(amount, remaining);
            if (ResourceHandlerLongOps.sameStorageSlot(
                    inputHandler, inputSlot, outputHandler, outputSlot)) {
                extractedFromTarget = addUpTo(extractedFromTarget, extracted, Long.MAX_VALUE);
            }
            remaining -= extracted;
        }
        return extractedFromTarget;
    }

    private @Nullable ResourceHandler<ScalarResource> scalarHandler(
                                                                    @Nullable MachineBlockEntity machine,
                                                                    RecipeRole io) {
        return machine == null ? null : machine.machineComponents().resources().recipeSide().handler(resourceType, io);
    }

    static long maxParallelByInputs(
                                    @Nullable ResourceHandler<ScalarResource> handler,
                                    ScalarResource resource,
                                    List<Long> contents,
                                    long maxParallel) {
        if (maxParallel < 1) {
            throw new IllegalArgumentException("maxParallel must be >= 1 (was " + maxParallel + ")");
        }
        if (contents.isEmpty()) {
            return maxParallel;
        }
        if (handler == null) {
            return 0;
        }

        long required = 0L;
        for (int index = 0; index < contents.size(); index++) {
            long amount = contents.get(index);
            if (amount <= 0L || required > Long.MAX_VALUE - amount) {
                return 0L;
            }
            required += amount;
        }

        long available = 0L;
        for (int slot = 0; slot < handler.size(); slot++) {
            if (handler.getResource(slot) == resource) {
                long amount = handler.getAmountAsLong(slot);
                if (amount < 0L) {
                    return 0L;
                }
                available = addUpTo(available, amount, required * Math.min(maxParallel, Long.MAX_VALUE / required));
            }
        }
        return Math.min(maxParallel, available / required);
    }

    private static long addUpTo(long left, long right, long limit) {
        if (right <= 0L || left >= limit) {
            return left;
        }
        return right >= limit - left ? limit : left + right;
    }

    private boolean extractAll(
                               ResourceHandler<ScalarResource> handler,
                               List<Long> contents,
                               Transaction transaction) {
        for (Long content : contents) {
            long requested = content;
            if (ResourceHandlerLongOps.extract(handler, resource, requested, transaction) != requested) {
                return false;
            }
        }
        return true;
    }

    private boolean insertAll(
                              ResourceHandler<ScalarResource> handler,
                              List<Long> contents,
                              Transaction transaction) {
        for (Long content : contents) {
            long requested = content;
            if (ResourceHandlerLongOps.insert(handler, resource, requested, transaction) != requested) {
                return false;
            }
        }
        return true;
    }
}
