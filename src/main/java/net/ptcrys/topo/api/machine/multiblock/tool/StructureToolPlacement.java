package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import org.jspecify.annotations.Nullable;

import java.util.*;

/** 服务端分批放置事务。按背包现有材料选择本次目标，失败还原本批方块；成功后只扣除本批使用的物品，不要求机器一次成型。 */
public final class StructureToolPlacement {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StructureToolPlacement.class);

    private StructureToolPlacement() {}

    public record Supply(int slot, Item item, int count) {

        public Supply {
            if (slot < 0 || count < 0) throw new IllegalArgumentException("Invalid material supply");
            Objects.requireNonNull(item);
        }
    }

    /** 按槽位分摊材料；同一物品对应多个方块需求时合并计算，不足则返回 null。此方法不读取物品组件。 */
    public static @Nullable Map<Integer, Integer> allocate(List<Supply> supplies, Map<Item, Integer> required) {
        Map<Item, Integer> remaining = new LinkedHashMap<>(required);
        required.forEach((item, count) -> { if (item == null || count == null || count < 0) throw new IllegalArgumentException("Invalid material requirement"); });
        Map<Integer, Integer> result = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        for (Supply supply : supplies) {
            if (!seen.add(supply.slot())) throw new IllegalArgumentException("Duplicate material slot");
            int needed = remaining.getOrDefault(supply.item(), 0);
            int taken = Math.min(needed, supply.count());
            if (taken > 0) {
                result.put(supply.slot(), taken);
                remaining.put(supply.item(), needed - taken);
            }
        }
        return remaining.values().stream().anyMatch(count -> count > 0) ? null : Map.copyOf(result);
    }

    /** 本批实际放置数量与失败原因；failure 为 null 时表示本批已提交，结构仍可能未完成。 */
    public record Result(int placed, @Nullable Component failure) {}

    /** 选出的施工目标及对应槽位扣除量，均为不可变集合。 */
    public record Batch(List<StructureToolPlan.Target> targets, Map<Integer, Integer> allocation) {

        public Batch {
            targets = List.copyOf(targets);
            allocation = Map.copyOf(allocation);
        }
    }

    /** 从低到高选择现有材料能支持的缺失位置；不因某种材料不足而阻止使用其他材料，创造模式选择全部缺失位置。 */
    public static Batch selectBatch(List<StructureToolPlan.Target> targets, List<Supply> supplies, boolean creative) {
        // 先验证槽位唯一性，避免重复槽位虚增可用数量。
        allocate(supplies, Map.of());
        Map<Item, Integer> available = new IdentityHashMap<>();
        for (Supply supply : supplies) available.merge(supply.item(), supply.count(), Math::addExact);
        List<StructureToolPlan.Target> selected = new ArrayList<>();
        Map<Item, Integer> required = new LinkedHashMap<>();
        for (var target : targets.stream().filter(target -> target.status() == StructureToolPlan.Status.MISSING)
                .sorted(Comparator.comparingInt(target -> target.pos().getY())).toList()) {
            Item item = target.state().getBlock().asItem();
            int count = available.getOrDefault(item, 0);
            if (!creative && (item == Items.AIR || count <= 0)) continue;
            selected.add(target);
            if (!creative) {
                available.put(item, count - 1);
                required.merge(item, 1, Math::addExact);
            }
        }
        return new Batch(selected, creative ? Map.of() : Objects.requireNonNull(allocate(supplies, required)));
    }

    /** 只允许消耗默认组件的方块物品，避免吞掉已保存机器、命名物品或容器内容。 */
    public static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem && ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }

    /** 按可用材料补建一批，返回真实放置数量；任何失败只回滚本批，不撤销此前已经完成的施工。 */
    public static Result place(ServerPlayer player, ItemStack tool, Direction face, StructureToolPlan plan, Blueprint blueprint, StructureToolOptions.Settings settings, StructureView view) {
        var level = player.level();
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots) return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
        if (!player.mayBuild() || player.isSpectator()) return new Result(0, TopoApiLang.TOOL_STRUCTURE_DENIED.getComponent());
        if (!plan.buildable()) return new Result(0, TopoApiLang.TOOL_STRUCTURE_OBSTRUCTED.getComponent(plan.problems().size()));
        List<Supply> supplies = new ArrayList<>();
        Map<Integer, ItemStack> originals = new LinkedHashMap<>();
        if (!player.hasInfiniteMaterials()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (usable(stack)) {
                    supplies.add(new Supply(slot, stack.getItem(), stack.getCount()));
                    originals.put(slot, stack.copy());
                }
            }
        }
        Batch batch = selectBatch(plan.targets(), supplies, player.hasInfiniteMaterials());
        List<StructureToolPlan.Target> placements = batch.targets();
        Map<Integer, Integer> allocation = batch.allocation();
        if (placements.isEmpty()) {
            return plan.targets().stream().anyMatch(target -> target.status() == StructureToolPlan.Status.MISSING) ? new Result(0, TopoApiLang.TOOL_STRUCTURE_MATERIALS.getComponent()) : new Result(0, null);
        }
        for (var target : placements) {
            BlockPos pos = target.pos();
            if (!player.mayInteract(level, pos) || !player.mayUseItemAt(pos, face, tool) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).isAir() || !level.isUnobstructed(target.state(), pos, CollisionContext.placementContext(player)))
                return new Result(0, TopoApiLang.TOOL_STRUCTURE_DENIED_AT.getComponent(pos.toShortString()));
        }
        List<BlockSnapshot> snapshots = new ArrayList<>();
        boolean committed = false;
        boolean deducting = false;
        try {
            for (var target : placements) {
                // 回调可能改变后续位置，写入前再次确认空气与权限，不依赖最初预检。
                if (!player.mayInteract(level, target.pos()) || !level.hasChunkAt(target.pos()) || !level.getBlockState(target.pos()).isAir()) return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
                snapshots.add(BlockSnapshot.create(level.dimension(), level, target.pos()));
                if (!level.setBlock(target.pos(), target.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
            }
            if (!snapshots.isEmpty() && EventHooks.onMultiBlockPlace(player, snapshots, face)) return new Result(0, TopoApiLang.TOOL_STRUCTURE_DENIED.getComponent());
            for (var target : placements) {
                if (!level.getBlockState(target.pos()).canSurvive(level, target.pos())) return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
            }
            var compiled = blueprint.compiled(settings.orientation());
            var layout = blueprint.layout(settings.repeats());
            var metadata = plan.metadata().verify();
            for (var target : placements) {
                int index = compiled.cellIndexOfWorld(target.pos(), view.controllerPos(), layout);
                if (index < 0 || !StructureEngine.evaluateCompiled(view, target.pos(), compiled.definitionAt(index), metadata).passed()) return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
            }
            // 本批可以只完成部分结构，但必须仍能按同一个目标布局继续补齐。
            if (!StructureToolPlan.create(view, blueprint, settings, block -> Integer.MAX_VALUE, plan.metadata()).buildable()) return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
            for (int slot : allocation.keySet()) {
                if (!ItemStack.matches(originals.get(slot), player.getInventory().getItem(slot))) return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
            }
            deducting = true;
            for (var entry : allocation.entrySet()) player.getInventory().getItem(entry.getKey()).shrink(entry.getValue());
            player.getInventory().setChanged();
            committed = true;
        } catch (RuntimeException exception) {
            LOGGER.error("Structure tool placement failed at {}", view.controllerPos(), exception);
            return new Result(0, TopoApiLang.TOOL_STRUCTURE_CHANGED.getComponent());
        } finally {
            if (!committed && deducting) {
                for (int slot : allocation.keySet()) player.getInventory().setItem(slot, originals.get(slot).copy());
            }
            if (!committed) for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore();
        }
        for (var target : placements) level.updateNeighborsAt(target.pos(), target.state().getBlock());
        return new Result(placements.size(), null);
    }
}
