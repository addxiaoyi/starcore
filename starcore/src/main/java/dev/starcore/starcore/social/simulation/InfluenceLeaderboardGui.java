package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.social.simulation.InfluenceLeaderboardEntry;
import dev.starcore.starcore.social.simulation.InfluenceLeaderboardService;
import dev.starcore.starcore.social.simulation.LeaderboardPeriod;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 影响力排行榜 GUI
 * 提供可视化的时间周期选择和排行榜展示界面
 */
public class InfluenceLeaderboardGui implements Listener {

    private final Plugin plugin;
    private final InfluenceLeaderboardService leaderboardService;

    // GUI 标题
    private static final String MAIN_GUI_TITLE = "§6§l影响力排行榜";
    private static final String LEADERBOARD_GUI_TITLE = "§6§l影响力排行榜 §7- ";

    // 周期图标映射
    private static final Map<LeaderboardPeriod, Material> PERIOD_ICONS = Map.of(
        LeaderboardPeriod.DAILY, Material.SUNFLOWER,
        LeaderboardPeriod.WEEKLY, Material.CLOCK,
        LeaderboardPeriod.MONTHLY, Material.MAP,  // CHEST -> MAP 作为月历替代
        LeaderboardPeriod.ALLTIME, Material.NETHER_STAR
    );

    public InfluenceLeaderboardGui(Plugin plugin, InfluenceLeaderboardService leaderboardService) {
        this.plugin = plugin;
        this.leaderboardService = leaderboardService;

        // 注册事件监听器
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 获取排行榜服务
     */
    public InfluenceLeaderboardService getLeaderboardService() {
        return leaderboardService;
    }

    /**
     * 打开主菜单 - 周期选择
     */
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, MAIN_GUI_TITLE);

        // 填充边框
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        // 标题物品
        gui.setItem(4, createTitleItem());

        // 周期选择按钮
        gui.setItem(10, createPeriodButton(LeaderboardPeriod.DAILY, "§e每日影响力榜", "§7查看今日影响力排行"));
        gui.setItem(12, createPeriodButton(LeaderboardPeriod.WEEKLY, "§b本周影响力榜", "§7查看本周影响力排行"));
        gui.setItem(14, createPeriodButton(LeaderboardPeriod.MONTHLY, "§d本月影响力榜", "§7查看本月影响力排行"));
        gui.setItem(16, createPeriodButton(LeaderboardPeriod.ALLTIME, "§6总影响力榜", "§7查看历史总排行"));

        // 我的排名按钮
        gui.setItem(22, createMyRankButton(player));

        // 关闭按钮
        gui.setItem(26, createCloseButton());

