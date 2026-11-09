package net.ptcrys.topo.api.api.util;

/** 后端异常时携带此前已确认完成的转移；失败批次的结果不得自动重试。 */
public final class StorageAccessException extends RuntimeException {

    private final StorageResult confirmed;

    public StorageAccessException(String message, StorageResult confirmed, Throwable cause) {
        super(message, cause);
        this.confirmed = confirmed;
    }

    /** 失败批次之前已经确认的数量；剩余部分可能包含状态未知的资源。 */
    public StorageResult confirmed() {
        return confirmed;
    }
}
