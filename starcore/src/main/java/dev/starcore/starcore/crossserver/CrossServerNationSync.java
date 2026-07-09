package dev.starcore.starcore.crossserver;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.starcore.starcore.core.net.RedisCrossServerService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 跨服国家数据同步服务
 *
 * 功能:
 * 1. 国家基础信息同步（名称、创始者、成员）
 * 2. 国家金库余额同步
 * 3. 外交关系同步（联盟、停战、宣战）
 * 4. 国家政策同步
 * 5. 国家领土同步（通过 CrossServerTerritorySync）
 */
public class CrossServerNationSync {
    private final Plugin plugin;
    private final RedisCrossServerService redisService;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final DiplomacyService diplomacyService;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().create();
    private static final java.lang.reflect.Type STRING_OBJECT_MAP = new TypeToken<Map<String, Object>>() {}.getType();

    // 国家数据缓存
    private final Map<UUID, CachedNationData> nationCache = new ConcurrentHashMap<>();

    // 待同步队列
    private final Queue<PendingNationSync> pendingSyncs = new LinkedList<>();

    // 同步间隔（tick）
    private static final long FULL_SYNC_INTERVAL = 20L * 60; // 60秒
    private static final long TREASURY_SYNC_INTERVAL = 20L * 10; // 10秒

    // 已处理的同步消息
    private final Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public CrossServerNationSync(
        Plugin plugin,
        RedisCrossServerService redisService,
        NationService nationService,
        TreasuryService treasuryService,
        DiplomacyService diplomacyService
    ) {
        this.plugin = plugin;
        this.redisService = redisService;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.diplomacyService = diplomacyService;
        this.logger = plugin.getLogger();

        if (redisService != null) {
            registerHandlers();
            startSyncTasks();
            logger.info("[跨服] 国家数据同步服务已启用");
        } else {
            logger.info("[跨服] 国家数据同步服务未启用（Redis未连接）");
        }
    }

    /**
     * 注册消息处理器
     */
    private void registerHandlers() {
        redisService.registerHandler(RedisCrossServerService.CHANNEL_NATION_MESSAGE, this::handleNationMessage);
    }

