package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.data.DataTransaction;

/**
 * 可复用的单元检测算法。具体内容由 input 传入，由 CellBinding 绑定到蓝图；
 * 算法不保存方块、角色、候选、材料或显示信息，也不读取实时世界。
 * 实现必须可重入；本轮事实和跨位置约束通过结构共享的 metadata 读取与写入。
 */
@FunctionalInterface
public interface CellPredicate<I> {

    /** 返回统一检测结果；失败或未知分支的元数据由调用方丢弃。 */
    CellResult evaluate(I input, CellContext context, DataTransaction metadata);
}
