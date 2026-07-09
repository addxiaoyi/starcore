package dev.starcore.starcore.protection.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * ItemsAdder 集成
 *
 * 为 ItemsAdder 提供自定义内容保护支持
 * - 自定义方块保护
 * - 自定义物品保护
 * - 自定义家具保护
 * - 自定义实体保护
 */
public class ItemsAdderIntegration implements CustomBlockProtection, CustomItemProtection {

    private final Plugin plugin;
    private final Logger logger;
    private boolean enabled = false;

    // ItemsAdder API 引用（通过反射加载）
    private Class<?> customBlockClass;
    private Class<?> customStackClass;
    private Class<?> customFurnitureClass;

    public ItemsAdderIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化集成
     */
    public boolean initialize() {
        try {
            // 检查 ItemsAdder 插件是否存在
            Plugin itemsAdderPlugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            if (itemsAdderPlugin == null || !itemsAdderPlugin.isEnabled()) {
                logger.fine("ItemsAdder 插件未找到或未启用");
                return false;
            }

            // 通过反射加载 ItemsAdder API
            customBlockClass = Class.forName("dev.lone.itemsadder.api.CustomBlock");
            customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            customFurnitureClass = Class.forName("dev.lone.itemsadder.api.CustomFurniture");

            enabled = true;
            logger.info("ItemsAdder 集成初始化成功");
            return true;

        } catch (ClassNotFoundException e) {
            logger.warning("ItemsAdder API 类未找到: " + e.getMessage());
            logger.warning("这可能是因为 ItemsAdder 版本不兼容");
            return false;
        } catch (Exception e) {
            logger.warning("ItemsAdder 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== 方块保护 ====================

    @Override
    public boolean canBreak(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查是否为 ItemsAdder 方块
            if (!isCustomBlock(block)) {
                return true;
            }

            String blockId = getCustomBlockId(block);
            if (blockId == null) {
                return true;
            }

            // 检查基本破坏权限
            if (!player.hasPermission("starcore.protection.itemsadder.break")) {
                logger.fine(String.format("玩家 %s 无权破坏 ItemsAdder 方块 %s", player.getName(), blockId));
                return false;
            }

            // 检查特定方块权限
            String permission = "starcore.protection.itemsadder.break." + blockId.toLowerCase().replace(":", ".");
            if (!player.hasPermission(permission)) {
                logger.fine(String.format("玩家 %s 无权破坏 ItemsAdder 方块 %s (缺少权限 %s)",
                        player.getName(), blockId, permission));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 ItemsAdder 方块破坏权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canPlace(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查放置权限
            if (!player.hasPermission("starcore.protection.itemsadder.place")) {
                logger.fine(String.format("玩家 %s 无权放置 ItemsAdder 方块", player.getName()));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 ItemsAdder 方块放置权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canInteract(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查是否为 ItemsAdder 方块或家具
            if (!isCustomBlock(block) && !isCustomFurniture(block)) {
                return true;
            }

            String blockId = getCustomBlockId(block);
            if (blockId == null) {
                blockId = getCustomFurnitureId(block);
            }

            if (blockId == null) {
                return true;
            }

            // 检查基本交互权限
            if (!player.hasPermission("starcore.protection.itemsadder.interact")) {
                logger.fine(String.format("玩家 %s 无权与 ItemsAdder 方块/家具 %s 交互", player.getName(), blockId));
                return false;
            }

            // 检查特定方块权限
            String permission = "starcore.protection.itemsadder.interact." + blockId.toLowerCase().replace(":", ".");
            if (!player.hasPermission(permission)) {
                logger.fine(String.format("玩家 %s 无权与 ItemsAdder 方块/家具 %s 交互 (缺少权限 %s)",
                        player.getName(), blockId, permission));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 ItemsAdder 方块交互权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isCustomBlock(Block block) {
        if (!enabled) return false;

        try {
            // CustomBlock.byAlreadyPlaced(block) != null
            Object customBlock = customBlockClass.getMethod("byAlreadyPlaced", Block.class).invoke(null, block);
            return customBlock != null;
        } catch (Exception e) {
            logger.fine("检查 ItemsAdder 方块时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomBlockId(Block block) {
        if (!enabled) return null;

        try {
            // CustomBlock.byAlreadyPlaced(block).getNamespacedID()
            Object customBlock = customBlockClass.getMethod("byAlreadyPlaced", Block.class).invoke(null, block);
            if (customBlock != null) {
                Object id = customBlockClass.getMethod("getNamespacedID").invoke(customBlock);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            logger.fine("获取 ItemsAdder 方块ID时出错: " + e.getMessage());
        }

        return null;
    }

    // ==================== 物品保护 ====================

    @Override
    public boolean canUse(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 ItemsAdder 物品
            if (!isCustomItem(item)) {
                return true;
            }

            String itemId = getCustomItemId(item);
            if (itemId == null) {
                return true;
            }

            // 检查使用权限
            if (!player.hasPermission("starcore.protection.itemsadder.use")) {
                logger.fine(String.format("玩家 %s 无权使用 ItemsAdder 物品 %s", player.getName(), itemId));
                return false;
            }

            // 检查特定物品权限
            String permission = "starcore.protection.itemsadder.use." + itemId.toLowerCase().replace(":", ".");
            if (!player.hasPermission(permission)) {
                logger.fine(String.format("玩家 %s 无权使用 ItemsAdder 物品 %s (缺少权限 %s)",
                        player.getName(), itemId, permission));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 ItemsAdder 物品使用权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canPickup(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 ItemsAdder 物品
            if (!isCustomItem(item)) {
                return true;
            }

            // 检查拾取权限
            if (!player.hasPermission("starcore.protection.itemsadder.pickup")) {
                String itemId = getCustomItemId(item);
                logger.fine(String.format("玩家 %s 无权拾取 ItemsAdder 物品 %s", player.getName(), itemId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 ItemsAdder 物品拾取权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canDrop(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        // ItemsAdder 物品丢弃通常不需要限制
        return true;
    }

    @Override
    public boolean canCraft(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 ItemsAdder 物品
            if (!isCustomItem(item)) {
                return true;
            }

            String itemId = getCustomItemId(item);
            if (itemId == null) {
                return true;
            }

            // 检查制作权限
            if (!player.hasPermission("starcore.protection.itemsadder.craft")) {
                logger.fine(String.format("玩家 %s 无权制作 ItemsAdder 物品 %s", player.getName(), itemId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 ItemsAdder 物品制作权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) return false;

        try {
            // CustomStack.byItemStack(item) != null
            Object customStack = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            return customStack != null;
        } catch (Exception e) {
            logger.fine("检查 ItemsAdder 物品时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!enabled || item == null) return null;

        try {
            // CustomStack.byItemStack(item).getNamespacedID()
            Object customStack = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            if (customStack != null) {
                Object id = customStackClass.getMethod("getNamespacedID").invoke(customStack);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            logger.fine("获取 ItemsAdder 物品ID时出错: " + e.getMessage());
        }

        return null;
    }

    // ==================== 家具保护 ====================

    /**
     * 检查方块是否为自定义家具
     */
    private boolean isCustomFurniture(Block block) {
        if (!enabled) return false;

        try {
            // CustomFurniture.byAlreadySpawned(block) != null
            Object furniture = customFurnitureClass.getMethod("byAlreadySpawned", Block.class).invoke(null, block);
            return furniture != null;
        } catch (Exception e) {
            logger.fine("检查 ItemsAdder 家具时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取自定义家具ID
     */
    private String getCustomFurnitureId(Block block) {
        if (!enabled) return null;

        try {
            // CustomFurniture.byAlreadySpawned(block).getNamespacedID()
            Object furniture = customFurnitureClass.getMethod("byAlreadySpawned", Block.class).invoke(null, block);
            if (furniture != null) {
                Object id = customFurnitureClass.getMethod("getNamespacedID").invoke(furniture);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            logger.fine("获取 ItemsAdder 家具ID时出错: " + e.getMessage());
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
