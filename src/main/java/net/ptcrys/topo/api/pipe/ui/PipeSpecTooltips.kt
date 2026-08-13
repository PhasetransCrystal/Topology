package net.ptcrys.topo.api.pipe.ui

import net.ptcrys.topo.api.api.lang.TopoApiLang
import net.ptcrys.topo.api.machine.ui.LcdData
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis
import net.ptcrys.topo.api.pipe.PipeDefinition
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy
import net.ptcrys.topo.api.pipe.Pipes

import net.minecraft.network.chat.Component
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * 管道物品的悬浮规格面板:hover 管道物品时,用 LDLib2 LCD 面板展示该管道**注册期定义**的
 * 性能规格(抽取速率、节点吞吐、策略集、过滤包络)。数据全部来自 [PipeDefinition]——
 * 纯注册期事实,无运行期数据、无任何物品持久化(物品 tooltip 不挂数据组件)。
 *
 * 注册时机:管道物品是延迟注册条目,mod 构造期拿不到 Item 实例,所以 provider 注册与
 * [ItemTooltipUis.freeze] 都发生在 FMLCommonSetup 的 enqueueWork 里(物品注册已完成)。
 */
object PipeSpecTooltips {

    /** mod 构造期接线;真正的 provider 注册延迟到物品实例可用之后。 */
    @JvmStatic
    fun register(modEventBus: IEventBus) {
        modEventBus.addListener { event: FMLCommonSetupEvent ->
            event.enqueueWork {
                for (definition in Pipes.registered()) {
                    val item = definition.registeredBlock().get().asItem()
                    ItemTooltipUis.register(item) { createSpecPanel(definition) }
                }
                ItemTooltipUis.freeze()
            }
        }
    }

    /**
     * 一根管道的规格面板。竖排 LCD,值列右对齐:
     * 速率与吞吐经该资源种类的 [PipeDefinition.profile] formatter 渲染(与端口屏/Jade 同源);
     * 策略行右列是数量,其下每个策略一条缩进子项(全名 | 简述,测试 292);过滤行展示
     * 条目容量(·#tag)或「—」。短码 `.short` 退回端口屏侧栏专用。
     */
    fun createSpecPanel(definition: PipeDefinition): UIElement {
        val lcd = MachineUiContainerTemplate.createTooltipLcdData(LcdData.Orientation.VERTICAL)
        lcd.setId("topo_pipe_spec_tooltip")
        lcd.addStaticEntry(
            TopoApiLang.TOOLTIP_PIPE_MAX_RATE.getComponent(),
            perTick(definition, definition.maxExtractRate()),
            LcdData.LED_TEXT,
        )
        lcd.addStaticEntry(
            TopoApiLang.TOOLTIP_PIPE_NODE_THROUGHPUT.getComponent(),
            perTick(definition, definition.nodeThroughput()),
            LcdData.LED_TEXT,
        )
        lcd.addStaticEntry(
            TopoApiLang.TOOLTIP_PIPE_STRATEGIES.getComponent(),
            strategyCountValue(definition.strategies().size),
            LcdData.LED_TEXT,
        )
        for ((name, desc) in strategyRows(definition.strategies().map { it.strategy() })) {
            lcd.addStaticSubEntry(name, desc, LcdData.LED_TEXT)
        }
        lcd.addStaticEntry(
            TopoApiLang.TOOLTIP_PIPE_FILTER.getComponent(),
            filterSpec(definition),
            LcdData.LED_TEXT,
        )
        return lcd
    }

    private fun perTick(definition: PipeDefinition, amount: Int): Component = TopoApiLang.TOOLTIP_PIPE_PER_TICK.getComponent(definition.profile().formatAmount(amount.toLong()))

    /** 策略行右列:可用策略数量(测试 292)。 */
    @JvmStatic
    fun strategyCountValue(count: Int): Component = TopoApiLang.TOOLTIP_PIPE_STRATEGY_COUNT.getComponent(count)

    /**
     * 策略子项纯函数(测试 292):每个策略从自身句柄取 (displayName, description),声明顺序。
     * 不做键拼接。
     */
    @JvmStatic
    fun strategyRows(strategies: List<PipeDistributionStrategy>): List<Pair<Component, Component>> = strategies.map { it.displayName() to it.description() }

    private fun filterSpec(definition: PipeDefinition): Component {
        val filter = definition.filterSettings()
        if (!filter.enabled()) {
            return TopoApiLang.TOOLTIP_PIPE_FILTER_NONE.getComponent()
        }
        return if (filter.allowTags) {
            TopoApiLang.TOOLTIP_PIPE_FILTER_ENTRIES_TAGS.getComponent(filter.entryCapacity())
        } else {
            TopoApiLang.TOOLTIP_PIPE_FILTER_ENTRIES.getComponent(filter.entryCapacity())
        }
    }
}
