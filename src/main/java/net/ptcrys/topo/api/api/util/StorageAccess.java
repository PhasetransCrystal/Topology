package net.ptcrys.topo.api.api.util;

import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.Map;

/**
 * 通用批量仓储访问。实现绑定目标和权限，返回实际转移及剩余数量。
 * 各后端自行管理事务；组合访问不承诺跨后端回滚。
 */
public interface StorageAccess {

    /** 取出整批需求；不得修改输入，允许部分满足。 */
    StorageResult extract(Map<? extends Resource, Long> request);

    /** 存入整批资源；未接收部分仍由调用方管理。 */
    StorageResult insert(Map<? extends Resource, Long> request);

    /**
     * 单资源便捷入口。返回实际取出数量，范围为 {@code [0, amount]}。
     *
     * @param resource 非空资源身份，包含用于区分资源的数据组件
     * @param amount   非负需求量，单位由资源类型定义
     */
    long extract(Resource resource, long amount);

    /** 单资源存入便捷入口，返回实际接收数量。 */
    long insert(Resource resource, long amount);
}
