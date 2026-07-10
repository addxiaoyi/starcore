package dev.starcore.starcore.social.gui;

import dev.starcore.starcore.social.friend.FriendService;
import dev.starcore.starcore.social.simulation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 社交排行榜 GUI
 * 整合影响力排行榜、好友排行榜、社交活跃度排行榜
 */
public final class SocialLeaderboardGui implements InventoryHolder {
    private static final int SIZE = 54;
    private static final String GUI_TITLE = "§6§l社交排行榜";

    private final Player player;
    private final SocialInfluenceService influenceService;
    private final InfluenceLeaderboardService leaderboardService;
    private final FriendRecommendationService recommendationService;
    private final FriendService friendService;
    private final RelationshipNetwork relationshipNetwork;

    private final Inventory inventory;
    private LeaderboardType currentType = LeaderboardType.INFLUENCE;
    private LeaderboardPeriod currentPeriod = LeaderboardPeriod.ALLTIME;

    public SocialLeaderboardGui(Player player, SocialInfluenceService influenceService,
                               InfluenceLeaderboardService leaderboardService,
                               FriendRecommendationService recommendationService,
                               FriendService friendService,
                               RelationshipNetwork relationshipNetwork) {
        this.player = player;
        this.influenceService = influenceService;
        this.leaderboardService = leaderboardService;
        this.recommendationService = recommendationService;
        this.friendService = friendService;
        this.relationshipNetwork = relationshipNetwork;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("社交排行榜", NamedTextColor.GOLD));
        buildMenu();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        // 标题
        inventory.setItem(4, createTitleItem());

        // 第一行：排行榜类型选择
        inventory.setItem(10, createLeaderboardTypeItem(LeaderboardType.INFLUENCE, "影响力榜", "查看影响力排行"));
        inventory.setItem(11, createLeaderboardTypeItem(LeaderboardType.ACTIVITY, "活跃榜", "查看社交活跃排行"));
        inventory.setItem(12, createLeaderboardTypeItem(LeaderboardType.FRIENDS, "好友榜", "查看好友数量排行"));
        inventory.setItem(14, createLeaderboardTypeItem(LeaderboardType.REPUTATION, "声望榜", "查看声望排行"));
        inventory.setItem(15, createLeaderboardTypeItem(LeaderboardType.RELATIONSHIP, "社交榜", "查看社交关系排行"));

        // 第二行：时间周期选择（仅影响力榜需要）
        inventory.setItem(19, createPeriodItem(LeaderboardPeriod.DAILY, "今日"));
        inventory.setItem(20, createPeriodItem(LeaderboardPeriod.WEEKLY, "本周"));
        inventory.setItem(21, createPeriodItem(LeaderboardPeriod.MONTHLY, "本月"));
        inventory.setItem(22, createPeriodItem(LeaderboardPeriod.ALLTIME, "全部"));

        // 第三行：显示我的排名
        inventory.setItem(25, createMyRankItem());

        // 第四行开始：显示排行榜内容
        displayLeaderboard();

