package dev.starcore.starcore.social.simulation;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 八卦传播服务
 *
 * 管理社交八卦和流言:
 * - 八卦创建
 * - 八卦传播 (基于社交关系)
 * - 八卦真实性
 * - 八卦影响声誉
 */
public class GossipService {

    private final JavaPlugin plugin;
    private final RelationshipNetwork relationshipNetwork;
    private final ReputationService reputationService;
    private final Map<String, Gossip> gossips = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerGossipHistory = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerSeenGossips = new ConcurrentHashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public GossipService(JavaPlugin plugin, RelationshipNetwork relationshipNetwork, ReputationService reputationService) {
        this.plugin = plugin;
        this.relationshipNetwork = relationshipNetwork;
        this.reputationService = reputationService;
        loadData();
    }

    /**
     * 创建八卦
     */
    public String createGossip(UUID creator, String content, GossipTopic topic) {
        String id = UUID.randomUUID().toString();
        double baseCredibility = calculateCredibility(creator, topic);

        Gossip gossip = new Gossip(
            id, creator, content, topic,
            System.currentTimeMillis(),
            baseCredibility,
            new HashSet<>(Set.of(creator)),
            1,
            0,
            new ArrayList<>()
        );
        gossips.put(id, gossip);

        // 记录创建者看到
        playerGossipHistory.computeIfAbsent(creator, k -> new HashSet<>()).add(id);

        // 推送给创建者的社交圈
        spreadGossip(id, creator, 1);

        return id;
    }

    /**
     * 计算八卦可信度
     */
    private double calculateCredibility(UUID creator, GossipTopic topic) {
        double base = 0.5;

        // 高声望创建者更可信
        int rep = reputationService.getReputation(creator);
        base += Math.min(0.3, rep / 1000.0);

        // 特定话题更可信
        base += topic.credibilityBonus();

        // 社交圈子大更可信
        int friends = relationshipNetwork.getFriends(creator).size();
        base += Math.min(0.1, friends * 0.01);

        return Math.min(0.95, Math.max(0.1, base));
    }

    /**
     * 传播八卦
     */
    public void spreadGossip(String gossipId, UUID spreader, int depth) {
        Gossip gossip = gossips.get(gossipId);
        if (gossip == null) return;

        // 检查深度限制
        if (depth > gossip.maxDepth()) return;

        // 找到传播者的社交圈
        Set<UUID> socialCircle = relationshipNetwork.getSocialCircle(spreader, 20);
        for (UUID target : socialCircle) {
            if (gossip.seenBy().contains(target)) continue;

            // 基于关系强度决定是否传播
            var rel = relationshipNetwork.getRelationship(spreader, target);
            double chance = rel != null ? Math.abs(rel.strength()) / 100.0 : 0.2;

            if (ThreadLocalRandom.current().nextDouble() < chance * gossip.credibility()) {
                // 传播八卦
                Set<UUID> newSeenBy = new HashSet<>(gossip.seenBy());
                newSeenBy.add(target);

                int newSpreadCount = gossip.spreadCount() + 1;

                Gossip updated = new Gossip(
                    gossip.id(), gossip.creator(), gossip.content(),
                    gossip.topic(), gossip.timestamp(),
                    gossip.credibility(), newSeenBy,
                    newSpreadCount, gossip.maxDepth(),
                    gossip.comments()
                );
                gossips.put(gossipId, updated);

                // 记录历史
                playerGossipHistory.computeIfAbsent(target, k -> new HashSet<>()).add(gossipId);

                // 继续传播
                spreadGossip(gossipId, target, depth + 1);

                // 通知目标
                notifyGossip(target, gossip);

                // 标记已读
                markGossipSeen(target, gossipId);

                // 如果传播次数达到阈值，应用声誉影响
                if (newSpreadCount >= 3 && newSpreadCount % 3 == 0) {
                    applyReputationImpact(gossipId);
                }
            }
        }
    }

