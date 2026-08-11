package net.ptcrys.topo.api.machine.ui

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

import java.util.function.BooleanSupplier

class ComponentCollector {
    private val entries = ArrayList<Entry>()

    /** Show on every page. */
    fun sink(side: Side, key: String, title: Component, element: UIElement) {
        sink(side, null, key, title, element)
    }

    /** Show only on [pageKey]; null means every page. */
    fun sink(side: Side, pageKey: String?, key: String, title: Component, element: UIElement) {
        sink(side, pageKey, key, title, null, element, null)
    }

    /**
     * Card with an element title bar (icons etc.) instead of plain text; [title] stays as the
     * textual fallback/identity. Null [titleBar] renders the text title as usual.
     *
     * @param visibleWhen optional live predicate; when false the side card is layout-hidden
     *   (e.g. port search-pool chrome while ME pattern separation owns pools).
     */
    @JvmOverloads
    fun sink(side: Side, pageKey: String?, key: String, title: Component, titleBar: UIElement?, element: UIElement, visibleWhen: BooleanSupplier? = null) {
        val componentKey = requireKey(key)
        element.setId(componentKey)
        entries.add(
            Entry(
                side,
                normalizePageKey(pageKey),
                componentKey,
                title,
                titleBar,
                element,
                visibleWhen,
            ),
        )
    }

    fun entries(): List<Entry> = entries.toList()

    enum class Side(private val sideId: String) {
        LEFT("machine_ui_left"),
        RIGHT("machine_ui_right"),

        /** 主窗内、玩家物品栏上方的全宽条带(资源条等);不包卡片、无标题头。 */
        BOTTOM("machine_ui_bottom"),
        ;

        fun id(): String = sideId
    }

    /**
     * Accessors use Java-record style (`side()`, `key()`, …) so existing Java call sites keep working.
     */
    class Entry(private val side: Side, private val pageKey: String?, private val key: String, private val title: Component, private val titleBar: UIElement?, private val element: UIElement, private val visibleWhen: BooleanSupplier? = null) {
        fun side(): Side = side

        fun pageKey(): String? = pageKey

        fun key(): String = key

        fun title(): Component = title

        fun titleBar(): UIElement? = titleBar

        fun element(): UIElement = element

        /** Whether this entry has a live (non-page) visibility predicate. */
        fun hasLiveVisibility(): Boolean = visibleWhen != null

        /** Preserve the live predicate when machine UI content normalizes this entry. */
        fun visibleWhen(): BooleanSupplier? = visibleWhen

        /**
         * Live [visibleWhen] only (no page filter). Must be evaluated on the logical-server side of
         * the menu (or via S2C mirror): client machine DataBoolean fields may still be waiting /
         * stale while LDLib UI bindings already show the server truth.
         */
        fun liveVisible(): Boolean = visibleWhen?.asBoolean ?: true

        /** Page filter only (ignores [visibleWhen]). */
        fun pageVisibleOn(activePageKey: String): Boolean = pageKey == null || pageKey == activePageKey

        /** Page filter plus optional live [visibleWhen] predicate (local evaluation). */
        fun visibleOn(activePageKey: String): Boolean {
            if (!pageVisibleOn(activePageKey)) {
                return false
            }
            return liveVisible()
        }
    }

    private companion object {
        fun normalizePageKey(pageKey: String?): String? {
            if (pageKey == null) {
                return null
            }
            require(pageKey.isNotBlank()) { "page key cannot be blank" }
            return pageKey
        }

        fun requireKey(key: String): String {
            require(key.isNotBlank()) { "component key cannot be blank" }
            return key
        }
    }
}
