package dev.starcore.starcore.module.shop.model;
import java.util.Optional;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 商店实体
 * 代表一个完整的商店（玩家商店、NPC商店或公共商店）
 */
public final class Shop {
    private final UUID shopId;
    private final UUID ownerId;              // 拥有者ID（玩家UUID或国家ID）
    private final ShopOwnerType ownerType;  // 拥有者类型
    private String name;                      // 商店名称
    private String description;               // 商店描述
    private transient Location location;      // 商店位置（用于NPC商店，transient避免Gson序列化问题）
    private transient String locationWorld;   // 位置世界名（用于反序列化时恢复Location）
    private Integer npcId;                   // 关联的NPC ID（Citizens）
    private ShopType shopType;               // 商店类型
    private boolean infiniteStock;            // 是否无限库存
    private boolean buyEnabled;              // 是否启用购买
    private boolean sellEnabled;              // 是否启用出售
    private boolean nationPublic;             // 是否对国家成员公开
    private boolean globalPublic;             // 是否对所有人公开
    private Set<UUID> allowedPlayers;        // 允许访问的玩家列表
    private Set<UUID> blockedPlayers;        // 禁止访问的玩家列表
    private final List<ShopItem> items;       // 商店物品列表
    private final Instant createdAt;
    private Instant lastUpdated;
    private boolean open;                    // 商店是否营业
    private transient NationService nationService; // 国家服务（用于权限检查）

