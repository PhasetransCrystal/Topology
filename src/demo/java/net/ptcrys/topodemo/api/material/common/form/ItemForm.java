package net.ptcrys.topodemo.api.material.common.form;

import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.api.lang.TemplateDisplayName;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormStrategy;
import net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Parameters belong to the declaration; this strategy only generates material content. */
public final class ItemForm extends MaterialFormStrategy {

    private final String registryPath;
    private final TemplateDisplayName displayName;
    private final TextureRef texture;
    private final Supplier<ResourceKey<CreativeModeTab>> creativeTab;

    private ItemForm(Builder builder) {
        super(List.of());
        requirePattern(builder.registryPath, "registryPath");
        this.registryPath = builder.registryPath;
        this.displayName = Objects.requireNonNull(builder.displayName, "displayName(en, cn) required");
        this.texture = Objects.requireNonNull(builder.texture, "texture required");
        this.creativeTab = Objects.requireNonNull(builder.creativeTab, "creativeTab required");
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
        requireMaterialData(material, form, List.of(BuiltinMaterialDataTypes.MASS));
    }

    @Override
    public void register(MaterialContentContext context, MaterialForm form) {
        String path = registryPath.formatted(context.material().id().getPath());
        var builder = context.core().item(path).modelTexture(texture).addTab(creativeTab.get());
        RegistryDisplayLang.applyItem(builder, context.core(), path, displayName.resolve(context.material()));
        builder.register();
    }

    public static final class Builder {

        private String registryPath;
        private TemplateDisplayName displayName;
        private TextureRef texture;
        private Supplier<ResourceKey<CreativeModeTab>> creativeTab;

        private Builder() {}

        public Builder registryPath(String pattern) {
            this.registryPath = pattern;
            return this;
        }

        public Builder displayName(String en, String cn) {
            this.displayName = DisplayNames.template(en, cn);
            return this;
        }

        public Builder texture(TextureRef texture) {
            this.texture = texture;
            return this;
        }

        public Builder creativeTab(Supplier<ResourceKey<CreativeModeTab>> tab) {
            this.creativeTab = tab;
            return this;
        }

        public ItemForm build() {
            return new ItemForm(this);
        }
    }
}
