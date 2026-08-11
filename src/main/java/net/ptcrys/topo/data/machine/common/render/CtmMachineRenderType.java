package net.ptcrys.topo.data.machine.common.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.topo.api.api.visual.ConnectedTextureFamily;
import net.ptcrys.topo.api.api.visual.ConnectedTextureSkin;
import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.render.MachineBlockRenderStrategy;
import net.ptcrys.topo.api.machine.render.MachineBlockRenderType;
import net.ptcrys.topo.api.machine.render.MachineBlockRenderUse;
import net.ptcrys.topo.data.data.visual.ConnectedTextureModelDatagen;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Connected-texture machine render: the CTM casing shell merges with same-family neighbor blocks
 * while a facing-following front overlay swaps between idle and active art at render time (one
 * dynamic blockstate model, no per-state variants). The block class must join the texture family
 * itself (e.g. {@code ConnectedTextureFormedMachineBlock}) so neighbors connect back to it.
 */
public final class CtmMachineRenderType extends MachineBlockRenderType<CtmMachineRenderType.Data> {

    public CtmMachineRenderType(Identifier id) {
        super(id);
    }

    public MachineBlockRenderUse<Data> ctm(
                                           ConnectedTextureSkin skin,
                                           Identifier idleFront,
                                           Identifier activeFront) {
        Objects.requireNonNull(skin, "connected texture skin");
        return use(new Data(skin.family(), skin.base(), skin.ctm(), idleFront, activeFront));
    }

    public static final class Strategy implements MachineBlockRenderStrategy<Data> {

        @Override
        public void validate(Data data) {
            Objects.requireNonNull(data, "ctm machine render data");
            Objects.requireNonNull(data.family(), "connected texture family");
            Objects.requireNonNull(data.base(), "base texture");
            Objects.requireNonNull(data.ctm(), "ctm texture");
            Objects.requireNonNull(data.idleFront(), "idle front texture");
            Objects.requireNonNull(data.activeFront(), "active front texture");
        }

        @Override
        public void applyBlockModel(
                                    BlockBuilder<? extends MachineBlock, RegistryCore> builder,
                                    Data data,
                                    MachineDefinition definition) {
            builder.blockstate(ConnectedTextureModelDatagen.machineBlockstate(
                    data.family().id(),
                    data.base(),
                    data.ctm(),
                    data.idleFront(),
                    data.activeFront()));
        }
    }

    public record Data(
                       ConnectedTextureFamily family,
                       Identifier base,
                       Identifier ctm,
                       Identifier idleFront,
                       Identifier activeFront) {}
}
