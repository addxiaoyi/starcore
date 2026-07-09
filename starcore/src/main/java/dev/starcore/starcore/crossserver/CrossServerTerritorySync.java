package dev.starcore.starcore.crossserver;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.core.net.RedisCrossServerService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 跨服领地同步服务
 * 负责在多服务器环境下同步领地所有权和战争状态
 *
 * 功能:
 * 1. 同步领地声明/取消声明
 * 2. 同步战争状态（委托给 CrossServerWarSync）
 * 3. 同步被占领区块信息
 * 4. 处理跨服战争事件
 */
public class CrossServerTerritorySync {
    private final Plugin plugin;
    private final TerritoryService territoryService;
    private final RedisCrossServerService redisService;
    private final WarService warService;
    private final Logger logger;

    private static final String SYNC_TYPE_CLAIM = "territory_claim";
    private static final String SYNC_TYPE_UNCLAIM = "territory_unclaim";
    private static final String SYNC_TYPE_CONQUEST = "territory_conquest";

    // 本地待同步变更队列
    private final Queue<PendingSync> pendingSyncs = new LinkedList<>();

    // 已处理的同步消息缓存（防止重复处理）
    private final Set<String> processedMessageIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Gson gson = new GsonBuilder().create();

    // 跨服战争同步（可选）
    private CrossServerWarSync warSync;

    public CrossServerTerritorySync(
        Plugin plugin,
        TerritoryService territoryService,
        RedisCrossServerService redisService,
        WarService warService
    ) {
        this.plugin = plugin;
        this.territoryService = territoryService;
        this.redisService = redisService;
        this.warService = warService;
        this.logger = plugin.getLogger();

        // 注册数据处理器
        registerHandlers();

        // 启动同步任务
        startSyncTask();

        logger.info("[跨服] 领地同步服务已初始化");
    }

    /**
     * 设置跨服战争同步服务
     */
    public void setWarSync(CrossServerWarSync warSync) {
        this.warSync = warSync;
    }

    /**
     * 注册跨服消息处理器
     */
    private void registerHandlers() {
        if (redisService == null) {
            logger.warning("跨服同步服务未配置，领地跨服同步已禁用");
            return;
        }

        // 注册处理器（如果使用 RedisTransport 兼容方式）
        // 注意：这里主要通过 CrossServerService 的数据处理器方式
        logger.info("[跨服] 领地同步处理器已注册");
    }

