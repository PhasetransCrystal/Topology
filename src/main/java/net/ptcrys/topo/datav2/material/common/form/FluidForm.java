package net.ptcrys.topo.datav2.material.common.form;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.FluidBuilder;
import net.ptcrys.topo.api.lang.ChineseConvert;
import net.ptcrys.topo.apiv2.lang.DisplayNames;
import net.ptcrys.topo.apiv2.lang.FixedDisplayName;
import net.ptcrys.topo.apiv2.lang.TemplateDisplayName;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.MaterialContentContext;
import net.ptcrys.topo.apiv2.material.MaterialFormOptions;
import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.form.FormDataUse;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.form.MaterialFormStrategy;
import net.ptcrys.topo.datav2.material.common.RgbColorData;
import net.ptcrys.topo.helper.TagHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Standard fluid form: registers one source/flowing fluid pair per material. */
public final class FluidForm extends MaterialFormStrategy {

    private final String registryPath;
    private final TemplateDisplayName displayName;
    private final String commonTag;
    private final ResourceKey<CreativeModeTab> creativeTab;
    private final Identifier stillTexture;
    private final Identifier flowingTexture;
    private final MaterialDataType<RgbColorData> tintData;
    private final Consumer<FluidType.Properties> typeProperties;
    private final Consumer<BaseFlowingFluid.Properties> flowProperties;

    private FluidForm(Builder builder) {
        super(builder.data);
        this.registryPath = builder.registryPath;
        this.displayName = builder.displayName;
        this.commonTag = builder.commonTag;
        this.creativeTab = builder.creativeTab;
        this.stillTexture = builder.stillTexture;
        this.flowingTexture = builder.flowingTexture;
        this.tintData = builder.tintData;
        this.typeProperties = builder.typeProperties;
        this.flowProperties = builder.flowProperties;
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
                            "material " + material.id() + " declares an item override for fluid form " + form.id() + "; fluid forms do not accept item overrides");
                });
        material.strategy().optionsFor(form)
                .flatMap(MaterialFormOptions::blockOverride)
                .ifPresent(target -> {
                    throw new IllegalStateException(
                            "material " + material.id() + " declares a block override for fluid form " + form.id() + "; fluid forms do not accept block overrides");
                });
        requireMaterialData(material, form, List.of(tintData));
    }

    @Override
    public void register(MaterialContentContext context, MaterialForm form) {
        String materialPath = context.material().id().getPath();
        String entryPath = String.format(registryPath, materialPath);
        RegistryCore core = context.core();
        var groupTag = TagHelper.fluid(commonTag);
        var materialTag = TagHelper.fluidMaterial(commonTag, materialPath);
        int tint = context.material().strategy().data(tintData)
                .map(RgbColorData::rgb)
                .map(FluidForm::opaqueArgb)
                .orElse(0xFFFFFFFF);

        FixedDisplayName resolved = displayName.resolve(context.material());
        var builder = FluidBuilder.create(core, core, entryPath, FluidType::new, BaseFlowingFluid.Flowing::new)
                .clientExtension(stillTexture, flowingTexture, tint)
                .lang(resolved.en())
                .defaultBucketTab(creativeTab)
                .properties(typeProperties)
                .fluidProperties(flowProperties)
                .tag(groupTag, materialTag);

        if (core.doDatagen()) {
            builder.lang(Map.of(
                    "zh_cn", resolved.cn(),
                    "zh_tw", ChineseConvert.s2t(resolved.cn())));
        }

        builder.register();
        // Fluid id namespace follows the material owner, not a hard-coded host mod id.
        Identifier fluidId = Identifier.fromNamespaceAndPath(
                context.material().id().getNamespace(), entryPath);
        core.fluidTags()
                .addIds(groupTag, fluidId)
                .addIds(materialTag, fluidId);
    }

    static int opaqueArgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    void applyTypeProperties(FluidType.Properties properties) {
        typeProperties.accept(properties);
    }

    void applyFlowProperties(BaseFlowingFluid.Properties properties) {
        flowProperties.accept(properties);
    }

    public static final class Builder {

        private String registryPath;
        private TemplateDisplayName displayName;
        private String commonTag;
        private ResourceKey<CreativeModeTab> creativeTab;
        private Identifier stillTexture;
        private Identifier flowingTexture;
        private MaterialDataType<RgbColorData> tintData;
        private Consumer<FluidType.Properties> typeProperties = properties -> {};
        private Consumer<BaseFlowingFluid.Properties> flowProperties = properties -> {};
        private final List<FormDataUse<?>> data = new ArrayList<>();

        private Builder() {}

        /** A typed payload for this form; AMOUNT is optional and only used by unit-conversion forms. */
        public Builder data(FormDataUse<?> use) {
            this.data.add(Objects.requireNonNull(use, "use"));
            return this;
        }

        public Builder registryPath(String pattern) {
            this.registryPath = pattern;
            return this;
        }

        /**
         * Unique product entry for fluid instance names. Both en/cn patterns required ({@code %s} =
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

        public Builder texture(String path) {
            // Form template textures live on the form-owner (official material domain) pack.
            Identifier texture = net.ptcrys.topo.datav2.OfficialOIPlugin.INSTANCE.material().id(path);
            this.stillTexture = texture;
            this.flowingTexture = texture;
            return this;
        }

        public Builder tint(MaterialDataType<RgbColorData> tintData) {
            this.tintData = tintData;
            return this;
        }

        public Builder properties(Consumer<FluidType.Properties> consumer) {
            this.typeProperties = this.typeProperties.andThen(Objects.requireNonNull(consumer, "consumer"));
            return this;
        }

        public Builder fluidProperties(Consumer<BaseFlowingFluid.Properties> consumer) {
            this.flowProperties = this.flowProperties.andThen(Objects.requireNonNull(consumer, "consumer"));
            return this;
        }

        public FluidForm build() {
            requirePattern(registryPath, "registryPath");
            Objects.requireNonNull(displayName, "fluid form requires displayName(en, cn)");
            Objects.requireNonNull(commonTag, "fluid form requires commonTag");
            Objects.requireNonNull(creativeTab, "fluid form requires creativeTab");
            Objects.requireNonNull(stillTexture, "fluid form requires texture");
            Objects.requireNonNull(flowingTexture, "fluid form requires flowingTexture");
            Objects.requireNonNull(tintData, "fluid form requires tint data");
            return new FluidForm(this);
        }
    }
}
