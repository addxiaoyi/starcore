package dev.starcore.starcore.module.lease.model;

/**
 * 租约状态枚举
 */
public enum LeaseStatus {
    /**
     * 待签署 - 租约已创建，等待双方签署
     */
    PENDING("待签署", "Pending"),

    /**
     * 进行中 - 租约已签署并生效
     */
    ACTIVE("进行中", "Active"),

    /**
     * 已过期 - 租约到期但未续签
     */
    EXPIRED("已过期", "Expired"),

    /**
     * 已终止 - 被出租方或承租方提前终止
     */
    TERMINATED("已终止", "Terminated"),

    /**
     * 逾期未付款 - 租约有效但未按时支付租金
     */
    OVERDUE("逾期未付款", "Overdue"),

    /**
     * 已完成 - 租约正常履行完毕
     */
    COMPLETED("已完成", "Completed");

    private final String zhName;
    private final String enName;

    LeaseStatus(String zhName, String enName) {
        this.zhName = zhName;
        this.enName = enName;
    }

    public String getZhName() {
        return zhName;
    }

    public String getEnName() {
        return enName;
    }

    public String getDisplayName(boolean chinese) {
        return chinese ? zhName : enName;
    }
}
