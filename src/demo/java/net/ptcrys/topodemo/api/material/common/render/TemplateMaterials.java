package net.ptcrys.topodemo.api.material.common.render;

import net.ptcrys.topodemo.api.TopoDemoApiPlugin;

import net.minecraft.client.resources.model.sprite.Material;

/** 将 Demo 模板纹理路径转换为原版模型材质。 */
final class TemplateMaterials {

    private TemplateMaterials() {}

    static Material templateMaterial(String texturePath) {
        return new Material(TopoDemoApiPlugin.INSTANCE.material().id(texturePath));
    }
}
