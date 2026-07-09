package dev.starcore.starcore.pvp.killstreak;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连杀服务
 * D-137: 已知问题：玩家死亡后立即清零 killstreak，但 PvPStats 已写 death。
 * 建议：单播 KillStreakService.recordEvent 由 PvPStatsService 触发实现单一数据源。
 */
public final class KillStreakService {
    // 玩家连杀数（玩家UUID -> 连杀数）
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();

    // 玩家最高连杀（玩家UUID -> 最高连杀数）
    private final Map<UUID, Integer> bestStreaks = new ConcurrentHashMap<>();

    // 连杀里程碑配置（从配置文件读取）
    private List<MilestoneConfig> milestoneConfigs = new ArrayList<>();

    // 终结奖励配置
    private VengeanceConfig vengeanceConfig = new VengeanceConfig(true, 50, 10);

    // 插件和数据文件
    private org.bukkit.plugin.Plugin plugin;
    private File dataFile;

    /**
     * 里程碑配置
     */
    public static class MilestoneConfig {
        public final int killCount;
        public final String displayName;
        public final int reward;
        public final boolean broadcast;

        public MilestoneConfig(int killCount, String displayName, int reward, boolean broadcast) {
            this.killCount = killCount;
            this.displayName = displayName;
            this.reward = reward;
            this.broadcast = broadcast;
        }
    }

    /**
     * 终结奖励配置
     */
    public static class VengeanceConfig {
        public final boolean enabled;
        public final int base;
        public final int perStreak;

        public VengeanceConfig(boolean enabled, int base, int perStreak) {
            this.enabled = enabled;
            this.base = base;
            this.perStreak = perStreak;
        }
    }

    // 默认里程碑
    private static final List<MilestoneConfig> DEFAULT_MILESTONES = Arrays.asList(
        new MilestoneConfig(3, "大杀特杀", 100, true),
        new MilestoneConfig(5, "暴走", 200, true),
        new MilestoneConfig(10, "主宰", 500, true),
        new MilestoneConfig(15, "无人能挡", 1000, true),
        new MilestoneConfig(20, "如同天神", 2000, true),
        new MilestoneConfig(25, "传说", 5000, true),
        new MilestoneConfig(30, "不朽", 10000, true)
    );

    private static final VengeanceConfig DEFAULT_VENGEANCE = new VengeanceConfig(true, 50, 10);

    public KillStreakService() {
        milestoneConfigs = new ArrayList<>(DEFAULT_MILESTONES);
        vengeanceConfig = DEFAULT_VENGEANCE;
    }

    /**
     * 初始化插件引用（用于数据持久化）
     */
    public void initialize(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
        if (plugin != null) {
            File dataFolder = new File(plugin.getDataFolder(), "pvp_data");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            this.dataFile = new File(dataFolder, "killstreaks.json");
            loadData();
        }
    }

