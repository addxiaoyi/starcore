package dev.starcore.starcore.territory;

/**
 * 租赁状态枚举
 */
public enum LeaseStatus {

    /**
     * 待生效 - 等待首次支付
     */
    PENDING("§e待生效", "等待租客首次支付租金"),

    /**
     * 生效中 - 正常租赁中
     */
    ACTIVE("§a生效中", "租约正常生效"),

    /**
     * 已过期 - 租期结束
     */
    EXPIRED("§c已过期", "租期已结束"),

    /**
     * 已取消 - 提前终止
     */
    CANCELLED("§7已取消", "租约已被取消"),

    /**
     * 欠租中 - 未按时支付
     */
    OVERDUE("§6欠租中", "租金支付逾期");

    private final String displayName;
    private final String description;

    LeaseStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 检查是否为可租赁状态
     */
    public boolean isRentable() {
        return this == PENDING;
    }

    /**
     * 检查是否为有效状态
     */
    public boolean isValid() {
        return this == ACTIVE || this == OVERDUE;
    }

    /**
     * 检查是否已结束
     */
    public boolean isTerminated() {
        return this == EXPIRED || this == CANCELLED;
    }

    /**
     * 从字符串获取状态
     */
    public static LeaseStatus fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
