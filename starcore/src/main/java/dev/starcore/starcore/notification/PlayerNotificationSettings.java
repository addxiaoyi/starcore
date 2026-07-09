package dev.starcore.starcore.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * 玩家通知设置
 * 每个玩家可以独立配置通知偏好
 */
public class PlayerNotificationSettings {
    private final UUID playerId;
    private boolean enabled;
    private boolean soundEnabled;
    private boolean chatEnabled;
    private boolean actionBarEnabled;
    private boolean titleEnabled;
    private boolean notificationCenterEnabled;

    // 各类通知开关
    private boolean nationNotifications = true;
    private boolean diplomacyNotifications = true;
    private boolean militaryNotifications = true;
    private boolean economyNotifications = true;
    private boolean territoryNotifications = true;
    private boolean technologyNotifications = true;
    private boolean socialNotifications = true;
    private boolean questNotifications = true;
    private boolean eventNotifications = true;

    // 免打扰模式
    private boolean doNotDisturb;
    private Instant dndEndTime;
    private String dndReason;

    // 过滤设置
    private NotificationPriority minPriority = NotificationPriority.LOW;
    private boolean filterDuplicates = true;
    private int maxNotificationsPerHour = 50;

    public PlayerNotificationSettings(UUID playerId) {
        this.playerId = playerId;
        this.enabled = true;
        this.soundEnabled = true;
        this.chatEnabled = true;
        this.actionBarEnabled = true;
        this.titleEnabled = true;
        this.notificationCenterEnabled = true;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public boolean isEnabled() { return enabled; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public boolean isChatEnabled() { return chatEnabled; }
    public boolean isActionBarEnabled() { return actionBarEnabled; }
    public boolean isTitleEnabled() { return titleEnabled; }
    public boolean isNotificationCenterEnabled() { return notificationCenterEnabled; }
    public boolean isNationNotifications() { return nationNotifications; }
    public boolean isDiplomacyNotifications() { return diplomacyNotifications; }
    public boolean isMilitaryNotifications() { return militaryNotifications; }
    public boolean isEconomyNotifications() { return economyNotifications; }
    public boolean isTerritoryNotifications() { return territoryNotifications; }
    public boolean isTechnologyNotifications() { return technologyNotifications; }
    public boolean isSocialNotifications() { return socialNotifications; }
    public boolean isQuestNotifications() { return questNotifications; }
    public boolean isEventNotifications() { return eventNotifications; }
    public boolean isDoNotDisturb() { return doNotDisturb; }
    public Instant getDndEndTime() { return dndEndTime; }
    public String getDndReason() { return dndReason; }
    public NotificationPriority getMinPriority() { return minPriority; }
    public boolean isFilterDuplicates() { return filterDuplicates; }
    public int getMaxNotificationsPerHour() { return maxNotificationsPerHour; }

    // Setters
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
    public void setChatEnabled(boolean chatEnabled) { this.chatEnabled = chatEnabled; }
    public void setActionBarEnabled(boolean actionBarEnabled) { this.actionBarEnabled = actionBarEnabled; }
    public void setTitleEnabled(boolean titleEnabled) { this.titleEnabled = titleEnabled; }
    public void setNotificationCenterEnabled(boolean notificationCenterEnabled) { this.notificationCenterEnabled = notificationCenterEnabled; }
    public void setNationNotifications(boolean nationNotifications) { this.nationNotifications = nationNotifications; }
    public void setDiplomacyNotifications(boolean diplomacyNotifications) { this.diplomacyNotifications = diplomacyNotifications; }
    public void setMilitaryNotifications(boolean militaryNotifications) { this.militaryNotifications = militaryNotifications; }
    public void setEconomyNotifications(boolean economyNotifications) { this.economyNotifications = economyNotifications; }
    public void setTerritoryNotifications(boolean territoryNotifications) { this.territoryNotifications = territoryNotifications; }
    public void setTechnologyNotifications(boolean technologyNotifications) { this.technologyNotifications = technologyNotifications; }
    public void setSocialNotifications(boolean socialNotifications) { this.socialNotifications = socialNotifications; }
    public void setQuestNotifications(boolean questNotifications) { this.questNotifications = questNotifications; }
    public void setEventNotifications(boolean eventNotifications) { this.eventNotifications = eventNotifications; }
    public void setDoNotDisturb(boolean doNotDisturb) { this.doNotDisturb = doNotDisturb; }
    public void setDndEndTime(Instant dndEndTime) { this.dndEndTime = dndEndTime; }
    public void setDndReason(String dndReason) { this.dndReason = dndReason; }
    public void setMinPriority(NotificationPriority minPriority) { this.minPriority = minPriority; }
    public void setFilterDuplicates(boolean filterDuplicates) { this.filterDuplicates = filterDuplicates; }
    public void setMaxNotificationsPerHour(int maxNotificationsPerHour) { this.maxNotificationsPerHour = maxNotificationsPerHour; }

    /**
     * 检查是否应该接收特定类型的通知
     */
    public boolean shouldReceive(NotificationType type) {
        if (!enabled) return false;
        if (isInDoNotDisturbMode()) return false;
        if (type.getPriority() == NotificationPriority.CRITICAL) return true; // 最高优先级总是接收

        NotificationType.Category category = type.getCategory();
        return switch (category) {
            case SYSTEM -> true;
            case NATION -> nationNotifications;
            case DIPLOMACY -> diplomacyNotifications;
            case MILITARY -> militaryNotifications;
            case ECONOMY -> economyNotifications;
            case TERRITORY -> territoryNotifications;
            case TECHNOLOGY -> technologyNotifications;
            case SOCIAL -> socialNotifications;
            case QUEST -> questNotifications;
            case EVENT -> eventNotifications;
            case SPECIAL -> true;
        };
    }

    /**
     * 检查是否处于免打扰模式
     */
    public boolean isInDoNotDisturbMode() {
        if (!doNotDisturb) return false;
        if (dndEndTime == null) return true; // 没有结束时间则一直免打扰
        return Instant.now().isBefore(dndEndTime);
    }

    /**
     * 启用免打扰模式
     */
    public void enableDoNotDisturb(String reason, Instant endTime) {
        this.doNotDisturb = true;
        this.dndReason = reason;
        this.dndEndTime = endTime;
    }

    /**
     * 禁用免打扰模式
     */
    public void disableDoNotDisturb() {
        this.doNotDisturb = false;
        this.dndReason = null;
        this.dndEndTime = null;
    }

    /**
     * 检查通知渠道是否启用
     */
    public boolean isChannelEnabled(NotificationChannel channel) {
        return switch (channel) {
            case CHAT -> chatEnabled;
            case ACTION_BAR -> actionBarEnabled;
            case TITLE -> titleEnabled;
            case NOTIFICATION_CENTER -> notificationCenterEnabled;
            case SOUND -> soundEnabled;
            default -> true;
        };
    }
}