    /**
     * 加载数据
     */
    public void loadData() {
        if (dataFile == null || !dataFile.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8)) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                content.append(buffer, 0, len);
            }

            // 简单解析 JSON
            String json = content.toString();
            // 解析 killStreaks
            parseAndLoadData(json);
        } catch (IOException e) {
            if (plugin != null) {
                plugin.getLogger().warning("无法加载连杀数据: " + e.getMessage());
            }
        }
    }

    private void parseAndLoadData(String json) {
        try {
            // 简单解析 {"killStreaks": {...}, "bestStreaks": {...}}
            int ksStart = json.indexOf("\"killStreaks\":{");
            int bsStart = json.indexOf("\"bestStreaks\":{");

            if (ksStart >= 0) {
                int ksEnd = json.indexOf("}", ksStart);
                String ksSection = json.substring(ksStart + 15, ksEnd);
                parseStreakMap(ksSection, killStreaks);
            }

            if (bsStart >= 0) {
                int bsEnd = json.indexOf("}", bsStart);
                String bsSection = json.substring(bsStart + 14, bsEnd);
                parseStreakMap(bsSection, bestStreaks);
            }
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("解析连杀数据失败: " + e.getMessage());
            }
        }
    }

    private void parseStreakMap(String section, Map<UUID, Integer> target) {
        // 解析 {"uuid1": value1, "uuid2": value2, ...}
        String[] pairs = section.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim();
                try {
                    UUID uuid = UUID.fromString(key);
                    int streak = Integer.parseInt(value);
                    target.put(uuid, streak);
                } catch (Exception e) {
                    if (plugin != null) {
                        plugin.getLogger().warning("解析连杀数据条目失败: " + e.getMessage());
                    }
                }
                        // 静默跳过，保持数据兼容
            }
        }
    }

    /**
     * 保存数据
     */
    public void saveData() {
        if (dataFile == null) return;

        try {
            StringBuilder json = new StringBuilder();
            json.append("{\n");

            // 保存 killStreaks
            json.append("  \"killStreaks\":{");
            boolean first = true;
            for (Map.Entry<UUID, Integer> entry : killStreaks.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey().toString()).append("\":").append(entry.getValue());
                first = false;
            }
            json.append("},\n");

            // 保存 bestStreaks
            json.append("  \"bestStreaks\":{");
            first = true;
            for (Map.Entry<UUID, Integer> entry : bestStreaks.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey().toString()).append("\":").append(entry.getValue());
                first = false;
            }
            json.append("}\n");

            json.append("}");

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            if (plugin != null) {
                plugin.getLogger().warning("无法保存连杀数据: " + e.getMessage());
            }
        }
    }

    /**
     * 初始化配置
     */
    public void loadConfig(FileConfiguration config) {
        if (config == null) {
            milestoneConfigs = new ArrayList<>(DEFAULT_MILESTONES);
            vengeanceConfig = DEFAULT_VENGEANCE;
            return;
        }

        // 加载里程碑配置
        List<?> milestoneList = config.getList("killstreak.milestones", DEFAULT_MILESTONES);
        milestoneConfigs = new ArrayList<>();

        for (Object obj : milestoneList) {
            if (obj instanceof Map<?, ?> map) {
                Object killsObj = map.get("kills");
                Object nameObj = map.get("name");
                Object rewardObj = map.get("reward");
                Object broadcastObj = map.get("broadcast");

                int kills = killsObj instanceof Number ? ((Number) killsObj).intValue() : 0;
                String name = nameObj != null ? nameObj.toString() : "里程碑";
                int reward = rewardObj instanceof Number ? ((Number) rewardObj).intValue() : 0;
                boolean broadcast = Boolean.TRUE.equals(broadcastObj);

                milestoneConfigs.add(new MilestoneConfig(kills, name, reward, broadcast));
            }
        }

        // 按击杀数排序
        milestoneConfigs.sort(Comparator.comparingInt(m -> m.killCount));

        // 加载终结奖励配置
        boolean vEnabled = config.getBoolean("killstreak.vengeance-reward.enabled", true);
        int vBase = config.getInt("killstreak.vengeance-reward.base", 50);
        int vPerStreak = config.getInt("killstreak.vengeance-reward.per-streak", 10);
        vengeanceConfig = new VengeanceConfig(vEnabled, vBase, vPerStreak);
    }

    /**
     * 增加连杀
     */
    public KillStreakResult addKill(UUID playerId) {
        int currentStreak = killStreaks.compute(playerId, (k, v) -> v == null ? 1 : v + 1);

        // 更新最高连杀
        int bestStreak = bestStreaks.getOrDefault(playerId, 0);
        if (currentStreak > bestStreak) {
            bestStreaks.put(playerId, currentStreak);
        }

        // 检查是否达到里程碑
        Milestone milestone = checkMilestone(currentStreak);

        return new KillStreakResult(
            currentStreak,
            milestone,
            isNewRecord(playerId, currentStreak)
        );
    }

    /**
     * 重置连杀
     */
    public int resetKillStreak(UUID playerId) {
        Integer streak = killStreaks.remove(playerId);
        return streak != null ? streak : 0;
    }

    /**
     * 获取当前连杀
     */
    public int getKillStreak(UUID playerId) {
        return killStreaks.getOrDefault(playerId, 0);
    }

    /**
     * 获取最高连杀
     */
    public int getBestStreak(UUID playerId) {
        return bestStreaks.getOrDefault(playerId, 0);
    }

    /**
     * 检查里程碑
     */
    private Milestone checkMilestone(int streak) {
        for (MilestoneConfig config : milestoneConfigs) {
            if (streak == config.killCount) {
                return new Milestone(
                    config.killCount,
                    config.displayName,
                    config.reward
                );
            }
        }
        return null;
    }

    /**
     * 检查是否是新纪录
     */
    private boolean isNewRecord(UUID playerId, int currentStreak) {
        int bestStreak = bestStreaks.getOrDefault(playerId, 0);
        return currentStreak > bestStreak;
    }

    /**
     * 获取连杀排行榜（当前连杀）
     */
    public Map<UUID, Integer> getCurrentStreakLeaderboard(int limit) {
        return killStreaks.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .limit(limit)
            .collect(ConcurrentHashMap::new,
                (m, e) -> m.put(e.getKey(), e.getValue()),
                ConcurrentHashMap::putAll);
    }

    /**
     * 获取最高连杀排行榜
     */
    public Map<UUID, Integer> getBestStreakLeaderboard(int limit) {
        return bestStreaks.entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
            .limit(limit)
            .collect(ConcurrentHashMap::new,
                (m, e) -> m.put(e.getKey(), e.getValue()),
                ConcurrentHashMap::putAll);
    }

    /**
     * 计算终结连杀奖励
     */
    public int calculateVengeanceReward(int endedStreak) {
        if (!vengeanceConfig.enabled || endedStreak < 3) {
            return 0;
        }
        return vengeanceConfig.base + (endedStreak - 1) * vengeanceConfig.perStreak;
    }

    /**
     * 是否启用终结奖励
     */
    public boolean isVengeanceEnabled() {
        return vengeanceConfig.enabled;
    }

    /**
     * 保存玩家连杀数据到持久化存储
     */
    public void savePlayerData(UUID playerId, int currentStreak, int bestStreak) {
        // 更新内存数据
        if (currentStreak > 0) {
            killStreaks.put(playerId, currentStreak);
        }
        if (bestStreak > this.bestStreaks.getOrDefault(playerId, 0)) {
            bestStreaks.put(playerId, bestStreak);
        }
    }

    /**
     * 获取里程碑配置列表
     */
    public List<MilestoneConfig> getMilestoneConfigs() {
        return Collections.unmodifiableList(milestoneConfigs);
    }

    /**
     * 连杀里程碑
     */
    public record Milestone(
        int killCount,
        String displayName,
        int reward
    ) {
        public static Milestone KILLING_SPREE = new Milestone(3, "大杀特杀", 100);
        public static Milestone RAMPAGE = new Milestone(5, "暴走", 200);
        public static Milestone DOMINATING = new Milestone(10, "主宰", 500);
        public static Milestone UNSTOPPABLE = new Milestone(15, "无人能挡", 1000);
        public static Milestone GODLIKE = new Milestone(20, "如同天神", 2000);
        public static Milestone LEGENDARY = new Milestone(25, "传说", 5000);
        public static Milestone IMMORTAL = new Milestone(30, "不朽", 10000);
    }

    /**
     * 连杀结果
     */
    public record KillStreakResult(
        int currentStreak,
        Milestone milestone,
        boolean isNewRecord
    ) {}
}
