package dev.starcore.starcore.module.territory.rent.model;

/**
 * 租借契约状态枚举
 */
public enum LeaseStatus {
    /**
     * 契约生效中
     */
    ACTIVE("生效中"),

    /**
     * 等待确认/待处理
     */
    PENDING("待确认"),

    /**
     * 已过期
     */
    EXPIRED("已过期"),

    /**
     * 已终止
     */
    TERMINATED("已终止"),

    /**
     * 已完成（租金已全部支付）
     */
    COMPLETED("已完成");

    private final String displayName;

    LeaseStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
