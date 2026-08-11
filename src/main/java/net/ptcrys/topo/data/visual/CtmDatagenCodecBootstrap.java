package net.ptcrys.topo.data.visual;

import net.ptcrys.topo.api.visual.CtmBlockStateModelCodecs;

import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.client.model.block.BlockStateModelHooks;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

import com.mojang.serialization.MapCodec;

/**
 * Datagen-side CTM codec registration. The {@code RegisterBlockStateModels} client event does not
 * fire during datagen, but serializing a custom blockstate model still resolves its codec through
 * {@link BlockStateModelHooks}' late-bound id mapper — so the datagen path injects the codecs into
 * that mapper directly (reflectively; the mapper has no public registration seam). Ported from the
 * old GTOdyssey datagen bootstrap.
 */
final class CtmDatagenCodecBootstrap {

    private static boolean registered;

    private CtmDatagenCodecBootstrap() {}

    @SuppressWarnings("unchecked")
    static synchronized void ensureRegistered() {
        if (registered) {
            return;
        }
        try {
            var field = BlockStateModelHooks.class.getDeclaredField("BLOCK_STATE_MODEL_IDS");
            field.setAccessible(true);
            var mapper = (ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends CustomUnbakedBlockStateModel>>) field.get(null);
            CtmBlockStateModelCodecs.register(mapper::put);
            registered = true;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to register CTM model codecs for datagen", e);
        }
    }
}
