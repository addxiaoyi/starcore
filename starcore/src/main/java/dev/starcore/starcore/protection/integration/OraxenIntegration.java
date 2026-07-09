package dev.starcore.starcore.protection.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Oraxen 集成
 *
 * 为 Oraxen 提供自定义内容保护支持
 * - 自定义家具保护
 * - 自定义方块保护
 * - 自定义物品保护
 * - 自定义武器/工具保护
 */
public class OraxenIntegration implements CustomBlockProtection, CustomItemProtection {

    private final Plugin plugin;
    private final Logger logger;
    private boolean enabled = false;

    // Oraxen API 引用（通过反射加载）
    private Class<?> oraxenBlocksClass;
    private Class<?> oraxenItemsClass;
    private Class<?> oraxenFurnitureClass;

    public OraxenIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化集成
     */
    public boolean initialize() {
        try {
            // 检查 Oraxen 插件是否存在
            Plugin oraxenPlugin = Bukkit.getPluginManager().getPlugin("Oraxen");
            if (oraxenPlugin == null || !oraxenPlugin.isEnabled()) {
                logger.fine("Oraxen 插件未找到或未启用");
                return false;
            }

            // 通过反射加载 Oraxen API
            oraxenBlocksClass = Class.forName("io.th0rgal.oraxen.api.OraxenBlocks");
            oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            oraxenFurnitureClass = Class.forName("io.th0rgal.oraxen.api.OraxenFurniture");

            enabled = true;
            logger.info("Oraxen 集成初始化成功");
            return true;

        } catch (ClassNotFoundException e) {
            logger.warning("Oraxen API 类未找到: " + e.getMessage());
            logger.warning("这可能是因为 Oraxen 版本不兼容");
            return false;
        } catch (Exception e) {
            logger.warning("Oraxen 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== 方块保护 ====================

    @Override
    public boolean canBreak(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查是否为 Oraxen 方块或家具
            if (!isOraxenBlock(block) && !isOraxenFurniture(block)) {
                return true;
            }

            String blockId = getOraxenBlockId(block);
            if (blockId == null) {
                blockId = getOraxenFurnitureId(block);
            }

            if (blockId == null) {
                return true;
            }

            // 检查是否为家具
            if (isOraxenFurniture(block)) {
                if (!player.hasPermission("starcore.protection.oraxen.break.furniture")) {
                    logger.fine(String.format("玩家 %s 无权破坏 Oraxen 家具 %s", player.getName(), blockId));
                    return false;
                }
            }

            // 检查基本破坏权限
            if (!player.hasPermission("starcore.protection.oraxen.break")) {
                logger.fine(String.format("玩家 %s 无权破坏 Oraxen 方块 %s", player.getName(), blockId));
                return false;
            }

            // 检查特定方块权限
            String permission = "starcore.protection.oraxen.break." + blockId.toLowerCase();
            if (!player.hasPermission(permission)) {
                logger.fine(String.format("玩家 %s 无权破坏 Oraxen 方块 %s (缺少权限 %s)",
                        player.getName(), blockId, permission));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 Oraxen 方块破坏权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canPlace(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查放置权限
            if (!player.hasPermission("starcore.protection.oraxen.place")) {
                logger.fine(String.format("玩家 %s 无权放置 Oraxen 方块", player.getName()));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 Oraxen 方块放置权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canInteract(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查是否为 Oraxen 方块或家具
            if (!isOraxenBlock(block) && !isOraxenFurniture(block)) {
                return true;
            }

            String blockId = getOraxenBlockId(block);
            if (blockId == null) {
                blockId = getOraxenFurnitureId(block);
            }

            if (blockId == null) {
                return true;
            }

            // 家具交互需要特殊权限
            if (isOraxenFurniture(block)) {
                if (!player.hasPermission("starcore.protection.oraxen.interact.furniture")) {
                    logger.fine(String.format("玩家 %s 无权与 Oraxen 家具 %s 交互", player.getName(), blockId));
                    return false;
                }
            }

            // 检查基本交互权限
            if (!player.hasPermission("starcore.protection.oraxen.interact")) {
                logger.fine(String.format("玩家 %s 无权与 Oraxen 方块 %s 交互", player.getName(), blockId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 Oraxen 方块交互权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isCustomBlock(Block block) {
        if (!enabled) return false;

        try {
            return isOraxenBlock(block) || isOraxenFurniture(block);
        } catch (Exception e) {
            logger.fine("检查 Oraxen 方块时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomBlockId(Block block) {
        if (!enabled) return null;

        try {
            String blockId = getOraxenBlockId(block);
            if (blockId != null) {
                return blockId;
            }
            return getOraxenFurnitureId(block);
        } catch (Exception e) {
            logger.fine("获取 Oraxen 方块ID时出错: " + e.getMessage());
            return null;
        }
    }

    // ==================== 物品保护 ====================

    @Override
    public boolean canUse(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 Oraxen 物品
            if (!isCustomItem(item)) {
                return true;
            }

            String itemId = getCustomItemId(item);
            if (itemId == null) {
                return true;
            }

            // 检查使用权限
            if (!player.hasPermission("starcore.protection.oraxen.use")) {
                logger.fine(String.format("玩家 %s 无权使用 Oraxen 物品 %s", player.getName(), itemId));
                return false;
            }

            // 检查特定物品权限
            String permission = "starcore.protection.oraxen.use." + itemId.toLowerCase();
            if (!player.hasPermission(permission)) {
                logger.fine(String.format("玩家 %s 无权使用 Oraxen 物品 %s (缺少权限 %s)",
                        player.getName(), itemId, permission));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 Oraxen 物品使用权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canPickup(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 Oraxen 物品
            if (!isCustomItem(item)) {
                return true;
            }

            // 检查拾取权限
            if (!player.hasPermission("starcore.protection.oraxen.pickup")) {
                String itemId = getCustomItemId(item);
                logger.fine(String.format("玩家 %s 无权拾取 Oraxen 物品 %s", player.getName(), itemId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 Oraxen 物品拾取权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canDrop(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        // Oraxen 物品丢弃通常不需要限制
        return true;
    }

    @Override
    public boolean canCraft(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为 Oraxen 物品
            if (!isCustomItem(item)) {
                return true;
            }

            String itemId = getCustomItemId(item);
            if (itemId == null) {
                return true;
            }

            // 检查制作权限
            if (!player.hasPermission("starcore.protection.oraxen.craft")) {
                logger.fine(String.format("玩家 %s 无权制作 Oraxen 物品 %s", player.getName(), itemId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查 Oraxen 物品制作权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) return false;

        try {
            // OraxenItems.getIdByItem(item) != null
            Object id = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
            return id != null;
        } catch (Exception e) {
            logger.fine("检查 Oraxen 物品时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!enabled || item == null) return null;

        try {
            // OraxenItems.getIdByItem(item)
            Object id = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
            if (id != null) {
                return id.toString();
            }
        } catch (Exception e) {
            logger.fine("获取 Oraxen 物品ID时出错: " + e.getMessage());
        }

        return null;
    }

    // ==================== Oraxen API 方法 ====================

    /**
     * 检查方块是否为 Oraxen 方块
     */
    private boolean isOraxenBlock(Block block) {
        try {
            // OraxenBlocks.isOraxenBlock(block)
            Object result = oraxenBlocksClass.getMethod("isOraxenBlock", Block.class).invoke(null, block);
            return result != null && (Boolean) result;
        } catch (Exception e) {
            logger.fine("检查 Oraxen 方块失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取 Oraxen 方块ID
     */
    private String getOraxenBlockId(Block block) {
        try {
            // OraxenBlocks.getOraxenBlock(block).getMechanicID()
            Object oraxenBlock = oraxenBlocksClass.getMethod("getOraxenBlock", Block.class).invoke(null, block);
            if (oraxenBlock != null) {
                Object id = oraxenBlock.getClass().getMethod("getMechanicID").invoke(oraxenBlock);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            logger.fine("获取 Oraxen 方块ID失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 检查方块是否为 Oraxen 家具
     */
    private boolean isOraxenFurniture(Block block) {
        try {
            // OraxenFurniture.isFurniture(block)
            Object result = oraxenFurnitureClass.getMethod("isFurniture", Block.class).invoke(null, block);
            return result != null && (Boolean) result;
        } catch (Exception e) {
            logger.fine("检查 Oraxen 家具失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取 Oraxen 家具ID
     */
    private String getOraxenFurnitureId(Block block) {
        try {
            // OraxenFurniture.getFurniture(block).getMechanicID()
            Object furniture = oraxenFurnitureClass.getMethod("getFurniture", Block.class).invoke(null, block);
            if (furniture != null) {
                Object id = furniture.getClass().getMethod("getMechanicID").invoke(furniture);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            logger.fine("获取 Oraxen 家具ID失败: " + e.getMessage());
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
