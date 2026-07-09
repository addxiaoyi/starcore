package dev.starcore.starcore.module.shop.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 商店物品
 * 代表商店中出售的单个物品
 */
public final class ShopItem {
    private final UUID itemId;
    private final Material material;
    private String displayName;              // 显示名称（可自定义）
    private List<String> lore;               // 物品描述
    private int amount;                      // 数量
    private int stock;                       // 当前库存
    private int maxStock;                    // 最大库存
    private BigDecimal buyPrice;             // 购买价格
    private BigDecimal sellPrice;            // 出售价格
    private int restockAmount;               // 自动补货数量
    private int restockInterval;             // 自动补货间隔（分钟）
    private Instant lastRestock;             // 上次补货时间
    private boolean infiniteStock;           // 是否无限库存
    private Map<String, Object> metadata;    // 自定义元数据
    private final Instant createdAt;
    private Instant lastUpdated;

    public ShopItem(
        UUID itemId,
        Material material,
        int amount,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        int stock,
        int maxStock
    ) {
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.material = Objects.requireNonNull(material, "material");
        this.amount = amount;
        this.buyPrice = Objects.requireNonNull(buyPrice, "buyPrice");
        this.sellPrice = Objects.requireNonNull(sellPrice, "sellPrice");
        this.stock = Math.max(0, stock);
        this.maxStock = Math.max(1, maxStock);
        this.lore = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.infiniteStock = false;
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * 创建新商店物品
     */
    public static ShopItem create(Material material, int amount, BigDecimal buyPrice, BigDecimal sellPrice, int stock) {
        return new ShopItem(
            UUID.randomUUID(),
            material,
            amount,
            buyPrice,
            sellPrice,
            stock,
            stock * 10 // 默认最大库存为初始库存的10倍
        );
    }

    /**
     * 创建无限库存物品
     */
    public static ShopItem createInfinite(Material material, int amount, BigDecimal buyPrice, BigDecimal sellPrice) {
        ShopItem item = new ShopItem(
            UUID.randomUUID(),
            material,
            amount,
            buyPrice,
            sellPrice,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
        );
        item.infiniteStock = true;
        return item;
    }

    // ==================== Getters ====================

    public UUID itemId() {
        return itemId;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return Collections.unmodifiableList(lore);
    }

    public int amount() {
        return amount;
    }

    public int stock() {
        return stock;
    }

    public int maxStock() {
        return maxStock;
    }

    public BigDecimal buyPrice() {
        return buyPrice;
    }

    public BigDecimal sellPrice() {
        return sellPrice;
    }

    public int restockAmount() {
        return restockAmount;
    }

    public int restockInterval() {
        return restockInterval;
    }

    public Instant lastRestock() {
        return lastRestock;
    }

    public boolean infiniteStock() {
        return infiniteStock;
    }

    public Map<String, Object> metadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    // ==================== Setters ====================

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.lastUpdated = Instant.now();
    }

    public void setLore(List<String> lore) {
        this.lore = new ArrayList<>(Objects.requireNonNull(lore, "lore"));
        this.lastUpdated = Instant.now();
    }

    public void addLoreLine(String line) {
        this.lore.add(line);
        this.lastUpdated = Instant.now();
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
        this.lastUpdated = Instant.now();
    }

    public void setStock(int stock) {
        if (!infiniteStock) {
            this.stock = Math.max(0, Math.min(stock, maxStock));
        }
        this.lastUpdated = Instant.now();
    }

    public void setMaxStock(int maxStock) {
        this.maxStock = Math.max(1, maxStock);
        if (!infiniteStock) {
            this.stock = Math.min(stock, this.maxStock);
        }
        this.lastUpdated = Instant.now();
    }

    public void setBuyPrice(BigDecimal buyPrice) {
        this.buyPrice = Objects.requireNonNull(buyPrice, "buyPrice");
        this.lastUpdated = Instant.now();
    }

    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = Objects.requireNonNull(sellPrice, "sellPrice");
        this.lastUpdated = Instant.now();
    }

    public void setRestockAmount(int restockAmount) {
        this.restockAmount = Math.max(0, restockAmount);
        this.lastUpdated = Instant.now();
    }

    public void setRestockInterval(int restockInterval) {
        this.restockInterval = Math.max(0, restockInterval);
        this.lastUpdated = Instant.now();
    }

    public void setLastRestock(Instant lastRestock) {
        this.lastRestock = lastRestock;
    }

    public void setInfiniteStock(boolean infiniteStock) {
        this.infiniteStock = infiniteStock;
        if (infiniteStock) {
            this.stock = Integer.MAX_VALUE;
            this.maxStock = Integer.MAX_VALUE;
        }
        this.lastUpdated = Instant.now();
    }

    public void setMetadata(String key, Object value) {
        this.metadata.put(key, value);
        this.lastUpdated = Instant.now();
    }

    // ==================== 库存操作 ====================

    /**
     * 购买物品（减少库存）
     * @param quantity 购买数量
     * @return 实际能购买的数量
     */
    public int purchase(int quantity) {
        if (quantity <= 0) {
            return 0;
        }

        if (infiniteStock) {
            return quantity;
        }

        int actual = Math.min(quantity, stock);
        this.stock -= actual;
        this.lastUpdated = Instant.now();
        return actual;
    }

    /**
     * 出售物品（增加库存）
     * @param quantity 出售数量
     * @return 实际能出售的数量
     */
    public int sell(int quantity) {
        if (quantity <= 0) {
            return 0;
        }

        if (infiniteStock) {
            return quantity;
        }

        int actual = Math.min(quantity, maxStock - stock);
        this.stock += actual;
        this.lastUpdated = Instant.now();
        return actual;
    }

    /**
     * 补货
     */
    public void restock() {
        if (infiniteStock || restockAmount <= 0) {
            return;
        }
        this.stock = Math.min(stock + restockAmount, maxStock);
        this.lastRestock = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * 检查是否需要补货
     */
    public boolean needsRestock() {
        if (infiniteStock || restockAmount <= 0 || restockInterval <= 0) {
            return false;
        }
        if (lastRestock == null) {
            return stock < maxStock;
        }
        long minutesSinceLastRestock = java.time.Duration.between(lastRestock, Instant.now()).toMinutes();
        return stock < maxStock && minutesSinceLastRestock >= restockInterval;
    }

    // ==================== 计算 ====================

    /**
     * 计算购买总价
     */
    public BigDecimal calculateBuyPrice(int quantity) {
        return buyPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 计算出售总价
     */
    public BigDecimal calculateSellPrice(int quantity) {
        return sellPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * 检查库存是否充足
     */
    public boolean hasStock(int quantity) {
        return infiniteStock || stock >= quantity;
    }

    /**
     * 检查库存是否已满
     */
    public boolean isFull() {
        return !infiniteStock && stock >= maxStock;
    }

    /**
     * 获取库存百分比
     */
    public double getStockPercentage() {
        if (infiniteStock) {
            return 100.0;
        }
        return (double) stock / maxStock * 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShopItem other)) return false;
        return itemId.equals(other.itemId);
    }

    @Override
    public int hashCode() {
        return itemId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ShopItem{id=%s, material=%s, amount=%d, stock=%d/%d, buyPrice=%s, sellPrice=%s}",
            itemId, material, amount, stock, maxStock, buyPrice, sellPrice);
    }

    /**
     * 转换为Bukkit ItemStack
     */
    public ItemStack toBukkitItemStack() {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null) {
                meta.displayName(net.kyori.adventure.text.Component.text(displayName));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore.stream()
                    .map(net.kyori.adventure.text.Component::text)
                    .collect(java.util.stream.Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
