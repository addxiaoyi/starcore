package dev.starcore.starcore.crossserver;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.starcore.starcore.core.net.RedisCrossServerService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 跨服战争状态同步服务
 *
 * 功能:
 * 1. 战争宣战同步
 * 2. 战争开始/结束同步
 * 3. 战争分数/进度同步
 * 4. 领土征服同步（通过 CrossServerTerritorySync）
 * 5. 停战协议同步
 */
public class CrossServerWarSync {
    private final Plugin plugin;
    private final RedisCrossServerService redisService;
    private final WarService warService;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().create();
    private static final java.lang.reflect.Type STRING_OBJECT_MAP = new TypeToken<Map<String, Object>>() {}.getType();

    // 战争缓存（跨服战争状态）
    private final Map<String, CachedWarData> warCache = new ConcurrentHashMap<>();

    // 宣战状态追踪（防止重复宣战）
    private final Map<String, Long> pendingWars = new ConcurrentHashMap<>();
    private static final long WAR_PENDING_TIMEOUT = 60000; // 60秒

    // 待同步队列
    private final Queue<PendingWarSync> pendingSyncs = new LinkedList<>();

    // 已处理的同步消息
    private final Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 同步间隔
    private static final long SYNC_INTERVAL = 20L * 30; // 30秒

    // 脏标记：数据有变化时才同步
    private volatile boolean dirty = false;
    private final Object dirtyLock = new Object();

    public CrossServerWarSync(
        Plugin plugin,
        RedisCrossServerService redisService,
        WarService warService
    ) {
        this.plugin = plugin;
        this.redisService = redisService;
        this.warService = warService;
        this.logger = plugin.getLogger();

        if (redisService != null) {
            registerHandlers();
            startSyncTasks();
            logger.info("[跨服] 战争状态同步服务已启用");
        } else {
            logger.info("[跨服] 战争状态同步服务未启用（Redis未连接）");
        }
    }

    /**
     * 注册消息处理器
     */
    private void registerHandlers() {
        redisService.registerHandler(RedisCrossServerService.CHANNEL_WAR_UPDATE, this::handleWarMessage);
        redisService.registerHandler(RedisCrossServerService.CHANNEL_SYNC_REQUEST, this::handleSyncRequest);
    }

