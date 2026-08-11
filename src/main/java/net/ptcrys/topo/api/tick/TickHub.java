package net.ptcrys.topo.api.tick;

import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Per-level scheduler for all centralised machine tick work.
 *
 * <p>
 * The hot path is grouped by {@link TickKind} and interval. Each (kind, interval) bucket pays
 * one modulo check, then walks a compact array of entries. Structural changes are queued and
 * applied at scheduling boundaries, so hooks can safely alert, reseat, or unsubscribe themselves.
 */
public final class TickHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(TickHub.class);

    private final Level level;
    private final KindBuckets[] byKind;
    private final @Nullable ScheduledExecutorService asyncExecutor;
    private @Nullable ScheduledFuture<?> asyncFuture;
    private long asyncTickCount;
    private volatile boolean detached;

    private TickHub(Level level, @Nullable ScheduledExecutorService asyncExecutor) {
        this.level = level;
        this.asyncExecutor = asyncExecutor;
        int n = TickKind.VALUES.length;
        this.byKind = new KindBuckets[n];
        for (int i = 0; i < n; i++) {
            this.byKind[i] = new KindBuckets();
        }
    }

    static TickHub attach(Level level, @Nullable ScheduledExecutorService asyncExecutor) {
        TickHub hub = new TickHub(level, asyncExecutor);
        TickHubAttachment.set(level, hub);
        if (asyncExecutor != null) {
            hub.asyncFuture = asyncExecutor.scheduleAtFixedRate(
                    hub::safeAsyncDriver, 50L, 50L, TimeUnit.MILLISECONDS);
        }
        return hub;
    }

    static void detach(Level level) {
        TickHub hub = TickHubAttachment.remove(level);
        if (hub != null) {
            hub.close();
        }
    }

    public static @Nullable TickHub of(Level level) {
        return TickHubAttachment.get(level);
    }

    public static List<TickHub> allLoaded() {
        return List.copyOf(TickHubAttachment.all());
    }

    public Level level() {
        return level;
    }

    public TickHandle register(TickKind kind, int interval, TickHook hook) {
        if (interval < 1) {
            throw new IllegalArgumentException("interval must be >= 1, got " + interval);
        }
        Entry entry = new Entry(this, kind, interval, hook);
        if (detached) {
            entry.cancelled = true;
            return entry;
        }
        Object lock = lockFor(kind);
        if (lock == null) {
            byKind[kind.ordinal()].add(interval, entry);
        } else {
            synchronized (lock) {
                byKind[kind.ordinal()].add(interval, entry);
            }
        }
        return entry;
    }

    public void tickSync(long gameTime) {
        if (detached) {
            return;
        }
        for (TickKind kind : TickKind.VALUES) {
            if (!kind.async()) {
                runKind(kind, gameTime);
            }
        }
    }

    public void tickAsync() {
        if (detached) {
            return;
        }
        long tick = ++asyncTickCount;
        for (TickKind kind : TickKind.VALUES) {
            if (kind.async()) {
                runKind(kind, tick);
            }
        }
    }

    private void safeAsyncDriver() {
        try {
            tickAsync();
        } catch (Throwable error) {
            LOGGER.error("[TickHub] async driver crashed in level {}", level.dimension(), error);
        }
    }

    private void runKind(TickKind kind, long gameTime) {
        Object lock = lockFor(kind);
        if (lock == null) {
            byKind[kind.ordinal()].run(gameTime);
        } else {
            synchronized (lock) {
                byKind[kind.ordinal()].run(gameTime);
            }
        }
    }

    private @Nullable Object lockFor(TickKind kind) {
        return kind.async() ? this : null;
    }

    private void close() {
        detached = true;
        ScheduledFuture<?> future = asyncFuture;
        asyncFuture = null;
        if (future != null) {
            future.cancel(false);
        }
        for (TickKind kind : TickKind.VALUES) {
            Object lock = lockFor(kind);
            if (lock == null) {
                byKind[kind.ordinal()].clearAndCancel();
            } else {
                synchronized (lock) {
                    byKind[kind.ordinal()].clearAndCancel();
                }
            }
        }
    }

    static final class Entry implements TickHandle {

        final TickHub hub;
        final TickKind kind;
        final TickHook hook;
        int interval;
        int pendingInterval;
        volatile boolean suspended;
        volatile boolean cancelled;
        @Nullable
        ObjectArrayList<Entry> bucket;
        int bucketIndex = -1;
        boolean alertQueued;
        boolean dirtyQueued;
        boolean ranThisTick;

        Entry(TickHub hub, TickKind kind, int interval, TickHook hook) {
            this.hub = hub;
            this.kind = kind;
            this.interval = interval;
            this.hook = hook;
        }

        @Override
        public Level level() {
            return hub.level;
        }

        @Override
        public TickKind kind() {
            return kind;
        }

        @Override
        public int interval() {
            return interval;
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setInterval(int newInterval) {
            if (newInterval < 1) {
                throw new IllegalArgumentException("interval must be >= 1, got " + newInterval);
            }
            if (cancelled || hub.detached) {
                return;
            }
            Object lock = hub.lockFor(kind);
            if (lock == null) {
                pendingInterval = newInterval;
                hub.byKind[kind.ordinal()].queueDirty(this);
            } else {
                synchronized (lock) {
                    pendingInterval = newInterval;
                    hub.byKind[kind.ordinal()].queueDirty(this);
                }
            }
        }

        @Override
        public void suspend() {
            if (!cancelled) {
                suspended = true;
            }
        }

        @Override
        public void resume() {
            if (!cancelled) {
                suspended = false;
            }
        }

        @Override
        public void alert() {
            if (cancelled || hub.detached) {
                return;
            }
            Object lock = hub.lockFor(kind);
            if (lock == null) {
                hub.byKind[kind.ordinal()].queueAlert(this);
            } else {
                synchronized (lock) {
                    hub.byKind[kind.ordinal()].queueAlert(this);
                }
            }
        }

        @Override
        public void unsubscribe() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            Object lock = hub.lockFor(kind);
            if (lock == null) {
                hub.byKind[kind.ordinal()].queueDirty(this);
            } else {
                synchronized (lock) {
                    hub.byKind[kind.ordinal()].queueDirty(this);
                }
            }
        }
    }

    static final class KindBuckets {

        final IntArrayList intervals = new IntArrayList(2);
        final ObjectArrayList<ObjectArrayList<Entry>> buckets = new ObjectArrayList<>(2);
        final Int2ObjectOpenHashMap<ObjectArrayList<Entry>> byInterval = new Int2ObjectOpenHashMap<>();
        final ObjectArrayList<Entry> alertQueue = new ObjectArrayList<>(4);
        final ObjectArrayList<Entry> dirtyQueue = new ObjectArrayList<>(4);
        final ObjectArrayList<Entry> ranThisTick = new ObjectArrayList<>(4);

        void add(int interval, Entry entry) {
            ObjectArrayList<Entry> bucket = byInterval.get(interval);
            if (bucket == null) {
                bucket = new ObjectArrayList<>(4);
                byInterval.put(interval, bucket);
                int pos = lowerBound(intervals, interval);
                intervals.add(pos, interval);
                buckets.add(pos, bucket);
            }
            entry.bucket = bucket;
            entry.bucketIndex = bucket.size();
            bucket.add(entry);
        }

        void queueAlert(Entry entry) {
            if (entry.cancelled || entry.alertQueued) {
                return;
            }
            entry.alertQueued = true;
            alertQueue.add(entry);
        }

        void queueDirty(Entry entry) {
            if (entry.dirtyQueued) {
                return;
            }
            entry.dirtyQueued = true;
            dirtyQueue.add(entry);
        }

        void run(long gameTime) {
            drainDirtyQueue();
            runAlertQueue(gameTime);
            drainDirtyQueue();

            for (int i = 0, n = intervals.size(); i < n; i++) {
                int interval = intervals.getInt(i);
                if (gameTime % interval != 0L) {
                    continue;
                }
                ObjectArrayList<Entry> bucket = buckets.get(i);
                Object[] entries = bucket.elements();
                int size = bucket.size();
                for (int j = 0; j < size; j++) {
                    Entry entry = (Entry) entries[j];
                    if (entry.cancelled || entry.suspended || entry.ranThisTick) {
                        continue;
                    }
                    runEntry(entry, gameTime);
                }
            }

            drainDirtyQueue();
            clearRanThisTick();
        }

        void clearAndCancel() {
            cancelEntries(alertQueue);
            cancelEntries(dirtyQueue);
            for (int i = 0, n = buckets.size(); i < n; i++) {
                cancelEntries(buckets.get(i));
            }
            intervals.clear();
            buckets.clear();
            byInterval.clear();
            alertQueue.clear();
            dirtyQueue.clear();
            ranThisTick.clear();
        }

        private static void cancelEntries(ObjectArrayList<Entry> entries) {
            Object[] arr = entries.elements();
            for (int i = 0, n = entries.size(); i < n; i++) {
                Entry entry = (Entry) arr[i];
                entry.cancelled = true;
                entry.alertQueued = false;
                entry.dirtyQueued = false;
                entry.ranThisTick = false;
                entry.pendingInterval = 0;
                entry.bucket = null;
                entry.bucketIndex = -1;
            }
        }

        private void runAlertQueue(long gameTime) {
            int count = alertQueue.size();
            if (count == 0) {
                return;
            }
            Object[] entries = alertQueue.elements();
            for (int i = 0; i < count; i++) {
                Entry entry = (Entry) entries[i];
                entry.alertQueued = false;
                if (entry.cancelled) {
                    continue;
                }
                entry.ranThisTick = true;
                ranThisTick.add(entry);
                runEntry(entry, gameTime);
            }
            discardPrefix(alertQueue, count);
        }

        private static void runEntry(Entry entry, long gameTime) {
            if (entry.hook instanceof MachineTicker ticker) {
                ticker.runProfiledTick(gameTime, entry);
            } else {
                entry.hook.tick(gameTime, entry);
            }
        }

        private void clearRanThisTick() {
            Object[] entries = ranThisTick.elements();
            for (int i = 0, n = ranThisTick.size(); i < n; i++) {
                ((Entry) entries[i]).ranThisTick = false;
            }
            ranThisTick.clear();
        }

        private void drainDirtyQueue() {
            int count = dirtyQueue.size();
            if (count == 0) {
                return;
            }
            Object[] entries = dirtyQueue.elements();
            for (int i = 0; i < count; i++) {
                Entry entry = (Entry) entries[i];
                entry.dirtyQueued = false;
                if (entry.cancelled) {
                    entry.pendingInterval = 0;
                    remove(entry);
                    continue;
                }
                int newInterval = entry.pendingInterval;
                if (newInterval != 0) {
                    entry.pendingInterval = 0;
                    if (newInterval != entry.interval) {
                        reseat(entry, newInterval);
                    }
                }
            }
            dirtyQueue.clear();
        }

        private void reseat(Entry entry, int newInterval) {
            remove(entry);
            entry.interval = newInterval;
            add(newInterval, entry);
        }

        private void remove(Entry entry) {
            ObjectArrayList<Entry> bucket = entry.bucket;
            if (bucket == null) {
                return;
            }
            removeByIndex(bucket, entry);
            if (bucket.isEmpty()) {
                removeEmptyBucket(entry.interval, bucket);
            }
            entry.bucket = null;
            entry.bucketIndex = -1;
        }

        private void removeByIndex(ObjectArrayList<Entry> bucket, Entry entry) {
            int index = entry.bucketIndex;
            int lastIndex = bucket.size() - 1;
            if (index < 0 || index > lastIndex || bucket.get(index) != entry) {
                throw new IllegalStateException("TickHub bucket index is stale");
            }
            if (index != lastIndex) {
                Entry moved = bucket.get(lastIndex);
                bucket.set(index, moved);
                moved.bucketIndex = index;
            }
            bucket.size(lastIndex);
        }

        private void removeEmptyBucket(int interval, ObjectArrayList<Entry> bucket) {
            ObjectArrayList<Entry> current = byInterval.get(interval);
            if (current != bucket || !bucket.isEmpty()) {
                return;
            }
            int pos = lowerBound(intervals, interval);
            if (pos < intervals.size() && intervals.getInt(pos) == interval) {
                intervals.removeInt(pos);
                buckets.remove(pos);
                byInterval.remove(interval);
            }
        }

        private static int lowerBound(IntArrayList list, int value) {
            int lo = 0;
            int hi = list.size();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (list.getInt(mid) < value) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }

        private static void discardPrefix(ObjectArrayList<Entry> list, int count) {
            int size = list.size();
            if (count == size) {
                list.clear();
                return;
            }
            for (int i = count; i < size; i++) {
                list.set(i - count, list.get(i));
            }
            list.size(size - count);
        }
    }

    static final class TickHubAttachment {

        private static final Map<Level, TickHub> MAP = new IdentityHashMap<>();

        static synchronized void set(Level level, TickHub hub) {
            if (MAP.containsKey(level)) {
                throw new IllegalStateException("TickHub already exists for level " + level.dimension());
            }
            MAP.put(level, hub);
        }

        static synchronized @Nullable TickHub remove(Level level) {
            return MAP.remove(level);
        }

        static synchronized @Nullable TickHub get(Level level) {
            return MAP.get(level);
        }

        static synchronized List<TickHub> all() {
            return List.copyOf(MAP.values());
        }
    }
}
