package dev.starcore.starcore.crossserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.core.net.RedisCrossServerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * 跨服聊天同步服务
 *
 * 功能:
 * 1. 国家频道聊天跨服
 * 2. 派系频道聊天跨服
 * 3. 全局广播跨服
 * 4. 私聊跨服（跨服消息通知）
 */
public class CrossServerChatSync {
    // 颜色常量
    private static final TextColor COLOR_BRAND_A = TextColor.color(0x5BC8FF);
    private static final TextColor COLOR_BRAND_B = TextColor.color(0xFFD479);
    private static final TextColor COLOR_SUCCESS = TextColor.color(0x5BF0A5);
    private static final TextColor COLOR_ERROR = TextColor.color(0xFF6B6B);
    private static final TextColor COLOR_WARN = TextColor.color(0xFFC83D);
    private static final TextColor COLOR_INFO = TextColor.color(0x8AB4FF);
    private static final TextColor COLOR_ACCENT = TextColor.color(0xFFD479);
    private static final TextColor COLOR_MUTED = TextColor.color(0x8A93A6);

    private final Plugin plugin;
    private final RedisCrossServerService redisService;
    private final Logger logger;
    private final Gson gson = new GsonBuilder().create();

    // 聊天频道处理器
    private final Map<String, ChatChannelHandler> channelHandlers = new ConcurrentHashMap<>();

    // 跨服消息历史（用于去重）
    private final Set<String> processedMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Deque<String> messageHistory = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY = 100;
    private static final long MESSAGE_ID_TTL_MS = 60_000L; // 消息ID去重窗口：60秒

    // 本服务器标识（用于来源校验）
    private final String localServerId;

    // 玩家聊天模式缓存
    private final Map<UUID, String> playerChatModes = new ConcurrentHashMap<>();

    // 是否启用跨服聊天
    private boolean enabled = false;

    public CrossServerChatSync(Plugin plugin, RedisCrossServerService redisService) {
        this.plugin = plugin;
        this.redisService = redisService;
        this.logger = plugin.getLogger();
        this.localServerId = plugin.getServer().getName(); // 用服务器名称作为本地标识
        // 延迟初始化，等待 Redis 完全启动后再调用 start()
        logger.info("[跨服] 聊天同步服务已创建，等待 start() 启用");
    }

    /**
     * 启动服务并注册处理器（由外部在 Redis 完全连接后调用）
     */
    public void start() {
        if (redisService == null || !redisService.isConnected()) {
            logger.info("[跨服] 聊天同步服务未启用（Redis未连接）");
            return;
        }
        registerHandlers();
        enabled = true;
        logger.info("[跨服] 聊天同步服务已启用");
    }

