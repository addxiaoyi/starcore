package dev.starcore.starcore.protection.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * MMOItems 集成
 *
 * 为 MMOItems 提供自定义物品保护支持
 * - 自定义物品识别
 * - 物品使用保护
 * - 物品技能保护
 * - 物品能力保护
 */
public class MMOItemsIntegration implements CustomItemProtection {

    private final Plugin plugin;
    private final Logger logger;
    private boolean enabled = false;

    // MMOItems API 引用（通过反射加载）
    private Class<?> mmoItemsClass;
    private Class<?> nbtItemClass;

    public MMOItemsIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化集成
     */
    public boolean initialize() {
        try {
            // 检查 MMOItems 插件是否存在
            Plugin mmoItemsPlugin = Bukkit.getPluginManager().getPlugin("MMOItems");
            if (mmoItemsPlugin == null || !mmoItemsPlugin.isEnabled()) {
                logger.fine("MMOItems 插件未找到或未启用");
                return false;
            }

            // 通过反射加载 MMOItems API
            mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            nbtItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");

            enabled = true;
            logger.info("MMOItems 集成初始化成功");
            return true;

        } catch (ClassNotFoundException e) {
            logger.warning("MMOItems API 类未找到: " + e.getMessage());
            logger.warning("这可能是因为 MMOItems 版本不兼容");
            return false;
        } catch (Exception e) {
            logger.warning("MMOItems 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== 物品保护 ====================

    @Override
    public boolean canUse(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 MMOItems 物品
            if (!isMMOItem(item)) {
                return true;
            }

            String itemId = getMMOItemId(item);
            if (itemId == null) {
                return true;
            }

            // 检查使用权限
            if (!player.hasPermission("starcore.protection.mmoitems.use")) {
                logger.fine(String.format("玩家 %s 无权使用 MMOItems 物品 %s", player.getName(), itemId));
                return false;
            }

            // 检查特定物品权限
            String permission = "starcore.protection.mmoitems.use." + itemId.toLowerCase();
            if (!player.hasPermission(permission)) {
                logger.fine(String.format("玩家 %s 无权使用 MMOItems 物品 %s (缺少权限 %s)",
                        player.getName(), itemId, permission));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 MMOItems 物品使用权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canPickup(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 MMOItems 物品
            if (!isMMOItem(item)) {
                return true;
            }

            // 检查拾取权限
            if (!player.hasPermission("starcore.protection.mmoitems.pickup")) {
                String itemId = getMMOItemId(item);
                logger.fine(String.format("玩家 %s 无权拾取 MMOItems 物品 %s", player.getName(), itemId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 MMOItems 物品拾取权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canDrop(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 MMOItems 物品
            if (!isMMOItem(item)) {
                return true;
            }

            // 检查丢弃权限
            if (!player.hasPermission("starcore.protection.mmoitems.drop")) {
                String itemId = getMMOItemId(item);
                logger.fine(String.format("玩家 %s 无权丢弃 MMOItems 物品 %s", player.getName(), itemId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 MMOItems 物品丢弃权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canCraft(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 MMOItems 物品
            if (!isMMOItem(item)) {
                return true;
            }

            String itemId = getMMOItemId(item);
            if (itemId == null) {
                return true;
            }

            // 检查制作权限
            if (!player.hasPermission("starcore.protection.mmoitems.craft")) {
                logger.fine(String.format("玩家 %s 无权制作 MMOItems 物品 %s", player.getName(), itemId));
                return false;
            }

            // 检查特定物品制作权限
            String permission = "starcore.protection.mmoitems.craft." + itemId.toLowerCase();
            if (!player.hasPermission(permission)) {
                logger.fine(String.format("玩家 %s 无权制作 MMOItems 物品 %s (缺少权限 %s)",
                        player.getName(), itemId, permission));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 MMOItems 物品制作权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) return false;

        try {
            return isMMOItem(item);
        } catch (Exception e) {
            logger.warning("检查 MMOItems 物品时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!enabled || item == null) return null;

        try {
            return getMMOItemId(item);
        } catch (Exception e) {
            logger.warning("获取 MMOItems 物品ID时出错: " + e.getMessage());
            return null;
        }
    }

    // ==================== MMOItems API 方法 ====================

    /**
     * 检查物品是否为 MMOItems 物品
     */
    private boolean isMMOItem(ItemStack item) {
        try {
            // 检查物品是否有 MMOItems NBT 标签
            if (!item.hasItemMeta()) {
                return false;
            }

            // 使用 NBTItem 检查
            Class<?> nbtItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
            Object nbtItem = nbtItemClass.getMethod("getItem", ItemStack.class).invoke(null, item);

            return nbtItem != null;

        } catch (Exception e) {
            logger.fine("检查 MMOItems 物品失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取 MMOItems 物品ID
     */
    private String getMMOItemId(ItemStack item) {
        try {
            // 获取 MMOItem 实例
            Class<?> nbtItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
            Object mmoItem = nbtItemClass.getMethod("getItem", ItemStack.class).invoke(null, item);

            if (mmoItem != null) {
                // 获取 Type 和 ID
                Object type = nbtItemClass.getMethod("getType").invoke(mmoItem);
                Object id = nbtItemClass.getMethod("getId").invoke(mmoItem);

                if (type != null && id != null) {
                    return type.toString() + ":" + id.toString();
                }
            }

        } catch (Exception e) {
            logger.fine("获取 MMOItems 物品ID失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 检查物品类型
     */
    private String getMMOItemType(ItemStack item) {
        try {
            Class<?> nbtItemClass = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
            Object mmoItem = nbtItemClass.getMethod("getItem", ItemStack.class).invoke(null, item);

            if (mmoItem != null) {
                Object type = nbtItemClass.getMethod("getType").invoke(mmoItem);
                if (type != null) {
                    return type.toString();
                }
            }

        } catch (Exception e) {
            logger.fine("获取 MMOItems 物品类型失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 检查集成是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
