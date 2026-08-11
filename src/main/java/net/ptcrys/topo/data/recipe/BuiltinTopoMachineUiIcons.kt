package net.ptcrys.topo.data.recipe

import net.ptcrys.topo.api.machine.ui.MachineUiIcons

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture

object BuiltinTopoMachineUiIcons {
    @JvmStatic
    fun chest(color: Int): IGuiTexture = MachineUiIcons.pixelGlyph(
        color,
        floatArrayOf(3f, 4f, 8f, 1f),
        floatArrayOf(3f, 10f, 8f, 1f),
        floatArrayOf(3f, 5f, 1f, 5f),
        floatArrayOf(10f, 5f, 1f, 5f),
        floatArrayOf(4f, 7f, 6f, 1f),
        floatArrayOf(6f, 6f, 2f, 2f),
    )

    @JvmStatic
    fun drop(color: Int): IGuiTexture = MachineUiIcons.pixelGlyph(
        color,
        floatArrayOf(6f, 2f, 2f, 2f),
        floatArrayOf(5f, 4f, 4f, 2f),
        floatArrayOf(4f, 6f, 6f, 3f),
        floatArrayOf(5f, 9f, 4f, 1f),
    )

    @JvmStatic
    fun bolt(color: Int): IGuiTexture = MachineUiIcons.pixelGlyph(
        color,
        floatArrayOf(7f, 2f, 2f, 1f),
        floatArrayOf(6f, 3f, 2f, 1f),
        floatArrayOf(5f, 4f, 2f, 1f),
        floatArrayOf(4f, 5f, 5f, 1f),
        floatArrayOf(6f, 6f, 3f, 1f),
        floatArrayOf(6f, 7f, 2f, 1f),
        floatArrayOf(5f, 8f, 2f, 1f),
        floatArrayOf(4f, 9f, 2f, 1f),
        floatArrayOf(4f, 10f, 1f, 1f),
    )

    @JvmStatic
    fun spark(color: Int): IGuiTexture = MachineUiIcons.pixelGlyph(
        color,
        floatArrayOf(6f, 3f, 2f, 3f),
        floatArrayOf(6f, 8f, 2f, 3f),
        floatArrayOf(3f, 6f, 3f, 2f),
        floatArrayOf(8f, 6f, 3f, 2f),
        floatArrayOf(6f, 6f, 2f, 2f),
        floatArrayOf(4f, 4f, 1f, 1f),
        floatArrayOf(9f, 4f, 1f, 1f),
        floatArrayOf(4f, 9f, 1f, 1f),
        floatArrayOf(9f, 9f, 1f, 1f),
    )

    @JvmStatic
    fun flame(color: Int): IGuiTexture = MachineUiIcons.pixelGlyph(
        color,
        floatArrayOf(7f, 2f, 1f, 1f),
        floatArrayOf(6f, 3f, 2f, 1f),
        floatArrayOf(6f, 4f, 3f, 1f),
        floatArrayOf(5f, 5f, 4f, 1f),
        floatArrayOf(5f, 6f, 5f, 1f),
        floatArrayOf(4f, 7f, 6f, 3f),
        floatArrayOf(5f, 10f, 4f, 1f),
    )
}
