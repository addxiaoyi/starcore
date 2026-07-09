package dev.starcore.starcore.social.mail.attachment.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService.MailAttachment;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 邮件附件存储实现
 *
 * 提供附件数据的数据库持久化功能
 */
public final class MailAttachmentStorage {

    private final DatabaseService databaseService;
    private final Logger logger;

    // 内存缓存: mailId -> List<MailAttachment>
    private final Map<UUID, List<MailAttachment>> attachmentCache = new ConcurrentHashMap<>();

    public MailAttachmentStorage(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
    }

    /**
     * 确保数据库表存在
     */
    public void ensureTables() {
        if (!databaseService.isRunning()) {
            logger.info("邮件附件存储使用缓存模式（数据库不可用）");
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement()) {

                    // 邮件附件表
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS starcore_mail_attachments_v2 (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            mail_id VARCHAR(36) NOT NULL,
                            item_id VARCHAR(64) NOT NULL,
                            amount INT NOT NULL DEFAULT 1,
                            serialized_item TEXT,
                            attached_at BIGINT NOT NULL,
                            slot_index INT DEFAULT 0,
                            FOREIGN KEY (mail_id) REFERENCES starcore_mails(id) ON DELETE CASCADE,
                            INDEX idx_mail_id (mail_id)
                        )
                    """);

                    logger.info("邮件附件存储表已初始化");
                } catch (SQLException e) {
                    logger.warning("初始化邮件附件存储表失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("邮件附件存储初始化失败: " + e.getMessage());
        }
    }

    /**
     * 保存邮件附件
     * @param mailId 邮件ID
     * @param attachments 附件列表
     */
    public void saveAttachments(UUID mailId, List<MailAttachment> attachments) {
        if (mailId == null) {
            return;
        }

        // 更新缓存
        attachmentCache.put(mailId, new ArrayList<>(attachments));

        // 保存到数据库
        if (databaseService.isRunning()) {
            saveToDatabase(mailId, attachments);
        }
    }

    /**
     * 保存附件到数据库
     */
    private void saveToDatabase(UUID mailId, List<MailAttachment> attachments) {
        try {
            databaseService.dataSource().ifPresent(ds -> {
                String deleteSql = "DELETE FROM starcore_mail_attachments_v2 WHERE mail_id = ?";
                String insertSql = """
                    INSERT INTO starcore_mail_attachments_v2
                    (mail_id, item_id, amount, serialized_item, attached_at, slot_index)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;

                try (Connection conn = ds.getConnection()) {
                    conn.setAutoCommit(false);

                    try (PreparedStatement delStmt = conn.prepareStatement(deleteSql)) {
                        delStmt.setString(1, mailId.toString());
                        delStmt.executeUpdate();
                    }

                    if (attachments != null && !attachments.isEmpty()) {
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            for (int i = 0; i < attachments.size(); i++) {
                                MailAttachment attach = attachments.get(i);
                                insertStmt.setString(1, mailId.toString());
                                insertStmt.setString(2, attach.itemId());
                                insertStmt.setInt(3, attach.amount());
                                insertStmt.setString(4, attach.serializedItem());
                                insertStmt.setLong(5, attach.attachedAt());
                                insertStmt.setInt(6, i);
                                insertStmt.addBatch();
                            }
                            insertStmt.executeBatch();
                        }
                    }

                    conn.commit();
                } catch (SQLException e) {
                    logger.warning("保存邮件附件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("保存邮件附件失败: " + e.getMessage());
        }
    }

    /**
     * 加载邮件附件
     * @param mailId 邮件ID
     * @return 附件列表
     */
    public List<MailAttachment> loadAttachments(UUID mailId) {
        if (mailId == null) {
            return List.of();
        }

        // 检查缓存
        List<MailAttachment> cached = attachmentCache.get(mailId);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        // 从数据库加载
        if (databaseService.isRunning()) {
            List<MailAttachment> attachments = loadFromDatabase(mailId);
            if (!attachments.isEmpty()) {
                attachmentCache.put(mailId, attachments);
            }
            return attachments;
        }

        return List.of();
    }

    /**
     * 从数据库加载附件
     */
    private List<MailAttachment> loadFromDatabase(UUID mailId) {
        List<MailAttachment> attachments = new ArrayList<>();

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = """
                    SELECT * FROM starcore_mail_attachments_v2
                    WHERE mail_id = ?
                    ORDER BY slot_index ASC
                """;

                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, mailId.toString());

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String itemId = rs.getString("item_id");
                            int amount = rs.getInt("amount");
                            String serializedItem = rs.getString("serialized_item");
                            long attachedAt = rs.getLong("attached_at");

                            attachments.add(new MailAttachment(itemId, amount, serializedItem, attachedAt));
                        }
                    }
                } catch (SQLException e) {
                    logger.warning("加载邮件附件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("加载邮件附件失败: " + e.getMessage());
        }

        return attachments;
    }

    /**
     * 删除邮件附件
     * @param mailId 邮件ID
     */
    public void deleteAttachments(UUID mailId) {
        // 从缓存移除
        attachmentCache.remove(mailId);

        // 从数据库删除
        if (databaseService.isRunning()) {
            deleteFromDatabase(mailId);
        }
    }

    /**
     * 从数据库删除附件
     */
    private void deleteFromDatabase(UUID mailId) {
        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "DELETE FROM starcore_mail_attachments_v2 WHERE mail_id = ?";

                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, mailId.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logger.warning("删除邮件附件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("删除邮件附件失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期附件（超过指定天数）
     * @param expirationDays 过期天数
     * @return 清理数量
     */
    public int cleanupExpiredAttachments(long expirationDays) {
        if (!databaseService.isRunning()) {
            return 0;
        }

        long expirationTime = System.currentTimeMillis() - (expirationDays * 24 * 60 * 60 * 1000);

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = """
                    DELETE FROM starcore_mail_attachments_v2
                    WHERE mail_id IN (
                        SELECT id FROM starcore_mails WHERE created_at < ?
                    )
                """;
                long finalExpTime = expirationTime;

                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setLong(1, finalExpTime);
                    int count = stmt.executeUpdate();

                    if (count > 0) {
                        logger.info("清理了 " + count + " 个过期邮件附件");
                    }
                } catch (SQLException e) {
                    logger.warning("清理过期附件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("清理过期附件失败: " + e.getMessage());
        }

        // 清空缓存
        attachmentCache.clear();

        return 0; // 返回 0 因为在 lambda 中无法获取准确数量
    }

    /**
     * 获取附件总数
     */
    public long getTotalAttachmentCount() {
        if (!databaseService.isRunning()) {
            return attachmentCache.values().stream().mapToInt(List::size).sum();
        }

        try {
            return databaseService.dataSource().map(ds -> {
                String sql = "SELECT COUNT(*) FROM starcore_mail_attachments_v2";
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                } catch (SQLException e) {
                    logger.warning("获取附件总数失败: " + e.getMessage());
                }
                return 0L;
            }).orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 保存所有缓存数据
     */
    public void saveAll() {
        if (!databaseService.isRunning()) {
            return;
        }

        for (Map.Entry<UUID, List<MailAttachment>> entry : attachmentCache.entrySet()) {
            saveToDatabase(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        attachmentCache.clear();
    }
}
