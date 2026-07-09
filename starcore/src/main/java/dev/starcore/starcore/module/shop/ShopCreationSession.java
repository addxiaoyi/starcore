package dev.starcore.starcore.module.shop;

import dev.starcore.starcore.module.shop.model.ShopType;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * 商店创建会话
 * 用于跟踪玩家创建商店的过程
 */
public class ShopCreationSession {

    private final UUID playerId;
    private final String playerName;
    private Location location;
    private ShopType shopType;
    private ItemStack itemStack;
    private int amount;
    private BigDecimal buyPrice;
    private BigDecimal sellPrice;
    private String shopName;
    private String description;
    private int maxStock;
    private long createdAt;

    public ShopCreationSession(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.amount = 1;
        this.buyPrice = BigDecimal.ZERO;
        this.sellPrice = BigDecimal.ZERO;
        this.maxStock = 100;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters
    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Location getLocation() {
        return location;
    }

    public ShopType getShopType() {
        return shopType;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public int getAmount() {
        return amount;
    }

    public BigDecimal getBuyPrice() {
        return buyPrice;
    }

    public BigDecimal getSellPrice() {
        return sellPrice;
    }

    public String getShopName() {
        return shopName;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxStock() {
        return maxStock;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // Setters
    public void setLocation(Location location) {
        this.location = location;
    }

    public void setShopType(ShopType shopType) {
        this.shopType = shopType;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = buyPrice != null ? buyPrice : BigDecimal.ZERO;
    }

    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = sellPrice != null ? sellPrice : BigDecimal.ZERO;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setMaxStock(int maxStock) {
        this.maxStock = Math.max(1, maxStock);
    }

    // Validation
    public boolean isComplete() {
        return location != null &&
               shopType != null &&
               itemStack != null &&
               itemStack.getType().isItem() &&
               itemStack.getType() != org.bukkit.Material.AIR &&
               (buyPrice.compareTo(BigDecimal.ZERO) > 0 || sellPrice.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * 获取缺失的需求字段
     * @return Optional.empty() 表示验证通过，Optional.of(field) 表示缺少指定字段
     */
    public Optional<String> getMissingRequirement() {
        if (location == null) return Optional.of("位置");
        if (shopType == null) return Optional.of("商店类型");
        if (itemStack == null || itemStack.getType() == org.bukkit.Material.AIR) return Optional.of("物品");
        if (buyPrice.compareTo(BigDecimal.ZERO) <= 0 && sellPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of("价格");
        }
        return Optional.empty();
    }

    /**
     * 重置会话
     */
    public void reset() {
        this.location = null;
        this.shopType = null;
        this.itemStack = null;
        this.amount = 1;
        this.buyPrice = BigDecimal.ZERO;
        this.sellPrice = BigDecimal.ZERO;
        this.shopName = null;
        this.description = null;
        this.maxStock = 100;
        this.createdAt = System.currentTimeMillis();
    }
}