        player.openInventory(gui);
    }

    /**
     * 打开指定周期的排行榜
     */
    public void openLeaderboard(Player player, LeaderboardPeriod period) {
        String title = LEADERBOARD_GUI_TITLE + period.getDisplayName();
        Inventory gui = Bukkit.createInventory(null, 54, title);

        // 填充边框
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        // 显示加载状态
        gui.setItem(22, createLoadingItem());

        player.openInventory(gui);

        // 异步获取排行榜数据
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<InfluenceLeaderboardEntry> leaderboard = leaderboardService.getLeaderboard(period);

            Bukkit.getScheduler().runTask(plugin, () -> {
                // 检查玩家是否还在查看这个 GUI
                if (!player.getOpenInventory().getTitle().equals(title)) {
                    return;
                }

                // 填充排行榜数据
                fillLeaderboardData(gui, leaderboard, period);

                // 添加底部导航
                fillBottomNav(gui, period, player);

                player.updateInventory();
            });
        });
    }

    /**
     * 打开玩家排名详情
     */
    public void openPlayerDetail(Player player, UUID targetId) {
        String playerName = getPlayerName(targetId);
        String title = "§6§l" + playerName + " §7的影响力";
        Inventory gui = Bukkit.createInventory(null, 45, title);

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        // 显示加载状态
        gui.setItem(22, createLoadingItem());

        player.openInventory(gui);

        // 异步获取数据
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<LeaderboardPeriod, Integer> ranks = leaderboardService.getAllRanks(targetId);
            List<String> titles = leaderboardService.getPlayerTitles(targetId);
            int influence = leaderboardService.getInfluenceService().getInfluence(targetId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.getOpenInventory().getTitle().equals(title)) {
                    return;
                }

                fillPlayerDetailData(gui, targetId, playerName, influence, ranks, titles);

                // 返回按钮
                gui.setItem(44, createBackButton());

                player.updateInventory();
            });
        });
    }

    private void fillLeaderboardData(Inventory gui, List<InfluenceLeaderboardEntry> leaderboard, LeaderboardPeriod period) {
        // 前10名显示在中间区域
        int[] topSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

        Material[] medals = {Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT};
        String[] topColors = {"§6", "§f", "§c"};

        for (int i = 0; i < Math.min(10, leaderboard.size()); i++) {
            InfluenceLeaderboardEntry entry = leaderboard.get(i);
            ItemStack item;

            if (i < 3) {
                // 前三名特殊显示
                item = createTopPlayerItem(entry, medals[i], topColors[i]);
            } else {
                // 4-10名显示玩家头颅
                item = createPlayerListItem(entry);
            }

            gui.setItem(topSlots[i], item);
        }

        // 11-50名显示在下方
        int listSlot = 30;
        for (int i = 10; i < Math.min(50, leaderboard.size()); i++) {
            if (listSlot > 43) break;
            if (listSlot == 36) listSlot = 45; // 跳过底行中间
            if (listSlot >= 45 && listSlot < 53) listSlot = 53;

            InfluenceLeaderboardEntry entry = leaderboard.get(i);
            ItemStack item = createListItem(entry);
            gui.setItem(listSlot++, item);
        }
    }

    private void fillPlayerDetailData(Inventory gui, UUID playerId, String playerName,
                                       int influence, Map<LeaderboardPeriod, Integer> ranks,
                                       List<String> titles) {
        // 玩家头颅
        gui.setItem(4, createDetailPlayerHead(playerId, playerName, influence));

        // 各周期排名
        int slot = 10;
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            Integer rank = ranks.get(period);
            Material icon = PERIOD_ICONS.get(period);
            String rankStr = rank != null && rank > 0 ? "§a第 " + rank + " 名" : "§7未上榜";

            ItemStack item = createRankItem(icon, period.getDisplayName() + "排名", rankStr);
            gui.setItem(slot++, item);
        }

        // 影响力称号
        gui.setItem(20, createTitlesItem(titles));

        // 社会地位
        var status = leaderboardService.getInfluenceService().getStatus(playerId);
        gui.setItem(24, createStatusItem(status));

        // 统计信息
        gui.setItem(31, createStatsItem(influence, ranks));
    }

    private void fillBottomNav(Inventory gui, LeaderboardPeriod currentPeriod, Player player) {
        // 返回按钮
        gui.setItem(45, createBackButton());

        // 我的排名按钮
        gui.setItem(49, createMyRankButton(player));

        // 周期切换按钮
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            if (period == currentPeriod) continue;
            int slot = period.getIndex() + 46;
            if (slot >= 45) slot--;
            gui.setItem(slot, createPeriodSwitchButton(period));
        }
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§6§l影响力排行榜", net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text("查看各时间周期的", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        lore.add(net.kyori.adventure.text.Component.text("影响力排行榜", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text("选择周期查看详细排名", net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPeriodButton(LeaderboardPeriod period, String name, String desc) {
        Material material = PERIOD_ICONS.get(period);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text(desc, net.kyori.adventure.text.format.NamedTextColor.GRAY));

        // 显示第一名
        List<InfluenceLeaderboardEntry> top = leaderboardService.getTopPlayers(period, 1);
        if (!top.isEmpty()) {
            InfluenceLeaderboardEntry topEntry = top.get(0);
            lore.add(net.kyori.adventure.text.Component.text(""));
            lore.add(net.kyori.adventure.text.Component.text("当前第一名: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text(topEntry.playerName(), net.kyori.adventure.text.format.NamedTextColor.GOLD)));
            lore.add(net.kyori.adventure.text.Component.text("影响力: " + topEntry.influence(), net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }

        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text("点击查看", net.kyori.adventure.text.format.NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMyRankButton(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);

            skullMeta.displayName(net.kyori.adventure.text.Component.text("我的排名", net.kyori.adventure.text.format.NamedTextColor.AQUA));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text(""));

            // 显示各周期排名
            Map<LeaderboardPeriod, Integer> ranks = leaderboardService.getAllRanks(player.getUniqueId());
            for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
                Integer rank = ranks.get(period);
                String rankStr = rank != null && rank > 0 ? "§a第 " + rank + " 名" : "§7未上榜";
                lore.add(net.kyori.adventure.text.Component.text(period.getDisplayName() + ": " + rankStr, net.kyori.adventure.text.format.NamedTextColor.GRAY));
            }

            lore.add(net.kyori.adventure.text.Component.text(""));
            lore.add(net.kyori.adventure.text.Component.text("点击查看详情", net.kyori.adventure.text.format.NamedTextColor.YELLOW));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack createTopPlayerItem(InfluenceLeaderboardEntry entry, Material medal, String color) {
        ItemStack item = new ItemStack(medal);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(net.kyori.adventure.text.Component.text(
            color + "§l" + entry.getRankString() + ". " + entry.playerName(), net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text("影响力: " + entry.influence(), net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        lore.add(net.kyori.adventure.text.Component.text("排名变化: " + entry.getChangeDescription(), net.kyori.adventure.text.format.NamedTextColor.GRAY));
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text("点击查看详情", net.kyori.adventure.text.format.NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerListItem(InfluenceLeaderboardEntry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.playerId());
            skullMeta.setOwningPlayer(offlinePlayer);

            skullMeta.displayName(net.kyori.adventure.text.Component.text(
                "§7" + entry.rank() + ". " + entry.playerName(), net.kyori.adventure.text.format.NamedTextColor.WHITE));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text("影响力: " + entry.influence(), net.kyori.adventure.text.format.NamedTextColor.GRAY));
            lore.add(net.kyori.adventure.text.Component.text("变化: " + entry.getChangeDescription(), net.kyori.adventure.text.format.NamedTextColor.GRAY));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack createListItem(InfluenceLeaderboardEntry entry) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        String rankColor = entry.rank() <= 10 ? "§e" : "§7";
        meta.displayName(net.kyori.adventure.text.Component.text(
            rankColor + "#" + entry.rank() + " " + entry.playerName(), net.kyori.adventure.text.format.NamedTextColor.WHITE));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("影响力: " + entry.influence(), net.kyori.adventure.text.format.NamedTextColor.GRAY));
        lore.add(net.kyori.adventure.text.Component.text("变化: " + entry.getChangeDescription(), net.kyori.adventure.text.format.NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRankItem(Material material, String name, String rankStr) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text(rankStr, net.kyori.adventure.text.format.NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDetailPlayerHead(UUID playerId, String playerName, int influence) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            skullMeta.setOwningPlayer(offlinePlayer);

            skullMeta.displayName(net.kyori.adventure.text.Component.text(playerName, net.kyori.adventure.text.format.NamedTextColor.GOLD));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.Component.text(""));
            lore.add(net.kyori.adventure.text.Component.text("总影响力: " + influence, net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            lore.add(net.kyori.adventure.text.Component.text(""));
            lore.add(net.kyori.adventure.text.Component.text("社会地位", net.kyori.adventure.text.format.NamedTextColor.GRAY));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack createTitlesItem(List<String> titles) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);  // 使用金锭代替 MEDAL
        ItemMeta meta = item.getItemMeta();

        meta.displayName(net.kyori.adventure.text.Component.text("影响力称号", net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(""));

        if (titles.isEmpty()) {
            lore.add(net.kyori.adventure.text.Component.text("暂无称号", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        } else {
            for (String title : titles) {
                lore.add(net.kyori.adventure.text.Component.text(title, net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatusItem(SocialInfluenceService.SocialStatus status) {
        Material material = switch (status) {
            case NOBODY -> Material.BARRIER;
            case REGIONAL -> Material.OAK_LOG;
            case KNOWN -> Material.IRON_INGOT;
            case INFLUENTIAL -> Material.GOLD_INGOT;
            case POWER_BROKER -> Material.DIAMOND;
            case KINGMAKER -> Material.NETHER_STAR;
            case LEGENDARY -> Material.DRAGON_HEAD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(net.kyori.adventure.text.Component.text("社会地位", net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text(status.getColor() + status.getName(), net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        lore.add(net.kyori.adventure.text.Component.text("所需影响力: " + status.getThreshold(), net.kyori.adventure.text.format.NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatsItem(int influence, Map<LeaderboardPeriod, Integer> ranks) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(net.kyori.adventure.text.Component.text("统计数据", net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text(""));
        lore.add(net.kyori.adventure.text.Component.text("当前影响力: " + influence, net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        lore.add(net.kyori.adventure.text.Component.text(""));

        int totalRanked = (int) ranks.values().stream().filter(r -> r != null && r > 0).count();
        lore.add(net.kyori.adventure.text.Component.text("上榜周期数: " + totalRanked + "/4", net.kyori.adventure.text.format.NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("返回", net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("点击返回上一级", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPeriodSwitchButton(LeaderboardPeriod period) {
        Material material = PERIOD_ICONS.get(period);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(period.getDisplayName(), net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("切换到" + period.getDisplayName(), net.kyori.adventure.text.format.NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("关闭", net.kyori.adventure.text.format.NamedTextColor.RED));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("点击关闭", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLoadingItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("加载中...", net.kyori.adventure.text.format.NamedTextColor.YELLOW));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("正在获取排行榜数据", net.kyori.adventure.text.format.NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 辅助方法 ====================

    private void fillBorder(Inventory gui, Material borderMaterial) {
        ItemStack border = new ItemStack(borderMaterial);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text(" "));
            border.setItemMeta(meta);
        }

        // 填充边框
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(gui.getSize() - 9 + i, border);
        }
        for (int i = 1; i < (gui.getSize() / 9) - 1; i++) {
            gui.setItem(i * 9, border);
            gui.setItem(i * 9 + 8, border);
        }
    }

    private String getPlayerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() != null ? player.getName() : "Unknown";
    }

    // ==================== 事件处理 ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // 主菜单处理
        if (title.equals(MAIN_GUI_TITLE)) {
            event.setCancelled(true);
            handleMainMenuClick(player, event.getRawSlot());
            return;
        }

        // 排行榜菜单处理
        if (title.startsWith(LEADERBOARD_GUI_TITLE)) {
            event.setCancelled(true);
            handleLeaderboardClick(player, event.getRawSlot(), title);
            return;
        }

        // 玩家详情菜单处理
        if (title.contains("的影响力")) {
            event.setCancelled(true);
            handlePlayerDetailClick(player, event.getRawSlot());
            return;
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 10 -> openLeaderboard(player, LeaderboardPeriod.DAILY);
            case 12 -> openLeaderboard(player, LeaderboardPeriod.WEEKLY);
            case 14 -> openLeaderboard(player, LeaderboardPeriod.MONTHLY);
            case 16 -> openLeaderboard(player, LeaderboardPeriod.ALLTIME);
            case 22 -> openPlayerDetail(player, player.getUniqueId());
            case 26 -> player.closeInventory();
        }
    }

    private void handleLeaderboardClick(Player player, int slot, String title) {
        // 返回按钮
        if (slot == 45) {
            openMainMenu(player);
            return;
        }

        // 我的排名按钮
        if (slot == 49) {
            openPlayerDetail(player, player.getUniqueId());
            return;
        }

        // 周期切换按钮
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            int switchSlot = period.getIndex() + 46;
            if (switchSlot >= 45) switchSlot--;
            if (slot == switchSlot) {
                openLeaderboard(player, period);
                return;
            }
        }

        // 点击玩家项目 - 获取当前周期
        LeaderboardPeriod currentPeriod = LeaderboardPeriod.ALLTIME;
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            if (title.endsWith(period.getDisplayName())) {
                currentPeriod = period;
                break;
            }
        }

        // 前10名区域
        int[] topSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        for (int i = 0; i < topSlots.length; i++) {
            if (slot == topSlots[i]) {
                List<InfluenceLeaderboardEntry> leaderboard = leaderboardService.getLeaderboard(currentPeriod);
                if (i < leaderboard.size()) {
                    openPlayerDetail(player, leaderboard.get(i).playerId());
                }
                return;
            }
        }
    }

    private void handlePlayerDetailClick(Player player, int slot) {
        if (slot == 44) {
            // 返回 - 需要知道之前查看的是哪个周期
            openMainMenu(player);
        }
    }
}
