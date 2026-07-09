package dev.starcore.starcore.module.shop.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 交易结果
 */
public final class TransactionResult {
    private final boolean success;
    private final String message;
    private final ShopTransaction transaction;
    private final BigDecimal amount;
    private final int actualQuantity;
    private final UUID itemId;

    private TransactionResult(
        boolean success,
        String message,
        ShopTransaction transaction,
        BigDecimal amount,
        int actualQuantity,
        UUID itemId
    ) {
        this.success = success;
        this.message = message;
        this.transaction = transaction;
        this.amount = amount;
        this.actualQuantity = actualQuantity;
        this.itemId = itemId;
    }

    /**
     * 创建成功结果
     */
    public static TransactionResult success(ShopTransaction transaction, int actualQuantity) {
        return new TransactionResult(
            true,
            "交易成功",
            transaction,
            transaction.getAmount(),
            actualQuantity,
            null  // ShopTransaction不包含itemId
        );
    }

    /**
     * 创建失败结果
     */
    public static TransactionResult failure(String message) {
        return new TransactionResult(false, message, null, BigDecimal.ZERO, 0, null);
    }

    /**
     * 创建库存不足结果
     */
    public static TransactionResult insufficientStock(int available, int requested) {
        return new TransactionResult(
            false,
            String.format("库存不足：请求 %d 件，只有 %d 件可用", requested, available),
            null,
            BigDecimal.ZERO,
            available,
            null
        );
    }

    /**
     * 创建余额不足结果
     */
    public static TransactionResult insufficientFunds(BigDecimal required, BigDecimal available) {
        return new TransactionResult(
            false,
            String.format("余额不足：需要 %s，只有 %s", required, available),
            null,
            required.subtract(available),
            0,
            null
        );
    }

    /**
     * 创建物品不存在结果
     */
    public static TransactionResult itemNotFound() {
        return new TransactionResult(false, "物品不存在", null, BigDecimal.ZERO, 0, null);
    }

    /**
     * 创建商店已关闭结果
     */
    public static TransactionResult shopClosed() {
        return new TransactionResult(false, "商店已关闭", null, BigDecimal.ZERO, 0, null);
    }

    // ==================== Getters ====================

    public boolean isSuccess() {
        return success;
    }

    public String message() {
        return message;
    }

    public ShopTransaction transaction() {
        return transaction;
    }

    public BigDecimal amount() {
        return amount;
    }

    public int actualQuantity() {
        return actualQuantity;
    }

    public UUID itemId() {
        return itemId;
    }

    @Override
    public String toString() {
        return String.format("TransactionResult{success=%s, message='%s', amount=%s, quantity=%d}",
            success, message, amount, actualQuantity);
    }
}
