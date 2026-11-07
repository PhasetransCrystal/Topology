package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.multiblock.*;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** 服务端工具会话。客户端只能刷新先前右键授权的锚点，权限检查先于区块与控制器读取；放置方案始终由服务端生成。 */
public final class StructureToolManager {

    private record Session(Identifier dimension, BlockPos anchor, InteractionHand hand, long tick) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private StructureToolManager() {}

    public static void forget(UUID id) {
        SESSIONS.remove(id);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /** 返回是否处理了控制器交互；非控制器返回 false，由物品打开配置页。 */
    public static boolean use(ServerPlayer player, InteractionHand hand, BlockPos anchor, boolean build, Direction face) {
        return inspect(player, hand, anchor, build, face, true);
    }

    private static boolean inspect(ServerPlayer player, InteractionHand hand, BlockPos anchor, boolean build, Direction face, boolean announce) {
        var level = player.level();
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof StructureToolItem)) return false;
        if (player.isSpectator() || !player.mayInteract(level, anchor) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(anchor)) > 128 * 128 || !level.hasChunkAt(anchor)) {
            reject(player, anchor, TopoApiLang.TOOL_STRUCTURE_DENIED.getComponent());
            return true;
        }
        if (!(level.getBlockEntity(anchor) instanceof MachineBlockEntity machine)) return false;
        var metadata = machine.definition().metadata(MultiblockControllerMetadata.TYPE);
        if (metadata.isEmpty()) return false;
        int module = StructureToolOptions.module(stack);
        if (module > metadata.getFirst().modules().size()) {
            reject(player, anchor, TopoApiLang.TOOL_STRUCTURE_MODULE_RANGE.getComponent(module, metadata.getFirst().modules().size()));
            return true;
        }
        Blueprint blueprint = metadata.getFirst().blueprint(module);
        if (blueprint.maxCellCount() > StructureToolPlan.MAX_CELLS || metadata.getFirst().blueprint().maxCellCount() > StructureToolPlan.MAX_CELLS) {
            reject(player, anchor, TopoApiLang.TOOL_STRUCTURE_LIMIT.getComponent(StructureToolPlan.MAX_CELLS));
            return true;
        }
        Orientation primary = machine.getBlockState().hasProperty(OrientedMachineBlock.FACING) ? Orientations.forFacing(machine.getBlockState().getValue(OrientedMachineBlock.FACING)) : Orientations.IDENTITY;
        try {
            var settings = StructureToolOptions.configure(new StructureToolOptions.Context(player, machine, blueprint, module), stack, primary);
            StructureView view = new AuthorizedView(player, new MultiblockLiveView(level, anchor));
            // 工具严格使用配置的镜像和重复次数；本体运行时的自动识别不能覆盖施工目标。
            Blueprint body = metadata.getFirst().blueprint();
            List<Blueprint> definitions = new ArrayList<>(List.of(body));
            metadata.getFirst().modules().forEach(spec -> definitions.add(spec.blueprint()));
            var bodyOutcome = StructureDetection.detect((definition, transform) -> view, body, metadata.getFirst().modules(), primary,
                    net.ptcrys.topo.api.machine.multiblock.pattern.StructureMetadata.defaults(definitions), false);
            Map<Item, Integer> inventory = new IdentityHashMap<>();
            if (!player.hasInfiniteMaterials()) for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack supply = player.getInventory().getItem(slot);
                if (StructureToolPlacement.usable(supply)) inventory.merge(supply.getItem(), supply.getCount(), Integer::sum);
            }
            var plan = StructureToolPlan.create(view, blueprint, settings, block -> player.hasInfiniteMaterials() ? Integer.MAX_VALUE : inventory.getOrDefault(block.asItem(), 0), bodyOutcome.metadata());
            if (build) {
                boolean conflict = !MultiblockLevelBinder.services(level).claims().conflictingExclusive(anchor, plannedExclusive(view, plan)).isEmpty();
                var placement = conflict ? new StructureToolPlacement.Result(0, TopoApiLang.TOOL_STRUCTURE_EXCLUSIVE.getComponent()) : StructureToolPlacement.place(player, stack, face, plan, blueprint, settings, view);
                long remaining = plan.targets().stream().filter(target -> target.status() == StructureToolPlan.Status.MISSING).count() - placement.placed();
                player.sendSystemMessage(placement.failure() == null ? TopoApiLang.TOOL_STRUCTURE_BUILT.getComponent(placement.placed(), remaining) : placement.failure());
                plan = StructureToolPlan.create(view, blueprint, settings, block -> Integer.MAX_VALUE, plan.metadata());
                bodyOutcome = StructureDetection.detect((definition, transform) -> view, body, metadata.getFirst().modules(), primary,
                        net.ptcrys.topo.api.machine.multiblock.pattern.StructureMetadata.defaults(definitions), false);
            }
            var targetResult = StructureEngine.recognize(view, blueprint, settings.orientation(), settings.repeats(), plan.metadata().verify(), "body");
            var result = module == 0 ? bodyOutcome.result() : StructureEngine.recognize(view, blueprint, bodyOutcome.orientation(), bodyOutcome.metadata().verify(), "module/" + metadata.getFirst().modules().get(module - 1).id());
            List<Component> details = new ArrayList<>();
            add(details, TopoApiLang.TOOL_STRUCTURE_TARGET.getComponent(targetResult.matchedCount(), blueprint.cellCount(settings.repeats()), Arrays.toString(settings.repeats().countsCopy()),
                    (settings.orientation().mirror() ? TopoApiLang.TOOL_STRUCTURE_ENABLED : TopoApiLang.TOOL_STRUCTURE_DISABLED).getComponent()));
            for (Component detail : details(blueprint, result.formed() ? targetResult : result, result.formed() ? settings.orientation() : bodyOutcome.orientation())) add(details, detail);
            Set<BlockPos> exclusive = new LinkedHashSet<>();
            for (BlockPos member : result.members()) {
                var info = view.member(member);
                if (info != null && info.exclusive()) exclusive.add(member);
            }
            boolean formed = result.formed();
            if (!MultiblockLevelBinder.services(level).claims().conflictingExclusive(anchor, exclusive).isEmpty()) {
                formed = false;
                add(details, TopoApiLang.TOOL_STRUCTURE_EXCLUSIVE.getComponent());
            }
            if (module > 0) {
                if (!bodyOutcome.result().formed()) {
                    formed = false;
                    add(details, TopoApiLang.TOOL_STRUCTURE_BODY.getComponent());
                }
                var report = bodyOutcome.modules().reports().get(metadata.getFirst().modules().get(module - 1).id());
                if (!report.blockedBy().isEmpty()) {
                    formed = false;
                    add(details, TopoApiLang.TOOL_STRUCTURE_DEPENDENCIES.getComponent(String.join(", ", report.blockedBy())));
                }
            }
            Component summary = (formed ? TopoApiLang.TOOL_STRUCTURE_FORMED : TopoApiLang.TOOL_STRUCTURE_FAILED).getComponent(module, result.matchedCount(), blueprint.cellCount(result.repeats()));
            var snapshot = new StructureToolSnapshot(level.dimension().identifier(), anchor, summary, details, plan.targets());
            PacketDistributor.sendToPlayer(player, new StructureToolNetworking.Snapshot(snapshot));
            if (announce) {
                player.sendSystemMessage(summary);
                for (Component detail : details) player.sendSystemMessage(detail);
            }
            SESSIONS.put(player.getUUID(), new Session(level.dimension().identifier(), anchor.immutable(), hand, level.getGameTime()));
            if (build) MultiblockLevelBinder.services(level).rechecks().markDirty(anchor);
        } catch (IllegalArgumentException exception) {
            reject(player, anchor, TopoApiLang.TOOL_STRUCTURE_CONFIG_ERROR.getComponent(exception.getMessage()));
        }
        return true;
    }

    private static Set<BlockPos> plannedExclusive(StructureView view, StructureToolPlan plan) {
        Set<BlockPos> result = new LinkedHashSet<>();
        for (var target : plan.targets()) {
            if (target.status() == StructureToolPlan.Status.MATCHED) {
                var member = view.member(target.pos());
                if (member != null && member.exclusive()) result.add(target.pos());
            } else if (target.status() == StructureToolPlan.Status.MISSING && target.state().getBlock() instanceof net.ptcrys.topo.api.machine.MachineBlock machine &&
                    !machine.definition().metadata(net.ptcrys.topo.api.machine.multiblock.ability.ExclusivePartAttachment.TYPE).isEmpty()) {
                        result.add(target.pos());
                    }
        }
        return result;
    }

    private static void reject(ServerPlayer player, BlockPos anchor, Component reason) {
        player.sendSystemMessage(reason);
        SESSIONS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new StructureToolNetworking.Snapshot(new StructureToolSnapshot(player.level().dimension().identifier(), anchor, reason, List.of(), List.of())));
    }

    private static List<MultiblockModule> dependencyClosure(List<MultiblockModule> modules, int index) {
        Map<String, MultiblockModule> byId = new LinkedHashMap<>();
        for (var module : modules) byId.put(module.id(), module);
        Set<String> needed = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(modules.get(index - 1).id());
        long cells = 0;
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!needed.add(id)) continue;
            var module = byId.get(id);
            cells += module.blueprint().maxCellCount();
            if (cells > StructureToolPlan.MAX_CELLS) throw new IllegalArgumentException("Module dependencies exceed tool cell limit");
            pending.addAll(module.dependencies());
        }
        return modules.stream().filter(module -> needed.contains(module.id())).toList();
    }

    /** 逐位置先检查访问权限，再委托世界读取，禁止通过跨区域蓝图读取受保护位置。 */
    private record AuthorizedView(ServerPlayer player, StructureView delegate) implements StructureView {

        public BlockPos controllerPos() {
            return delegate.controllerPos();
        }

        public boolean isLoaded(BlockPos pos) {
            return player.mayInteract(player.level(), pos) && player.level().getWorldBorder().isWithinBounds(pos) && delegate.isLoaded(pos);
        }

        public net.minecraft.world.level.block.state.@org.jspecify.annotations.Nullable BlockState blockState(BlockPos pos) {
            return isLoaded(pos) ? delegate.blockState(pos) : null;
        }

        public Set<net.ptcrys.topo.api.machine.multiblock.ability.PartRole> roles(BlockPos pos) {
            return isLoaded(pos) ? delegate.roles(pos) : Set.of();
        }

        public @org.jspecify.annotations.Nullable Member member(BlockPos pos) {
            return isLoaded(pos) ? delegate.member(pos) : null;
        }
    }

    private static void add(List<Component> list, Component value) {
        if (list.size() < StructureToolSnapshot.MAX_DETAILS) list.add(value);
    }

    private static List<Component> details(Blueprint blueprint, RecognitionResult result, Orientation orientation) {
        var compiled = blueprint.compiled(orientation);
        var layout = blueprint.layout(result.repeats());
        List<Component> details = new ArrayList<>();
        if (result.failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED) add(details, TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNLOADED_COUNT.getComponent(result.unloadedCount()));
        if (result.failureReason() == RecognitionResult.FailureReason.CELL_UNKNOWN) add(details, TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNKNOWN.getComponent());
        if (result.failureReason() == RecognitionResult.FailureReason.ORIENTATION_UNSUPPORTED) add(details, TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNSUPPORTED_DETAIL.getComponent());
        for (var issue : result.unknownCells()) add(details, MultiblockClientDiagnosis.describe(issue.result()));
        int shownMissing = 0;
        for (var missing : result.missingCells()) {
            add(details, MultiblockClientDiagnosis.describe(missing.result()));
            var local = missing.localPos();
            var state = compiled.definitionAt(layout.cellIndexAt(local.getX(), local.getY(), local.getZ())).orientedExpected();
            Component expected;
            if (state != null) expected = stateName(state);
            else {
                var names = Component.empty();
                var candidates = missing.expected().binding().candidateBlocks();
                for (int i = 0; i < Math.min(4, candidates.size()); i++) {
                    if (i > 0) names.append(" / ");
                    names.append(candidates.get(i).getName());
                }
                expected = candidates.isEmpty() ? TopoApiLang.UI_MULTIBLOCK_NONE.getComponent() : names;
            }
            Component actual = missing.actual() == null ? TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_UNLOADED.getComponent() : stateName(missing.actual());
            add(details, TopoApiLang.TOOL_STRUCTURE_MISSING.getComponent(missing.worldPos().toShortString(), expected, actual));
            shownMissing++;
            if (details.size() >= 7) break;
        }
        if (result.missingCount() > shownMissing) add(details, TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_MORE.getComponent(result.missingCount() - shownMissing));
        if (result.failureReason() == RecognitionResult.FailureReason.NONE) for (var requirement : blueprint.countRequirements().values()) {
            int count = result.roleCounts().getOrDefault(requirement.role(), 0);
            if (count < requirement.min() || count > requirement.max()) add(details, TopoApiLang.TOOL_STRUCTURE_ROLE.getComponent(requirement.role().displayName(), count, requirement.min(), requirement.max()));
        }
        return details;
    }

    private static Component stateName(net.minecraft.world.level.block.state.BlockState state) {
        var name = state.getBlock().getName().copy();
        if (!state.getProperties().isEmpty()) {
            StringJoiner properties = new StringJoiner(",", "[", "]");
            for (var property : state.getProperties()) properties.add(property.getName() + "=" + state.getValue(property));
            name.append(properties.toString());
        }
        return name;
    }

    public static void refresh(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!session.dimension().equals(player.level().dimension().identifier()) || !(player.getItemInHand(session.hand()).getItem() instanceof StructureToolItem) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(session.anchor())) > 128 * 128) {
            forget(player.getUUID());
            return;
        }
        if (player.level().getGameTime() - session.tick() < 20) return;
        if (!inspect(player, session.hand(), session.anchor(), false, Direction.UP, false)) forget(player.getUUID());
    }
}
