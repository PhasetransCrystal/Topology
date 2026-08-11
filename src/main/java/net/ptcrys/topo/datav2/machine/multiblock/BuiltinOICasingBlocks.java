package net.ptcrys.topo.datav2.machine.multiblock;

import net.ptcrys.registrylib.util.entry.BlockEntry;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.visual.ConnectedTextureBlock;
import net.ptcrys.topo.api.visual.ConnectedTextureFamily;
import net.ptcrys.topo.api.visual.ConnectedTextureSkin;
import net.ptcrys.topo.apiv2.lang.DisplayNames;
import net.ptcrys.topo.apiv2.lang.RegistryDisplayLang;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.data.visual.ConnectedTextureModelDatagen;
import net.ptcrys.topo.datav2.OfficialOIPlugin;

import net.minecraft.world.level.block.Blocks;

/**
 * Builtin multiblock casing blocks: connected-texture full cubes whose casing surface merges across
 * same-family neighbors (controller included). Blueprints reference them through the lazy
 * {@code CellPredicates.block(Supplier)} form (blocks resolve after the registry event).
 *
 * <p>
 * Design id {@code dense_structure_casing} from {@code docs/design/machine_construction.md}.
 */
public final class BuiltinOICasingBlocks {

    /**
     * Dense structure-casing surface: identity family (casing blocks + hatches + the large-grinder
     * controller) atomically paired with its base/CTM textures. Machines wearing this shell pass the
     * skin to their render contribution; the family token rides inside it.
     */
    public static final ConnectedTextureSkin DENSE_STRUCTURE_CASING_SKIN = new ConnectedTextureSkin(
            new ConnectedTextureFamily(OfficialOIPlugin.INSTANCE.machine().id("dense_structure_casing")),
            OfficialOIPlugin.INSTANCE.machine().id("block/casings/multiblock/dense_structure_casing"),
            OfficialOIPlugin.INSTANCE.machine().id("block/casings/multiblock/dense_structure_casing_ctm"));

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
                        .addTab(BuiltinOICreativeTabs.MACHINES.getKey())
                        .model(() -> (item, prov) -> prov.createWithExistingModel(
                                item,
                                OfficialOIPlugin.INSTANCE.machine().id("block/dense_structure_casing"))));
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

    private BuiltinOICasingBlocks() {}

    /** Only activates class initialization; the bootstrap orders this before the machine catalogs. */
    public static void init() {}
}
