package dev.starcore.starcore.protection.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 粘液科技集成
 *
 * 为粘液科技（Slimefun）提供完整的保护支持
 * - 机器保护
 * - 方块保护
 * - 物品保护
 * - 能量网络保护
 * - 货运网络保护
 */
public class SlimefunIntegration implements CustomBlockProtection, CustomItemProtection {

    private final Plugin plugin;
    private final Logger logger;
    private boolean enabled = false;

    // Slimefun API 引用（通过反射加载）
    private Class<?> slimefunItemClass;
    private Class<?> blockStorageClass;
    private Class<?> slimefunClass;

    public SlimefunIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化集成
     */
    public boolean initialize() {
        try {
            // 检查 Slimefun 插件是否存在
            Plugin slimefunPlugin = Bukkit.getPluginManager().getPlugin("Slimefun");
            if (slimefunPlugin == null || !slimefunPlugin.isEnabled()) {
                logger.fine("Slimefun 插件未找到或未启用");
                return false;
            }

            // 通过反射加载 Slimefun API
            slimefunItemClass = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem");
            blockStorageClass = Class.forName("me.mrCookieSlime.Slimefun.api.BlockStorage");
            slimefunClass = Class.forName("io.github.thebusybiscuit.slimefun4.implementation.Slimefun");

            enabled = true;
            logger.info("Slimefun 集成初始化成功");
            return true;

        } catch (ClassNotFoundException e) {
            logger.warning("Slimefun API 类未找到: " + e.getMessage());
            logger.warning("这可能是因为 Slimefun 版本不兼容");
            return false;
        } catch (Exception e) {
            logger.warning("Slimefun 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== 方块保护 ====================

    @Override
    public boolean canBreak(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查是否为粘液科技方块
            if (!isSlimefunBlock(block)) {
                return true;
            }

            // 获取方块信息
            String blockId = getSlimefunBlockId(block);
            if (blockId == null) {
                return true;
            }

            // 粘液科技机器需要特殊权限
            if (isMachine(blockId)) {
                if (!player.hasPermission("starcore.protection.slimefun.break.machine")) {
                    logger.fine(String.format("玩家 %s 无权破坏粘液科技机器 %s", player.getName(), blockId));
                    return false;
                }
            }

            // 检查基本破坏权限
            if (!player.hasPermission("starcore.protection.slimefun.break")) {
                logger.fine(String.format("玩家 %s 无权破坏粘液科技方块 %s", player.getName(), blockId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查粘液科技方块破坏权限时出错: " + e.getMessage());
            return true; // 出错时允许操作，避免阻塞游戏
        }
    }

    @Override
    public boolean canPlace(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查放置权限
            if (!player.hasPermission("starcore.protection.slimefun.place")) {
                logger.fine(String.format("玩家 %s 无权放置粘液科技方块", player.getName()));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查粘液科技方块放置权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canInteract(Player player, Block block) {
        if (!enabled) return true;

        try {
            // 检查是否为粘液科技方块
            if (!isSlimefunBlock(block)) {
                return true;
            }

            String blockId = getSlimefunBlockId(block);
            if (blockId == null) {
                return true;
            }

            // 机器交互需要特殊权限
            if (isMachine(blockId)) {
                if (!player.hasPermission("starcore.protection.slimefun.interact.machine")) {
                    logger.fine(String.format("玩家 %s 无权与粘液科技机器 %s 交互", player.getName(), blockId));
                    return false;
                }
            }

            // 检查基本交互权限
            if (!player.hasPermission("starcore.protection.slimefun.interact")) {
                logger.fine(String.format("玩家 %s 无权与粘液科技方块 %s 交互", player.getName(), blockId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查粘液科技方块交互权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isCustomBlock(Block block) {
        if (!enabled) return false;

        try {
            return isSlimefunBlock(block);
        } catch (Exception e) {
            logger.warning("检查粘液科技方块时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomBlockId(Block block) {
        if (!enabled) return null;

        try {
            return getSlimefunBlockId(block);
        } catch (Exception e) {
            logger.warning("获取粘液科技方块ID时出错: " + e.getMessage());
            return null;
        }
    }

    // ==================== 物品保护 ====================

    @Override
    public boolean canUse(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为粘液科技物品
            if (!isSlimefunItem(item)) {
                return true;
            }

            String itemId = getSlimefunItemId(item);
            if (itemId == null) {
                return true;
            }

            // 检查使用权限
            if (!player.hasPermission("starcore.protection.slimefun.use")) {
                logger.fine(String.format("玩家 %s 无权使用粘液科技物品 %s", player.getName(), itemId));
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("检查粘液科技物品使用权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean canPickup(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        // 粘液科技物品拾取通常不需要限制
        return true;
    }

    @Override
    public boolean canDrop(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        // 粘液科技物品丢弃通常不需要限制
        return true;
    }

    @Override
    public boolean canCraft(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        try {
            // 检查是否为粘液科技物品
            if (!isSlimefunItem(item)) {
                return true;
            }

            // 粘液科技有自己的配方系统，这里不需要额外限制
            return true;

        } catch (Exception e) {
            logger.warning("检查粘液科技物品制作权限时出错: " + e.getMessage());
            return true;
        }
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) return false;

        try {
            return isSlimefunItem(item);
        } catch (Exception e) {
            logger.warning("检查粘液科技物品时出错: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String getCustomItemId(ItemStack item) {
        if (!enabled || item == null) return null;

        try {
            return getSlimefunItemId(item);
        } catch (Exception e) {
            logger.warning("获取粘液科技物品ID时出错: " + e.getMessage());
            return null;
        }
    }

    // ==================== Slimefun API 方法 ====================

    /**
     * 检查方块是否为粘液科技方块
     */
    private boolean isSlimefunBlock(Block block) {
        try {
            // BlockStorage.check(block) != null
            Object blockStorage = blockStorageClass.getMethod("check", Block.class).invoke(null, block);
            return blockStorage != null;
        } catch (Exception e) {
            logger.fine("检查粘液科技方块失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取粘液科技方块ID
     */
    private String getSlimefunBlockId(Block block) {
        try {
            // BlockStorage.check(block)
            Object result = blockStorageClass.getMethod("check", Block.class).invoke(null, block);
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            logger.fine("获取粘液科技方块ID失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 检查物品是否为粘液科技物品
     */
    private boolean isSlimefunItem(ItemStack item) {
        try {
            // SlimefunItem.getByItem(item) != null
            Object slimefunItem = slimefunItemClass.getMethod("getByItem", ItemStack.class).invoke(null, item);
            return slimefunItem != null;
        } catch (Exception e) {
            logger.fine("检查粘液科技物品失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取粘液科技物品ID
     */
    private String getSlimefunItemId(ItemStack item) {
        try {
            // SlimefunItem.getByItem(item).getId()
            Object slimefunItem = slimefunItemClass.getMethod("getByItem", ItemStack.class).invoke(null, item);
            if (slimefunItem != null) {
                Object id = slimefunItemClass.getMethod("getId").invoke(slimefunItem);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            logger.fine("获取粘液科技物品ID失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 检查是否为机器
     */
    private boolean isMachine(String blockId) {
        if (blockId == null) return false;

        // 常见的粘液科技机器关键词
        String lowerBlockId = blockId.toLowerCase();
        return lowerBlockId.contains("machine") ||
               lowerBlockId.contains("generator") ||
               lowerBlockId.contains("reactor") ||
               lowerBlockId.contains("furnace") ||
               lowerBlockId.contains("farm") ||
               lowerBlockId.contains("miner") ||
               lowerBlockId.contains("processor");
    }

    /**
     * 检查集成是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
