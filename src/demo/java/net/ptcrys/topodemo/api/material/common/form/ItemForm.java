package net.ptcrys.topodemo.api.material.common.form;

import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormStrategy;
import net.ptcrys.topodemo.api.material.BuiltinDemoMaterialDataTypes;

import net.minecraft.world.item.CreativeModeTabs;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A reusable item-form strategy; declarations supply paths, names and vanilla textures. */
public final class ItemForm extends MaterialFormStrategy {

    private final String registryPath;
    private final String nameEn;
    private final String nameCn;
    private final TextureRef texture;

    public ItemForm(String registryPath, String nameEn, String nameCn, TextureRef texture) {
        super(List.of());
        requirePattern(registryPath, "registryPath");
        requirePattern(nameEn, "nameEn");
        requirePattern(nameCn, "nameCn");
        this.registryPath = registryPath;
        this.nameEn = nameEn;
        this.nameCn = nameCn;
        this.texture = Objects.requireNonNull(texture, "texture");
    }

    @Override
    public String registryPath() {
        return registryPath;
    }

    @Override
    public void validateMaterial(Material material, MaterialForm form) {
        requireMaterialData(material, form, List.of(BuiltinDemoMaterialDataTypes.MASS));
    }

    @Override
    public void register(MaterialContentContext context, MaterialForm form) {
        Material material = context.material();
        context.core().item(registryPath.formatted(material.id().getPath()))
                .lang(Map.of("en_us", nameEn.formatted(material.displayNameEn()),
                        "zh_cn", nameCn.formatted(material.displayNameCn())))
                .modelTexture(texture)
                .addTab(CreativeModeTabs.INGREDIENTS)
                .register();
    }
}
