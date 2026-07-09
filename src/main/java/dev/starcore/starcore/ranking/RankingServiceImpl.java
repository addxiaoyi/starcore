package dev.starcore.starcore.ranking;

import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 排名服务实现
 * 整合 RankingCache 和 PvPStatsService 提供排名数据
 * 支持内存模式和数据库模式
 */
public class RankingServiceImpl implements RankingService {

    private final Plugin plugin;
    private final PvPStatsService pvpStatsService;
    private final RankingDatabase database;
    private final NationRankingCache rankingCache;

    // 在线时间追踪（玩家UUID -> 加入时间戳毫秒）
    private final Map<UUID, Long> onlineTimeTracker = new ConcurrentHashMap<>();

    // 在线时间累计缓存（玩家UUID -> 累计在线秒数）
    private final Map<UUID, Long> totalOnlineTimeCache = new ConcurrentHashMap<>();

    // 内存模式缓存 (ALLTIME)
    private final ConcurrentHashMap<UUID, Long> killsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> deathsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> assistsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> onlineTimeCache = new ConcurrentHashMap<>();

    // 周期特定缓存
    private final Map<RankPeriod, ConcurrentHashMap<UUID, Long>> periodKillsCache = new ConcurrentHashMap<>();
    private final Map<RankPeriod, ConcurrentHashMap<UUID, Long>> periodDeathsCache = new ConcurrentHashMap<>();
    private final Map<RankPeriod, ConcurrentHashMap<UUID, Long>> periodAssistsCache = new ConcurrentHashMap<>();
    private final Map<RankPeriod, CopyOnWriteArrayList<UUID>> periodKillLeaderboard = new ConcurrentHashMap<>();
    private final Map<RankPeriod, CopyOnWriteArrayList<UUID>> periodDeathsLeaderboard = new ConcurrentHashMap<>();
    private final Map<RankPeriod, CopyOnWriteArrayList<UUID>> periodKDRLeaderboard = new ConcurrentHashMap<>();

    private final CopyOnWriteArrayList<UUID> killLeaderboard = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UUID> deathsLeaderboard = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UUID> kdRatioLeaderboard = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<UUID> onlineTimeLeaderboard = new CopyOnWriteArrayList<>();

    // D-119: 改为 1500ms（从 5000ms），高频操作时排行榜反馈更及时
    private static final long CACHE_EXPIRY_MS = 1500;
    private long lastLeaderboardRebuild = 0;

    public RankingServiceImpl(Plugin plugin, PvPStatsService pvpStatsService) {
        this(plugin, pvpStatsService, null);
    }

    public RankingServiceImpl(Plugin plugin, PvPStatsService pvpStatsService, RankingDatabase database) {
        this.plugin = plugin;
        this.pvpStatsService = pvpStatsService;
        this.database = database;
        this.rankingCache = database != null ? new NationRankingCache(database) : null;

        // 如果有 PvPStatsService，同步现有数据
        if (this.pvpStatsService != null) {
            syncAllStatsFromPvPService();
        }
    }

    /**
     * 从 PvPStatsService 同步所有玩家的统计数据
     */
    private void syncAllStatsFromPvPService() {
        if (pvpStatsService == null) return;

        Map<UUID, PvPStats> allStats = pvpStatsService.getAllStats();
        for (Map.Entry<UUID, PvPStats> entry : allStats.entrySet()) {
            UUID playerId = entry.getKey();
            PvPStats stats = entry.getValue();
            killsCache.put(playerId, (long) stats.getKills());
            deathsCache.put(playerId, (long) stats.getDeaths());
            assistsCache.put(playerId, (long) stats.getAssists());
        }
    }

    /**
     * 从数据库或缓存同步玩家数据
     */
    private void syncPlayerData(UUID playerId) {
        if (pvpStatsService != null) {
            PvPStats stats = pvpStatsService.getStats(playerId);
            if (stats != null) {
                killsCache.put(playerId, (long) stats.getKills());
                deathsCache.put(playerId, (long) stats.getDeaths());
                assistsCache.put(playerId, (long) stats.getAssists());
            }
        }

        // 同步在线时间
        syncOnlineTime(playerId);
    }

    /**
     * 同步玩家在线时间
     */
    private void syncOnlineTime(UUID playerId) {
        // 基础累计时间
        long baseTime = totalOnlineTimeCache.getOrDefault(playerId, 0L);

        // 如果玩家在线，加上当前会话时间
        Long joinTime = onlineTimeTracker.get(playerId);
        if (joinTime != null) {
            long currentSession = (System.currentTimeMillis() - joinTime) / 1000;
            onlineTimeCache.put(playerId, baseTime + currentSession);
        } else {
            onlineTimeCache.put(playerId, baseTime);
        }
    }

