package net.ptcrys.topo.data.material.common.form;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.util.entry.BlockEntry;
import net.ptcrys.topo.api.api.registration.ExternalBlockTarget;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.api.lang.TemplateDisplayName;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialFormOptions;
import net.ptcrys.topo.api.material.form.FormDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormStrategy;
import net.ptcrys.topo.api.material.render.MaterialBlockRender;
import net.ptcrys.topo.helper.TagHelper;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Standard block form: registers one block (and its item) per material with conventional tags,
 * generated lang and a creative tab entry. Every behavior decision is a builder knob; exotic forms
 * implement {@link MaterialFormStrategy} directly instead of growing flags here.
 *
 * <p>
 * Loot is the block dropping itself; a drop-other-form knob lands together with the
 * ore-processing chain.
 */
public final class BlockForm extends MaterialFormStrategy {

    private final MaterialBlockRender render;
    private final String registryPath;
    private final TemplateDisplayName displayName;
    private final String commonTag;
    private final ResourceKey<CreativeModeTab> creativeTab;
    private final Function<BlockBehaviour.Properties, Block> blockFactory;
    private final BiFunction<Block, Item.Properties, ? extends BlockItem> blockItemFactory;
    private final Block initialProperties;
    private final UnaryOperator<BlockBehaviour.Properties> properties;

    private BlockForm(Builder builder) {
        super(builder.data);
        this.render = builder.render;
        this.registryPath = builder.registryPath;
        this.displayName = builder.displayName;
        this.commonTag = builder.commonTag;
        this.creativeTab = builder.creativeTab;
        this.blockFactory = builder.blockFactory;
        this.blockItemFactory = builder.blockItemFactory;
        this.initialProperties = builder.initialProperties;
        this.properties = builder.properties;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String registryPath() {
        return registryPath;
    }

    @Override
    public void validateMaterial(Material material, MaterialForm form) {
        material.strategy().optionsFor(form)
                .flatMap(MaterialFormOptions::itemOverride)
                .ifPresent(target -> {
                    throw new IllegalStateException(
                            "material " + material.id() + " declares an item override for block form " + form.id() + "; block forms only accept overrideBlock" + " (the block's item is derived from it)");
                });
        if (render != null) {
            requireMaterialData(material, form, render.requiredMaterialData());
        }
    }

    @Override
    public void register(MaterialContentContext context, MaterialForm form) {
        String materialPath = context.material().id().getPath();
        RegistryCore core = context.core();
        // The conventional tags are the same for both paths; only how they are attached differs.
        var blockGroupTag = TagHelper.block(commonTag);
        var blockMaterialTag = TagHelper.blockMaterial(commonTag, materialPath);
        var itemGroupTag = TagHelper.item(commonTag);
        var itemMaterialTag = TagHelper.itemMaterial(commonTag, materialPath);

        ExternalBlockTarget override = context.material().strategy().optionsFor(form)
                .flatMap(MaterialFormOptions::blockOverride)
                .orElse(null);
        if (override != null) {
            // A vanilla block stands in for an overridden form. It is already bound, so attach tags
            // and the creative tab through the existing-entry APIs.
            BlockEntry<Block> block = core.existingBlock(override.id());
            core.blockTags().addSuppliers(blockGroupTag, block).addSuppliers(blockMaterialTag, block);
            core.itemTags().addSuppliers(itemGroupTag, block).addSuppliers(itemMaterialTag, block);
            core.addExistingSupplierToTab(creativeTab, block);
            return;
        }

        // A native block: attach tags + tab through the builder so they defer to registration (the
        // entry is not bound yet during this construction-phase pass).
        String entryPath = String.format(registryPath, materialPath);
        BlockBuilder<Block, RegistryCore> builder = core.block(entryPath, props -> blockFactory.apply(props));
        builder.initialProperties(initialProperties);
        if (properties != null) {
            builder.properties(props -> properties.apply(props));
        }
        RegistryDisplayLang.applyBlock(builder, core, entryPath, displayName.resolve(context.material()));
        if (render != null) {
            render.apply(builder, context.material());
        }
        builder.addTag(blockGroupTag, blockMaterialTag);
        builder.addItemTag(itemGroupTag, itemMaterialTag);
        builder.defaultLoot();
        if (blockItemFactory == null) {
            builder.item(item -> item.addTab(creativeTab));
        } else {
            builder.item(blockItemFactory, item -> item.addTab(creativeTab));
        }
        builder.register();
    }

    public static final class Builder {

        private MaterialBlockRender render;
        private String registryPath;
        private TemplateDisplayName displayName;
        private String commonTag;
        private ResourceKey<CreativeModeTab> creativeTab;
        private Function<BlockBehaviour.Properties, Block> blockFactory = Block::new;
        private BiFunction<Block, Item.Properties, ? extends BlockItem> blockItemFactory;
        private Block initialProperties = Blocks.STONE;
        private UnaryOperator<BlockBehaviour.Properties> properties;
        private final List<FormDataUse<?>> data = new ArrayList<>();

        private Builder() {}

        /** A typed payload for this form; AMOUNT is optional and only used by unit-conversion forms. */
        public Builder data(FormDataUse<?> use) {
            this.data.add(Objects.requireNonNull(use, "use"));
            return this;
        }

        /** Optional: a form without a render registers blocks with no generated model. */
        public Builder render(MaterialBlockRender render) {
            this.render = render;
            return this;
        }

        public Builder registryPath(String pattern) {
            this.registryPath = pattern;
            return this;
        }

        /**
         * Unique product entry for block instance names. Both en/cn patterns required ({@code %s} =
         * material name).
         */
        public Builder displayName(String enPattern, String cnPattern) {
            this.displayName = DisplayNames.template(enPattern, cnPattern);
            return this;
        }

        public Builder commonTag(String group) {
            this.commonTag = group;
            return this;
        }

        public Builder creativeTab(ResourceKey<CreativeModeTab> creativeTab) {
            this.creativeTab = creativeTab;
            return this;
        }

        /** Block construction; defaults to {@code Block::new}. */
        public Builder block(Function<BlockBehaviour.Properties, Block> factory) {
            this.blockFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        /** Optional block item factory; defaults to a normal placeable {@link BlockItem}. */
        public Builder blockItem(BiFunction<Block, Item.Properties, ? extends BlockItem> factory) {
            this.blockItemFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        /** Base properties copied from this block; defaults to {@link Blocks#STONE}. */
        public Builder initialProperties(Block source) {
            this.initialProperties = Objects.requireNonNull(source, "source");
            return this;
        }

        /** Modifier applied on top of the initial properties; defaults to none. */
        public Builder properties(UnaryOperator<BlockBehaviour.Properties> modifier) {
            this.properties = Objects.requireNonNull(modifier, "modifier");
            return this;
        }

        public BlockForm build() {
            requirePattern(registryPath, "registryPath");
            Objects.requireNonNull(displayName, "block form requires displayName(en, cn)");
            Objects.requireNonNull(commonTag, "block form requires commonTag");
            Objects.requireNonNull(creativeTab, "block form requires creativeTab");
            return new BlockForm(this);
        }
    }
}
