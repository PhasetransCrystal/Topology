package net.ptcrys.topo.data.machine.common.component

import net.ptcrys.topo.api.machine.component.ComponentContext
import net.ptcrys.topo.api.machine.component.ComponentKey
import net.ptcrys.topo.api.machine.component.ComponentMount
import net.ptcrys.topo.api.machine.component.MachineComponent
import net.ptcrys.topo.api.machine.component.ServiceKey
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate.TextLayout
import net.ptcrys.topo.api.machine.ui.MachineUiContribution

import net.minecraft.network.chat.Component

/** Example plain trait mounted twice with different stable keys. */
class ExampleBuffer private constructor(context: ComponentContext<ExampleBuffer>, private val processValue: Int) : MachineComponent(context) {

    fun processValue(): Int = processValue

    override fun collectMachineUi(contribution: MachineUiContribution) {
        val traitKey = key().toString()
        contribution.mainPage(traitKey, Component.literal("测试")) {
            add(
                MachineUiComponentTemplate.createText(
                    Component.literal("测试"),
                    TextLayout.MAX_WIDTH_AUTO_HEIGHT,
                ),
            )
            add(
                MachineUiComponentTemplate.createText(
                    Component.literal(traitKey),
                    TextLayout.MAX_WIDTH_AUTO_HEIGHT,
                ),
            )
            add(
                MachineUiComponentTemplate.createText(
                    Component.literal(
                        "long text test long text test long text test long text test long text test " +
                            "long text test long text test long text test long text test long text test " +
                            "long text test long text test long text test long text test long text test ",
                    ),
                    TextLayout.MAX_WIDTH_AUTO_HEIGHT,
                ),
            )
        }
        contribution.leftPanel(
            "$traitKey:primary",
            Component.literal("test"),
            element = MachineUiComponentTemplate.createText(
                Component.literal("测试"),
                TextLayout.AUTO_WIDTH_1_LINE,
            ),
        )
        contribution.rightPanel(
            "$traitKey:secondary",
            Component.literal("test"),
            element = MachineUiComponentTemplate.createText(
                Component.literal("测试"),
                TextLayout.AUTO_WIDTH_1_LINE,
            ),
        )
    }

    fun interface ProcessValueView {
        fun processValue(): Int
    }

    companion object {
        @JvmField
        val PROCESS_VALUE: ServiceKey<ProcessValueView, Void> =
            ServiceKey.oi("example_process_value", ProcessValueView::class.java, Void::class.java)

        @JvmField
        val LEFT: ComponentKey<ExampleBuffer> =
            ComponentKey.id("left_buffer", ExampleBuffer::class.java)
                .service(PROCESS_VALUE) { trait, _ -> ProcessValueView { trait.processValue() } }

        @JvmField
        val RIGHT: ComponentKey<ExampleBuffer> =
            ComponentKey.id("right_buffer", ExampleBuffer::class.java)
                .service(PROCESS_VALUE) { trait, _ -> ProcessValueView { trait.processValue() } }

        @JvmStatic
        fun left(): ComponentMount<ExampleBuffer> = LEFT.mount { context -> ExampleBuffer(context, 10) }

        @JvmStatic
        fun right(): ComponentMount<ExampleBuffer> = RIGHT.mount { context -> ExampleBuffer(context, 20) }
    }
}
