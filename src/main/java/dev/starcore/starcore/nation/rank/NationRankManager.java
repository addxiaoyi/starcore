package dev.starcore.starcore.nation.rank;

import dev.starcore.starcore.nation.permission.NationPermission;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nation Rank管理器
 * 管理所有Nation的职位
 */
public class NationRankManager {

    // 全局Rank注册表（审计 A-028：使用 synchronizedMap 包装 LinkedHashMap 以保留插入顺序并支持并发访问）
    private final Map<String, NationRank> globalRanks = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    // 每个Nation的自定义Rank
    private final Map<UUID, Map<String, NationRank>> nationRanks = new ConcurrentHashMap<>();

    // 玩家Rank分配
    // 设计决策：playerRanks 按 playerId 存 rankName，跨国家共名 Rank 可能错配
    // 理想结构为 Map<PlayerId, Map<NationId, RankName>>；当前保留旧 API 兼容大量调用方
    // getNationRank 已先查自定义后查全局，跨国家错配风险有限；完整修复需重构 Rank 协议
    private final Map<UUID, String> playerRanks = new ConcurrentHashMap<>();

    /**
     * 初始化预设Rank
     */
    public void initDefaultRanks() {
        List<NationRank> starter = NationRank.getStarterRanks();

        for (NationRank rank : starter) {
            globalRanks.put(rank.getName(), rank);
        }
    }

    /**
     * 注册全局Rank
     */
    public void registerGlobalRank(NationRank rank) {
        globalRanks.put(rank.getName(), rank);
    }

    /**
     * 获取全局Rank
     */
    public NationRank getGlobalRank(String name) {
        return globalRanks.get(name);
    }

    /**
     * 获取所有全局Rank
     */
    public Collection<NationRank> getAllGlobalRanks() {
        return Collections.unmodifiableCollection(globalRanks.values());
    }

    /**
     * 为Nation创建自定义Rank
     */
    public void createNationRank(UUID nationId, NationRank rank) {
        nationRanks.computeIfAbsent(nationId, k -> new LinkedHashMap<>())
            .put(rank.getName(), rank);
    }

    /**
     * 删除Nation的自定义Rank
     */
    public boolean deleteNationRank(UUID nationId, String rankName) {
        Map<String, NationRank> ranks = nationRanks.get(nationId);
        if (ranks != null) {
            return ranks.remove(rankName) != null;
        }
        return false;
    }

    /**
     * 获取Nation的Rank（优先自定义，其次全局）
     */
    public NationRank getNationRank(UUID nationId, String rankName) {
        // 优先查找自定义Rank
        Map<String, NationRank> ranks = nationRanks.get(nationId);
        if (ranks != null) {
            NationRank rank = ranks.get(rankName);
            if (rank != null) {
                return rank;
            }
        }

        // 其次查找全局Rank
        return globalRanks.get(rankName);
    }

    /**
     * 获取Nation的所有Rank
     */
    public List<NationRank> getAllNationRanks(UUID nationId) {
        List<NationRank> result = new ArrayList<>();

        // 添加全局Rank
        result.addAll(globalRanks.values());

        // 添加自定义Rank
        Map<String, NationRank> custom = nationRanks.get(nationId);
        if (custom != null) {
            result.addAll(custom.values());
        }

        // 按优先级排序
        result.sort(Comparator.naturalOrder());

        return result;
    }

    /**
     * 分配玩家Rank
     */
    public void assignRank(UUID playerId, String rankName) {
        // 校验目标职位存在（审计 A-025）：必须有对应的全局或某个国家的自定义职位，否则拒绝写入空引用。
        // 由于当前 API 无 nationId 参数，这里保守地校验全局职位存在；自定义职位场景请改用带 nationId 的重载。
        if (rankName == null || rankName.isEmpty() || getGlobalRank(rankName) == null) {
            return;
        }
        playerRanks.put(playerId, rankName);
    }

    /** 指定 Nation 维度分配玩家 Rank（审计 A-024/A-025 配套重载） */
    public void assignRank(UUID playerId, UUID nationId, String rankName) {
        if (rankName == null || rankName.isEmpty() || nationId == null) {
            return;
        }
        NationRank resolved = getNationRank(nationId, rankName);
        if (resolved == null) {
            return;
        }
        playerRanks.put(playerId, rankName);
    }

