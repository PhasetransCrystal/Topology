package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次完整检测的不可变结果，可跨线程传递，不持有实时方块实体。
 * 成员以世界位置表示；控制器在最终提交时通过位置解析实体。
 * <p>
 * formed 表示方块、角色数量和加载状态全部满足；matchedCount 包含控制器；missingCount 是最终布局中已加载但不匹配的总数。
 * unloadedCount 对未加载位置去重计数，unknownCount 还包含扩展能力尚不能判断的位置，因此不能和匹配、缺失数相加来推导布局大小。
 * missingCells 最多保留 64 个缺失样本，roleCounts 为已匹配位置的角色计数，partCount 为携带角色的位置数（同位置多个角色只算一次），members 为已匹配成员的世界位置。
 * repeats 保存本轮解析的副本数，bounds 包含最终布局中匹配、缺失和未知的所有位置，failureReason 表示引擎级阻塞。
 */
public record RecognitionResult(
                                boolean formed,
                                int matchedCount,
                                int missingCount,
                                int unloadedCount,
                                int unknownCount,
                                List<CellIssue> missingCells,
                                List<CellIssue> unknownCells,
                                Map<PartRole, Integer> roleCounts,
                                int partCount,
                                List<BlockPos> members,
                                RepeatResolution repeats,
                                Bounds bounds,
                                FailureReason failureReason,
                                DataSnapshot metadata) {

    public RecognitionResult {
        if (matchedCount < 0) {
            throw new IllegalArgumentException("Matched cell count must be non-negative");
        }
        if (missingCount < 0) {
            throw new IllegalArgumentException("Missing cell count must be non-negative");
        }
        if (unknownCount < unloadedCount) throw new IllegalArgumentException("Unknown count must include unloaded positions");
        if (unloadedCount < 0) {
            throw new IllegalArgumentException("Unloaded cell count must be non-negative");
        }
        missingCells = List.copyOf(Objects.requireNonNull(missingCells, "missing cells"));
        unknownCells = List.copyOf(unknownCells);
        if (unknownCells.size() > unknownCount) throw new IllegalArgumentException("Unknown sample cannot exceed unknown count");
        if (missingCells.size() > missingCount) {
            throw new IllegalArgumentException("Missing sample cannot exceed the missing count");
        }
        if (partCount < 0 || partCount > matchedCount) throw new IllegalArgumentException("Invalid matched part count");
        roleCounts = Map.copyOf(Objects.requireNonNull(roleCounts, "capability counts"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        repeats = Objects.requireNonNull(repeats, "repeat resolution");
        bounds = Objects.requireNonNull(bounds, "bounds");
        failureReason = Objects.requireNonNull(failureReason, "failure reason");
        Objects.requireNonNull(metadata, "metadata snapshot");
        if (formed && failureReason != FailureReason.NONE) {
            throw new IllegalArgumentException("A formed result cannot carry an engine failure reason");
        }
    }

    /** 朝向不支持时返回空结果；尚未扫描单元格，无成员或计数。 */
    public static RecognitionResult orientationUnsupported(RepeatResolution repeats) {
        return new RecognitionResult(
                false,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                Map.of(),
                0,
                List.of(),
                repeats,
                Bounds.empty(),
                FailureReason.ORIENTATION_UNSUPPORTED, DataSnapshot.defaults(List.of()));
    }

    /** 无法作出确定结论，应保留已确认结构并稍后重试。 */
    public boolean unknown() {
        return unknownCount > 0;
    }

    /** 最终布局的世界轴对齐边界。空边界使用反向区间（min 为 MAX、max 为 MIN），通过 isEmpty 判断。 */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        public static Bounds empty() {
            return new Bounds(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        public boolean isEmpty() {
            return minX > maxX;
        }

        /** 包含两个边界的最小边界。 */
        public Bounds union(Bounds other) {
            return new Bounds(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ));
        }
    }

    /** 模块只有满足或缺失两种状态。 */
    public enum ModuleState {
        ABSENT,
        SATISFIED
    }

    /**
     * 模块诊断报告。matched/total 仅表示不含控制器的布局位置进度，不改变模块的二元接入状态。
     * blockedBy 列出未满足的依赖，repeats 和 bounds 表示解析出的模块布局；未检测时边界为空。
     */
    public record ModuleReport(
                               String id,
                               boolean satisfied,
                               int matched,
                               int total,
                               List<String> blockedBy,
                               RepeatResolution repeats,
                               Bounds bounds) {

        public ModuleReport {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Module id must not be blank");
            }
            if (matched < 0 || total < 0 || matched > total) {
                throw new IllegalArgumentException("Invalid module match counts: " + matched + "/" + total);
            }
            blockedBy = List.copyOf(Objects.requireNonNull(blockedBy, "blocked dependencies"));
            repeats = Objects.requireNonNull(repeats, "module repeat resolution");
            bounds = Objects.requireNonNull(bounds, "module bounds");
            if (satisfied && (total == 0 || matched != total)) {
                throw new IllegalArgumentException("A satisfied module must match a non-empty cell set");
            }
        }

        /** 仅完整满足的模块视为已接入。 */
        public boolean present() {
            return satisfied;
        }

        /** 所有布局位置是否均已匹配；不表示角色数量或依赖约束也已满足。 */
        public boolean configured() {
            return total > 0 && matched == total;
        }

        /** 是否存在未满足的依赖模块。 */
        public boolean blocked() {
            return !blockedBy.isEmpty();
        }

        public ModuleState state() {
            return satisfied ? ModuleState.SATISFIED : ModuleState.ABSENT;
        }
    }

    /** 引擎级检测状态。NONE 仍可能存在普通方块或数量不匹配；CHUNK_UNLOADED 可能来自最终布局或额外增长探测。 */
    public enum FailureReason {
        /** 检测无引擎级阻塞，是否成型仍以 formed 为准。 */
        NONE,
        /** 某个规范状态的属性规则无法表达有效旋转，在逐格扫描前拒绝检测。 */
        ORIENTATION_UNSUPPORTED,
        /** 至少一个读取位置未加载。调用者应保留当前成型状态并延迟重试，不能当作确定损坏。 */
        CHUNK_UNLOADED,
        /** 扩展检测器返回未知；位置已加载，但所需事实尚不可用。 */
        CELL_UNKNOWN
    }

    public record CellIssue(
                            BlockPos localPos,
                            BlockPos worldPos,
                            Cell expected,
                            @Nullable BlockState actual, CellResult result) {

        public CellIssue {
            localPos = Objects.requireNonNull(localPos, "local position").immutable();
            worldPos = Objects.requireNonNull(worldPos, "world position").immutable();
            expected = Objects.requireNonNull(expected, "expected cell");
            Objects.requireNonNull(result, "cell result");
        }
    }
}
