package dev.starcore.starcore.ranking.gui;

import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;
import dev.starcore.starcore.ranking.RankingService.TopPlayerData;
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
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 排行榜GUI界面
 * 提供可视化的 Top10 展示和玩家排名详情
 */
public class RankingGUI implements Listener {

    private final Plugin plugin;
    private final RankingService rankingService;
    private final PvPStatsService pvpStatsService;

    // GUI 标题
    private static final String TOP_GUI_TITLE = "§6§l排行榜 §7- ";
    private static final String PLAYER_GUI_TITLE = "§6§l排名详情 §7- ";
    private static final String STAT_GUI_TITLE = "§6§l个人统计";

    // 统计类型
    private static final Map<String, String> STAT_NAMES = Map.of(
        "kills", "§c击杀榜",
        "deaths", "§7死亡榜",
        "playtime", "§b在线时间榜",
        "kdratio", "§aK/D榜"
    );

    // 反向映射：从显示名称获取 statType
    private static final Map<String, String> STAT_TYPE_FROM_TITLE = Map.of(
        "§c击杀榜", "kills",
        "§7死亡榜", "deaths",
        "§b在线时间榜", "playtime",
        "§aK/D榜", "kdratio"
    );

    public RankingGUI(Plugin plugin, RankingService rankingService, PvPStatsService pvpStatsService) {
        this.plugin = plugin;
        this.rankingService = rankingService;
        this.pvpStatsService = pvpStatsService;

        // 注册事件监听器
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 打开主排行榜选择界面
     */
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, TOP_GUI_TITLE + "§e选择榜单");

        // 填充背景
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        // 设置统计类型选项
        setMenuItem(gui, 11, Material.DIAMOND_SWORD, "§c击杀榜",
            "§7查看玩家击杀排名", "§7查看前10名玩家");
        setMenuItem(gui, 13, Material.BONE, "§7死亡榜",
            "§7查看玩家死亡排名", "§7查看前10名玩家");
        setMenuItem(gui, 15, Material.CLOCK, "§b在线时间榜",
            "§7查看玩家在线时间排名", "§7查看前10名玩家");

        // 第4行中间添加 K/D 比率
        setMenuItem(gui, 22, Material.NETHER_STAR, "§aK/D 比率榜",
            "§7查看玩家K/D排名", "§7查看前10名玩家");

