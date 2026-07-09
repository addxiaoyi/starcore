package dev.starcore.starcore.module.army.wounded;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.army.wounded.WoundedService.*;
import dev.starcore.starcore.module.army.wounded.storage.WoundedStateCodec;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 伤兵服务实现
 * 管理战场上受伤的士兵，包括治疗、康复等功能
 */
public final class WoundedServiceImpl implements WoundedService {
    private static final String PERSISTENCE_NAMESPACE = "wounded";
    private static final String WOUNDED_STATE_FILE = "wounded.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final MessageService messages;
    private final WoundedStateCodec stateCodec;
    private final PersistenceService persistenceService;
    private final WoundedConfig config;

    // 所有伤兵记录（内存中）
    private final ConcurrentHashMap<UUID, WoundedRecord> woundedRecords = new ConcurrentHashMap<>();
    // 国家伤兵索引
    private final ConcurrentHashMap<UUID, Set<UUID>> nationWounded = new ConcurrentHashMap<>();
    // 玩家伤兵索引
    private final ConcurrentHashMap<UUID, Set<UUID>> playerWounded = new ConcurrentHashMap<>();

    // 正在治疗的伤兵
    private final Set<UUID> healingWounded = ConcurrentHashMap.newKeySet();

    public WoundedServiceImpl(
        Plugin plugin,
        NationService nationService,
        MessageService messages,
        WoundedConfig config,
        PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.messages = messages;
        this.config = config;
        this.stateCodec = new WoundedStateCodec();
        this.persistenceService = persistenceService;

        // 加载已有数据
        if (persistenceService != null) {
            loadWoundedRecords();
        }

        // 启动定时任务
        startPeriodicTasks();
    }

    @Override
    public WoundedConfig getConfig() {
        return config;
    }

    @Override
    public WoundedRecord createWounded(ArmyUnit armyUnit, int woundedCount) {
        UUID nationId = armyUnit.nationId();

        // 检查伤兵上限
        int currentCount = getNationWoundedCount(nationId);
        int limit = getWoundedLimit(nationId);
        if (currentCount + woundedCount > limit) {
            // 超出上限，部分士兵直接死亡
            int canAccept = Math.max(0, limit - currentCount);
            if (canAccept < woundedCount) {
                woundedCount = canAccept;
            }
        }

        if (woundedCount <= 0) {
            throw new IllegalStateException("wounded.error.limit-reached");
        }

        // 确定严重程度
        WoundedSeverity severity = WoundedSeverity.fromDamagePercent(1.0 - (armyUnit.health() / 100.0));

        // 创建伤兵记录
        WoundedRecord record = WoundedRecord.create(
            nationId,
            armyUnit.id(),
            null, // 玩家ID可能为空，表示军队整体
            woundedCount,
            armyUnit.location(),
            severity,
            config
        );

        // 检查到达时死亡
        if (config.enableDeathRisk() && ThreadLocalRandom.current().nextDouble() < severity.deathChanceOnArrival()) {
            // 随机死亡部分伤兵
            int deaths = (int) (woundedCount * 0.3 * ThreadLocalRandom.current().nextDouble());
            woundedCount = Math.max(1, woundedCount - deaths);
            record = new WoundedRecord(
                record.id(),
                record.nationId(),
                record.armyId(),
                record.playerId(),
                record.originalSoldiers() - deaths,
                woundedCount,
                record.severity(),
                record.status(),
                record.injuryLocation(),
                record.hospitalLocation(),
                record.injuredAt(),
                record.healingStartedAt(),
                record.expectedRecoveryAt(),
                record.healingProgress()
            );
        }

        // 注册记录
        woundedRecords.put(record.id(), record);
        nationWounded.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(record.id());
        if (record.playerId() != null) {
            playerWounded.computeIfAbsent(record.playerId(), k -> ConcurrentHashMap.newKeySet()).add(record.id());
        }

        // 持久化
        persistRecord(record);

        return record;
    }

    @Override
    public List<WoundedRecord> getNationWounded(UUID nationId) {
        Set<UUID> ids = nationWounded.getOrDefault(nationId, Collections.emptySet());
        return ids.stream()
            .map(woundedRecords::get)
            .filter(Objects::nonNull)
            .filter(r -> r.status() != WoundedStatus.DEAD && r.status() != WoundedStatus.RECOVERED)
            .collect(Collectors.toList());
    }