    /**
     * 玩家加入 - 开始追踪在线时间
     * D-115: 同步该玩家的 PvP stats 到内存缓存，避免排行榜重建前新玩家不在榜
     */
    public void onPlayerJoin(UUID playerId) {
        onlineTimeTracker.put(playerId, System.currentTimeMillis());
        // 同步该玩家 PvP 数据
        if (pvpStatsService != null) {
            PvPStats stats = pvpStatsService.getStats(playerId);
            if (stats != null) {
                killsCache.put(playerId, (long) stats.getKills());
                deathsCache.put(playerId, (long) stats.getDeaths());
                assistsCache.put(playerId, (long) stats.getAssists());
            }
        }
    }

    /**
     * 玩家离开 - 累计在线时间
     * D-114: 退出时写 DB，防止重启后在线时间数据丢失
     */
    public void onPlayerQuit(UUID playerId) {
        Long joinTime = onlineTimeTracker.remove(playerId);
        if (joinTime != null) {
            long playTime = (System.currentTimeMillis() - joinTime) / 1000;
            totalOnlineTimeCache.merge(playerId, playTime, Long::sum);
            long total = totalOnlineTimeCache.get(playerId);
            onlineTimeCache.put(playerId, total);

            // D-114: 异步写 DB 持久化
            if (database != null) {
                database.saveOnlineTime(playerId, total);
            }
        }
    }

    /**
     * 获取周期特定的击杀缓存
     */
    private ConcurrentHashMap<UUID, Long> getPeriodKillsCache(RankPeriod period) {
        return periodKillsCache.computeIfAbsent(period, p -> new ConcurrentHashMap<>());
    }

    /**
     * 获取周期特定的死亡缓存
     */
    private ConcurrentHashMap<UUID, Long> getPeriodDeathsCache(RankPeriod period) {
        return periodDeathsCache.computeIfAbsent(period, p -> new ConcurrentHashMap<>());
    }

    /**
     * 获取周期特定的助攻缓存
     */
    private ConcurrentHashMap<UUID, Long> getPeriodAssistsCache(RankPeriod period) {
        return periodAssistsCache.computeIfAbsent(period, p -> new ConcurrentHashMap<>());
    }

    /**
     * 获取周期特定的击杀排行榜
     */
    private CopyOnWriteArrayList<UUID> getPeriodKillLeaderboard(RankPeriod period) {
        return periodKillLeaderboard.computeIfAbsent(period, p -> new CopyOnWriteArrayList<>());
    }

    /**
     * 获取周期特定的死亡排行榜
     */
    private CopyOnWriteArrayList<UUID> getPeriodDeathsLeaderboard(RankPeriod period) {
        return periodDeathsLeaderboard.computeIfAbsent(period, p -> new CopyOnWriteArrayList<>());
    }

    /**
     * 获取周期特定的K/D比率排行榜
     */
    private CopyOnWriteArrayList<UUID> getPeriodKDRLeaderboard(RankPeriod period) {
        return periodKDRLeaderboard.computeIfAbsent(period, p -> new CopyOnWriteArrayList<>());
    }

    /**
     * 检查是否需要重建排行榜
     */
    private void checkLeaderboardRebuild() {
        if (System.currentTimeMillis() - lastLeaderboardRebuild > CACHE_EXPIRY_MS) {
            rebuildLeaderboards();
            lastLeaderboardRebuild = System.currentTimeMillis();
        }
    }

