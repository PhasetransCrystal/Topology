package net.ptcrys.topo.api.machine.ui.recipe

import net.ptcrys.topo.api.machine.resource.RecipeRole
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.recipe.RecipePreviewPlan.SlotPlan
import net.ptcrys.topo.api.recipe.TopoRecipeType
import net.ptcrys.topo.api.recipe.capability.SlottedRecipeCapability

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Clip
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection
import dev.vfyjxf.taffy.style.TaffyPosition

/**
 * 机器实况页与 JEI 预览共用的配方 IO 行布局（槽列 + 进度条）。
 * 尺寸全部来自 [MachineUiComponentStyle]。
 *
 * 布局用显式 UIElement + FlexDirection（与历史 RecipeUiLayoutHelper 同构）；
 * 不用 MachineUiLayout 间接包装，避免 JEI ModularUI 宽高/换行错位。
 */
object RecipeUiLayout {
    @JvmField
    val SLOT_SIZE: Int = MachineUiComponentStyle.slotSize

    @JvmField
    val PLAYER_INVENTORY_WIDTH: Int = MachineUiComponentStyle.playerInventoryWidth

    @JvmField
    val PROGRESS_SIZE: Int = MachineUiComponentStyle.progressSize

    @JvmField
    val IO_ROW_HEIGHT: Int = MachineUiComponentStyle.ioRowHeight

    private val IO_ROW_GAP = 6f

    @JvmStatic
    fun buildRecipeIoRow(recipeType: TopoRecipeType<*>, slotCounts: Map<SlottedRecipeCapability<*, *, *>, SlotPlan>, slotFactory: (RecipeRole, Int) -> UIElement, progressBinder: (ProgressBar) -> Unit): UIElement = UIElement().apply {
        setId("topo_recipe_io_row")
        // 与历史 RecipeUiLayoutHelper 同构：定宽定高 + ROW + gapColumn，无 min/max 钳制。
        layout {
            it.flexDirection(FlexDirection.ROW)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.CENTER)
            it.height(IO_ROW_HEIGHT.toFloat())
            it.width(PLAYER_INVENTORY_WIDTH.toFloat())
            it.gapColumn(IO_ROW_GAP)
        }
        addChild(buildSlotColumn(slotCounts, RecipeRole.INPUT, slotFactory))
        addChild(buildProgressBar(recipeType, progressBinder))
        addChild(buildSlotColumn(slotCounts, RecipeRole.OUTPUT, slotFactory))
    }

    @JvmStatic
    fun buildSlotColumn(slotCounts: Map<SlottedRecipeCapability<*, *, *>, SlotPlan>, io: RecipeRole, slotFactory: (RecipeRole, Int) -> UIElement): UIElement {
        val slots = ArrayList<UIElement>()
        var globalIndex = 0
        for ((_, plan) in slotCounts) {
            val count = plan.count(io)
            repeat(count) {
                slots.add(slotFactory(io, globalIndex++))
            }
        }
        if (io == RecipeRole.INPUT && slots.isEmpty()) {
            // 纯标量输入：进度条左侧补旋转风扇占位（机器实况与 JEI 共用）。
            slots.add(buildScalarConversionFan())
        }

        val columns = if (slots.size <= 1) 1 else 2
        return UIElement().apply {
            setId("topo_recipe_slot_column")
            layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.alignItems(AlignItems.CENTER)
            }
            var start = 0
            while (start < slots.size) {
                val end = minOf(start + columns, slots.size)
                val row = UIElement().apply {
                    setId("topo_recipe_slot_row")
                    layout { it.flexDirection(FlexDirection.ROW) }
                    for (i in start until end) {
                        addChild(slots[i])
                    }
                }
                addChild(row)
                start = end
            }
        }
    }

    @JvmStatic
    fun buildProgressBar(recipeType: TopoRecipeType<*>, progressBinder: (ProgressBar) -> Unit): ProgressBar {
        val textureWidth = recipeType.progressBarTextureWidth()
        val frameHeight = recipeType.progressBarTextureFrameHeight()
        val emptyHalf = SpriteTexture.of(recipeType.progressBarTexture())
            .setSprite(0, 0, textureWidth, frameHeight)
        val fullHalf = SpriteTexture.of(recipeType.progressBarTexture())
            .setSprite(0, frameHeight, textureWidth, frameHeight)

        val bar = ProgressBar()
        bar.setId("topo_recipe_progress_bar")
        bar.layout {
            it.width(PROGRESS_SIZE.toFloat())
            it.height(PROGRESS_SIZE.toFloat())
        }
        bar.barContainer.layout { it.paddingAll(0f) }
        bar.barContainer.style { it.backgroundTexture(emptyHalf) }
        bar.bar.style {
            it.backgroundTexture(IGuiTexture.EMPTY)
            it.clip(Clip.SCISSOR)
        }
        val fill = UIElement().apply {
            setId("topo_recipe_progress_fill")
            layout {
                it.positionType(TaffyPosition.ABSOLUTE)
                when (recipeType.fillDirection()) {
                    FillDirection.RIGHT_TO_LEFT -> {
                        it.right(0f)
                        it.top(0f)
                    }
                    FillDirection.DOWN_TO_UP -> {
                        it.left(0f)
                        it.bottom(0f)
                    }
                    else -> {
                        it.left(0f)
                        it.top(0f)
                    }
                }
                it.width(PROGRESS_SIZE.toFloat())
                it.height(PROGRESS_SIZE.toFloat())
            }
            style { it.backgroundTexture(fullHalf) }
        }
        bar.bar.addChild(fill)
        bar.label.setDisplay(false)
        bar.progressBarStyle {
            it.fillDirection(recipeType.fillDirection())
            it.interpolate(false)
        }
        progressBinder(bar)
        return bar
    }

    private fun buildScalarConversionFan(): UIElement = UIElement().apply {
        setId("topo_recipe_scalar_conversion_fan")
        layout {
            it.width(MachineUiComponentStyle.scalarFanSize)
            it.height(MachineUiComponentStyle.scalarFanSize)
            it.flexShrink(0f)
        }
        style { it.backgroundTexture(MachineUiComponentStyle.scalarConversionFanTexture()) }
    }
}
