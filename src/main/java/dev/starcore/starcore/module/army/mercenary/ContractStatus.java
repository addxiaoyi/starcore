package dev.starcore.starcore.module.army.mercenary;

/**
 * 雇佣兵合同状态
 */
public enum ContractStatus {
    ACTIVE("active", "进行中"),
    PENDING_PAYMENT("pending_payment", "待支付"),
    COMPLETED("completed", "已完成"),
    TERMINATED("terminated", "已终止"),
    EXPIRED("expired", "已过期"),
    SUSPENDED("suspended", "已暂停");

    private final String key;
    private final String displayName;

    ContractStatus(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isTerminated() {
        return this == TERMINATED || this == EXPIRED;
    }
}