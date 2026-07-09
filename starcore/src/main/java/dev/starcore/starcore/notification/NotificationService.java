package dev.starcore.starcore.notification;

import dev.starcore.starcore.StarCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 智能通知服务
 * 核心通知系统，处理所有通知的发送、管理和显示
 */
public class NotificationService {
    private final StarCorePlugin plugin;
    private final Map<UUID, PlayerNotificationSettings> playerSettings;
    private final Map<UUID, List<Notification>> playerNotifications;
    private final Map<String, Long> recentNotifications; // 去重缓存
    private final Map<UUID, Integer> hourlyNotificationCount; // 每小时通知计数

    // 通知处理器
    private final List<NotificationHandler> handlers;

    // 配置
    private boolean globalEnabled = true;
    private int maxNotificationsPerPlayer = 100;
    private int notificationExpireHours = 24;
    private boolean enableSound = true;
    private boolean enableTitle = true;
    private boolean enableActionBar = true;

    public NotificationService(StarCorePlugin plugin) {
        this.plugin = plugin;
        this.playerSettings = new ConcurrentHashMap<>();
        this.playerNotifications = new ConcurrentHashMap<>();
        this.recentNotifications = new ConcurrentHashMap<>();
        this.hourlyNotificationCount = new ConcurrentHashMap<>();
        this.handlers = new CopyOnWriteArrayList<>();

        // 注册默认处理器
        registerDefaultHandlers();

        // 启动定时清理任务
        startCleanupTask();
    }

    /**
     * 注册默认通知处理器
     */
    private void registerDefaultHandlers() {
        // Chat Handler
        registerHandler((notification, player, settings) -> {
            if (settings.isChatEnabled() && notification.getPriority().canBatch()) {
                String prefix = getTypePrefix(notification.getType());
                String message = prefix + notification.getMessage();
                player.sendMessage(message);
            }
        });

        // Sound Handler
        registerHandler((notification, player, settings) -> {
            if (settings.isSoundEnabled() && notification.getPriority().shouldPlaySound()) {
                Sound sound = getSoundForPriority(notification.getPriority());
                player.playSound(player.getLocation(), sound, SoundCategory.MASTER,
                    0.5f, notification.getPriority().getSoundVolume() / 10f);
            }
        });

        // Title Handler
        registerHandler((notification, player, settings) -> {
            if (settings.isTitleEnabled() && notification.getPriority().shouldAnimate()) {
                int fadeIn = 10;
                int stay = 40;
                int fadeOut = 20;
                player.sendTitle(
                    formatTitle(notification.getTitle(), notification.getPriority()),
                    notification.getMessage(),
                    fadeIn, stay, fadeOut
                );
            }
        });

        // Action Bar Handler
        registerHandler((notification, player, settings) -> {
            if (settings.isActionBarEnabled() && notification.getPriority().getLevel() >= 2) {
                player.sendActionBar(Component.text(
                    formatActionBar(notification.getMessage(), notification.getPriority())
                ));
            }
        });
    }

    /**
     * 注册通知处理器
     */
    public void registerHandler(NotificationHandler handler) {
        handlers.add(handler);
    }

    /**
     * 发送通知给单个玩家
     */
    public void send(Notification notification) {
        if (!globalEnabled) return;

        Player player = Bukkit.getPlayer(notification.getReceiverId());
        if (player == null || !player.isOnline()) {
            // 离线玩家，存储到通知列表
            addToNotificationList(notification);
            return;
        }

        // 获取玩家设置
        PlayerNotificationSettings settings = getPlayerSettings(player.getUniqueId());

        // 检查是否应该接收
        if (!settings.shouldReceive(notification.getType())) {
            return;
        }

        // 检查优先级过滤
        if (notification.getPriority().getLevel() < settings.getMinPriority().getLevel()) {
            return;
        }

        // 去重检查
        if (settings.isFilterDuplicates() && shouldSkipDuplicate(notification)) {
            return;
        }

        // 频率限制
        if (isRateLimited(player.getUniqueId(), settings)) {
            return;
        }

        // 执行所有处理器
        for (NotificationHandler handler : handlers) {
            try {
                handler.handle(notification, player, settings);
            } catch (Exception e) {
                plugin.getLogger().warning("通知处理器异常: " + e.getMessage());
            }
        }

        // 添加到通知列表
        addToNotificationList(notification);

        // 更新计数
        incrementHourlyCount(player.getUniqueId());
    }

    /**
     * 发送通知给多个玩家
     */
    public void sendToPlayers(Collection<UUID> playerIds, Notification notification) {
        for (UUID playerId : playerIds) {
            send(copyWithReceiver(notification, playerId));
        }
    }

