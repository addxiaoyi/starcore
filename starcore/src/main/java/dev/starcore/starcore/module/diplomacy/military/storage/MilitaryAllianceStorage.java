package dev.starcore.starcore.module.diplomacy.military.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.PactType;
import dev.starcore.starcore.module.diplomacy.military.MilitaryPactData;
import dev.starcore.starcore.module.diplomacy.military.MilitaryPactKey;
import dev.starcore.starcore.module.nation.model.NationId;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 军事联盟数据存储实现
 * 支持 Properties 文件持久化
 */
public class MilitaryAllianceStorage {

    private static final String FILE_NAME = "military_alliance.properties";
    private static final String INVITES_FILE_NAME = "military_alliance_invites.properties";
    private static final String COOLDOWNS_FILE_NAME = "military_alliance_cooldowns.properties";

    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;

    private CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);
    private final Object saveLock = new Object();

    public MilitaryAllianceStorage(
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger
    ) {
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.logger = logger;
    }

    /**
     * 保存军事联盟关系数据
     */
    public void savePacts(Map<MilitaryPactKey, MilitaryPactData> pacts) {
        Properties properties = new Properties();
        for (Map.Entry<MilitaryPactKey, MilitaryPactData> entry : pacts.entrySet()) {
            MilitaryPactKey key = entry.getKey();
            MilitaryPactData data = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            String propValue = data.nation1Name() + "|" + data.nation2Name() + "|" +
                data.pactType().name() + "|" + data.formedAt().toEpochMilli() + "|" + data.upgradedAt().toEpochMilli();
            properties.setProperty(propKey, propValue);
        }
        persistenceService.savePropertiesAsync("military-alliance", FILE_NAME, properties);
    }

    /**
     * 保存邀请数据
     */
    public void saveInvites(Map<MilitaryPactKey, PactInviteRecord> invites) {
        Properties properties = new Properties();
        for (Map.Entry<MilitaryPactKey, PactInviteRecord> entry : invites.entrySet()) {
            MilitaryPactKey key = entry.getKey();
            PactInviteRecord record = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, record.pactType().name() + "|" + record.invitedAt().toEpochMilli());
        }
        persistenceService.savePropertiesAsync("military-alliance", INVITES_FILE_NAME, properties);
    }

    /**
     * 保存冷却时间数据
     */
    public void saveCooldowns(Map<MilitaryPactKey, Instant> cooldowns) {
        Properties properties = new Properties();
        for (Map.Entry<MilitaryPactKey, Instant> entry : cooldowns.entrySet()) {
            MilitaryPactKey key = entry.getKey();
            Instant time = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, String.valueOf(time.toEpochMilli()));
        }
        persistenceService.savePropertiesAsync("military-alliance", COOLDOWNS_FILE_NAME, properties);
    }

    /**
     * 加载军事联盟关系数据
     */
    public Map<MilitaryPactKey, MilitaryPactData> loadPacts() {
        Map<MilitaryPactKey, MilitaryPactData> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties("military-alliance", FILE_NAME);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    try {
                        UUID leftUuid = UUID.fromString(parts[0]);
                        UUID rightUuid = UUID.fromString(parts[1]);
                        String value = properties.getProperty(key);
                        String[] valueParts = value.split("\\|");
                        if (valueParts.length >= 5) {
                            String nation1Name = valueParts[0];
                            String nation2Name = valueParts[1];
                            PactType pactType = PactType.valueOf(valueParts[2]);
                            long formedMilli = Long.parseLong(valueParts[3]);
                            long upgradedMilli = Long.parseLong(valueParts[4]);
                            Instant formedAt = Instant.ofEpochMilli(formedMilli);
                            Instant upgradedAt = Instant.ofEpochMilli(upgradedMilli);

                            MilitaryPactKey pactKey = new MilitaryPactKey(
                                NationId.of(leftUuid),
                                NationId.of(rightUuid)
                            );
                            MilitaryPactData data = new MilitaryPactData(
                                NationId.of(leftUuid),
                                NationId.of(rightUuid),
                                nation1Name,
                                nation2Name,
                                pactType,
                                formedAt,
                                upgradedAt
                            );
                            result.put(pactKey, data);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse military alliance data: {} - {}", key, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load military alliance data: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 加载邀请数据
     */
    public Map<MilitaryPactKey, PactInviteRecord> loadInvites() {
        Map<MilitaryPactKey, PactInviteRecord> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties("military-alliance", INVITES_FILE_NAME);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    try {
                        UUID leftUuid = UUID.fromString(parts[0]);
                        UUID rightUuid = UUID.fromString(parts[1]);
                        String value = properties.getProperty(key);
                        String[] valueParts = value.split("\\|");
                        if (valueParts.length >= 2) {
                            PactType pactType = PactType.valueOf(valueParts[0]);
                            long epochMilli = Long.parseLong(valueParts[1]);
                            Instant time = Instant.ofEpochMilli(epochMilli);

                            MilitaryPactKey pactKey = new MilitaryPactKey(
                                NationId.of(leftUuid),
                                NationId.of(rightUuid)
                            );
                            result.put(pactKey, new PactInviteRecord(pactType, time));
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse military alliance invite data: {} - {}", key, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load military alliance invite data: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 加载冷却时间数据
     */
    public Map<MilitaryPactKey, Instant> loadCooldowns() {
        Map<MilitaryPactKey, Instant> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties("military-alliance", COOLDOWNS_FILE_NAME);
            for (String key : properties.stringPropertyNames()) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    try {
                        UUID leftUuid = UUID.fromString(parts[0]);
                        UUID rightUuid = UUID.fromString(parts[1]);
                        long epochMilli = Long.parseLong(properties.getProperty(key));
                        Instant time = Instant.ofEpochMilli(epochMilli);

                        MilitaryPactKey pactKey = new MilitaryPactKey(
                            NationId.of(leftUuid),
                            NationId.of(rightUuid)
                        );
                        result.put(pactKey, time);
                    } catch (Exception e) {
                        logger.warn("Failed to parse military alliance cooldown data: {} - {}", key, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load military alliance cooldown data: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 同步保存（阻塞）
     */
    public void savePactsSync(Map<MilitaryPactKey, MilitaryPactData> pacts) {
        Properties properties = new Properties();
        for (Map.Entry<MilitaryPactKey, MilitaryPactData> entry : pacts.entrySet()) {
            MilitaryPactKey key = entry.getKey();
            MilitaryPactData data = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            String propValue = data.nation1Name() + "|" + data.nation2Name() + "|" +
                data.pactType().name() + "|" + data.formedAt().toEpochMilli() + "|" + data.upgradedAt().toEpochMilli();
            properties.setProperty(propKey, propValue);
        }
        persistenceService.saveProperties("military-alliance", FILE_NAME, properties);
    }

    /**
     * 同步保存邀请
     */
    public void saveInvitesSync(Map<MilitaryPactKey, PactInviteRecord> invites) {
        Properties properties = new Properties();
        for (Map.Entry<MilitaryPactKey, PactInviteRecord> entry : invites.entrySet()) {
            MilitaryPactKey key = entry.getKey();
            PactInviteRecord record = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, record.pactType().name() + "|" + record.invitedAt().toEpochMilli());
        }
        persistenceService.saveProperties("military-alliance", INVITES_FILE_NAME, properties);
    }

    /**
     * 同步保存冷却时间
     */
    public void saveCooldownsSync(Map<MilitaryPactKey, Instant> cooldowns) {
        Properties properties = new Properties();
        for (Map.Entry<MilitaryPactKey, Instant> entry : cooldowns.entrySet()) {
            MilitaryPactKey key = entry.getKey();
            Instant time = entry.getValue();
            String propKey = key.left().value() + ":" + key.right().value();
            properties.setProperty(propKey, String.valueOf(time.toEpochMilli()));
        }
        persistenceService.saveProperties("military-alliance", COOLDOWNS_FILE_NAME, properties);
    }

    /**
     * 邀请记录数据类
     */
    public record PactInviteRecord(PactType pactType, Instant invitedAt) {}
}