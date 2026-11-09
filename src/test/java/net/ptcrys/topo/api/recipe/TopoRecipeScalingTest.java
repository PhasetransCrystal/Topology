package net.ptcrys.topo.api.recipe;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.api.recipe.search.RecipeSearchKeyRegistry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TopoRecipeScalingTest {

    private static final TestCapability CAPABILITY = new TestCapability(Identifier.parse("topo_test:scaling_probe"));
    private static final TopoRecipeType<ProbeRecipe> TYPE = new TopoRecipeType<>(
            Identifier.parse("topo_test:scaling_type"), ProbeRecipe::new);

    @Test
    void scalingPreservesCustomRecipeSubtype() {
        ProbeRecipe recipe = probeRecipe();

        assertInstanceOf(ProbeRecipe.class, recipe.withScaledDuration(2.0d));
        assertInstanceOf(ProbeRecipe.class, recipe.withScaledCapabilityAmounts(CAPABILITY, 2.0d));
        assertInstanceOf(ProbeRecipe.class, recipe.withScaledAllContents(2.0d));
        assertInstanceOf(ProbeRecipe.class, recipe.withParallel(2L));
        assertInstanceOf(ProbeRecipe.class, recipe.withScaledCapabilityPerformance(CAPABILITY, 2.0d, 2.0d));
        assertInstanceOf(
                ProbeRecipe.class,
                recipe.withScaledCapabilityConversion(CAPABILITY, CAPABILITY, 2.0d, 2.0d, 2.0d));
    }

    @Test
    void inputEntriesAggregateCapabilityConflictKeys() {
        TopoRecipe.InputEntry<Integer> entry = new TopoRecipe.InputEntry<>(CAPABILITY, List.of(7));
        assertEquals(Set.of(7), entry.importConflictKeys());
    }

    @Test
    void searchKeyRegistrySeparatesCapabilityNamespaces() {
        RecipeSearchKeyRegistry registry = new RecipeSearchKeyRegistry();
        int first = registry.getOrAssign(CAPABILITY, "same_key");
        TestCapability other = new TestCapability(Identifier.parse("topo_test:other_scaling_probe"));
        int otherFirst = registry.getOrAssign(other, "same_key");

        assertNotEquals(first, otherFirst);
        assertEquals(first, registry.getOrAssign(CAPABILITY, "same_key"));
        assertEquals(otherFirst, registry.getOrAssign(other, "same_key"));
        assertEquals(-1, registry.idOf(CAPABILITY, "missing"));
        assertEquals(-1, registry.idOf(other, "missing"));
    }

    @Test
    void scalingStillProducesTheExpectedValues() {
        ProbeRecipe recipe = probeRecipe();

        ProbeRecipe faster = (ProbeRecipe) recipe.withScaledDuration(2.0d);
        assertEquals(200, faster.duration());
        assertEquals(2, inputCount(faster));
        assertEquals(3, outputCount(faster));

        ProbeRecipe doubled = (ProbeRecipe) recipe.withScaledCapabilityAmounts(CAPABILITY, 2.0d);
        assertEquals(4, inputCount(doubled));
        assertEquals(6, outputCount(doubled));

        ProbeRecipe parallel = (ProbeRecipe) recipe.withParallel(2L);
        assertEquals(4, inputCount(parallel));
        assertEquals(6, outputCount(parallel));
    }

    private static ProbeRecipe probeRecipe() {
        return TYPE.createRecipe(
                new TopoRecipe.InputEntry<?>[] { new TopoRecipe.InputEntry<>(CAPABILITY, List.of(2)) },
                new TopoRecipe.OutputEntry<?>[] { new TopoRecipe.OutputEntry<>(CAPABILITY, List.of(3)) },
                TopoRecipe.EMPTY_INPUTS,
                TopoRecipe.EMPTY_OUTPUTS,
                100);
    }

    private static int inputCount(TopoRecipe recipe) {
        return (Integer) recipe.inputs()[0].contents().getFirst();
    }

    private static int outputCount(TopoRecipe recipe) {
        return (Integer) recipe.outputs()[0].contents().getFirst();
    }

    private static final class ProbeRecipe extends TopoRecipe {

        private ProbeRecipe(
                            TopoRecipeType<ProbeRecipe> type,
                            InputEntry<?>[] inputs,
                            OutputEntry<?>[] outputs,
                            InputEntry<?>[] tickInputs,
                            OutputEntry<?>[] tickOutputs,
                            int duration,
                            List<ProductionLine> productionLines) {
            super(type, inputs, outputs, tickInputs, tickOutputs, duration, productionLines);
        }
    }

    private static final class TestCapability extends RecipeCapability<Integer, Integer> {

        private static final StreamCodec<RegistryFriendlyByteBuf, Integer> STREAM = StreamCodec.of(
                (buffer, value) -> buffer.writeVarInt(value),
                buffer -> buffer.readVarInt());

        private TestCapability(Identifier id) {
            super(id, Integer.class, Integer.class, Codec.INT, Codec.INT, STREAM, STREAM);
        }

        @Override
        public MachineResourceType<? extends Resource> resourceType() {
            return null;
        }

        @Override
        public boolean matchInput(MachineBlockEntity machine, List<Integer> contents) {
            return true;
        }

        @Override
        public boolean matchOutput(MachineBlockEntity machine, List<Integer> contents) {
            return true;
        }

        @Override
        public boolean handleInputChecked(MachineBlockEntity machine, List<Integer> contents, Transaction transaction) {
            return true;
        }

        @Override
        public boolean handleOutputChecked(MachineBlockEntity machine, List<Integer> contents, Transaction transaction) {
            return true;
        }

        @Override
        public Integer scaleInput(Integer content, double factor) {
            return Math.max(1, (int) Math.round(content * factor));
        }

        @Override
        public Integer scaleInputForParallel(Integer content, long factor) {
            return Math.toIntExact(content * factor);
        }

        @Override
        public Integer scaleOutput(Integer content, double factor) {
            return Math.max(1, (int) Math.round(content * factor));
        }

        @Override
        public Integer scaleOutputForParallel(Integer content, long factor) {
            return Math.toIntExact(content * factor);
        }

        @Override
        public boolean hasAnyContent(MachineBlockEntity machine) {
            return false;
        }

        @Override
        public Set<?> importConflictKeys(Integer content) {
            return Set.of(content);
        }
    }
}
