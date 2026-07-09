package dev.starcore.starcore.module.alliance;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 联盟数据持久化存储
 *
 * 支持将联盟数据保存到 Properties 文件中：
 * - 联盟基本信息
 * - 联盟成员列表
 * - 国家-联盟映射
 * - 待处理邀请
 * - 联盟外交关系
 * - 联盟公告
 */
public class AllianceStorage {

    private static final String ALLIANCES_FILE = "alliance_data.properties";
    private static final String MEMBERS_FILE = "alliance_members.properties";
    private static final String NATION_MAP_FILE = "nation_alliance_map.properties";
    private static final String INVITES_FILE = "alliance_invites.properties";
    private static final String RELATIONS_FILE = "alliance_relations.properties";
    private static final String ANNOUNCEMENTS_FILE = "alliance_announcements.properties";

    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;

    private static final String NAMESPACE = "alliance";

    public AllianceStorage(
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger
    ) {
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.logger = logger;
    }

    // ==================== 联盟数据 ====================

    /**
     * 保存所有联盟数据
     */
    public void saveAlliances(Map<UUID, AllianceService.Alliance> alliances) {
        Properties properties = new Properties();
        for (Map.Entry<UUID, AllianceService.Alliance> entry : alliances.entrySet()) {
            UUID id = entry.getKey();
            AllianceService.Alliance alliance = entry.getValue();
            String prefix = id.toString() + ".";
            properties.setProperty(prefix + "name", alliance.name());
            properties.setProperty(prefix + "leader", alliance.leaderId().value().toString());
            properties.setProperty(prefix + "created", String.valueOf(alliance.createdAt().toEpochMilli()));
            properties.setProperty(prefix + "emblem", alliance.emblem() != null ? alliance.emblem() : "");
        }
        persistenceService.savePropertiesAsync(NAMESPACE, ALLIANCES_FILE, properties);
    }

    /**
     * 加载所有联盟数据
     */
    public Map<UUID, AllianceService.Alliance> loadAlliances() {
        Map<UUID, AllianceService.Alliance> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties(NAMESPACE, ALLIANCES_FILE);
            Set<String> allianceIds = new HashSet<>();

            for (String key : properties.stringPropertyNames()) {
                int dotIndex = key.indexOf('.');
                if (dotIndex > 0) {
                    allianceIds.add(key.substring(0, dotIndex));
                }
            }

            for (String idStr : allianceIds) {
                try {
                    UUID id = UUID.fromString(idStr);
                    String name = properties.getProperty(idStr + ".name", "");
                    String leaderStr = properties.getProperty(idStr + ".leader", "");
                    String createdStr = properties.getProperty(idStr + ".created", "0");
                    String emblem = properties.getProperty(idStr + ".emblem", "");

                    if (!name.isEmpty() && !leaderStr.isEmpty()) {
                        UUID leaderUuid = UUID.fromString(leaderStr);
                        NationId leaderId = NationId.of(leaderUuid);
                        Instant created = Instant.ofEpochMilli(Long.parseLong(createdStr));
                        String emblemValue = emblem.isEmpty() ? null : emblem;

                        AllianceService.Alliance alliance = new AllianceService.Alliance(
                            id, name, leaderId, created, emblemValue
                        );
                        result.put(id, alliance);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse alliance {}: {}", idStr, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load alliances: {}", e.getMessage());
        }

        return result;
    }

    // ==================== 成员数据 ====================

    /**
     * 保存联盟成员数据
     */
    public void saveMembers(Map<UUID, List<AllianceService.AllianceMember>> members) {
        Properties properties = new Properties();
        for (Map.Entry<UUID, List<AllianceService.AllianceMember>> entry : members.entrySet()) {
            UUID allianceId = entry.getKey();
            List<AllianceService.AllianceMember> memberList = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (AllianceService.AllianceMember member : memberList) {
                if (sb.length() > 0) sb.append(";");
                sb.append(member.nationId().value().toString())
                  .append(",")
                  .append(member.role().name())
                  .append(",")
                  .append(member.joinedAt().toEpochMilli());
            }
            properties.setProperty(allianceId.toString(), sb.toString());
        }
        persistenceService.savePropertiesAsync(NAMESPACE, MEMBERS_FILE, properties);
    }

    /**
     * 加载联盟成员数据
     */
    public Map<UUID, List<AllianceService.AllianceMember>> loadMembers() {
        Map<UUID, List<AllianceService.AllianceMember>> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties(NAMESPACE, MEMBERS_FILE);
            for (String key : properties.stringPropertyNames()) {
                try {
                    UUID allianceId = UUID.fromString(key);
                    String value = properties.getProperty(key, "");
                    List<AllianceService.AllianceMember> memberList = new ArrayList<>();

                    if (!value.isEmpty()) {
                        String[] memberStrs = value.split(";");
                        for (String memberStr : memberStrs) {
                            String[] parts = memberStr.split(",");
                            if (parts.length >= 3) {
                                UUID nationUuid = UUID.fromString(parts[0]);
                                AllianceService.AllianceMember.Role role =
                                    AllianceService.AllianceMember.Role.valueOf(parts[1]);
                                Instant joinedAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));

                                AllianceService.AllianceMember member = new AllianceService.AllianceMember(
                                    NationId.of(nationUuid), role, joinedAt
                                );
                                memberList.add(member);
                            }
                        }
                    }

                    result.put(allianceId, memberList);
                } catch (Exception e) {
                    logger.warn("Failed to parse member data for {}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load members: {}", e.getMessage());
        }

        return result;
    }

