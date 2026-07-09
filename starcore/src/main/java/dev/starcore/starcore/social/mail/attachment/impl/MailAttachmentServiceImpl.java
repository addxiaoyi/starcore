package dev.starcore.starcore.social.mail.attachment.impl;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.social.mail.Mail;
import dev.starcore.starcore.social.mail.MailService;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService;
import dev.starcore.starcore.social.mail.attachment.MailAttachmentService.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 邮件附件服务实现
 *
 * 功能：
 * 1. 附件序列化与反序列化
 * 2. 附件验证与限制检查
 * 3. 附件领取逻辑
 * 4. 附件价值估算
 * 5. 领取历史记录
 */
public final class MailAttachmentServiceImpl implements MailAttachmentService {

    // 附件相关配置常量
    private static final int MAX_ATTACHMENTS_PER_MAIL = 27;  // 最多27个物品
    private static final int MAX_STACK_SIZE = 64;
    private static final long ATTACHMENT_EXPIRATION_DAYS = 30;
    private static final long MAX_ATTACHMENT_VALUE = 1_000_000_000L;  // 10亿金币上限

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
    private final MailService mailService;
    private final Logger logger;

    // 领取记录缓存: playerId -> List<ClaimRecord>
    private final Map<UUID, List<AttachmentClaimRecord>> claimHistoryCache = new ConcurrentHashMap<>();

    // 物品价值估算表（常用物品）
    private static final Map<String, Long> ITEM_VALUE_TABLE = new ConcurrentHashMap<>();

    static {
        // 矿物类
        ITEM_VALUE_TABLE.put("DIAMOND", 1000L);
        ITEM_VALUE_TABLE.put("EMERALD", 500L);
        ITEM_VALUE_TABLE.put("GOLD_INGOT", 100L);
        ITEM_VALUE_TABLE.put("IRON_INGOT", 50L);
        ITEM_VALUE_TABLE.put("COPPER_INGOT", 20L);

        // 附魔类
        ITEM_VALUE_TABLE.put("ENCHANTED_GOLDEN_APPLE", 50000L);
        ITEM_VALUE_TABLE.put("GOLDEN_APPLE", 5000L);
        ITEM_VALUE_TABLE.put("ENCHANTED_BOOK", 2000L);

        // 稀有物品
        ITEM_VALUE_TABLE.put("NETHER_STAR", 10000L);
        ITEM_VALUE_TABLE.put("DRAGON_BREATH", 2000L);
        ITEM_VALUE_TABLE.put("SHULKER_BOX", 5000L);
        ITEM_VALUE_TABLE.put("ELYTRA", 10000L);
        ITEM_VALUE_TABLE.put("NETHERITE_INGOT", 50000L);
        ITEM_VALUE_TABLE.put("NETHERITE_SWORD", 100000L);
        ITEM_VALUE_TABLE.put("NETHERITE_HELMET", 80000L);
        ITEM_VALUE_TABLE.put("NETHERITE_CHESTPLATE", 100000L);
        ITEM_VALUE_TABLE.put("NETHERITE_LEGGINGS", 90000L);
        ITEM_VALUE_TABLE.put("NETHERITE_BOOTS", 80000L);

        // 常规物品默认值
        ITEM_VALUE_TABLE.put("DEFAULT", 10L);
    }

    public MailAttachmentServiceImpl(DatabaseService databaseService, StarCoreScheduler scheduler,
                                     MailService mailService, Logger logger) {
        this.databaseService = databaseService;
        this.scheduler = scheduler;
        this.mailService = mailService;
        this.logger = logger;

        // 初始化数据库表
        ensureTables();
    }

