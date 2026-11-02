package net.ptcrys.topo.api.pipe.ui

import net.ptcrys.topo.api.pipe.PipeDefinition
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig

import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue

import java.util.function.Consumer
import java.util.function.Supplier
import java.util.function.UnaryOperator

/**
 * What a strategy's contributed config widgets may see and do for the port being edited. The
 * screen is built identically on both sides; only the server side carries a live session —
 * [updateConfig] is a no-op on the client and [bindInt] owns the standard S2C mirror plus
 * authoritative-return RPC, so widgets never need side checks or manual sync registration.
 */
interface PipePortAccess {

    fun definition(): PipeDefinition

    /**
     * Server-side live config of the port (healed). Only call inside [bindInt] getters or
     * [updateConfig] operators — both run exclusively on the server.
     */
    fun currentConfig(): PipePortStrategyConfig

    /**
     * Transform-and-persist channel for widget clicks: the operator receives the healed config
     * and returns the replacement; the runtime re-clamps against the offer and persists. The
     * operator must keep the strategy unchanged. No-op on the client.
     */
    fun updateConfig(update: UnaryOperator<PipePortStrategyConfig>)

    /**
     * Create one stable hidden integer value in the final UI tree. Client edits invoke
     * [serverSetter] through a returning RPC; both the callback and continuous S2C mirror read
     * [serverGetter] and deliver only the validated value to [clientApply].
     */
    fun bindInt(id: String, initialValue: Int, serverGetter: Supplier<Int>, serverSetter: Consumer<Int>, clientApply: Consumer<Int>): BindableValue<Int>
}