    /**
     * 发送通知给国家所有成员
     */
    public void sendToNation(UUID nationId, Notification notification) {
        // 这里应该从 NationService 获取国家成员
        // 暂时使用广播方式
        Bukkit.getOnlinePlayers().forEach(player -> {
            send(copyWithReceiver(notification, player.getUniqueId()));
        });
    }

    /**
     * 复制通知并设置新的接收者
     */
    private Notification copyWithReceiver(Notification original, UUID newReceiverId) {
        return Notification.builder()
            .id(original.getId())
            .type(original.getType())
            .priority(original.getPriority())
            .title(original.getTitle())
            .message(original.getMessage())
            .titleComponent(original.getTitleComponent())
            .messageComponent(original.getMessageComponent())
            .senderId(original.getSenderId())
            .receiverId(newReceiverId)
            .relatedNationId(original.getRelatedNationId())
            .createdAt(original.getCreatedAt())
            .expireAt(original.getExpireAt())
            .primaryChannel(original.getPrimaryChannel())
            .metadata(original.getMetadata())
            .build();
    }

    /**
     * 发送战争通知（高优先级）
     */
    public void sendWarNotification(UUID nationId, String attackerName, String message) {
        Notification notification = Notification.builder()
            .type(NotificationType.DIPLOMACY_WAR_DECLARE)
            .priority(NotificationPriority.URGENT)
            .title("§c战争警报!")
            .message(message)
            .primaryChannel(NotificationChannel.TITLE)
            .build();

        sendToNation(nationId, notification);
    }

    /**
     * 发送领土警报
     */
    public void sendTerritoryAlert(UUID nationId, String territoryName, String attackerName) {
        Notification notification = Notification.builder()
            .type(NotificationType.TERRITORY_DISPUTE)
            .priority(NotificationPriority.HIGH)
            .title("§e领土警报")
            .message(attackerName + " 正在进攻 " + territoryName + "!")
            .primaryChannel(NotificationChannel.ACTION_BAR)
            .build();

        sendToNation(nationId, notification);
    }

    /**
     * 发送经济警报
     */
    public void sendEconomyAlert(UUID playerId, String message) {
        Notification notification = Notification.builder()
            .type(NotificationType.ECONOMY_TREASURY_LOW)
            .priority(NotificationPriority.HIGH)
            .title("§6经济警报")
            .message(message)
            .primaryChannel(NotificationChannel.CHAT)
            .build();

        send(copyWithReceiver(notification, playerId));
    }

    /**
     * 发送简单文本通知
     */
    public void sendSimple(UUID playerId, NotificationType type, String title, String message) {
        send(Notification.builder()
            .type(type)
            .receiverId(playerId)
            .title(title)
            .message(message)
            .build());
    }

    /**
     * 发送带组件的通知
     */
    public void sendComponent(UUID playerId, NotificationType type, ComponentLike title, ComponentLike message) {
        Notification notification = Notification.builder()
            .type(type)
            .receiverId(playerId)
            .titleComponent(title.asComponent())
            .messageComponent(message.asComponent())
            .build();

        send(notification);
    }

    /**
     * 获取玩家通知设置
     */
    public PlayerNotificationSettings getPlayerSettings(UUID playerId) {
        return playerSettings.computeIfAbsent(playerId, PlayerNotificationSettings::new);
    }

    /**
     * 更新玩家通知设置
     */
    public void updatePlayerSettings(PlayerNotificationSettings settings) {
        playerSettings.put(settings.getPlayerId(), settings);
    }

    /**
     * 获取玩家未读通知数量
     */
    public int getUnreadCount(UUID playerId) {
        List<Notification> notifications = playerNotifications.get(playerId);
        if (notifications == null) return 0;
        return (int) notifications.stream().filter(n -> !n.isRead()).count();
    }

    /**
     * 获取玩家通知列表
     */
    public List<Notification> getPlayerNotifications(UUID playerId) {
        return playerNotifications.getOrDefault(playerId, Collections.emptyList());
    }

    /**
     * 标记通知为已读
     */
    public void markAsRead(UUID playerId, String notificationId) {
        List<Notification> notifications = playerNotifications.get(playerId);
        if (notifications != null) {
            notifications.stream()
                .filter(n -> n.getId().equals(notificationId))
                .findFirst()
                .ifPresent(n -> {
                    // 由于 Notification 是不可变的，这里需要重建
                    // 简化处理：移除该通知
                    notifications.remove(n);
                });
        }
    }

