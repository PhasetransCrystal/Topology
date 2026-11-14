package net.ptcrys.topo.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopoCompactNumberTest {

    @Test
    void handlesLongMinValueWithoutRecursing() {
        assertEquals("-9E", TopoCompactNumber.formatCompact(Long.MIN_VALUE));
        assertTrue(TopoCompactNumber.formatCompactBuckets(Long.MIN_VALUE).startsWith("-"));
    }

    @Test
    void formatsNegativeValues() {
        assertEquals("-1200", TopoCompactNumber.formatCompact(-1_200));
        assertEquals("-12k", TopoCompactNumber.formatCompact(-12_000));
    }
}
