package dev.starcore.starcore.module.shop.service;

import dev.starcore.starcore.module.shop.model.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.*;

/**
 * 商店服务接口
 * 提供商店的创建、管理、交易等核心功能
 */
public interface ShopService {

    // ==================== 商店管理 ====================

    /**
     * 创建玩家商店
     */
    Shop createPlayerShop(Player owner, String name);

    /**
     * 创建国家商店
     */
    Shop createNationShop(UUID nationId, String name);

    /**
     * 获取商店
     */
    Optional<Shop> getShop(UUID shopId);

    /**
     * 获取玩家的所有商店
     */
    List<Shop> getPlayerShops(UUID playerId);

    /**
     * 获取国家所有商店
     */
    List<Shop> getNationShops(UUID nationId);

    /**
     * 获取所有公开商店
     */
    List<Shop> getPublicShops();

    /**
     * 获取所有商店（包括私有商店）
     */
    List<Shop> getAllShops();

    /**
     * 删除商店
     */
    boolean deleteShop(UUID shopId, UUID playerId);

    /**
     * 更新商店信息
     */
    boolean updateShop(Shop shop);

    /**
     * 保存所有商店数据
     */
    void saveAll();

    // ==================== 物品管理 ====================

    /**
     * 添加物品到商店
     */
    boolean addItemToShop(UUID shopId, ShopItem item, UUID playerId);

    /**
     * 从商店移除物品
     */
    boolean removeItemFromShop(UUID shopId, UUID itemId, UUID playerId);

    /**
     * 更新商店物品
     */
    boolean updateShopItem(UUID shopId, ShopItem item, UUID playerId);

    /**
     * 获取商店物品
     */
    Optional<ShopItem> getShopItem(UUID shopId, UUID itemId);

    // ==================== 交易 ====================

    /**
     * 玩家购买物品
     */
    TransactionResult buyItem(UUID shopId, UUID itemId, int quantity, Player player);

    /**
     * 玩家出售物品
     */
    TransactionResult sellItem(UUID shopId, UUID itemId, int quantity, ItemStack itemStack, Player player);

    /**
     * 获取商店交易记录
     */
    List<ShopTransaction> getShopTransactions(UUID shopId, int limit);

    /**
     * 获取玩家交易记录
     */
    List<ShopTransaction> getPlayerTransactions(UUID playerId, int limit);

    // ==================== 搜索 ====================

    /**
     * 搜索商店
     */
    List<Shop> searchShops(String searchTerm);

    /**
     * 按类型获取商店
     */
    List<Shop> getShopsByType(ShopType type);

    /**
     * 按价格范围获取商店
     */
    List<Shop> getShopsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice);

    // ==================== 统计 ====================

    /**
     * 获取商店总收入
     */
    BigDecimal getShopRevenue(UUID shopId);

    /**
     * 获取玩家总交易额
     */
    BigDecimal getPlayerTotalSpent(UUID playerId);

    /**
     * 获取玩家总收入
     */
    BigDecimal getPlayerTotalEarned(UUID playerId);

    // ==================== 权限 ====================

    /**
     * 检查玩家是否是商店管理员
     */
    boolean isShopAdmin(UUID playerId, UUID shopId);

    /**
     * 添加商店管理员
     */
    boolean addShopAdmin(UUID shopId, UUID playerId);

    /**
     * 移除商店管理员
     */
    boolean removeShopAdmin(UUID shopId, UUID playerId);

    // ==================== 数据重载 ====================

    /**
     * 重新加载所有商店数据
     */
    void reload();
}