    /**
     * 注册消息处理器
     */
    private void registerHandlers() {
        redisService.registerHandler(RedisCrossServerService.CHANNEL_CHAT, this::handleChatMessage);
        redisService.registerHandler(RedisCrossServerService.CHANNEL_BROADCAST, this::handleBroadcastMessage);
        logger.info("[跨服] 聊天消息处理器已注册");
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 聊天发送接口 ====================

    /**
     * 发送国家频道聊天
     */
    public void sendNationChat(UUID senderId, String senderName, UUID nationId, String message) {
        if (!enabled) return;
        broadcastChat("nation", senderId, senderName, nationId.toString(), message);
    }

    /**
     * 发送派系频道聊天
     */
    public void sendClanChat(UUID senderId, String senderName, UUID clanId, String message) {
        if (!enabled) return;
        broadcastChat("clan", senderId, senderName, clanId.toString(), message);
    }

    /**
     * 发送盟友频道聊天
     */
    public void sendAllianceChat(UUID senderId, String senderName, UUID allianceId, String message) {
        if (!enabled) return;
        broadcastChat("alliance", senderId, senderName, allianceId.toString(), message);
    }

    /**
     * 发送全球广播
     */
    public void sendGlobalBroadcast(String title, String message) {
        if (!enabled) return;
        redisService.broadcastServer(title, message);
    }

    /**
     * 设置玩家聊天模式
     */
    public void setPlayerChatMode(UUID playerId, String mode) {
        playerChatModes.put(playerId, mode);
    }

    /**
     * 获取玩家聊天模式
     */
    public String getPlayerChatMode(UUID playerId) {
        return playerChatModes.get(playerId);
    }

    /**
     * 清除玩家聊天模式
     */
    public void clearPlayerChatMode(UUID playerId) {
        playerChatModes.remove(playerId);
    }

    /**
     * 注册聊天频道处理器
     */
    public void registerChannelHandler(String channelType, ChatChannelHandler handler) {
        channelHandlers.put(channelType, handler);
    }

    /**
     * 广播聊天消息到所有服务器
     */
    private void broadcastChat(String channel, UUID senderId, String senderName, String targetId, String message) {
        if (redisService == null) return;

        // 生成消息ID用于去重（add 返回 false 表示已存在，直接跳过）
        String messageId = generateMessageId(senderId, message);
        if (!processedMessages.add(messageId)) {
            return;
        }
        trimMessageHistory();

        // E-122: 包含来源服务器ID，供接收方校验
        redisService.broadcastChatMessage(senderId, senderName, localServerId, channel, targetId, message);
    }

    private static final AtomicLong messageCounter = new AtomicLong(0);

    /**
     * 生成消息ID
     */
    private String generateMessageId(UUID senderId, String message) {
        long timestamp = System.currentTimeMillis();
        long counter = messageCounter.incrementAndGet();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((senderId + ":" + message).getBytes(StandardCharsets.UTF_8));
            String shortHash = String.format("%016x", hash[0] ^ hash[1] ^ hash[2] ^ hash[3]);
            return String.format("%s:%d:%d:%s", senderId, timestamp, counter, shortHash);
        } catch (Exception e) {
            return String.format("%s:%d:%d", senderId, timestamp, counter);
        }
    }

    /**
     * 清理消息历史
     */
    private void trimMessageHistory() {
        long cutoff = System.currentTimeMillis() - MESSAGE_ID_TTL_MS;
        while (messageHistory.size() > MAX_HISTORY) {
            String removed = messageHistory.pollFirst();
            // 不再从 processedMessages 移除（由TTL自动过期）
        }
        // 也清理过期的消息ID
        processedMessages.removeIf(id -> {
            try {
                String[] parts = id.split(":");
                if (parts.length >= 2) {
                    long timestamp = Long.parseLong(parts[1]);
                    return timestamp < cutoff;
                }
            } catch (Exception ignored) {}
            return false;
        });
    }

    /**
     * 处理聊天消息
     */
    private void handleChatMessage(String message) {
        try {
            ChatMessage chatMsg = gson.fromJson(message, ChatMessage.class);
            if (chatMsg == null || chatMsg.data == null) {
                return;
            }

            // 解析内部数据
            ChatData chatData = gson.fromJson(chatMsg.data, ChatData.class);
            if (chatData == null) {
                return;
            }

            // E-122: 来源校验——若包含 senderServerId，校验不是本服发来的消息（防伪造）
            String senderServerId = chatData.senderServerId;
            if (senderServerId != null && senderServerId.equals(localServerId)) {
                // 本服消息循环回来了，跳过
                return;
            }

            // 检查是否重复
            String messageId = generateMessageId(UUID.fromString(chatData.senderId), chatData.message);
            if (!processedMessages.add(messageId)) {
                return;
            }
            trimMessageHistory();

            // 在主线程处理消息
            Bukkit.getScheduler().runTask(plugin, () -> {
                deliverChatMessage(chatMsg.type, chatData);
            });
        } catch (Exception e) {
            logger.warning("[跨服聊天] 解析消息失败: " + e.getMessage());
        }
    }

