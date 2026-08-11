package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.machine.resource.DirectResourceAccess;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipeSideRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One extraction port's per-tick transaction session: the engine opens at most one transaction
 * per port tick (lazily, on the first move that will actually transfer) and commits it after the
 * strategy finishes. {@code abort} drops the whole port tick; bookings already made for earlier
 * moves of the same tick stay in the display ledger (a conservative, self-correcting inaccuracy).
 */
interface PipeTxSession {

    @Nullable
    Transaction transactionOrOpen();

    void abort();
}

/**
 * One connected component of same-kind pipes, derived from saved-data roles by flood fill.
 * Owns the compact runtime arrays the per-tick engine runs on: adjacency, per-node throughput,
 * per-extractor BFS parent tables (the primary lane; saturated batches open further
 * node-disjoint lanes by residual BFS, so endpoint throughput equals the min-cut of the
 * component), and the tick ledger (path-occupancy budgets plus per-side flow statistics
 * double-buffered for Jade).
 *
 * <p>
 * Instances are immutable in shape: any topology-affecting change retires the network and a
 * fresh component is flooded lazily. Port-only changes refresh in place and bump
 * {@link #revision}, which invalidates extractor path tables lazily.
 */
public final class PipeNetwork<R extends Resource> {

    private static final Direction[] DIRECTIONS = Direction.values();
    /**
     * 单次批转移最多发起的增广搜索数:点不相交车道数受入口节点度(≤5)限制,留出份额钳制
     * 引发的零额复搜余量;真正的收束由"每次落账至少 1"与零额新路即收手保证,这里只是围栏。
     */
    private static final int MAX_AUGMENT_SEARCHES = 16;

    final MachineResourceType<R> resourceType;
    private final ServerLevel level;
    private final long[] nodePositions;
    private final Long2IntOpenHashMap indexByPos;
    private final int[] throughput;
    /** {@code nodeIndex * 6 + direction.ordinal()} → neighbor node index, or -1. */
    private final int[] adjacency;
    private final List<ExtractorPort<R>> extractors = new ArrayList<>();
    private final List<DestinationPort<R>> destinations = new ArrayList<>();

    int revision;
    boolean retired;

    private long tickStamp = Long.MIN_VALUE;
    /** Future reservation ledger; only allocated while the network has extraction ports. */
    private @Nullable PipeFutureLedger ledger;
    /** Display statistics window in ticks (= the ledger window; 1 without extractors). */
    private int displayWindow = 1;
    private long windowStartTick = Long.MIN_VALUE;
    private long[] windowFlowCurrent;
    private long[] windowFlowLast;
    private long[] windowNodeCurrent;
    private long[] windowNodeLast;
    private long windowMovedCurrent;
    private long windowMovedLast;
    /** 展示窗口脏标记:双缓冲只在持有过流量时才换页/清零,空闲网络免 O(节点数) 清扫。 */
    private boolean windowCurrentDirty;
    private boolean windowLastDirty;
    private long tickNanosThisTick;
    private long lastBatchNanos;
    private final int[] pathScratch;
    /** 与 {@link #pathScratch} 平行:path[i] 指向 path[i+1](靠抽取口一侧)的方向 ordinal,末位 -1。 */
    private final int[] pathDirScratch;
    private final int[] searchQueue;
    private final int[] searchPrev;
    private final int[] searchPrevDir;
    /** 残量搜索的世代戳访问标记:免逐次清零,主线程独占复用。 */
    private final int[] searchSeen;
    private int searchStamp;
    /** 本端口批已在各节点落账的总量(公平份额钳制);touched 列表使清零 O(触碰数)。 */
    private final int[] batchBooked;
    private final int[] batchTouched;
    private int batchTouchedCount;
    /** 残量搜索的预解析列基址,长度随账本窗口在 {@link #rebuildLedger} 同步。 */
    private int[] laneColumnScratch = new int[1];

    private PipeNetwork(MachineResourceType<R> resourceType, ServerLevel level, long[] nodePositions) {
        this.resourceType = resourceType;
        this.level = level;
        this.nodePositions = nodePositions;
        int count = nodePositions.length;
        this.indexByPos = new Long2IntOpenHashMap(count);
        this.indexByPos.defaultReturnValue(-1);
        this.throughput = new int[count];
        this.adjacency = new int[count * 6];
        Arrays.fill(adjacency, -1);
        this.windowFlowCurrent = new long[count * 6];
        this.windowFlowLast = new long[count * 6];
        this.windowNodeCurrent = new long[count];
        this.windowNodeLast = new long[count];
        this.pathScratch = new int[count];
        this.pathDirScratch = new int[count];
        this.searchQueue = new int[count];
        this.searchPrev = new int[count];
        this.searchPrevDir = new int[count];
        this.searchSeen = new int[count];
        this.batchBooked = new int[count];
        this.batchTouched = new int[count];
    }

