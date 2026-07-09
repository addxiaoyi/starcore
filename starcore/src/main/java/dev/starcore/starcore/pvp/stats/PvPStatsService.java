package dev.starcore.starcore.pvp.stats;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PvP统计服务（带持久化支持）
 */
public final class PvPStatsService {
    // 玩家统计（玩家UUID -> 统计数据）
    private final Map<UUID, PvPStats> playerStats = new ConcurrentHashMap<>();
    private final org.bukkit.plugin.Plugin plugin;
    private final JsonPvPStatsStorage storage;
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    public PvPStatsService(Plugin plugin) {
        this.plugin = plugin;
        this.storage = new JsonPvPStatsStorage(plugin);
        // 异步加载所有统计数据
        loadAllAsync();
    }

    /**
     * 异步加载所有统计数据
     */
    private void loadAllAsync() {
        storage.loadAll().thenAccept(allData -> {
            for (PvPStatsData data : allData) {
                PvPStats stats = PvPStatsData.toStats(data);
                if (stats != null) {
                    playerStats.put(stats.getPlayerId(), stats);
                }
            }
            loaded.set(true);
        });
    }

    /**
     * 数据就绪回调。D-130: 替代 awaitLoaded 自旋，调度主线程回调避免 TPS 损耗
     */
    public void whenLoaded(java.util.function.Consumer<PvPStatsService> callback) {
        if (loaded.get()) {
            // 已就绪，直接主线程回调
            if (org.bukkit.Bukkit.isPrimaryThread()) {
                callback.accept(this);
            } else {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> callback.accept(this));
            }
        } else {
            // 未就绪，延迟 1tick 再检查
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> whenLoaded(callback), 1L);
        }
    }

    /**
     * @deprecated D-130: 自旋等待会阻塞主线程。请改用 whenLoaded(Consumer) 回调版本
     */
    @Deprecated
    public void awaitLoaded() {
        while (!loaded.get()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 记录击杀
     */
    public void recordKill(UUID killerId, UUID victimId) {
        PvPStats stats = getOrCreateStats(killerId);
        stats.addKill();

        // 受害者死亡
        PvPStats victimStats = getOrCreateStats(victimId);
        victimStats.addDeath();

        // 异步保存
        storage.saveAsync(killerId, stats);
        storage.saveAsync(victimId, victimStats);
    }

    /**
     * 记录助攻
     */
    public void recordAssist(UUID assisterId) {
        PvPStats stats = getOrCreateStats(assisterId);
        stats.addAssist();
        storage.saveAsync(assisterId, stats);
    }

    /**
     * 记录伤害
     */
    public void recordDamage(UUID attackerId, int damage) {
        PvPStats stats = getOrCreateStats(attackerId);
        stats.addDamage(damage);
        storage.saveAsync(attackerId, stats);
    }

    /**
     * 记录决斗胜利
     */
    public void recordDuelWin(UUID playerId) {
        PvPStats stats = getOrCreateStats(playerId);
        stats.addDuelWin();
        storage.saveAsync(playerId, stats);
    }

    /**
     * 记录决斗失败
     */
    public void recordDuelLoss(UUID playerId) {
        PvPStats stats = getOrCreateStats(playerId);
        stats.addDuelLoss();
        storage.saveAsync(playerId, stats);
    }

    /**
     * 获取或创建统计
     */
    public PvPStats getOrCreateStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, k -> new PvPStats(playerId));
    }

    /**
     * 获取玩家统计
     */
    public PvPStats getStats(UUID playerId) {
        return playerStats.getOrDefault(playerId, new PvPStats(playerId));
    }

    /**
     * 获取击杀排行榜
     */
    public List<PvPStats> getKillLeaderboard(int limit) {
        return playerStats.values().stream()
            .sorted((s1, s2) -> Integer.compare(s2.getKills(), s1.getKills()))
            .limit(limit)
            .toList();
    }

    /**
     * 获取K/D排行榜
     * D-132: 增加最低场次门槛（10场），避免新玩家 1 kill / 0 death = KDR 极高霸榜
     */
    public List<PvPStats> getKDLeaderboard(int limit) {
        return playerStats.values().stream()
            .filter(s -> s.getKills() + s.getDeaths() >= 10)
            .sorted((s1, s2) -> Double.compare(s2.getKDRatio(), s1.getKDRatio()))
            .limit(limit)
            .toList();
    }

    /**
     * 获取决斗胜率排行榜
     */
    public List<PvPStats> getDuelWinRateLeaderboard(int limit) {
        return playerStats.values().stream()
            .filter(s -> s.getDuelWins() + s.getDuelLosses() >= 10) // 至少10场决斗
            .sorted((s1, s2) -> Double.compare(s2.getDuelWinRate(), s1.getDuelWinRate()))
            .limit(limit)
            .toList();
    }

    /**
     * 重置玩家统计
     */
    public void resetStats(UUID playerId) {
        playerStats.remove(playerId);
        storage.deleteAsync(playerId);
    }

    /**
     * 重置所有统计
     * D-134: 加写锁防止并发 recordKill 在 clear 后仍写回文件
     */
    public void resetAllStats() {
        synchronized (playerStats) {
            playerStats.clear();
            // 删除所有文件
            File[] files = storage.getStorageFolder().listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }

    /**
     * 获取所有统计数据
     */
    public Map<UUID, PvPStats> getAllStats() {
        return new HashMap<>(playerStats);
    }

    /**
     * 保存玩家统计数据
     * @param playerId 玩家UUID
     * @param stats 统计数据
     */
    public void savePlayerStats(UUID playerId, PvPStats stats) {
        storage.saveAsync(playerId, stats);
    }

    /**
     * 加载玩家统计数据
     * @param playerId 玩家UUID
     * @return 统计数据，如果不存在则返回新的统计
     */
    public PvPStats loadPlayerStats(UUID playerId) {
        // 如果已在内存中，直接返回
        PvPStats existing = playerStats.get(playerId);
        if (existing != null) {
            return existing;
        }

        // 从文件加载
        PvPStatsData data = storage.loadSync(playerId);
        if (data != null) {
            PvPStats stats = PvPStatsData.toStats(data);
            if (stats != null) {
                playerStats.put(playerId, stats);
                return stats;
            }
        }

        return getOrCreateStats(playerId);
    }

    /**
     * 保存所有统计数据（同步）
     */
    public void saveAll() {
        for (var entry : playerStats.entrySet()) {
            storage.saveSync(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 保存所有统计数据（异步）
     */
    public java.util.concurrent.CompletableFuture<Integer> saveAllAsync() {
        return storage.saveAll(playerStats);
    }
}
