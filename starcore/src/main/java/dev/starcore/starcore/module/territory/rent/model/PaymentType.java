package dev.starcore.starcore.module.territory.rent.model;

/**
 * 付款类型
 */
public enum PaymentType {
    /**
     * 初始付款
     */
    INITIAL,

    /**
     * 续约付款
     */
    RENEWAL,

    /**
     * 每日自动扣款
     */
    DAILY,

    /**
     * 自动续约
     */
    AUTO
}
