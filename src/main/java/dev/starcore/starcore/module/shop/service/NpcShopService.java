package dev.starcore.starcore.module.shop.service;

import dev.starcore.starcore.module.shop.model.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NPC商店服务接口
 * 提供NPC绑定商店的创建、管理和交互
 */
public interface NpcShopService {

    // ==================== 交易 ====================

    /**
     * 购买物品
     * @param player 玩家
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @param amount 数量
     * @return 购买结果
     */
    TransactionResult buyItem(Player player, UUID shopId, UUID itemId, int amount);

    /**
     * 出售物品
     * @param player 玩家
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @param amount 数量
     * @return 出售结果
     */
    TransactionResult sellItem(Player player, UUID shopId, UUID itemId, int amount);

    /**
     * 获取商店交易记录
     * @param shopId 商店ID
     * @return 交易记录列表
     */
    List<ShopTransaction> getTradeHistory(UUID shopId);

    /**
     * 获取玩家交易历史记录
     * @param playerId 玩家UUID
     * @param limit 返回记录数量限制
     * @return 交易记录列表，按时间倒序排列
     */
    List<ShopTransaction> getPlayerTradeHistory(UUID playerId, int limit);

    // ==================== NPC绑定 ====================

    /**
     * 将商店绑定到NPC
     */
    boolean bindShopToNpc(UUID shopId, int npcId);

    /**
     * 解除商店与NPC的绑定
     */
    boolean unbindShopFromNpc(int npcId);

    /**
     * 获取NPC绑定的商店
     */
    Optional<Shop> getShopByNpc(int npcId);

    /**
     * 获取绑定到NPC的商店ID
     */
    Optional<UUID> getNpcShopId(int npcId);

    /**
     * 检查NPC是否有绑定商店
     */
    boolean hasShop(int npcId);

    // ==================== 商店模板 ====================

    /**
     * 创建NPC商店模板
     */
    Shop createNpcShopTemplate(String name, String description, ShopCategory category);

    /**
     * 创建并绑定NPC商店
     * @param name 商店名称
     * @param description 商店描述
     * @param npcId NPC ID
     * @param category 商店类别
     * @return 创建的商店，如果NPC不存在或绑定失败返回null
     */
    Shop createNpcShop(String name, String description, int npcId, ShopCategory category);

    /**
     * 获取商店模板
     */
    Optional<Shop> getShopTemplate(String templateId);

    /**
     * 获取所有商店模板
     */
    List<Shop> getAllTemplates();

    /**
     * 删除商店模板
     */
    boolean deleteTemplate(String templateId);

    // ==================== 商店类别 ====================

    /**
     * 获取商店类别
     */
    List<ShopCategory> getCategories();

    /**
     * 获取类别下的商店
     */
    List<Shop> getShopsByCategory(ShopCategory category);

    // ==================== 交互 ====================

    /**
     * 打开商店GUI
     */
    void openShopGui(Player player, UUID shopId);

    /**
     * 打开NPC商店GUI
     */
    void openNpcShopGui(Player player, int npcId);

    /**
     * 处理NPC点击
     */
    void onNpcClick(Player player, int npcId);

    // ==================== 交易限制 ====================

    /**
     * 检查玩家是否可以交易
     */
    boolean canTrade(Player player, UUID shopId);

    /**
     * 设置玩家交易限制
     */
    boolean setPlayerTradeLimit(UUID shopId, UUID playerId, TradeLimit limit);

    /**
     * 获取玩家交易限制
     */
    TradeLimit getPlayerTradeLimit(UUID shopId, UUID playerId);

    /**
     * 获取玩家已交易次数
     */
    int getPlayerTradeCount(UUID shopId, UUID playerId);

    /**
     * 记录交易
     */
    void recordTrade(UUID shopId, UUID playerId, int quantity);

    // ==================== 物品管理 ====================

    /**
     * 添加物品到商店
     * @param shopId 商店ID
     * @param material 物品材质
     * @param price 购买价格
     * @param stock 库存数量
     * @return 添加的物品数据
     */
    ShopItem addItem(UUID shopId, Material material, BigDecimal price, int stock);

    /**
     * 添加物品到商店（设置出售价）
     * @param shopId 商店ID
     * @param material 物品材质
     * @param buyPrice 购买价格
     * @param sellPrice 出售价格
     * @param stock 库存数量
     * @return 添加的物品数据
     */
    ShopItem addItem(UUID shopId, Material material, BigDecimal buyPrice, BigDecimal sellPrice, int stock);

    /**
     * 从商店移除物品
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @return 是否成功移除
     */
    boolean removeItem(UUID shopId, UUID itemId);

    /**
     * 获取商店物品列表
     * @param shopId 商店ID
     * @return 物品列表
     */
    List<ShopItem> getShopItems(UUID shopId);

    /**
     * 更新物品价格
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @param buyPrice 新购买价格
     * @param sellPrice 新出售价格
     * @return 是否更新成功
     */
    boolean updateItemPrice(UUID shopId, UUID itemId, BigDecimal buyPrice, BigDecimal sellPrice);

    // ==================== 库存管理 ====================

    /**
     * 设置商店无限库存
     * @param shopId 商店ID
     * @param infinite 是否无限库存
     * @return 更新后的商店数据
     */
    Shop setInfiniteStock(UUID shopId, boolean infinite);

    // ==================== 统计 ====================

    /**
     * 获取商店总收入
     * @param shopId 商店ID
     * @return 总收入
     */
    BigDecimal getShopRevenue(UUID shopId);

    /**
     * 获取商店总交易次数
     * @param shopId 商店ID
     * @return 交易次数
     */
    int getShopTransactionCount(UUID shopId);

    // ==================== 数据重载 ====================

    /**
     * 重新加载所有数据
     * 清空缓存并从数据库重新加载
     */
    void reload();
}
