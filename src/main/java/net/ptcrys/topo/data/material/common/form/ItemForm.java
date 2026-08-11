package net.ptcrys.topo.data.material.common.form;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.api.api.registration.ExternalItemTarget;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.api.lang.TemplateDisplayName;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialFormOptions;
import net.ptcrys.topo.api.material.form.FormDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormStrategy;
import net.ptcrys.topo.api.material.render.MaterialItemRender;
import net.ptcrys.topo.helper.TagHelper;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Standard item form: registers one item per material with conventional tags, generated lang and a
 * creative tab entry. Every behavior decision is a builder knob; exotic forms implement
 * {@link MaterialFormStrategy} directly instead of growing flags here.
 */
public final class ItemForm extends MaterialFormStrategy {

    private final MaterialItemRender render;
    private final String registryPath;
    private final TemplateDisplayName displayName;
    private final String commonTag;
    private final ResourceKey<CreativeModeTab> creativeTab;

    private ItemForm(Builder builder) {
        super(builder.data);
        this.render = builder.render;
        this.registryPath = builder.registryPath;
        this.displayName = builder.displayName;
        this.commonTag = builder.commonTag;
        this.creativeTab = builder.creativeTab;
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
                .flatMap(MaterialFormOptions::blockOverride)
                .ifPresent(target -> {
                    throw new IllegalStateException(
                            "material " + material.id() + " declares a block override for item form " + form.id() + "; item forms only accept overrideItem");
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
        var itemGroupTag = TagHelper.item(commonTag);
        var itemMaterialTag = TagHelper.itemMaterial(commonTag, materialPath);

        ExternalItemTarget override = context.material().strategy().optionsFor(form)
                .flatMap(MaterialFormOptions::itemOverride)
                .orElse(null);
        if (override != null) {
            // A vanilla item stands in for an overridden form. It is already bound, so attach tags
            // and the creative tab through the existing-entry APIs.
            ItemEntry<Item> item = core.existingItem(override.id());
            core.itemTags().addSuppliers(itemGroupTag, item).addSuppliers(itemMaterialTag, item);
            core.addExistingSupplierToTab(creativeTab, item);
            return;
        }

        // A native item: attach tags + tab through the builder so they defer to registration (the
        // entry is not bound yet during this construction-phase pass).
        String entryPath = String.format(registryPath, materialPath);
        ItemBuilder<Item, RegistryCore> builder = core.item(entryPath);
        RegistryDisplayLang.applyItem(builder, core, entryPath, displayName.resolve(context.material()));
        if (render != null) {
            render.apply(builder, context.material());
        }
        builder.addTab(creativeTab);
        builder.addTag(itemGroupTag, itemMaterialTag);
        builder.register();
    }

    public static final class Builder {

        private MaterialItemRender render;
        private String registryPath;
        private TemplateDisplayName displayName;
        private String commonTag;
        private ResourceKey<CreativeModeTab> creativeTab;
        private final List<FormDataUse<?>> data = new ArrayList<>();

        private Builder() {}

        /** A typed payload for this form; AMOUNT is optional and only used by unit-conversion forms. */
        public Builder data(FormDataUse<?> use) {
            this.data.add(Objects.requireNonNull(use, "use"));
            return this;
        }

        /** Optional: a form without a render registers items with no generated model. */
        public Builder render(MaterialItemRender render) {
            this.render = render;
            return this;
        }

        public Builder registryPath(String pattern) {
            this.registryPath = pattern;
            return this;
        }

        /**
         * Unique product entry for item instance names. Both en/cn patterns required ({@code %s} =
         * material name). Do not pass constructed template objects (code-style §3.13).
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

        public ItemForm build() {
            requirePattern(registryPath, "registryPath");
            Objects.requireNonNull(displayName, "item form requires displayName(en, cn)");
            Objects.requireNonNull(commonTag, "item form requires commonTag");
            Objects.requireNonNull(creativeTab, "item form requires creativeTab");
            return new ItemForm(this);
        }
    }
}
