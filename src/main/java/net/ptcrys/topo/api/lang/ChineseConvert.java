package net.ptcrys.topo.api.lang;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

/**
 * Simplified-to-Traditional Chinese conversion. <b>Datagen-only</b>: the opencc4j dependency is not
 * jarJar'd, so this class must never be loaded at runtime. {@code zh_tw.json} is derived from
 * {@code zh_cn} at datagen time via {@link #s2t}.
 */
public final class ChineseConvert {

    private ChineseConvert() {}

    /** OpenCC character/phrase-level Simplified to Traditional (generic Traditional). */
    public static String s2t(String simplified) {
        return ZhConverterUtil.toTraditional(simplified);
    }
}