    // ==================== 国家-联盟映射 ====================

    /**
     * 保存国家-联盟映射
     */
    public void saveNationAllianceMap(Map<NationId, UUID> nationMap) {
        Properties properties = new Properties();
        for (Map.Entry<NationId, UUID> entry : nationMap.entrySet()) {
            properties.setProperty(entry.getKey().value().toString(), entry.getValue().toString());
        }
        persistenceService.savePropertiesAsync(NAMESPACE, NATION_MAP_FILE, properties);
    }

    /**
     * 加载国家-联盟映射
     */
    public Map<NationId, UUID> loadNationAllianceMap() {
        Map<NationId, UUID> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties(NAMESPACE, NATION_MAP_FILE);
            for (String key : properties.stringPropertyNames()) {
                try {
                    UUID nationUuid = UUID.fromString(key);
                    UUID allianceId = UUID.fromString(properties.getProperty(key));
                    result.put(NationId.of(nationUuid), allianceId);
                } catch (Exception e) {
                    logger.warn("Failed to parse nation-alliance mapping {}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load nation-alliance map: {}", e.getMessage());
        }

        return result;
    }

    // ==================== 待处理邀请 ====================

    /**
     * 保存待处理邀请
     */
    public void savePendingInvites(Map<NationId, AllianceService.AllianceInviteInfo> invites) {
        Properties properties = new Properties();
        for (Map.Entry<NationId, AllianceService.AllianceInviteInfo> entry : invites.entrySet()) {
            AllianceService.AllianceInviteInfo invite = entry.getValue();
            String key = entry.getKey().value().toString();
            String value = String.format("%s|%s|%s|%s|%s",
                invite.allianceId().toString(),
                invite.allianceName(),
                invite.invitedAt().toEpochMilli(),
                invite.invitedBy().value().toString(),
                invite.expiresAt().toEpochMilli()
            );
            properties.setProperty(key, value);
        }
        persistenceService.savePropertiesAsync(NAMESPACE, INVITES_FILE, properties);
    }

    /**
     * 加载待处理邀请
     */
    public Map<NationId, AllianceService.AllianceInviteInfo> loadPendingInvites() {
        Map<NationId, AllianceService.AllianceInviteInfo> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties(NAMESPACE, INVITES_FILE);
            for (String key : properties.stringPropertyNames()) {
                try {
                    UUID nationUuid = UUID.fromString(key);
                    String value = properties.getProperty(key, "");
                    String[] parts = value.split("\\|");

                    if (parts.length >= 5) {
                        UUID allianceId = UUID.fromString(parts[0]);
                        String allianceName = parts[1];
                        Instant invitedAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));
                        UUID invitedByUuid = UUID.fromString(parts[3]);
                        Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(parts[4]));

                        AllianceService.AllianceInviteInfo invite = new AllianceService.AllianceInviteInfo(
                            allianceId,
                            allianceName,
                            NationId.of(nationUuid),
                            invitedAt,
                            NationId.of(invitedByUuid),
                            expiresAt
                        );
                        result.put(NationId.of(nationUuid), invite);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse invite data {}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load invites: {}", e.getMessage());
        }

        return result;
    }

    // ==================== 联盟外交关系 ====================

    /**
     * 保存联盟外交关系
     */
    public void saveRelations(Map<AllianceServiceImpl.AlliancePairKey, AllianceService.AllianceRelation> relations) {
        Properties properties = new Properties();
        for (Map.Entry<AllianceServiceImpl.AlliancePairKey, AllianceService.AllianceRelation> entry : relations.entrySet()) {
            AllianceService.AllianceRelation relation = entry.getValue();
            String key = entry.getKey().alliance1().toString() + ":" + entry.getKey().alliance2().toString();
            String value = String.format("%s|%s|%s",
                relation.type().name(),
                relation.startedAt().toEpochMilli(),
                relation.notes() != null ? relation.notes() : ""
            );
            properties.setProperty(key, value);
        }
        persistenceService.savePropertiesAsync(NAMESPACE, RELATIONS_FILE, properties);
    }

