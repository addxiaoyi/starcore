package dev.starcore.starcore.module.territoryrent.model;

/**
 * 租借状态枚举
 */
public enum RentalStatus {
    /**
     * 租借中
     */
    ACTIVE("active", "租借中"),

    /**
     * 已完成（租期结束）
     */
    COMPLETED("completed", "已完成"),

    /**
     * 已终止（提前终止）
     */
    TERMINATED("terminated", "已终止"),

    /**
     * 已过期
     */
    EXPIRED("expired", "已过期"),

    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");

    private final String key;
    private final String displayName;

    RentalStatus(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 从键获取枚举值
     */
    public static RentalStatus fromKey(String key) {
        for (RentalStatus status : values()) {
            if (status.key.equalsIgnoreCase(key)) {
                return status;
            }
        }
        return ACTIVE;
    }
}