package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.api.async.TopoAsyncExecutors;
import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureMetadata;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 大型稳定布局的异步检测服务。世界线程先采集不可变 CompiledSnapshot，工作线程仅计算结果，提交、预留及成员接入仍在服务端线程执行。
 * 实时视图不允许提交给工作线程；小结构使用同步路径，阈值由 MULTIBLOCK.asyncCellThreshold 配置。
 */
public final class AsyncStructureService {

    private AsyncStructureService() {}

    /** 在结构工作线程池检测快照，朝向选择与同步路径共用同一方法。 */
    public static CompletableFuture<Outcome> recognizeAsync(Captures captures, Blueprint blueprint) {
        return CompletableFuture.supplyAsync(() -> recognizePreferringMirror(captures, blueprint), TopoAsyncExecutors.structureExecutor());
    }

    /** 按快照所属朝向执行快速维护，不创建完整诊断及成员集合。整体本体与模块检测使用 StructureDetection。 */
    public static CompletableFuture<StructureEngine.Verification> verifyAsync(
                                                                              CompiledSnapshot snapshot, Blueprint blueprint) {
        return verifyAsync(snapshot, blueprint, StructureMetadata.defaults(List.of(blueprint)));
    }

    /** 在工作线程用已确认基线校验，只读取快照，不修改正式字段。 */
    public static CompletableFuture<StructureEngine.Verification> verifyAsync(CompiledSnapshot snapshot, Blueprint blueprint, DataSnapshot metadata) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(blueprint, "blueprint");
        return CompletableFuture.supplyAsync(
                () -> StructureEngine.verify(snapshot, blueprint, snapshot.compiled().orientation(), metadata.verify()), TopoAsyncExecutors.structureExecutor());
    }

    /** 根据主快照及镜像快照识别，委托给共用的朝向选择。 */
    public static Outcome recognizePreferringMirror(Captures captures, Blueprint blueprint) {
        Objects.requireNonNull(captures, "captures");
        Objects.requireNonNull(blueprint, "blueprint");
        return recognizePreferringMirror(captures.primary(), blueprint, captures.primary().compiled().orientation(),
                captures::mirrored);
    }

    /**
     * 同步与异步共用的朝向选择。主朝向成型时返回；否则尝试镜像，确定成型优先于未知；两者均确定失败时选择匹配比例较高的布局。
     * 镜像未知必须保留未知结果，避免把区块未加载误判为结构损坏。实时视图的调用者须在世界线程执行。
     */
    public static Outcome recognizePreferringMirror(StructureView primary, Blueprint blueprint, Orientation orientation,
                                                    Supplier<? extends StructureView> mirrorView) {
        RecognitionResult result = StructureEngine.recognize(primary, blueprint, orientation);
        if (result.formed()) {
            return new Outcome(result, orientation);
        }
        Orientation mirrored = orientation.mirrored();
        RecognitionResult mirrorResult = StructureEngine.recognize(Objects.requireNonNull(mirrorView.get(), "mirror view"), blueprint, mirrored);
        if (mirrorResult.formed() || mirrorResult.unknown()) return new Outcome(mirrorResult, mirrored);
        if (result.unknown()) return new Outcome(result, orientation);
        long normalTotal = blueprint.cellCount(result.repeats()), mirrorTotal = blueprint.cellCount(mirrorResult.repeats());
        return (long) mirrorResult.matchedCount() * normalTotal > (long) result.matchedCount() * mirrorTotal ? new Outcome(mirrorResult, mirrored) : new Outcome(result, orientation);
    }

    /** 一次检测的主快照及镜像快照，均由世界线程采集。 */
    public record Captures(CompiledSnapshot primary, CompiledSnapshot mirrored) {

        public Captures {
            Objects.requireNonNull(primary, "primary capture");
            Objects.requireNonNull(mirrored, "mirrored capture");
        }
    }

    /** 检测结果和产生该结果的控制器朝向。 */
    public record Outcome(RecognitionResult result, Orientation orientation) {}
}
