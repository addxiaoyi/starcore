package dev.starcore.starcore.module.mail.attachment;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.mail.attachment.MailAttachmentService.AttachmentClaimResult;
import dev.starcore.starcore.module.mail.attachment.MailAttachmentService.AttachmentItem;
import dev.starcore.starcore.module.mail.attachment.MailAttachmentService.MailAttachment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 邮件附件服务实现
 *
 * 功能：
 * 1. 发送带附件的邮件
 * 2. 附件序列化与反序列化
 * 3. 附件领取逻辑
 * 4. 附件过期管理
 * 5. 未读邮件统计
 */
public final class MailAttachmentServiceImpl implements MailAttachmentService {

    // 附件相关配置常量
    private static final int MAX_ATTACHMENTS_PER_MAIL = 27;  // 最多27个物品
    private static final int MAX_STACK_SIZE = 64;
    private static final long DEFAULT_EXPIRATION_DAYS = 30;
    private static final long EXPIRATION_MS = DEFAULT_EXPIRATION_DAYS * 24 * 60 * 60 * 1000L;

    // 禁止传输的物品类型
    private static final Set<Material> FORBIDDEN_MATERIALS = EnumSet.of(
        Material.AIR,
        Material.WRITTEN_BOOK,
        Material.WRITABLE_BOOK,
        Material.FILLED_MAP,
        Material.MAP,
        Material.OMINOUS_BOTTLE,
        Material.AXOLOTL_BUCKET,
        Material.TADPOLE_BUCKET,
        Material.POWDER_SNOW_BUCKET,
        Material.LAVA_BUCKET,
        Material.WATER_BUCKET,
        Material.MILK_BUCKET
    );

    private final DatabaseService databaseService;
    private final StarCoreScheduler scheduler;
    private final Plugin plugin;
    private final Logger logger;

    // 玩家邮件缓存: playerId -> List<MailAttachment>
    private final Map<UUID, List<MailAttachment>> playerMails = new ConcurrentHashMap<>();

    // 在线玩家未读邮件数量缓存
    private final Map<UUID, Integer> unreadCounts = new ConcurrentHashMap<>();

    // 邮件存储接口
    private AttachmentStorage storage;

    // 是否使用数据库
    private boolean useDatabase = false;

    public MailAttachmentServiceImpl(DatabaseService databaseService, StarCoreScheduler scheduler,
                                     Plugin plugin, Logger logger) {
        this.databaseService = databaseService;
        this.scheduler = scheduler;
        this.plugin = plugin;
        this.logger = logger;

        // 初始化存储
        initializeStorage();

        // 初始化数据库表
        ensureTables();

        // 注册过期清理任务
        scheduleExpirationCleanup();
    }

    /**
     * 初始化存储
     */
    private void initializeStorage() {
        this.storage = new AttachmentStorage(databaseService, plugin, logger);
    }

