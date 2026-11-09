package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.api.util.StorageAccessException;
import net.ptcrys.topo.api.api.util.StorageResult;

import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AeStorageAccessTest {

    @Test
    void batchResolvesNetworkOnceAndReportsPartialResult() {
        var first = new AeResource(new TestAeKey("first"));
        var second = new AeResource(new TestAeKey("second"));
        Map<Resource, Long> request = new LinkedHashMap<>();
        request.put(first, 4L);
        request.put(second, 5L);
        AtomicInteger lookups = new AtomicInteger();
        AeStorageAccess storage = new AeStorageAccess(() -> {
            lookups.incrementAndGet();
            return new FakeNetwork(false);
        });

        StorageResult result = storage.extract(request);
        assertEquals(1, lookups.get());
        assertEquals(Map.of(first, 2L, second, 2L), result.fulfilled());
        assertEquals(Map.of(first, 2L, second, 3L), result.remaining());
        storage.insert(request);
        assertEquals(2, lookups.get());
        storage.extract(Map.of());
        assertEquals(2, lookups.get());
        Resource unsupported = () -> false;
        StorageResult skipped = storage.extract(Map.of(unsupported, 3L));
        assertEquals(Map.of(unsupported, 3L), skipped.remaining());
        assertEquals(2, lookups.get());
    }

    @Test
    void failurePreservesPreviouslyConfirmedTransfers() {
        var first = new AeResource(new TestAeKey("first"));
        var second = new AeResource(new TestAeKey("second"));
        Map<Resource, Long> request = new LinkedHashMap<>();
        request.put(first, 4L);
        request.put(second, 5L);
        AeStorageAccess storage = new AeStorageAccess(() -> new FakeNetwork(true));

        StorageAccessException failure = assertThrows(StorageAccessException.class, () -> storage.extract(request));
        assertEquals(Map.of(first, 2L), failure.confirmed().fulfilled());
        assertEquals(Map.of(first, 2L, second, 5L), failure.confirmed().remaining());
    }

    private static final class FakeNetwork implements AeNetworkAccessor {

        private final boolean failSecond;
        private int calls;

        private FakeNetwork(boolean failSecond) {
            this.failSecond = failSecond;
        }

        @Override
        public boolean isOnline() {
            return true;
        }

        @Override
        public long available(AEKey key) {
            return 0;
        }

        @Override
        public long availableCached(AEKey key) {
            return 0;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            if (++calls == 2 && failSecond) throw new IllegalStateException("network failure");
            return Math.min(2, amount);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            return Math.min(2, amount);
        }
    }
}
