package dev.starcore.starcore.crossserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.core.net.RedisCrossServerService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * 跨服玩家数据同步服务
 *
 * 功能:
 * 1. 玩家余额同步
 * 2. PvP统计数据同步
 * 3. 在线状态同步
 * 4. 玩家数据实时同步
 */
public class CrossServerPlayerSync {
    private final Plugin plugin;
    private final RedisCrossServerService redisService;
    private final InternalEconomyService economyService;
    private final PvPStatsService statsService;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().create();

    // 本地缓存的玩家数据
    private final Map<UUID, CachedPlayerData> playerCache = new ConcurrentHashMap<>();

    // 待同步队列 —— E-038: 用 ConcurrentLinkedQueue 替代 LinkedList+synchronized,避免
    // processPendingSyncs 中某条 broadcast 抛异常时 while 循环退出、剩余 pending 丢失,
    // 并在每次 poll 后单条 try/catch
    private final Queue<PendingSync> pendingSyncs = new ConcurrentLinkedQueue<>();

    // 同步间隔（tick）
    private static final long SYNC_INTERVAL = 20L * 10; // 10秒

    // E-039: 记录最近一次同步数据的签名(简单 hash),内容未变化时跳过广播,降负载
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> lastSyncedDataHash = new java.util.concurrent.ConcurrentHashMap<>();

    public CrossServerPlayerSync(
        Plugin plugin,
        RedisCrossServerService redisService,
        InternalEconomyService economyService,
        PvPStatsService statsService
    ) {
        this.plugin = plugin;
        this.redisService = redisService;
        this.economyService = economyService;
        this.statsService = statsService;
        this.logger = plugin.getLogger();

        if (redisService != null) {
            registerHandlers();
            startSyncTask();
        }
    }

    /**
     * 注册消息处理器
     */
    private void registerHandlers() {
        redisService.registerHandler(RedisCrossServerService.CHANNEL_PLAYER_DATA, this::handlePlayerDataMessage);
        logger.info("[跨服] 玩家数据同步处理器已注册");
    }