    /**
     * 创建数据库表
     */
    private void ensureTables() {
        if (!databaseService.isRunning()) {
            logger.info("邮件附件系统使用缓存模式（数据库不可用）");
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement()) {

                    // 附件领取记录表
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS starcore_mail_attachment_claims (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            record_id VARCHAR(36) NOT NULL UNIQUE,
                            player_id VARCHAR(36) NOT NULL,
                            mail_id VARCHAR(36) NOT NULL,
                            sender_id VARCHAR(36),
                            sender_name VARCHAR(64),
                            attachment_data TEXT,
                            claimed_at BIGINT NOT NULL,
                            total_value BIGINT DEFAULT 0
                        )
                    """);
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_attachment ON starcore_mail_attachment_claims(player_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_mail_attachment ON starcore_mail_attachment_claims(mail_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_claimed_at ON starcore_mail_attachment_claims(claimed_at)");

                    logger.info("邮件附件系统数据库表已初始化");
                } catch (SQLException e) {
                    logger.warning("初始化邮件附件数据库表失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("邮件附件系统数据库初始化失败: " + e.getMessage());
        }
    }

    @Override
    public AttachmentValidationResult validateAttachments(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return AttachmentValidationResult.success(0);
        }

        List<String> warnings = new ArrayList<>();
        int validCount = 0;
        int invalidCount = 0;

        // 检查数量限制
        if (items.size() > MAX_ATTACHMENTS_PER_MAIL) {
            return AttachmentValidationResult.failure(
                "附件数量超过限制（最多 " + MAX_ATTACHMENTS_PER_MAIL + " 个）"
            );
        }

        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item == null || item.getType().isAir()) {
                invalidCount++;
                continue;
            }

            // 检查禁止的物品类型
            if (FORBIDDEN_MATERIALS.contains(item.getType())) {
                invalidCount++;
                warnings.add("物品 [" + (i + 1) + "] " + item.getType().name() + " 禁止传输");
                continue;
            }

            // 检查数量合理性
            if (item.getAmount() <= 0 || item.getAmount() > MAX_STACK_SIZE * 64) {
                invalidCount++;
                warnings.add("物品 [" + (i + 1) + "] 数量异常");
                continue;
            }

            validCount++;
        }

        if (invalidCount > 0 && validCount == 0) {
            return AttachmentValidationResult.failure("所有附件均无效");
        }

        return AttachmentValidationResult.partialSuccess(validCount, invalidCount, warnings);
    }

    @Override
    public List<MailAttachment> serializeAttachments(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<MailAttachment> attachments = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && !FORBIDDEN_MATERIALS.contains(item.getType())) {
                MailAttachment attachment = MailAttachment.fromItemStack(item);
                if (attachment != null) {
                    attachments.add(attachment);
                }
            }
        }
        return attachments;
    }

    @Override
    public List<ItemStack> deserializeAttachments(List<MailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        List<ItemStack> items = new ArrayList<>();
        for (MailAttachment attachment : attachments) {
            if (attachment != null) {
                ItemStack item = attachment.toItemStack();
                if (item != null && !item.getType().isAir()) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    @Override
    public AttachmentClaimResult claimAttachments(Player player, UUID mailId) {
        if (player == null || mailId == null) {
            return AttachmentClaimResult.failure("参数错误");
        }

        // 获取邮件
        List<Mail> mails = mailService.getPlayerMails(player.getUniqueId());
        Mail targetMail = null;
        for (Mail mail : mails) {
            if (mail.getId().equals(mailId)) {
                targetMail = mail;
                break;
            }
        }

        if (targetMail == null) {
            return AttachmentClaimResult.failure("邮件不存在");
        }

        // 检查附件
        if (!targetMail.hasAttachments()) {
            return AttachmentClaimResult.noAttachments();
        }

        // 检查是否已领取
        if (targetMail.isClaimed()) {
            return AttachmentClaimResult.alreadyClaimed();
        }

        // 获取附件物品
        List<ItemStack> items = new ArrayList<>();
        for (var attach : targetMail.getAttachments()) {
            ItemStack item = attach.deserializeItem();
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }

        if (items.isEmpty()) {
            return AttachmentClaimResult.noAttachments();
        }

        // 检查背包空间
        if (!hasInventorySpace(player, items)) {
            // 尝试只放入能放入的物品
            List<ItemStack> toGive = new ArrayList<>();
            List<ItemStack> overflow = new ArrayList<>(items);

            for (ItemStack item : items) {
                Map<Integer, ItemStack> overflowResult = player.getInventory().addItem(item);
                if (overflowResult.isEmpty()) {
                    toGive.add(item);
                    overflow.remove(item);
                }
            }

            if (toGive.isEmpty()) {
                return AttachmentClaimResult.failure("背包空间不足，请清理背包后重试");
            }

            // 标记为已领取
            mailService.claimAttachments(player.getUniqueId(), mailId);

            // 记录日志
            logAttachmentClaim(player.getUniqueId(), mailId,
                serializeAttachments(toGive));

            return AttachmentClaimResult.successWithOverflow(toGive, toGive.size(), overflow);
        }

        // 放入物品到背包
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }

        // 标记为已领取
        mailService.claimAttachments(player.getUniqueId(), mailId);

        // 记录日志
        logAttachmentClaim(player.getUniqueId(), mailId,
            serializeAttachments(items));

        return AttachmentClaimResult.success(items, items.size());
    }

    @Override
    public CompletableFuture<AttachmentClaimResult> claimAttachmentsAsync(Player player, UUID mailId) {
        return scheduler.supplyAsync(() -> claimAttachments(player, mailId));
    }

    @Override
    public boolean hasInventorySpace(Player player, List<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) {
            return true;
        }

        // 计算需要的槽位
        int requiredSlots = calculateRequiredSlots(player, items);

        // 获取玩家背包剩余空间
        int emptySlots = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                emptySlots++;
            }
        }

        return emptySlots >= requiredSlots;
    }

    @Override
    public int calculateRequiredSlots(Player player, List<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) {
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
            // 每个槽位最多64个
            requiredSlots += Math.ceilDiv(totalAmount, MAX_STACK_SIZE);
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
                        materialCounts.put(mat, needed);
                    }
                }
            }
        }

        // 重新计算所需槽位
        int finalSlots = 0;
        for (Map.Entry<Material, Integer> entry : materialCounts.entrySet()) {
            int remaining = entry.getValue();
            if (remaining > 0) {
                Material mat = entry.getKey();
                int maxStack = Math.min(MAX_STACK_SIZE, mat.getMaxStackSize());
                finalSlots += Math.ceilDiv(remaining, maxStack);
            }
        }

        return Math.max(requiredSlots, finalSlots);
    }

    @Override
    public long estimateTotalValue(List<MailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return 0L;
        }

        long totalValue = 0L;
        for (MailAttachment attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String itemId = attachment.itemId();
            long itemValue = ITEM_VALUE_TABLE.getOrDefault(itemId, ITEM_VALUE_TABLE.getOrDefault("DEFAULT", 10L));
            totalValue += itemValue * attachment.amount();

            // 安全检查
            if (totalValue > MAX_ATTACHMENT_VALUE) {
                return MAX_ATTACHMENT_VALUE;
            }
        }

        return totalValue;
    }

    @Override
    public void logAttachmentClaim(UUID playerId, UUID mailId, List<MailAttachment> attachments) {
        if (playerId == null || mailId == null) {
            return;
        }

        // 获取邮件发送者信息
        String senderId = "";
        String senderName = "";
        List<Mail> mails = mailService.getPlayerMails(playerId);
        for (Mail mail : mails) {
            if (mail.getId().equals(mailId)) {
                senderId = mail.getSenderId().toString();
                senderName = mail.getSenderName();
                break;
            }
        }

        // 计算总价值
        long totalValue = estimateTotalValue(attachments);

        // 创建记录
        AttachmentClaimRecord record = new AttachmentClaimRecord(
            UUID.randomUUID(),
            playerId,
            mailId,
            senderId.isEmpty() ? null : UUID.fromString(senderId),
            senderName,
            attachments,
            System.currentTimeMillis(),
            totalValue
        );

        // 保存到数据库
        saveClaimRecord(record);

        // 更新缓存
        claimHistoryCache.computeIfAbsent(playerId, k -> new ArrayList<>()).add(0, record);
    }

    /**
     * 保存领取记录到数据库
     */
    private void saveClaimRecord(AttachmentClaimRecord record) {
        if (!databaseService.isRunning()) {
            return;
        }

        scheduler.runAsync(() -> {
            try {
                databaseService.dataSource().ifPresent(ds -> {
                    String sql = """
                        INSERT INTO starcore_mail_attachment_claims
                        (record_id, player_id, mail_id, sender_id, sender_name, attachment_data, claimed_at, total_value)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                    try (Connection conn = ds.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setString(1, record.recordId().toString());
                        stmt.setString(2, record.playerId().toString());
                        stmt.setString(3, record.mailId().toString());
                        stmt.setString(4, record.senderId() != null ? record.senderId().toString() : null);
                        stmt.setString(5, record.senderName());
                        stmt.setString(6, serializeAttachmentsToJson(record.attachments()));
                        stmt.setLong(7, record.claimedAt());
                        stmt.setLong(8, record.totalValue());

                        stmt.executeUpdate();
                    } catch (SQLException e) {
                        logger.warning("保存附件领取记录失败: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                logger.warning("保存附件领取记录失败: " + e.getMessage());
            }
        });
    }

