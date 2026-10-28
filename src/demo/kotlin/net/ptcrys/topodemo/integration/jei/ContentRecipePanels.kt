package net.ptcrys.topodemo.integration.jei

import net.ptcrys.topo.api.machine.resource.RecipeRole
import net.ptcrys.topo.api.machine.ui.recipe.RecipeUiLayout
import net.ptcrys.topo.api.ore.OreVein
import net.ptcrys.topo.api.ore.display.OreVeinDisplays
import net.ptcrys.topo.api.recipe.RecipePreviewPlan.SlotPlan
import net.ptcrys.topo.api.recipe.TopoRecipe
import net.ptcrys.topo.api.recipe.TopoRecipeType
import net.ptcrys.topo.api.recipe.capability.SlottedRecipeCapability
import net.ptcrys.topo.api.recipe.content.TopoItemInput
import net.ptcrys.topo.api.recipe.content.TopoItemOutput
import net.ptcrys.topodemo.api.recipe.BuiltinResources
import net.ptcrys.topodemo.data.ContentLang

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection

/** Client-only adapters for this content's item recipes and mode-owned ore previews. */
object ContentRecipePanels {
    const val WIDTH = 210
    const val RECIPE_HEIGHT = 90
    const val VEIN_HEIGHT = 140

    @JvmStatic
    fun recipe(type: TopoRecipeType<TopoRecipe>, recipe: TopoRecipe): ModularUI {
        val items = BuiltinResources.ITEM
        val inputs = recipe.inputs().filter { it.capability() === items }
            .flatMap { it.contents().map { value -> value as TopoItemInput.Resource } }
        val outputs = recipe.outputs().filter { it.capability() === items }
            .flatMap { it.contents().map { value -> value as TopoItemOutput } }
        // Standalone machines use the frozen canonical layout. A bus-fed controller has no
        // canonical local ports, so its preview uses the recipe's actual content count.
        val plan = type.previewPlan().slotted().ifEmpty {
            mapOf<SlottedRecipeCapability<*, *, *>, SlotPlan>(items to SlotPlan(inputs.size, outputs.size))
        }
        val root = root(RECIPE_HEIGHT)
        root.addChild(
            RecipeUiLayout.buildRecipeIoRow(
                type,
                plan,
                { io, slot ->
                    if (io == RecipeRole.INPUT) {
                        items.createPreviewInputSlotWidget(inputs.getOrNull(slot))
                    } else {
                        items.createPreviewOutputSlotWidget(outputs.getOrNull(slot))
                    }
                },
                { bar ->
                    val durationMs = maxOf(1, recipe.duration()) * 50L
                    bar.bindDataSource(SupplierDataSource.of { (System.currentTimeMillis() % durationMs) / durationMs.toFloat() })
                },
            ),
        )
        root.addChild(Label().setText(ContentLang.RECIPE_DURATION.getComponent(recipe.duration())))
        return modular(root)
    }

    @JvmStatic
    fun vein(vein: OreVein): ModularUI {
        val root = root(VEIN_HEIGHT)
        root.addChild(OreVeinDisplays.require(vein.mode().id()).buildPreview(vein))
        return modular(root)
    }

    private fun root(height: Int): UIElement = UIElement().apply {
        layout {
            it.width(WIDTH.toFloat())
            it.height(height.toFloat())
            it.flexDirection(FlexDirection.COLUMN)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.CENTER)
        }
    }

    private fun modular(root: UIElement): ModularUI = ModularUI.of(
        UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)),
    )
}
