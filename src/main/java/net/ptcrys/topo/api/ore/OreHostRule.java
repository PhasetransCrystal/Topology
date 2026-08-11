package net.ptcrys.topo.api.ore;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.material.form.MaterialForm;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Maps a replaceable host-stone tag to the ore form generated in it, plus a display name for JEI.
 * Pure value: form is a strong {@link MaterialForm} handle resolved to a block per material at
 * placement time.
 */
public record OreHostRule(TagKey<Block> replaceableTag, MaterialForm generatedForm, LangKey displayName) {

    public OreHostRule {
        Objects.requireNonNull(replaceableTag, "replaceableTag");
        Objects.requireNonNull(generatedForm, "generatedForm");
        Objects.requireNonNull(displayName, "displayName");
    }

    public boolean matches(BlockState state) {
        return state.is(replaceableTag);
    }
}