    /**
     * 启动定期同步任务
     */
    private void startSyncTask() {
        // 每30秒同步一次待处理的领地变更
        new BukkitRunnable() {
            @Override
            public void run() {
                processPendingSyncs();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 30, 20L * 30);
    }

    /**
     * 处理待同步的变更
     */
    private void processPendingSyncs() {
        List<PendingSync> toProcess;
        synchronized (pendingSyncs) {
            toProcess = new ArrayList<>(pendingSyncs);
            pendingSyncs.clear();
        }

        for (PendingSync sync : toProcess) {
            if (!isProcessed(sync.messageId())) {
                broadcastSync(sync);
                markProcessed(sync.messageId());
            }
        }
    }

    /**
     * 广播同步消息
     */
    private void broadcastSync(PendingSync sync) {
        if (redisService == null || !redisService.isConnected()) {
            return;
        }

        try {
            String dataJson = gson.toJson(Map.of(
                "ownerId", sync.payload().get("ownerId"),
                "world", sync.payload().get("world"),
                "chunkX", sync.payload().get("chunkX"),
                "chunkZ", sync.payload().get("chunkZ"),
                "timestamp", sync.timestamp()
            ));

            switch (sync.type()) {
                case SYNC_TYPE_CLAIM -> redisService.broadcastNationMessage(
                    UUID.fromString((String) sync.payload().get("ownerId")),
                    "territory_claim",
                    dataJson
                );
                case SYNC_TYPE_UNCLAIM -> redisService.broadcastNationMessage(
                    UUID.fromString((String) sync.payload().get("ownerId")),
                    "territory_unclaim",
                    dataJson
                );
                case SYNC_TYPE_CONQUEST -> redisService.broadcastWarUpdate(
                    (String) sync.payload().get("warId"),
                    "territory_conquest",
                    dataJson
                );
            }
        } catch (Exception e) {
            logger.warning("广播同步消息失败: " + e.getMessage());
        }
    }

    // ==================== 同步方法 ====================

    /**
     * 同步领地声明
     */
    public void syncClaim(String ownerId, ChunkCoordinate coordinate) {
        String messageId = UUID.randomUUID().toString();
        PendingSync sync = new PendingSync(
            messageId,
            SYNC_TYPE_CLAIM,
            System.currentTimeMillis(),
            Map.of(
                "ownerId", ownerId,
                "world", coordinate.world(),
                "chunkX", coordinate.x(),
                "chunkZ", coordinate.z()
            )
        );
        enqueueSync(sync);
    }

    /**
     * 同步取消领地声明
     */
    public void syncUnclaim(String ownerId, ChunkCoordinate coordinate) {
        String messageId = UUID.randomUUID().toString();
        PendingSync sync = new PendingSync(
            messageId,
            SYNC_TYPE_UNCLAIM,
            System.currentTimeMillis(),
            Map.of(
                "ownerId", ownerId,
                "world", coordinate.world(),
                "chunkX", coordinate.x(),
                "chunkZ", coordinate.z()
            )
        );
        enqueueSync(sync);
    }

    /**
     * 同步领土征服
     */
    public void syncConquest(ChunkCoordinate coordinate, String fromOwnerId, String toOwnerId, String warId) {
        String messageId = UUID.randomUUID().toString();
        PendingSync sync = new PendingSync(
            messageId,
            SYNC_TYPE_CONQUEST,
            System.currentTimeMillis(),
            Map.of(
                "world", coordinate.world(),
                "chunkX", coordinate.x(),
                "chunkZ", coordinate.z(),
                "fromOwner", fromOwnerId,
                "toOwner", toOwnerId,
                "warId", warId
            )
        );
        enqueueSync(sync);
    }

    // ==================== 战争事件处理（转发到 CrossServerWarSync） ====================

    /**
     * 同步战争宣战
     */
    public void syncWarDeclared(NationId aggressor, NationId defender) {
        if (warSync != null) {
            warSync.syncWarDeclared(aggressor, defender);
        }
    }

    /**
     * 同步战争开始
     */
    public void syncWarStarted(String warKey) {
        if (warSync != null) {
            warSync.syncWarStarted(warKey);
        }
    }

    /**
     * 同步战争结束
     */
    public void syncWarEnded(String warKey, String reason, NationId winner, NationId loser) {
        if (warSync != null) {
            warSync.syncWarEnded(warKey, reason, winner, loser);
        }
    }

    // ==================== 消息处理 ====================

    /**
     * 处理领地声明同步
     */
    @SuppressWarnings("unchecked")
    public void handleClaimSync(String messageId, Object data) {
        try {
            Map<String, Object> payload;
            if (data instanceof Map) {
                payload = (Map<String, Object>) data;
            } else {
                return;
            }

            String ownerId = (String) payload.get("ownerId");
            String world = (String) payload.get("world");
            int chunkX = ((Number) payload.get("chunkX")).intValue();
            int chunkZ = ((Number) payload.get("chunkZ")).intValue();

            ChunkCoordinate coordinate = new ChunkCoordinate(world, chunkX, chunkZ);

            // 在主线程执行领地声明
            Bukkit.getScheduler().runTask(plugin, () -> {
                territoryService.claim(ownerId, coordinate);
                logger.info("[跨服] 同步领地声明: " + ownerId + " at " + coordinate);
            });
        } catch (Exception e) {
            logger.warning("处理领地声明同步失败: " + e.getMessage());
        }
    }

    /**
     * 处理取消领地声明同步
     */
    @SuppressWarnings("unchecked")
    public void handleUnclaimSync(String messageId, Object data) {
        try {
            Map<String, Object> payload;
            if (data instanceof Map) {
                payload = (Map<String, Object>) data;
            } else {
                return;
            }

            String ownerId = (String) payload.get("ownerId");
            String world = (String) payload.get("world");
            int chunkX = ((Number) payload.get("chunkX")).intValue();
            int chunkZ = ((Number) payload.get("chunkZ")).intValue();

            ChunkCoordinate coordinate = new ChunkCoordinate(world, chunkX, chunkZ);

            // 在主线程执行取消声明
            Bukkit.getScheduler().runTask(plugin, () -> {
                territoryService.unclaim(ownerId, coordinate);
                logger.info("[跨服] 同步取消领地声明: " + ownerId + " at " + coordinate);
            });
        } catch (Exception e) {
            logger.warning("处理取消领地声明同步失败: " + e.getMessage());
        }
    }

    /**
     * 处理领土征服同步
     */
    @SuppressWarnings("unchecked")
    public void handleConquestSync(String messageId, Object data) {
        try {
            Map<String, Object> payload;
            if (data instanceof Map) {
                payload = (Map<String, Object>) data;
            } else {
                return;
            }

            String world = (String) payload.get("world");
            int chunkX = ((Number) payload.get("chunkX")).intValue();
            int chunkZ = ((Number) payload.get("chunkZ")).intValue();
            String fromOwner = (String) payload.get("fromOwner");
            String toOwner = (String) payload.get("toOwner");

            ChunkCoordinate coordinate = new ChunkCoordinate(world, chunkX, chunkZ);

            // 在主线程执行领土征服
            Bukkit.getScheduler().runTask(plugin, () -> {
                territoryService.unclaim(fromOwner, coordinate);
                territoryService.claim(toOwner, coordinate);
                logger.info("[跨服] 同步领土征服: " + coordinate + " 从 " + fromOwner + " 到 " + toOwner);
            });
        } catch (Exception e) {
            logger.warning("处理领土征服同步失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private void enqueueSync(PendingSync sync) {
        synchronized (pendingSyncs) {
            pendingSyncs.offer(sync);
        }
    }

    private boolean isProcessed(String messageId) {
        return processedMessageIds.contains(messageId);
    }

    private void markProcessed(String messageId) {
        processedMessageIds.add(messageId);
        // 清理过期消息ID（保留最近1000个）
        if (processedMessageIds.size() > 1000) {
            Iterator<String> iter = processedMessageIds.iterator();
            for (int i = 0; i < 100 && iter.hasNext(); i++) {
                iter.next();
                iter.remove();
            }
        }
    }

    /**
     * 获取领地所属者
     */
    public Optional<String> getOwnerAt(ChunkCoordinate coordinate) {
        return territoryService.claimAt(coordinate).map(TerritoryClaim::ownerId);
    }

    /**
     * 检查区块是否被声明
     */
    public boolean isClaimed(ChunkCoordinate coordinate) {
        return territoryService.isClaimed(coordinate);
    }

    /**
     * 待同步消息记录
     */
    private record PendingSync(
        String messageId,
        String type,
        long timestamp,
        Map<String, Object> payload
    ) {}
}
