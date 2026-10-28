package net.ptcrys.topodemo.api.machine.common;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.render.*;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Typed vanilla-texture render: no assets from the former gameplay mod are required. */
public final class CubeRender extends MachineBlockRenderType<TextureRef> {

    public CubeRender(Identifier id) {
        super(id);
    }

    public MachineBlockRenderUse<TextureRef> cube(String texture) {
        return use(TextureRef.mc(texture));
    }

    public static final class Strategy implements MachineBlockRenderStrategy<TextureRef> {

        @Override
        public void validate(TextureRef texture) {
            Objects.requireNonNull(texture);
        }

        @Override
        public void applyBlockModel(BlockBuilder<? extends MachineBlock, RegistryCore> builder, TextureRef texture, MachineDefinition definition) {
            builder.modelTexture(texture);
        }
    }
}