    /**
     * 处理广播消息
     */
    private void handleBroadcastMessage(String message) {
        try {
            BroadcastData broadcast = gson.fromJson(message, BroadcastData.class);
            if (broadcast == null) {
                return;
            }

            // 检查是否重复
            String messageId = "broadcast:" + broadcast.title + ":" + broadcast.message;
            if (!processedMessages.add(messageId)) {
                return;
            }
            trimMessageHistory();

            // 在主线程显示广播
            Bukkit.getScheduler().runTask(plugin, () -> {
                displayBroadcast(broadcast.title, broadcast.message);
            });
        } catch (Exception e) {
            logger.warning("[跨服聊天] 解析广播失败: " + e.getMessage());
        }
    }

    /**
     * 投递聊天消息到本地玩家
     */
    private void deliverChatMessage(String channelType, ChatData data) {
        UUID senderId = UUID.fromString(data.senderId);

        // 查找频道处理器
        ChatChannelHandler handler = channelHandlers.get(channelType);
        if (handler != null) {
            handler.handle(data);
            return;
        }

        // 默认处理：根据频道类型格式化消息
        Component formattedMessage = formatChatMessage(channelType, data);

        // 发送给相关玩家
        if ("nation".equals(channelType) || "clan".equals(channelType) || "alliance".equals(channelType)) {
            // 发送给同国家/派系的所有玩家
            UUID targetId = UUID.fromString(data.targetId);
            sendToChannelMembers(channelType, targetId, formattedMessage, senderId);
        } else {
            // 全服消息
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getUniqueId().equals(senderId)) {
                    player.sendMessage(formattedMessage);
                }
            }
        }
    }

    /**
     * 发送给频道成员
     */
    private void sendToChannelMembers(String channelType, UUID targetId, Component message, UUID excludeSender) {
        // 根据不同频道类型找到对应成员
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getUniqueId().equals(excludeSender)) {
                continue;
            }

            // 检查玩家是否属于该频道
            String playerMode = playerChatModes.get(player.getUniqueId());
            if (channelType.equals(playerMode)) {
                player.sendMessage(message);
            } else {
                // 非专属频道但在线，显示跨服消息
                player.sendMessage(message);
            }
        }
    }

    /**
     * 格式化聊天消息
     */
    private Component formatChatMessage(String channelType, ChatData data) {
        Component prefixComponent = switch (channelType) {
            case "nation" -> Component.text("[国家] ", COLOR_BRAND_A);
            case "clan" -> Component.text("[派系] ", COLOR_BRAND_B);
            case "alliance" -> Component.text("[联盟] ", COLOR_SUCCESS);
            default -> Component.text("[聊天] ", COLOR_INFO);
        };

        Component senderComponent = Component.text(data.senderName, COLOR_ACCENT);
        Component messageComponent = Component.text(": ", COLOR_MUTED)
            .append(Component.text(data.message, COLOR_INFO));

        return prefixComponent.append(senderComponent).append(messageComponent);
    }

    /**
     * 显示广播消息
     */
    private void displayBroadcast(String title, String message) {
        Component broadcast = Component.text("[全服广播]", COLOR_WARN)
            .append(Component.text(" " + title, COLOR_ACCENT))
            .append(Component.newline())
            .append(Component.text(message, COLOR_INFO));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(broadcast);
        }
    }

    // ==================== 数据结构 ====================

    /**
     * 聊天消息（接收）
     */
    private static class ChatMessage {
        String messageId;
        String type;       // chat
        String id;         // targetId
        String action;     // channel type
        String data;       // ChatData JSON
        long timestamp;
    }

    /**
     * 聊天数据
     */
    private static class ChatData {
        String senderId;
        String senderName;
        String senderServerId; // 来源服务器ID，用于校验
        String channel;
        String targetId;
        String message;
    }

    /**
     * 广播数据
     */
    private static class BroadcastData {
        String title;
        String message;
    }

    /**
     * 聊天频道处理器接口
     */
    @FunctionalInterface
    public interface ChatChannelHandler {
        void handle(ChatData data);
    }
}
