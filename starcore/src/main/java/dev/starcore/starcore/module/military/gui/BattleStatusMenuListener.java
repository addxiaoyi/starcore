package dev.starcore.starcore.module.military.gui;

import dev.starcore.starcore.module.military.gui.BattleStatusMenu.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.Optional;

/**
 * 战况预览 GUI 点击事件监听器
 */
public class BattleStatusMenuListener implements Listener {

    private final BattleStatusMenu battleStatusMenu;

    // 用于跟踪自动刷新的玩家
    private final java.util.Map<java.util.UUID, Integer> refreshTasks = new java.util.concurrent.ConcurrentHashMap<>();

    public BattleStatusMenuListener(BattleStatusMenu battleStatusMenu) {
        this.battleStatusMenu = battleStatusMenu;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inv = event.getInventory();
        if (inv == null) {
            return;
        }

        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        // 检查是否是我们的 GUI
        if (!isBattleStatusGui(title)) {
            return;
        }

        event.setCancelled(true);

        // 播放点击音效
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int slot = event.getSlot();
        ItemMeta meta = clickedItem.getItemMeta();

        // 处理战况总览
        if (title.contains("战况总览")) {
            handleMainMenuClick(player, rawSlot, clickedItem, inv);
            return;
        }

        // 处理战场详情
        if (title.contains("战场详情")) {
            handleBattlefieldClick(player, rawSlot, inv);
            return;
        }

        // 处理军队分布
        if (title.contains("军队分布")) {
            handleArmyDistributionClick(player, rawSlot, inv);
            return;
        }

        // 处理战局趋势
        if (title.contains("战局趋势")) {
            handleWarTrendClick(player, rawSlot, inv);
            return;
        }

        // 处理敌情分析
        if (title.contains("敌情分析")) {
            handleEnemyAnalysisClick(player, rawSlot, inv);
            return;
        }
    }

    private boolean isBattleStatusGui(String title) {
        return title.contains("战况") ||
               title.contains("战场详情") ||
               title.contains("军队分布") ||
               title.contains("战局趋势") ||
               title.contains("敌情分析");
    }

    private void handleMainMenuClick(Player player, int slot, ItemStack item, Inventory inv) {
        int size = inv.getSize();

        switch (slot) {
            case 0 -> {
                // 刷新 - 重新打开菜单
                battleStatusMenu.openMainMenu(player);
            }
            case 10, 12, 14, 16 -> {
                // 统计框 - 暂时显示详情
                player.sendMessage("§6=== 统计详情 ===");
                player.sendMessage("§7点击查看更多统计信息...");
            }
            case 19 -> {
                // 战场态势预览 - 打开战场详情
                battleStatusMenu.openBattlefieldMenu(player, 0);
            }
            case 21 -> {
                // 军力概览 - 打开军队分布
                battleStatusMenu.openArmyDistributionMenu(player, 0);
            }
            case 23 -> {
                // 盟军状态 - 显示盟军信息
                player.sendMessage("§6=== 盟军状态 ===");
                player.sendMessage("§7查看盟军详细信息...");
            }
            case 28 -> {
                // 战场详情按钮
                battleStatusMenu.openBattlefieldMenu(player, 0);
            }
            case 30 -> {
                // 军队分布按钮
                battleStatusMenu.openArmyDistributionMenu(player, 0);
            }
            case 32 -> {
                // 战局趋势按钮
                battleStatusMenu.openWarTrendMenu(player);
            }
            case 34 -> {
                // 敌情分析按钮
                battleStatusMenu.openEnemyAnalysisMenu(player);
            }
            case 37, 38, 39, 40, 41, 42, 43 -> {
                // 战争指示器 - 查看具体战争
                handleWarIndicatorClick(player, item);
            }
            case 49 -> {
                // 帮助信息
                player.sendMessage("§6=== 战况预览帮助 ===");
                player.sendMessage("§7战况预览提供实时的战场态势感知");
                player.sendMessage("§7- 战场详情: 查看所有战场状态");
                player.sendMessage("§7- 军队分布: 查看军队部署");
                player.sendMessage("§7- 战局趋势: 分析战争走向");
                player.sendMessage("§7- 敌情分析: 识别敌方弱点");
            }
            case 53 -> {
                // 返回按钮
                player.closeInventory();
            }
        }
    }

    private void handleBattlefieldClick(Player player, int slot, Inventory inv) {
        int size = inv.getSize();

        // 返回按钮
        if (slot == size - 9 + 4) {
            battleStatusMenu.openMainMenu(player);
            return;
        }

        // 上一页
        if (slot == size - 9) {
            Integer currentPage = battleStatusMenu.getPlayerPages().get(player.getUniqueId());
            int page = currentPage != null ? currentPage - 1 : 0;
            battleStatusMenu.openBattlefieldMenu(player, page);
            return;
        }

        // 下一页
        if (slot == size - 1) {
            Integer currentPage = battleStatusMenu.getPlayerPages().get(player.getUniqueId());
            int page = currentPage != null ? currentPage + 1 : 1;
            battleStatusMenu.openBattlefieldMenu(player, page);
            return;
        }

        // 刷新按钮
        if (slot == 0) {
            Integer currentPage = battleStatusMenu.getPlayerPages().get(player.getUniqueId());
            int page = currentPage != null ? currentPage : 0;
            battleStatusMenu.openBattlefieldMenu(player, page);
            return;
        }

        // 战场项目 - 显示详情
        if (slot >= 9 && slot <= size - 10) {
            ItemMeta meta = inv.getItem(slot) != null ? inv.getItem(slot).getItemMeta() : null;
            if (meta != null && meta.lore() != null && !meta.lore().isEmpty()) {
                String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                player.sendMessage("§6=== 战场详情 ===");
                player.sendMessage("§7战场: §f" + itemName.replaceAll("[§][a-f0-9]", "").trim());
                player.sendMessage("§7查看完整战场信息...");
            }
        }
    }

