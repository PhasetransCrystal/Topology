package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 控制器归一化后的规范单元格，坐标尚未应用 frame 和控制器朝向。
 * expectedState 为旋转前的固定状态，rules 描述变换，maskedProperties 预先合并所有比较屏蔽项。
 */
public record Cell(
                   BlockPos localPos,
                   @Nullable BlockState expectedState,
                   Set<PropertyRule> rules,
                   CellBinding binding,
                   Set<Property<?>> maskedProperties) {

    /** 世界可原地改变而不替换方块的属性。规范状态自动屏蔽 WATERLOGGED；如需限制该值，应使用谓词。 */
    public static final Set<Property<?>> ENVIRONMENT_MUTABLE_PROPERTIES = Set.of(BlockStateProperties.WATERLOGGED);

    public Cell {
        localPos = Objects.requireNonNull(localPos, "local position").immutable();
        rules = Set.copyOf(Objects.requireNonNull(rules, "property rules"));
        binding = Objects.requireNonNull(binding, "cell binding");
        maskedProperties = Set.copyOf(Objects.requireNonNull(maskedProperties, "masked properties"));
    }

    /** 合并全部规则的屏蔽属性，并加入状态自带的运行环境屏蔽项。 */
    public static Set<Property<?>> maskedPropertiesOf(Set<PropertyRule> rules, @Nullable BlockState expectedState) {
        Objects.requireNonNull(rules, "property rules");
        Set<Property<?>> masked = new HashSet<>();
        for (PropertyRule rule : rules) {
            masked.addAll(rule.maskedProperties());
        }
        if (expectedState != null) {
            for (Property<?> property : ENVIRONMENT_MUTABLE_PROPERTIES) {
                if (expectedState.hasProperty(property)) {
                    masked.add(property);
                }
            }
        }
        return masked.isEmpty() ? Set.of() : Set.copyOf(masked);
    }
}
