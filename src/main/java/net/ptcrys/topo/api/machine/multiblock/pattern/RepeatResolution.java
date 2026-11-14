package net.ptcrys.topo.api.machine.multiblock.pattern;

import java.util.Arrays;

/**
 * 一次布局解析选定的各重复段副本数，按段的声明顺序索引。
 * 内部数组防御性复制；成型占地、定点失效、预留和预览使用同一计数。
 */
public final class RepeatResolution {

    private final int[] counts;

    public RepeatResolution(int[] counts) {
        this.counts = counts.clone();
    }

    /** 指定重复段的副本数，不是展开层数。 */
    public int count(int sectionIndex) {
        return counts[sectionIndex];
    }

    public int size() {
        return counts.length;
    }

    /** 返回内部计数的防御性副本。 */
    public int[] countsCopy() {
        return counts.clone();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof RepeatResolution other && Arrays.equals(counts, other.counts));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(counts);
    }

    @Override
    public String toString() {
        return "RepeatResolution" + Arrays.toString(counts);
    }
}