    /**
     * 序列化附件为 JSON 字符串
     */
    private String serializeAttachmentsToJson(List<MailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attachments.size(); i++) {
            MailAttachment attach = attachments.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                .append("\"itemId\":\"").append(attach.itemId()).append("\",")
                .append("\"amount\":").append(attach.amount()).append(",")
                .append("\"serializedItem\":\"").append(escapeJson(attach.serializedItem())).append("\",")
                .append("\"attachedAt\":").append(attach.attachedAt())
                .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    @Override
    public List<AttachmentClaimRecord> getClaimHistory(UUID playerId, int limit) {
        if (playerId == null) {
            return List.of();
        }

        // 检查缓存
        List<AttachmentClaimRecord> cached = claimHistoryCache.get(playerId);
        if (cached != null && cached.size() >= limit) {
            return cached.subList(0, Math.min(limit, cached.size()));
        }

        // 从数据库加载
        if (databaseService.isRunning()) {
            try {
                return loadClaimHistoryFromDb(playerId, limit);
            } catch (Exception e) {
                logger.warning("加载领取历史失败: " + e.getMessage());
            }
        }

        return cached != null ? cached : List.of();
    }

    /**
     * 从数据库加载领取历史
     */
    private List<AttachmentClaimRecord> loadClaimHistoryFromDb(UUID playerId, int limit) {
        List<AttachmentClaimRecord> records = new ArrayList<>();

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = """
                    SELECT * FROM starcore_mail_attachment_claims
                    WHERE player_id = ?
                    ORDER BY claimed_at DESC
                    LIMIT ?
                """;

                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {

                    stmt.setString(1, playerId.toString());
                    stmt.setInt(2, limit);

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String recordIdStr = rs.getString("record_id");
                            String mailIdStr = rs.getString("mail_id");
                            String senderIdStr = rs.getString("sender_id");
                            String senderName = rs.getString("sender_name");
                            String attachmentData = rs.getString("attachment_data");
                            long claimedAt = rs.getLong("claimed_at");
                            long totalValue = rs.getLong("total_value");

                            List<MailAttachment> attachments = parseAttachmentsFromJson(attachmentData);

                            AttachmentClaimRecord record = new AttachmentClaimRecord(
                                UUID.fromString(recordIdStr),
                                playerId,
                                UUID.fromString(mailIdStr),
                                senderIdStr != null ? UUID.fromString(senderIdStr) : null,
                                senderName,
                                attachments,
                                claimedAt,
                                totalValue
                            );
                            records.add(record);
                        }
                    }
                } catch (SQLException e) {
                    logger.warning("查询领取历史失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("加载领取历史失败: " + e.getMessage());
        }

        return records;
    }

