package net.ptcrys.topodemo.api.material.common;

/** RGB 颜色数据本体(0xRRGGBB);构造由 {@link RgbColorDataType#rgb} 铸造,调用方不能直接 new。 */
public final class RgbColorData {

    private final int rgb;

    private RgbColorData(int rgb) {
        this.rgb = rgb;
    }

    static RgbColorData create(int rgb) {
        return new RgbColorData(rgb);
    }

    public int rgb() {
        return rgb;
    }
}
