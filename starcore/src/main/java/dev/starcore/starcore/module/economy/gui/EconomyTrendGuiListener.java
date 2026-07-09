package dev.starcore.starcore.module.economy.gui;

import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.module.economy.EconomyTrendService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 经济趋势分析 GUI 事件监听器
 */
public class EconomyTrendGuiListener implements Listener {

    private final EconomyTrendService trendService;
    private final TreasuryService treasuryService;
    private final NationService nationService;
    private final EconomyTrendGui trendGui;
    private final SoundFeedbackManager soundManager;

    // 玩家当前查看的页面
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    public EconomyTrendGuiListener(
            EconomyTrendService trendService,
            TreasuryService treasuryService,
            NationService nationService,
            EconomyTrendGui trendGui,
            SoundFeedbackManager soundManager
    ) {
        this.trendService = trendService;
        this.treasuryService = treasuryService;
        this.nationService = nationService;
        this.trendGui = trendGui;
        this.soundManager = soundManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().title().toString();

        // 处理经济趋势主菜单
        if (title.startsWith("§6§l📈 经济趋势分析")) {
            event.setCancelled(true);
            handleMainMenuClick(player, event.getSlot(), event.getCurrentItem());
            return;
        }

        // 处理走势图菜单
        if (title.startsWith("§b§l📊 收支走势图")) {
            event.setCancelled(true);
            handleChartMenuClick(player, event.getSlot(), event.getCurrentItem(), title);
            return;
        }

        // 处理深度分析菜单
        if (title.startsWith("§d§l🔍 深度分析")) {
            event.setCancelled(true);
            handleAnalysisMenuClick(player, event.getSlot(), event.getCurrentItem());
            return;
        }

        // 处理历史明细菜单
        if (title.startsWith("§6§l📋 历史明细")) {
            event.setCancelled(true);
            handleHistoryMenuClick(player, event.getSlot(), event.getCurrentItem(), title);
            return;
        }
    }

    private void handleMainMenuClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        playClickSound(player);

        switch (slot) {
            case 28 -> trendGui.openChartMenu(player, 7);
            case 30 -> trendGui.openAnalysisMenu(player);
            case 32 -> {
                int page = playerPages.getOrDefault(player.getUniqueId(), 1);
                trendGui.openHistoryMenu(player, page);
            }
            case 40 -> {
                // 返回按钮 - 返回到国库菜单
                player.closeInventory();
                player.sendMessage("§7使用 §e/treasury §7打开国库管理");
            }
        }
    }

    private void handleChartMenuClick(Player player, int slot, ItemStack item, String title) {
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        playClickSound(player);

        // 从标题提取当前天数
        int currentDays = title.contains("7天") ? 7 : title.contains("30天") ? 30 : 7;

        switch (slot) {
            case 46 -> trendGui.openChartMenu(player, 7);
            case 47 -> trendGui.openChartMenu(player, 30);
            case 49 -> trendGui.openMainMenu(player);
        }
    }

    private void handleAnalysisMenuClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        playClickSound(player);

        switch (slot) {
            case 49 -> trendGui.openMainMenu(player);
        }
    }

    private void handleHistoryMenuClick(Player player, int slot, ItemStack item, String title) {
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        // 从标题提取当前页码
        int currentPage = 1;
        try {
            int start = title.indexOf("第") + 1;
            int end = title.indexOf("页");
            if (start > 0 && end > start) {
                currentPage = Integer.parseInt(title.substring(start, end));
            }
        } catch (Exception ignored) {
        }

        playClickSound(player);

        switch (slot) {
            case 45 -> {
                // 上一页
                if (currentPage > 1) {
                    playerPages.put(player.getUniqueId(), currentPage - 1);
                    trendGui.openHistoryMenu(player, currentPage - 1);
                }
            }
            case 48 -> {
                // 保持当前页
                trendGui.openHistoryMenu(player, currentPage);
            }
            case 49 -> {
                // 返回主菜单
                playerPages.remove(player.getUniqueId());
                trendGui.openMainMenu(player);
            }
            case 50 -> {
                // 下一页
                playerPages.put(player.getUniqueId(), currentPage + 1);
                trendGui.openHistoryMenu(player, currentPage + 1);
            }
        }
    }

    private void playClickSound(Player player) {
        if (soundManager != null) {
            soundManager.playMenuSelect(player);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }

    // E-037 修复: 玩家退出时清理 playerPages Map 状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerPages.remove(event.getPlayer().getUniqueId());
    }
}