    @Override
    public List<WoundedRecord> getPlayerWounded(UUID playerId) {
        Set<UUID> ids = playerWounded.getOrDefault(playerId, Collections.emptySet());
        return ids.stream()
            .map(woundedRecords::get)
            .filter(Objects::nonNull)
            .filter(r -> r.status() != WoundedStatus.DEAD && r.status() != WoundedStatus.RECOVERED)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<WoundedRecord> getWounded(UUID woundedId) {
        return Optional.ofNullable(woundedRecords.get(woundedId));
    }

    @Override
    public boolean startHealing(UUID woundedId, Location location) {
        WoundedRecord record = woundedRecords.get(woundedId);
        if (record == null) {
            return false;
        }

        if (!record.canStartHealing()) {
            return false;
        }

        // 检查是否需要医院
        if (config.enableHospitalRequired()) {
            if (!isHospitalLocation(location)) {
                return false;
            }
        }

        // 开始治疗
        WoundedRecord updated = record.startHealing(location);
        woundedRecords.put(woundedId, updated);
        healingWounded.add(woundedId);

        // 持久化
        persistRecord(updated);

        return true;
    }

    @Override
    public boolean cancelHealing(UUID woundedId) {
        WoundedRecord record = woundedRecords.get(woundedId);
        if (record == null || !record.isHealing()) {
            return false;
        }

        // 取消治疗，回到等待状态
        WoundedRecord updated = new WoundedRecord(
            record.id(),
            record.nationId(),
            record.armyId(),
            record.playerId(),
            record.originalSoldiers(),
            record.currentWounded(),
            record.severity(),
            WoundedStatus.WAITING,
            record.injuryLocation(),
            null,
            record.injuredAt(),
            0,
            0,
            0.0
        );

        woundedRecords.put(woundedId, updated);
        healingWounded.remove(woundedId);

        // 持久化
        persistRecord(updated);

        return true;
    }

    @Override
    public int completeHealing(UUID woundedId) {
        WoundedRecord record = woundedRecords.get(woundedId);
        if (record == null || !record.isHealing()) {
            return 0;
        }

        int recoveredSoldiers = record.currentWounded();

        // 标记为已康复
        WoundedRecord updated = new WoundedRecord(
            record.id(),
            record.nationId(),
            record.armyId(),
            record.playerId(),
            record.originalSoldiers(),
            0,
            record.severity(),
            WoundedStatus.RECOVERED,
            record.injuryLocation(),
            record.hospitalLocation(),
            record.injuredAt(),
            record.healingStartedAt(),
            System.currentTimeMillis(),
            1.0
        );

        // 从内存移除（已康复）
        woundedRecords.remove(woundedId);
        healingWounded.remove(woundedId);

        // 从索引移除
        Set<UUID> nationSet = nationWounded.get(record.nationId());
        if (nationSet != null) {
            nationSet.remove(woundedId);
        }
        if (record.playerId() != null) {
            Set<UUID> playerSet = playerWounded.get(record.playerId());
            if (playerSet != null) {
                playerSet.remove(woundedId);
            }
        }

        // 从持久化移除
        removePersistedRecord(woundedId);

        return recoveredSoldiers;
    }

    @Override
    public void woundedDeath(UUID woundedId) {
        WoundedRecord record = woundedRecords.get(woundedId);
        if (record == null) {
            return;
        }

        // 标记为已死亡
        WoundedRecord updated = new WoundedRecord(
            record.id(),
            record.nationId(),
            record.armyId(),
            record.playerId(),
            record.originalSoldiers(),
            0,
            record.severity(),
            WoundedStatus.DEAD,
            record.injuryLocation(),
            record.hospitalLocation(),
            record.injuredAt(),
            record.healingStartedAt(),
            System.currentTimeMillis(),
            0.0
        );

        // 从内存移除
        woundedRecords.remove(woundedId);
        healingWounded.remove(woundedId);

        // 从索引移除
        Set<UUID> nationSet = nationWounded.get(record.nationId());
        if (nationSet != null) {
            nationSet.remove(woundedId);
        }
        if (record.playerId() != null) {
            Set<UUID> playerSet = playerWounded.get(record.playerId());
            if (playerSet != null) {
                playerSet.remove(woundedId);
            }
        }

        // 从持久化移除
        removePersistedRecord(woundedId);
    }

    @Override
    public int getNationWoundedCount(UUID nationId) {
        Set<UUID> ids = nationWounded.getOrDefault(nationId, Collections.emptySet());
        return ids.stream()
            .map(woundedRecords::get)
            .filter(Objects::nonNull)
            .filter(r -> r.status() != WoundedStatus.DEAD && r.status() != WoundedStatus.RECOVERED)
            .mapToInt(WoundedRecord::currentWounded)
            .sum();
    }

    @Override
    public int getNationHealingCount(UUID nationId) {
        Set<UUID> ids = nationWounded.getOrDefault(nationId, Collections.emptySet());
        return ids.stream()
            .map(woundedRecords::get)
            .filter(Objects::nonNull)
            .filter(WoundedRecord::isHealing)
            .mapToInt(WoundedRecord::currentWounded)
            .sum();
    }

    @Override
    public int getWoundedLimit(UUID nationId) {
        int baseLimit = config.baseWoundedLimit();
        int perLevel = config.woundedLimitPerLevel();

        // 获取国家等级
        int level = 1;
        try {
            NationId nationIdObj = new NationId(nationId);
            var nation = nationService.nationById(nationIdObj).orElse(null);
            if (nation != null) {
                level = Math.max(1, (int) (nation.experience() / 1000) + 1);
            }
        } catch (Exception e) {
            // 忽略，获取等级失败时使用默认等级
        }

        return baseLimit + (level - 1) * perLevel;
    }

    // ==================== 定时任务 ====================

    private void startPeriodicTasks() {
        // 定期更新治疗进度
        int checkIntervalTicks = config.healingCheckInterval() * 20;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateHealingProgress();
        }, checkIntervalTicks, checkIntervalTicks);

