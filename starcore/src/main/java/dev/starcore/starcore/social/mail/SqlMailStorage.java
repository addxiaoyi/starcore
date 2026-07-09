package dev.starcore.starcore.social.mail;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.database.DatabaseUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 邮件系统 SQL 存储实现
 */
public final class SqlMailStorage {

    private final DatabaseService databaseService;
    private final Logger logger;

    public SqlMailStorage(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
    }

    private Connection getConnection() throws SQLException {
        return databaseService.dataSource()
            .orElseThrow(() -> new SQLException("Database not available"))
            .getConnection();
    }

    /**
     * 创建邮件表
     */
    public void ensureTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
            boolean isSQLite = dbType == DatabaseUtils.DatabaseType.SQLITE;

            if (isSQLite) {
                // SQLite 版本
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS starcore_mails (
                        id TEXT PRIMARY KEY,
                        sender_uuid TEXT NOT NULL,
                        sender_name TEXT NOT NULL,
                        recipient_uuid TEXT NOT NULL,
                        recipient_name TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        content TEXT,
                        created_at INTEGER NOT NULL,
                        expiration_days INTEGER DEFAULT 30,
                        is_read INTEGER DEFAULT 0,
                        is_claimed INTEGER DEFAULT 0
                    )
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_recipient ON starcore_mails(recipient_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sender ON starcore_mails(sender_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_created ON starcore_mails(created_at)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_read ON starcore_mails(is_read)");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS starcore_mail_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        mail_id TEXT NOT NULL,
                        item_data TEXT NOT NULL,
                        slot_index INTEGER DEFAULT 0,
                        FOREIGN KEY (mail_id) REFERENCES starcore_mails(id) ON DELETE CASCADE
                    )
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_mail ON starcore_mail_attachments(mail_id)");
            } else {
                // MySQL 版本
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS starcore_mails (
                        id VARCHAR(36) PRIMARY KEY,
                        sender_uuid VARCHAR(36) NOT NULL,
                        sender_name VARCHAR(64) NOT NULL,
                        recipient_uuid VARCHAR(36) NOT NULL,
                        recipient_name VARCHAR(64) NOT NULL,
                        subject VARCHAR(255) NOT NULL,
                        content TEXT,
                        created_at BIGINT NOT NULL,
                        expiration_days INT DEFAULT 30,
                        is_read BOOLEAN DEFAULT FALSE,
                        is_claimed BOOLEAN DEFAULT FALSE,
                        INDEX idx_recipient (recipient_uuid),
                        INDEX idx_sender (sender_uuid),
                        INDEX idx_created (created_at),
                        INDEX idx_read (is_read)
                    )
                """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS starcore_mail_attachments (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        mail_id VARCHAR(36) NOT NULL,
                        item_data TEXT NOT NULL,
                        slot_index INT DEFAULT 0,
                        FOREIGN KEY (mail_id) REFERENCES starcore_mails(id) ON DELETE CASCADE,
                        INDEX idx_mail (mail_id)
                    )
                """);
            }

