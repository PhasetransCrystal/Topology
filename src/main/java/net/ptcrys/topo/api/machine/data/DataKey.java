package net.ptcrys.topo.api.machine.data;

import net.minecraft.resources.Identifier;

import com.mojang.serialization.Codec;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** 自定义值字段的声明。键须由声明方复用同一实例；复制函数必须隔离可变对象，值不可为空。 */
public record DataKey<T>(Identifier id, Codec<T> codec, T initialValue, UnaryOperator<T> copier) {

    public DataKey {
        Objects.requireNonNull(id);
        Objects.requireNonNull(codec);
        Objects.requireNonNull(initialValue);
        Objects.requireNonNull(copier);
        initialValue = Objects.requireNonNull(copier.apply(initialValue));
    }

    /** 仅用于真正不可变的值类型。 */
    public static <T> DataKey<T> immutable(Identifier id, Codec<T> codec, T initialValue) {
        return new DataKey<>(id, codec, initialValue, UnaryOperator.identity());
    }

    @Override
    public T initialValue() {
        return copy(initialValue);
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    public T copy(T value) {
        return Objects.requireNonNull(copier.apply(Objects.requireNonNull(value)));
    }
}