        // 定期保存所有伤兵
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAll();
        }, 20L * 60 * 5, 20L * 60 * 5); // 每5分钟
    }

    /**
     * 更新治疗进度
     */
    private void updateHealingProgress() {
        long now = System.currentTimeMillis();

        for (UUID woundedId : healingWounded) {
            WoundedRecord record = woundedRecords.get(woundedId);
            if (record == null || !record.isHealing()) {
                healingWounded.remove(woundedId);
                continue;
            }

            // 检查是否治疗完成
            if (now >= record.expectedRecoveryAt()) {
                completeHealing(woundedId);
                continue;
            }

            // 计算进度
            long totalDuration = record.expectedRecoveryAt() - record.healingStartedAt();
            long elapsed = now - record.healingStartedAt();
            double progress = Math.min(1.0, (elapsed * 1.0) / totalDuration);

            // 应用康复率修正
            progress *= record.severity().recoveryRate();
            progress *= config.healingSpeedBonus();

            // 检查死亡风险
            if (config.enableDeathRisk() && ThreadLocalRandom.current().nextDouble() < 0.001 * record.severity().deathChanceOnArrival()) {
                woundedDeath(woundedId);
                continue;
            }

            // 更新进度
            WoundedRecord updated = record.updateProgress(progress);
            woundedRecords.put(woundedId, updated);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 检查是否为医院位置
     */
    private boolean isHospitalLocation(Location location) {
        // 可以检查特定方块或区域
        // 目前简化处理，只要在世界内即可
        return location != null && location.getWorld() != null;
    }

    // ==================== 持久化 ====================

    private void loadWoundedRecords() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, WOUNDED_STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                String json = props.getProperty(key);
                try {
                    WoundedRecord record = stateCodec.decode(json);
                    woundedRecords.put(record.id(), record);

                    // 恢复索引
                    nationWounded.computeIfAbsent(record.nationId(), k -> ConcurrentHashMap.newKeySet()).add(record.id());
                    if (record.playerId() != null) {
                        playerWounded.computeIfAbsent(record.playerId(), k -> ConcurrentHashMap.newKeySet()).add(record.id());
                    }

                    // 恢复治疗中的记录
                    if (record.isHealing()) {
                        healingWounded.add(record.id());
                    }

                    plugin.getLogger().info("Loaded wounded record: " + record.id());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load wounded record from key " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + woundedRecords.size() + " wounded records from persistence");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load wounded records: " + e.getMessage());
        }
    }

    @Override
    public void saveAll() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new java.util.Properties();
            for (Map.Entry<UUID, WoundedRecord> entry : woundedRecords.entrySet()) {
                String key = entry.getKey().toString();
                String json = stateCodec.encode(entry.getValue());
                props.setProperty(key, json);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, WOUNDED_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save wounded records: " + e.getMessage());
        }
    }

    private void persistRecord(WoundedRecord record) {
        if (persistenceService == null) {
            return;
        }

        try {
            String key = record.id().toString();
            String json = stateCodec.encode(record);

            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, WOUNDED_STATE_FILE);
            props.setProperty(key, json);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, WOUNDED_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist wounded record " + record.id() + ": " + e.getMessage());
        }
    }

    private void removePersistedRecord(UUID woundedId) {
        if (persistenceService == null) {
            return;
        }

        try {
            String key = woundedId.toString();
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, WOUNDED_STATE_FILE);
            props.remove(key);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, WOUNDED_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove wounded record " + woundedId + " from persistence: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        saveAll();
    }
}