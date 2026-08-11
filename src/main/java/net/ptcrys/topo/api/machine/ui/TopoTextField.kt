package net.ptcrys.topo.api.machine.ui

import net.minecraft.client.Minecraft

import com.lowdragmc.lowdraglib2.LDLib2
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent
import org.lwjgl.glfw.GLFW

/**
 * Topo text field: LDLib2 [TextField] with system-clipboard paste.
 *
 * Upstream Ctrl+V only reads LDLib's in-process [com.lowdragmc.lowdraglib2.editor.ClipboardManager],
 * which is empty after OS / `keyboardHandler.setClipboard` copies. We handle paste here so Topo does
 * not need a LDLib mixin or a forked library build.
 */
class TopoTextField : TextField() {
    override fun onKeyDown(event: UIEvent) {
        if (isPrimaryShortcut(event) && event.keyCode == GLFW.GLFW_KEY_V) {
            if (isEditable) {
                insertText(systemClipboardText())
            }
            return
        }
        super.onKeyDown(event)
    }

    private fun isPrimaryShortcut(event: UIEvent): Boolean = event.isCtrlDown || (event.modifiers and GLFW.GLFW_MOD_SUPER) != 0

    companion object {
        /** System / Minecraft keyboard clipboard; empty when unavailable. */
        @JvmStatic
        fun systemClipboardText(): String {
            if (!LDLib2.isClient()) {
                return ""
            }
            return Minecraft.getInstance().keyboardHandler.clipboard
        }
    }
}
