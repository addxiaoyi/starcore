package dev.starcore.starcore.module.shop.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 商店交易记录
 *
 * <p>简化模型：交易记录不直接存储 itemId 和 quantity，
 * 这些信息通过 itemDetails 字符串字段以文本形式记录。</p>
 */
public final class ShopTransaction {
    private final UUID transactionId;
    private final UUID shopId;
    private final UUID playerId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final Instant timestamp;
    private String itemDetails;

    public ShopTransaction(
        UUID transactionId,
        UUID shopId,
        UUID playerId,
        TransactionType type,
        BigDecimal amount,
        Instant timestamp,
        String itemDetails
    ) {
        this.transactionId = transactionId;
        this.shopId = shopId;
        this.playerId = playerId;
        this.type = type;
        this.amount = amount;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.itemDetails = itemDetails;
    }

    // ==================== Getters ====================

    public UUID getTransactionId() { return transactionId; }
    public UUID getShopId() { return shopId; }
    public UUID getPlayerId() { return playerId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Instant getTimestamp() { return timestamp; }
    public String getItemDetails() { return itemDetails; }

    public void setItemDetails(String itemDetails) { this.itemDetails = itemDetails; }

    // ==================== Backward-compatible accessors ====================

    public TransactionType type() { return type; }
    public BigDecimal totalPrice() { return amount; }
    public UUID shopId() { return shopId; }
    public UUID playerId() { return playerId; }

    /**
     * 返回物品ID（简化模型：返回 null 表示无物品ID）
     * @return 从 itemDetails 中解析物品ID，如果没有则返回 null
     */
    public UUID itemId() {
        if (itemDetails == null) return null;
        // 格式: "Item: UUID, Qty: N, Unit: xxx"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Item:\\s*([a-f0-9\\-]+)");
        java.util.regex.Matcher matcher = pattern.matcher(itemDetails);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 返回交易数量
     * @return 从 itemDetails 中解析数量，格式: "Item: ..., Qty: N, Unit: ..."
     */
    public int quantity() {
        if (itemDetails == null) return 0;
        // 格式: "Item: xxx, Qty: N, Unit: xxx"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(",\\s*Qty:\\s*(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(itemDetails);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    // ==================== Factory method ====================

    public static ShopTransaction create(UUID shopId, UUID playerId, UUID itemId,
            TransactionType type, int quantity, BigDecimal unitPrice) {
        UUID transactionId = UUID.randomUUID();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        Instant timestamp = Instant.now();
        String itemDetails = "Item: " + itemId + ", Qty: " + quantity + ", Unit: " + unitPrice;
        return new ShopTransaction(transactionId, shopId, playerId, type, totalPrice, timestamp, itemDetails);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShopTransaction other)) return false;
        return transactionId.equals(other.transactionId);
    }

    @Override
    public int hashCode() {
        return transactionId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ShopTransaction{id=%s, type=%s, amount=%s, player=%s}",
            transactionId, type, amount, playerId);
    }
}
