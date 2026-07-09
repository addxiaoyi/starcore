package dev.starcore.starcore.essentials.baltop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 财富排行榜服务
 * 管理玩家财富排名
 */
public final class BalTopService {
    // 排行榜缓存 排名 -> (UUID, 余额)
    // volatile + 不可变快照：异步 recalculateRankings 整体替换、主线程 GUI/命令按索引遍历。
    // 读方先快照到局部变量，避免重算时 size()/get(i) 之间列表被替换导致越界。
    private volatile List<BalTopEntry> topPlayers = List.of();

    // 完整排名数据（用于快速查询玩家排名）
    private final Map<UUID, BigDecimal> playerBalances = new ConcurrentHashMap<>();

    // 最后更新时间
    private long lastUpdate = 0;

    // 缓存时间（毫秒）
    private final long cacheTime = 60000; // 1分钟

    /**
     * 更新排行榜
     */
    public void updateRankings(Map<UUID, BigDecimal> allBalances) {
        // 更新完整数据
        playerBalances.clear();
        playerBalances.putAll(allBalances);

        // 重新计算排名
        recalculateRankings();

        lastUpdate = System.currentTimeMillis();
    }

    /**
     * 重新计算排名
     */
    private void recalculateRankings() {
        // 本地构建完整快照后一次性替换，读方永远看到一致的列表
        List<BalTopEntry> snapshot = playerBalances.entrySet().stream()
            .map(entry -> new BalTopEntry(entry.getKey(), entry.getValue()))
            .sorted((a, b) -> b.balance().compareTo(a.balance()))
            .limit(100) // 只保留前100名
            .toList();
        topPlayers = snapshot;
    }

    /**
     * 获取排行榜
     */
    public List<BalTopEntry> getTopPlayers(int limit) {
        List<BalTopEntry> snapshot = topPlayers; // 快照，避免多次读 volatile
        if (snapshot.isEmpty()) {
            return List.of();
        }
        int actualLimit = Math.min(limit, snapshot.size());
        return new ArrayList<>(snapshot.subList(0, actualLimit));
    }

    /**
     * 获取玩家排名
     */
    public OptionalInt getPlayerRank(UUID playerId) {
        List<BalTopEntry> snapshot = topPlayers; // 快照，避免 size()/get(i) 间列表被替换
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).playerId().equals(playerId)) {
                return OptionalInt.of(i + 1); // 排名从1开始
            }
        }
        return OptionalInt.empty();
    }

    /**
     * 检查是否需要更新
     */
    public boolean needsUpdate() {
        return System.currentTimeMillis() - lastUpdate > cacheTime;
    }

    /**
     * 获取排行榜大小
     */
    public int size() {
        return topPlayers.size();
    }

    /**
     * 获取玩家余额
     */
    public BigDecimal getBalance(UUID playerId) {
        return playerBalances.getOrDefault(playerId, BigDecimal.ZERO);
    }

    /**
     * 加载数据
     */
    public void loadData(YamlConfiguration config) {
        ConfigurationSection balancesSection = config.getConfigurationSection("balances");

        if (balancesSection == null) {
            return;
        }

        playerBalances.clear();

        for (String uuidStr : balancesSection.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidStr);
                double balance = balancesSection.getDouble(uuidStr);
                playerBalances.put(playerId, BigDecimal.valueOf(balance));
            } catch (IllegalArgumentException e) {
                // 跳过无效UUID
            }
        }

        recalculateRankings();
        lastUpdate = config.getLong("lastUpdate", System.currentTimeMillis());
    }

    /**
     * 保存数据
     */
    public void saveData(YamlConfiguration config) {
        ConfigurationSection balancesSection = config.createSection("balances");

        for (Map.Entry<UUID, BigDecimal> entry : playerBalances.entrySet()) {
            balancesSection.set(entry.getKey().toString(), entry.getValue().doubleValue());
        }

        config.set("lastUpdate", lastUpdate);
    }

    /**
     * 排行榜条目
     */
    public record BalTopEntry(UUID playerId, BigDecimal balance) {}
}
