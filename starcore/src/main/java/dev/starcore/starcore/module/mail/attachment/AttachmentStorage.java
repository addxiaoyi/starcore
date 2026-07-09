package dev.starcore.starcore.module.mail.attachment;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.mail.attachment.MailAttachmentService.AttachmentItem;
import dev.starcore.starcore.module.mail.attachment.MailAttachmentService.MailAttachment;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * 邮件附件数据持久化
 *
 * 提供附件数据的数据库持久化功能
 */
public final class AttachmentStorage {

    private final DatabaseService databaseService;
    private final Plugin plugin;
    private final Logger logger;

    public AttachmentStorage(DatabaseService databaseService, Plugin plugin, Logger logger) {
        this.databaseService = databaseService;
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * 保存邮件
     */
    public void saveMail(MailAttachment mail) {
        if (mail == null || !databaseService.isRunning()) {
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String mailSql = """
                    INSERT INTO starcore_mail_v2
                    (id, sender_id, sender_name, recipient_id, recipient_name, subject, message, sent_at, read_status, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                    subject = VALUES(subject),
                    message = VALUES(message),
                    read_status = VALUES(read_status)
                """;

                String attachmentSql = """
                    INSERT INTO starcore_mail_attachments
                    (mail_id, item_id, amount, serialized_item, claimed, slot_index)
                    VALUES (?, ?, ?, ?, ?, ?)
                """;

                try (Connection conn = ds.getConnection()) {
                    conn.setAutoCommit(false);

                    try (PreparedStatement mailStmt = conn.prepareStatement(mailSql);
                         PreparedStatement attachStmt = conn.prepareStatement(attachmentSql)) {

                        // 保存邮件
                        mailStmt.setString(1, mail.id().toString());
                        mailStmt.setString(2, mail.senderId().toString());
                        mailStmt.setString(3, mail.senderName());
                        mailStmt.setString(4, mail.recipientId().toString());
                        mailStmt.setString(5, mail.recipientName());
                        mailStmt.setString(6, mail.subject());
                        mailStmt.setString(7, mail.message());
                        mailStmt.setLong(8, mail.sentAt());
                        mailStmt.setBoolean(9, mail.read());
                        mailStmt.setLong(10, mail.expiresAt());
                        mailStmt.executeUpdate();

                        // 保存附件
                        if (mail.hasAttachments()) {
                            int slotIndex = 0;
                            for (AttachmentItem item : mail.items()) {
                                attachStmt.setString(1, mail.id().toString());
                                attachStmt.setString(2, item.itemId());
                                attachStmt.setInt(3, item.amount());
                                attachStmt.setString(4, item.serializedItem());
                                attachStmt.setBoolean(5, item.claimed());
                                attachStmt.setInt(6, slotIndex++);
                                attachStmt.addBatch();
                            }
                            attachStmt.executeBatch();
                        }

                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        logger.warning("保存邮件失败: " + e.getMessage());
                    }
                } catch (SQLException e) {
                    logger.warning("保存邮件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("保存邮件失败: " + e.getMessage());
        }
    }

    /**
     * 加载玩家邮件
     */
    public List<MailAttachment> loadMailsForPlayer(UUID playerId) {
        List<MailAttachment> mails = new ArrayList<>();

        if (!databaseService.isRunning()) {
            return mails;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String mailSql = """
                    SELECT * FROM starcore_mail_v2
                    WHERE recipient_id = ?
                    ORDER BY sent_at DESC
                """;

                try (Connection conn = ds.getConnection();
                     PreparedStatement mailStmt = conn.prepareStatement(mailSql)) {

                    mailStmt.setString(1, playerId.toString());

                    try (ResultSet mailRs = mailStmt.executeQuery()) {
                        while (mailRs.next()) {
                            UUID mailId = UUID.fromString(mailRs.getString("id"));
                            List<AttachmentItem> attachments = loadAttachmentsForMail(conn, mailId);

                            MailAttachment mail = new MailAttachment(
                                mailId,
                                UUID.fromString(mailRs.getString("sender_id")),
                                mailRs.getString("sender_name"),
                                UUID.fromString(mailRs.getString("recipient_id")),
                                mailRs.getString("recipient_name"),
                                mailRs.getString("subject"),
                                mailRs.getString("message"),
                                attachments,
                                mailRs.getLong("sent_at"),
                                mailRs.getBoolean("read_status"),
                                mailRs.getLong("expires_at")
                            );
                            mails.add(mail);
                        }
                    }
                } catch (SQLException e) {
                    logger.warning("加载邮件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("加载邮件失败: " + e.getMessage());
        }

        return mails;
    }

    /**
     * 加载邮件的附件
     */
    private List<AttachmentItem> loadAttachmentsForMail(Connection conn, UUID mailId) {
        List<AttachmentItem> attachments = new ArrayList<>();

        String sql = """
            SELECT * FROM starcore_mail_attachments
            WHERE mail_id = ?
            ORDER BY slot_index ASC
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mailId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AttachmentItem item = new AttachmentItem(
                        rs.getString("item_id"),
                        rs.getInt("amount"),
                        rs.getString("serialized_item"),
                        rs.getBoolean("claimed")
                    );
                    attachments.add(item);
                }
            }
        } catch (SQLException e) {
            logger.warning("加载附件失败: " + e.getMessage());
        }

        return attachments;
    }

    /**
     * 标记邮件为已读
     */
    public void markAsRead(UUID mailId) {
        if (!databaseService.isRunning()) {
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "UPDATE starcore_mail_v2 SET read_status = TRUE WHERE id = ?";

                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, mailId.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logger.warning("标记邮件已读失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("标记邮件已读失败: " + e.getMessage());
        }
    }

    /**
     * 标记附件为已领取
     */
    public void markAsClaimed(UUID mailId) {
        if (!databaseService.isRunning()) {
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "UPDATE starcore_mail_attachments SET claimed = TRUE WHERE mail_id = ?";

                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, mailId.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    logger.warning("标记附件已领取失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("标记附件已领取失败: " + e.getMessage());
        }
    }

    /**
     * 删除邮件
     */
    public void deleteMail(UUID mailId) {
        if (!databaseService.isRunning()) {
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection()) {
                    conn.setAutoCommit(false);

                    try (PreparedStatement attachStmt = conn.prepareStatement(
                            "DELETE FROM starcore_mail_attachments WHERE mail_id = ?");
                         PreparedStatement mailStmt = conn.prepareStatement(
                            "DELETE FROM starcore_mail_v2 WHERE id = ?")) {

                        attachStmt.setString(1, mailId.toString());
                        attachStmt.executeUpdate();

                        mailStmt.setString(1, mailId.toString());
                        mailStmt.executeUpdate();

                        conn.commit();
                    } catch (SQLException e) {
                        conn.rollback();
                        logger.warning("删除邮件失败: " + e.getMessage());
                    }
                } catch (SQLException e) {
                    logger.warning("删除邮件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("删除邮件失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期邮件
     */
    public void cleanupExpired() {
        if (!databaseService.isRunning()) {
            return;
        }

        long now = System.currentTimeMillis();

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "DELETE FROM starcore_mail_v2 WHERE expires_at < ?";

                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setLong(1, now);
                    int deleted = stmt.executeUpdate();

                    if (deleted > 0) {
                        logger.info("清理了 " + deleted + " 封过期邮件");
                    }
                } catch (SQLException e) {
                    logger.warning("清理过期邮件失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("清理过期邮件失败: " + e.getMessage());
        }
    }

    /**
     * 保存所有缓存数据
     */
    public void saveAll(Map<UUID, List<MailAttachment>> playerMails) {
        if (!databaseService.isRunning()) {
            return;
        }

        for (List<MailAttachment> mails : playerMails.values()) {
            for (MailAttachment mail : mails) {
                saveMail(mail);
            }
        }
    }
}
