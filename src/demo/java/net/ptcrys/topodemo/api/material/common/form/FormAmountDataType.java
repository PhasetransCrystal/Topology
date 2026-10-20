package net.ptcrys.topodemo.api.material.common.form;

import net.ptcrys.topo.api.material.form.FormDataType;
import net.ptcrys.topo.api.material.form.FormDataUse;

import net.minecraft.resources.Identifier;

/**
 * AMOUNT 载荷:一个形态物品所代表的材料数量,以每锭 {@value #UNITS_PER_INGOT} 单位计。
 * 72 能被所有内建分数(1/2、1/4、1/8、1/9)整除,因此所有形态数量都是精确整数,配方处理器
 * 可在不取整的情况下校验质量守恒。铸造辅助方法在声明行即做校验。
 */
public final class FormAmountDataType extends FormDataType<Integer> {

    public static final int UNITS_PER_INGOT = 72;

    public FormAmountDataType(Identifier id) {
        super(id);
    }

    /** Whole ingots, e.g. {@code ingots(4)} for a gear. */
    public FormDataUse<Integer> ingots(int count) {
        return units(Math.multiplyExact(count, UNITS_PER_INGOT));
    }

    /** An exact ingot fraction, e.g. {@code ingotFraction(9)} for a nugget or tiny dust. */
    public FormDataUse<Integer> ingotFraction(int denominator) {
        if (denominator <= 0 || UNITS_PER_INGOT % denominator != 0) {
            throw new IllegalArgumentException(
                    "ingot fraction denominator must divide " + UNITS_PER_INGOT + ": " + denominator);
        }
        return use(UNITS_PER_INGOT / denominator);
    }

    /** Raw units, for amounts that are not a whole-ingot multiple or fraction. */
    public FormDataUse<Integer> units(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("form amount must be positive: " + units);
        }
        return use(units);
    }
}
