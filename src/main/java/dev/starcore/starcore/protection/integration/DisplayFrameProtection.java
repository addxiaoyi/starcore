package dev.starcore.starcore.protection.integration;

import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 展示框保护
 *
 * 保护展示框中的自定义物品
 * - 防止破坏包含自定义物品的展示框
 * - 防止从展示框中取出自定义物品
 * - 防止旋转展示框中的自定义物品
 */
public class DisplayFrameProtection {

    private final Plugin plugin;
    private final Logger logger;
    private final PluginIntegrationManager integrationManager;

    public DisplayFrameProtection(Plugin plugin, PluginIntegrationManager integrationManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.integrationManager = integrationManager;
    }

    /**
     * 检查玩家是否可以破坏展示框
     */
    public boolean canBreakFrame(Player player, ItemFrame frame) {
        ItemStack item = frame.getItem();

        // 如果展示框为空，允许破坏
        if (item == null || item.getType().isAir()) {
            return true;
        }

        // 检查是否为自定义物品
        if (!integrationManager.isCustomItem(item)) {
            return true;
        }

        // 检查破坏权限
        if (!player.hasPermission("starcore.protection.itemframe.break")) {
            logger.fine(String.format("玩家 %s 无权破坏包含自定义物品的展示框 at %s",
                    player.getName(), formatLocation(frame)));
            return false;
        }

        // 检查特定自定义物品的权限
        if (integrationManager.isIntegrationEnabled("Slimefun")) {
            if (!player.hasPermission("starcore.protection.itemframe.break.slimefun")) {
                logger.fine(String.format("玩家 %s 无权破坏包含粘液科技物品的展示框",
                        player.getName()));
                return false;
            }
        }

        return true;
    }

    /**
     * 检查玩家是否可以从展示框中取出物品
     */
    public boolean canRemoveItem(Player player, ItemFrame frame) {
        ItemStack item = frame.getItem();

        // 如果展示框为空，允许操作
        if (item == null || item.getType().isAir()) {
            return true;
        }

        // 检查是否为自定义物品
        if (!integrationManager.isCustomItem(item)) {
            return true;
        }

        // 检查取出权限
        if (!player.hasPermission("starcore.protection.itemframe.remove")) {
            logger.fine(String.format("玩家 %s 无权从展示框中取出自定义物品 at %s",
                    player.getName(), formatLocation(frame)));
            return false;
        }

        return true;
    }

    /**
     * 检查玩家是否可以放置物品到展示框
     */
    public boolean canPlaceItem(Player player, ItemFrame frame, ItemStack item) {
        // 如果不是自定义物品，允许放置
        if (item == null || !integrationManager.isCustomItem(item)) {
            return true;
        }

        // 检查放置权限
        if (!player.hasPermission("starcore.protection.itemframe.place")) {
            logger.fine(String.format("玩家 %s 无权将自定义物品放入展示框 at %s",
                    player.getName(), formatLocation(frame)));
            return false;
        }

        return true;
    }

    /**
     * 检查玩家是否可以旋转展示框中的物品
     */
    public boolean canRotateItem(Player player, ItemFrame frame) {
        ItemStack item = frame.getItem();

        // 如果展示框为空，允许操作
        if (item == null || item.getType().isAir()) {
            return true;
        }

        // 检查是否为自定义物品
        if (!integrationManager.isCustomItem(item)) {
            return true;
        }

        // 检查旋转权限
        if (!player.hasPermission("starcore.protection.itemframe.rotate")) {
            logger.fine(String.format("玩家 %s 无权旋转展示框中的自定义物品 at %s",
                    player.getName(), formatLocation(frame)));
            return false;
        }

        return true;
    }

    /**
     * 检查展示框是否包含自定义物品
     */
    public boolean hasCustomItem(ItemFrame frame) {
        ItemStack item = frame.getItem();
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return integrationManager.isCustomItem(item);
    }

    /**
     * 获取展示框中的自定义物品信息
     */
    public String getCustomItemInfo(ItemFrame frame) {
        ItemStack item = frame.getItem();
        if (item == null || item.getType().isAir()) {
            return null;
        }

        if (!integrationManager.isCustomItem(item)) {
            return null;
        }

        StringBuilder info = new StringBuilder();
        info.append("类型: ").append(item.getType().name());

        // 尝试获取各种集成的物品ID
        if (integrationManager.isIntegrationEnabled("Slimefun")) {
            // 粘液科技物品
            info.append(" | 来源: Slimefun");
        } else if (integrationManager.isIntegrationEnabled("MMOItems")) {
            // MMOItems 物品
            info.append(" | 来源: MMOItems");
        } else if (integrationManager.isIntegrationEnabled("ItemsAdder")) {
            // ItemsAdder 物品
            info.append(" | 来源: ItemsAdder");
        } else if (integrationManager.isIntegrationEnabled("Oraxen")) {
            // Oraxen 物品
            info.append(" | 来源: Oraxen");
        } else {
            info.append(" | 来源: 未知");
        }

        info.append(" | 位置: ").append(formatLocation(frame));

        return info.toString();
    }

    /**
     * 检查玩家是否可以与展示框交互
     */
    public boolean canInteract(Player player, ItemFrame frame) {
        ItemStack item = frame.getItem();

        // 如果展示框为空，允许交互
        if (item == null || item.getType().isAir()) {
            return true;
        }

        // 检查是否为自定义物品
        if (!integrationManager.isCustomItem(item)) {
            return true;
        }

        // 检查交互权限
        if (!player.hasPermission("starcore.protection.itemframe.interact")) {
            logger.fine(String.format("玩家 %s 无权与包含自定义物品的展示框交互 at %s",
                    player.getName(), formatLocation(frame)));
            return false;
        }

        return true;
    }

    // ==================== 工具方法 ====================

    /**
     * 格式化实体位置
     */
    private String formatLocation(Entity entity) {
        return String.format("%s(%.1f,%.1f,%.1f)",
                entity.getWorld().getName(),
                entity.getLocation().getX(),
                entity.getLocation().getY(),
                entity.getLocation().getZ());
    }

    /**
     * 发送保护消息
     */
    public void sendProtectionMessage(Player player, String action) {
        player.sendMessage("§c你没有权限" + action + "展示框中的自定义物品！");
    }

    /**
     * 发送保护消息（带物品类型）
     */
    public void sendProtectionMessage(Player player, String action, String itemType) {
        player.sendMessage(String.format("§c你没有权限%s展示框中的 %s 物品！", action, itemType));
    }
}
