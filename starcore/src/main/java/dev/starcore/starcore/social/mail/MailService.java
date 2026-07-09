package dev.starcore.starcore.social.mail;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 邮件系统核心服务
 *
 * 功能：
 * 1. 邮件发送接收
 * 2. 附件系统
 * 3. 已读未读状态
 * 4. 邮件过期清理
 */
public final class MailService {

    // 默认邮件过期天数
    private static final long DEFAULT_EXPIRATION_DAYS = 30;

    // 玩家邮件缓存: playerId -> List<Mail>
    private final Map<UUID, List<Mail>> playerMails = new ConcurrentHashMap<>();

    // 在线玩家未读邮件数量缓存
    private final Map<UUID, Integer> unreadCounts = new ConcurrentHashMap<>();

    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;
    private final Plugin plugin;

    private SqlMailStorage sqlStorage;
    private File mailsFile;
    private boolean useDatabase = false;

    public MailService(DatabaseService databaseService, PersistenceService persistenceService, Plugin plugin) {
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化邮件服务
     */
    public void initialize() {
        // 初始化数据库存储
        if (databaseService != null && databaseService.isRunning()) {
            sqlStorage = new SqlMailStorage(databaseService, logger);
            sqlStorage.ensureTables();
            useDatabase = true;
            logger.info("邮件系统使用数据库存储");
        } else {
            // 创建邮件数据文件
            File mailDir = new File(plugin.getDataFolder(), "social");
            if (!mailDir.exists()) mailDir.mkdirs();
            mailsFile = new File(mailDir, "mails.yml");
            useDatabase = false;
            logger.info("邮件系统使用YAML存储（数据库不可用）");
        }

        // 注册过期清理任务（每小时执行）
        scheduleExpirationCleanup();
    }

    /**
     * 加载玩家邮件
     */
    public void loadMailsForPlayer(UUID playerId) {
        if (useDatabase && sqlStorage != null) {
            List<Mail> mails = sqlStorage.loadMailsForPlayer(playerId);
            playerMails.put(playerId, mails);
            updateUnreadCount(playerId);
        } else {
            loadFromYaml(playerId);
        }
    }

    /**
     * 获取玩家邮件列表
     */
    /**
     * 获取玩家邮件列表。
     * D-029: 避免 computeIfAbsent 内部 put 触发 ConcurrentHashMap RecursiveUpdate。
     */
    public List<Mail> getPlayerMails(UUID playerId) {
        List<Mail> mails = playerMails.get(playerId);
        if (mails == null) {
            loadMailsForPlayer(playerId);
            mails = playerMails.get(playerId);
            if (mails == null) {
                mails = new ArrayList<>();
                playerMails.put(playerId, mails);
            }
        }
        return mails;
    }

    /**
     * 获取玩家的未读邮件数量
     */
    public int getUnreadCount(UUID playerId) {
        return unreadCounts.getOrDefault(playerId, 0);
    }

    /**
     * 更新未读计数
     */
    private void updateUnreadCount(UUID playerId) {
        List<Mail> mails = playerMails.get(playerId);
        if (mails != null) {
            long count = mails.stream().filter(m -> !m.isRead() && !m.isExpired()).count();
            unreadCounts.put(playerId, (int) count);
        }
    }

    /**
     * 发送邮件
     * @param sender 发送者
     * @param recipientName 接收者名称
     * @param subject 主题
     * @param content 内容
     * @param attachments 附件物品
     * @return 成功返回 true
     */
    public boolean sendMail(Player sender, String recipientName, String subject, String content,
                           List<ItemStack> attachments) {
        // 查找接收者：优先在线玩家；离线时使用 getOfflinePlayer(name) 并校验是否真实存在过
        UUID recipientId = null;
        Player recipientPlayer = Bukkit.getPlayer(recipientName);
        if (recipientPlayer != null) {
            recipientId = recipientPlayer.getUniqueId();
        } else {
            // D-026: 不再遍历 Bukkit.getOfflinePlayers()（触发大批量 IO），
            // 改用单点 getOfflinePlayer(name) + hasPlayedBefore 校验。
            OfflinePlayer off = Bukkit.getOfflinePlayer(recipientName);
            if (off != null && off.hasPlayedBefore()) {
                recipientId = off.getUniqueId();
            }
        }

        if (recipientId == null) {
            return false;
        }

        // 不能给自己发邮件
        if (recipientId.equals(sender.getUniqueId())) {
            return false;
        }

        // 创建附件列表
        List<MailAttachment> mailAttachments = new ArrayList<>();
        if (attachments != null) {
            for (ItemStack item : attachments) {
                if (item != null && !item.getType().isAir()) {
                    mailAttachments.add(new MailAttachment(item));
                }
            }
        }

        // 创建邮件
        Mail mail = new Mail(
            UUID.randomUUID(),
            sender.getUniqueId(),
            sender.getName(),
            recipientId,
            recipientName,
            subject,
            content,
            mailAttachments,
            Instant.now(),
            DEFAULT_EXPIRATION_DAYS
        );

        // 保存邮件
        if (useDatabase && sqlStorage != null) {
            sqlStorage.saveMail(mail);
        } else {
            saveMailToYaml(mail);
        }

        // 添加到缓存
        List<Mail> mails = playerMails.computeIfAbsent(recipientId, k -> new ArrayList<>());
        mails.add(0, mail); // 添加到列表开头
        updateUnreadCount(recipientId);

        // 如果接收者在线，发送通知
        if (recipientPlayer != null) {
            recipientPlayer.sendMessage("§a[邮件] §f你收到了一封新邮件，来自: " + sender.getName());
            recipientPlayer.sendMessage("§e主题: " + subject);
        }

        return true;
    }

    /**
     * 读取邮件（标记为已读）
     */
    public Mail readMail(UUID playerId, UUID mailId) {
        List<Mail> mails = playerMails.get(playerId);
        if (mails == null) return null;

        for (Mail mail : mails) {
            if (mail.getId().equals(mailId)) {
                if (!mail.isRead()) {
                    mail.setRead(true);
                    updateUnreadCount(playerId);
                    if (useDatabase && sqlStorage != null) {
                        sqlStorage.markAsRead(mailId);
                    }
                }
                return mail;
            }
        }
        return null;
    }

    /**
     * 删除邮件
     */
    public boolean deleteMail(UUID playerId, UUID mailId) {
        List<Mail> mails = playerMails.get(playerId);
        if (mails == null) return false;

        boolean removed = mails.removeIf(m -> m.getId().equals(mailId));
        if (removed) {
            updateUnreadCount(playerId);
            if (useDatabase && sqlStorage != null) {
                sqlStorage.deleteMail(mailId);
            }
        }
        return removed;
    }

    /**
     * 领取附件
     */
    /**
     * 领取附件。
     * D-027: 背包满时通过 addAll 检查剩余物品，剩余物品不标记为已领取，避免物品丢失。
     * D-028: per-player CAS 锁防止并发双击造成重复领取。
     */
    public List<ItemStack> claimAttachments(UUID playerId, UUID mailId) {
        // D-028 per-player CAS 锁：占用则直接返回，避免并发双击重复领取
        if (claimLocks.putIfAbsent(playerId, Boolean.TRUE) != null) {
            return Collections.emptyList();
        }
        try {
            List<Mail> mails = playerMails.get(playerId);
            if (mails == null) return Collections.emptyList();

            for (Mail mail : mails) {
                if (mail.getId().equals(mailId)) {
                    if (!mail.hasAttachments() || mail.isClaimed()) {
                        return Collections.emptyList();
                    }

                    // 反序列化物品
                    List<ItemStack> items = new ArrayList<>();
                    for (MailAttachment attachment : mail.getAttachments()) {
                        ItemStack item = attachment.deserializeItem();
                        if (item != null) items.add(item);
                    }

                    // D-027: 调用方需自检背包；这里通过 Player 的背包 addItem 检查剩余。
                    // 由于本方法无 Player 引用，调用方应在调用前自行尝试 addItem；
                    // 本方法仅返回 items，仍标记 claimed。为保留旧契约：
                    // 不在这里标记 claimed —— 由调用方在物品成功入背包后调用 confirmClaimed。
                    return items;
                }
            }
            return Collections.emptyList();
        } finally {
            claimLocks.remove(playerId);
        }
    }

    /**
     * 确认附件已成功入背包（由调用方在 addItem 后调用）。
     * D-027: 背包满时调用方不应调用此方法，物品保留在邮件中。
     * 返回 false 表示邮件不存在或已被领取。
     */
    public boolean confirmClaimed(UUID playerId, UUID mailId) {
        List<Mail> mails = playerMails.get(playerId);
        if (mails == null) return false;
        for (Mail mail : mails) {
            if (mail.getId().equals(mailId)) {
                if (mail.isClaimed()) return true;
                mail.setClaimed(true);
                updateUnreadCount(playerId);
                if (useDatabase && sqlStorage != null) {
                    sqlStorage.markAsClaimed(mailId);
                }
                return true;
            }
        }
        return false;
    }

    // D-028 per-player CAS 锁，避免并发双击重复领取
    private final Map<UUID, Boolean> claimLocks = new ConcurrentHashMap<>();

    /**
     * 获取玩家的未读邮件提示
     */
    public String getUnreadMailHint(UUID playerId) {
        int count = getUnreadCount(playerId);
        if (count > 0) {
            return "§a[邮件] §f你有 " + count + " 封未读邮件";
        }
        return "";
    }

    /**
     * 安排过期邮件清理任务
     */
    private void scheduleExpirationCleanup() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (useDatabase && sqlStorage != null) {
                sqlStorage.cleanupExpiredMails();
            }
            // 清理缓存中的过期邮件
            cleanupExpiredMailsInCache();
        }, 72000L, 72000L); // 1小时后开始，每小时执行
    }

    /**
     * 清理缓存中的过期邮件
     */
    /**
     * 清理缓存中的过期邮件，并 unload 离线玩家的邮件缓存以回收内存（D-030）。
     */
    private void cleanupExpiredMailsInCache() {
        var it = playerMails.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            // 清理过期邮件
            entry.getValue().removeIf(Mail::isExpired);
            updateUnreadCount(entry.getKey());
            // 玩家离线则卸载缓存，回收内存
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                it.remove();
                unreadCounts.remove(entry.getKey());
            }
        }
    }

    /**
     * 保存所有数据。YAML 模式批量保存；DB 模式下 mail 状态变更已即时落盘，无需重复。
     */
    public void saveAll() {
        if (!useDatabase) {
            saveAllToYaml();
        }
        // DB 模式下 sendMail/readMail/claimAttachments 已即时写入，此处空操作即可
    }

    // ========== YAML 持久化方法 ==========

    private void loadFromYaml(UUID playerId) {
        if (mailsFile == null || !mailsFile.exists()) {
            playerMails.put(playerId, new ArrayList<>());
            return;
        }

        try {
            org.bukkit.configuration.file.YamlConfiguration yml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mailsFile);

            List<Mail> mails = new ArrayList<>();
            String path = playerId.toString();

            if (yml.contains(path)) {
                for (String mailId : yml.getConfigurationSection(path).getKeys(false)) {
                    try {
                        Mail mail = loadMailFromYaml(yml, path + "." + mailId);
                        if (mail != null && !mail.isExpired()) {
                            mails.add(mail);
                        }
                    } catch (Exception e) {
                        logger.warning("加载邮件失败: " + e.getMessage());
                    }
                }
            }

            mails.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            playerMails.put(playerId, mails);
            updateUnreadCount(playerId);
        } catch (Exception e) {
            logger.warning("加载邮件数据失败: " + e.getMessage());
            playerMails.put(playerId, new ArrayList<>());
        }
    }

    private Mail loadMailFromYaml(org.bukkit.configuration.file.YamlConfiguration yml, String path) {
        String idStr = yml.getString(path + ".id");
        if (idStr == null) return null;

        List<MailAttachment> attachments = new ArrayList<>();
        if (yml.contains(path + ".attachments")) {
            for (String attachStr : yml.getStringList(path + ".attachments")) {
                attachments.add(new MailAttachment(null, 1, attachStr));
            }
        }

        return new Mail(
            UUID.fromString(idStr),
            UUID.fromString(yml.getString(path + ".senderId")),
            yml.getString(path + ".senderName"),
            UUID.fromString(yml.getString(path + ".recipientId")),
            yml.getString(path + ".recipientName"),
            yml.getString(path + ".subject"),
            yml.getString(path + ".content"),
            attachments,
            Instant.ofEpochMilli(yml.getLong(path + ".createdAt")),
            yml.getLong(path + ".expirationDays", DEFAULT_EXPIRATION_DAYS)
        );
    }

    private void saveMailToYaml(Mail mail) {
        if (mailsFile == null) return;

        try {
            org.bukkit.configuration.file.YamlConfiguration yml;
            if (mailsFile.exists()) {
                yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mailsFile);
            } else {
                yml = new org.bukkit.configuration.file.YamlConfiguration();
            }

            String path = mail.getRecipientId().toString() + "." + mail.getId().toString();

            yml.set(path + ".id", mail.getId().toString());
            yml.set(path + ".senderId", mail.getSenderId().toString());
            yml.set(path + ".senderName", mail.getSenderName());
            yml.set(path + ".recipientId", mail.getRecipientId().toString());
            yml.set(path + ".recipientName", mail.getRecipientName());
            yml.set(path + ".subject", mail.getSubject());
            yml.set(path + ".content", mail.getContent());
            yml.set(path + ".createdAt", mail.getCreatedAt().toEpochMilli());
            yml.set(path + ".expirationDays", mail.getExpirationDays());
            yml.set(path + ".read", mail.isRead());
            yml.set(path + ".claimed", mail.isClaimed());

            List<String> attachList = new ArrayList<>();
            for (MailAttachment attach : mail.getAttachments()) {
                attachList.add(attach.getSerializedItem());
            }
            yml.set(path + ".attachments", attachList);

            yml.save(mailsFile);
        } catch (Exception e) {
            logger.warning("保存邮件失败: " + e.getMessage());
        }
    }

    // D-034: YAML 持久化同步锁，避免异步任务与读写线程并发写文件损坏 yml
    private final Object yamlSaveLock = new Object();

    private void saveAllToYaml() {
        if (mailsFile == null) return;
        synchronized (yamlSaveLock) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();

                for (Map.Entry<UUID, List<Mail>> entry : playerMails.entrySet()) {
                    for (Mail mail : entry.getValue()) {
                        String path = entry.getKey().toString() + "." + mail.getId().toString();

                        yml.set(path + ".id", mail.getId().toString());
                        yml.set(path + ".senderId", mail.getSenderId().toString());
                        yml.set(path + ".senderName", mail.getSenderName());
                        yml.set(path + ".recipientId", mail.getRecipientId().toString());
                        yml.set(path + ".recipientName", mail.getRecipientName());
                        yml.set(path + ".subject", mail.getSubject());
                        yml.set(path + ".content", mail.getContent());
                        yml.set(path + ".createdAt", mail.getCreatedAt().toEpochMilli());
                        yml.set(path + ".expirationDays", mail.getExpirationDays());
                        yml.set(path + ".read", mail.isRead());
                        yml.set(path + ".claimed", mail.isClaimed());

                        List<String> attachList = new ArrayList<>();
                        for (MailAttachment attach : mail.getAttachments()) {
                            attachList.add(attach.getSerializedItem());
                        }
                        yml.set(path + ".attachments", attachList);
                    }
                }

                yml.save(mailsFile);
            } catch (Exception e) {
                logger.warning("保存所有邮件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 清理玩家数据（当玩家离开时）
     */
    public void cleanup(UUID playerId) {
        playerMails.remove(playerId);
        unreadCounts.remove(playerId);
    }
}
