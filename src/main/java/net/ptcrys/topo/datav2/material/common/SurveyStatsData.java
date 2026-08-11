package net.ptcrys.topo.datav2.material.common;

/** 管网勘测仪的材质档位负载:观测范围(以锚点为心的 Chebyshev 半径,格)。 */
public final class SurveyStatsData {

    private final int range;

    private SurveyStatsData(int range) {
        this.range = range;
    }

    static SurveyStatsData create(int range) {
        if (range <= 0) {
            throw new IllegalArgumentException("survey range must be positive: " + range);
        }
        return new SurveyStatsData(range);
    }

    public int range() {
        return range;
    }
}