    /**
     * 从 JSON 解析附件列表
     */
    private List<MailAttachment> parseAttachmentsFromJson(String json) {
        List<MailAttachment> attachments = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return attachments;
        }

        try {
            // 简单的 JSON 解析（不依赖外部库）
            json = json.trim();
            if (!json.startsWith("[") || !json.endsWith("]")) {
                return attachments;
            }

            String content = json.substring(1, json.length() - 1);
            if (content.isEmpty()) {
                return attachments;
            }

            // 分割对象
            int depth = 0;
            int start = 0;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String obj = content.substring(start, i + 1);
                        MailAttachment attach = parseAttachmentObject(obj);
                        if (attach != null) {
                            attachments.add(attach);
                        }
                        start = i + 1;
                        // 跳过逗号
                        while (start < content.length() && (content.charAt(start) == ',' || content.charAt(start) == ' ')) {
                            start++;
                        }
                        i = start - 1;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("解析附件 JSON 失败: " + e.getMessage());
        }

        return attachments;
    }

    /**
     * 解析单个附件对象
     */
    private MailAttachment parseAttachmentObject(String obj) {
        try {
            String itemId = extractJsonString(obj, "itemId");
            int amount = (int) extractJsonNumber(obj, "amount");
            String serializedItem = extractJsonString(obj, "serializedItem");
            long attachedAt = (long) extractJsonNumber(obj, "attachedAt");

            if (itemId == null) {
                return null;
            }

            return new MailAttachment(itemId, amount, serializedItem, attachedAt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JSON 对象中提取字符串值
     */
    private String extractJsonString(String obj, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = obj.indexOf(pattern);
        if (keyIndex == -1) {
            // 尝试无引号版本
            pattern = key + ":";
            keyIndex = obj.indexOf(pattern);
            if (keyIndex == -1) return null;
            keyIndex += pattern.length();
        } else {
            keyIndex += pattern.length() + 1; // 跳过引号
        }

        // 跳过空白
        while (keyIndex < obj.length() && Character.isWhitespace(obj.charAt(keyIndex))) {
            keyIndex++;
        }

        if (keyIndex >= obj.length() || obj.charAt(keyIndex) != '"') {
            return null;
        }

        keyIndex++; // 跳过开始引号
        int endIndex = keyIndex;
        boolean escaped = false;
        while (endIndex < obj.length()) {
            char c = obj.charAt(endIndex);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            }
            endIndex++;
        }

        return obj.substring(keyIndex, endIndex);
    }

    /**
     * 从 JSON 对象中提取数字值
     */
    private Number extractJsonNumber(String obj, String key) {
        String pattern = "\"" + key + "\":";
        int keyIndex = obj.indexOf(pattern);
        if (keyIndex == -1) return 0;

        keyIndex += pattern.length();
        while (keyIndex < obj.length() && (Character.isWhitespace(obj.charAt(keyIndex)) || obj.charAt(keyIndex) == ',' || obj.charAt(keyIndex) == '}')) {
            keyIndex++;
        }

        if (keyIndex >= obj.length()) return 0;

        int endIndex = keyIndex;
        while (endIndex < obj.length() && (Character.isDigit(obj.charAt(endIndex)) || obj.charAt(endIndex) == '.' || obj.charAt(endIndex) == '-')) {
            endIndex++;
        }

        String numStr = obj.substring(keyIndex, endIndex);
        try {
            if (numStr.contains(".")) {
                return Double.parseDouble(numStr);
            } else {
                return Long.parseLong(numStr);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