    /**
     * Flood-fill the component containing {@code seedPos} over mutual LINK edges and build the
     * runtime arrays. Returns null when the seed is not a recorded node.
     */
    @SuppressWarnings("unchecked")
    public static @Nullable PipeNetwork<?> flood(ServerLevel level, PipeNetworksSavedData data, long seedPos) {
        PipeNodeRecord seed = data.node(seedPos);
        if (seed == null) {
            return null;
        }
        LongArrayList order = new LongArrayList(16);
        LongOpenHashSet visited = new LongOpenHashSet(16);
        order.add(seedPos);
        visited.add(seedPos);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int head = 0; head < order.size(); head++) {
            long pos = order.getLong(head);
            PipeNodeRecord record = data.node(pos);
            if (record == null) {
                continue;
            }
            for (Direction direction : DIRECTIONS) {
                if (record.role(direction) != PipeSideRole.LINK) {
                    continue;
                }
                cursor.set(pos).move(direction);
                long neighborPos = cursor.asLong();
                if (visited.contains(neighborPos)) {
                    continue;
                }
                PipeNodeRecord neighbor = data.node(neighborPos);
                if (neighbor == null || neighbor.role(direction.getOpposite()) != PipeSideRole.LINK) {
                    continue;
                }
                visited.add(neighborPos);
                order.add(neighborPos);
            }
        }

