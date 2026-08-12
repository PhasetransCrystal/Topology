package net.ptcrys.topo.data.machine.common.component;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.machine.data.DataInt;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.machine.ui.MachineUiContribution;
import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import kotlin.Unit;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Creative energy generator: each server tick tops up a sibling scalar output buffer to the
 * player-set generation rate, so the machine supplies up to {@code rate} energy/tick to whatever
 * pulls on it (and wastes nothing when nothing pulls). The rate is an editable GUI field; the
 * machine itself burns no fuel and the buffer never overflows past the rate.
 */
public final class CreativeEnergyGenerator extends MachineTicker {

    public static final ComponentKey<CreativeEnergyGenerator> CREATIVE_GENERATOR = ComponentKey.id("creative_generator", CreativeEnergyGenerator.class);

    /** Upper bound on the settable rate (energy/tick); also the output buffer's natural capacity. */
    public static final int MAX_RATE = 1_000_000_000;
    private static final int DEFAULT_RATE = 10_000;

    private final ComponentKey<ScalarResourcePort> outputKey;
    private final DataInt rate = data().intField("rate", DEFAULT_RATE)
            .persisted()
            .syncNone()
            .done();
    private @Nullable ScalarResourcePort output;

    private CreativeEnergyGenerator(
                                    ComponentContext<CreativeEnergyGenerator> context, ComponentKey<ScalarResourcePort> outputKey) {
        super(context);
        this.outputKey = outputKey;
    }

    public static ComponentMount<CreativeEnergyGenerator> mount(
                                                                ComponentKey<CreativeEnergyGenerator> key, ComponentKey<ScalarResourcePort> outputKey) {
        Objects.requireNonNull(key, "creative generator trait key");
        Objects.requireNonNull(outputKey, "energy output trait key");
        return key.mount(context -> new CreativeEnergyGenerator(context, outputKey));
    }

    @Override
    public void resolveDependencies(MachineComponents traits) {
        this.output = traits.require(outputKey);
    }

    public int rate() {
        return rate.value();
    }

    /** Server-side authoritative setter; clamps into {@code [0, MAX_RATE]}. */
    public void setRate(int value) {
        rate.set(Math.clamp(value, 0, MAX_RATE));
        data().markPersistedStateChanged();
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        ScalarResourcePort out = output;
        if (out == null) {
            return;
        }
        int target = rate.value();
        ResourceHandler<ScalarResource> handler = out.handler();
        ScalarResource energy = out.resource();
        long current = handler.getAmountAsLong(0);
        // Top the buffer up to the rate (never above it): the next puller sees up to rate/t and the
        // refill replaces exactly what was drawn, so sustained output equals the consumed rate.
        if (current < target) {
            try (Transaction transaction = Transaction.openRoot()) {
                handler.insert(energy, (int) (target - current), transaction);
                transaction.commit();
            }
        }
    }

    /**
     * Clickable generation-rate editor on the GUI bottom strip: opens the shared
     * {@link MachineUiComponentTemplate#createAmountEditor} popup (same component as the ME part /
     * pipe port amount editing), committing the new rate to the server authoritatively.
     */
    @Override
    public void collectMachineUi(MachineUiContribution contribution) {
        UIElement editor = MachineUiComponentTemplate.INSTANCE.createAmountEditor(
                BuiltinTopoMachineUiLang.UI_CREATIVE_GENERATOR_RATE_POPUP_TITLE.getComponent(),
                () -> rate.value(),
                value -> {
                    setRate(value);
                    return Unit.INSTANCE;
                },
                0,
                MAX_RATE);
        contribution.bottomStrip(
                "topo_creative_rate_" + id().getPath(),
                BuiltinTopoMachineUiLang.UI_CREATIVE_GENERATOR_RATE.getComponent(),
                null,
                editor);
    }
}