    /**
     * 重建所有排行榜
     */
    private void rebuildLeaderboards() {
        // 击杀榜 (ALLTIME)
        List<UUID> killSorted = killsCache.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> -e.getValue()))
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());
        killLeaderboard.clear();
        killLeaderboard.addAll(killSorted);

        // 死亡榜（按死亡数降序）(ALLTIME)
        List<UUID> deathSorted = deathsCache.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> -e.getValue()))
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());
        deathsLeaderboard.clear();
        deathsLeaderboard.addAll(deathSorted);

        // K/D 比率榜 (ALLTIME) - 按 K/D 降序排列
        List<UUID> kdSorted = killsCache.keySet().stream()
            .sorted((a, b) -> {
                double kdA = calculateKDRatio(a);
                double kdB = calculateKDRatio(b);
                return Double.compare(kdB, kdA);
            })
            .collect(Collectors.toList());
        kdRatioLeaderboard.clear();
        kdRatioLeaderboard.addAll(kdSorted);

        // 在线时间榜
        List<UUID> onlineSorted = onlineTimeCache.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> -e.getValue()))
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());
        onlineTimeLeaderboard.clear();
        onlineTimeLeaderboard.addAll(onlineSorted);

        // 重建周期特定排行榜
        for (RankPeriod period : RankPeriod.values()) {
            if (period == RankPeriod.ALLTIME) continue;
            rebuildPeriodLeaderboard(period);
        }
    }

    /**
     * 计算玩家的 K/D 比率
     */
    private double calculateKDRatio(UUID playerId) {
        long kills = killsCache.getOrDefault(playerId, 0L);
        long deaths = deathsCache.getOrDefault(playerId, 0L);
        return deaths > 0 ? (double) kills / deaths : kills;
    }

    /**
     * 重建指定周期的排行榜
     */
    private void rebuildPeriodLeaderboard(RankPeriod period) {
        ConcurrentHashMap<UUID, Long> periodKills = getPeriodKillsCache(period);
        ConcurrentHashMap<UUID, Long> periodDeaths = getPeriodDeathsCache(period);
        CopyOnWriteArrayList<UUID> periodKillLB = getPeriodKillLeaderboard(period);
        CopyOnWriteArrayList<UUID> periodDeathLB = getPeriodDeathsLeaderboard(period);
        CopyOnWriteArrayList<UUID> periodKDRLB = getPeriodKDRLeaderboard(period);

        // 周期击杀榜
        List<UUID> killSorted = periodKills.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> -e.getValue()))
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());
        periodKillLB.clear();
        periodKillLB.addAll(killSorted);

        // 周期死亡榜
        List<UUID> deathSorted = periodDeaths.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> -e.getValue()))
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());
        periodDeathLB.clear();
        periodDeathLB.addAll(deathSorted);

        // 周期K/D比率榜
        List<UUID> kdrSorted = periodKills.keySet().stream()
            .sorted((a, b) -> {
                double kdA = calculatePeriodKDRatio(a, period);
                double kdB = calculatePeriodKDRatio(b, period);
                return Double.compare(kdB, kdA);
            })
            .collect(Collectors.toList());
        periodKDRLB.clear();
        periodKDRLB.addAll(kdrSorted);
    }

    /**
     * 计算周期内玩家的 K/D 比率
     */
    private double calculatePeriodKDRatio(UUID playerId, RankPeriod period) {
        long kills = getPeriodKillsCache(period).getOrDefault(playerId, 0L);
        long deaths = getPeriodDeathsCache(period).getOrDefault(playerId, 0L);
        return deaths > 0 ? (double) kills / deaths : kills;
    }

    /**
     * 获取玩家排名（击杀榜）
     */
    @Override
    public CompletableFuture<Integer> getKillRank(UUID playerId, RankPeriod period) {
        return CompletableFuture.supplyAsync(() -> {
            syncPlayerData(playerId);
            checkLeaderboardRebuild();
            if (period == RankPeriod.ALLTIME) {
                int index = killLeaderboard.indexOf(playerId);
                return index >= 0 ? index + 1 : -1;
            } else {
                int index = getPeriodKillLeaderboard(period).indexOf(playerId);
                return index >= 0 ? index + 1 : -1;
            }
        });
    }

    /**
     * 获取玩家排名（K/D比率榜）
     */
    @Override
    public CompletableFuture<Integer> getKDRatioRank(UUID playerId, RankPeriod period) {
        return CompletableFuture.supplyAsync(() -> {
            syncPlayerData(playerId);
            checkLeaderboardRebuild();
            if (period == RankPeriod.ALLTIME) {
                int index = kdRatioLeaderboard.indexOf(playerId);
                return index >= 0 ? index + 1 : -1;
            } else {
                int index = getPeriodKDRLeaderboard(period).indexOf(playerId);
                return index >= 0 ? index + 1 : -1;
            }
        });
    }

    /**
     * 获取玩家排名（综合榜）
     */
    @Override
    public CompletableFuture<Integer> getOverallRank(UUID playerId, RankPeriod period) {
        return getKillRank(playerId, period);
    }

    /**
     * 获取玩家排名（在线时间榜）
     */
    @Override
    public CompletableFuture<Integer> getOnlineTimeRank(UUID playerId, RankPeriod period) {
        return CompletableFuture.supplyAsync(() -> {
            syncOnlineTime(playerId);
            checkLeaderboardRebuild();
            int index = onlineTimeLeaderboard.indexOf(playerId);
            return index >= 0 ? index + 1 : -1;
        });
    }

    /**
     * 获取玩家击杀数
     */
    @Override
    public CompletableFuture<Long> getKillCount(UUID playerId, RankPeriod period) {
        return CompletableFuture.supplyAsync(() -> {
            syncPlayerData(playerId);
            if (period == RankPeriod.ALLTIME) {
                return killsCache.getOrDefault(playerId, 0L);
            } else {
                return getPeriodKillsCache(period).getOrDefault(playerId, 0L);
            }
        });
    }

    /**
     * 获取玩家死亡数
     */
    @Override
    public CompletableFuture<Long> getDeathCount(UUID playerId, RankPeriod period) {
        return CompletableFuture.supplyAsync(() -> {
            syncPlayerData(playerId);
            if (period == RankPeriod.ALLTIME) {
                return deathsCache.getOrDefault(playerId, 0L);
            } else {
                return getPeriodDeathsCache(period).getOrDefault(playerId, 0L);
            }
        });
    }

    /**
     * 获取玩家助攻数
     */
    @Override
    public CompletableFuture<Long> getAssistCount(UUID playerId, RankPeriod period) {
        return CompletableFuture.supplyAsync(() -> {
            syncPlayerData(playerId);
            if (period == RankPeriod.ALLTIME) {
                return assistsCache.getOrDefault(playerId, 0L);
            } else {
                return getPeriodAssistsCache(period).getOrDefault(playerId, 0L);
            }
        });
    }

    /**
     * 获取玩家KDA比率 (K+D+A)/D
     */
    @Override
    public double getKDARatio(UUID playerId) {
        syncPlayerData(playerId);
        long kills = killsCache.getOrDefault(playerId, 0L);
        long deaths = deathsCache.getOrDefault(playerId, 0L);
        long assists = assistsCache.getOrDefault(playerId, 0L);
        if (deaths > 0) {
            return (double) (kills + assists) / deaths;
        }
        return kills + assists;
    }

    /**
     * 获取玩家K/D比率
     */
    @Override
    public double getKDRatio(UUID playerId) {
        syncPlayerData(playerId);
        return calculateKDRatio(playerId);
    }

    /**
     * 获取玩家在线时间（秒）
     */
    @Override
    public CompletableFuture<Long> getOnlineTime(UUID playerId, RankPeriod period) {
        return CompletableFuture.supplyAsync(() -> {
            syncOnlineTime(playerId);
            return onlineTimeCache.getOrDefault(playerId, 0L);
        });
    }

    /**
     * 获取排行榜大小
     */
    @Override
    public int getLeaderboardSize(String statType) {
        if (database != null) {
            return database.querySize(statType);
        }
        return switch (statType) {
            case "kills" -> killLeaderboard.size();
            case "deaths" -> deathsLeaderboard.size();
            case "kdratio" -> kdRatioLeaderboard.size();
            case "playtime" -> onlineTimeLeaderboard.size();
            default -> 0;
        };
    }

    /**
     * 获取 Top N 玩家
     */
    @Override
    public List<UUID> getTopN(String statType, int limit) {
        checkLeaderboardRebuild();
        List<UUID> leaderboard = switch (statType) {
            case "kills" -> killLeaderboard;
            case "deaths" -> deathsLeaderboard;
            case "kdratio" -> kdRatioLeaderboard;
            case "playtime" -> onlineTimeLeaderboard;
            default -> killLeaderboard;
        };
        return leaderboard.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Top 玩家数据 (使用接口定义)
     */
    @Override
    public List<TopPlayerData> getTopPlayers(String statType, int limit, RankPeriod period) {
        checkLeaderboardRebuild();
        List<UUID> leaderboard = switch (statType) {
            case "kills" -> killLeaderboard;
            case "deaths" -> deathsLeaderboard;
            case "kdratio" -> kdRatioLeaderboard;
            case "playtime" -> onlineTimeLeaderboard;
            default -> killLeaderboard;
        };

        List<TopPlayerData> result = new ArrayList<>();
        int position = 1;
        for (UUID playerId : leaderboard) {
            if (position > limit) break;

            // 同步数据
            syncPlayerData(playerId);

            long value = switch (statType) {
                case "kills" -> killsCache.getOrDefault(playerId, 0L);
                case "deaths" -> deathsCache.getOrDefault(playerId, 0L);
                case "kdratio" -> (long) (calculateKDRatio(playerId) * 100); // 乘100保留2位小数
                case "playtime" -> onlineTimeCache.getOrDefault(playerId, 0L);
                default -> 0L;
            };

            result.add(new TopPlayerData(position++, playerId, value));
        }
        return result;
    }

    @Override
    public String getPlayerRankDisplay(UUID playerId) {
        // D-112: 不在主线程用 .join() 阻塞，改用同步读缓存
        if (Bukkit.isPrimaryThread()) {
            int index = killLeaderboard.indexOf(playerId);
            return index >= 0 ? "#" + (index + 1) : "未上榜";
        } else {
            syncPlayerData(playerId);
            checkLeaderboardRebuild();
            int index = killLeaderboard.indexOf(playerId);
            return index >= 0 ? "#" + (index + 1) : "未上榜";
        }
    }

    @Override
    public String getRankPosition(UUID playerId) {
        if (Bukkit.isPrimaryThread()) {
            return getKillRank(playerId, RankPeriod.ALLTIME)
                .thenApply(rank -> rank > 0 ? String.valueOf(rank) : "-")
                .join();
        } else {
            syncPlayerData(playerId);
            checkLeaderboardRebuild();
            int index = killLeaderboard.indexOf(playerId);
            return index >= 0 ? String.valueOf(index + 1) : "-";
        }
    }

    /**
     * 获取玩家显示用的排名字符串（带格式化）
     */
    public String getFormattedRank(UUID playerId) {
        String rank = getRankPosition(playerId);
        if ("-".equals(rank)) {
            return "无排名";
        }
        return "第" + rank + "名";
    }

    /**
     * 获取格式化在线时间
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 格式化的在线时间字符串，如 "10小时30分钟"
     */
    public String getFormattedOnlineTime(UUID playerId, RankPeriod period) {
        try {
            long seconds = getOnlineTime(playerId, period).join();
            return formatTime(seconds);
        } catch (Exception e) {
            return "0小时0分钟";
        }
    }

    /**
     * 格式化时间（秒）为可读字符串
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        }
        return minutes + "分钟";
    }

    /**
     * 设置玩家助攻数（周期数据）
     */
    public void setAssistCount(UUID playerId, long count, RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            assistsCache.put(playerId, count);
        } else {
            getPeriodAssistsCache(period).put(playerId, count);
        }
    }

    /**
     * 增加玩家击杀数（更新周期数据）
     * D-131: 仅处理周期数据，ALLTIME 由 PvPStatsService.recordKill 统一计数，避免双倍统计
     */
    public void addKill(UUID playerId, RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            // PvPStatsService.recordKill 已处理 ALLTIME，这里忽略
            return;
        }
        getPeriodKillsCache(period).merge(playerId, 1L, Long::sum);
    }

    /**
     * 增加玩家死亡数（更新周期数据）
     */
    public void addDeath(UUID playerId, RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            // PvPStatsService.recordKill 已处理 ALLTIME，这里忽略
            return;
        }
        getPeriodDeathsCache(period).merge(playerId, 1L, Long::sum);
    }

    /**
     * 设置玩家击杀数（周期数据）
     */
    public void setKillCount(UUID playerId, long count, RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            killsCache.put(playerId, count);
        } else {
            getPeriodKillsCache(period).put(playerId, count);
        }
    }

    /**
     * 设置玩家死亡数（周期数据）
     */
    public void setDeathCount(UUID playerId, long count, RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            deathsCache.put(playerId, count);
        } else {
            getPeriodDeathsCache(period).put(playerId, count);
        }
    }

    /**
     * 获取周期特定的 Top N 玩家
     */
    public List<TopPlayerData> getTopPlayersByPeriod(String statType, int limit, RankPeriod period) {
        if (period == RankPeriod.ALLTIME) {
            return getTopPlayers(statType, limit, period);
        }

        checkLeaderboardRebuild();
        List<UUID> leaderboard = switch (statType) {
            case "kills" -> getPeriodKillLeaderboard(period);
            case "deaths" -> getPeriodDeathsLeaderboard(period);
            default -> getPeriodKillLeaderboard(period);
        };

        List<TopPlayerData> result = new ArrayList<>();
        ConcurrentHashMap<UUID, Long> cache = switch (statType) {
            case "kills" -> getPeriodKillsCache(period);
            case "deaths" -> getPeriodDeathsCache(period);
            default -> getPeriodKillsCache(period);
        };

        int position = 1;
        for (UUID playerId : leaderboard) {
            if (position > limit) break;
            long value = cache.getOrDefault(playerId, 0L);
            result.add(new TopPlayerData(position++, playerId, value));
        }
        return result;
    }
}