    public Shop(
        UUID shopId,
        UUID ownerId,
        ShopOwnerType ownerType,
        String name,
        ShopType shopType
    ) {
        this.shopId = Objects.requireNonNull(shopId, "shopId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        this.name = Objects.requireNonNull(name, "name");
        this.shopType = Objects.requireNonNull(shopType, "shopType");
        this.items = new ArrayList<>();
        this.allowedPlayers = new HashSet<>();
        this.blockedPlayers = new HashSet<>();
        this.infiniteStock = false;
        this.buyEnabled = true;
        this.sellEnabled = true;
        this.nationPublic = false;
        this.globalPublic = false;
        this.open = true;
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * 创建新商店
     */
    public static Shop create(UUID ownerId, ShopOwnerType ownerType, String name, ShopType shopType) {
        return new Shop(UUID.randomUUID(), ownerId, ownerType, name, shopType);
    }

    // ==================== Getters ====================

    public UUID shopId() {
        return shopId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public ShopOwnerType ownerType() {
        return ownerType;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Location location() {
        return location;
    }

    public Integer npcId() {
        return npcId;
    }

    public ShopType shopType() {
        return shopType;
    }

    public boolean infiniteStock() {
        return infiniteStock;
    }

    public boolean buyEnabled() {
        return buyEnabled;
    }

    public boolean sellEnabled() {
        return sellEnabled;
    }

    public boolean nationPublic() {
        return nationPublic;
    }

    public boolean globalPublic() {
        return globalPublic;
    }

    public Set<UUID> allowedPlayers() {
        return Collections.unmodifiableSet(allowedPlayers);
    }

    public Set<UUID> blockedPlayers() {
        return Collections.unmodifiableSet(blockedPlayers);
    }

    public List<ShopItem> items() {
        return Collections.unmodifiableList(items);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    public boolean isOpen() {
        return open;
    }

    // ==================== Setters ====================

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
        this.lastUpdated = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.lastUpdated = Instant.now();
    }

    public void setLocation(Location location) {
        this.location = location;
        this.locationWorld = location != null ? location.getWorld().getName() : null;
        this.lastUpdated = Instant.now();
    }

    public void setNpcId(Integer npcId) {
        this.npcId = npcId;
        this.lastUpdated = Instant.now();
    }

    public void setShopType(ShopType shopType) {
        this.shopType = Objects.requireNonNull(shopType, "shopType");
        this.lastUpdated = Instant.now();
    }

    public void setInfiniteStock(boolean infiniteStock) {
        this.infiniteStock = infiniteStock;
        this.lastUpdated = Instant.now();
    }

    public void setBuyEnabled(boolean buyEnabled) {
        this.buyEnabled = buyEnabled;
        this.lastUpdated = Instant.now();
    }

    public void setSellEnabled(boolean sellEnabled) {
        this.sellEnabled = sellEnabled;
        this.lastUpdated = Instant.now();
    }

    public void setNationPublic(boolean nationPublic) {
        this.nationPublic = nationPublic;
        this.lastUpdated = Instant.now();
    }

    public void setGlobalPublic(boolean globalPublic) {
        this.globalPublic = globalPublic;
        this.lastUpdated = Instant.now();
    }

    public void setOpen(boolean open) {
        this.open = open;
        this.lastUpdated = Instant.now();
    }

    public void setNationService(NationService nationService) {
        this.nationService = nationService;
    }

    /**
     * 获取商店拥有者的国家
     */
    private Optional<Nation> getOwnerNation() {
        if (nationService == null) {
            return Optional.empty();
        }
        if (ownerType == ShopOwnerType.NATION) {
            // 国家商店：直接用 ownerId 查询
            return nationService.nationById(NationId.of(ownerId));
        } else {
            // 玩家商店：通过玩家 UUID 获取国家
            return nationService.nationOf(ownerId);
        }
    }

    // ==================== 权限管理 ====================

    public void addAllowedPlayer(UUID playerId) {
        this.allowedPlayers.add(playerId);
        this.blockedPlayers.remove(playerId);
        this.lastUpdated = Instant.now();
    }

    public void removeAllowedPlayer(UUID playerId) {
        this.allowedPlayers.remove(playerId);
        this.lastUpdated = Instant.now();
    }

    public void addBlockedPlayer(UUID playerId) {
        this.blockedPlayers.add(playerId);
        this.allowedPlayers.remove(playerId);
        this.lastUpdated = Instant.now();
    }

    public void removeBlockedPlayer(UUID playerId) {
        this.blockedPlayers.remove(playerId);
        this.lastUpdated = Instant.now();
    }

    /**
     * 检查玩家是否有权限访问商店
     */
    public boolean canAccess(UUID playerId) {
        // 检查是否被封禁
        if (blockedPlayers.contains(playerId)) {
            return false;
        }

        // 检查是否全局公开
        if (globalPublic) {
            return true;
        }

        // 检查是否国家公开
        if (nationPublic && nationService != null) {
            // 获取商店拥有者的国家
            Optional<Nation> ownerNation = getOwnerNation();
            // 获取玩家的国家
            Optional<Nation> playerNation = nationService.nationOf(playerId);

            // 如果商店拥有者和玩家都在同一国家，允许访问
            if (ownerNation.isPresent() && playerNation.isPresent()) {
                if (ownerNation.get().getId().equals(playerNation.get().getId())) {
                    return true;
                }
            }
            return false;
        }

        // 检查是否在允许列表中
        if (allowedPlayers.contains(playerId)) {
            return true;
        }

        // 检查是否是拥有者
        return ownerId.equals(playerId);
    }

    // ==================== 物品管理 ====================

    /**
     * 添加物品到商店
     */
    public void addItem(ShopItem item) {
        Objects.requireNonNull(item, "item");
        items.add(item);
        this.lastUpdated = Instant.now();
    }

    /**
     * 移除商店物品
     */
    public boolean removeItem(UUID itemId) {
        boolean removed = items.removeIf(item -> item.itemId().equals(itemId));
        if (removed) {
            this.lastUpdated = Instant.now();
        }
        return removed;
    }

    /**
     * 获取物品
     */
    public Optional<ShopItem> getItem(UUID itemId) {
        return items.stream()
            .filter(item -> item.itemId().equals(itemId))
            .findFirst();
    }

    /**
     * 根据物品ID获取物品索引
     */
    public int getItemIndex(UUID itemId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).itemId().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 更新物品
     */
    public boolean updateItem(ShopItem updatedItem) {
        int index = getItemIndex(updatedItem.itemId());
        if (index >= 0) {
            items.set(index, updatedItem);
            this.lastUpdated = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * 获取商店中所有可购买的物品
     */
    public List<ShopItem> getBuyableItems() {
        return items.stream()
            .filter(item -> buyEnabled && (infiniteStock || item.stock() > 0))
            .toList();
    }

    /**
     * 获取商店中所有可出售的物品
     */
    public List<ShopItem> getSellableItems() {
        return items.stream()
            .filter(item -> sellEnabled && item.sellPrice().signum() > 0)
            .toList();
    }

    /**
     * 搜索物品
     */
    public List<ShopItem> searchItems(String searchTerm) {
        String term = searchTerm.toLowerCase();
        return items.stream()
            .filter(item -> {
                Material material = item.material();
                String materialName = material.name().toLowerCase();
                String displayName = item.displayName() != null ? item.displayName().toLowerCase() : "";
                return materialName.contains(term) || displayName.contains(term);
            })
            .toList();
    }

    // ==================== 统计 ====================

    /**
     * 获取物品数量
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * 获取总库存
     */
    public int getTotalStock() {
        if (infiniteStock) {
            return Integer.MAX_VALUE;
        }
        return items.stream()
            .mapToInt(ShopItem::stock)
            .sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Shop other)) return false;
        return shopId.equals(other.shopId);
    }

    @Override
    public int hashCode() {
        return shopId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Shop{id=%s, name='%s', type=%s, owner=%s, items=%d}",
            shopId, name, shopType, ownerId, items.size());
    }
}
