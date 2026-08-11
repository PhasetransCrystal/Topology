package net.ptcrys.topo.helper;

import java.util.Locale;

/**
 * Compact long formatting for slot-corner overlays: at most {@link #DEFAULT_MAX_LEN} characters with
 * k/M/G/T/P/E suffixes ("999", "1.2k", "999M"). Values under 10000 print verbatim.
 * Fluid corners use the bucket-denominated variant {@link #formatCompactBuckets(long)}.
 */
public final class OiCompactNumber {

    private static final int DEFAULT_MAX_LEN = 4;
    private static final long K = 1_000L;
    private static final long M = 1_000_000L;
    private static final long G = 1_000_000_000L;
    private static final long T = 1_000_000_000_000L;
    private static final long P = 1_000_000_000_000_000L;
    private static final long E = 1_000_000_000_000_000_000L;

    /** 1 桶 = 1000 mB(刻意不引 MC 的 FluidType,保持本助手纯 JVM 可测)。 */
    private static final long MB_PER_BUCKET = 1_000L;

    private OiCompactNumber() {}

    public static String formatCompact(long value) {
        return formatCompact(value, DEFAULT_MAX_LEN);
    }

    public static String formatCompact(long value, int maxLen) {
        if (value < 0L) {
            return "-" + formatCompact(-value, Math.max(1, maxLen - 1));
        }
        if (value < 10_000L) {
            return Long.toString(value);
        }
        return suffixed(value, maxLen, false);
    }

    /**
     * 流体角标的桶计价紧凑格式(对齐 AE2 终端惯例):输入 mB,按 1 桶 = 1000 mB 折算,"1" 即
     * 1000 mB。亚桶量以省略前导零的截断小数呈现(1 mB→".001",500 mB→".5");桶数不足 1000
     * 时携带塞得进 {@code maxLen} 的截断毫桶小数位("1.5"/"12.3"),塞不下则只显示整桶;从 1000 桶
     * 起换 {@link #formatCompact(long)} 同套 k/M/G 后缀("1k" = 1000 桶 = 一百万 mB)并完全忽略毫桶零头,
     * 恰为后缀整倍数时不带 ".0"。精确 mB 数以悬浮提示为准。
     */
    public static String formatCompactBuckets(long amountMb) {
        return formatCompactBuckets(amountMb, DEFAULT_MAX_LEN);
    }

    public static String formatCompactBuckets(long amountMb, int maxLen) {
        if (amountMb < 0L) {
            return "-" + formatCompactBuckets(-amountMb, Math.max(1, maxLen - 1));
        }
        long buckets = amountMb / MB_PER_BUCKET;
        String milliDigits = String.format(Locale.ROOT, "%03d", amountMb % MB_PER_BUCKET);
        if (buckets == 0L) {
            if (amountMb == 0L) {
                return "0";
            }
            String frac = trimEndZeros(milliDigits.substring(0, Math.clamp(maxLen - 1, 1, milliDigits.length())));
            return frac.isEmpty() ? "0" : "." + frac;
        }
        if (buckets >= K) {
            return suffixed(buckets, maxLen, true);
        }
        String whole = Long.toString(buckets);
        int fracBudget = maxLen - whole.length() - 1;
        if (fracBudget <= 0) {
            return whole;
        }
        String frac = trimEndZeros(milliDigits.substring(0, Math.min(milliDigits.length(), fracBudget)));
        return frac.isEmpty() ? whole : whole + "." + frac;
    }

    /**
     * 悬浮提示用的精确量文本(桶 + mB 双单位,千分位分组):"1,234.567 B (1,234,567 mB)",
     * 整桶时桶数不带小数。角标紧凑显示的信息损失由它兜底;负数按 0 处理(调用方对空槽/零量
     * 本就不出提示)。
     */
    public static String exactBucketsAndMb(long amountMb) {
        long mb = Math.max(amountMb, 0L);
        long buckets = mb / MB_PER_BUCKET;
        int milli = (int) (mb % MB_PER_BUCKET);
        String bucketText;
        if (milli == 0) {
            bucketText = String.format(Locale.ROOT, "%,d", buckets);
        } else {
            bucketText = String.format(Locale.ROOT, "%,d.%s", buckets, trimEndZeros(String.format(Locale.ROOT, "%03d", milli)));
        }
        return String.format(Locale.ROOT, "%s B (%,d mB)", bucketText, mb);
    }

    private static String suffixed(long value, int maxLen, boolean skipZeroDecimal) {
        if (value < M) {
            return scaled(value, K, "k", maxLen, skipZeroDecimal);
        }
        if (value < G) {
            return scaled(value, M, "M", maxLen, skipZeroDecimal);
        }
        if (value < T) {
            return scaled(value, G, "G", maxLen, skipZeroDecimal);
        }
        if (value < P) {
            return scaled(value, T, "T", maxLen, skipZeroDecimal);
        }
        if (value < E) {
            return scaled(value, P, "P", maxLen, skipZeroDecimal);
        }
        return scaled(value, E, "E", maxLen, skipZeroDecimal);
    }

    private static String scaled(long value, long unit, String suffix, int maxLen, boolean skipZeroDecimal) {
        long whole = value / unit;
        long remainder = value % unit;
        int decimal = (int) (remainder * 10L / unit);
        String decimalForm = whole + "." + decimal + suffix;
        String integerForm = whole + suffix;
        if (skipZeroDecimal && decimal == 0) {
            return integerForm;
        }
        return whole < 10L && decimalForm.length() <= maxLen ? decimalForm : integerForm;
    }

    private static String trimEndZeros(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '0') {
            end--;
        }
        return value.substring(0, end);
    }
}