    /**
     * 启动定期同步任务
     */
    private void startSyncTasks() {
        // 国库同步（频繁）
        new BukkitRunnable() {
            @Override
            public void run() {
                syncTreasuries();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 5, TREASURY_SYNC_INTERVAL);

        // 全量同步（间隔较长）
        new BukkitRunnable() {
            @Override
            public void run() {
                syncAllNations();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 30, FULL_SYNC_INTERVAL);

        // 处理待同步队列
        new BukkitRunnable() {
            @Override
            public void run() {
                processPendingSyncs();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 2, 20L * 5);
    }

    // ==================== 同步方法 ====================

    /**
     * 同步所有国家数据
     */
    public void syncAllNations() {
        if (nationService == null) return;

        for (Nation nation : nationService.nations()) {
            syncNationData(nation.id());
        }
    }

    /**
     * 同步单个国家数据
     */
    public void syncNationData(NationId nationId) {
        if (nationService == null) return;

        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) return;

        Nation nation = nationOpt.get();
        enqueueSync(new PendingNationSync(
            UUID.randomUUID().toString(),
            nationId.value(),
            "full_sync",
            buildNationDataMap(nation),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步国家创建
     */
    public void syncNationCreated(NationId nationId, String nationName, UUID founderId, String founderName) {
        enqueueSync(new PendingNationSync(
            UUID.randomUUID().toString(),
            nationId.value(),
            "created",
            Map.of(
                "nationId", nationId.value().toString(),
                "nationName", nationName,
                "founderId", founderId.toString(),
                "founderName", founderName,
                "createdAt", System.currentTimeMillis()
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步国家解散
     */
    public void syncNationDisbanded(NationId nationId) {
        enqueueSync(new PendingNationSync(
            UUID.randomUUID().toString(),
            nationId.value(),
            "disbanded",
            Map.of("nationId", nationId.value().toString()),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步国家成员变更
     */
    public void syncNationMemberChange(NationId nationId, UUID playerId, String playerName, String action) {
        enqueueSync(new PendingNationSync(
            UUID.randomUUID().toString(),
            nationId.value(),
            "member_change",
            Map.of(
                "nationId", nationId.value().toString(),
                "playerId", playerId.toString(),
                "playerName", playerName,
                "action", action // "join", "leave", "kick"
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步国家金库变更
     */
    public void syncNationTreasury(NationId nationId, double balance) {
        enqueueSync(new PendingNationSync(
            UUID.randomUUID().toString(),
            nationId.value(),
            "treasury_update",
            Map.of(
                "nationId", nationId.value().toString(),
                "balance", balance
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 同步国家重命名
     */
    public void syncNationRenamed(NationId nationId, String oldName, String newName) {
        enqueueSync(new PendingNationSync(
            UUID.randomUUID().toString(),
            nationId.value(),
            "renamed",
            Map.of(
                "nationId", nationId.value().toString(),
                "oldName", oldName,
                "newName", newName
            ),
            System.currentTimeMillis()
        ));
    }

    /**
     * 请求同步其他服务器的国家数据
     */
    public void requestFullSync() {
        if (redisService != null) {
            redisService.requestSync(UUID.randomUUID(), "nations");
        }
    }

    // ==================== 消息处理 ====================

    /**
     * 处理国家消息
     */
    private void handleNationMessage(String message) {
        try {
            NationMessage nationMsg = gson.fromJson(message, NationMessage.class);
            if (nationMsg == null || nationMsg.id == null) {
                return;
            }

            // 检查是否已处理
            if (!processedMessages.add(nationMsg.messageId)) {
                return;
            }

            // 在主线程处理
            Bukkit.getScheduler().runTask(plugin, () -> {
                handleNationAction(nationMsg);
            });
        } catch (Exception e) {
            logger.warning("[跨服国家] 解析消息失败: " + e.getMessage());
        }
    }

    /**
     * 处理国家操作
     */
    private void handleNationAction(NationMessage msg) {
        UUID nationId = UUID.fromString(msg.id);

        switch (msg.action) {
            case "full_sync", "created", "member_change", "treasury_update", "renamed" -> {
                applyNationData(nationId, msg.action, msg.data);
            }
            case "disbanded" -> {
                handleNationDisbanded(nationId);
            }
            default -> {
                logger.fine("[跨服国家] 未知操作: " + msg.action);
            }
        }
    }

    /**
     * 应用国家数据
     */
    private void applyNationData(UUID nationId, String action, String dataJson) {
        try {
            Map<String, Object> data = gson.fromJson(dataJson, STRING_OBJECT_MAP);
            if (data == null) return;

            CachedNationData cached = nationCache.computeIfAbsent(nationId, k -> new CachedNationData());
            cached.nationId = nationId;
            cached.lastUpdate = System.currentTimeMillis();

            switch (action) {
                case "full_sync" -> {
                    cached.nationName = (String) data.get("nationName");
                    cached.founderId = data.get("founderId") != null ? UUID.fromString((String) data.get("founderId")) : null;
                    cached.level = data.get("level") != null ? ((Number) data.get("level")).intValue() : 1;
                    cached.memberCount = data.get("memberCount") != null ? ((Number) data.get("memberCount")).intValue() : 0;
                    cached.territoryCount = data.get("territoryCount") != null ? ((Number) data.get("territoryCount")).intValue() : 0;
                    logger.info("[跨服] 同步国家数据: " + cached.nationName);
                }
                case "created" -> {
                    cached.nationName = (String) data.get("nationName");
                    cached.founderId = UUID.fromString((String) data.get("founderId"));
                    logger.info("[跨服] 同步国家创建: " + cached.nationName);
                }
                case "member_change" -> {
                    String playerAction = (String) data.get("action");
                    if ("join".equals(playerAction)) {
                        cached.memberCount++;
                    } else if ("leave".equals(playerAction) || "kick".equals(playerAction)) {
                        cached.memberCount = Math.max(0, cached.memberCount - 1);
                    }
                    logger.info("[跨服] 同步成员变更: " + cached.nationName + " - " + playerAction);
                }
                case "treasury_update" -> {
                    if (data.get("balance") != null) {
                        cached.treasuryBalance = ((Number) data.get("balance")).doubleValue();
                    }
                    // 通知本地 treasuryService（如果已注入）
                    if (treasuryService != null) {
                        // treasuryService 的同步由 CrossServerTerritorySync 处理
                    }
                }
                case "renamed" -> {
                    String oldName = cached.nationName;
                    cached.nationName = (String) data.get("newName");
                    logger.info("[跨服] 国家更名: " + oldName + " -> " + cached.nationName);
                }
            }

            // 通知本地国家服务更新
            if (nationService != null) {
                NationId nationIdObj = new NationId(nationId);
                nationService.refreshNationCache(nationIdObj);
            }
        } catch (Exception e) {
            logger.warning("[跨服国家] 应用数据失败: " + e.getMessage());
        }
    }

    /**
     * 处理国家解散
     */
    private void handleNationDisbanded(UUID nationId) {
        nationCache.remove(nationId);
        logger.info("[跨服] 同步国家解散: " + nationId);

        // 通知本地国家服务
        if (nationService != null) {
            // nationService.disband() 应该由本地服务器调用
        }
    }

    /**
     * 同步所有国库
     */
    private void syncTreasuries() {
        if (treasuryService == null) return;

        // 遍历所有国家同步国库
        if (nationService != null) {
            for (Nation nation : nationService.nations()) {
                try {
                    double balance = treasuryService.balance(nation.id()).doubleValue();
                    syncNationTreasury(nation.id(), balance);
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    private void enqueueSync(PendingNationSync sync) {
        synchronized (pendingSyncs) {
            pendingSyncs.offer(sync);
        }
    }

    private void processPendingSyncs() {
        synchronized (pendingSyncs) {
            while (!pendingSyncs.isEmpty()) {
                PendingNationSync sync = pendingSyncs.poll();
                if (sync != null && redisService != null) {
                    broadcastNationSync(sync);
                }
            }
        }
    }

    private void broadcastNationSync(PendingNationSync sync) {
        try {
            String dataJson = gson.toJson(sync.data());
            redisService.broadcastNationMessage(sync.nationId(), sync.action(), dataJson);
        } catch (Exception e) {
            logger.warning("[跨服] 广播国家同步失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildNationDataMap(Nation nation) {
        Map<String, Object> data = new HashMap<>();
        data.put("nationId", nation.id().value().toString());
        data.put("nationName", nation.name());
        if (nation.founderId() != null) {
            data.put("founderId", nation.founderId().toString());
        }
        data.put("level", nationService != null ? nationService.levelOf(nation.id()) : 1);
        data.put("memberCount", nation.members() != null ? nation.members().size() : 0);
        data.put("territoryCount", nationService != null ? nationService.claimCount(nation.id()) : 0);
        if (treasuryService != null) {
            data.put("balance", treasuryService.balance(nation.id()).doubleValue());
        }
        return data;
    }

    /**
     * 获取缓存的国家数据
     */
    public CachedNationData getCachedNation(UUID nationId) {
        return nationCache.get(nationId);
    }

    /**
     * 获取缓存的国家名称
     */
    public String getCachedNationName(UUID nationId) {
        CachedNationData cached = nationCache.get(nationId);
        return cached != null ? cached.nationName : null;
    }

    // ==================== 数据结构 ====================

    private static class NationMessage {
        String messageId;
        String type;
        String id;      // nationId
        String action;
        String data;
        long timestamp;
    }

    private record PendingNationSync(
        String messageId,
        UUID nationId,
        String action,
        Map<String, Object> data,
        long timestamp
    ) {}

    /**
     * 缓存的国家数据
     */
    public static class CachedNationData {
        public UUID nationId;
        public String nationName;
        public UUID founderId;
        public int level;
        public int memberCount;
        public int territoryCount;
        public double treasuryBalance;
        public long lastUpdate;
    }
}
