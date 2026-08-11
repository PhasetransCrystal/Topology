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
import net.ptcrys.topo.api.machine.render.MachineShellMaterial;
import net.ptcrys.topo.data.data.visual.ConnectedTextureModelDatagen;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Connected-texture hatch render: standalone the part wears {@code shell} with its pipe + resource
 * icon on the front; melted into a formed structure ({@code CTM_ACTIVE}) it renders the family's
 * CTM casing shell, keeping the front overlays so the port stays readable. The block class must
 * join the family itself ({@code ConnectedTextureOrientedMachineBlock}).
 */
public final class CtmHatchRenderType extends MachineBlockRenderType<CtmHatchRenderType.Data> {

    public CtmHatchRenderType(Identifier id) {
        super(id);
    }

    public MachineBlockRenderUse<Data> hatch(
                                             ConnectedTextureSkin skin,
                                             MachineShellMaterial shell,
                                             Identifier pipe,
                                             Identifier icon) {
        Objects.requireNonNull(skin, "connected texture skin");
        return use(new Data(skin.family(), shell, skin.base(), skin.ctm(), pipe, icon));
    }

    public static final class Strategy implements MachineBlockRenderStrategy<Data> {

        @Override
        public void validate(Data data) {
            Objects.requireNonNull(data, "ctm hatch render data");
            Objects.requireNonNull(data.family(), "connected texture family");
            Objects.requireNonNull(data.shell(), "standalone shell material");
            Objects.requireNonNull(data.formedBase(), "formed base texture");
            Objects.requireNonNull(data.formedCtm(), "formed ctm texture");
            Objects.requireNonNull(data.pipe(), "pipe texture");
            Objects.requireNonNull(data.icon(), "icon texture");
        }

        @Override
        public void applyBlockModel(
                                    BlockBuilder<? extends MachineBlock, RegistryCore> builder,
                                    Data data,
                                    MachineDefinition definition) {
            builder.blockstate(ConnectedTextureModelDatagen.hatchBlockstate(
                    data.family().id(),
                    data.shell().bottomTexture(),
                    data.shell().sideTexture(),
                    data.shell().topTexture(),
                    data.formedBase(),
                    data.formedCtm(),
                    data.pipe(),
                    data.icon()));
        }
    }

    public record Data(
                       ConnectedTextureFamily family,
                       MachineShellMaterial shell,
                       Identifier formedBase,
                       Identifier formedCtm,
                       Identifier pipe,
                       Identifier icon) {}
}
