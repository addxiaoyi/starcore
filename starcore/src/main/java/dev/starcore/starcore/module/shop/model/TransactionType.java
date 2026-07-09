package dev.starcore.starcore.module.shop.model;

/**
 * 交易类型
 */
public enum TransactionType {
    BUY("购买", "玩家从商店购买物品"),
    SELL("出售", "玩家向商店出售物品"),
    AUCTION_BID("竞拍", "玩家参与拍卖出价"),
    AUCTION_WIN("竞拍获胜", "玩家竞拍成功"),
    AUCTION_LIST("上架", "物品上架拍卖"),
    AUCTION_CANCEL("取消拍卖", "拍卖被取消"),
    ADMIN_GIVE("管理员发放", "管理员发放物品"),
    ADMIN_TAKE("管理员回收", "管理员回收物品");

    private final String displayName;
    private final String description;

    TransactionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * 检查是否是购买类型
     */
    public boolean isBuy() {
        return this == BUY;
    }

    /**
     * 检查是否是出售类型
     */
    public boolean isSell() {
        return this == SELL;
    }

    /**
     * 检查是否是拍卖相关
     */
    public boolean isAuction() {
        return this == AUCTION_BID || this == AUCTION_WIN ||
               this == AUCTION_LIST || this == AUCTION_CANCEL;
    }
}
