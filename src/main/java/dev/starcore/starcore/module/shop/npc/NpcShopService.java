package dev.starcore.starcore.module.shop.npc;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * NPC商店服务接口
 * 定义NPC商店的核心功能
 */
public interface NpcShopService {

    // ==================== 商店管理 ====================

    /**
     * 创建NPC商店
     * @param name 商店名称
     * @param location 商店位置
     * @param owner 拥有者
     * @return 创建的商店数据
     */
    NpcShopData createNpcShop(String name, Location location, Player owner);

    /**
     * 创建带描述的NPC商店
     * @param name 商店名称
     * @param location 商店位置
     * @param owner 拥有者
     * @param description 商店描述
     * @return 创建的商店数据
     */
    NpcShopData createNpcShop(String name, Location location, Player owner, String description);

    /**
     * 删除NPC商店
     * @param shopId 商店ID
     * @return 是否成功删除
     */
    boolean deleteNpcShop(UUID shopId);

    /**
     * 获取NPC商店
     * @param shopId 商店ID
     * @return 商店数据
     */
    Optional<NpcShopData> getShop(UUID shopId);

    /**
     * 获取玩家拥有的所有NPC商店
     * @param playerId 玩家UUID
     * @return 商店列表
     */
    List<NpcShopData> getPlayerShops(UUID playerId);

    /**
     * 获取所有NPC商店
     * @return 所有商店列表
     */
    List<NpcShopData> getAllShops();

    // ==================== NPC绑定 ====================

    /**
     * 将商店绑定到NPC
     * @param shopId 商店ID
     * @param npcId NPC ID
     * @return 是否绑定成功
     */
    boolean bindToNpc(UUID shopId, int npcId);

    /**
     * 解除商店与NPC的绑定
     * @param shopId 商店ID
     * @return 是否解除成功
     */
    boolean unbindFromNpc(UUID shopId);

    /**
     * 解除指定NPC的商店绑定
     * @param npcId NPC ID
     * @return 是否解除成功
     */
    boolean unbindShopFromNpc(int npcId);

    /**
     * 根据NPC ID获取商店
     * @param npcId NPC ID
     * @return 商店数据
     */
    Optional<NpcShopData> getShopByNpc(int npcId);

    /**
     * 检查NPC是否绑定了商店
     * @param npcId NPC ID
     * @return 是否有商店
     */
    boolean hasShop(int npcId);

    // ==================== 物品管理 ====================

    /**
     * 添加物品到商店
     * @param shopId 商店ID
     * @param material 物品材质
     * @param price 购买价格
     * @param stock 库存数量
     * @return 添加的物品数据
     */
    NpcShopItemData addItem(UUID shopId, Material material, BigDecimal price, int stock);

    /**
     * 添加物品到商店（设置出售价）
     * @param shopId 商店ID
     * @param material 物品材质
     * @param buyPrice 购买价格
     * @param sellPrice 出售价格
     * @param stock 库存数量
     * @return 添加的物品数据
     */
    NpcShopItemData addItem(UUID shopId, Material material, BigDecimal buyPrice, BigDecimal sellPrice, int stock);

    /**
     * 移除商店物品
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
    List<NpcShopItemData> getShopItems(UUID shopId);

    /**
     * 获取商店物品
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @return 物品数据
     */
    Optional<NpcShopItemData> getItem(UUID shopId, UUID itemId);

    /**
     * 更新物品价格
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @param buyPrice 新购买价格
     * @param sellPrice 新出售价格
     * @return 更新后的物品数据
     */
    Optional<NpcShopItemData> updateItemPrice(UUID shopId, UUID itemId, BigDecimal buyPrice, BigDecimal sellPrice);

    /**
     * 更新物品库存
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @param stock 新库存数量
     * @return 更新后的物品数据
     */
    Optional<NpcShopItemData> updateItemStock(UUID shopId, UUID itemId, int stock);

    // ==================== 库存设置 ====================

    /**
     * 设置商店无限库存
     * @param shopId 商店ID
     * @param infinite 是否无限
     * @return 更新后的商店数据
     */
    Optional<NpcShopData> setInfiniteStock(UUID shopId, boolean infinite);

    /**
     * 更新商店属性
     * @param shopId 商店ID
     * @param property 属性名 (name/description/infinite/buy/sell)
     * @param value 新值
     * @return 更新后的商店数据
     */
    Optional<NpcShopData> updateShop(UUID shopId, String property, String value);

    /**
     * 设置物品无限库存
     * @param shopId 商店ID
     * @param itemId 物品ID
     * @param infinite 是否无限
     * @return 更新后的物品数据
     */
    Optional<NpcShopItemData> setItemInfiniteStock(UUID shopId, UUID itemId, boolean infinite);

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
     * 获取交易记录
     * @param shopId 商店ID
     * @return 交易记录列表
     */
    List<NpcShopStorage.NpcTradeRecord> getTradeHistory(UUID shopId);

    /**
     * 获取玩家交易记录
     * @param playerId 玩家UUID
     * @param limit 返回记录数量限制
     * @return 交易记录列表
     */
    List<NpcShopStorage.NpcTradeRecord> getPlayerTradeHistory(UUID playerId, int limit);

    // ==================== 管理 ====================

    /**
     * 重载商店配置
     */
    void reload();

    /**
     * 保存所有数据
     */
    void saveAll();

    /**
     * 打开商店GUI
     * @param player 玩家
     * @param shopId 商店ID
     */
    void openShopGui(Player player, UUID shopId);

    /**
     * 打开NPC商店GUI
     * @param player 玩家
     * @param npcId NPC ID
     */
    void openNpcShopGui(Player player, int npcId);

    /**
     * 处理NPC点击
     * @param player 玩家
     * @param npcId NPC ID
     */
    void onNpcClick(Player player, int npcId);

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
}
