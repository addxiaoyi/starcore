package dev.starcore.starcore.protection.integration;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * 插件集成管理器
 *
 * 负责检测和管理第三方插件兼容性，提供统一的保护接口
 */
public class PluginIntegrationManager {

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, CustomBlockProtection> blockProtections;
    private final Map<String, CustomItemProtection> itemProtections;
    private final Map<String, Boolean> integrationStatus;
    private final boolean enabled;

    public PluginIntegrationManager(Plugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = enabled;
        this.blockProtections = new HashMap<>();
        this.itemProtections = new HashMap<>();
        this.integrationStatus = new HashMap<>();
    }

    /**
     * 初始化所有集成
     */
    public void initialize() {
        if (!enabled) {
            logger.info("第三方插件保护系统已禁用");
            return;
        }

        logger.info("========================================");
        logger.info("  初始化第三方插件保护集成...");
        logger.info("========================================");

        // 检测并加载粘液科技
        if (isPluginEnabled("Slimefun")) {
            try {
                SlimefunIntegration integration = new SlimefunIntegration(plugin);
                if (integration.initialize()) {
                    blockProtections.put("Slimefun", integration);
                    itemProtections.put("Slimefun", integration);
                    integrationStatus.put("Slimefun", true);
                    logger.info("  [√] Slimefun 集成已启用");
                } else {
                    integrationStatus.put("Slimefun", false);
                    logger.warning("  [×] Slimefun 集成初始化失败");
                }
            } catch (Exception e) {
                integrationStatus.put("Slimefun", false);
                logger.warning("  [×] Slimefun 集成加载失败: " + e.getMessage());
            }
        }

        // 检测并加载 MMOItems
        if (isPluginEnabled("MMOItems")) {
            try {
                MMOItemsIntegration integration = new MMOItemsIntegration(plugin);
                if (integration.initialize()) {
                    itemProtections.put("MMOItems", integration);
                    integrationStatus.put("MMOItems", true);
                    logger.info("  [√] MMOItems 集成已启用");
                } else {
                    integrationStatus.put("MMOItems", false);
                    logger.warning("  [×] MMOItems 集成初始化失败");
                }
            } catch (Exception e) {
                integrationStatus.put("MMOItems", false);
                logger.warning("  [×] MMOItems 集成加载失败: " + e.getMessage());
            }
        }

        // 检测并加载 ItemsAdder
        if (isPluginEnabled("ItemsAdder")) {
            try {
                ItemsAdderIntegration integration = new ItemsAdderIntegration(plugin);
                if (integration.initialize()) {
                    blockProtections.put("ItemsAdder", integration);
                    itemProtections.put("ItemsAdder", integration);
                    integrationStatus.put("ItemsAdder", true);
                    logger.info("  [√] ItemsAdder 集成已启用");
                } else {
                    integrationStatus.put("ItemsAdder", false);
                    logger.warning("  [×] ItemsAdder 集成初始化失败");
                }
            } catch (Exception e) {
                integrationStatus.put("ItemsAdder", false);
                logger.warning("  [×] ItemsAdder 集成加载失败: " + e.getMessage());
            }
        }

        // 检测并加载 Oraxen
        if (isPluginEnabled("Oraxen")) {
            try {
                OraxenIntegration integration = new OraxenIntegration(plugin);
                if (integration.initialize()) {
                    blockProtections.put("Oraxen", integration);
                    itemProtections.put("Oraxen", integration);
                    integrationStatus.put("Oraxen", true);
                    logger.info("  [√] Oraxen 集成已启用");
                } else {
                    integrationStatus.put("Oraxen", false);
                    logger.warning("  [×] Oraxen 集成初始化失败");
                }
            } catch (Exception e) {
                integrationStatus.put("Oraxen", false);
                logger.warning("  [×] Oraxen 集成加载失败: " + e.getMessage());
            }
        }

        // 打印集成报告
        printIntegrationReport();
    }

    /**
     * 检查插件是否已启用
     */
    private boolean isPluginEnabled(String pluginName) {
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return targetPlugin != null && targetPlugin.isEnabled();
    }

    /**
     * 打印集成报告
     */
    private void printIntegrationReport() {
        logger.info("========================================");
        logger.info("  第三方插件保护集成报告");
        logger.info("========================================");

        int enabled = 0;
        int total = integrationStatus.size();

        if (total == 0) {
            logger.info("  未检测到支持的第三方插件");
        } else {
            for (Map.Entry<String, Boolean> entry : integrationStatus.entrySet()) {
                String status = entry.getValue() ? "[√] 已启用" : "[×] 未启用";
                logger.info(String.format("  %s: %s", entry.getKey(), status));
                if (entry.getValue()) enabled++;
            }
        }

        logger.info("========================================");
        logger.info(String.format("  总计: %d/%d 已启用", enabled, total));
        logger.info("========================================");
    }