        player.openInventory(gui);
    }

    /**
     * 打开指定类型的 Top10 排行榜
     */
    public void openTop10(Player player, String statType) {
        String displayName = STAT_NAMES.getOrDefault(statType, statType);
        Inventory gui = Bukkit.createInventory(null, 36, TOP_GUI_TITLE + displayName);

        // 填充背景
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        // 显示加载提示
        setMenuItem(gui, 13, Material.CLOCK, "§e加载中...",
            "§7正在获取排名数据",
            "§7请稍候...");

        // 先打开 GUI 显示加载状态
        player.openInventory(gui);

        // 异步获取 Top10 数据
        CompletableFuture<List<TopPlayerData>> future = CompletableFuture.supplyAsync(() ->
            rankingService.getTopPlayers(statType, 10, RankPeriod.ALLTIME)
        );

        future.thenAccept(topPlayers -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // 检查玩家是否还在查看这个 GUI
                if (!player.getOpenInventory().getTitle().equals(TOP_GUI_TITLE + displayName)) {
                    return; // 玩家已关闭或切换了 GUI
                }

                // 填充 Top10 数据
                fillTop10Data(gui, statType, topPlayers);

                // 添加返回按钮
                setMenuItem(gui, 31, Material.ARROW, "§e返回",
                    "§7点击返回主菜单");

                player.updateInventory();
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to load Top10 for " + statType + ": " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.getOpenInventory().getTitle().equals(TOP_GUI_TITLE + displayName)) {
                    return;
                }
                setMenuItem(gui, 13, Material.BARRIER, "§c加载失败",
                    "§7无法获取排名数据",
                    "§7请稍后重试");
                player.updateInventory();
            });
            return null;
        });
    }

    /**
     * 打开玩家排名详情界面
     */
    public void openPlayerStats(Player player, UUID targetId) {
        String displayName = STAT_NAMES.getOrDefault("kills", "击杀");
        Inventory gui = Bukkit.createInventory(null, 45, PLAYER_GUI_TITLE + "§f" + getPlayerName(targetId));

        // 填充背景
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        // 先打开空界面，显示加载提示
        setMenuItem(gui, 22, Material.BARRIER, "§e加载中...",
            "§7正在获取玩家数据",
            "§7请稍候...");

        player.openInventory(gui);

        // 异步获取数据
        CompletableFuture<PlayerStatsData> statsFuture = CompletableFuture.supplyAsync(() -> {
            // 获取各项数据
            int killRank = rankingService.getKillRank(targetId, RankPeriod.ALLTIME).join();
            int kdRank = rankingService.getKDRatioRank(targetId, RankPeriod.ALLTIME).join();
            long kills = rankingService.getKillCount(targetId, RankPeriod.ALLTIME).join();
            long deaths = rankingService.getDeathCount(targetId, RankPeriod.ALLTIME).join();
            long assists = rankingService.getAssistCount(targetId, RankPeriod.ALLTIME).join();
            long onlineTime = rankingService.getOnlineTime(targetId, RankPeriod.ALLTIME).join();
            int onlineRank = rankingService.getOnlineTimeRank(targetId, RankPeriod.ALLTIME).join();

            // 计算 K/D 比率
            double kdRatio = deaths > 0 ? (double) kills / deaths : kills;
            // 计算 KDA = (Kills + Assists) / Deaths
            double kda = deaths > 0 ? (double) (kills + assists) / deaths : kills + assists;

            // 获取完整 PvP 统计
            PvPStats pvpStats = (pvpStatsService != null) ? pvpStatsService.getStats(targetId) : null;

            return new PlayerStatsData(killRank, kdRank, kills, deaths, assists, onlineTime, onlineRank, kdRatio, kda, pvpStats);
        });

        // 异步完成后在主线程更新 GUI
        statsFuture.thenAccept(stats -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // 更新标题（可能需要关闭后重新打开，或直接更新现有GUI内容）
                gui.setContents(createPlayerStatsContents(stats));
                // 强制刷新
                player.updateInventory();
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                gui.setContents(createErrorContents());
                player.updateInventory();
            });
            return null;
        });
    }

    /**
     * 创建玩家统计 GUI 内容
     */
    private ItemStack[] createPlayerStatsContents(PlayerStatsData stats) {
        Inventory tempGui = Bukkit.createInventory(null, 45, "");
        fillBorder(tempGui, Material.GRAY_STAINED_GLASS_PANE);

        // PvP 统计
        setMenuItem(tempGui, 10, Material.DIAMOND_SWORD, "§c击杀数",
            "§7数值: §f" + stats.kills(),
            "§7排名: " + (stats.killRank() > 0 ? "§a第 " + stats.killRank() + " 名" : "§7未上榜"));

        setMenuItem(tempGui, 12, Material.BONE, "§7死亡数",
            "§7数值: §f" + stats.deaths(),
            "§7K/D: §f" + String.format("%.2f", stats.kdRatio()));

        setMenuItem(tempGui, 14, Material.GOLDEN_APPLE, "§aK/D 比率",
            "§7数值: §f" + String.format("%.2f", stats.kdRatio()),
            "§7排名: " + (stats.kdRank() > 0 ? "§a第 " + stats.kdRank() + " 名" : "§7未上榜"));

        // 在线时间
        setMenuItem(tempGui, 16, Material.CLOCK, "§b在线时间",
            "§7数值: §f" + formatDuration(stats.onlineTime()),
            "§7排名: " + (stats.onlineRank() > 0 ? "§a第 " + stats.onlineRank() + " 名" : "§7未上榜"));

        // 获取更详细的 PvP 统计
        if (stats.pvpStats() != null) {
            setMenuItem(tempGui, 28, Material.IRON_SWORD, "§b助攻数",
                "§7数值: §f" + stats.assists(),
                "§7KDA: §f" + String.format("%.2f", stats.kda()));

            setMenuItem(tempGui, 30, Material.BLAZE_POWDER, "§6连杀统计",
                "§7当前连杀: §f" + stats.pvpStats().getCurrentKillStreak(),
                "§7最高连杀: §f" + stats.pvpStats().getBestKillStreak());

            setMenuItem(tempGui, 32, Material.END_CRYSTAL, "§d决斗统计",
                "§7胜利: §f" + stats.pvpStats().getDuelWins(),
                "§7失败: §f" + stats.pvpStats().getDuelLosses(),
                "§7胜率: §f" + String.format("%.1f%%", stats.pvpStats().getDuelWinRate()));
        } else {
            // 如果没有 PvP 统计，使用基本数据
            setMenuItem(tempGui, 28, Material.IRON_SWORD, "§b助攻数",
                "§7数值: §f" + stats.assists(),
                "§7KDA: §f" + String.format("%.2f", stats.kda()));
        }

        // 排行榜入口
        setMenuItem(tempGui, 37, Material.DIAMOND_SWORD, "§c击杀榜",
            "§7点击查看 Top10");

        setMenuItem(tempGui, 38, Material.BONE, "§7死亡榜",
            "§7点击查看 Top10");

        // K/D 比率榜入口
        setMenuItem(tempGui, 39, Material.GOLDEN_APPLE, "§aK/D 比率榜",
            "§7点击查看 Top10");

        setMenuItem(tempGui, 40, Material.CLOCK, "§b在线时间榜",
            "§7点击查看 Top10");

        // KDA 比率榜入口
        setMenuItem(tempGui, 42, Material.BLAZE_ROD, "§dKDA 比率榜",
            "§7(K + A) / D",
            "§7点击查看 Top10");

        // 返回按钮
        setMenuItem(tempGui, 44, Material.ARROW, "§e返回",
            "§7点击返回");

        return tempGui.getContents();
    }

    /**
     * 创建错误状态内容
     */
    private ItemStack[] createErrorContents() {
        Inventory tempGui = Bukkit.createInventory(null, 45, "");
        fillBorder(tempGui, Material.GRAY_STAINED_GLASS_PANE);
        setMenuItem(tempGui, 22, Material.BARRIER, "§c加载失败",
            "§7获取数据时发生错误");
        return tempGui.getContents();
    }

    /**
     * 玩家统计数据记录
     */
    private record PlayerStatsData(int killRank, int kdRank, long kills, long deaths, long assists,
                                   long onlineTime, int onlineRank, double kdRatio, double kda,
                                   PvPStats pvpStats) {}

    /**
     * 填充 Top10 数据
     */
    private void fillTop10Data(Inventory gui, String statType, List<TopPlayerData> topPlayers) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        Material[] medals = {Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT};
        String[] medalColors = {"§6", "§f", "§c"};

        for (int i = 0; i < 10 && i < topPlayers.size(); i++) {
            TopPlayerData data = topPlayers.get(i);
            String playerName = getPlayerName(data.playerId());
            String valueStr = formatStatValue(statType, data.value());

            // 排名图标
            Material icon;
            String name;
            List<String> lore = new ArrayList<>();

            if (i < 3) {
                icon = medals[i];
                name = medalColors[i] + "§l" + data.position() + ". " + playerName;
                lore.add("§7" + valueStr);
            } else {
                icon = Material.PLAYER_HEAD;
                name = "§7" + data.position() + ". " + playerName;
                lore.add("§8" + valueStr);
            }

            lore.add("");
            lore.add("§e点击查看详情");

            // 特殊处理玩家头颅
            ItemStack item = icon == Material.PLAYER_HEAD
                ? createPlayerHead(data.playerId(), name, lore)
                : createItem(icon, name, lore);

            gui.setItem(slots[i], item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // 主菜单处理
        if (title.startsWith(TOP_GUI_TITLE + "§e选择榜单")) {
            event.setCancelled(true);
            handleMainMenuClick(player, event.getRawSlot());
            return;
        }

        // Top10 菜单处理
        if (title.startsWith(TOP_GUI_TITLE) && !title.contains("选择")) {
            event.setCancelled(true);
            handleTop10Click(player, event, title);
            return;
        }

        // 玩家详情菜单处理
        if (title.startsWith(PLAYER_GUI_TITLE)) {
            event.setCancelled(true);
            handlePlayerStatsClick(player, event);
            return;
        }
    }

    /**
     * 处理主菜单点击
     */
    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 11 -> openTop10(player, "kills");
            case 13 -> openTop10(player, "deaths");
            case 15 -> openTop10(player, "playtime");
            case 22 -> openTop10(player, "kdratio");
            case 31 -> {
                // 打开个人统计
                openPlayerStats(player, player.getUniqueId());
            }
        }
    }

    /**
     * 处理 Top10 菜单点击
     */
    private void handleTop10Click(Player player, InventoryClickEvent event, String title) {
        int slot = event.getRawSlot();
        int[] topSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

        if (slot == 31) {
            // 返回按钮
            openMainMenu(player);
            return;
        }

        // 检查是否点击了玩家项目
        for (int i = 0; i < topSlots.length; i++) {
            if (slot == topSlots[i]) {
                // 从标题提取统计类型
                String displayName = title.replace(TOP_GUI_TITLE, "");
                String statType = STAT_TYPE_FROM_TITLE.getOrDefault(displayName, "kills");

                int index = i;
                List<RankingService.TopPlayerData> topPlayers = rankingService.getTopPlayers(statType, 10, RankPeriod.ALLTIME);
                if (index < topPlayers.size()) {
                    openPlayerStats(player, topPlayers.get(index).playerId());
                }
                return;
            }
        }
    }

    /**
     * 处理玩家详情菜单点击
     */
    private void handlePlayerStatsClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();

        switch (slot) {
            case 37 -> openTop10(player, "kills");
            case 38 -> openTop10(player, "deaths");
            case 39 -> openTop10(player, "playtime");
            case 40 -> openTop10(player, "kdratio");
            case 42 -> openTop10(player, "kda");
            case 44 -> openMainMenu(player);
        }
    }

    // ==================== 辅助方法 ====================

    private void fillBorder(Inventory gui, Material borderMaterial) {
        ItemStack border = new ItemStack(borderMaterial);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            border.setItemMeta(meta);
        }

        // 填充边框（第一行和最后一行，以及左右两侧）
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(gui.getSize() - 9 + i, border);
        }
        for (int i = 1; i < (gui.getSize() / 9) - 1; i++) {
            gui.setItem(i * 9, border);
            gui.setItem(i * 9 + 8, border);
        }
    }

    private void setMenuItem(Inventory gui, int slot, Material material, String name, String... lore) {
        ItemStack item = createItem(material, name, Arrays.asList(lore));
        gui.setItem(slot, item);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayerHead(UUID playerId, String name, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = head.getItemMeta();
        if (!(baseMeta instanceof org.bukkit.inventory.meta.SkullMeta meta)) {
            // 不是头颅类型，返回普通物品
            return createItem(Material.PLAYER_HEAD, name, lore);
        }

        meta.setDisplayName(name);
        meta.setLore(lore);

        // 设置玩家头颅的皮肤
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        if (offlinePlayer != null && offlinePlayer.getName() != null) {
            try {
                // Paper 1.20.5+: 使用 PlayerProfile API
                org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(offlinePlayer.getUniqueId(), offlinePlayer.getName());
                meta.setOwnerProfile(profile);
            } catch (Exception e) {
                // 回退：直接设置持久化的玩家资料
                meta.setOwningPlayer(offlinePlayer);
            }
        }

        head.setItemMeta(meta);
        return head;
    }

    private String getPlayerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() != null ? player.getName() : playerId.toString().substring(0, 8);
    }

    private String formatStatValue(String statType, long value) {
        return switch (statType) {
            case "kills" -> value + " 击杀";
            case "deaths" -> value + " 死亡";
            case "playtime" -> formatDuration(value);
            case "kdratio" -> String.format("%.2f", value / 100.0); // K/D值存储时乘了100
            default -> String.valueOf(value);
        };
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 24) {
            long days = hours / 24;
            hours = hours % 24;
            return days + " 天 " + hours + " 小时";
        }
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分钟";
        }
        return minutes + " 分钟";
    }
}
