package net.ptcrys.topo.integration.ae2;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AeStockingPersistenceTest {

    @Test
    void rejectedRollbackSurvivesSaveAndIsReturnedAfterReconnect() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var problems = new ProblemReporter.Collector();
        var waterHolder = Fluids.WATER.builtInRegistryHolder();
        if (!waterHolder.areComponentsBound()) {
            waterHolder.bindComponents(DataComponentMap.EMPTY);
        }
        var water = FluidResource.of(Fluids.WATER);
        var network = new RollbackNetwork();
        var notifications = new AtomicInteger();
        var handler = new AeStockingResourceHandler<>(FluidResource.class, FluidResource.EMPTY,
                List.of(AeConfigSlot.of(water, 1_000)), () -> network,
                notifications::incrementAndGet, resource -> new TestAeKey("water"));
        try (var transaction = Transaction.openRoot()) {
            assertEquals(350, handler.extract(0, water, 350, transaction));
        }
        assertEquals(350, handler.pendingRefundAmountForTest(water));
        var saved = TagValueOutput.createWithContext(problems, registries);
        handler.serialize(saved);
        var restoredNotifications = new AtomicInteger();
        var restored = new AeStockingResourceHandler<>(FluidResource.class, FluidResource.EMPTY,
                List.of(AeConfigSlot.<FluidResource>empty()), () -> network,
                restoredNotifications::incrementAndGet, resource -> new TestAeKey("water"));
        restored.deserialize(TagValueInput.create(problems, registries, saved.buildResult()));
        assertEquals(AeConfigSlot.of(water, 1_000), restored.configSlot(0));
        assertEquals(350, restored.pendingRefundAmountForTest(water));
        assertEquals(0, restoredNotifications.get());
        network.acceptRefunds = true;
        restored.flushPendingRefundsForTest();
        assertEquals(350, network.returned);
        assertEquals(0, restored.pendingRefundAmountForTest(water));
        assertEquals(1, restoredNotifications.get());
        assertTrue(problems.isEmpty());
    }

    @Test
    void restoresConfigurationAndRefundWithoutNotifyingUntilRealMutation() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var problems = new ProblemReporter.Collector();
        var waterHolder = Fluids.WATER.builtInRegistryHolder();
        if (!waterHolder.areComponentsBound()) {
            waterHolder.bindComponents(DataComponentMap.EMPTY);
        }
        var water = FluidResource.of(Fluids.WATER);
        var encoded = TagValueOutput.createWithContext(problems, registries);
        var slot = encoded.childrenList("slots").addChild();
        slot.putLong("target", 1_000);
        slot.store("resource", FluidResource.OPTIONAL_CODEC, water);
        var refund = encoded.childrenList("pendingRefunds").addChild();
        refund.putLong("amount", 350);
        refund.store("resource", FluidResource.OPTIONAL_CODEC, water);

        var notifications = new AtomicInteger();
        var network = new RefundNetwork();
        var handler = new AeStockingResourceHandler<>(FluidResource.class, FluidResource.EMPTY,
                List.of(AeConfigSlot.<FluidResource>empty()), () -> network,
                notifications::incrementAndGet, resource -> new TestAeKey("water"));
        handler.deserialize(TagValueInput.create(problems, registries, encoded.buildResult()));
        assertEquals(AeConfigSlot.of(water, 1_000), handler.configSlot(0));
        assertEquals(350, handler.pendingRefundAmountForTest(water));
        assertEquals(0, notifications.get());

        var saved = TagValueOutput.createWithContext(problems, registries);
        handler.serialize(saved);
        var restored = new AeStockingResourceHandler<>(FluidResource.class, FluidResource.EMPTY,
                List.of(AeConfigSlot.<FluidResource>empty()), () -> network,
                notifications::incrementAndGet, resource -> new TestAeKey("water"));
        restored.deserialize(TagValueInput.create(problems, registries, saved.buildResult()));
        assertEquals(AeConfigSlot.of(water, 1_000), restored.configSlot(0));
        assertEquals(350, restored.pendingRefundAmountForTest(water));
        assertEquals(0, notifications.get());
        restored.flushPendingRefundsForTest();
        assertEquals(350, network.inserted);
        assertEquals(0, restored.pendingRefundAmountForTest(water));
        assertEquals(1, notifications.get());
        restored.setConfigSlot(0, AeConfigSlot.of(water, 500));
        assertEquals(2, notifications.get());
        assertTrue(problems.isEmpty());
    }

    private static final class RefundNetwork implements AeNetworkAccessor {

        private long inserted;

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public long available(AEKey key) {
            return 0;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            return 0;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            inserted += amount;
            return amount;
        }
    }

    private static final class RollbackNetwork implements AeNetworkAccessor {

        private boolean acceptRefunds;
        private long returned;

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public long available(AEKey key) {
            return 1_000;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            return amount;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            if (!acceptRefunds) return 0;
            returned += amount;
            return amount;
        }
    }
}
