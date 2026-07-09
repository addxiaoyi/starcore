package dev.starcore.starcore.protection.integration;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * 自定义方块保护接口
 *
 * 定义对第三方插件自定义方块的保护规则
 */
public interface CustomBlockProtection {

    /**
     * 检查玩家是否可以破坏指定方块
     *
     * @param player 玩家
     * @param block 方块
     * @return true 如果允许破坏
     */
    boolean canBreak(Player player, Block block);

    /**
     * 检查玩家是否可以放置指定方块
     *
     * @param player 玩家
     * @param block 方块位置
     * @return true 如果允许放置
     */
    boolean canPlace(Player player, Block block);

    /**
     * 检查玩家是否可以与方块交互
     *
     * @param player 玩家
     * @param block 方块
     * @return true 如果允许交互
     */
    boolean canInteract(Player player, Block block);

    /**
     * 检查方块是否为自定义方块
     *
     * @param block 方块
     * @return true 如果是自定义方块
     */
    boolean isCustomBlock(Block block);

    /**
     * 获取自定义方块的ID
     *
     * @param block 方块
     * @return 自定义方块ID，如果不是自定义方块返回null
     */
    String getCustomBlockId(Block block);
}
