package net.ptcrys.topo.api.machine.multiblock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** 按世界持有纯占地索引与脏标记集合，服务值不反向持有弱键世界。事件订阅由 MultiblockChangeWatcher 负责，并在世界卸载时清理绑定。所有绑定方法共用锁；返回的服务仅限世界线程使用。 */
public final class MultiblockLevelBinder {

    private static final Map<Level, Services> SERVICES = new WeakHashMap<>();

    private MultiblockLevelBinder() {}

    /** 取得世界服务，首次访问时创建。 */
    public static synchronized Services services(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return SERVICES.computeIfAbsent(level, ignored -> new Services(new MultiblockClaimIndex(), new MultiblockRecheckDirtySet()));
    }

    /** 只查询已有服务，不创建；事件入口据此跳过没有多方块活动的世界。 */
    public static synchronized @Nullable Services servicesIfPresent(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return SERVICES.get(level);
    }

    /** 丢弃世界服务；后续访问会重新建立。 */
    public static synchronized void forget(Level level) {
        SERVICES.remove(Objects.requireNonNull(level, "level"));
    }

    /** 同一世界的占地与脏标记服务。 */
    public record Services(MultiblockClaimIndex claims, MultiblockRecheckDirtySet rechecks) {}
}
