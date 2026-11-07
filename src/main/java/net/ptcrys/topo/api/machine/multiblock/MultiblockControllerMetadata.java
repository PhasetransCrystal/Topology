package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.machine.component.Attachment;
import net.ptcrys.topo.api.machine.component.AttachmentType;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;

import java.util.List;
import java.util.Objects;

/** 控制器的声明元数据，用于结构预览、诊断和放置工具。只能由 {@link MultiblockController#mount} 创建，确保蓝图与实际挂载的控制器行为一致。 */
public final class MultiblockControllerMetadata implements Attachment {

    public static final AttachmentType<MultiblockControllerMetadata> TYPE = OfficialTopoAPIPlugin.INSTANCE
            .machine()
            .attachmentType("multiblock_controller", MultiblockControllerMetadata.class);

    private final Blueprint blueprint;
    private final List<MultiblockModule> modules;

    MultiblockControllerMetadata(Blueprint blueprint) {
        this(blueprint, List.of());
    }

    MultiblockControllerMetadata(Blueprint blueprint, List<MultiblockModule> modules) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.modules = List.copyOf(Objects.requireNonNull(modules, "multiblock modules"));
    }

    /** 触发类初始化并注册元数据类型。 */
    public static void init() {}

    public Blueprint blueprint() {
        return blueprint;
    }

    /** 按工具模块序号取得蓝图：0 为本体，1 起为模块声明顺序，不接受越界值。 */
    public Blueprint blueprint(int moduleIndex) {
        if (moduleIndex < 0 || moduleIndex > modules.size()) throw new IllegalArgumentException("Module index outside controller declaration");
        return moduleIndex == 0 ? blueprint : modules.get(moduleIndex - 1).blueprint();
    }

    /** 此控制器的叠加模块，保持声明顺序，工具序号从 1 自动递增。 */
    public List<MultiblockModule> modules() {
        return modules;
    }

    @Override
    public AttachmentType<MultiblockControllerMetadata> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof MultiblockControllerMetadata other && blueprint.equals(other.blueprint) && modules.equals(other.modules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blueprint, modules);
    }

    @Override
    public String toString() {
        return "MultiblockControllerMetadata[blueprint=" + blueprint + ", modules=" + modules + "]";
    }
}
