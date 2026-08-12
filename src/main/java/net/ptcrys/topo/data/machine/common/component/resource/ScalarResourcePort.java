package net.ptcrys.topo.data.machine.common.component.resource;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleMount;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.machine.resource.ResourcePort;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.machine.ui.MachineUiContribution;
import net.ptcrys.topo.api.machine.ui.PortUiHighlight;
import net.ptcrys.topo.api.machine.ui.ResourceBar;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Machine-mounted scalar buffer (energy / advanced energy / heat). One trait stores one scalar
 * kind in a single index; everything else (persistence, transactions, capability/recipe/UI port
 * exposure) is inherited from {@link ResourcePort} unchanged.
 */
public final class ScalarResourcePort extends ResourcePort<ResourceStack<ScalarResource>, ScalarResource> {

    /**
     * 标量内容周期性持久化(与 recipe progress 的 20t 粒度一致):标量每 tick 都在变,逐 tick 标记
     * 区块 unsaved 毫无增量价值;崩溃最多回退 1 秒的供能,远低于区块自动保存的粒度。
     */
    /** @deprecated Persistence cadence is now coalesced per machine snapshot epoch. */
    @Deprecated(forRemoval = false)
    public static final int CONTENTS_PERSIST_INTERVAL_TICKS = 20;

    public static final ComponentKey<ScalarResourcePort> ENERGY_INPUT_1 = ComponentKey.id("energy_input_1", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> ENERGY_OUTPUT_1 = ComponentKey.id("energy_output_1", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> ENERGY_STORAGE = ComponentKey.id("energy_storage", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> ADVANCED_ENERGY_INPUT_1 = ComponentKey.id("advanced_energy_input_1", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> ADVANCED_ENERGY_OUTPUT_1 = ComponentKey.id("advanced_energy_output_1", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> ADVANCED_ENERGY_STORAGE = ComponentKey.id("advanced_energy_storage", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> HEAT_INPUT_1 = ComponentKey.id("heat_input_1", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> HEAT_OUTPUT_1 = ComponentKey.id("heat_output_1", ScalarResourcePort.class);
    public static final ComponentKey<ScalarResourcePort> HEAT_STORAGE = ComponentKey.id("heat_storage", ScalarResourcePort.class);

    private ScalarResourcePort(
                               ComponentContext<ScalarResourcePort> context,
                               ScalarResourcePortMetadata metadata) {
        this(
                context,
                new ObservableScalarResourceHandler(
                        metadata.resource(),
                        metadata.capacity(),
                        metadata::accepts),
                metadata);
    }

    private ScalarResourcePort(
                               ComponentContext<ScalarResourcePort> context,
                               ObservableScalarResourceHandler handler,
                               ScalarResourcePortMetadata metadata) {
        super(context, metadata.integration().resourceType(), handler, metadata);
        handler.setOnChanged(this::onStorageChanged);
    }

    /** The scalar kind stored by this port. */
    public ScalarResource resource() {
        return ((ScalarResourcePortMetadata) metadata()).resource();
    }

    /** Current stored amount of the port's single buffer index. */
    public long storedAmount() {
        return handler().getAmountAsLong(0);
    }

    /** Declared buffer capacity. */
    public long capacityAmount() {
        return handler().getCapacityAsLong(0, resource());
    }

    /**
     * 被破坏时分发到标量资源注册期绑定的行为(电容放电/破壳泄热/NONE),携带实时存量与容量;行为返回
     * 的报告(严重度+文案)经注入机器名(旧 state)与资源名后,广播给半径内玩家并按严重度施加冲击波。
     */
    @Override
    public void onMachineDestroyed(ServerLevel level, BlockPos pos, BlockState state) {
        ScalarResource resource = resource();
        MachineDestroyedReport report = resource.destroyedBehavior().onDestroyed(level, pos, storedAmount(), capacityAmount());
        // 资源名用资源自身的展示色 + 粗体,与游戏内 UI 资源条/图标同色呼应(剥去 alpha 取 RGB)。
        Component resourceName = resource.displayName().copy().withColor(resource.color() & 0xFFFFFF).withStyle(ChatFormatting.BOLD);
        MachineDestroyedFeedback.deliver(
                level, pos, report.severity(), report.message(state.getBlock().getName(), resourceName));
    }

    /**
     * 标量端口的 AutoUI 形态:玩家物品栏上方的横向资源条(每个端口一条,按挂载顺序排列),
     * 经服务端同步显示 存量/容量。所有页面可见。super 保留基类的 side-IO 配置卡片贡献。
     */
    @Override
    public void collectMachineUi(MachineUiContribution contribution) {
        super.collectMachineUi(contribution);
        ScalarResource resource = resource();
        ResourceBar bar = MachineUiComponentTemplate.INSTANCE
                .createResourceBar(resource.displayName(), resource.color(), ResourceBar.Orientation.HORIZONTAL)
                .bindStorage(this::storedAmount, this::capacityAmount);
        // 标量端口无槽位,side-IO 卡标题悬浮时高亮本端口的资源条。
        PortUiHighlight.tag(bar, id());
        contribution.bottomStrip(
                "topo_resource_bar_" + id().getPath(),
                resource.displayName(),
                null,
                bar);
    }

    /**
     * Port with a caller-supplied {@link PortAccess} — side-restricted or otherwise
     * customized ports compose the policy here (e.g. {@code PortAccess.output(DOWN)});
     * chain {@link PartRoleMount#role} to additionally expose it as a multiblock part port.
     */
    public static PartRoleMount<ScalarResourcePort> mount(
                                                          ComponentKey<ScalarResourcePort> key,
                                                          BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                          int capacity,
                                                          PortAccess policy) {
        return mount(key, integration, capacity, policy, null);
    }

    public static PartRoleMount<ScalarResourcePort> mount(
                                                          ComponentKey<ScalarResourcePort> key,
                                                          BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                          int capacity,
                                                          PortAccess policy,
                                                          @Nullable Predicate<Resource> resourceFilter) {
        ScalarResourcePortMetadata metadata = new ScalarResourcePortMetadata(integration, capacity, policy, resourceFilter);
        return new PartRoleMount<>(
                key.mount(context -> new ScalarResourcePort(context, metadata), metadata));
    }

    public static PartRoleMount<ScalarResourcePort> input(
                                                          ComponentKey<ScalarResourcePort> key,
                                                          BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                          int capacity) {
        return mount(key, integration, capacity, PortAccess.input());
    }

    public static PartRoleMount<ScalarResourcePort> output(
                                                           ComponentKey<ScalarResourcePort> key,
                                                           BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                           int capacity) {
        return mount(key, integration, capacity, PortAccess.output());
    }

    public static PartRoleMount<ScalarResourcePort> storage(
                                                            ComponentKey<ScalarResourcePort> key,
                                                            BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                                            int capacity) {
        return mount(key, integration, capacity, PortAccess.storage());
    }
}
