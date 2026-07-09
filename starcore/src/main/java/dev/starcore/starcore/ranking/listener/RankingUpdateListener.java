package dev.starcore.starcore.ranking.listener;

import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.ranking.NationRankingCache;
import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排行榜更新监听器
 * 自动更新玩家统计数据
 */
public class RankingUpdateListener implements Listener {

    private final NationRankingCache rankingCache;
    private final RankingService rankingService;
    private final PvPStatsService pvpStatsService;

    // 玩家在线时间追踪
    private final Map<UUID, Long> onlineTimeTracker = new ConcurrentHashMap<>();

    // 当前会话击杀数（用于增量更新）
    private final Map<UUID, Integer> sessionKills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> sessionDeaths = new ConcurrentHashMap<>();

    public RankingUpdateListener(NationRankingCache rankingCache, RankingService rankingService, PvPStatsService pvpStatsService) {
        this.rankingCache = rankingCache;
        this.rankingService = rankingService;
        this.pvpStatsService = pvpStatsService;
    }

    /**
     * 玩家加入 - 开始追踪在线时间
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 记录加入时间
        onlineTimeTracker.put(playerId, System.currentTimeMillis());

        // 重置当前会话统计
        sessionKills.put(playerId, 0);
        sessionDeaths.put(playerId, 0);

        // 通知 RankingService
        if (rankingService != null) {
            rankingService.onPlayerJoin(playerId);
        }
    }

    /**
     * 玩家离开 - 更新累计在线时间
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 计算本次在线时长
        Long joinTime = onlineTimeTracker.remove(playerId);
        if (joinTime != null) {
            long playTimeSeconds = (System.currentTimeMillis() - joinTime) / 1000;

            // 获取本次会话的击杀/死亡统计（先 get 后 remove）
            int sessionKill = sessionKills.getOrDefault(playerId, 0);
            int sessionDeath = sessionDeaths.getOrDefault(playerId, 0);
            sessionKills.remove(playerId);
            sessionDeaths.remove(playerId);

            // 更新所有周期的在线时间（累加）
            for (RankPeriod period : RankPeriod.values()) {
                if (period.shouldReset()) {
                    // 周期榜：累加到 delta
                    rankingCache.updatePlayerDelta(playerId, "playtime", period, playTimeSeconds);
                }
            }
            // 全时榜：累加到总在线时间
            rankingCache.updatePlayerDelta(playerId, "playtime", RankPeriod.ALLTIME, playTimeSeconds);

            // 更新击杀和死亡统计（累加）
            if (sessionKill > 0) {
                for (RankPeriod period : RankPeriod.values()) {
                    if (period.shouldReset()) {
                        rankingCache.updatePlayerDelta(playerId, "kills", period, sessionKill);
                    }
                }
                rankingCache.updatePlayerDelta(playerId, "kills", RankPeriod.ALLTIME, sessionKill);
            }

            if (sessionDeath > 0) {
                for (RankPeriod period : RankPeriod.values()) {
                    if (period.shouldReset()) {
                        rankingCache.updatePlayerDelta(playerId, "deaths", period, sessionDeath);
                    }
                }
                rankingCache.updatePlayerDelta(playerId, "deaths", RankPeriod.ALLTIME, sessionDeath);
            }
        }

        // 通知 RankingService
        if (rankingService != null) {
            rankingService.onPlayerQuit(playerId);
        }
    }

    /**
     * 玩家死亡 - 更新死亡统计
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        UUID victimId = victim.getUniqueId();

        // 更新受害者死亡数（当前会话）
        sessionDeaths.merge(victimId, 1, Integer::sum);

        // 更新 PvPStatsService（持久化）
        if (pvpStatsService != null) {
            pvpStatsService.recordKill(killer != null ? killer.getUniqueId() : null, victimId);
        }

        // 更新击杀者击杀数（当前会话）
        if (killer != null) {
            UUID killerId = killer.getUniqueId();
            sessionKills.merge(killerId, 1, Integer::sum);

            // 显示击杀提示（仅对击杀者）
            killer.sendMessage("§a+1 击杀");
        }
    }

    /**
     * 手动更新统计（设置绝对值）
     */
    public void updateStat(UUID playerId, String statType, long value) {
        for (RankPeriod period : RankPeriod.values()) {
            rankingCache.updatePlayer(playerId, statType, period, value);
        }
    }

    /**
     * 增量更新统计（累加）
     */
    public void incrementStat(UUID playerId, String statType, long delta) {
        for (RankPeriod period : RankPeriod.values()) {
            rankingCache.updatePlayerDelta(playerId, statType, period, delta);
        }
    }

    /**
     * 获取玩家当前会话在线时间（秒）
     */
    public long getCurrentSessionTime(UUID playerId) {
        Long joinTime = onlineTimeTracker.get(playerId);
        if (joinTime == null) {
            return 0;
        }
        return (System.currentTimeMillis() - joinTime) / 1000;
    }

    /**
     * 获取玩家当前会话击杀数
     */
    public int getSessionKills(UUID playerId) {
        return sessionKills.getOrDefault(playerId, 0);
    }

    /**
     * 获取玩家当前会话死亡数
     */
    public int getSessionDeaths(UUID playerId) {
        return sessionDeaths.getOrDefault(playerId, 0);
    }
}