    private void handleArmyDistributionClick(Player player, int slot, Inventory inv) {
        int size = inv.getSize();

        // 返回按钮
        if (slot == size - 9 + 4) {
            battleStatusMenu.openMainMenu(player);
            return;
        }

        // 刷新按钮
        if (slot == 0) {
            Integer currentPage = battleStatusMenu.getPlayerPages().get(player.getUniqueId());
            int page = currentPage != null ? currentPage : 0;
            battleStatusMenu.openArmyDistributionMenu(player, page);
            return;
        }

        // 军队项目 - 显示详情
        if (slot >= 18 && slot <= size - 10) {
            ItemMeta meta = inv.getItem(slot) != null ? inv.getItem(slot).getItemMeta() : null;
            if (meta != null) {
                String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                player.sendMessage("§6=== 军队详情 ===");
                player.sendMessage("§7" + itemName.replaceAll("[§][a-f0-9]", "").trim());
            }
        }
    }

    private void handleWarTrendClick(Player player, int slot, Inventory inv) {
        int size = inv.getSize();

        // 返回按钮
        if (slot == 49) {
            battleStatusMenu.openMainMenu(player);
            return;
        }

        // 刷新按钮
        if (slot == 0) {
            battleStatusMenu.openWarTrendMenu(player);
            return;
        }

        // 趋势项目
        switch (slot) {
            case 10 -> player.sendMessage("§6=== 战争评分详情 ===\n§7基于战争持续时间和规模计算");
            case 16 -> player.sendMessage("§6=== 战略态势详情 ===\n§7评估当前战争的战略影响");
            case 28 -> player.sendMessage("§6=== 战局预测详情 ===\n§7基于历史数据预测战争走向");
            case 34 -> player.sendMessage("§6=== 资源消耗详情 ===\n§7统计战争期间的物资消耗");
        }
    }

    private void handleEnemyAnalysisClick(Player player, int slot, Inventory inv) {
        int size = inv.getSize();

        // 返回按钮
        if (slot == 49) {
            battleStatusMenu.openMainMenu(player);
            return;
        }

        // 刷新按钮
        if (slot == 0) {
            battleStatusMenu.openEnemyAnalysisMenu(player);
            return;
        }

        // 敌情分析项目
        switch (slot) {
            case 10 -> player.sendMessage("§6=== 威胁等级详情 ===\n§7评估所有敌国的威胁程度");
            case 16 -> player.sendMessage("§6=== 弱点分析详情 ===\n§7识别敌方防守薄弱环节");
        }

        // 敌方详情
        if (slot >= 19 && slot <= size - 10) {
            ItemMeta meta = inv.getItem(slot) != null ? inv.getItem(slot).getItemMeta() : null;
            if (meta != null) {
                String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                player.sendMessage("§6=== 敌情详情 ===");
                player.sendMessage("§c" + itemName.replaceAll("[§][a-f0-9]", "").trim());
            }
        }
    }

    private void handleWarIndicatorClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 提取敌国名称
        if (itemName.contains("vs ")) {
            String enemyName = itemName.substring(itemName.indexOf("vs ") + 3).trim();
            player.sendMessage("§6=== 战争详情 ===");
            player.sendMessage("§7敌国: §c" + enemyName);
            player.sendMessage("§7使用 §e/war status " + enemyName + " §7查看详细战争信息");
        }
    }

    /**
     * 启动菜单的自动刷新
     */
    public void startAutoRefresh(Player player) {
        // 取消之前的刷新任务
        Integer existingTask = refreshTasks.remove(player.getUniqueId());
        if (existingTask != null) {
            Bukkit.getScheduler().cancelTask(existingTask);
        }

        // 每10秒刷新一次菜单
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() == null) {
                    refreshTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                String titleStr = player.getOpenInventory().getTitle();

                if (isBattleStatusGui(titleStr)) {
                    // 刷新菜单
                    if (titleStr.contains("战况总览")) {
                        battleStatusMenu.openMainMenu(player);
                    } else if (titleStr.contains("战场详情")) {
                        Integer page = battleStatusMenu.getPlayerPages().get(player.getUniqueId());
                        battleStatusMenu.openBattlefieldMenu(player, page != null ? page : 0);
                    } else if (titleStr.contains("军队分布")) {
                        Integer page = battleStatusMenu.getPlayerPages().get(player.getUniqueId());
                        battleStatusMenu.openArmyDistributionMenu(player, page != null ? page : 0);
                    } else if (titleStr.contains("战局趋势")) {
                        battleStatusMenu.openWarTrendMenu(player);
                    } else if (titleStr.contains("敌情分析")) {
                        battleStatusMenu.openEnemyAnalysisMenu(player);
                    }
                } else {
                    // 用户已经离开战况菜单
                    refreshTasks.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugin("StarCore"), 200L, 200L).getTaskId();

        refreshTasks.put(player.getUniqueId(), taskId);
    }

    /**
     * 停止菜单的自动刷新
     */
    public void stopAutoRefresh(Player player) {
        Integer taskId = refreshTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /**
     * 停止所有刷新任务
     */
    public void stopAllRefresh() {
        for (Integer taskId : refreshTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        refreshTasks.clear();
    }

    // E-050 修复: 玩家退出时取消自动刷新任务并清理状态
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopAutoRefresh(event.getPlayer());
    }
}
