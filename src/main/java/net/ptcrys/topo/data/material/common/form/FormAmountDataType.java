package net.ptcrys.topo.data.material.common.form;

import net.ptcrys.topo.api.material.form.FormDataType;
import net.ptcrys.topo.api.material.form.FormDataUse;

import net.minecraft.resources.Identifier;

/**
 * The AMOUNT payload: the material quantity one item of a form represents, in fixed units of
 * {@value #UNITS_PER_INGOT} units per ingot. 72 divides evenly by every builtin fraction
 * (1/2, 1/4, 1/8, 1/9), so all form amounts are exact integers and recipe processors can verify
 * mass balance without rounding. Minting helpers validate at the declaration line.
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
