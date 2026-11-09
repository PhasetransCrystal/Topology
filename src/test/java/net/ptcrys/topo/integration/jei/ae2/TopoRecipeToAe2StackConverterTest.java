package net.ptcrys.topo.integration.jei.ae2;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;
import net.ptcrys.topo.integration.ae2.TestAeKey;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopoRecipeToAe2StackConverterTest {

    @Test
    void groupedContentsBecomeIndependentSlotsAndPreserveAlternatives() {
        TestCapability capability = new TestCapability();
        var first = new TestAeKey("first");
        var alternative = new TestAeKey("alternative");
        TopoRecipeToAe2StackConverter.register(capability, new TopoRecipeToAe2StackConverter.ContentConverter<>() {

            @Override
            public List<GenericStack> inputs(Integer value) {
                return value == 1 ? List.of(new GenericStack(first, 1), new GenericStack(alternative, 1)) : List.of(new GenericStack(first, value));
            }

            @Override
            public GenericStack output(Integer value) {
                return new GenericStack(first, value);
            }
        });

        var result = TopoRecipeToAe2StackConverter.convertEntries(
                new TopoRecipe.InputEntry<?>[] { new TopoRecipe.InputEntry<>(capability, List.of(1, 2)) },
                new TopoRecipe.OutputEntry<?>[] { new TopoRecipe.OutputEntry<>(capability, List.of(3, 4)) });
        assertEquals(List.of(List.of(new GenericStack(first, 1), new GenericStack(alternative, 1)),
                List.of(new GenericStack(first, 2))), result.inputs());
        assertEquals(List.of(new GenericStack(first, 3), new GenericStack(first, 4)), result.outputs());
    }

    private static final class TestCapability extends RecipeCapability<Integer, Integer> {

        private static final StreamCodec<RegistryFriendlyByteBuf, Integer> STREAM = StreamCodec.of(
                (buffer, value) -> buffer.writeVarInt(value), buffer -> buffer.readVarInt());

        private TestCapability() {
            super(Identifier.fromNamespaceAndPath("topo_test", "pattern_contents"), Integer.class, Integer.class,
                    Codec.INT, Codec.INT, STREAM, STREAM);
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
        public Integer scaleOutput(Integer content, double factor) {
            return Math.max(1, (int) Math.round(content * factor));
        }

        @Override
        public Integer scaleInputForParallel(Integer content, long factor) {
            return Math.toIntExact(content * factor);
        }

        @Override
        public Integer scaleOutputForParallel(Integer content, long factor) {
            return Math.toIntExact(content * factor);
        }

        @Override
        public boolean hasAnyContent(MachineBlockEntity machine) {
            return false;
        }
    }
}
