package net.ptcrys.topodemo.api.recipe.common;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.*;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.recipe.capability.*;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.content.TopoItemOutput;
import net.ptcrys.topodemo.api.machine.common.ItemPort;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.slot.ItemResourceHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.mojang.serialization.DataResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Exact-item recipes: typed inputs and outputs use the library's deferred content values.
 * Transactions account for duplicate inputs and roll back every simulation or failed batch.
 * This example deliberately has no tag alternatives or unconsumed catalysts.
 */
public final class ItemRecipeCapability extends SlottedRecipeCapability<TopoItemInput.Resource, TopoItemOutput, ItemResource> {

    private final MachineResourceType<ItemResource> type;

    public ItemRecipeCapability(MachineResourceType<ItemResource> type) {
        super(type.id(), TopoItemInput.Resource.class, TopoItemOutput.class,
                TopoItemInput.CODEC.comapFlatMap(input -> input instanceof TopoItemInput.Resource resource ? DataResult.success(resource) : DataResult.error(() -> "This capability accepts exact item inputs"), input -> input),
                TopoItemOutput.CODEC,
                TopoItemInput.STREAM_CODEC.map(ItemRecipeCapability::exactInput, input -> input), TopoItemOutput.STREAM_CODEC);
        this.type = type;
    }

    @Override
    public MachineResourceType<ItemResource> resourceType() {
        return type;
    }

    public RecipeInputUse<TopoItemInput.Resource> in(Supplier<? extends ItemLike> item, long amount) {
        return inputUse(exactInput(TopoItemInput.of(item, amount)));
    }

    public RecipeOutputUse<TopoItemOutput> out(Supplier<? extends ItemLike> item, long amount) {
        return outputUse(TopoItemOutput.of(item, amount));
    }

    @Override
    public boolean matchInput(@Nullable MachineBlockEntity machine, List<TopoItemInput.Resource> contents) {
        try (Transaction simulation = Transaction.openRoot()) {
            return transferInputs(machine, contents, simulation);
        }
    }

    @Override
    public boolean matchOutput(@Nullable MachineBlockEntity machine, List<TopoItemOutput> contents) {
        try (Transaction simulation = Transaction.openRoot()) {
            return transferOutputs(machine, contents, simulation);
        }
    }

    @Override
    public boolean handleInputChecked(@Nullable MachineBlockEntity machine, List<TopoItemInput.Resource> contents, Transaction transaction) {
        return transferInputs(machine, contents, transaction);
    }

    @Override
    public boolean handleOutputChecked(@Nullable MachineBlockEntity machine, List<TopoItemOutput> contents, Transaction transaction) {
        return transferOutputs(machine, contents, transaction);
    }

    private static TopoItemInput.Resource exactInput(TopoItemInput input) {
        if (input instanceof TopoItemInput.Resource resource) return resource;
        throw new IllegalArgumentException("This capability accepts exact item inputs");
    }

    private boolean transferInputs(@Nullable MachineBlockEntity machine, List<TopoItemInput.Resource> contents, Transaction parent) {
        if (contents.isEmpty()) return true;
        if (machine == null) return false;
        ResourceHandler<ItemResource> handler = machine.machineComponents().resources().recipeSide().handler(type, RecipeRole.INPUT);
        if (handler == null) return false;
        try (Transaction batch = Transaction.open(parent)) {
            for (TopoItemInput.Resource content : contents) {
                if (ResourceHandlerLongOps.extract(handler, content.resource(), content.count(), batch) != content.count()) return false;
            }
            batch.commit();
            return true;
        }
    }

    private boolean transferOutputs(@Nullable MachineBlockEntity machine, List<TopoItemOutput> contents, Transaction parent) {
        if (contents.isEmpty()) return true;
        if (machine == null) return false;
        ResourceHandler<ItemResource> handler = machine.machineComponents().resources().recipeSide()
                .handler(type, RecipeRole.OUTPUT);
        if (handler == null) return false;
        try (Transaction batch = Transaction.open(parent)) {
            for (TopoItemOutput content : contents) {
                long moved = ResourceHandlerLongOps.insert(handler, content.resource(), content.count(), batch);
                if (moved != content.count()) return false;
            }
            batch.commit();
            return true;
        }
    }

    @Override
    public TopoItemInput.Resource scaleInput(TopoItemInput.Resource content, double factor) {
        return exactInput(content.withScaledCount(factor));
    }

    @Override
    public TopoItemOutput scaleOutput(TopoItemOutput content, double factor) {
        return content.withScaledCount(factor);
    }

    @Override
    public TopoItemInput.Resource scaleInputForParallel(TopoItemInput.Resource content, long factor) {
        return exactInput(content.withParallelCount(factor));
    }

    @Override
    public TopoItemOutput scaleOutputForParallel(TopoItemOutput content, long factor) {
        return content.withParallelCount(factor);
    }

    @Override
    public long maxParallelScaleInput(TopoItemInput.Resource content) {
        return Long.MAX_VALUE / content.count();
    }

    @Override
    public long maxParallelScaleOutput(TopoItemOutput content) {
        return Long.MAX_VALUE / content.count();
    }

    @Override
    public boolean hasAnyContent(@Nullable MachineBlockEntity machine) {
        if (machine == null) return false;
        ResourceHandler<ItemResource> handler = machine.machineComponents().resources().recipeSide().handler(type, RecipeRole.INPUT);
        if (handler == null) return false;
        for (int slot = 0; slot < handler.size(); slot++) if (handler.getAmountAsLong(slot) > 0) return true;
        return false;
    }

    @Override
    public boolean isStableForResourceContentVersion() {
        return true;
    }

    @Override
    public int countPreviewSlots(ResourcePortMetadata port) {
        return ((ItemPort.Metadata) port).slots();
    }

    @Override
    public UIElement createPreviewInputSlotWidget(TopoItemInput.@Nullable Resource content) {
        ItemSlot slot = MachineUiComponentTemplate.INSTANCE.createItemSlot();
        if (content != null) {
            slot.setItem(content.displayStacks().getFirst());
            ItemSlot.JEISupport.recipeIngredient(slot, IngredientIO.INPUT, () -> content.displayStacks().stream());
            ItemSlot.JEISupport.recipeSlot(slot, IngredientIO.INPUT, () -> content.displayStacks().stream());
        }
        return slot;
    }

    @Override
    public UIElement createPreviewOutputSlotWidget(@Nullable TopoItemOutput content) {
        return preview(content, IngredientIO.OUTPUT);
    }

    private ItemSlot preview(@Nullable TopoItemOutput content, IngredientIO io) {
        ItemSlot slot = MachineUiComponentTemplate.INSTANCE.createItemSlot();
        if (content != null) {
            ItemStack stack = content.stack();
            slot.setItem(stack);
            ItemSlot.JEISupport.recipeIngredient(slot, io, () -> Stream.of(stack));
            ItemSlot.JEISupport.recipeSlot(slot, io, () -> Stream.of(stack));
        }
        return slot;
    }

    @Override
    public UIElement createLiveSlotWidget(ResourcePort<?, ItemResource> port, int slot) {
        PlayerAccess access = port.playerSlotAccess();
        return MachineUiComponentTemplate.INSTANCE.createItemSlot(new ItemResourceHandlerSlot(port.handler(), slot)
                .setCanPlace(stack -> access.canPlace() && port.metadata().accepts(ItemResource.of(stack)))
                .setCanTake(player -> access.canTake()));
    }
}
