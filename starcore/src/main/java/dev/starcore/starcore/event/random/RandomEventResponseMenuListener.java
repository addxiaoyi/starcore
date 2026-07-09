package dev.starcore.starcore.event.random;

import dev.starcore.starcore.event.random.effect.EconomyEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 随机事件响应菜单监听器
 * 处理玩家在事件响应菜单中的交互
 */
public class RandomEventResponseMenuListener implements Listener {

    private final RandomEventService eventService;
    private final Map<UUID, RandomEventResponseMenu> openMenus;
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    public RandomEventResponseMenuListener(RandomEventService eventService) {
        this.eventService = eventService;
        this.openMenus = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null || clicked.getHolder() == null) {
            return;
        }

        if (!(clicked.getHolder() instanceof RandomEventResponseMenu menu)) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();

        // 检查是否是导航按钮
        if (handleNavigation(player, menu, slot)) {
            return;
        }

        // 检查是否是响应选项
        String responseId = menu.getResponseIdAtSlot(slot);
        if (responseId != null) {
            handleResponseSelection(player, menu, responseId);
        }
    }

    /**
     * 处理导航按钮点击
     */
    private boolean handleNavigation(Player player, RandomEventResponseMenu menu, int slot) {
        switch (slot) {
            case 45: // 上一页
                RandomEventResponseMenu prevPage = menu.createPrevPage();
                if (prevPage != null) {
                    prevPage.buildMenu();
                    openMenus.put(player.getUniqueId(), prevPage);
                    player.openInventory(prevPage.getInventory());
                    return true;
                }
                break;

            case 53: // 下一页
                RandomEventResponseMenu nextPage = menu.createNextPage();
                if (nextPage != null) {
                    nextPage.buildMenu();
                    openMenus.put(player.getUniqueId(), nextPage);
                    player.openInventory(nextPage.getInventory());
                    return true;
                }
                break;

            case 49: // 关闭按钮
                player.closeInventory();
                player.sendMessage(Component.text("§7你放弃了选择，事件将自动执行。", NamedTextColor.GRAY));
                return true;
        }
        return false;
    }

    /**
     * 处理响应选项选择
     */
    private void handleResponseSelection(Player player, RandomEventResponseMenu menu, String responseId) {
        UUID playerId = player.getUniqueId();

        // Bug修复 #1: 防止重复点击
        if (!processingPlayers.add(playerId)) {
            return; // 正在处理中，忽略重复点击
        }

        try {
            RandomEvent event = menu.getEvent();
            RandomEvent.EventResponse response = event.getResponses().get(responseId);

            if (response == null) {
                player.sendMessage(Component.text("§c响应选项不存在！", NamedTextColor.RED));
                return;
            }

            // 检查需求
            if (!response.meetsRequirements(player)) {
                player.sendMessage(Component.text("§c你尚未满足此响应的需求！", NamedTextColor.RED));
                return;
            }

            // 执行响应
            boolean success = eventService.executeResponse(event, responseId, player, player.getLocation());

            if (success) {
                player.sendMessage(Component.text("§a你选择了: " + response.getName(), NamedTextColor.GREEN));
                player.sendMessage(Component.text("§7响应效果已生效。", NamedTextColor.GRAY));

                // 处理后续事件链
                UUID chainId = menu.getEventChainId();
                if (chainId != null) {
                    eventService.processChainResponses(chainId, response.getChainEvents());
                }
            } else {
                player.sendMessage(Component.text("§c响应执行失败！", NamedTextColor.RED));
            }

            // 关闭菜单
            player.closeInventory();
        } finally {
            processingPlayers.remove(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof RandomEventResponseMenu) {
            openMenus.remove(player.getUniqueId());
        }
    }

    /**
     * 注册打开的菜单
     */
    public void registerOpenMenu(RandomEventResponseMenu menu) {
        openMenus.put(menu.getPlayer().getUniqueId(), menu);
    }

    /**
     * 获取玩家打开的菜单
     */
    public RandomEventResponseMenu getOpenMenu(Player player) {
        return openMenus.get(player.getUniqueId());
    }

    /**
     * 检查玩家是否有打开的菜单
     */
    public boolean hasOpenMenu(Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 Map 状态导致的内存泄漏和状态泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        openMenus.remove(playerId);
        processingPlayers.remove(playerId);
    }
}
