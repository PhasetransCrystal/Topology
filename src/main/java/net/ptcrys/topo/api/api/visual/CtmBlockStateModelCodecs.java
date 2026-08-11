package net.ptcrys.topo.api.api.visual;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

import com.mojang.serialization.MapCodec;

import java.util.function.BiConsumer;

/** Single registration list of every CTM blockstate-model codec (client event + datagen bootstrap). */
public final class CtmBlockStateModelCodecs {

    private CtmBlockStateModelCodecs() {}

    public static void register(
                                BiConsumer<Identifier, MapCodec<? extends CustomUnbakedBlockStateModel>> registrar) {
        registrar.accept(ConnectedTextureCasingModel.LOADER, ConnectedTextureCasingModel.CODEC);
        registrar.accept(ConnectedTextureMachineModel.LOADER, ConnectedTextureMachineModel.CODEC);
        registrar.accept(ConnectedTextureHatchModel.LOADER, ConnectedTextureHatchModel.CODEC);
    }
}
