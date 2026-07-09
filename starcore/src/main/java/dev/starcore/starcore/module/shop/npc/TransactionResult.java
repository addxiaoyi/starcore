package dev.starcore.starcore.module.shop.npc;

import java.math.BigDecimal;

/**
 * 交易结果（NPC商店专用）
 * 用于返回购买/出售操作的结果
 *
 * @param success 是否成功
 * @param message 结果消息
 * @param actualQuantity 实际交易数量
 * @param totalPrice 总价
 * @param itemName 物品名称
 * @param isBuy 是否是购买操作
 */
public record TransactionResult(
    boolean success,
    String message,
    int actualQuantity,
    BigDecimal totalPrice,
    String itemName,
    boolean isBuy
) {
    /**
     * 创建成功结果
     */
    public static TransactionResult success(String message, int quantity, BigDecimal price) {
        return new TransactionResult(true, message, quantity, price, null, true);
    }

    /**
     * 创建成功结果（带物品名称）
     */
    public static TransactionResult success(String message, int quantity, BigDecimal price, String itemName, boolean isBuy) {
        return new TransactionResult(true, message, quantity, price, itemName, isBuy);
    }

    /**
     * 创建失败结果
     */
    public static TransactionResult failure(String message) {
        return new TransactionResult(false, message, 0, BigDecimal.ZERO, null, false);
    }

    /**
     * 创建库存不足结果
     */
    public static TransactionResult outOfStock(String itemName, int available) {
        String msg = String.format("%s 库存不足! (剩余: %d)", itemName, available);
        return new TransactionResult(false, msg, available, BigDecimal.ZERO, itemName, false);
    }

    /**
     * 创建余额不足结果
     */
    public static TransactionResult insufficientFunds(BigDecimal required, BigDecimal available) {
        BigDecimal deficit = required.subtract(available);
        String msg = String.format("金币不足! 需要 %s，还差 %s", required.toPlainString(), deficit.toPlainString());
        return new TransactionResult(false, msg, 0, required, null, true);
    }

    /**
     * 创建物品不足结果
     */
    public static TransactionResult insufficientItems(String itemName, int have, int need) {
        String msg = String.format("你没有足够的 %s (拥有: %d, 需要: %d)", itemName, have, need);
        return new TransactionResult(false, msg, have, BigDecimal.ZERO, itemName, false);
    }

    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 检查是否失败
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * 获取格式化金额字符串
     */
    public String getFormattedPrice() {
        if (totalPrice == null || totalPrice.signum() <= 0) {
            return "0";
        }
        return totalPrice.toPlainString();
    }

    /**
     * 获取操作类型描述
     */
    public String getOperationType() {
        return isBuy ? "购买" : "出售";
    }

    @Override
    public String toString() {
        return String.format("TransactionResult{success=%s, message='%s', quantity=%d, price=%s, item=%s, isBuy=%s}",
            success, message, actualQuantity, totalPrice, itemName, isBuy);
    }
}
