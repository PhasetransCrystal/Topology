package net.ptcrys.topo.api.pipe;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.builders.ItemBuilder;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Strategy socket for everything data-flavoured about a pipe's block/item registration: base
 * properties and style, tags, loot, the generated blockstate/model set and the item display
 * model. The standard implementation lives in {@code data/pipe/common}; {@link PipeDefinition}
 * drives the template during the bootstrap block pass (Hollywood principle) and appends the
 * framework invariants — {@code noOcclusion} (multipart visual) and piston
 * {@code PushReaction.BLOCK} (SavedData authority cannot survive untracked moves) — after
 * {@link #styleBlockProperties}.
 */
public interface PipeBlockTemplate {

    /** Style pass over the block properties (strength, sound, ...); invariants come after. */
    BlockBehaviour.Properties styleBlockProperties(BlockBehaviour.Properties properties);

    /** Registration wiring: base-copy properties, tags, loot, blockstate and model datagen. */
    void configureBlock(BlockBuilder<PipeBlock, RegistryCore> builder, PipeDefinition definition);

    /** Item-side wiring: display-model binding plus any item datagen. */
    void configureItem(ItemBuilder<BlockItem, ?> item, PipeDefinition definition);

    /** Templates sharing a group contribute {@link #contributeShared} once per block pass. */
    default Object sharedGroup() {
        return this;
    }

    /** One-shot shared datagen (e.g. parent models); invoked once per {@link #sharedGroup}. */
    default void contributeShared(RegistryCore core) {}
}
