package dev.starcore.starcore.notification;

import net.kyori.adventure.text.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 通知模型
 * 表示一个完整的通知对象
 */
public class Notification {
    private final String id;
    private final NotificationType type;
    private final NotificationPriority priority;
    private final String title;
    private final String message;
    private final Component titleComponent;
    private final Component messageComponent;
    private final UUID senderId;
    private final UUID receiverId;
    private final UUID relatedNationId;
    private final Instant createdAt;
    private final Instant expireAt;
    private final boolean read;
    private final NotificationChannel primaryChannel;
    private final Object metadata;

    private Notification(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.type = builder.type;
        this.priority = builder.priority;
        this.title = builder.title;
        this.message = builder.message;
        this.titleComponent = builder.titleComponent;
        this.messageComponent = builder.messageComponent;
        this.senderId = builder.senderId;
        this.receiverId = builder.receiverId;
        this.relatedNationId = builder.relatedNationId;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expireAt = builder.expireAt;
        this.read = false;
        this.primaryChannel = builder.primaryChannel != null ? builder.primaryChannel : NotificationChannel.CHAT;
        this.metadata = builder.metadata;
    }

    // Getters
    public String getId() { return id; }
    public NotificationType getType() { return type; }
    public NotificationPriority getPriority() { return priority; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Component getTitleComponent() { return titleComponent; }
    public Component getMessageComponent() { return messageComponent; }
    public UUID getSenderId() { return senderId; }
    public UUID getReceiverId() { return receiverId; }
    public UUID getRelatedNationId() { return relatedNationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpireAt() { return expireAt; }
    public boolean isRead() { return read; }
    public NotificationChannel getPrimaryChannel() { return primaryChannel; }
    public Object getMetadata() { return metadata; }

    /**
     * 检查通知是否已过期
     */
    public boolean isExpired() {
        return expireAt != null && Instant.now().isAfter(expireAt);
    }

    /**
     * 检查是否紧急通知
     */
    public boolean isUrgent() {
        return priority.getLevel() >= NotificationPriority.HIGH.getLevel();
    }

    /**
     * 检查是否是战争相关通知
     */
    public boolean isWarRelated() {
        return type.getCategory() == NotificationType.Category.MILITARY
            || type == NotificationType.DIPLOMACY_WAR_DECLARE
            || type == NotificationType.DIPLOMACY_WAR_END;
    }

    /**
     * 创建通知构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 快速创建简单通知
     */
    public static Notification of(NotificationType type, UUID receiverId, String title, String message) {
        return builder()
            .type(type)
            .receiverId(receiverId)
            .title(title)
            .message(message)
            .build();
    }

    /**
     * 快速创建紧急通知
     */
    public static Notification urgent(NotificationType type, UUID receiverId, String title, String message) {
        return builder()
            .type(type)
            .priority(NotificationPriority.HIGH)
            .receiverId(receiverId)
            .title(title)
            .message(message)
            .primaryChannel(NotificationChannel.TITLE)
            .build();
    }

    public static class Builder {
        private String id;
        private NotificationType type;
        private NotificationPriority priority = NotificationPriority.NORMAL;
        private String title;
        private String message;
        private Component titleComponent;
        private Component messageComponent;
        private UUID senderId;
        private UUID receiverId;
        private UUID relatedNationId;
        private Instant createdAt;
        private Instant expireAt;
        private NotificationChannel primaryChannel;
        private Object metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder type(NotificationType type) { this.type = type; return this; }
        public Builder priority(NotificationPriority priority) { this.priority = priority; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder titleComponent(Component titleComponent) { this.titleComponent = titleComponent; return this; }
        public Builder messageComponent(Component messageComponent) { this.messageComponent = messageComponent; return this; }
        public Builder senderId(UUID senderId) { this.senderId = senderId; return this; }
        public Builder receiverId(UUID receiverId) { this.receiverId = receiverId; return this; }
        public Builder relatedNationId(UUID relatedNationId) { this.relatedNationId = relatedNationId; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder expireAt(Instant expireAt) { this.expireAt = expireAt; return this; }
        public Builder primaryChannel(NotificationChannel channel) { this.primaryChannel = channel; return this; }
        public Builder metadata(Object metadata) { this.metadata = metadata; return this; }

        public Notification build() {
            if (type == null) throw new IllegalArgumentException("NotificationType is required");
            if (receiverId == null) throw new IllegalArgumentException("ReceiverId is required");
            return new Notification(this);
        }
    }

    @Override
    public String toString() {
        return "Notification{" +
            "id='" + id + '\'' +
            ", type=" + type +
            ", priority=" + priority +
            ", title='" + title + '\'' +
            ", receiverId=" + receiverId +
            ", read=" + read +
            '}';
    }
}