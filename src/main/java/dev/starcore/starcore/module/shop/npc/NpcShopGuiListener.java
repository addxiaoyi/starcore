package dev.starcore.starcore.module.shop.npc;

import dev.starcore.starcore.module.shop.npc.NpcShopServiceImpl.ShopEconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC商店GUI监听器
 * 处理玩家在商店GUI中的交互
 */
public class NpcShopGuiListener implements Listener {

    private final Plugin plugin;
    private final NpcShopService npcShopService;
    private final ShopEconomyService economyService;

    // 跟踪打开商店GUI的玩家，防止内存泄漏
    private final Set<UUID> openShopPlayers = ConcurrentHashMap.newKeySet();

    // 冷却时间追踪（防止快速点击）
    private final Set<UUID> clickCooldown = ConcurrentHashMap.newKeySet();
    private static final long CLICK_COOLDOWN_MS = 200;

    public NpcShopGuiListener(Plugin plugin, NpcShopService npcShopService, ShopEconomyService economyService) {
        this.plugin = plugin;
        this.npcShopService = npcShopService;
        this.economyService = economyService;
    }

    /**
     * 标记玩家打开了商店GUI
     */
    public void markPlayerOpened(Player player) {
        openShopPlayers.add(player.getUniqueId());
    }

    /**
     * 标记玩家关闭了商店GUI
     */
    public void markPlayerClosed(Player player) {
        openShopPlayers.remove(player.getUniqueId());
        NpcShopGui.resetQuantity(player.getUniqueId());
    }

    /**
     * 检查玩家是否在冷却中
     */
    private boolean isOnCooldown(UUID playerId) {
        if (clickCooldown.contains(playerId)) {
            return true;
        }
        clickCooldown.add(playerId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> clickCooldown.remove(playerId), 1);
        return false;
    }

    /**
     * 处理GUI点击事件
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 检查是否是我们的商店GUI
        String title = event.getView().getTitle();
        if (!title.contains("NPC商店")) {
            return;
        }

        // 检查冷却
        UUID playerId = player.getUniqueId();
        if (isOnCooldown(playerId)) {
            event.setCancelled(true);
            return;
        }

        // 标记玩家正在使用商店GUI（只在首次点击时添加）
        openShopPlayers.add(playerId);

        // 阻止玩家从上方拖入物品
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            event.setCancelled(true);

            // 处理点击
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != org.bukkit.Material.BLACK_STAINED_GLASS_PANE) {
                NpcShopGui.handleClick(player, event.getSlot(), clickedItem, npcShopService, economyService);
            }
        }
    }

    /**
     * 处理拖拽事件（防止物品进入商店GUI）
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (title.contains("NPC商店")) {
            event.setCancelled(true);
        }
    }

    /**
     * 处理GUI关闭事件
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (title.contains("NPC商店")) {
            // 播放关闭声音
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 1.0f);

            // 清理玩家状态，防止内存泄漏
            markPlayerClosed(player);
        }
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 openShopPlayers Set 导致的内存泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openShopPlayers.remove(event.getPlayer().getUniqueId());
    }
}
