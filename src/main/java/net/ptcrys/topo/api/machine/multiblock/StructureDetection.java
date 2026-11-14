package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import java.util.List;
import java.util.Objects;

/** 整体结构检测入口。朝向、镜像、本体及所有模块使用同一周期元数据，放置规划另行调用。 */
public final class StructureDetection {

    private StructureDetection() {}

    /** 调用方决定读取实时世界还是预先采集的快照；异步使用时工厂只能返回不可变快照。 */
    @FunctionalInterface
    public interface Views {

        StructureView get(Blueprint blueprint, Orientation orientation);
    }

    /** 两个整体变换互相隔离；主体对称时优先选择接入模块更多的整体，而不是分别翻转模块。 */
    public static Outcome detect(Views views, Blueprint body, List<MultiblockModule> modules, Orientation orientation,
                                 DataSnapshot source, boolean verifying) {
        Outcome primary = evaluate(views, body, modules, orientation, source, verifying);
        if (modules.isEmpty() && primary.result().formed()) return primary;
        Outcome mirrored = evaluate(views, body, modules, orientation.mirrored(), source, verifying);
        if (primary.result().formed() != mirrored.result().formed()) return primary.result().formed() ? primary : mirrored;
        if (primary.result().formed()) {
            long normalModules = primary.modules().reports().values().stream().filter(RecognitionResult.ModuleReport::satisfied).count();
            long mirrorModules = mirrored.modules().reports().values().stream().filter(RecognitionResult.ModuleReport::satisfied).count();
            return mirrorModules > normalModules ? mirrored : primary;
        }
        if (unknown(primary) != unknown(mirrored)) return unknown(primary) ? primary : mirrored;
        long normalTotal = body.cellCount(primary.result().repeats()), mirrorTotal = body.cellCount(mirrored.result().repeats());
        return (long) mirrored.result().matchedCount() * normalTotal > (long) primary.result().matchedCount() * mirrorTotal ? mirrored : primary;
    }

    private static Outcome evaluate(Views views, Blueprint body, List<MultiblockModule> modules, Orientation orientation,
                                    DataSnapshot source, boolean verifying) {
        DataTransaction metadata = verifying ? source.verify() : source.edit();
        RecognitionResult result = StructureEngine.recognize(views.get(body, orientation), body, orientation, metadata, "body");
        MultiblockModuleEvaluator.Evaluation evaluation = MultiblockModuleEvaluator.evaluateOverlays(
                blueprint -> views.get(blueprint, orientation), modules, orientation, metadata, members -> true);
        return new Outcome(result, orientation, evaluation);
    }

    public static boolean unknown(Outcome outcome) {
        return outcome.result().unknown() || outcome.modules().unloaded();
    }

    public record Outcome(RecognitionResult result, Orientation orientation, MultiblockModuleEvaluator.Evaluation modules) {

        public Outcome {
            Objects.requireNonNull(result);
            Objects.requireNonNull(orientation);
            Objects.requireNonNull(modules);
        }

        public DataSnapshot metadata() {
            return modules.metadata();
        }
    }
}
