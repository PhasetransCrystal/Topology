package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.List;

/**
 * 描述规范状态属性在有效旋转下的处理方式。规则为普通不可变值，通过对象引用使用，不按字符串匹配。
 * 受限值域可能无法表达某些旋转，须由 supports 提前声明；引擎在扫描前拒绝不支持的朝向。
 */
public interface PropertyRule {

    /** 返回受管属性按 o 变换后的状态；属性不存在或旋转无法表达时返回原状态。调用者应先检查 supports。 */
    BlockState apply(Orientation o, BlockState s);

    /** 属性处理分类；只有 COVARIANT 会被引擎应用旋转。 */
    Classification classification();

    /**
     * 该旋转是否将每个受管属性的所有合法值仍映射到其合法值域。
     * 默认支持全部朝向；受限方向属性应重写。返回 false 时检测入口拒绝朝向，避免未旋转的回退值造成误匹配。
     */
    default boolean supports(Orientation o) {
        return true;
    }

    /**
     * 返回同一几何形态的额外合法状态编码，检测接受规范状态或任一替代编码。
     * 输入是已旋转的可信预期状态；只处理本规则的受管属性，不读取实际世界或邻居派生值。默认无替代编码。
     */
    default Collection<BlockState> alternateEncodings(BlockState expected) {
        return List.of();
    }

    /**
     * 比较时忽略的属性集合，通常是由邻居决定的形状或连接状态。
     * 屏蔽能力与分类独立；引擎合并同一单元格所有规则的屏蔽项，默认空集合。
     */
    default Collection<Property<?>> maskedProperties() {
        return List.of();
    }

    /** 本规则会变换的属性，供构建器拒绝同一属性上的重复协变规则。只屏蔽、不变换的规则返回空集合。 */
    default Collection<Property<?>> governedProperties() {
        return List.of();
    }

    /** 属性分类：FIXED 保留规范值直接比较；COVARIANT 随有效旋转变换；DERIVED 由环境派生，通常通过 maskedProperties 屏蔽。 */
    enum Classification {
        FIXED,
        COVARIANT,
        DERIVED
    }
}
