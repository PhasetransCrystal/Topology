package net.ptcrys.topo.apiv2.recipe;

import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.recipe.capability.SlottedRecipeCapability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一个配方类型的冻结期预览槽位计划：JEI 预览面板与机内配方页共用的布局事实。
 *
 * <p>
 * 由 bootstrap 在机器冻结后经 {@link OIRecipeTypes#installPreviewPlans} 一次性安装到每个
 * {@link OIRecipeType}；计划内容（规范机选择等）由 data 侧 planner 决定，本类型只承载结果。
 * {@code slotted} 保持插入序——槽位渲染顺序即 capability 注册顺序。
 */
public record RecipePreviewPlan(
                                Map<SlottedRecipeCapability<?, ?, ?>, SlotPlan> slotted,
                                int implicitCapabilities) {

    public static final RecipePreviewPlan EMPTY = new RecipePreviewPlan(Map.of(), 0);

    public RecipePreviewPlan {
        Objects.requireNonNull(slotted, "slotted slot plans");
        if (implicitCapabilities < 0) {
            throw new IllegalArgumentException("Implicit capability count must be >= 0 (was " + implicitCapabilities + ")");
        }
        slotted = Collections.unmodifiableMap(new LinkedHashMap<>(slotted));
    }

    /** 单个 SLOTTED 家族在预览中的输入/输出槽数。 */
    public record SlotPlan(int inputSlots, int outputSlots) {

        public SlotPlan {
            if (inputSlots < 0) {
                throw new IllegalArgumentException("Input slot count must be >= 0 (was " + inputSlots + ")");
            }
            if (outputSlots < 0) {
                throw new IllegalArgumentException("Output slot count must be >= 0 (was " + outputSlots + ")");
            }
        }

        public int count(RecipeRole io) {
            return switch (io) {
                case INPUT -> inputSlots;
                case OUTPUT -> outputSlots;
                case BOTH -> inputSlots + outputSlots;
                case NONE -> 0;
            };
        }
    }
}