    /**
     * 加载联盟外交关系
     */
    public Map<AllianceServiceImpl.AlliancePairKey, AllianceService.AllianceRelation> loadRelations() {
        Map<AllianceServiceImpl.AlliancePairKey, AllianceService.AllianceRelation> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties(NAMESPACE, RELATIONS_FILE);
            for (String key : properties.stringPropertyNames()) {
                try {
                    String[] keyParts = key.split(":");
                    if (keyParts.length == 2) {
                        UUID alliance1 = UUID.fromString(keyParts[0]);
                        UUID alliance2 = UUID.fromString(keyParts[1]);

                        String value = properties.getProperty(key, "");
                        String[] parts = value.split("\\|");

                        if (parts.length >= 2) {
                            AllianceService.AllianceRelationType type =
                                AllianceService.AllianceRelationType.valueOf(parts[0]);
                            Instant startedAt = Instant.ofEpochMilli(Long.parseLong(parts[1]));
                            String notes = parts.length > 2 ? parts[2] : null;

                            AllianceService.AllianceRelation relation = new AllianceService.AllianceRelation(
                                alliance1, alliance2, type, startedAt, notes
                            );

                            AllianceServiceImpl.AlliancePairKey pairKey =
                                AllianceServiceImpl.AlliancePairKey.of(alliance1, alliance2);
                            result.put(pairKey, relation);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse relation data {}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load relations: {}", e.getMessage());
        }

        return result;
    }

    // ==================== 联盟公告 ====================

    /**
     * 保存联盟公告
     */
    public void saveAnnouncements(Map<UUID, AllianceService.AllianceAnnouncement> announcements) {
        Properties properties = new Properties();
        for (Map.Entry<UUID, AllianceService.AllianceAnnouncement> entry : announcements.entrySet()) {
            AllianceService.AllianceAnnouncement ann = entry.getValue();
            String key = entry.getKey().toString();
            String value = String.format("%s|%s|%s|%s",
                ann.content(),
                ann.publishedBy().value().toString(),
                ann.publishedAt().toEpochMilli(),
                ann.allianceId().toString()
            );
            properties.setProperty(key, value);
        }
        persistenceService.savePropertiesAsync(NAMESPACE, ANNOUNCEMENTS_FILE, properties);
    }

    /**
     * 加载联盟公告
     */
    public Map<UUID, AllianceService.AllianceAnnouncement> loadAnnouncements() {
        Map<UUID, AllianceService.AllianceAnnouncement> result = new HashMap<>();

        try {
            Properties properties = persistenceService.loadProperties(NAMESPACE, ANNOUNCEMENTS_FILE);
            for (String key : properties.stringPropertyNames()) {
                try {
                    UUID allianceId = UUID.fromString(key);
                    String value = properties.getProperty(key, "");
                    String[] parts = value.split("\\|", 4);

                    if (parts.length >= 3) {
                        String content = parts[0];
                        UUID publisherUuid = UUID.fromString(parts[1]);
                        Instant publishedAt = Instant.ofEpochMilli(Long.parseLong(parts[2]));

                        AllianceService.AllianceAnnouncement ann = new AllianceService.AllianceAnnouncement(
                            allianceId,
                            content,
                            NationId.of(publisherUuid),
                            publishedAt
                        );
                        result.put(allianceId, ann);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse announcement {}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load announcements: {}", e.getMessage());
        }

        return result;
    }

    // ==================== 同步保存方法 ====================

    /**
     * 同步保存所有数据（阻塞）
     */
    public void saveAllSync(
            Map<UUID, AllianceService.Alliance> alliances,
            Map<UUID, List<AllianceService.AllianceMember>> members,
            Map<NationId, UUID> nationMap,
            Map<NationId, AllianceService.AllianceInviteInfo> invites,
            Map<AllianceServiceImpl.AlliancePairKey, AllianceService.AllianceRelation> relations,
            Map<UUID, AllianceService.AllianceAnnouncement> announcements
    ) {
        saveAlliances(alliances);
        saveMembers(members);
        saveNationAllianceMap(nationMap);
        savePendingInvites(invites);
        saveRelations(relations);
        saveAnnouncements(announcements);
    }
}
