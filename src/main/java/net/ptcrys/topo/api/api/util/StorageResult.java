package net.ptcrys.topo.api.api.util;

import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次实际转移的不可变结果。后续仓储直接处理 {@link #remaining()}。
 */
public record StorageResult(Map<Resource, Long> fulfilled, Map<Resource, Long> remaining) {

    public StorageResult {
        fulfilled = requestCopy(fulfilled);
        remaining = requestCopy(remaining);
    }

    /** 校验全部需求后复制，保留请求顺序；须在产生任何仓储副作用前调用。 */
    public static Map<Resource, Long> requestCopy(Map<? extends Resource, Long> request) {
        Objects.requireNonNull(request, "storage request");
        Map<Resource, Long> copy = new LinkedHashMap<>();
        request.forEach((resource, amount) -> {
            check(resource, Objects.requireNonNull(amount, "storage amount"));
            if (amount > 0) copy.put(resource, amount);
        });
        return Collections.unmodifiableMap(copy);
    }

    /** 按本次输入核对实际转移数量并计算剩余；不接受额外资源或超额转移。 */
    public static StorageResult of(Map<? extends Resource, Long> request, Map<? extends Resource, Long> fulfilled) {
        Map<Resource, Long> demand = requestCopy(request);
        Map<Resource, Long> actual = requestCopy(fulfilled);
        actual.forEach((resource, amount) -> {
            if (amount > demand.getOrDefault(resource, 0L))
                throw new IllegalArgumentException("Storage fulfilled more than requested");
        });
        Map<Resource, Long> remaining = new LinkedHashMap<>();
        demand.forEach((resource, amount) -> {
            long left = amount - actual.getOrDefault(resource, 0L);
            if (left > 0) remaining.put(resource, left);
        });
        return new StorageResult(actual, remaining);
    }

    public static void check(Resource resource, long amount) {
        Objects.requireNonNull(resource, "storage resource");
        if (resource.isEmpty() || amount < 0) throw new IllegalArgumentException("Expected non-empty resource and non-negative amount");
    }
}
