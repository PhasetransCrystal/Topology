package net.ptcrys.topo.data.machine.multiblock;

import net.ptcrys.registrylib.util.entry.BlockEntry;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.visual.ConnectedTextureBlock;
import net.ptcrys.topo.api.api.visual.ConnectedTextureFamily;
import net.ptcrys.topo.api.api.visual.ConnectedTextureSkin;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.data.visual.ConnectedTextureModelDatagen;

import net.minecraft.world.level.block.Blocks;

/**
 * Builtin multiblock casing blocks: connected-texture full cubes whose casing surface merges across
 * same-family neighbors (controller included). Blueprints reference them through the lazy
 * {@code CellPredicates.block(Supplier)} form (blocks resolve after the registry event).
 *
 * <p>
 * Design id {@code dense_structure_casing} from {@code docs/design/machine_construction.md}.
 */
public final class BuiltinTopoCasingBlocks {

    /**
     * Dense structure-casing surface: identity family (casing blocks + hatches + the large-grinder
     * controller) atomically paired with its base/CTM textures. Machines wearing this shell pass the
     * skin to their render contribution; the family token rides inside it.
     */
    public static final ConnectedTextureSkin DENSE_STRUCTURE_CASING_SKIN = new ConnectedTextureSkin(
            new ConnectedTextureFamily(OfficialTopoPlugin.INSTANCE.machine().id("dense_structure_casing")),
            OfficialTopoPlugin.INSTANCE.machine().id("block/casings/multiblock/dense_structure_casing"),
            OfficialTopoPlugin.INSTANCE.machine().id("block/casings/multiblock/dense_structure_casing_ctm"));

    /** @deprecated use {@link #DENSE_STRUCTURE_CASING_SKIN} */
    @Deprecated
    public static final ConnectedTextureSkin GRINDER_CASING_SKIN = DENSE_STRUCTURE_CASING_SKIN;

    public static final BlockEntry<ConnectedTextureBlock> DENSE_STRUCTURE_CASING = denseStructureCasing();

    private static BlockEntry<ConnectedTextureBlock> denseStructureCasing() {
        var builder = Topology.REGISTRY
                .block(
                        "dense_structure_casing",
                        properties -> new ConnectedTextureBlock(properties, DENSE_STRUCTURE_CASING_SKIN.family()))
                .initialProperties(Blocks.IRON_BLOCK)
                .blockstate(ConnectedTextureModelDatagen.casingBlockstate(
                        DENSE_STRUCTURE_CASING_SKIN.base(), DENSE_STRUCTURE_CASING_SKIN.ctm()))
                .defaultLoot()
                .item(casingItem -> casingItem
                        .addTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                        .model(() -> (item, prov) -> prov.createWithExistingModel(
                                item,
                                OfficialTopoPlugin.INSTANCE.machine().id("block/dense_structure_casing"))));
        RegistryDisplayLang.applyBlock(
                builder,
                Topology.REGISTRY,
                "dense_structure_casing",
                DisplayNames.fixed("Dense Structure Casing", "致密结构机壳"));
        return builder.register();
    }

    /** @deprecated use {@link #DENSE_STRUCTURE_CASING} */
    @Deprecated
    public static final BlockEntry<ConnectedTextureBlock> GRINDER_CASING = DENSE_STRUCTURE_CASING;

    private BuiltinTopoCasingBlocks() {}

    /** Only activates class initialization; the bootstrap orders this before the machine catalogs. */
    public static void init() {}
}
