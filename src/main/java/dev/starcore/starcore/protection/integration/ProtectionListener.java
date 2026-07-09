package dev.starcore.starcore.protection.integration;

import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 统一保护监听器
 *
 * 监听所有与自定义方块和物品相关的事件，
 * 并通过 PluginIntegrationManager 进行权限检查
 */
public class ProtectionListener implements Listener {

    private final Plugin plugin;
    private final Logger logger;
    private final PluginIntegrationManager integrationManager;

    public ProtectionListener(Plugin plugin, PluginIntegrationManager integrationManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.integrationManager = integrationManager;
    }

    // ==================== 方块破坏 ====================

    /**
     * 监听方块破坏事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // 检查是否为自定义方块
        if (!integrationManager.isCustomBlock(block)) {
            return;
        }

        // 检查破坏权限
        if (!integrationManager.canBreakBlock(player, block)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限破坏这个自定义方块！");
            logger.fine(String.format("阻止玩家 %s 破坏自定义方块 at %s",
                    player.getName(), formatLocation(block)));
        }
    }

    // ==================== 方块放置 ====================

    /**
     * 监听方块放置事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack item = event.getItemInHand();

        // 检查是否为自定义物品
        if (!integrationManager.isCustomItem(item)) {
            return;
        }

        // 检查放置权限
        if (!integrationManager.canPlaceBlock(player, block)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限放置这个自定义方块！");
            logger.fine(String.format("阻止玩家 %s 放置自定义方块 at %s",
                    player.getName(), formatLocation(block)));
        }
    }

    // ==================== 方块交互 ====================

    /**
     * 监听玩家交互事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();

        // 检查方块交互
        if (block != null && integrationManager.isCustomBlock(block)) {
            if (!integrationManager.canInteractBlock(player, block)) {
                event.setCancelled(true);
                player.sendMessage("§c你没有权限与这个自定义方块交互！");
                logger.fine(String.format("阻止玩家 %s 与自定义方块交互 at %s",
                        player.getName(), formatLocation(block)));
                return;
            }
        }

        // 检查物品使用
        if (item != null && integrationManager.isCustomItem(item)) {
            if (!integrationManager.canUseItem(player, item)) {
                event.setCancelled(true);
                player.sendMessage("§c你没有权限使用这个自定义物品！");
                logger.fine(String.format("阻止玩家 %s 使用自定义物品 %s",
                        player.getName(), item.getType()));
            }
        }
    }

    // ==================== 物品拾取 ====================

    /**
     * 监听物品拾取事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        ItemStack item = event.getItem().getItemStack();

        // 检查是否为自定义物品
        if (!integrationManager.isCustomItem(item)) {
            return;
        }

        // 检查拾取权限
        if (!integrationManager.canPickupItem(player, item)) {
            event.setCancelled(true);
            logger.fine(String.format("阻止玩家 %s 拾取自定义物品 %s",
                    player.getName(), item.getType()));
        }
    }

    // ==================== 物品丢弃 ====================

    /**
     * 监听物品丢弃事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        // 检查是否为自定义物品
        if (!integrationManager.isCustomItem(item)) {
            return;
        }

        // 检查丢弃权限
        if (!integrationManager.canDropItem(player, item)) {
            event.setCancelled(true);
            player.sendMessage("§c你没有权限丢弃这个自定义物品！");
            logger.fine(String.format("阻止玩家 %s 丢弃自定义物品 %s",
                    player.getName(), item.getType()));
        }
    }

    // ==================== 实体伤害 ====================

    /**
     * 监听实体伤害事件（用于保护展示框等）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();

        // 检查展示框
        if (event.getEntity() instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) event.getEntity();
            ItemStack item = frame.getItem();

            // 检查展示框中的物品是否为自定义物品
            if (item != null && integrationManager.isCustomItem(item)) {
                // 检查破坏权限
                if (!player.hasPermission("starcore.protection.itemframe.break")) {
                    event.setCancelled(true);
                    player.sendMessage("§c你没有权限破坏展示的自定义物品！");
                    logger.fine(String.format("阻止玩家 %s 破坏展示框中的自定义物品 %s",
                            player.getName(), item.getType()));
                }
            }
        }
    }

    // ==================== 展示框破坏 ====================

    /**
     * 监听悬挂实体破坏事件（展示框）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getRemover();

        // 检查展示框
        if (event.getEntity() instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) event.getEntity();
            ItemStack item = frame.getItem();

            // 检查展示框中的物品是否为自定义物品
            if (item != null && integrationManager.isCustomItem(item)) {
                // 检查破坏权限
                if (!player.hasPermission("starcore.protection.itemframe.break")) {
                    event.setCancelled(true);
                    player.sendMessage("§c你没有权限破坏展示的自定义物品！");
                    logger.fine(String.format("阻止玩家 %s 破坏展示框中的自定义物品 %s",
                            player.getName(), item.getType()));
                }
            }
        }
    }

    // ==================== 物品点击 ====================

    /**
     * 监听背包点击事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        // 检查是否为自定义物品
        if (item == null || !integrationManager.isCustomItem(item)) {
            return;
        }

        // 这里可以添加额外的物品交互限制
        // 例如：防止玩家将自定义物品放入特定容器
        logger.fine(String.format("玩家 %s 点击了自定义物品 %s",
                player.getName(), item.getType()));
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

    /**
     * 注册监听器
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("第三方插件保护监听器已注册");
    }
}