    /**
     * 启动定期同步任务
     */
    private void startSyncTasks() {
        // 定期同步活跃战争
        new BukkitRunnable() {
            @Override
            public void run() {
                syncActiveWars();
                processPendingSyncs();
                cleanupPendingWars();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 5, SYNC_INTERVAL);
    }

    // ==================== 同步方法 ====================

    /**
     * 同步所有活跃战争
     */
    public void syncActiveWars() {
        if (!dirty) {
            return; // 无变化，跳过同步
        }
        if (warService == null) return;

        for (WarSnapshot war : warService.activeWars()) {
            // 使用 String 作为战争标识符: left_uuid_right_uuid
            String warKey = war.left().toString() + "_" + war.right().toString();
            syncWarState(warKey);
        }
        synchronized (dirtyLock) {
            dirty = false;
        }
    }

    /**
     * 标记数据已变化，下次同步周期触发同步
     */
    public void markDirty() {
        synchronized (dirtyLock) {
            dirty = true;
        }
    }

    /**
     * 同步单个战争状态
     */
    public void syncWarState(String warKey) {
        if (warService == null) return;

        Optional<WarSnapshot> warOpt = getWarByKey(warKey);
        if (warOpt.isEmpty()) return;

        var war = warOpt.get();
        enqueueSync(new PendingWarSync(
            UUID.randomUUID().toString(),
            warKey,
            "state_update",
            buildWarDataMap(war),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步战争宣战
     */
    public void syncWarDeclared(NationId aggressor, NationId defender) {
        // 检查是否正在处理
        String warKey = generateWarKey(aggressor, defender);
        if (pendingWars.containsKey(warKey)) {
            logger.fine("[跨服] 跳过重复宣战请求: " + warKey);
            return;
        }

        pendingWars.put(warKey, System.currentTimeMillis());
        markDirty();

        enqueueSync(new PendingWarSync(
            UUID.randomUUID().toString(),
            warKey,
            "declared",
            Map.of(
                "warKey", warKey,
                "aggressor", aggressor.toString(),
                "defender", defender.toString(),
                "declaredAt", System.currentTimeMillis()
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步战争开始
     */
    public void syncWarStarted(String warKey) {
        markDirty();
        enqueueSync(new PendingWarSync(
            UUID.randomUUID().toString(),
            warKey,
            "started",
            Map.of(
                "warKey", warKey,
                "startedAt", System.currentTimeMillis()
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步战争结束
     */
    public void syncWarEnded(String warKey, String reason, NationId winner, NationId loser) {
        markDirty();
        enqueueSync(new PendingWarSync(
            UUID.randomUUID().toString(),
            warKey,
            "ended",
            Map.of(
                "warKey", warKey,
                "reason", reason,
                "winner", winner != null ? winner.toString() : "",
                "loser", loser != null ? loser.toString() : "",
                "endedAt", System.currentTimeMillis()
            ),
            System.currentTimeMillis()
        ));

        // 清理相关缓存
        warCache.remove(warKey);
        if (winner != null && loser != null) {
            pendingWars.remove(generateWarKey(winner, loser));
            pendingWars.remove(generateWarKey(loser, winner));
        }
    }

    /**
     * 同步战争分数更新
     */
    public void syncWarScore(String warKey, int aggressorScore, int defenderScore) {
        markDirty();
        enqueueSync(new PendingWarSync(
            UUID.randomUUID().toString(),
            warKey,
            "score_update",
            Map.of(
                "warKey", warKey,
                "aggressorScore", aggressorScore,
                "defenderScore", defenderScore,
                "updatedAt", System.currentTimeMillis()
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步领土被占领
     */
    public void syncTerritoryConquered(String warKey, NationId aggressor, NationId defender, int conqueredChunks) {
        markDirty();
        enqueueSync(new PendingWarSync(
            UUID.randomUUID().toString(),
            warKey,
            "conquered",
            Map.of(
                "warKey", warKey,
                "aggressor", aggressor.toString(),
                "defender", defender.toString(),
                "conqueredChunks", conqueredChunks,
                "conqueredAt", System.currentTimeMillis()
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 请求全量同步
     */
    public void requestFullSync() {
        if (redisService != null) {
            redisService.requestSync(UUID.randomUUID(), "wars");
        }
    }

    // ==================== 消息处理 ====================

    /**
     * 处理战争消息
     */
    private void handleWarMessage(String message) {
        try {
            WarMessage warMsg = gson.fromJson(message, WarMessage.class);
            if (warMsg == null || warMsg.id == null) {
                return;
            }

            // 检查是否已处理
            if (!processedMessages.add(warMsg.messageId)) {
                return;
            }

            String warKey = warMsg.id;

            // 在主线程处理
            Bukkit.getScheduler().runTask(plugin, () -> {
                handleWarAction(warKey, warMsg.action, warMsg.data);
            });
        } catch (Exception e) {
            logger.warning("[跨服战争] 解析消息失败: " + e.getMessage());
        }
    }

    /**
     * 处理同步请求
     */
    private void handleSyncRequest(String message) {
        try {
            SyncRequest request = gson.fromJson(message, SyncRequest.class);
            if (request == null || !"wars".equals(request.action)) {
                return;
            }

            // 在主线程响应同步请求
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (warService != null) {
                    for (WarSnapshot war : warService.activeWars()) {
                        String warKey = war.left().toString() + "_" + war.right().toString();
                        syncWarState(warKey);
                    }
                }
            });
        } catch (Exception e) {
            logger.warning("[跨服战争] 解析同步请求失败: " + e.getMessage());
        }
    }

    /**
     * 处理战争操作
     */
    private void handleWarAction(String warKey, String action, String dataJson) {
        try {
            Map<String, Object> data = gson.fromJson(dataJson, STRING_OBJECT_MAP);
            if (data == null) return;

            switch (action) {
                case "declared" -> handleWarDeclared(warKey, data);
                case "started" -> handleWarStarted(warKey);
                case "ended" -> handleWarEnded(warKey, data);
                case "state_update", "score_update" -> handleWarStateUpdate(warKey, data);
                case "conquered" -> handleTerritoryConquered(warKey, data);
            }
        } catch (Exception e) {
            logger.warning("[跨服战争] 处理战争动作失败: " + e.getMessage());
        }
    }

    /**
     * 处理战争宣战
     */
    private void handleWarDeclared(String warKey, Map<String, Object> data) {
        try {
            NationId aggressor = new NationId(UUID.fromString((String) data.get("aggressor")));
            NationId defender = new NationId(UUID.fromString((String) data.get("defender")));

            // 检查是否已在本地处理
            if (pendingWars.containsKey(warKey)) {
                pendingWars.remove(warKey);
                logger.fine("[跨服] 收到本服务器发出的宣战同步，跳过");
                return;
            }

            // 检查是否已经是战争状态
            if (warService != null && warService.atWar(aggressor, defender)) {
                logger.fine("[跨服] 国家已在战争状态: " + warKey);
                return;
            }

            // 更新缓存
            CachedWarData cached = warCache.computeIfAbsent(warKey, k -> new CachedWarData());
            cached.warKey = warKey;
            cached.aggressor = aggressor;
            cached.defender = defender;
            cached.state = "declared";
            cached.lastUpdate = System.currentTimeMillis();

            logger.info("[跨服] 同步战争宣战: " + aggressor + " vs " + defender);
        } catch (Exception e) {
            logger.warning("[跨服战争] 处理宣战失败: " + e.getMessage());
        }
    }

    /**
     * 处理战争开始
     */
    private void handleWarStarted(String warKey) {
        CachedWarData cached = warCache.get(warKey);
        if (cached == null) {
            logger.fine("[跨服] 收到战争开始但无缓存: " + warKey);
            return;
        }

        cached.state = "active";
        cached.lastUpdate = System.currentTimeMillis();

        logger.info("[跨服] 同步战争开始: " + warKey);
    }

    /**
     * 处理战争结束
     */
    private void handleWarEnded(String warKey, Map<String, Object> data) {
        String reason = (String) data.get("reason");
        String winnerStr = (String) data.get("winner");
        String loserStr = (String) data.get("loser");

        NationId winner = winnerStr != null && !winnerStr.isEmpty() ? new NationId(UUID.fromString(winnerStr)) : null;
        NationId loser = loserStr != null && !loserStr.isEmpty() ? new NationId(UUID.fromString(loserStr)) : null;

        // 更新缓存
        CachedWarData cached = warCache.get(warKey);
        if (cached != null) {
            cached.state = "ended";
            cached.winner = winner;
            cached.lastUpdate = System.currentTimeMillis();
        }

        // 清理
        warCache.remove(warKey);

        logger.info("[跨服] 同步战争结束: " + warKey + " - " + reason);
    }

    /**
     * 处理战争状态更新
     */
    private void handleWarStateUpdate(String warKey, Map<String, Object> data) {
        CachedWarData cached = warCache.computeIfAbsent(warKey, k -> new CachedWarData());
        cached.warKey = warKey;
        cached.lastUpdate = System.currentTimeMillis();

        if (data.containsKey("aggressorScore")) {
            cached.aggressorScore = ((Number) data.get("aggressorScore")).intValue();
        }
        if (data.containsKey("defenderScore")) {
            cached.defenderScore = ((Number) data.get("defenderScore")).intValue();
        }
        if (data.containsKey("conqueredChunks")) {
            cached.conqueredChunks = ((Number) data.get("conqueredChunks")).intValue();
        }

        logger.fine("[跨服] 战争状态更新: " + warKey + " score=" + cached.aggressorScore + ":" + cached.defenderScore);
    }

    /**
     * 处理领土被占领
     */
    private void handleTerritoryConquered(String warKey, Map<String, Object> data) {
        CachedWarData cached = warCache.get(warKey);
        if (cached == null) {
            return;
        }

        if (data.containsKey("conqueredChunks")) {
            cached.conqueredChunks += ((Number) data.get("conqueredChunks")).intValue();
        }

        logger.info("[跨服] 领土被占领: warKey=" + warKey + " chunks=" + cached.conqueredChunks);
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过战争键获取战争快照
     */
    private Optional<WarSnapshot> getWarByKey(String warKey) {
        if (warService == null) return Optional.empty();

        // warKey 格式: uuid_uuid (用下划线分隔)
        String[] parts = warKey.split("_");
        if (parts.length != 2) return Optional.empty();

        NationId left = new NationId(UUID.fromString(parts[0]));
        NationId right = new NationId(UUID.fromString(parts[1]));

        return warService.activeWars().stream()
            .filter(w -> (w.left().equals(left) && w.right().equals(right)) ||
                         (w.left().equals(right) && w.right().equals(left)))
            .findFirst();
    }

    private String generateWarKey(NationId left, NationId right) {
        String a = left.toString();
        String b = right.toString();
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    private Map<String, Object> buildWarMap(WarSnapshot war) {
        Map<String, Object> data = new HashMap<>();
        data.put("warKey", generateWarKey(war.left(), war.right()));
        data.put("left", war.left().toString());
        data.put("right", war.right().toString());
        data.put("declaredAt", war.declaredAt() != null ? war.declaredAt().toString() : "");
        return data;
    }

    private Map<String, Object> buildWarDataMap(WarSnapshot war) {
        return buildWarMap(war);
    }

    private void enqueueSync(PendingWarSync sync) {
        synchronized (pendingSyncs) {
            pendingSyncs.offer(sync);
        }
    }

    private void processPendingSyncs() {
        synchronized (pendingSyncs) {
            while (!pendingSyncs.isEmpty()) {
                PendingWarSync sync = pendingSyncs.poll();
                if (sync != null && redisService != null) {
                    broadcastWarSync(sync);
                }
            }
        }
    }

    private void broadcastWarSync(PendingWarSync sync) {
        try {
            String dataJson = gson.toJson(sync.data());
            redisService.broadcastWarUpdate(sync.warKey(), sync.action(), dataJson);
        } catch (Exception e) {
            logger.warning("[跨服] 广播战争同步失败: " + e.getMessage());
        }
    }

    private void cleanupPendingWars() {
        long now = System.currentTimeMillis();
        pendingWars.entrySet().removeIf(entry -> now - entry.getValue() > WAR_PENDING_TIMEOUT);
    }

    /**
     * 检查国家是否处于战争状态
     */
    public boolean atWar(NationId nationId) {
        if (warService != null) {
            return !warService.activeWarsOf(nationId).isEmpty();
        }
        // 检查缓存
        for (CachedWarData cached : warCache.values()) {
            if ("active".equals(cached.state) &&
                (cached.aggressor.equals(nationId) || cached.defender.equals(nationId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查两个国家是否处于战争状态
     */
    public boolean atWar(NationId left, NationId right) {
        if (warService != null) {
            return warService.atWar(left, right);
        }
        // 检查缓存
        String warKey = generateWarKey(left, right);
        return pendingWars.containsKey(warKey);
    }

    /**
     * 获取缓存的战争数据
     */
    public CachedWarData getCachedWar(String warKey) {
        return warCache.get(warKey);
    }

    // ==================== 数据结构 ====================

    private static class WarMessage {
        String messageId;
        String type;
        String id;      // warKey
        String action;
        String data;
        long timestamp;
    }

    private static class SyncRequest {
        String type;
        String id;
        String action;
        String data;
        long timestamp;
    }

    private record PendingWarSync(
        String messageId,
        String warKey,
        String action,
        Map<String, Object> data,
        long timestamp
    ) {}

    /**
     * 缓存的战争数据
     */
    public static class CachedWarData {
        public String warKey;
        public NationId aggressor;
        public NationId defender;
        public String state; // "declared", "active", "ended"
        public int aggressorScore;
        public int defenderScore;
        public int conqueredChunks;
        public NationId winner;
        public long lastUpdate;
    }
}
