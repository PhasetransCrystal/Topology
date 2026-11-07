package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * 检测引擎读取的结构视图。仅暴露方块状态、部件角色和成员位置，不向引擎传递实时方块实体。
 * 实时实现只能在世界所属线程使用；异步调用须使用不可变 CompiledSnapshot。成员通过位置返回，控制器在服务端线程重新解析实体。
 */
public interface StructureView {

    /** 蓝图世界锚点，即控制器位置。 */
    BlockPos controllerPos();

    /**
     * 一次读取状态、角色和成员；null 表示位置未加载（未知，不等于缺失）。
     * 引擎优先使用此接口，避免实时视图重复查询实体；独立访问器仍供谓词使用。
     */
    default @Nullable CellInfo cellInfo(BlockPos worldPos) {
        if (!isLoaded(worldPos)) {
            return null;
        }
        return new CellInfo(blockState(worldPos), roles(worldPos), member(worldPos));
    }

    /** 已加载空气必须返回空气状态；null 表示未提供状态事实，依赖状态的检测返回 UNKNOWN。 */
    @Nullable
    BlockState blockState(BlockPos worldPos);

    /** 位置所在区块是否已加载；false 表示未知。 */
    default boolean isLoaded(BlockPos worldPos) {
        return true;
    }

    Set<PartRole> roles(BlockPos worldPos);

    /** 该位置的机器成员及独占声明；不存在时返回 null。 */
    default @Nullable Member member(BlockPos worldPos) {
        return null;
    }

    /** 该位置是否存在机器成员。 */
    default boolean isMember(BlockPos worldPos) {
        return member(worldPos) != null;
    }

    default boolean hasCapability(BlockPos worldPos, PartRole capability) {
        return roles(worldPos).contains(capability);
    }

    default boolean isController(BlockPos worldPos) {
        return controllerPos().equals(worldPos);
    }

    /** 一个已加载位置的完整读取结果；state 可为空，表示读取实现未提供状态。 */
    record CellInfo(@Nullable BlockState state, Set<PartRole> roles, @Nullable Member member) {

        public CellInfo {
            roles = Set.copyOf(Objects.requireNonNull(roles, "cell roles"));
        }

        /** 该位置是否具有成员信息。 */
        public boolean isMember() {
            return member != null;
        }
    }

    /**
     * 一个机器成员的不可变世界位置 pos 及独占声明 exclusive。
     * 独占成员仅能被一个成型控制器预留，普通成员可共享。
     */
    record Member(BlockPos pos, boolean exclusive) {

        public Member {
            pos = Objects.requireNonNull(pos, "member position").immutable();
        }
    }
}
