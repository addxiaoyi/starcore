package dev.starcore.starcore.social.mail;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 邮件数据模型
 */
public final class Mail {
    private final UUID id;
    private final UUID senderId;
    private final String senderName;
    private final UUID recipientId;
    private final String recipientName;
    private final String subject;
    private final String content;
    private final List<MailAttachment> attachments;
    private final Instant createdAt;
    private final long expirationDays;
    private boolean read;
    private boolean claimed;

    public Mail(UUID id, UUID senderId, String senderName, UUID recipientId, String recipientName,
                String subject, String content, List<MailAttachment> attachments,
                Instant createdAt, long expirationDays) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.subject = subject;
        this.content = content;
        this.attachments = new ArrayList<>(attachments);
        this.createdAt = createdAt;
        this.expirationDays = expirationDays;
        this.read = false;
        this.claimed = attachments.isEmpty();
    }

    public UUID getId() { return id; }
    public UUID getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public UUID getRecipientId() { return recipientId; }
    public String getRecipientName() { return recipientName; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public List<MailAttachment> getAttachments() { return new ArrayList<>(attachments); }
    public Instant getCreatedAt() { return createdAt; }
    public long getExpirationDays() { return expirationDays; }
    public boolean isRead() { return read; }
    public boolean isClaimed() { return claimed; }
    public boolean hasAttachments() { return !attachments.isEmpty(); }

    public void setRead(boolean read) { this.read = read; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }

    /**
     * 检查邮件是否已过期
     */
    public boolean isExpired() {
        Instant expiration = createdAt.plus(expirationDays, ChronoUnit.DAYS);
        return Instant.now().isAfter(expiration);
    }

    /**
     * 获取剩余过期天数
     */
    public long getRemainingDays() {
        Instant expiration = createdAt.plus(expirationDays, ChronoUnit.DAYS);
        long days = ChronoUnit.DAYS.between(Instant.now(), expiration);
        return Math.max(0, days);
    }

    /**
     * 获取格式化的时间字符串
     */
    public String getFormattedTime() {
        long days = ChronoUnit.DAYS.between(createdAt, Instant.now());
        if (days == 0) {
            return "今天";
        } else if (days == 1) {
            return "昨天";
        } else if (days < 7) {
            return days + "天前";
        } else {
            long weeks = days / 7;
            return weeks + "周前";
        }
    }

    /**
     * 转换为邮件预览物品
     */
    public ItemStack toPreviewItem() {
        Material material = isRead()
            ? (hasAttachments() && !isClaimed() ? Material.GOLD_INGOT : Material.PAPER)
            : (hasAttachments() && !isClaimed() ? Material.NETHER_STAR : Material.BOOK);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // 显示名称
        NamedTextColor titleColor = isRead() ? NamedTextColor.GRAY : NamedTextColor.WHITE;
        meta.displayName(Component.text()
            .append(Component.text("[")
                .color(NamedTextColor.DARK_GRAY))
            .append(Component.text(isRead() ? "已读" : "未读")
                .color(isRead() ? NamedTextColor.GRAY : NamedTextColor.RED))
            .append(Component.text("] ")
                .color(NamedTextColor.DARK_GRAY))
            .append(Component.text(subject, titleColor))
            .build());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("发件人: " + senderName, NamedTextColor.GRAY));
        lore.add(Component.text("时间: " + getFormattedTime(), NamedTextColor.GRAY));
        lore.add(Component.text("剩余: " + getRemainingDays() + " 天后过期", NamedTextColor.DARK_GRAY));

        if (hasAttachments()) {
            lore.add(Component.text(""));
            if (isClaimed()) {
                lore.add(Component.text("[附件] 已领取", NamedTextColor.GREEN));
            } else {
                lore.add(Component.text("[附件] " + attachments.size() + " 个物品 - 点击领取", NamedTextColor.GOLD));
            }
        }

        lore.add(Component.text(""));
        lore.add(Component.text("点击查看详情", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