    /**
     * 通知玩家八卦
     */
    private void notifyGossip(UUID playerId, Gossip gossip) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        player.sendMessage("§5§l【八卦】§d" + gossip.topic().emoji() + " " + gossip.content());
        player.sendMessage("§7可信度: " + getCredibilityBar(gossip.credibility()) + " §7传播: " + gossip.spreadCount() + "次");
    }

    /**
     * 获取可信度条
     */
    private String getCredibilityBar(double credibility) {
        int bars = (int) (credibility * 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < bars ? "§a|" : "§7|");
        }
        return sb.toString();
    }

    /**
     * 评论八卦
     */
    public void commentOnGossip(UUID commenter, String gossipId, String comment) {
        Gossip gossip = gossips.get(gossipId);
        if (gossip == null) return;

        List<String> newComments = new ArrayList<>(gossip.comments());
        newComments.add(commenter.toString() + ": " + comment);

        Gossip updated = new Gossip(
            gossip.id(), gossip.creator(), gossip.content(),
            gossip.topic(), gossip.timestamp(),
            gossip.credibility(), gossip.seenBy(),
            gossip.spreadCount(), gossip.maxDepth(),
            newComments
        );
        gossips.put(gossipId, updated);
    }

    /**
     * 获取玩家的八卦历史
     */
    public List<Gossip> getPlayerGossipHistory(UUID playerId) {
        Set<String> ids = playerGossipHistory.getOrDefault(playerId, Set.of());
        return ids.stream()
            .map(gossips::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingLong(Gossip::timestamp).reversed())
            .limit(20)
            .toList();
    }

    /**
     * 获取热门八卦
     */
    public List<Gossip> getTrendingGossip() {
        return gossips.values().stream()
            .sorted((a, b) -> Integer.compare(b.spreadCount(), a.spreadCount()))
            .limit(10)
            .toList();
    }

    /**
     * 更新八卦可信度变化
     */
    public void updateCredibility(String gossipId, double change) {
        Gossip gossip = gossips.get(gossipId);
        if (gossip == null) return;

        double newCredibility = Math.min(1.0, Math.max(0.1, gossip.credibility() + change));
        Gossip updated = new Gossip(
            gossip.id(), gossip.creator(), gossip.content(),
            gossip.topic(), gossip.timestamp(),
            newCredibility, gossip.seenBy(),
            gossip.spreadCount(), gossip.maxDepth(),
            gossip.comments()
        );
        gossips.put(gossipId, updated);
    }

    // ==================== GossipVerificationService 集成方法 ====================

    /**
     * 根据ID获取八卦 (用于验证服务)
     */
    public Gossip getGossipById(String gossipId) {
        return gossips.get(gossipId);
    }

    /**
     * 获取所有八卦 (用于验证服务)
     */
    public Collection<Gossip> getAllGossips() {
        return gossips.values();
    }

    /**
     * 删除八卦
     */
    public void removeGossip(String gossipId) {
        gossips.remove(gossipId);
    }

    /**
     * 获取八卦数量
     */
    public int getGossipCount() {
        return gossips.size();
    }

    // ==================== 八卦影响声誉系统 ====================

    /**
     * 根据八卦主题计算对创建者的声誉影响
     */
    public void applyReputationImpact(String gossipId) {
        Gossip gossip = gossips.get(gossipId);
        if (gossip == null || reputationService == null) return;

        int impactAmount = calculateReputationImpact(gossip);
        ReputationService.ReputationDimension dimension = getDimensionForTopic(gossip.topic());

        if (impactAmount != 0) {
            reputationService.modifyReputation(gossip.creator(), impactAmount,
                ReputationService.ReputationReason.CUSTOM).thenAccept(success -> {
                if (success) {
                    String direction = impactAmount > 0 ? "提升" : "下降";
                    Player creator = Bukkit.getPlayer(gossip.creator());
                    if (creator != null) {
                        creator.sendMessage("§5【八卦影响】§d你的八卦传播后，你的声誉" + direction + "了 " + Math.abs(impactAmount) + " 点");
                    }

                    // 通知高可信度八卦的参与者
                    if (gossip.credibility() > 0.7) {
                        StringBuilder message = new StringBuilder();
                        message.append("§5【八卦新闻】§d听说 §e");
                        message.append(getPlayerName(gossip.creator()));
                        message.append(" §d");
                        message.append(gossip.topic().getName());
                        message.append("八卦...");

                        for (UUID seenBy : gossip.seenBy()) {
                            if (!seenBy.equals(gossip.creator())) {
                                Player p = Bukkit.getPlayer(seenBy);
                                if (p != null) {
                                    p.sendMessage(message.toString());
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * 计算八卦对声誉的影响值
     */
    private int calculateReputationImpact(Gossip gossip) {
        int baseImpact = 0;

        // 根据话题类型决定影响
        switch (gossip.topic()) {
            case HEROISM -> baseImpact = 3;      // 英雄事迹 - 正面
            case CELEBRITY -> baseImpact = 2;     // 名人 - 轻微正面
            case TRADING -> baseImpact = 1;      // 交易 - 轻微正面
            case ROMANCE -> baseImpact = 0;      // 爱情 - 中性
            case CULTURE -> baseImpact = 0;      // 文化 - 中性
            case WEALTH -> baseImpact = 0;       // 财富 - 中性
            case POWER -> baseImpact = -1;       // 权力 - 轻微负面
            case POLITICS -> baseImpact = -2;    // 政治 - 负面
            case BATTLE -> baseImpact = -2;      // 战斗 - 负面
            case SCANDAL -> baseImpact = -5;     // 丑闻 - 严重负面
            case TRAGEDY -> baseImpact = -3;     // 悲剧 - 负面
            case MYSTERY -> baseImpact = 0;      // 神秘 - 中性
        }

        // 根据可信度调整
        double credibilityModifier = gossip.credibility() - 0.5; // -0.5 到 +0.5
        int finalImpact = (int) (baseImpact * (1 + credibilityModifier));

        // 根据传播次数调整（传播越多，影响越大）
        int spreadBonus = Math.min(5, gossip.spreadCount() / 2);
        finalImpact += (finalImpact > 0 ? spreadBonus : -spreadBonus);

        return finalImpact;
    }

    /**
     * 获取话题对应的声誉维度
     */
    private ReputationService.ReputationDimension getDimensionForTopic(GossipTopic topic) {
        return switch (topic) {
            case HEROISM, BATTLE -> ReputationService.ReputationDimension.MORAL;
            case TRADING, WEALTH -> ReputationService.ReputationDimension.WEALTH;
            case POLITICS, POWER -> ReputationService.ReputationDimension.CHARISMA;
            case CULTURE, CELEBRITY -> ReputationService.ReputationDimension.CHARISMA;
            default -> ReputationService.ReputationDimension.MORAL;
        };
    }

    /**
     * 获取玩家名称（处理离线玩家）
     */
    private String getPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        return Bukkit.getOfflinePlayer(playerId).getName() != null ?
            Bukkit.getOfflinePlayer(playerId).getName() : playerId.toString().substring(0, 8);
    }

    /**
     * 检查玩家是否已看过某八卦
     */
    public boolean hasSeenGossip(UUID playerId, String gossipId) {
        return playerSeenGossips.getOrDefault(playerId, Set.of()).contains(gossipId);
    }

    /**
     * 标记玩家已看过八卦
     */
    public void markGossipSeen(UUID playerId, String gossipId) {
        playerSeenGossips.computeIfAbsent(playerId, k -> new HashSet<>()).add(gossipId);
    }

    /**
     * 获取玩家看过但未处理的八卦数量
     */
    public int getUnreadGossipCount(UUID playerId) {
        Set<String> seen = playerSeenGossips.getOrDefault(playerId, Set.of());
        Set<String> history = playerGossipHistory.getOrDefault(playerId, Set.of());
        return (int) history.stream().filter(id -> !seen.contains(id)).count();
    }

    // ==================== YAML 持久化方法 ====================

    /**
     * 保存八卦数据到 YAML 文件
     */
    public void saveData() {
        if (plugin == null) return;

        dataFile = new File(plugin.getDataFolder(), "gossip_data.yml");
        dataConfig = new YamlConfiguration();

        try {
            // 保存八卦内容
            int gossipIndex = 0;
            for (Gossip gossip : gossips.values()) {
                String path = "gossips." + gossipIndex;
                dataConfig.set(path + ".id", gossip.id());
                dataConfig.set(path + ".creator", gossip.creator().toString());
                dataConfig.set(path + ".content", gossip.content());
                dataConfig.set(path + ".topic", gossip.topic().name());
                dataConfig.set(path + ".timestamp", gossip.timestamp());
                dataConfig.set(path + ".credibility", gossip.credibility());
                dataConfig.set(path + ".spread_count", gossip.spreadCount());
                dataConfig.set(path + ".max_depth", gossip.maxDepth());
                dataConfig.set(path + ".seen_by", gossip.seenBy().stream().map(UUID::toString).toList());
                dataConfig.set(path + ".comments", gossip.comments());
                gossipIndex++;
            }

            // 保存玩家八卦历史
            int playerIndex = 0;
            for (Map.Entry<UUID, Set<String>> entry : playerGossipHistory.entrySet()) {
                String path = "history." + entry.getKey().toString();
                dataConfig.set(path, new ArrayList<>(entry.getValue()));
                playerIndex++;
            }

            dataConfig.save(dataFile);
            plugin.getLogger().info("八卦数据已保存: " + gossips.size() + " 条八卦, " + playerGossipHistory.size() + " 名玩家历史");
        } catch (IOException e) {
            plugin.getLogger().warning("保存八卦数据失败: " + e.getMessage());
        }
    }

    /**
     * 从 YAML 文件加载八卦数据
     */
    public void loadData() {
        if (plugin == null) return;

        dataFile = new File(plugin.getDataFolder(), "gossip_data.yml");
        if (!dataFile.exists()) {
            plugin.getLogger().info("八卦数据文件不存在");
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        try {
            // 加载八卦内容
            if (dataConfig.contains("gossips")) {
                Map<String, Object> gossipsSection = dataConfig.getConfigurationSection("gossips").getValues(false);
                for (Map.Entry<String, Object> entry : gossipsSection.entrySet()) {
                    String path = "gossips." + entry.getKey();
                    String id = dataConfig.getString(path + ".id");
                    UUID creator = UUID.fromString(dataConfig.getString(path + ".creator", ""));
                    String content = dataConfig.getString(path + ".content", "");
                    String topicStr = dataConfig.getString(path + ".topic", "ROMANCE");
                    long timestamp = dataConfig.getLong(path + ".timestamp", 0);
                    double credibility = dataConfig.getDouble(path + ".credibility", 0.5);
                    int spreadCount = dataConfig.getInt(path + ".spread_count", 1);
                    int maxDepth = dataConfig.getInt(path + ".max_depth", 5);
                    List<String> seenByStr = dataConfig.getStringList(path + ".seen_by");
                    List<String> comments = dataConfig.getStringList(path + ".comments");

                    Set<UUID> seenBy = new HashSet<>();
                    for (String s : seenByStr) {
                        try {
                            seenBy.add(UUID.fromString(s));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("八卦数据中存在无效的UUID格式: " + s);
                        }
                        // 静默跳过，保持数据兼容
                    }

                    GossipTopic topic;
                    try {
                        topic = GossipTopic.valueOf(topicStr);
                    } catch (IllegalArgumentException e) {
                        topic = GossipTopic.ROMANCE;
                    }

                    Gossip gossip = new Gossip(id, creator, content, topic, timestamp,
                        credibility, seenBy, spreadCount, maxDepth, comments);
                    gossips.put(id, gossip);
                }
            }

            // 加载玩家八卦历史
            if (dataConfig.contains("history")) {
                Map<String, Object> historySection = dataConfig.getConfigurationSection("history").getValues(false);
                for (Map.Entry<String, Object> entry : historySection.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    List<String> gossipIds = dataConfig.getStringList("history." + entry.getKey());
                    playerGossipHistory.put(playerId, new HashSet<>(gossipIds));
                }
            }

            plugin.getLogger().info("八卦数据已加载: " + gossips.size() + " 条八卦, " + playerGossipHistory.size() + " 名玩家历史");
        } catch (Exception e) {
            plugin.getLogger().warning("加载八卦数据失败: " + e.getMessage());
        }
    }

    /**
     * 清除所有八卦数据
     */
    public void clearData() {
        gossips.clear();
        playerGossipHistory.clear();
    }

    /**
     * 获取已保存的八卦数量
     */
    public int getSavedGossipCount() {
        return gossips.size();
    }

    /**
     * 清理过期八卦 (超过指定时间的八卦)
     */
    public void cleanupOldGossip(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        gossips.entrySet().removeIf(entry -> entry.getValue().timestamp() < cutoff);
    }

    // ==================== 数据类 ====================

    public record Gossip(
        String id,
        UUID creator,
        String content,
        GossipTopic topic,
        long timestamp,
        double credibility,  // 0.0 - 1.0
        Set<UUID> seenBy,
        int spreadCount,
        int maxDepth,
        List<String> comments
    ) {
        public String getAgeText() {
            long age = System.currentTimeMillis() - timestamp();
            long hours = age / (60 * 60 * 1000);
            if (hours < 1) return "刚刚";
            if (hours < 24) return hours + "小时前";
            return (hours / 24) + "天前";
        }
    }

    public enum GossipTopic {
        ROMANCE("爱情", "§d💕", 0.1),
        WEALTH("财富", "§6💰", 0.0),
        POWER("权力", "§c⚔️", 0.2),
        BATTLE("战斗", "§4🗡️", 0.15),
        POLITICS("政治", "§c👑", 0.25),
        CULTURE("文化", "§b🎭", 0.05),
        SCANDAL("丑闻", "§5🔇", 0.3),
        HEROISM("英雄事迹", "§a✨", 0.1),
        TRAGEDY("悲剧", "§7💀", 0.2),
        MYSTERY("神秘", "§5🔮", 0.15),
        CELEBRITY("名人", "§6⭐", 0.1),
        TRADING("交易", "§e📊", 0.0);

        private final String name;
        private final String emoji;
        private final double credibilityBonus;

        GossipTopic(String name, String emoji, double bonus) {
            this.name = name;
            this.emoji = emoji;
            this.credibilityBonus = bonus;
        }

        public String getName() { return name; }
        public String emoji() { return emoji; }
        public double credibilityBonus() { return credibilityBonus; }
    }
}
