package dev.starcore.starcore.module.diplomacy.alliance.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.diplomacy.alliance.AllianceInfoData;
import dev.starcore.starcore.module.diplomacy.alliance.AlliancePairKey;
import dev.starcore.starcore.module.nation.model.NationId;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 联盟数据存储实现
 * 支持 Properties 文件持久化
 */
public class AllianceStorage {

    private static final String FILE_NAME = "alliance.properties";
    private static final String INVITES_FILE_NAME = "alliance_invites.properties";
    private static final String COOLDOWNS_FILE_NAME = "alliance_cooldowns.properties";

    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;

    private CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);
    private final Object saveLock = new Object();

    public AllianceStorage(
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger
    ) {
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.logger = logger;
    }

    /**
     * 保存联盟关系数据
     */
    public void saveAlliances(Map<AlliancePairKey, AllianceInfoData> alliances) {
        Properties properties = new Properties();
        for (Map.Entry<AlliancePairKey, AllianceInfoData> entry : alliances.entrySet()) {
            AlliancePairKey key = entry.getKey();
            AllianceInfoData data = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            String propValue = data.nation1Name() + "|" + data.nation2Name() + "|" + data.formedAt().toEpochMilli();
            properties.setProperty(propKey, propValue);
        }
        persistenceService.savePropertiesAsync("diplomacy", FILE_NAME, properties);
    }

    /**
     * 保存邀请数据
     */
    public void saveInvites(Map<AlliancePairKey, Instant> invites) {
        Properties properties = new Properties();
        for (Map.Entry<AlliancePairKey, Instant> entry : invites.entrySet()) {
            AlliancePairKey key = entry.getKey();
            Instant time = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, String.valueOf(time.toEpochMilli()));
        }
        persistenceService.savePropertiesAsync("diplomacy", INVITES_FILE_NAME, properties);
    }

    /**
     * 保存冷却时间数据
     */
    public void saveCooldowns(Map<AlliancePairKey, Instant> cooldowns) {
        Properties properties = new Properties();
        for (Map.Entry<AlliancePairKey, Instant> entry : cooldowns.entrySet()) {
            AlliancePairKey key = entry.getKey();
            Instant time = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, String.valueOf(time.toEpochMilli()));
        }
        persistenceService.savePropertiesAsync("diplomacy", COOLDOWNS_FILE_NAME, properties);
    }

    /**
     * 加载联盟关系数据
     */
    public Map<AlliancePairKey, AllianceInfoData> loadAlliances() {
        Map<AlliancePairKey, AllianceInfoData> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties("diplomacy", FILE_NAME);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    try {
                        UUID leftUuid = UUID.fromString(parts[0]);
                        UUID rightUuid = UUID.fromString(parts[1]);
                        String value = properties.getProperty(key);
                        String[] valueParts = value.split("\\|");
                        if (valueParts.length >= 3) {
                            String nation1Name = valueParts[0];
                            String nation2Name = valueParts[1];
                            long epochMilli = Long.parseLong(valueParts[2]);
                            Instant formedAt = Instant.ofEpochMilli(epochMilli);

                            AlliancePairKey pairKey = new AlliancePairKey(
                                NationId.of(leftUuid),
                                NationId.of(rightUuid)
                            );
                            AllianceInfoData data = new AllianceInfoData(
                                NationId.of(leftUuid),
                                NationId.of(rightUuid),
                                nation1Name,
                                nation2Name,
                                formedAt
                            );
                            result.put(pairKey, data);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse alliance data: {} - {}", key, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load alliance data: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 加载邀请数据
     */
    public Map<AlliancePairKey, Instant> loadInvites() {
        Map<AlliancePairKey, Instant> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties("diplomacy", INVITES_FILE_NAME);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    try {
                        UUID leftUuid = UUID.fromString(parts[0]);
                        UUID rightUuid = UUID.fromString(parts[1]);
                        long epochMilli = Long.parseLong(properties.getProperty(key));
                        Instant time = Instant.ofEpochMilli(epochMilli);

                        AlliancePairKey pairKey = new AlliancePairKey(
                            NationId.of(leftUuid),
                            NationId.of(rightUuid)
                        );
                        result.put(pairKey, time);
                    } catch (Exception e) {
                        logger.warn("Failed to parse invite data: {} - {}", key, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load invite data: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 加载冷却时间数据
     */
    public Map<AlliancePairKey, Instant> loadCooldowns() {
        Map<AlliancePairKey, Instant> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties("diplomacy", COOLDOWNS_FILE_NAME);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    try {
                        UUID leftUuid = UUID.fromString(parts[0]);
                        UUID rightUuid = UUID.fromString(parts[1]);
                        long epochMilli = Long.parseLong(properties.getProperty(key));
                        Instant time = Instant.ofEpochMilli(epochMilli);

                        AlliancePairKey pairKey = new AlliancePairKey(
                            NationId.of(leftUuid),
                            NationId.of(rightUuid)
                        );
                        result.put(pairKey, time);
                    } catch (Exception e) {
                        logger.warn("Failed to parse cooldown data: {} - {}", key, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load cooldown data: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 同步保存（阻塞）
     */
    public void saveAlliancesSync(Map<AlliancePairKey, AllianceInfoData> alliances) {
        Properties properties = new Properties();
        for (Map.Entry<AlliancePairKey, AllianceInfoData> entry : alliances.entrySet()) {
            AlliancePairKey key = entry.getKey();
            AllianceInfoData data = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            String propValue = data.nation1Name() + "|" + data.nation2Name() + "|" + data.formedAt().toEpochMilli();
            properties.setProperty(propKey, propValue);
        }
        persistenceService.saveProperties("diplomacy", FILE_NAME, properties);
    }

    /**
     * 同步保存邀请
     */
    public void saveInvitesSync(Map<AlliancePairKey, Instant> invites) {
        Properties properties = new Properties();
        for (Map.Entry<AlliancePairKey, Instant> entry : invites.entrySet()) {
            AlliancePairKey key = entry.getKey();
            Instant time = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, String.valueOf(time.toEpochMilli()));
        }
        persistenceService.saveProperties("diplomacy", INVITES_FILE_NAME, properties);
    }

    /**
     * 同步保存冷却时间
     */
    public void saveCooldownsSync(Map<AlliancePairKey, Instant> cooldowns) {
        Properties properties = new Properties();
        for (Map.Entry<AlliancePairKey, Instant> entry : cooldowns.entrySet()) {
            AlliancePairKey key = entry.getKey();
            Instant time = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, String.valueOf(time.toEpochMilli()));
        }
        persistenceService.saveProperties("diplomacy", COOLDOWNS_FILE_NAME, properties);
    }
}
