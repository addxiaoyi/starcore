package dev.starcore.starcore.api.v1.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.starcore.starcore.api.v1.auth.ApiAuthContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 连接管理器
 * 处理 WebSocket 连接、消息路由和广播
 */
public final class WebSocketConnectionManager {

    private final Gson gson;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong messageCounter = new AtomicLong(0);

    public WebSocketConnectionManager(Gson gson) {
        this.gson = gson;
    }

    /**
     * 注册新连接
     */
    public WebSocketSession register(String sessionId, ApiAuthContext authContext) {
        WebSocketSession session = new WebSocketSession(sessionId, authContext);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * 注销连接
     */
    public void unregister(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        if (session != null) {
            // 取消所有订阅
            for (Set<WebSocketSession> channelSessions : subscribers.values()) {
                channelSessions.remove(session);
            }
        }
    }

    /**
     * 处理消息
     */
    public String handleMessage(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            return serialize(WebSocketMessage.Error.of("SESSION_NOT_FOUND", "Session not found"));
        }

        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            String id = json.has("id") ? json.get("id").getAsString() : null;

            return switch (type) {
                case "ping" -> serialize(WebSocketMessage.Pong.create());
                case "subscribe" -> handleSubscribe(session, id, json);
                case "unsubscribe" -> handleUnsubscribe(session, id, json);
                case "request" -> handleRequest(session, id, json);
                default -> serialize(WebSocketMessage.Response.error(id, "Unknown message type: " + type));
            };
        } catch (Exception e) {
            return serialize(WebSocketMessage.Error.of("PARSE_ERROR", "Failed to parse message: " + e.getMessage()));
        }
    }

    /**
     * 处理二进制消息
     */
    public ByteBuffer handleBinary(String sessionId, ByteBuffer data) {
        // 目前不支持二进制消息
        return null;
    }

    /**
     * 订阅频道
     */
    private String handleSubscribe(WebSocketSession session, String id, JsonObject json) {
        if (!json.has("data")) {
            return serialize(WebSocketMessage.Response.error(id, "Missing data field"));
        }
        String channel = json.getAsJsonObject("data").get("channel").getAsString();
        if (channel == null || channel.isBlank()) {
            return serialize(WebSocketMessage.Response.error(id, "Invalid channel"));
        }

        subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(session);
        session.addSubscription(channel);

        return serialize(WebSocketMessage.Response.ok(id, Map.of("channel", channel)));
    }

    /**
     * 取消订阅
     */
    private String handleUnsubscribe(WebSocketSession session, String id, JsonObject json) {
        if (!json.has("data")) {
            return serialize(WebSocketMessage.Response.error(id, "Missing data field"));
        }
        String channel = json.getAsJsonObject("data").get("channel").getAsString();
        if (channel != null) {
            Set<WebSocketSession> channelSessions = subscribers.get(channel);
            if (channelSessions != null) {
                channelSessions.remove(session);
            }
            session.removeSubscription(channel);
        }

        return serialize(WebSocketMessage.Response.ok(id, Map.of("channel", channel)));
    }

    /**
     * 处理请求
     */
    private String handleRequest(WebSocketSession session, String id, JsonObject json) {
        if (!json.has("data")) {
            return serialize(WebSocketMessage.Response.error(id, "Missing data field"));
        }

        // 路由到对应的处理器
        String action = json.getAsJsonObject("data").has("action")
            ? json.getAsJsonObject("data").get("action").getAsString()
            : null;

        if (action == null) {
            return serialize(WebSocketMessage.Response.error(id, "Missing action field"));
        }

        // 返回处理中响应（实际处理由处理器异步完成）
        return serialize(WebSocketMessage.Response.ok(id, Map.of("status", "processing", "action", action)));
    }

    /**
     * 广播消息到频道
     */
    public void broadcast(String channel, Object data) {
        Set<WebSocketSession> channelSessions = subscribers.get(channel);
        if (channelSessions == null || channelSessions.isEmpty()) {
            return;
        }

        String message = serialize(WebSocketMessage.Event.of(channel, "update", data));
        for (WebSocketSession session : channelSessions) {
            session.send(message);
        }
    }

    /**
     * 发送消息到特定会话
     */
    public void sendTo(String sessionId, WebSocketMessage message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            session.send(serialize(message));
        }
    }

    /**
     * 获取活跃会话数
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * 获取频道订阅者数
     */
    public int getChannelSubscriberCount(String channel) {
        Set<WebSocketSession> channelSessions = subscribers.get(channel);
        return channelSessions != null ? channelSessions.size() : 0;
    }

    private String serialize(Object obj) {
        return gson.toJson(obj);
    }

    /**
     * WebSocket 会话
     */
    public static class WebSocketSession {
        private final String sessionId;
        private final ApiAuthContext authContext;
        private final Set<String> subscriptions = new CopyOnWriteArraySet<>();
        private final long createdAt = System.currentTimeMillis();
        private volatile long lastPing = System.currentTimeMillis();

        // 消息发送回调（由底层 WebSocket 实现设置）
        private volatile MessageSender messageSender;

        public WebSocketSession(String sessionId, ApiAuthContext authContext) {
            this.sessionId = sessionId;
            this.authContext = authContext;
        }

        public String getSessionId() {
            return sessionId;
        }

        public ApiAuthContext getAuthContext() {
            return authContext;
        }

        public Set<String> getSubscriptions() {
            return Set.copyOf(subscriptions);
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void addSubscription(String channel) {
            subscriptions.add(channel);
        }

        public void removeSubscription(String channel) {
            subscriptions.remove(channel);
        }

        public boolean isSubscribed(String channel) {
            return subscriptions.contains(channel);
        }

        public void setMessageSender(MessageSender sender) {
            this.messageSender = sender;
        }

        public void send(String message) {
            if (messageSender != null) {
                try {
                    messageSender.send(message);
                } catch (IOException e) {
                    // 忽略发送失败
                }
            }
        }

        public void ping() {
            lastPing = System.currentTimeMillis();
        }

        public long getLastPing() {
            return lastPing;
        }

        public boolean isStale(long timeoutMillis) {
            return System.currentTimeMillis() - lastPing > timeoutMillis;
        }
    }

    /**
     * 消息发送器接口
     */
    @FunctionalInterface
    public interface MessageSender {
        void send(String message) throws IOException;
    }
}
