package dev.starcore.starcore.module.shop.model;

/**
 * 交易状态
 */
public enum TransactionStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    CANCELLED("已取消"),
    REFUNDED("已退款");

    private final String displayName;

    TransactionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return this == COMPLETED;
    }

    /**
     * 检查是否可取消
     */
    public boolean isCancellable() {
        return this == PENDING || this == PROCESSING;
    }
}
