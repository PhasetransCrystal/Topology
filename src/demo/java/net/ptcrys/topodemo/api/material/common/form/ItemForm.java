package net.ptcrys.topodemo.api.material.common.form;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.topo.api.api.registration.ExternalItemTarget;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.api.lang.TemplateDisplayName;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialFormOptions;
import net.ptcrys.topo.api.material.MaterialFormTargets;
import net.ptcrys.topo.api.material.form.FormDataUse;
import net.ptcrys.topo.api.material.form.MaterialContentKey;
import net.ptcrys.topo.api.material.form.MaterialContentTypes;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormPlan;
import net.ptcrys.topo.api.material.form.MaterialFormStrategy;
import net.ptcrys.topo.api.material.render.MaterialItemRender;
import net.ptcrys.topo.helper.TagHelper;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 标准物品形态(骨架):每个引用它的材料注册一个物品,附常规标签、双语名与创造栏条目。
 * 行为决策全部是 builder 旋钮;非常规形态直接实现 {@link MaterialFormStrategy}。
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
    public MaterialFormPlan plan(Material material, MaterialForm form) {
        MaterialFormTargets targets = material.optionsFor(form)
                .map(MaterialFormOptions::targets)
                .orElseGet(MaterialFormTargets::empty);
        ExternalItemTarget override = targets.item().orElse(null);
        List<MaterialContentKey> outputs = outputKeys(material, override);
        return MaterialFormPlan.of(
                outputs,
                targets,
                () -> validateForm(material, form, targets),
                context -> registerForm(context, form, override));
    }

    private List<MaterialContentKey> outputKeys(Material material, @Nullable ExternalItemTarget override) {
        if (override != null) {
            try {
                return List.of(new MaterialContentKey(MaterialContentTypes.ITEM, override.id()));
            } catch (RuntimeException unavailable) {
                return List.of();
            }
        }
        return List.of(nativeContentKey(material, MaterialContentTypes.ITEM));
    }

    private void validateForm(Material material, MaterialForm form, MaterialFormTargets targets) {
        targets.block().ifPresent(target -> {
            throw new IllegalStateException(
                    "material " + material.id() + " declares a block override for item form " + form.id() + "; item forms only accept overrideItem");
        });
        targets.fluid().ifPresent(target -> {
            throw new IllegalStateException(
                    "material " + material.id() + " declares a fluid override for item form " + form.id() + "; item forms only accept overrideItem");
        });
        if (render != null) {
            requireMaterialData(material, form, render.requiredMaterialData());
        }
    }

    private void registerForm(MaterialContentContext context, MaterialForm form, @Nullable ExternalItemTarget override) {
        String materialPath = context.material().id().getPath();
        RegistryCore core = context.core();
        // The conventional tags are the same for both paths; only how they are attached differs.
        var itemGroupTag = TagHelper.item(commonTag);
        var itemMaterialTag = TagHelper.itemMaterial(commonTag, materialPath);

        if (override != null) {
            // External items may not be bound yet. Prefer id-based tag entries and resolve the
            // creative-tab item only when the tab event fires.
            if (override.explicitId().isPresent()) {
                core.itemTags().addIds(itemGroupTag, override.id()).addIds(itemMaterialTag, override.id());
            } else {
                TagHelper.addItemTag(core, itemGroupTag, override::id);
                TagHelper.addItemTag(core, itemMaterialTag, override::id);
            }
            core.addExistingSupplierToTab(creativeTab, override.itemSupplier());
            return;
        }

        // A native item: attach tags + tab through the builder so they defer to registration (the
        // entry is not bound yet during this construction-phase pass).
        String entryPath = String.format(registryPath, materialPath);
        ItemBuilder<Item, RegistryCore> builder = core.item(entryPath);
        RegistryDisplayLang.applyItem(builder, core, entryPath, displayName.resolve(context.material()));
        if (render != null) {
            render.apply(builder, context.material(), form);
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