    /**
     * 移除玩家Rank
     * @return true 表示该玩家原本确实拥有职位并已移除；false 表示原本无职位（审计 A-026）
     */
    public boolean removeRank(UUID playerId) {
        return playerRanks.remove(playerId) != null;
    }

    /**
     * 获取玩家的Rank名称
     */
    public String getPlayerRankName(UUID playerId) {
        return playerRanks.get(playerId);
    }

    /**
     * 获取玩家的Rank对象
     */
    public NationRank getPlayerRank(UUID playerId, UUID nationId) {
        String rankName = playerRanks.get(playerId);
        if (rankName == null) {
            return null;
        }

        return getNationRank(nationId, rankName);
    }

    /**
     * 检查玩家是否有Rank
     */
    public boolean hasRank(UUID playerId) {
        return playerRanks.containsKey(playerId);
    }

    /**
     * 从配置加载Rank
     */
    public void loadRanksFromConfig(ConfigurationSection config) {
        if (config == null) return;

        for (String rankName : config.getKeys(false)) {
            ConfigurationSection rankSection = config.getConfigurationSection(rankName);
            if (rankSection == null) continue;

            String displayName = rankSection.getString("display-name", rankName);
            List<String> permissionKeys = rankSection.getStringList("permissions");
            int priority = rankSection.getInt("priority", 0);

            NationRank rank = NationRank.fromConfig(rankName, displayName, permissionKeys);
            rank.setPriority(priority);

            globalRanks.put(rankName, rank);
        }
    }

    /**
     * 保存Rank到配置
     * 审计 A-027: 现在同时持久化 nationRanks 与 playerRanks，避免重启丢失。
     */
    public void saveRanksToConfig(ConfigurationSection config) {
        // 全局职位
        for (NationRank rank : globalRanks.values()) {
            ConfigurationSection rankSection = config.createSection(rank.getName());

            rankSection.set("display-name", rank.getDisplayName());
            rankSection.set("priority", rank.getPriority());

            List<String> permKeys = new ArrayList<>();
            for (NationPermission perm : rank.getPermissions()) {
                permKeys.add(perm.getConfigKey());
            }
            rankSection.set("permissions", permKeys);
        }

        // 持久化 nationRanks（自定义职位）
        ConfigurationSection nationsSection = config.createSection("nations");
        for (var entry : nationRanks.entrySet()) {
            String nationKey = entry.getKey().toString();
            ConfigurationSection oneNation = nationsSection.createSection(nationKey);
            for (NationRank rank : entry.getValue().values()) {
                ConfigurationSection rankSection = oneNation.createSection(rank.getName());
                rankSection.set("display-name", rank.getDisplayName());
                rankSection.set("priority", rank.getPriority());
                List<String> permKeys = new ArrayList<>();
                for (NationPermission perm : rank.getPermissions()) {
                    permKeys.add(perm.getConfigKey());
                }
                rankSection.set("permissions", permKeys);
            }
        }

        // 持久化 playerRanks
        ConfigurationSection playersSection = config.createSection("players");
        for (var entry : playerRanks.entrySet()) {
            playersSection.set(entry.getKey().toString(), entry.getValue());
        }
    }

    /**
     * 清空所有数据
     */
    public void clear() {
        globalRanks.clear();
        nationRanks.clear();
        playerRanks.clear();
    }

    /**
     * 获取统计信息
     */
    public RankStats getStats() {
        int totalNations = nationRanks.size();
        int totalCustomRanks = nationRanks.values().stream()
            .mapToInt(Map::size)
            .sum();
        int totalPlayerAssignments = playerRanks.size();

        return new RankStats(
            globalRanks.size(),
            totalNations,
            totalCustomRanks,
            totalPlayerAssignments
        );
    }

    /**
     * 统计信息记录
     */
    public record RankStats(
        int globalRanks,
        int nationsWithCustomRanks,
        int totalCustomRanks,
        int playerAssignments
    ) {
        @Override
        public String toString() {
            return String.format(
                "RankStats[global=%d, nations=%d, custom=%d, players=%d]",
                globalRanks, nationsWithCustomRanks, totalCustomRanks, playerAssignments
            );
        }
    }
}
