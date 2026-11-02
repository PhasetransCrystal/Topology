package net.ptcrys.topo.api.machine.ui

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

internal class MachineUiContent private constructor(private val pages: List<PageCollector.Page>, private val components: List<ComponentCollector.Entry>) {
    fun pages(): List<PageCollector.Page> = pages

    fun firstPageKey(): String = pages.first().key()

    fun componentsOn(side: ComponentCollector.Side): List<ComponentCollector.Entry> = components.filter { it.side() == side }

    companion object {
        fun create(pages: List<PageCollector.Page>, components: List<ComponentCollector.Entry>?): MachineUiContent {
            val normalizedPages = normalizePages(pages)
            val normalizedComponents = normalizeComponents(components)
            validateComponentPages(normalizedPages, normalizedComponents)
            return MachineUiContent(normalizedPages, normalizedComponents)
        }

        private fun normalizePages(pages: List<PageCollector.Page>): List<PageCollector.Page> {
            require(pages.isNotEmpty()) { "machine UI pages cannot be empty" }
            val byKey = LinkedHashMap<String, PageCollector.Page>()
            for (page in pages) {
                val key = requirePageKey(page.key())
                val normalized = PageCollector.Page(
                    key,
                    page.title(),
                    requireElementId("page", page.element()),
                )
                check(byKey.putIfAbsent(key, normalized) == null) {
                    "duplicate machine UI page key: $key"
                }
            }
            return byKey.values.toList()
        }

        private fun normalizeComponents(components: List<ComponentCollector.Entry>?): List<ComponentCollector.Entry> {
            if (components.isNullOrEmpty()) {
                return emptyList()
            }
            return components.map { component ->
                val key = requireComponentKey(component.key())
                val element = component.element()
                element.setId(key)
                ComponentCollector.Entry(
                    component.side(),
                    normalizePageKey(component.pageKey()),
                    key,
                    component.title(),
                    component.titleBar(),
                    element,
                    component.visibleWhen(),
                )
            }
        }

        private fun validateComponentPages(pages: List<PageCollector.Page>, components: List<ComponentCollector.Entry>) {
            val pageKeys = pages.map { it.key() }.toSet()
            for (component in components) {
                val pageKey = component.pageKey()
                if (pageKey != null && pageKey !in pageKeys) {
                    throw IllegalArgumentException(
                        "machine UI component references unknown page key: $pageKey",
                    )
                }
            }
        }

        private fun requirePageKey(key: String): String {
            require(key.isNotBlank()) { "page key cannot be blank" }
            return key
        }

        private fun normalizePageKey(pageKey: String?): String? {
            if (pageKey == null) {
                return null
            }
            require(pageKey.isNotBlank()) { "page key cannot be blank" }
            return pageKey
        }

        private fun requireComponentKey(key: String): String {
            require(key.isNotBlank()) { "component key cannot be blank" }
            return key
        }

        private fun requireElementId(owner: String, element: UIElement): UIElement {
            val id = element.id
            require(!id.isNullOrBlank()) { "$owner element must call setId(...)" }
            return element
        }
    }
}
