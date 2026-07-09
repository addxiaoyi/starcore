package dev.starcore.starcore.module.vassal.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.vassal.model.VassalRelation;
import dev.starcore.starcore.module.vassal.model.VassalRelationKey;
import dev.starcore.starcore.module.vassal.model.VassalType;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宗藩关系数据库存储
 * Database storage for vassal relations
 */
public class VassalStorage {

    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;
    private final String namespace;

    public VassalStorage(
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger) {
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.logger = logger;
        this.namespace = "vassal";
    }

    /**
     * 初始化数据库表
     */
    public void initializeTables() {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = """
                CREATE TABLE IF NOT EXISTS vassal_relations (
                    id VARCHAR(36) PRIMARY KEY,
                    suzerain_uuid VARCHAR(36) NOT NULL,
                    vassal_uuid VARCHAR(36) NOT NULL,
                    type VARCHAR(32) NOT NULL,
                    formed_at BIGINT NOT NULL,
                    tribute_amount VARCHAR(64) DEFAULT '0',
                    last_tribute_at BIGINT,
                    protection_enabled INTEGER DEFAULT 1,
                    UNIQUE(suzerain_uuid, vassal_uuid)
                )
                """;
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                logger.info("Vassal relations table initialized");
            } catch (SQLException e) {
                logger.error("Failed to initialize vassal relations table", e);
            }
        });
    }

    /**
     * 保存宗藩关系
     */
    public void saveRelations(Map<VassalRelationKey, VassalRelation> relations) {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = """
                INSERT OR REPLACE INTO vassal_relations
                (id, suzerain_uuid, vassal_uuid, type, formed_at, tribute_amount, last_tribute_at, protection_enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                conn.setAutoCommit(false);

                for (VassalRelation relation : relations.values()) {
                    String id = relation.suzerainId().value().toString() + "_" + relation.vassalId().value().toString();
                    stmt.setString(1, id);
                    stmt.setString(2, relation.suzerainId().value().toString());
                    stmt.setString(3, relation.vassalId().value().toString());
                    stmt.setString(4, relation.type().name());
                    stmt.setLong(5, relation.formedAt().toEpochMilli());
                    stmt.setString(6, relation.tributeAmount().toPlainString());
                    stmt.setObject(7, relation.lastTributeAt() != null ? relation.lastTributeAt().toEpochMilli() : null);
                    stmt.setInt(8, relation.protectionEnabled() ? 1 : 0);
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                logger.error("Failed to save vassal relations", e);
            }
        });
    }

    /**
     * 加载宗藩关系
     */
    public Optional<Map<VassalRelationKey, VassalRelation>> loadRelations() {
        Map<VassalRelationKey, VassalRelation> result = new ConcurrentHashMap<>();

        return databaseService.dataSource().map(ds -> {
            String sql = "SELECT * FROM vassal_relations";

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    NationId suzerainId = NationId.of(UUID.fromString(rs.getString("suzerain_uuid")));
                    NationId vassalId = NationId.of(UUID.fromString(rs.getString("vassal_uuid")));
                    VassalType type = VassalType.valueOf(rs.getString("type"));
                    Instant formedAt = Instant.ofEpochMilli(rs.getLong("formed_at"));
                    BigDecimal tributeAmount = new BigDecimal(rs.getString("tribute_amount"));
                    Instant lastTributeAt = rs.getObject("last_tribute_at") != null
                        ? Instant.ofEpochMilli(rs.getLong("last_tribute_at"))
                        : null;
                    boolean protectionEnabled = rs.getInt("protection_enabled") == 1;

                    VassalRelation relation = new VassalRelation(
                        suzerainId, vassalId, type, formedAt, tributeAmount, lastTributeAt, protectionEnabled
                    );

                    result.put(VassalRelationKey.of(suzerainId, vassalId), relation);
                }

                logger.info("Loaded " + result.size() + " vassal relations");
            } catch (SQLException e) {
                logger.error("Failed to load vassal relations", e);
            }

            return result;
        }).or(() -> {
            // 从 properties 文件加载
            Properties props = persistenceService.loadProperties(namespace, "vassals.properties");
            // 简化实现，实际应解析 properties
            return Optional.of(result);
        }).filter(m -> !m.isEmpty());
    }

    /**
     * 保存邀请记录
     */
    public void saveInvites(Map<VassalRelationKey, Instant> invites) {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = """
                INSERT OR REPLACE INTO vassal_invites
                (suzerain_uuid, vassal_uuid, invite_at)
                VALUES (?, ?, ?)
                """;

            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                conn.setAutoCommit(false);

                for (Map.Entry<VassalRelationKey, Instant> entry : invites.entrySet()) {
                    stmt.setString(1, entry.getKey().suzerainId().value().toString());
                    stmt.setString(2, entry.getKey().vassalId().value().toString());
                    stmt.setLong(3, entry.getValue().toEpochMilli());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                logger.error("Failed to save vassal invites", e);
            }
        });
    }

    /**
     * 加载邀请记录
     */
    public Optional<Map<VassalRelationKey, Instant>> loadInvites() {
        Map<VassalRelationKey, Instant> result = new ConcurrentHashMap<>();

        return databaseService.dataSource().map(ds -> {
            String sql = "SELECT * FROM vassal_invites";

            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    NationId suzerainId = NationId.of(UUID.fromString(rs.getString("suzerain_uuid")));
                    NationId vassalId = NationId.of(UUID.fromString(rs.getString("vassal_uuid")));
                    Instant inviteAt = Instant.ofEpochMilli(rs.getLong("invite_at"));

                    result.put(VassalRelationKey.of(suzerainId, vassalId), inviteAt);
                }
            } catch (SQLException e) {
                logger.error("Failed to load vassal invites", e);
            }

            return result;
        }).or(() -> Optional.of(result));
    }
}