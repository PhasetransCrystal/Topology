package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachinePerformanceSnapshot;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.List;

/** Server-side Jade data provider for machine timing snapshots and scalar resource bars. */
public final class MachineDataProvider implements IServerDataProvider<BlockAccessor> {

    public static final MachineDataProvider INSTANCE = new MachineDataProvider();

    static final String PERFORMANCE_NANOS_KEY = "perfNanos";
    static final String PERFORMANCE_TREE_KEY = "perfTree";
    static final String NODE_ID_KEY = "id";
    static final String NODE_LABEL_KEY = "label";
    static final String NODE_DETAIL_KEY = "detail";
    static final String NODE_NANOS_KEY = "nanos";
    static final String NODE_AVG_NANOS_KEY = "avgNanos";
    static final String NODE_PEAK_NANOS_KEY = "peakNanos";
    static final String NODE_CHILDREN_KEY = "children";

    static final String SCALAR_BARS_KEY = "scalarBars";
    static final String SCALAR_NAME_KEY = "name";
    static final String SCALAR_COLOR_KEY = "color";
    static final String SCALAR_AMOUNT_KEY = "amount";
    static final String SCALAR_CAPACITY_KEY = "capacity";

    /** 固定打包顺序:能量、高级能量、热量;同类多端口按机器端口顺序。 */
    private static final List<BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability>> SCALAR_INTEGRATIONS = List.of(
            BuiltinOIResourceIntegrations.ENERGY,
            BuiltinOIResourceIntegrations.ADVANCED_ENERGY,
            BuiltinOIResourceIntegrations.HEAT);

    private static final Identifier UID = IdHelper.oi("machine");
    private static final int MONITOR_TICKS = 40;

    private MachineDataProvider() {}

    @Override
    public @NonNull Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(@NonNull CompoundTag data, @NonNull BlockAccessor accessor) {
        packScalarBars(accessor.getBlockEntity(), data);
        packSnapshot(accessor.getBlockEntity(), data);
    }

    /** 标量端口的 Jade 形态:每个端口一条横向资源条(名称 key、资源色、存量、容量)。 */
    static void packScalarBars(@Nullable BlockEntity be, @NonNull CompoundTag data) {
        if (!(be instanceof MachineBlockEntity machine)) {
            return;
        }
        ListTag bars = new ListTag();
        for (BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration : SCALAR_INTEGRATIONS) {
            for (ResourcePort<?, ScalarResource> port : machine.machineComponents().resources().rawSide().ports(integration.resourceType())) {
                if (!(port instanceof ScalarResourcePort scalar)) {
                    continue;
                }
                CompoundTag bar = new CompoundTag();
                bar.putString(SCALAR_NAME_KEY, scalar.resource().translationKey());
                bar.putInt(SCALAR_COLOR_KEY, scalar.resource().color());
                bar.putLong(SCALAR_AMOUNT_KEY, scalar.storedAmount());
                bar.putLong(SCALAR_CAPACITY_KEY, scalar.capacityAmount());
                bars.add(bar);
            }
        }
        if (!bars.isEmpty()) {
            data.put(SCALAR_BARS_KEY, bars);
        }
    }

    public static void packSnapshot(@Nullable BlockEntity be, @NonNull CompoundTag data) {
        if (!(be instanceof MachineBlockEntity machine)) {
            return;
        }
        machine.activatePerformanceMonitoring(MONITOR_TICKS);
        // Freshness follows the machine's own cadence: ME hatches tick every 20-40 ticks, so a
        // fixed 2-tick window would drop their sample between runs and the panel would never show.
        int maxSampleAgeTicks = machine.performanceSampleMaxAgeTicks();
        machine.publishRecentPerformanceSnapshot(maxSampleAgeTicks);

        MachinePerformanceSnapshot snapshot = machine.recentPerformanceSnapshot(maxSampleAgeTicks);
        if (snapshot.isEmpty()) {
            return;
        }
        data.putLong(PERFORMANCE_NANOS_KEY, snapshot.totalNanos());
        data.put(PERFORMANCE_TREE_KEY, encodePerformanceTree(machine.definition().id().toString(), snapshot));
    }

    static CompoundTag encodePerformanceTree(String machineId, MachinePerformanceSnapshot snapshot) {
        CompoundTag root = node(
                "machine",
                machineId,
                "MachineBlockEntity",
                snapshot.totalNanos(),
                snapshot.totalAvgNanos(),
                snapshot.totalPeakNanos());
        ListTag rootChildren = new ListTag();
        rootChildren.add(node(
                "machine.self",
                "self",
                "framework + dispatch",
                snapshot.selfNanos(),
                snapshot.selfAvgNanos(),
                snapshot.selfPeakNanos()));

        CompoundTag traits = node(
                "traits",
                "traits",
                "mounted trait total",
                snapshot.componentsNanos(),
                snapshot.componentsAvgNanos(),
                snapshot.componentsPeakNanos());
        ListTag traitChildren = new ListTag();
        for (MachinePerformanceSnapshot.ComponentSample sample : snapshot.components()) {
            CompoundTag trait = node(
                    sample.id(), sample.id(), sample.className(), sample.nanos(), sample.avgNanos(), sample.peakNanos());
            ListTag childNodes = new ListTag();
            for (MachinePerformanceSnapshot.TimingSample child : sample.children()) {
                childNodes.add(node(
                        child.id(), child.label(), child.detail(), child.nanos(), child.avgNanos(), child.peakNanos()));
            }
            if (!childNodes.isEmpty()) {
                trait.put(NODE_CHILDREN_KEY, childNodes);
            }
            traitChildren.add(trait);
        }
        traits.put(NODE_CHILDREN_KEY, traitChildren);
        rootChildren.add(traits);
        root.put(NODE_CHILDREN_KEY, rootChildren);
        return root;
    }

    private static CompoundTag node(String id, String label, String detail, long nanos, long avgNanos, long peakNanos) {
        CompoundTag tag = new CompoundTag();
        tag.putString(NODE_ID_KEY, id);
        tag.putString(NODE_LABEL_KEY, label);
        tag.putString(NODE_DETAIL_KEY, detail);
        tag.putLong(NODE_NANOS_KEY, Math.max(0L, nanos));
        if (avgNanos > 0L) {
            tag.putLong(NODE_AVG_NANOS_KEY, avgNanos);
        }
        if (peakNanos > 0L) {
            tag.putLong(NODE_PEAK_NANOS_KEY, peakNanos);
        }
        return tag;
    }
}