    /**
     * 创建数据库表
     */
    private void ensureTables() {
        if (databaseService == null || !databaseService.isRunning()) {
            logger.info("邮件附件系统使用内存存储（数据库不可用）");
            useDatabase = false;
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement()) {

                    // 邮件表
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS starcore_mail_v2 (
                            id VARCHAR(36) PRIMARY KEY,
                            sender_id VARCHAR(36) NOT NULL,
                            sender_name VARCHAR(64) NOT NULL,
                            recipient_id VARCHAR(36) NOT NULL,
                            recipient_name VARCHAR(64) NOT NULL,
                            subject VARCHAR(255) NOT NULL,
                            message TEXT,
                            sent_at BIGINT NOT NULL,
                            read_status BOOLEAN DEFAULT FALSE,
                            expires_at BIGINT NOT NULL,
                            INDEX idx_recipient (recipient_id),
                            INDEX idx_sender (sender_id),
                            INDEX idx_expires (expires_at)
                        )
                    """);

                    // 附件表
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS starcore_mail_attachments (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            mail_id VARCHAR(36) NOT NULL,
                            item_id VARCHAR(64) NOT NULL,
                            amount INT NOT NULL DEFAULT 1,
                            serialized_item TEXT,
                            claimed BOOLEAN DEFAULT FALSE,
                            slot_index INT DEFAULT 0,
                            FOREIGN KEY (mail_id) REFERENCES starcore_mail_v2(id) ON DELETE CASCADE,
                            INDEX idx_mail_id (mail_id)
                        )
                    """);

                    useDatabase = true;
                    logger.info("邮件附件系统数据库表已初始化");
                } catch (SQLException e) {
                    logger.warning("初始化邮件数据库表失败: " + e.getMessage());
                    useDatabase = false;
                }
            });
        } catch (Exception e) {
            logger.warning("邮件附件系统数据库初始化失败: " + e.getMessage());
            useDatabase = false;
        }
    }

    /**
     * 安排过期清理任务
     */
    private void scheduleExpirationCleanup() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupExpiredMails();
        }, 72000L, 72000L); // 1小时后开始，每小时执行
    }

    /**
     * 清理过期邮件
     */
    private void cleanupExpiredMails() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, List<MailAttachment>> entry : playerMails.entrySet()) {
            entry.getValue().removeIf(mail -> mail.expiresAt() < now);
            updateUnreadCount(entry.getKey());
        }

        if (useDatabase && storage != null) {
            storage.cleanupExpired();
        }

        logger.info("过期邮件清理完成");
    }

    /**
     * 更新未读计数
     */
    private void updateUnreadCount(UUID playerId) {
        List<MailAttachment> mails = playerMails.get(playerId);
        if (mails != null) {
            long count = mails.stream()
                .filter(m -> !m.read() && !m.isExpired())
                .count();
            unreadCounts.put(playerId, (int) count);
        }
    }

    @Override
    public boolean sendMailWithAttachment(Player sender, String recipient, String subject, String message, List<ItemStack> items) {
        // 验证参数
        if (sender == null || recipient == null || recipient.isEmpty()) {
            return false;
        }

        // 不能给自己发邮件
        if (recipient.equalsIgnoreCase(sender.getName())) {
            return false;
        }

        // 查找接收者
        UUID recipientId = null;
        String recipientName = null;
        Player recipientPlayer = Bukkit.getPlayer(recipient);
        if (recipientPlayer != null) {
            recipientId = recipientPlayer.getUniqueId();
            recipientName = recipientPlayer.getName();
        }

        // 如果玩家不在线，尝试从离线玩家查找
        if (recipientId == null) {
            for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                if (offline.getName() != null && offline.getName().equalsIgnoreCase(recipient)) {
                    recipientId = offline.getUniqueId();
                    recipientName = offline.getName();
                    break;
                }
            }
        }

        if (recipientId == null) {
            return false;
        }

        // 验证并序列化附件
        List<AttachmentItem> attachmentItems = new ArrayList<>();
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir() && !FORBIDDEN_MATERIALS.contains(item.getType())) {
                    if (attachmentItems.size() < MAX_ATTACHMENTS_PER_MAIL) {
                        AttachmentItem attach = AttachmentItem.fromItemStack(item);
                        if (attach != null) {
                            attachmentItems.add(attach);
                        }
                    }
                }
            }
        }

        // 创建邮件
        UUID mailId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        MailAttachment mail = new MailAttachment(
            mailId,
            sender.getUniqueId(),
            sender.getName(),
            recipientId,
            recipientName,
            subject != null ? subject : "无主题",
            message != null ? message : "",
            attachmentItems,
            now,
            false,
            now + EXPIRATION_MS
        );

        // 保存邮件
        if (useDatabase) {
            storage.saveMail(mail);
        }

        // 添加到缓存
        List<MailAttachment> mails = playerMails.computeIfAbsent(recipientId, k -> new ArrayList<>());
        mails.add(0, mail);
        updateUnreadCount(recipientId);

        // 如果接收者在线，发送通知
        if (recipientPlayer != null) {
            recipientPlayer.sendMessage(Component.text("[邮件] ").color(NamedTextColor.GREEN)
                .append(Component.text("你收到了一封新邮件，来自: " + sender.getName(), NamedTextColor.WHITE)));
            recipientPlayer.sendMessage(Component.text("主题: " + subject, NamedTextColor.YELLOW));
        }

        return true;
    }

    @Override
    public CompletableFuture<Boolean> sendMailWithAttachmentAsync(Player sender, String recipient, String subject, String message, List<ItemStack> items) {
        return scheduler.supplyAsync(() -> sendMailWithAttachment(sender, recipient, subject, message, items));
    }

    @Override
    public List<ItemStack> getMailAttachments(UUID mailId) {
        List<MailAttachment> mails = getAllMails();
        for (MailAttachment mail : mails) {
            if (mail.id().equals(mailId)) {
                List<ItemStack> items = new ArrayList<>();
                for (AttachmentItem item : mail.items()) {
                    ItemStack stack = item.toItemStack();
                    if (stack != null) {
                        items.add(stack);
                    }
                }
                return items;
            }
        }
        return List.of();
    }

    @Override
    public AttachmentClaimResult claimAttachment(Player player, UUID mailId) {
        if (player == null || mailId == null) {
            return AttachmentClaimResult.failure(mailId, "参数错误");
        }

        UUID playerId = player.getUniqueId();
        List<MailAttachment> mails = playerMails.get(playerId);
        if (mails == null) {
            return AttachmentClaimResult.failure(mailId, "没有邮件");
        }

        MailAttachment targetMail = null;
        int mailIndex = -1;
        for (int i = 0; i < mails.size(); i++) {
            if (mails.get(i).id().equals(mailId)) {
                targetMail = mails.get(i);
                mailIndex = i;
                break;
            }
        }

        if (targetMail == null) {
            return AttachmentClaimResult.failure(mailId, "邮件不存在");
        }

        if (targetMail.isExpired()) {
            return AttachmentClaimResult.failure(mailId, "邮件已过期");
        }

        // 检查附件
        if (!targetMail.hasAttachments()) {
            return AttachmentClaimResult.noAttachments(mailId);
        }

        // 检查是否已领取
        if (targetMail.isClaimed()) {
            return AttachmentClaimResult.alreadyClaimed(mailId);
        }

        // 获取附件物品
        List<ItemStack> items = new ArrayList<>();
        for (AttachmentItem item : targetMail.items()) {
            if (!item.claimed()) {
                ItemStack stack = item.toItemStack();
                if (stack != null) {
                    items.add(stack);
                }
            }
        }

        if (items.isEmpty()) {
            return AttachmentClaimResult.noAttachments(mailId);
        }

        // 检查背包空间
        int requiredSlots = calculateRequiredSlots(player, items);
        int emptySlots = countEmptySlots(player);

        if (emptySlots < requiredSlots) {
            // 尝试部分领取
            List<ItemStack> toGive = new ArrayList<>();
            List<ItemStack> overflow = new ArrayList<>(items);

            for (ItemStack item : items) {
                Map<Integer, ItemStack> result = player.getInventory().addItem(item);
                if (result.isEmpty()) {
                    toGive.add(item);
                    overflow.remove(item);
                }
            }

            if (toGive.isEmpty()) {
                return AttachmentClaimResult.noSpace(mailId);
            }

            // 更新邮件状态
            updateMailAsClaimed(playerId, mailId);

            return AttachmentClaimResult.successWithOverflow(mailId, toGive, toGive.size(), overflow);
        }

        // 放入物品到背包
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }

        // 更新邮件状态
        updateMailAsClaimed(playerId, mailId);

        return AttachmentClaimResult.success(mailId, items, items.size());
    }

    @Override
    public CompletableFuture<AttachmentClaimResult> claimAttachmentAsync(Player player, UUID mailId) {
        return scheduler.supplyAsync(() -> claimAttachment(player, mailId));
    }

    @Override
    public List<AttachmentClaimResult> claimAllAttachments(Player player) {
        if (player == null) {
            return List.of();
        }

        UUID playerId = player.getUniqueId();
        List<MailAttachment> mails = playerMails.get(playerId);
        if (mails == null) {
            return List.of();
        }

        List<AttachmentClaimResult> results = new ArrayList<>();

        for (MailAttachment mail : mails) {
            if (!mail.isExpired() && mail.hasAttachments() && !mail.isClaimed()) {
                AttachmentClaimResult result = claimAttachment(player, mail.id());
                if (result.success() || result.message().contains("已被领取")) {
                    results.add(result);
                }
            }
        }

        return results;
    }

    @Override
    public boolean deleteMail(Player player, UUID mailId) {
        if (player == null || mailId == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        List<MailAttachment> mails = playerMails.get(playerId);
        if (mails == null) {
            return false;
        }

        boolean removed = mails.removeIf(m -> m.id().equals(mailId));
        if (removed) {
            updateUnreadCount(playerId);
            if (useDatabase) {
                storage.deleteMail(mailId);
            }
        }
        return removed;
    }

    @Override
    public int getUnreadCount(UUID playerId) {
        return unreadCounts.getOrDefault(playerId, 0);
    }

    @Override
    public List<MailAttachment> getPlayerMails(UUID playerId) {
        return playerMails.computeIfAbsent(playerId, k -> {
            loadMailsForPlayer(k);
            return new ArrayList<>();
        });
    }

    @Override
    public int getUnclaimedAttachmentCount(UUID playerId) {
        List<MailAttachment> mails = getPlayerMails(playerId);
        return (int) mails.stream()
            .filter(m -> !m.isExpired() && m.hasAttachments() && !m.isClaimed())
            .count();
    }

    /**
     * 获取所有玩家的所有邮件
     */
    private List<MailAttachment> getAllMails() {
        List<MailAttachment> all = new ArrayList<>();
        for (List<MailAttachment> mails : playerMails.values()) {
            all.addAll(mails);
        }
        return all;
    }

    /**
     * 加载玩家邮件
     */
    private void loadMailsForPlayer(UUID playerId) {
        if (useDatabase) {
            List<MailAttachment> mails = storage.loadMailsForPlayer(playerId);
            playerMails.put(playerId, mails);
            updateUnreadCount(playerId);
        }
    }

    /**
     * 更新邮件为已领取
     */
    private void updateMailAsClaimed(UUID playerId, UUID mailId) {
        List<MailAttachment> mails = playerMails.get(playerId);
        if (mails != null) {
            for (int i = 0; i < mails.size(); i++) {
                if (mails.get(i).id().equals(mailId)) {
                    MailAttachment oldMail = mails.get(i);
                    // 创建新的已领取状态的邮件
                    List<AttachmentItem> newItems = oldMail.items().stream()
                        .map(item -> new AttachmentItem(item.itemId(), item.amount(), item.serializedItem(), true))
                        .toList();
                    MailAttachment newMail = new MailAttachment(
                        oldMail.id(),
                        oldMail.senderId(),
                        oldMail.senderName(),
                        oldMail.recipientId(),
                        oldMail.recipientName(),
                        oldMail.subject(),
                        oldMail.message(),
                        newItems,
                        oldMail.sentAt(),
                        oldMail.read(),
                        oldMail.expiresAt()
                    );
                    mails.set(i, newMail);
                    break;
                }
            }
        }

        if (useDatabase) {
            storage.markAsClaimed(mailId);
        }

        updateUnreadCount(playerId);
    }

    /**
     * 计算所需背包槽位
     */
    private int calculateRequiredSlots(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int requiredSlots = 0;
        Map<Material, Integer> materialCounts = new HashMap<>();

        // 合并相同物品
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Material mat = item.getType();
            materialCounts.merge(mat, item.getAmount(), Integer::sum);
        }

        // 计算需要的槽位
        for (Map.Entry<Material, Integer> entry : materialCounts.entrySet()) {
            int totalAmount = entry.getValue();
            int maxStack = Math.min(MAX_STACK_SIZE, entry.getKey().getMaxStackSize());
            requiredSlots += Math.ceilDiv(totalAmount, maxStack);
        }

        // 考虑背包中已有的物品堆叠
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                Material mat = item.getType();
                int currentAmount = item.getAmount();
                int maxStack = Math.min(MAX_STACK_SIZE, mat.getMaxStackSize());

                if (materialCounts.containsKey(mat)) {
                    int needed = materialCounts.get(mat);
                    int canAdd = maxStack - currentAmount;
                    if (canAdd > 0) {
                        needed -= Math.min(needed, canAdd);
                        materialCounts.put(mat, Math.max(0, needed));
                    } else {
                        materialCounts.put(mat, 0);
                    }
                }
            }
        }

        // 重新计算所需槽位
        for (int remaining : materialCounts.values()) {
            if (remaining > 0) {
                requiredSlots++;
            }
        }

        return requiredSlots;
    }

    /**
     * 计算空槽位数量
     */
    private int countEmptySlots(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 标记邮件为已读
     */
    public void markAsRead(UUID playerId, UUID mailId) {
        List<MailAttachment> mails = playerMails.get(playerId);
        if (mails != null) {
            for (int i = 0; i < mails.size(); i++) {
                if (mails.get(i).id().equals(mailId)) {
                    MailAttachment oldMail = mails.get(i);
                    MailAttachment newMail = new MailAttachment(
                        oldMail.id(),
                        oldMail.senderId(),
                        oldMail.senderName(),
                        oldMail.recipientId(),
                        oldMail.recipientName(),
                        oldMail.subject(),
                        oldMail.message(),
                        oldMail.items(),
                        oldMail.sentAt(),
                        true,  // 标记为已读
                        oldMail.expiresAt()
                    );
                    mails.set(i, newMail);
                    break;
                }
            }
        }

        updateUnreadCount(playerId);
        if (useDatabase) {
            storage.markAsRead(mailId);
        }
    }

    /**
     * 加载玩家邮件（外部调用）
     */
    public void loadMails(UUID playerId) {
        loadMailsForPlayer(playerId);
    }

    /**
     * 保存所有数据
     */
    public void saveAll() {
        if (useDatabase && storage != null) {
            storage.saveAll(playerMails);
        }
    }
}
