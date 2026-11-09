package net.ptcrys.topo.api.machine.ui

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

class PageCollector {
    private val pages = ArrayList<Page>()

    fun sink(key: String, title: Component, element: UIElement) {
        val pageKey = requireKey(key)
        element.setId(pageKey)
        pages.add(Page(pageKey, title, element))
    }

    fun entries(): List<Page> = pages.toList()

    /** Accessors use Java-record style (`key()`, `title()`, `element()`). */
    class Page(private val key: String, private val title: Component, private val element: UIElement) {
        fun key(): String = key

        fun title(): Component = title

        fun element(): UIElement = element
    }

    private companion object {
        fun requireKey(key: String): String {
            require(key.isNotBlank()) { "page key cannot be blank" }
            return key
        }
    }
}