        // 底部导航
        inventory.setItem(49, createRefreshButton());
        inventory.setItem(53, createBackButton());
    }

    /**
     * 显示当前排行榜
     */
    private void displayLeaderboard() {
        List<LeaderboardEntry> entries = getCurrentLeaderboard();

        int slot = 28;
        int count = 0;

        for (LeaderboardEntry entry : entries) {
            if (slot > 44 || count >= 14) break;
            if (slot == 36) slot = 45; // 跳过底行中间位置
            if (slot >= 45 && slot < 53) slot = 53;

            inventory.setItem(slot++, createLeaderboardEntryItem(entry));
            count++;
        }

        // 如果没有数据，显示提示
        if (entries.isEmpty()) {
            inventory.setItem(31, createNoDataItem());
        }
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("社交排行榜", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前类型: " + currentType.getDisplayName(), NamedTextColor.YELLOW));
        if (currentType == LeaderboardType.INFLUENCE) {
            lore.add(Component.text("当前周期: " + currentPeriod.getDisplayName(), NamedTextColor.YELLOW));
        }
        lore.add(Component.text(""));
        lore.add(Component.text("查看各维度排行榜", NamedTextColor.GRAY));
        lore.add(Component.text("发现社交达人！", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLeaderboardTypeItem(LeaderboardType type, String name, String desc) {
        Material material = switch (type) {
            case INFLUENCE -> Material.BEACON;
            case ACTIVITY -> Material.DIAMOND;
            case FRIENDS -> Material.PLAYER_HEAD;
            case REPUTATION -> Material.GOLD_INGOT;
            case RELATIONSHIP -> Material.EMERALD;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean isSelected = currentType == type;
        meta.displayName(Component.text(name, isSelected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(desc, NamedTextColor.GRAY));
        lore.add(Component.text(""));

        if (isSelected) {
            lore.add(Component.text("[当前选中]", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("点击切换", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPeriodItem(LeaderboardPeriod period, String name) {
        Material material = switch (period) {
            case DAILY -> Material.SUNFLOWER;
            case WEEKLY -> Material.CLOCK;
            case MONTHLY -> Material.MAP;
            case ALLTIME -> Material.NETHER_STAR;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean isSelected = currentPeriod == period;
        meta.displayName(Component.text(name, isSelected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(period.getDisplayName() + "排行", NamedTextColor.GRAY));

        // 显示当前第一名
        if (currentType == LeaderboardType.INFLUENCE) {
            List<InfluenceLeaderboardEntry> top = leaderboardService.getTopPlayers(period, 1);
            if (!top.isEmpty()) {
                lore.add(Component.text(""));
                lore.add(Component.text("第一: " + top.get(0).playerName(), NamedTextColor.GOLD));
            }
        }

        if (isSelected) {
            lore.add(Component.text(""));
            lore.add(Component.text("[当前选中]", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text(""));
            lore.add(Component.text("点击切换", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMyRankItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);

            skullMeta.displayName(Component.text("我的排名", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("=== 我的排名 ===", NamedTextColor.GOLD));

            // 各榜单排名
            int influenceRank = leaderboardService.getPlayerRank(player.getUniqueId(),
                    currentType == LeaderboardType.INFLUENCE ? currentPeriod : LeaderboardPeriod.ALLTIME);
            lore.add(Component.text("影响力: " + (influenceRank > 0 ? "#" + influenceRank : "未上榜"),
                    NamedTextColor.GRAY));

            // 我的影响力值
            int influence = influenceService.getInfluence(player.getUniqueId());
            lore.add(Component.text("影响力值: " + influence, NamedTextColor.YELLOW));

            // 社会地位
            var status = influenceService.getStatus(player.getUniqueId());
            lore.add(Component.text("社会地位: " + status.getColor() + status.getName(), NamedTextColor.GRAY));

            lore.add(Component.text(""));
            lore.add(Component.text("点击查看详细信息", NamedTextColor.GREEN));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack createLeaderboardEntryItem(LeaderboardEntry entry) {
        Material material = switch (entry.rank()) {
            case 1 -> Material.GOLD_INGOT;
            case 2 -> Material.IRON_INGOT;
            case 3 -> Material.COPPER_INGOT;
            default -> Material.PAPER;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String rankStr = switch (entry.rank()) {
            case 1 -> "🥇 第1名";
            case 2 -> "🥈 第2名";
            case 3 -> "🥉 第3名";
            default -> "#" + entry.rank();
        };

        meta.displayName(Component.text(rankStr + " " + entry.playerName(),
                entry.rank() <= 3 ? NamedTextColor.GOLD : NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("数值: " + entry.value(), NamedTextColor.YELLOW));

        if (entry.change() != 0) {
            String changeStr = entry.change() > 0 ? "▲ +" + entry.change() : "▼ " + entry.change();
            lore.add(Component.text("变化: " + changeStr,
                    entry.change() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        }

        if (entry.description() != null) {
            lore.add(Component.text(entry.description(), NamedTextColor.GRAY));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("点击查看详情", NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNoDataItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("暂无数据", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("当前排行榜暂无数据", NamedTextColor.GRAY));
        lore.add(Component.text("可能是数据尚未收录", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshButton() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("刷新排行", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击刷新排行榜数据", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("返回社交中心", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击返回上一级", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 数据获取 ====================

    private List<LeaderboardEntry> getCurrentLeaderboard() {
        return switch (currentType) {
            case INFLUENCE -> getInfluenceLeaderboard();
            case ACTIVITY -> getActivityLeaderboard();
            case FRIENDS -> getFriendsLeaderboard();
            case REPUTATION -> getReputationLeaderboard();
            case RELATIONSHIP -> getRelationshipLeaderboard();
        };
    }

    private List<LeaderboardEntry> getInfluenceLeaderboard() {
        List<LeaderboardEntry> entries = new ArrayList<>();
        List<InfluenceLeaderboardEntry> leaderboard = leaderboardService.getLeaderboard(currentPeriod);

        for (int i = 0; i < Math.min(50, leaderboard.size()); i++) {
            InfluenceLeaderboardEntry e = leaderboard.get(i);
            entries.add(new LeaderboardEntry(
                    e.rank(),
                    e.playerId(),
                    e.playerName(),
                    e.influence(),
                    e.change(),
                    null
            ));
        }

        return entries;
    }

    private List<LeaderboardEntry> getActivityLeaderboard() {
        // 活跃度排行榜：根据玩家互动频率、社交活动参与度计算
        List<LeaderboardEntry> entries = new ArrayList<>();

        // 获取所有玩家的活跃度数据
        Map<UUID, Integer> activityScores = calculateAllPlayersActivity();

        // 排序并取前50名
        List<Map.Entry<UUID, Integer>> sorted = activityScores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .limit(50)
            .toList();

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<UUID, Integer> entry = sorted.get(i);
            String playerName = getPlayerName(entry.getKey());
            entries.add(new LeaderboardEntry(
                i + 1,
                entry.getKey(),
                playerName,
                entry.getValue(),
                0,
                "社交活跃度"
            ));
        }

        return entries;
    }

    /**
     * 计算所有玩家的活跃度分数
     */
    private Map<UUID, Integer> calculateAllPlayersActivity() {
        Map<UUID, Integer> scores = new HashMap<>();

        // 通过影响力服务获取所有玩家数据
        // 活跃度 = 好友数 + 社交圈大小 + 影响力变化
        if (friendService != null) {
            for (UUID playerId : getAllPlayerIds()) {
                int friendCount = friendService.getFriendCount(playerId);
                int socialCircleSize = 0;
                if (relationshipNetwork != null) {
                    socialCircleSize = relationshipNetwork.getSocialCircle(playerId, 10).size();
                }
                // 影响力服务提供的活跃度数据
                int influenceBonus = 0;
                if (influenceService != null) {
                    influenceBonus = (int) influenceService.getInfluence(playerId);
                }
                // 活跃度 = 好友数 * 2 + 社交圈 * 1 + 影响力 / 10
                scores.put(playerId, friendCount * 2 + socialCircleSize + influenceBonus / 10);
            }
        }

        return scores;
    }

    private List<LeaderboardEntry> getFriendsLeaderboard() {
        // 好友数量排行榜：遍历所有玩家并排序
        List<LeaderboardEntry> entries = new ArrayList<>();

        if (friendService != null) {
            // 获取所有玩家的好友数量
            List<Map.Entry<UUID, Integer>> sorted = getAllPlayerIds().stream()
                .map(id -> Map.entry(id, friendService.getFriendCount(id)))
                .sorted(java.util.Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(50)
                .toList();

            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<UUID, Integer> entry = sorted.get(i);
                String playerName = getPlayerName(entry.getKey());
                entries.add(new LeaderboardEntry(
                    i + 1,
                    entry.getKey(),
                    playerName,
                    entry.getValue(),
                    0,
                    "好友数量"
                ));
            }
        }

        return entries;
    }

    private List<LeaderboardEntry> getReputationLeaderboard() {
        // 声望排行榜：使用影响力作为综合声望指标
        List<LeaderboardEntry> entries = new ArrayList<>();

        if (influenceService != null) {
            // 获取所有玩家的声望数据（影响力作为综合声望）
            List<Map.Entry<UUID, Integer>> sorted = getAllPlayerIds().stream()
                .map(id -> Map.entry(id, (int) Math.min(influenceService.getInfluence(id), Integer.MAX_VALUE)))
                .sorted(java.util.Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(50)
                .toList();

            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<UUID, Integer> entry = sorted.get(i);
                String playerName = getPlayerName(entry.getKey());
                entries.add(new LeaderboardEntry(
                    i + 1,
                    entry.getKey(),
                    playerName,
                    entry.getValue(),
                    0,
                    "综合声望值"
                ));
            }
        }

        return entries;
    }

    private List<LeaderboardEntry> getRelationshipLeaderboard() {
        // 社交关系排行榜：基于社交圈覆盖人数
        List<LeaderboardEntry> entries = new ArrayList<>();

        if (relationshipNetwork != null) {
            List<Map.Entry<UUID, Integer>> sorted = getAllPlayerIds().stream()
                .map(id -> Map.entry(id, relationshipNetwork.getSocialCircle(id, 10).size()))
                .sorted(java.util.Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(50)
                .toList();

            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<UUID, Integer> entry = sorted.get(i);
                String playerName = getPlayerName(entry.getKey());
                entries.add(new LeaderboardEntry(
                    i + 1,
                    entry.getKey(),
                    playerName,
                    entry.getValue(),
                    0,
                    "社交圈覆盖人数"
                ));
            }
        }

        return entries;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取所有已知玩家的 UUID 列表（包括在线和离线）
     */
    private Set<UUID> getAllPlayerIds() {
        Set<UUID> allPlayers = new HashSet<>();

        // 添加在线玩家
        Bukkit.getOnlinePlayers().forEach(p -> allPlayers.add(p.getUniqueId()));

        // 尝试从各服务获取历史玩家
        if (friendService != null) {
            // FriendService 使用 ConcurrentHashMap，keySet() 可以获取所有玩家
            try {
                var friendships = (java.util.Map<?, ?>) friendService.getClass()
                    .getDeclaredField("friendships")
                    .get(friendService);
                if (friendships != null) {
                    friendships.keySet().forEach(k -> {
                        if (k instanceof UUID) {
                            allPlayers.add((UUID) k);
                        }
                    });
                }
            } catch (Exception ignored) {}
        }

        return allPlayers;
    }

    /**
     * 获取玩家名称（支持离线玩家）
     */
    private String getPlayerName(UUID playerId) {
        // 首先检查在线玩家
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }

        // 检查离线玩家
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        if (offline.hasPlayedBefore()) {
            return offline.getName();
        }

        // 最后的备选方案：显示 UUID 前8位
        return playerId.toString().substring(0, 8);
    }

    /**
     * 获取按钮对应的操作
     */
    public static LeaderboardAction getActionFromSlot(int slot) {
        return switch (slot) {
            // 排行榜类型
            case 10 -> LeaderboardAction.INFLUENCE;
            case 11 -> LeaderboardAction.ACTIVITY;
            case 12 -> LeaderboardAction.FRIENDS;
            case 14 -> LeaderboardAction.REPUTATION;
            case 15 -> LeaderboardAction.RELATIONSHIP;
            // 时间周期
            case 19 -> LeaderboardAction.DAILY;
            case 20 -> LeaderboardAction.WEEKLY;
            case 21 -> LeaderboardAction.MONTHLY;
            case 22 -> LeaderboardAction.ALLTIME;
            // 排行榜条目 (28-44, 45-53)
            case 28, 29, 30, 31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 42, 43, 44,
                 45, 46, 47, 48, 50, 51, 52, 53 -> LeaderboardAction.VIEW_ENTRY;
            // 功能按钮
            case 49 -> LeaderboardAction.REFRESH;
            default -> LeaderboardAction.NONE;
        };
    }

    /**
     * 设置当前排行榜类型
     */
    public void setCurrentType(LeaderboardType type) {
        this.currentType = type;
        rebuildMenu();
    }

    /**
     * 设置当前时间周期
     */
    public void setCurrentPeriod(LeaderboardPeriod period) {
        this.currentPeriod = period;
        rebuildMenu();
    }

    /**
     * 重新构建菜单
     */
    public void rebuildMenu() {
        inventory.clear();
        buildMenu();
    }

    // ==================== 内部类 ====================

    /**
     * 排行榜条目
     */
    public record LeaderboardEntry(
            int rank,
            UUID playerId,
            String playerName,
            int value,
            int change,
            String description
    ) {}

    /**
     * 排行榜类型
     */
    public enum LeaderboardType {
        INFLUENCE("影响力榜"),
        ACTIVITY("活跃榜"),
        FRIENDS("好友榜"),
        REPUTATION("声望榜"),
        RELATIONSHIP("社交榜");

        private final String displayName;

        LeaderboardType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 排行榜动作
     */
    public enum LeaderboardAction {
        NONE,
        INFLUENCE,
        ACTIVITY,
        FRIENDS,
        REPUTATION,
        RELATIONSHIP,
        DAILY,
        WEEKLY,
        MONTHLY,
        ALLTIME,
        VIEW_ENTRY,
        REFRESH
    }
}
