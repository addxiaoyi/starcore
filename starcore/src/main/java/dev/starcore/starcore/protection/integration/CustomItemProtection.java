package dev.starcore.starcore.protection.integration;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 自定义物品保护接口
 *
 * 定义对第三方插件自定义物品的保护规则
 */
public interface CustomItemProtection {

    /**
     * 检查玩家是否可以使用指定物品
     *
     * @param player 玩家
     * @param item 物品
     * @return true 如果允许使用
     */
    boolean canUse(Player player, ItemStack item);

    /**
     * 检查玩家是否可以拾取指定物品
     *
     * @param player 玩家
     * @param item 物品
     * @return true 如果允许拾取
     */
    boolean canPickup(Player player, ItemStack item);

    /**
     * 检查玩家是否可以丢弃指定物品
     *
     * @param player 玩家
     * @param item 物品
     * @return true 如果允许丢弃
     */
    boolean canDrop(Player player, ItemStack item);

    /**
     * 检查玩家是否可以制作指定物品
     *
     * @param player 玩家
     * @param item 物品
     * @return true 如果允许制作
     */
    boolean canCraft(Player player, ItemStack item);

    /**
     * 检查物品是否为自定义物品
     *
     * @param item 物品
     * @return true 如果是自定义物品
     */
    boolean isCustomItem(ItemStack item);

    /**
     * 获取自定义物品的ID
     *
     * @param item 物品
     * @return 自定义物品ID，如果不是自定义物品返回null
     */
    String getCustomItemId(ItemStack item);
}
