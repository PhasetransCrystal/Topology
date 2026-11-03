package net.ptcrys.topo.api.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Public port-configuration face used by the pipe screen and integrations. Topology and tick
 * scheduling are deliberately absent: this is only the persisted per-side settings domain.
 */
public interface PipePortSettings {

    PipePortStrategyConfig config(BlockPos pos, Direction side);

    int strategyIndex(BlockPos pos, Direction side);

    int amount(BlockPos pos, Direction side);

    int interval(BlockPos pos, Direction side);

    @Nullable
    AggregationWindow window(BlockPos pos, Direction side);

    PipePortFilter filter(BlockPos pos, Direction side);

    void selectStrategy(BlockPos pos, Direction side, int index);

    void setAmount(BlockPos pos, Direction side, int amount);

    void adjustAmount(BlockPos pos, Direction side, int delta);

    void setInterval(BlockPos pos, Direction side, int interval);

    void adjustInterval(BlockPos pos, Direction side, int delta);

    void updateConfig(BlockPos pos, Direction side, UnaryOperator<PipePortStrategyConfig> update);

    void addFilterEntry(BlockPos pos, Direction side, boolean white, String entry);

    void removeFilterEntry(BlockPos pos, Direction side, boolean white, String entry);
}