            logger.info("邮件系统数据库表已初始化");
        } catch (Exception e) {
            logger.warning("初始化邮件数据库表失败: " + e.getMessage());
        }
    }

    /**
     * 保存邮件
     */
    public void saveMail(Mail mail) {
        try (Connection conn = getConnection()) {
            DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
            boolean isSQLite = dbType == DatabaseUtils.DatabaseType.SQLITE;

            String mailSql;
            if (isSQLite) {
                mailSql = """
                    INSERT OR REPLACE INTO starcore_mails (id, sender_uuid, sender_name, recipient_uuid, recipient_name,
                        subject, content, created_at, expiration_days, is_read, is_claimed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            } else {
                mailSql = """
                    INSERT INTO starcore_mails (id, sender_uuid, sender_name, recipient_uuid, recipient_name,
                        subject, content, created_at, expiration_days, is_read, is_claimed)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE is_read = VALUES(is_read), is_claimed = VALUES(is_claimed)
                """;
            }

            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(mailSql)) {
                stmt.setString(1, mail.getId().toString());
                stmt.setString(2, mail.getSenderId().toString());
                stmt.setString(3, mail.getSenderName());
                stmt.setString(4, mail.getRecipientId().toString());
                stmt.setString(5, mail.getRecipientName());
                stmt.setString(6, mail.getSubject());
                stmt.setString(7, mail.getContent());
                stmt.setLong(8, mail.getCreatedAt().toEpochMilli());
                stmt.setInt(9, (int) mail.getExpirationDays());
                if (isSQLite) {
                    stmt.setInt(10, mail.isRead() ? 1 : 0);
                    stmt.setInt(11, mail.isClaimed() ? 1 : 0);
                } else {
                    stmt.setBoolean(10, mail.isRead());
                    stmt.setBoolean(11, mail.isClaimed());
                }
                stmt.executeUpdate();

                // 保存附件
                if (!mail.getAttachments().isEmpty()) {
                    // 先删除旧附件
                    try (PreparedStatement delStmt = conn.prepareStatement(
                            "DELETE FROM starcore_mail_attachments WHERE mail_id = ?")) {
                        delStmt.setString(1, mail.getId().toString());
                        delStmt.executeUpdate();
                    }

                    // 插入新附件
                    String attachSql = "INSERT INTO starcore_mail_attachments (mail_id, item_data, slot_index) VALUES (?, ?, ?)";
                    try (PreparedStatement attachStmt = conn.prepareStatement(attachSql)) {
                        int slot = 0;
                        for (MailAttachment attachment : mail.getAttachments()) {
                            attachStmt.setString(1, mail.getId().toString());
                            attachStmt.setString(2, attachment.getSerializedItem());
                            attachStmt.setInt(3, slot++);
                            attachStmt.addBatch();
                        }
                        attachStmt.executeBatch();
                    }
                }
            }
            conn.commit();
        } catch (Exception e) {
            logger.warning("保存邮件失败: " + e.getMessage());
        }
    }

    /**
     * 删除邮件
     */
    public void deleteMail(UUID mailId) {
        String sql = "DELETE FROM starcore_mails WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mailId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除邮件失败: " + e.getMessage());
        }
    }

    /**
     * 加载玩家的所有邮件
     */
    public List<Mail> loadMailsForPlayer(UUID playerId) {
        List<Mail> mails = new ArrayList<>();
        String mailSql = "SELECT * FROM starcore_mails WHERE recipient_uuid = ? ORDER BY created_at DESC";
        String attachSql = "SELECT * FROM starcore_mail_attachments WHERE mail_id = ? ORDER BY slot_index";

        try (Connection conn = getConnection()) {
            DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
            boolean isSQLite = dbType == DatabaseUtils.DatabaseType.SQLITE;

            try (PreparedStatement mailStmt = conn.prepareStatement(mailSql)) {
                mailStmt.setString(1, playerId.toString());

                try (ResultSet rs = mailStmt.executeQuery()) {
                    while (rs.next()) {
                        UUID mailId = UUID.fromString(rs.getString("id"));
                        UUID senderId = UUID.fromString(rs.getString("sender_uuid"));
                        String senderName = rs.getString("sender_name");
                        UUID recipientId = UUID.fromString(rs.getString("recipient_uuid"));
                        String recipientName = rs.getString("recipient_name");
                        String subject = rs.getString("subject");
                        String content = rs.getString("content");
                        Instant createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
                        int expirationDays = rs.getInt("expiration_days");
                        boolean isRead = isSQLite ? rs.getInt("is_read") == 1 : rs.getBoolean("is_read");
                        boolean isClaimed = isSQLite ? rs.getInt("is_claimed") == 1 : rs.getBoolean("is_claimed");

                        // 加载附件
                        List<MailAttachment> attachments = new ArrayList<>();
                        try (PreparedStatement attachStmt = conn.prepareStatement(attachSql)) {
                            attachStmt.setString(1, mailId.toString());
                            try (ResultSet attachRs = attachStmt.executeQuery()) {
                                while (attachRs.next()) {
                                    String itemData = attachRs.getString("item_data");
                                    attachments.add(new MailAttachment(null, 1, itemData));
                                }
                            }
                        }

                        Mail mail = new Mail(mailId, senderId, senderName, recipientId, recipientName,
                            subject, content, attachments, createdAt, expirationDays);
                        mail.setRead(isRead);
                        mail.setClaimed(isClaimed);
                        mails.add(mail);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("加载邮件失败: " + e.getMessage());
        }
        return mails;
    }

    /**
     * 获取玩家的未读邮件数量
     */
    public int getUnreadCount(UUID playerId) {
        try (Connection conn = getConnection()) {
            DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
            boolean isSQLite = dbType == DatabaseUtils.DatabaseType.SQLITE;

            String sql;
            if (isSQLite) {
                sql = "SELECT COUNT(*) FROM starcore_mails WHERE recipient_uuid = ? AND is_read = 0";
            } else {
                sql = "SELECT COUNT(*) FROM starcore_mails WHERE recipient_uuid = ? AND is_read = FALSE";
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("获取未读邮件数量失败: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 标记邮件为已读
     */
    public void markAsRead(UUID mailId) {
        try (Connection conn = getConnection()) {
            DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
            boolean isSQLite = dbType == DatabaseUtils.DatabaseType.SQLITE;

            String sql;
            if (isSQLite) {
                sql = "UPDATE starcore_mails SET is_read = 1 WHERE id = ?";
            } else {
                sql = "UPDATE starcore_mails SET is_read = TRUE WHERE id = ?";
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, mailId.toString());
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            logger.warning("标记邮件已读失败: " + e.getMessage());
        }
    }

    /**
     * 标记附件为已领取
     */
    public void markAsClaimed(UUID mailId) {
        String sql = "UPDATE starcore_mails SET is_claimed = TRUE WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mailId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("标记附件已领取失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期邮件
     */
    public int cleanupExpiredMails() {
        long expirationTime = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS).toEpochMilli();
        String sql = "DELETE FROM starcore_mails WHERE created_at < ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, expirationTime);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                logger.info("清理了 " + deleted + " 封过期邮件");
            }
            return deleted;
        } catch (Exception e) {
            logger.warning("清理过期邮件失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 检查数据库是否可用
     */
    public boolean isDatabaseAvailable() {
        return databaseService.isRunning();
    }
}