    /**
     * 标记所有通知为已读
     */
    public void markAllAsRead(UUID playerId) {
        playerNotifications.remove(playerId);
    }

    /**
     * 清除过期通知
     */
    public void clearExpiredNotifications() {
        Instant expireThreshold = Instant.now().minus(Duration.ofHours(notificationExpireHours));
        playerNotifications.values().forEach(list ->
            list.removeIf(n -> n.getCreatedAt().isBefore(expireThreshold))
        );
    }

    // ==================== 私有辅助方法 ====================

    private void addToNotificationList(Notification notification) {
        playerNotifications.computeIfAbsent(notification.getReceiverId(), k -> new CopyOnWriteArrayList<>())
            .add(notification);

        // 限制最大数量
        List<Notification> list = playerNotifications.get(notification.getReceiverId());
        while (list.size() > maxNotificationsPerPlayer) {
            list.remove(0);
        }
    }

    private boolean shouldSkipDuplicate(Notification notification) {
        String key = notification.getReceiverId() + ":" + notification.getType().name() + ":" + notification.getMessage();
        long now = System.currentTimeMillis();

        Long lastTime = recentNotifications.get(key);
        if (lastTime != null && now - lastTime < 5000) { // 5秒内相同通知视为重复
            return true;
        }

        recentNotifications.put(key, now);

        // 清理过期记录
        recentNotifications.entrySet().removeIf(e -> now - e.getValue() > 60000);

        return false;
    }

    private boolean isRateLimited(UUID playerId, PlayerNotificationSettings settings) {
        int count = hourlyNotificationCount.getOrDefault(playerId, 0);
        return count >= settings.getMaxNotificationsPerHour();
    }

    private void incrementHourlyCount(UUID playerId) {
        hourlyNotificationCount.merge(playerId, 1, Integer::sum);
    }

    private String getTypePrefix(NotificationType type) {
        return switch (type.getCategory()) {
            case SYSTEM -> "§7[系统] ";
            case NATION -> "§6[国家] ";
            case DIPLOMACY -> "§c[外交] ";
            case MILITARY -> "§4[军事] ";
            case ECONOMY -> "§e[经济] ";
            case TERRITORY -> "§a[领土] ";
            case TECHNOLOGY -> "§b[科技] ";
            case SOCIAL -> "§d[社交] ";
            case QUEST -> "§5[任务] ";
            case EVENT -> "§f[活动] ";
            case SPECIAL -> "§9[特别] ";
        };
    }

    private Sound getSoundForPriority(NotificationPriority priority) {
        return switch (priority) {
            case LOWEST, LOW -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case NORMAL -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case HIGH -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            case URGENT -> Sound.BLOCK_BELL_USE;
            case CRITICAL -> Sound.BLOCK_BEACON_ACTIVATE;
        };
    }

    private String formatTitle(String title, NotificationPriority priority) {
        return switch (priority) {
            case URGENT -> "§c§l" + title;
            case HIGH -> "§e§l" + title;
            default -> "§f§l" + title;
        };
    }

    private String formatActionBar(String message, NotificationPriority priority) {
        String color = switch (priority) {
            case URGENT -> "§c";
            case HIGH -> "§e";
            default -> "§f";
        };
        return color + message;
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            clearExpiredNotifications();
            hourlyNotificationCount.clear();
        }, 20L * 60 * 60, 20L * 60 * 60); // 每小时清理一次
    }

    /**
     * 通知处理器接口
     */
    @FunctionalInterface
    public interface NotificationHandler {
        void handle(Notification notification, Player player, PlayerNotificationSettings settings);
    }

    // 配置方法
    public void setGlobalEnabled(boolean enabled) { this.globalEnabled = enabled; }
    public void setMaxNotificationsPerPlayer(int max) { this.maxNotificationsPerPlayer = max; }
    public void setNotificationExpireHours(int hours) { this.notificationExpireHours = hours; }
    public void setEnableSound(boolean enable) { this.enableSound = enable; }
    public void setEnableTitle(boolean enable) { this.enableTitle = enable; }
    public void setEnableActionBar(boolean enable) { this.enableActionBar = enable; }

    public boolean isGlobalEnabled() { return globalEnabled; }
    public int getMaxNotificationsPerPlayer() { return maxNotificationsPerPlayer; }
    public int getNotificationExpireHours() { return notificationExpireHours; }
    public boolean isEnableSound() { return enableSound; }
    public boolean isEnableTitle() { return enableTitle; }
    public boolean isEnableActionBar() { return enableActionBar; }
}