    // ==================== 方块保护查询 ====================

    /**
     * 检查玩家是否可以破坏方块
     */
    public boolean canBreakBlock(Player player, Block block) {
        if (!enabled) return true;

        for (CustomBlockProtection protection : blockProtections.values()) {
            if (protection.isCustomBlock(block)) {
                boolean result = protection.canBreak(player, block);
                if (!result) {
                    logger.fine(String.format("玩家 %s 无权破坏自定义方块 %s at %s",
                            player.getName(),
                            protection.getCustomBlockId(block),
                            formatLocation(block)));
                }
                return result;
            }
        }
        return true;
    }

    /**
     * 检查玩家是否可以放置方块
     */
    public boolean canPlaceBlock(Player player, Block block) {
        if (!enabled) return true;

        for (CustomBlockProtection protection : blockProtections.values()) {
            if (protection.isCustomBlock(block)) {
                boolean result = protection.canPlace(player, block);
                if (!result) {
                    logger.fine(String.format("玩家 %s 无权放置自定义方块 at %s",
                            player.getName(),
                            formatLocation(block)));
                }
                return result;
            }
        }
        return true;
    }

    /**
     * 检查玩家是否可以与方块交互
     */
    public boolean canInteractBlock(Player player, Block block) {
        if (!enabled) return true;

        for (CustomBlockProtection protection : blockProtections.values()) {
            if (protection.isCustomBlock(block)) {
                boolean result = protection.canInteract(player, block);
                if (!result) {
                    logger.fine(String.format("玩家 %s 无权与自定义方块 %s 交互",
                            player.getName(),
                            protection.getCustomBlockId(block)));
                }
                return result;
            }
        }
        return true;
    }

    /**
     * 检查方块是否为自定义方块
     */
    public boolean isCustomBlock(Block block) {
        if (!enabled) return false;

        for (CustomBlockProtection protection : blockProtections.values()) {
            if (protection.isCustomBlock(block)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 物品保护查询 ====================

    /**
     * 检查玩家是否可以使用物品
     */
    public boolean canUseItem(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        for (CustomItemProtection protection : itemProtections.values()) {
            if (protection.isCustomItem(item)) {
                boolean result = protection.canUse(player, item);
                if (!result) {
                    logger.fine(String.format("玩家 %s 无权使用自定义物品 %s",
                            player.getName(),
                            protection.getCustomItemId(item)));
                }
                return result;
            }
        }
        return true;
    }

    /**
     * 检查玩家是否可以拾取物品
     */
    public boolean canPickupItem(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        for (CustomItemProtection protection : itemProtections.values()) {
            if (protection.isCustomItem(item)) {
                boolean result = protection.canPickup(player, item);
                if (!result) {
                    logger.fine(String.format("玩家 %s 无权拾取自定义物品 %s",
                            player.getName(),
                            protection.getCustomItemId(item)));
                }
                return result;
            }
        }
        return true;
    }

    /**
     * 检查玩家是否可以丢弃物品
     */
    public boolean canDropItem(Player player, ItemStack item) {
        if (!enabled || item == null) return true;

        for (CustomItemProtection protection : itemProtections.values()) {
            if (protection.isCustomItem(item)) {
                boolean result = protection.canDrop(player, item);
                if (!result) {
                    logger.fine(String.format("玩家 %s 无权丢弃自定义物品 %s",
                            player.getName(),
                            protection.getCustomItemId(item)));
                }
                return result;
            }
        }
        return true;
    }

    /**
     * 检查物品是否为自定义物品
     */
    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) return false;

        for (CustomItemProtection protection : itemProtections.values()) {
            if (protection.isCustomItem(item)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 状态查询 ====================

    /**
     * 检查集成是否启用
     */
    public boolean isIntegrationEnabled(String pluginName) {
        return integrationStatus.getOrDefault(pluginName, false);
    }

    /**
     * 获取所有集成状态
     */
    public Map<String, Boolean> getIntegrationStatus() {
        return new HashMap<>(integrationStatus);
    }

    /**
     * 获取已启用的集成数量
     */
    public int getEnabledIntegrationCount() {
        return (int) integrationStatus.values().stream().filter(b -> b).count();
    }

    /**
     * 检查保护系统是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 工具方法 ====================

    /**
     * 格式化方块位置
     */
    private String formatLocation(Block block) {
        return String.format("%s(%d,%d,%d)",
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ());
    }
}
