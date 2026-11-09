package net.ptcrys.topo.api.api.util;

import net.ptcrys.topo.api.machine.resource.LongResourceHandler;

import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 使用两种不同资源类型和真实事务日志验证批量访问，不依赖物品组件绑定或机器启动。 */
class StorageAccessTest {

    private static final Resource IRON = new Solid("iron");
    private static final Resource WATER = new Liquid("water");

    @Test
    void singleAndBatchOperationsUseHandlerAggregateMethods() {
        Store store = new Store(new Resource[] { IRON, WATER }, new long[] { 5, 200 }, 1000);
        StorageAccess access = access(store);
        assertEquals(2, access.extract(IRON, 2));
        assertEquals(1, store.aggregateExtractions);
        assertEquals(Map.of(WATER, 100L), access.extract(Map.of(WATER, 100L)).fulfilled());
        assertEquals(2, store.aggregateExtractions);
        assertEquals(0, store.reads);
    }

    @Test
    void forwardsOnlyRemainingMixedResourcesAndCommitsEachProvider() {
        Store first = new Store(new Resource[] { IRON, WATER }, new long[] { 4, 1000 }, 2000);
        Store second = new Store(new Resource[] { IRON, WATER }, new long[] { 8, 900 }, 2000);
        StorageResult result = new CompositeStorageAccess(List.of(access(first), access(second)))
                .extract(ordered(IRON, 10, WATER, 1500));
        assertEquals(ordered(IRON, 10, WATER, 1500), result.fulfilled());
        assertTrue(result.remaining().isEmpty());
        assertEquals(2, second.amount(IRON));
        assertEquals(400, second.amount(WATER));
        assertEquals(1, first.commits);
        assertEquals(1, second.commits);
    }

    @Test
    void validatesBeforeMutationAndHandlesLongCounts() {
        long amount = (long) Integer.MAX_VALUE + 50;
        Store store = new Store(new Resource[] { IRON, WATER }, new long[] { amount, 700 }, Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> access(store).extract(ordered(IRON, 2, WATER, -1)));
        assertEquals(0, store.reads);
        StorageResult result = access(store).extract(ordered(IRON, amount + 10, WATER, 1000));
        assertEquals(ordered(IRON, amount, WATER, 700), result.fulfilled());
        assertEquals(ordered(IRON, 10, WATER, 300), result.remaining());
        assertThrows(UnsupportedOperationException.class, () -> result.fulfilled().put(IRON, 1L));
    }

    @Test
    void reportsPreviouslyCompletedTransfersWhenAnotherProviderFails() {
        Store first = new Store(new Resource[] { IRON }, new long[] { 3 }, 100);
        StorageAccess broken = new StorageAccess() {

            public long extract(Resource resource, long amount) {
                throw new IllegalStateException();
            }

            public long insert(Resource resource, long amount) {
                throw new IllegalStateException();
            }

            public StorageResult extract(Map<? extends Resource, Long> request) {
                throw new IllegalStateException();
            }

            public StorageResult insert(Map<? extends Resource, Long> request) {
                throw new IllegalStateException();
            }
        };
        StorageAccessException failure = assertThrows(StorageAccessException.class,
                () -> new CompositeStorageAccess(List.of(access(first), broken)).extract(Map.of(IRON, 5L)));
        assertEquals(Map.of(IRON, 3L), failure.confirmed().fulfilled());
        assertEquals(Map.of(IRON, 2L), failure.confirmed().remaining());
        assertEquals(0, first.amount(IRON));
    }

    private static StorageAccess access(Store store) {
        return new ResourceHandlerStorageAccess<>(Resource.class, store);
    }

    private static Map<Resource, Long> ordered(Resource first, long firstAmount, Resource second, long secondAmount) {
        Map<Resource, Long> result = new LinkedHashMap<>();
        result.put(first, firstAmount);
        result.put(second, secondAmount);
        return result;
    }

    private record Solid(String id) implements Resource {

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private record Liquid(String id) implements Resource {

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private enum Empty implements Resource {

        INSTANCE;

        @Override
        public boolean isEmpty() {
            return true;
        }
    }

    private static final class Store extends SnapshotJournal<Store.State> implements LongResourceHandler<Resource> {

        private final Resource[] resources;
        private final long[] amounts;
        private final long capacity;
        private int reads;
        private int commits;
        private int aggregateExtractions;

        private Store(Resource[] resources, long[] amounts, long capacity) {
            this.resources = resources.clone();
            this.amounts = amounts.clone();
            this.capacity = capacity;
        }

        long amount(Resource resource) {
            long total = 0;
            for (int i = 0; i < resources.length; i++) if (resources[i].equals(resource)) total += amounts[i];
            return total;
        }

        @Override
        public int size() {
            return resources.length;
        }

        @Override
        public Resource getResource(int index) {
            reads++;
            return resources[index];
        }

        @Override
        public long getAmountAsLong(int index) {
            return amounts[index];
        }

        @Override
        public long getCapacityAsLong(int index, Resource resource) {
            return capacity;
        }

        @Override
        public boolean isValid(int index, Resource resource) {
            return true;
        }

        @Override
        public long extractLong(Resource resource, long amount, TransactionContext tx) {
            aggregateExtractions++;
            return LongResourceHandler.super.extractLong(resource, amount, tx);
        }

        @Override
        public long extractLong(int index, Resource resource, long amount, TransactionContext tx) {
            if (!resources[index].equals(resource)) return 0;
            long moved = Math.min(amount, amounts[index]);
            if (moved == 0) return 0;
            updateSnapshots(tx);
            amounts[index] -= moved;
            if (amounts[index] == 0) resources[index] = Empty.INSTANCE;
            return moved;
        }

        @Override
        public long insertLong(int index, Resource resource, long amount, TransactionContext tx) {
            if (!resources[index].isEmpty() && !resources[index].equals(resource)) return 0;
            long moved = Math.min(amount, capacity - amounts[index]);
            if (moved == 0) return 0;
            updateSnapshots(tx);
            resources[index] = resource;
            amounts[index] += moved;
            return moved;
        }

        @Override
        protected State createSnapshot() {
            return new State(resources.clone(), amounts.clone());
        }

        @Override
        protected void revertToSnapshot(State snapshot) {
            System.arraycopy(snapshot.resources, 0, resources, 0, resources.length);
            System.arraycopy(snapshot.amounts, 0, amounts, 0, amounts.length);
        }

        @Override
        protected void onRootCommit(State snapshot) {
            commits++;
        }

        private record State(Resource[] resources, long[] amounts) {}
    }
}