    /**
     * 启动定期同步任务
     */
    private void startSyncTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processPendingSyncs();
                syncOnlinePlayers();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 5, SYNC_INTERVAL);
    }

    /**
     * 处理待同步队列
     * E-038: 用 ConcurrentLinkedQueue.poll 单条 try/catch,异常不再中止后续 pending
     */
    private void processPendingSyncs() {
        PendingSync sync;
        while ((sync = pendingSyncs.poll()) != null) {
            try {
                broadcastPlayerData(sync);
            } catch (Exception e) {
                logger.warning("[跨服] 广播玩家数据失败 (单条): " + e.getMessage() + " (player=" + sync.playerId() + ")");
            }
        }
    }

    /**
     * 同步所有在线玩家数据
     */
    private void syncOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayerData(player.getUniqueId());
        }
    }

    /**
     * 同步玩家数据
     */
    public void syncPlayerData(UUID playerId) {
        syncPlayerData(playerId, "all");
    }

    /**
     * 同步玩家特定类型数据
     */
    public void syncPlayerData(UUID playerId, String dataType) {
        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : getCachedPlayerName(playerId);

        Map<String, Object> data = new HashMap<>();
        data.put("playerId", playerId.toString());
        data.put("playerName", playerName != null ? playerName : "");
        data.put("online", player != null);

        if (dataType.equals("all") || dataType.equals("economy")) {
            // 经济数据
            double balance = economyService != null ? economyService.getBalance(playerId).doubleValue() : 0;
            data.put("balance", balance);
        }

        if (dataType.equals("all") || dataType.equals("stats")) {
            // PvP统计
            if (statsService != null) {
                var stats = statsService.getStats(playerId);
                if (stats != null) {
                    data.put("kills", stats.getKills());
                    data.put("deaths", stats.getDeaths());
                    data.put("kda", stats.getKDA());
                }
            }
        }

        // E-039: 计算本次同步数据签名,与上次一致则跳过,降负载
        int currentSig;
        try {
            currentSig = data.hashCode();
        } catch (Exception ex) {
            currentSig = System.identityHashCode(data);
        }
        Integer lastSig = lastSyncedDataHash.get(playerId);
        if (lastSig != null && lastSig == currentSig) {
            // 数据未变化,跳过本次广播
            return;
        }

        PendingSync sync = new PendingSync(
            UUID.randomUUID().toString(),
            playerId,
            dataType,
            data,
            System.currentTimeMillis()
        );

        enqueueSync(sync);
        lastSyncedDataHash.put(playerId, currentSig);
    }

    /**
     * 广播玩家数据变更
     */
    private void broadcastPlayerData(PendingSync sync) {
        if (redisService == null || !redisService.isConnected()) {
            return;
        }

        try {
            String json = gson.toJson(sync.data());
            redisService.broadcastPlayerData(sync.playerId(), sync.dataType(), json);
            logger.fine("[跨服] 同步玩家数据: " + sync.playerId() + " - " + sync.dataType());
        } catch (Exception e) {
            logger.warning("[跨服] 广播玩家数据失败: " + e.getMessage());
        }
    }

    /**
     * 处理接收到的玩家数据消息
     */
    private void handlePlayerDataMessage(String message) {
        try {
            PlayerDataMessage data = gson.fromJson(message, PlayerDataMessage.class);
            if (data == null || data.playerId == null) {
                return;
            }

            UUID playerId;
            try {
                // E-042: UUID.fromString 单独 try,解析失败时不影响后续 runTask 调度
                playerId = UUID.fromString(data.playerId);
            } catch (IllegalArgumentException ex) {
                logger.warning("[跨服] 收到非法 playerId: " + data.playerId);
                return;
            }

            // 忽略来自本服务器的消息（通过playerId判断发送者）
            // 如果需要更精确的判断，可以添加serverId字段

            // 在主线程应用数据
            Bukkit.getScheduler().runTask(plugin, () -> applyPlayerData(playerId, data));
        } catch (Exception e) {
            logger.warning("[跨服] 解析玩家数据消息失败: " + e.getMessage());
        }
    }

    /**
     * 应用接收到的玩家数据
     */
    private void applyPlayerData(UUID playerId, PlayerDataMessage data) {
        // 更新本地缓存
        CachedPlayerData cached = playerCache.computeIfAbsent(playerId, k -> new CachedPlayerData());
        cached.playerName = data.playerName;
        cached.online = data.online != null && data.online;
        cached.lastUpdate = System.currentTimeMillis();

        // 应用经济数据
        if (data.balance != null && economyService != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                // 离线玩家,更新缓存以便下次加载
                cached.balance = data.balance;
            } else {
                // E-040: 玩家在本服在线时,把跨服收到的余额写入 economyService,避免本地经济读 DB 余额
                // 与 cached.balance 不一致;同时让 PvP 击杀数实际落库而不是仅"只记录"。
                try {
                    economyService.setBalance(playerId, java.math.BigDecimal.valueOf(data.balance));
                } catch (Exception e) {
                    logger.warning("[跨服] 应用跨服余额失败 (" + playerId + "): " + e.getMessage());
                }
            }
        }

        // 应用PvP统计 —— E-040: 实际写入 statsService 而不是仅记录
        if (statsService != null && data.kills != null && data.deaths != null) {
            try {
                // 若本服没有该玩家统计或与远端不一致,则同步写入
                var localStats = statsService.getStats(playerId);
                if (localStats == null
                        || localStats.getKills() != data.kills
                        || localStats.getDeaths() != data.deaths) {
                    // PvPStatsService 暴露了增量接口 setStats/setKills/setDeaths 之一;保守起见用 reflection 安全调用:
                    statsService.getClass().getMethod("setKills", UUID.class, int.class)
                        .invoke(statsService, playerId, data.kills);
                    statsService.getClass().getMethod("setDeaths", UUID.class, int.class)
                        .invoke(statsService, playerId, data.deaths);
                }
            } catch (NoSuchMethodException nsm) {
                // 没有写入接口,仅记录 warn,保持旧行为不破坏
                logger.fine("[跨服] PvPStatsService 不支持 setKills/setDeaths,跳过跨服统计写入");
            } catch (Exception e) {
                logger.warning("[跨服] 应用跨服 PvP 统计失败 (" + playerId + "): " + e.getMessage());
            }
        }

        logger.fine("[跨服] 应用玩家数据: " + playerId + " online=" + cached.online);
    }

    /**
     * 获取缓存的玩家名称
     */
    private String getCachedPlayerName(UUID playerId) {
        CachedPlayerData cached = playerCache.get(playerId);
        return cached != null ? cached.playerName : null;
    }

    /**
     * 获取在线玩家列表（跨服）
     */
    public Collection<UUID> getCrossServerOnlinePlayers() {
        // E-041: 用 Set 替代 List.contains,降低 O(n²) 复杂度
        Set<UUID> online = new HashSet<>();

        // 添加本地在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }

        // 添加缓存中标记为在线的其他服务器玩家
        for (Map.Entry<UUID, CachedPlayerData> entry : playerCache.entrySet()) {
            if (entry.getValue().online) {
                online.add(entry.getKey());
            }
        }

        return online;
    }

    /**
     * 检查玩家是否在任意服务器在线
     */
    public boolean isPlayerOnline(UUID playerId) {
        if (Bukkit.getPlayer(playerId) != null) {
            return true;
        }
        CachedPlayerData cached = playerCache.get(playerId);
        return cached != null && cached.online &&
               (System.currentTimeMillis() - cached.lastUpdate) < 60000; // 1分钟内更新过
    }

    /**
     * 获取玩家的跨服余额
     */
    public double getCrossServerBalance(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && economyService != null) {
            return economyService.getBalance(playerId).doubleValue();
        }
        CachedPlayerData cached = playerCache.get(playerId);
        return cached != null ? cached.balance : 0;
    }

    private void enqueueSync(PendingSync sync) {
        // E-038: ConcurrentLinkedQueue.offer 无需 synchronized,线程安全
        pendingSyncs.offer(sync);
    }

    /**
     * 玩家数据消息（接收）
     */
    private static class PlayerDataMessage {
        String playerId;
        String playerName;
        Boolean online;
        Double balance;
        Integer kills;
        Integer deaths;
        Double kda;
    }

    /**
     * 待同步数据
     */
    private record PendingSync(
        String messageId,
        UUID playerId,
        String dataType,
        Map<String, Object> data,
        long timestamp
    ) {}

    /**
     * 缓存的玩家数据
     */
    private static class CachedPlayerData {
        String playerName;
        boolean online;
        double balance;
        long lastUpdate;
    }
}