        MachineResourceType<?> kind = seed.definition().resourceType();
        PipeNetwork<?> network = new PipeNetwork<>((MachineResourceType<Resource>) kind, level, order.toLongArray());
        network.fill(data);
        return network;
    }

    private void fill(PipeNetworksSavedData data) {
        BlockCapability<ResourceHandler<R>, @Nullable Direction> capability = Objects.requireNonNull(resourceType.blockCapability(), "piped resource without block capability");
        int count = nodePositions.length;
        for (int i = 0; i < count; i++) {
            indexByPos.put(nodePositions[i], i);
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            long pos = nodePositions[i];
            PipeNodeRecord record = data.node(pos);
            if (record == null) {
                continue;
            }
            PipeDefinition definition = record.definition();
            throughput[i] = definition.nodeThroughput();
            for (Direction direction : DIRECTIONS) {
                PipeSideRole role = record.role(direction);
                if (role == PipeSideRole.NONE) {
                    continue;
                }
                cursor.set(pos).move(direction);
                switch (role) {
                    case LINK -> {
                        int neighborIndex = indexByPos.get(cursor.asLong());
                        if (neighborIndex >= 0) {
                            adjacency[i * 6 + direction.ordinal()] = neighborIndex;
                        }
                    }
                    case EXTRACT -> extractors.add(new ExtractorPort<>(
                            i, pos, direction, definition,
                            BlockCapabilityCache.create(capability, level, cursor.immutable(), direction.getOpposite())));
                    case DESTINATION -> destinations.add(new DestinationPort<>(
                            i, pos, direction,
                            BlockCapabilityCache.create(capability, level, cursor.immutable(), direction.getOpposite())));
                    default -> {}
                }
            }
        }
        rebuildLedger();
    }

    /**
     * (Re)size the future ledger to the widest aggregation window any extraction port's
     * definition offers — a network property derived from registration data, never a constant.
     * Resizing drops in-flight reservations (rare: port add/remove), which can transiently
     * over-admit for at most one window.
     */
    private void rebuildLedger() {
        int window = 1;
        for (int i = 0; i < extractors.size(); i++) {
            window = Math.max(window, extractors.get(i).definition.maxAggregationWindow());
        }
        if (extractors.isEmpty()) {
            ledger = null;
        } else if (ledger == null || ledger.window() != window) {
            ledger = new PipeFutureLedger(throughput, window);
        }
        if (laneColumnScratch.length < window) {
            laneColumnScratch = new int[window];
        }
        if (displayWindow != window) {
            displayWindow = window;
            Arrays.fill(windowFlowCurrent, 0L);
            Arrays.fill(windowFlowLast, 0L);
            Arrays.fill(windowNodeCurrent, 0L);
            Arrays.fill(windowNodeLast, 0L);
            windowMovedCurrent = 0L;
            windowMovedLast = 0L;
            windowCurrentDirty = false;
            windowLastDirty = false;
            windowStartTick = Long.MIN_VALUE;
        }
    }

    /**
     * Rebuild the extract/destination port lists contributed by one node in place (its LINK set
     * is unchanged). Bumps {@link #revision} so extractor path tables resync lazily.
     */
    public void refreshPortsAt(PipeNetworksSavedData data, long pos) {
        int nodeIndex = indexByPos.get(pos);
        if (nodeIndex < 0) {
            return;
        }
        extractors.removeIf(port -> port.nodeIndex == nodeIndex);
        destinations.removeIf(port -> port.nodeIndex == nodeIndex);
        PipeNodeRecord record = data.node(pos);
        if (record != null) {
            BlockCapability<ResourceHandler<R>, @Nullable Direction> capability = Objects.requireNonNull(resourceType.blockCapability(), "piped resource without block capability");
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (Direction direction : DIRECTIONS) {
                PipeSideRole role = record.role(direction);
                if (role == PipeSideRole.EXTRACT) {
                    cursor.set(pos).move(direction);
                    extractors.add(new ExtractorPort<>(nodeIndex, pos, direction, record.definition(),
                            BlockCapabilityCache.create(capability, level, cursor.immutable(), direction.getOpposite())));
                } else if (role == PipeSideRole.DESTINATION) {
                    cursor.set(pos).move(direction);
                    destinations.add(new DestinationPort<>(nodeIndex, pos, direction,
                            BlockCapabilityCache.create(capability, level, cursor.immutable(), direction.getOpposite())));
                }
            }
        }
        rebuildLedger();
        revision++;
    }

    public boolean retired() {
        return retired;
    }

    public int nodeCount() {
        return nodePositions.length;
    }

    public long[] nodePositions() {
        return nodePositions;
    }

    public int nodeIndexOf(long pos) {
        return indexByPos.get(pos);
    }

    public int extractorCount() {
        return extractors.size();
    }

    public int destinationCount() {
        return destinations.size();
    }

    public List<ExtractorPort<R>> extractors() {
        return extractors;
    }

    /**
     * Start this tick; idempotent per game tick. Display statistics double-buffer in
     * {@code displayWindow}-sized windows: the swap+clear runs once per window, so per-tick cost
     * is a couple of compares.
     */
    public void beginTick(long gameTime) {
        if (tickStamp == gameTime) {
            return;
        }
        tickStamp = gameTime;
        if (windowStartTick == Long.MIN_VALUE) {
            windowStartTick = gameTime;
        } else if (gameTime - windowStartTick >= (long) displayWindow * 2) {
            // Long idle gap: both buffers are stale; only ever-dirty buffers need the clear.
            if (windowCurrentDirty || windowLastDirty) {
                Arrays.fill(windowFlowCurrent, 0L);
                Arrays.fill(windowFlowLast, 0L);
                Arrays.fill(windowNodeCurrent, 0L);
                Arrays.fill(windowNodeLast, 0L);
                windowMovedCurrent = 0L;
                windowMovedLast = 0L;
                windowCurrentDirty = false;
                windowLastDirty = false;
            }
            windowStartTick = gameTime;
        } else if (gameTime - windowStartTick >= displayWindow) {
            if (windowCurrentDirty) {
                long[] flowSwap = windowFlowLast;
                windowFlowLast = windowFlowCurrent;
                windowFlowCurrent = flowSwap;
                Arrays.fill(windowFlowCurrent, 0L);
                long[] nodeSwap = windowNodeLast;
                windowNodeLast = windowNodeCurrent;
                windowNodeCurrent = nodeSwap;
                Arrays.fill(windowNodeCurrent, 0L);
                windowMovedLast = windowMovedCurrent;
                windowMovedCurrent = 0L;
                windowLastDirty = true;
                windowCurrentDirty = false;
            } else if (windowLastDirty) {
                // 流量停满一个整窗:把上窗读数翻成安静窗,此后双清窗滚动零成本。
                Arrays.fill(windowFlowLast, 0L);
                Arrays.fill(windowNodeLast, 0L);
                windowMovedLast = 0L;
                windowLastDirty = false;
            }
            windowStartTick += displayWindow;
        }
        if (tickNanosThisTick > 0) {
            lastBatchNanos = tickNanosThisTick;
            tickNanosThisTick = 0L;
        }
    }

    /** Book wall-clock engine time spent on this network this tick (performance display only). */
    public void addTickNanos(long nanos) {
        tickNanosThisTick += nanos;
    }

    /** Lazily (re)build the BFS parent table and distance-ordered destination view of a port. */
    void ensurePaths(ExtractorPort<R> port) {
        if (port.pathRevision == revision && port.parent != null) {
            return;
        }
        int count = nodePositions.length;
        if (port.parent == null || port.parent.length != count) {
            port.parent = new int[count];
            port.parentDir = new byte[count];
            port.distance = new int[count];
        }
        Arrays.fill(port.parent, -1);
        Arrays.fill(port.distance, Integer.MAX_VALUE);
        int[] queue = new int[count];
        int head = 0;
        int tail = 0;
        queue[tail++] = port.nodeIndex;
        port.distance[port.nodeIndex] = 0;
        while (head < tail) {
            int current = queue[head++];
            int base = current * 6;
            for (int d = 0; d < 6; d++) {
                int neighbor = adjacency[base + d];
                if (neighbor >= 0 && port.distance[neighbor] == Integer.MAX_VALUE) {
                    port.distance[neighbor] = port.distance[current] + 1;
                    port.parent[neighbor] = current;
                    port.parentDir[neighbor] = (byte) DIRECTIONS[d].getOpposite().ordinal();
                    queue[tail++] = neighbor;
                }
            }
        }

        int destinationTotal = destinations.size();
        long[] sorted = new long[destinationTotal];
        int reachable = 0;
        for (int i = 0; i < destinationTotal; i++) {
            int distance = port.distance[destinations.get(i).nodeIndex];
            if (distance != Integer.MAX_VALUE) {
                sorted[reachable++] = ((long) distance << 32) | i;
            }
        }
        Arrays.sort(sorted, 0, reachable);
        int[] order = new int[reachable];
        for (int i = 0; i < reachable; i++) {
            order[i] = (int) sorted[i];
        }
        port.destOrder = order;
        port.laneSets = new LaneSet[order.length];
        port.pathRevision = revision;
    }

    /** 一组已发现车道(同一抽取口×目的地):nodes/dirs 平行,目的地在前、抽取口收尾。 */
    static final class LaneSet {

        int[][] nodes = new int[2][];
        int[][] dirs = new int[2][];
        int count;
        /** 残量搜索已确认无更多车道;仅单抽取口网络在稳态信任此判定免搜。 */
        boolean complete;
    }

    DestinationPort<R> orderedDestination(ExtractorPort<R> port, int orderIndex) {
        return destinations.get(port.destOrder[orderIndex]);
    }

    /**
     * Read-only acceptance bound of one ordered destination for {@code resource}, early-exited
     * at {@code enough}. 0 when the destination is unloaded, full or an output-only view.
     */
    int destinationAcceptance(ExtractorPort<R> port, int orderIndex, R resource, int enough) {
        ResourceHandler<R> target = orderedDestination(port, orderIndex).cache.getCapability();
        if (target == null || enough <= 0) {
            return 0;
        }
        if (target instanceof DirectResourceAccess<?> direct) {
            @SuppressWarnings("unchecked")
            DirectResourceAccess<R> directTarget = (DirectResourceAccess<R>) direct;
            if (directTarget.directReady()) {
                return (int) Math.min(enough, Math.max(0, directTarget.directFreeFor(resource)));
            }
        }
        return (int) Math.min(enough, acceptanceUpperBound(target, resource, enough));
    }

    /**
     * Execute one transfer attempt from the extractor's source handler to the ordered
     * destination. Routing walks the per-(port, destination) cached lane set first — lane 0 is
     * the BFS-tree shortest path, later lanes were discovered by residual BFS over nodes that
     * still had per-tick headroom (greedy shortest-first, no flow cancellation — exact min-cut
     * max-flow on lattice-like builds, never below the old single-lane behavior elsewhere).
     * Discovery only runs while batch budget remains beyond the cached lanes' capacity; a
     * single-extractor network that has confirmed its lane set complete never searches again
     * until the topology revision bumps, so steady-state batch cost is O(lane length), not
     * O(component size). Every lane booking is capped by the future ledger's per-tick headroom
     * along that lane (both endpoints included) summed over the port's aggregation interval,
     * and by the batch's remaining fair-slice total per node ({@code batchBooked}) — one batch
     * can never sweep another port's share by stacking lanes. Endpoint-limited moves (source
     * drained, destination full, aborted session) stop the lane walk immediately.
     */
    int execute(ExtractorPort<R> port, ResourceHandler<R> source, int orderIndex, int requested,
                int interval, PipeTxSession session) {
        DestinationPort<R> destination = orderedDestination(port, orderIndex);
        ResourceHandler<R> target = destination.cache.getCapability();
        PipeFutureLedger futureLedger = ledger;
        if (target == null || requested <= 0 || futureLedger == null) {
            return 0;
        }
        boolean stats = PipeEngineStats.enabled;
        int boundedInterval = Math.min(Math.max(1, interval), futureLedger.window());
        LaneSet lanes = laneSetFor(port, orderIndex, destination.nodeIndex);
        int remaining = requested;
        int totalMoved = 0;
        // 已缓存车道:逐条 询价→搬运→落账;额度暂被他口占去的车道询价为 0,跳过即可。
        for (int i = 0; i < lanes.count && remaining > 0; i++) {
            long result = bookLane(port, destination, source, target, futureLedger,
                    lanes.nodes[i], lanes.dirs[i], boundedInterval, remaining, session);
            int cap = (int) (result >>> 32);
            int moved = (int) result;
            totalMoved += moved;
            remaining -= moved;
            if (cap > 0 && moved < cap) {
                return totalMoved; // 端点受限(源空/目的满/中止):换车道无补。
            }
        }
        // 预算未尽:残量搜索发现新车道并落缓存。单口网络一旦确认完備,稳态零搜索;
        // 多口网络的占用随他口逐批变化,保留再发现(每批至多 MAX_AUGMENT_SEARCHES 次)。
        if (remaining <= 0 || (lanes.complete && extractors.size() == 1)) {
            return totalMoved;
        }
        for (int search = 0; search < MAX_AUGMENT_SEARCHES && remaining > 0 && lanes.count < MAX_AUGMENT_SEARCHES; search++) {
            long mark = stats ? System.nanoTime() : 0;
            int laneLength = residualLane(port, destination.nodeIndex, boundedInterval, futureLedger);
            if (stats) {
                PipeEngineStats.routeNanos += System.nanoTime() - mark;
            }
            if (laneLength <= 0) {
                lanes.complete = true;
                break;
            }
            int laneIndex = appendLane(lanes, laneLength);
            long result = bookLane(port, destination, source, target, futureLedger,
                    lanes.nodes[laneIndex], lanes.dirs[laneIndex], boundedInterval, remaining, session);
            int cap = (int) (result >>> 32);
            int moved = (int) result;
            totalMoved += moved;
            remaining -= moved;
            if (cap <= 0 || moved < cap) {
                break; // 对齐边缘的零额新路保守收手(车道已入缓存,下批再试);端点受限同此。
            }
        }
        return totalMoved;
    }

    /**
     * 在一条车道上完成 询价→搬运→落账(账本预约、展示统计、批内份额记账)三步。
     * 返回 {@code (cap << 32) | moved}(两者非负);cap 为 0 表示该车道本批无额度。
     */
    private long bookLane(ExtractorPort<R> port, DestinationPort<R> destination,
                          ResourceHandler<R> source, ResourceHandler<R> target, PipeFutureLedger futureLedger,
                          int[] laneNodes, int[] laneDirs, int interval, int requested, PipeTxSession session) {
        boolean stats = PipeEngineStats.enabled;
        long mark = stats ? System.nanoTime() : 0;
        int cap = futureLedger.peekAvailable(
                tickStamp, laneNodes, laneNodes.length, interval, requested, extractors.size(), batchBooked);
        if (stats) {
            PipeEngineStats.bucketNanos += System.nanoTime() - mark;
        }
        if (cap <= 0) {
            return 0;
        }
        mark = stats ? System.nanoTime() : 0;
        int moved = moveOnce(source, target, cap, session, port.cachedFilter);
        if (stats) {
            long now = System.nanoTime();
            PipeEngineStats.transactionNanos += now - mark;
            mark = now;
        }
        if (moved <= 0) {
            return (long) cap << 32;
        }
        futureLedger.commit(tickStamp, laneNodes, laneNodes.length, interval, moved);
        bookFlow(port, destination, laneNodes, laneDirs, moved);
        recordBatchBooked(laneNodes, moved);
        if (stats) {
            PipeEngineStats.bookNanos += System.nanoTime() - mark;
        }
        return ((long) cap << 32) | moved;
    }

    /** 取(建)该抽取口×目的地的车道缓存;新建时以父表最短路为第 0 条车道。 */
    private LaneSet laneSetFor(ExtractorPort<R> port, int orderIndex, int destNode) {
        LaneSet[] sets = port.laneSets;
        LaneSet set = sets[orderIndex];
        if (set == null) {
            set = new LaneSet();
            appendLane(set, treeLane(port, destNode));
            sets[orderIndex] = set;
        }
        return set;
    }

    /** 主车道:抽取口 BFS 父表上的最短路写入 scratch(目的地在前、抽取口收尾),返回长度。 */
    private int treeLane(ExtractorPort<R> port, int destNode) {
        int pathLength = 0;
        for (int node = destNode; node >= 0; node = port.parent[node]) {
            pathDirScratch[pathLength] = node == port.nodeIndex ? -1 : port.parentDir[node];
            pathScratch[pathLength++] = node;
            if (node == port.nodeIndex) {
                break;
            }
        }
        return pathLength;
    }

    /** 把 scratch 里的一条车道按实际长度拷入缓存,返回其下标。 */
    private int appendLane(LaneSet set, int pathLength) {
        if (set.count == set.nodes.length) {
            set.nodes = Arrays.copyOf(set.nodes, set.count * 2);
            set.dirs = Arrays.copyOf(set.dirs, set.count * 2);
        }
        set.nodes[set.count] = Arrays.copyOf(pathScratch, pathLength);
        set.dirs[set.count] = Arrays.copyOf(pathDirScratch, pathLength);
        return set.count++;
    }

    /** 端口批开始:清空上一批的公平份额记账(O(上批触碰节点数))。tick 引擎在策略分发前调用。 */
    void beginPortBatch() {
        for (int i = 0; i < batchTouchedCount; i++) {
            batchBooked[batchTouched[i]] = 0;
        }
        batchTouchedCount = 0;
    }

    /** 把一条车道的落账量计入本批份额记账。 */
    private void recordBatchBooked(int[] laneNodes, int moved) {
        for (int node : laneNodes) {
            if (batchBooked[node] == 0) {
                batchTouched[batchTouchedCount++] = node;
            }
            batchBooked[node] += moved;
        }
    }

    /** 把一条车道的实绩登入展示窗口:节点占用、沿途两侧边流量、端点侧流量与网络合计。 */
    private void bookFlow(ExtractorPort<R> port, DestinationPort<R> destination,
                          int[] laneNodes, int[] laneDirs, int moved) {
        for (int p = 0; p < laneNodes.length; p++) {
            windowNodeCurrent[laneNodes[p]] += moved;
        }
        for (int p = 0; p + 1 < laneNodes.length; p++) {
            int towardParent = laneDirs[p];
            windowFlowCurrent[laneNodes[p] * 6 + towardParent] += moved;
            windowFlowCurrent[laneNodes[p + 1] * 6 + DIRECTIONS[towardParent].getOpposite().ordinal()] += moved;
        }
        windowFlowCurrent[port.nodeIndex * 6 + port.side.ordinal()] += moved;
        windowFlowCurrent[destination.nodeIndex * 6 + destination.side.ordinal()] += moved;
        windowMovedCurrent += moved;
        windowCurrentDirty = true;
    }

    /**
     * 在"未来窗口内仍有逐 tick 物理余量、且本批公平份额未耗尽"的节点子图上做一次 BFS,
     * 找下一条抽取口→目的地车道;路径写入 {@code pathScratch}/{@code pathDirScratch}
     * (目的地在前、抽取口收尾,与父表主车道同序),返回路径长度,无路返回 0。已订满或
     * 份额用尽的节点被剪枝,所以新车道与先前吃满的车道天然点不相交;两端节点同样要过门。
     */
    private int residualLane(ExtractorPort<R> port, int destNode, int interval, PipeFutureLedger futureLedger) {
        int start = port.nodeIndex;
        if (start == destNode) {
            return 0;
        }
        int baseCount = futureLedger.headroomBases(tickStamp, interval, laneColumnScratch);
        int divisor = Math.max(1, extractors.size());
        if (!lanePassable(start, interval, divisor, futureLedger, baseCount)) {
            return 0;
        }
        int stamp = ++searchStamp;
        if (stamp == Integer.MAX_VALUE) {
            Arrays.fill(searchSeen, 0);
            searchStamp = 1;
            stamp = 1;
        }
        int head = 0;
        int tail = 0;
        searchQueue[tail++] = start;
        searchSeen[start] = stamp;
        while (head < tail) {
            int current = searchQueue[head++];
            int base = current * 6;
            for (int d = 0; d < 6; d++) {
                int neighbor = adjacency[base + d];
                if (neighbor < 0 || searchSeen[neighbor] == stamp) {
                    continue;
                }
                searchSeen[neighbor] = stamp;
                if (!lanePassable(neighbor, interval, divisor, futureLedger, baseCount)) {
                    continue;
                }
                searchPrev[neighbor] = current;
                searchPrevDir[neighbor] = DIRECTIONS[d].getOpposite().ordinal();
                if (neighbor == destNode) {
                    return reconstructLane(start, destNode);
                }
                searchQueue[tail++] = neighbor;
            }
        }
        return 0;
    }

    /** 车道可通行 = 未来窗口仍有物理余量 且 本批在该节点的公平份额总额未耗尽。 */
    private boolean lanePassable(int node, int interval, int divisor, PipeFutureLedger futureLedger, int baseCount) {
        return futureLedger.hasHeadroomAt(node, laneColumnScratch, baseCount) && Math.max(1, throughput[node] / divisor) * interval > batchBooked[node];
    }

    private int reconstructLane(int start, int destNode) {
        int length = 0;
        int node = destNode;
        while (node != start) {
            pathScratch[length] = node;
            pathDirScratch[length] = searchPrevDir[node];
            length++;
            node = searchPrev[node];
        }
        pathScratch[length] = start;
        pathDirScratch[length] = -1;
        return length + 1;
    }

    /**
     * One move of up to {@code maxAmount} units, read-first: per source index it computes a
     * zero-allocation upper bound from {@code getAmountAsLong}/{@code getCapacityAsLong} reads
     * (bounds may overestimate — they only prune) and opens the single real transaction only
     * when something can actually move. Steady-state "source empty" / "targets full" ticks
     * therefore never touch the transaction machinery. A partial insert is compensated by
     * re-inserting into the source inside the same transaction; if even that fails the whole
     * transaction aborts with zero side effects.
     *
     * <p>
     * {@code filter} (the port's compiled white/black lists, null = pass-all) gates which
     * source resources may move: the indexed loop skips non-matching slots; the direct lane
     * stops at a non-matching front kind and falls through here, same as an unplaceable front.
     */
    private int moveOnce(ResourceHandler<R> source, ResourceHandler<R> target, int maxAmount,
                         PipeTxSession session, @Nullable Predicate<R> filter) {
        boolean stats = PipeEngineStats.enabled;
        // Fast lane: both ends are our own buffers (directly or through a directional view) and
        // no transaction is open on this thread — move directly, with zero transaction/journal
        // machinery. The lifecycle guard keeps direct writes from racing an open journal (e.g.
        // when an earlier move of this port tick already opened the transactional path).
        int directMoved = 0;
        if (!(source instanceof DirectResourceAccess<?>) || !(target instanceof DirectResourceAccess<?>)) {
            if (stats) {
                PipeEngineStats.directSkipType++;
            }
        }
        if (source instanceof DirectResourceAccess<?> rawSource && target instanceof DirectResourceAccess<?> rawTarget) {
            @SuppressWarnings("unchecked")
            DirectResourceAccess<R> directSource = (DirectResourceAccess<R>) rawSource;
            @SuppressWarnings("unchecked")
            DirectResourceAccess<R> directTarget = (DirectResourceAccess<R>) rawTarget;
            if (stats) {
                if (!directSource.directReady() || !directTarget.directReady()) {
                    PipeEngineStats.directSkipReady++;
                } else if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
                    PipeEngineStats.directSkipLifecycle++;
                }
            }
            if (directSource.directReady() && directTarget.directReady() && Transaction.getLifecycle() == Transaction.Lifecycle.NONE) {
                directMoved = directMove(directSource, directTarget, maxAmount, filter);
                if (stats && directMoved > 0) {
                    PipeEngineStats.directMoves++;
                }
                if (directMoved >= maxAmount) {
                    return directMoved;
                }
                // Fall through: a mixed-kind source whose FRONT kind the target rejects stops
                // the direct loop early — finish the remainder through the indexed transactional
                // loop below so semantics stay exactly equal to the pure transactional path
                // (later kinds still move). The common single-kind case never reaches here with
                // anything left to do, so the extra pass is a handful of empty reads.
            }
        }
        int moved = directMoved;
        R boundedResource = null;
        long boundedAcceptance = 0;
        int size = source.size();
        for (int index = 0; index < size && moved < maxAmount; index++) {
            R resource = source.getResource(index);
            if (resource.isEmpty()) {
                continue;
            }
            if (filter != null && !filter.test(resource)) {
                continue;
            }
            long have = source.getAmountAsLong(index);
            if (have <= 0) {
                continue;
            }
            int budget = maxAmount - moved;
            long mark = stats ? System.nanoTime() : 0;
            // The acceptance scan early-exits at the budget and is reused while consecutive
            // source indices hold the same resource (the common homogeneous-source case).
            if (boundedResource == null || !boundedResource.equals(resource)) {
                boundedResource = resource;
                boundedAcceptance = acceptanceUpperBound(target, resource, budget);
            }
            if (stats) {
                long now = System.nanoTime();
                PipeEngineStats.boundNanos += now - mark;
                mark = now;
            }
            if (boundedAcceptance <= 0) {
                continue;
            }
            int want = (int) Math.min(budget, Math.min(have, boundedAcceptance));
            if (want <= 0) {
                continue;
            }
            Transaction transaction = session.transactionOrOpen();
            if (transaction == null) {
                return moved; // this port tick was aborted earlier
            }
            if (stats) {
                long now = System.nanoTime();
                PipeEngineStats.openNanos += now - mark;
                mark = now;
            }
            long extracted = ResourceHandlerLongOps.extract(
                    source, index, resource, want, transaction);
            if (stats) {
                long now = System.nanoTime();
                PipeEngineStats.extractNanos += now - mark;
                mark = now;
            }
            if (extracted <= 0) {
                continue;
            }
            long inserted = ResourceHandlerLongOps.insert(target, resource, extracted, transaction);
            if (stats) {
                long now = System.nanoTime();
                PipeEngineStats.insertNanos += now - mark;
            }
            if (inserted < extracted) {
                long returned = ResourceHandlerLongOps.insert(
                        source, index, resource, extracted - inserted, transaction);
                if (returned != extracted - inserted) {
                    // Cannot compensate (one-way source view + picky target): drop the port tick.
                    session.abort();
                    return 0;
                }
            }
            moved += Math.toIntExact(inserted);
            boundedAcceptance -= inserted;
        }
        return moved;
    }

    /**
     * Transaction-free move between two of our own buffers; see {@link DirectResourceAccess}.
     * Loops over resource kinds in slot order (extracting one kind exposes the next), stopping
     * when the budget is spent or the front kind cannot be placed or fails the port filter — a
     * deliberate conservative cut: the indexed transactional fall-through in {@code moveOnce}
     * finishes whatever sits behind a blocked front, so semantics stay equal to the pure
     * transactional path.
     */
    private int directMove(DirectResourceAccess<R> source, DirectResourceAccess<R> target, int maxAmount,
                           @Nullable Predicate<R> filter) {
        long moved = 0L;
        while (moved < maxAmount) {
            R resource = source.directResource();
            if (resource.isEmpty()) {
                break;
            }
            if (filter != null && !filter.test(resource)) {
                break;
            }
            long have = source.directAmount();
            if (have <= 0) {
                break;
            }
            long free = target.directFreeFor(resource);
            if (free <= 0) {
                break;
            }
            long want = Math.min((long) maxAmount - moved, Math.min(have, free));
            long taken = source.directExtract(resource, want);
            if (taken <= 0L) {
                break;
            }
            if (taken > want) {
                throw new IllegalStateException("Direct source extracted " + taken + " for a request of " + want);
            }
            long put = target.directInsert(resource, taken);
            if (put < 0L || put > taken) {
                throw new IllegalStateException("Direct target inserted " + put + " for an extracted amount of " + taken);
            }
            if (put < taken) {
                // Guaranteed to fit by the interface contract (we just removed it); views keep
                // this path ungated even on extract-only ports.
                long shortfall = taken - put;
                long returned = source.directGiveBack(resource, shortfall);
                if (returned != shortfall) {
                    throw new IllegalStateException("Direct source accepted only " + returned + " of a required " + shortfall + " give-back");
                }
                moved += put;
                break;
            }
            moved += put;
        }
        return Math.toIntExact(moved);
    }

    /**
     * Read-only estimate of how much of {@code resource} the target could accept right now:
     * the sum of {@code capacity - amount} over indices that are empty or already hold the
     * resource, early-exiting once {@code enough} is reached. May overestimate (capacity is a
     * hint); never used for accounting, only to skip transactions that cannot move anything.
     */
    private long acceptanceUpperBound(ResourceHandler<R> target, R resource, int enough) {
        long acceptance = 0;
        int size = target.size();
        for (int index = 0; index < size; index++) {
            R slot = target.getResource(index);
            if (slot.isEmpty() || slot.equals(resource)) {
                long free = target.getCapacityAsLong(index, resource) - target.getAmountAsLong(index);
                if (free > 0) {
                    acceptance += free;
                    if (acceptance >= enough) {
                        return acceptance;
                    }
                }
            }
        }
        return acceptance;
    }

    // --- Jade / inspection accessors (cold path) ---

    public long tickStamp() {
        return tickStamp;
    }

    /** Length in ticks of the display statistics window (= the ledger window). */
    public int displayWindowTicks() {
        return displayWindow;
    }

    /** Total moved across the network during the last completed display window. */
    public long windowMovedLast() {
        return windowMovedLast;
    }

    /** Engine nanos of the most recent tick that actually executed batches. */
    public long lastBatchNanos() {
        return lastBatchNanos;
    }

    public int throughputOf(int nodeIndex) {
        return throughput[nodeIndex];
    }

    /** 勘测分析的包内邻接读数:{@code nodeIndex} 沿方向 ordinal 的邻位下标,无则 -1。 */
    int neighborOf(int nodeIndex, int direction) {
        return adjacency[nodeIndex * 6 + direction];
    }

    /** Flow through one node side during the last completed display window. */
    public long windowFlowLast(int nodeIndex, Direction side) {
        return windowFlowLast[nodeIndex * 6 + side.ordinal()];
    }

    /** Amount routed through one node during the last completed display window. */
    public long windowNodeUsedLast(int nodeIndex) {
        return windowNodeLast[nodeIndex];
    }

    /** Highest node utilisation of the last window, percent of {@code throughput x window}. */
    public int bottleneckPercentLastWindow() {
        int best = 0;
        for (int i = 0; i < windowNodeLast.length; i++) {
            long limit = (long) throughput[i] * displayWindow;
            if (limit > 0) {
                best = Math.max(best, (int) (windowNodeLast[i] * 100L / limit));
            }
        }
        return best;
    }

    public static final class ExtractorPort<R extends Resource> {

        final int nodeIndex;
        final long pos;
        final Direction side;
        final PipeDefinition definition;
        final BlockCapabilityCache<ResourceHandler<R>, @Nullable Direction> cache;
        int cursor;
        /** Resolved strategy config, cached against the runtime's config generation counter. */
        @Nullable
        PipePortStrategyConfig cachedConfig;
        /** Compiled white/black filter (null = pass-all), cached on the same generation. */
        @Nullable
        Predicate<R> cachedFilter;
        long cachedConfigGeneration = -1;
        int pathRevision = -1;
        int @Nullable [] parent;
        byte @Nullable [] parentDir;
        int @Nullable [] distance;
        int[] destOrder = new int[0];
        /** 已发现车道缓存(按 destOrder 下标),随 {@link #ensurePaths} 的 revision 重建作废。 */
        LaneSet[] laneSets = new LaneSet[0];

        ExtractorPort(int nodeIndex, long pos, Direction side, PipeDefinition definition,
                      BlockCapabilityCache<ResourceHandler<R>, @Nullable Direction> cache) {
            this.nodeIndex = nodeIndex;
            this.pos = pos;
            this.side = side;
            this.definition = definition;
            this.cache = cache;
        }

        public long pos() {
            return pos;
        }

        public Direction side() {
            return side;
        }

        public PipeDefinition definition() {
            return definition;
        }
    }

    static final class DestinationPort<R extends Resource> {

        final int nodeIndex;
        final long pos;
        final Direction side;
        final String key;
        final BlockCapabilityCache<ResourceHandler<R>, @Nullable Direction> cache;

        DestinationPort(int nodeIndex, long pos, Direction side,
                        BlockCapabilityCache<ResourceHandler<R>, @Nullable Direction> cache) {
            this.nodeIndex = nodeIndex;
            this.pos = pos;
            this.side = side;
            BlockPos blockPos = BlockPos.of(pos).relative(side);
            this.key = blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ() + "," + side.getSerializedName();
            this.cache = cache;
        }
    }
}
