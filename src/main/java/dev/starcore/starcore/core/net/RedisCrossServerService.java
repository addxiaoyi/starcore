package dev.starcore.starcore.core.net;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.config.RedisSettings;
import dev.starcore.starcore.core.service.StarCoreService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

/**
 * Cross-server communication service using Redis pub/sub.
 * Provides nation message broadcasting and war status synchronization across multiple servers.
 */
public class RedisCrossServerService implements StarCoreService {

    public static final String CHANNEL_NATION_MESSAGE = "starcore:nation:message";
    public static final String CHANNEL_WAR_UPDATE = "starcore:war:update";
    public static final String CHANNEL_SYNC_REQUEST = "starcore:sync:request";
    public static final String CHANNEL_PLAYER_DATA = "starcore:player:data";
    public static final String CHANNEL_CHAT = "starcore:chat:message";
    public static final String CHANNEL_BROADCAST = "starcore:broadcast";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final RedisSettings settings;
    private volatile boolean connected = false;
    private volatile boolean subscribed = false;

    private redis.clients.jedis.JedisPool jedisPool;
    private Thread subscriberThread;
    // E-048: 保存当前活动的 pubSub 以便 stopSubscriber 显式 unsubscribe() 解除阻塞 socket read,
    // 避免 interrupt 不一定能唤醒阻塞的 jedis.subscribe
    private volatile redis.clients.jedis.JedisPubSub activePubSub;

    // 消息处理器映射
    // E-049: 用 CopyOnWriteArrayList 替代 ArrayList,避免多线程注册 handler 时
    // 遍历(处理消息)与 add 并发触发 ConcurrentModificationException
    private final Map<String, List<Consumer<String>>> channelHandlers = new ConcurrentHashMap<>();

    // E-044/E-045: 已处理 messageId + 时间戳,使用 ConcurrentHashMap<String, Long> + 按时间过期
    // 替代原 Set,这样 cleanup 可按时间维度精确移除最早条目而不是迭代顺序无保证的 ConcurrentHashMap
    private final Map<String, Long> processedMessageIdTimestamps = new ConcurrentHashMap<>();
    private static final long PROCESSED_ID_TTL_MS = 5 * 60 * 1000L; // 5 分钟
    private static final int PROCESSED_ID_MAX = 5000;

    public RedisCrossServerService(JavaPlugin plugin, Logger logger, RedisSettings settings) {
        this.plugin = plugin;
        this.logger = logger;
        this.settings = settings;
    }

    @Override
    public void start(StarCoreContext context) {
        if (!settings.enabled()) {
            logInfo("[Redis] 跨服通信已禁用");
            return;
        }

        try {
            redis.clients.jedis.JedisPoolConfig poolConfig = new redis.clients.jedis.JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            poolConfig.setMaxIdle(4);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            String password = settings.password();
            if (password != null && !password.isBlank()) {
                jedisPool = new redis.clients.jedis.JedisPool(poolConfig, settings.host(), settings.port(), 10000, password, settings.database());
            } else {
                jedisPool = new redis.clients.jedis.JedisPool(poolConfig, settings.host(), settings.port(), 10000, null, settings.database());
            }

            // Test connection
            try (redis.clients.jedis.Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                connected = true;
                logInfo("[Redis] 已连接到 {}:{}", settings.host(), settings.port());
            }

            // 启动订阅线程
            startSubscriber();
        } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
            logWarn("[Redis] 无法连接到 Redis 服务器: {}:{} - {}", settings.host(), settings.port(), e.getMessage());
            closePool();
        } catch (Exception e) {
            logError("[Redis] 初始化 Redis 连接池时发生错误: " + e.getMessage(), e);
            closePool();
        }
    }

    @Override
    public void stop(StarCoreContext context) {
        stopSubscriber();
        closePool();
        connected = false;
        subscribed = false;
        logInfo("[Redis] 跨服通信服务已停止");
    }

    /**
     * 启动订阅线程
     */
    private void startSubscriber() {
        if (subscriberThread != null && subscriberThread.isAlive()) {
            return;
        }

        subscriberThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && connected) {
                try (redis.clients.jedis.Jedis subscriberJedis = jedisPool.getResource()) {
                    redis.clients.jedis.JedisPubSub pubSub = createPubSub();
                    activePubSub = pubSub;

                    // 订阅所有频道
                    String[] channels = {
                        CHANNEL_NATION_MESSAGE,
                        CHANNEL_WAR_UPDATE,
                        CHANNEL_SYNC_REQUEST,
                        CHANNEL_PLAYER_DATA,
                        CHANNEL_CHAT,
                        CHANNEL_BROADCAST
                    };

                    subscribed = true;
                    logInfo("[Redis] 开始订阅频道");
                    subscriberJedis.subscribe(pubSub, channels);
                    // subscribe 返回说明订阅结束,清空 activePubSub
                    activePubSub = null;
                } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        logWarn("[Redis] 订阅连接断开，尝试重连...");
                        subscribed = false;
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        logWarn("[Redis] 订阅异常: " + e.getMessage());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            subscribed = false;
        }, "Redis-Subscriber-Thread");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    /**
     * 停止订阅线程
     */
    private void stopSubscriber() {
        // E-048: 显式 pubSub.unsubscribe() 唤醒阻塞 socket read;jedis subscribe 对 interrupt 不一定响应
        redis.clients.jedis.JedisPubSub ps = activePubSub;
        if (ps != null) {
            try {
                if (ps.isSubscribed()) {
                    ps.unsubscribe();
                }
            } catch (Exception ignored) {
                // ignore
            }
            activePubSub = null;
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
            try {
                subscriberThread.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            subscriberThread = null;
        }
        subscribed = false;
    }

    /**
     * 创建 PubSub 处理器
     */
    private redis.clients.jedis.JedisPubSub createPubSub() {
        return new redis.clients.jedis.JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                try {
                    // 解析消息，检查是否重复
                    String messageId = extractMessageId(message);
                    if (messageId != null) {
                        // E-044/E-045: 用 timestamp 表判断重复与过期
                        long now = System.currentTimeMillis();
                        Long prev = processedMessageIdTimestamps.putIfAbsent(messageId, now);
                        if (prev != null) {
                            logFine("[Redis] 忽略重复消息: " + messageId);
                            return;
                        }
                    }

                    // 清理过期的消息ID
                    cleanupProcessedIds();

                    // 通知处理器
                    List<Consumer<String>> handlers = channelHandlers.get(channel);
                    if (handlers != null) {
                        for (Consumer<String> handler : handlers) {
                            try {
                                handler.accept(message);
                            } catch (Exception e) {
                                logWarn("[Redis] 处理消息异常: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    logWarn("[Redis] 处理消息出错: " + e.getMessage());
                }
            }

            @Override
            public void onSubscribe(String channel, int subscribedChannels) {
                logInfo("[Redis] 已订阅频道: " + channel);
            }

            @Override
            public void onUnsubscribe(String channel, int subscribedChannels) {
                logFine("[Redis] 已取消订阅频道: " + channel);
            }

            @Override
            public void onPSubscribe(String pattern, int subscribedChannels) {
                logInfo("[Redis] 已订阅模式: " + pattern);
            }

            @Override
            public void onPUnsubscribe(String pattern, int subscribedChannels) {
                logFine("[Redis] 已取消订阅模式: " + pattern);
            }
        };
    }

    /**
     * 从消息中提取 MessageId
     * E-043: 原基于字符串索引的 fallback 用 timestamp 截取 13 位,既不稳定(可被攻击者构造
     * 多 timestamp 绕过去重)又可能误判同毫秒消息为重复丢弃。改用 Gson 解析 JSON,
     * 提取 messageId 字段;若不存在或解析失败则返回 null,接收方不去重(向后兼容),
     * 同时避免误判丢弃合法消息。
     */
    private String extractMessageId(String message) {
        if (message == null || message.isBlank()) return null;
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(message).getAsJsonObject();
            com.google.gson.JsonElement idEl = root.get("messageId");
            if (idEl != null && !idEl.isJsonNull()) {
                return idEl.getAsString();
            }
            return null;
        } catch (Exception e) {
            // 非 JSON 或解析失败 → 视为无 messageId,不去重
            return null;
        }
    }

    /**
     * 清理过期的消息ID
     * E-044/E-045: 用 ConcurrentHashMap<String, Long>(时间戳) 按时间维度精确过期,
     * 替代原 iterate 取前 100 个的弱一致迭代清理。同时限制总条目数量防止内存膨胀。
     */
    private void cleanupProcessedIds() {
        long now = System.currentTimeMillis();
        long cutoff = now - PROCESSED_ID_TTL_MS;
        // 仅在超过 max 时强清理(少量 entry 不清理),减少开销
        if (processedMessageIdTimestamps.size() < PROCESSED_ID_MAX && Math.random() < 0.99) {
            return;
        }
        // 移除过期条目
        Iterator<Map.Entry<String, Long>> iter = processedMessageIdTimestamps.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Long> e = iter.next();
            if (e.getValue() != null && e.getValue() < cutoff) {
                iter.remove();
            }
        }
    }

    /**
     * 注册频道消息处理器
     */
    public void registerHandler(String channel, Consumer<String> handler) {
        // E-049: CopyOnWriteArrayList,遍历时安全
        channelHandlers.computeIfAbsent(channel, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(handler);
        logInfo("[Redis] 注册处理器: " + channel);
    }

    /**
     * 注册全局消息处理器（处理所有频道）
     */
    public void registerGlobalHandler(BiConsumer<String, String> handler) {
        // 全局处理器通过注册所有频道实现
        String[] channels = {
            CHANNEL_NATION_MESSAGE,
            CHANNEL_WAR_UPDATE,
            CHANNEL_SYNC_REQUEST,
            CHANNEL_PLAYER_DATA,
            CHANNEL_CHAT,
            CHANNEL_BROADCAST
        };
        for (String channel : channels) {
            registerHandler(channel, msg -> handler.accept(channel, msg));
        }
    }

    /**
     * Publish a message to a Redis channel.
     *
     * @param channel the channel name
     * @param message the message to publish
     * @return true if published successfully
     */
    public boolean publish(String channel, String message) {
        if (!settings.enabled() || !connected || jedisPool == null) {
            return false;
        }

        try (redis.clients.jedis.Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, message);
            return true;
        } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
            logWarn("[Redis] 发布消息到频道 {} 失败: 连接错误", channel);
            connected = false;
            // E-047: 主动触发重连,关闭旧池子并启动新订阅线程;否则 connected=false 后无任何重连
            triggerReconnect();
            return false;
        } catch (Exception e) {
            logError("[Redis] 发布消息到频道 " + channel + " 失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * E-047: 关闭旧连接池并尝试重启订阅线程,让 publish 失败后能恢复连接。
     */
    private void triggerReconnect() {
        try {
            stopSubscriber();
        } catch (Exception ignored) {
            // ignore
        }
        try {
            closePool();
        } catch (Exception ignored) {
            // ignore
        }
        if (settings.enabled()) {
            try {
                // 重新创建连接池并订阅(异步执行避免在调用 publish 的线程上阻塞)
                new Thread(this::reconnectAsync, "Redis-Reconnect").start();
            } catch (Exception ex) {
                logWarn("[Redis] 触发重连失败: " + ex.getMessage());
            }
        }
    }

    /** 重连主流程:重建连接池 -> 标记 connected=true -> 启动订阅线程 */
    private void reconnectAsync() {
        try {
            redis.clients.jedis.JedisPoolConfig poolConfig = new redis.clients.jedis.JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            poolConfig.setMaxIdle(4);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            String password = settings.password();
            if (password != null && !password.isBlank()) {
                jedisPool = new redis.clients.jedis.JedisPool(poolConfig, settings.host(), settings.port(), 10000, password, settings.database());
            } else {
                jedisPool = new redis.clients.jedis.JedisPool(poolConfig, settings.host(), settings.port(), 10000, null, settings.database());
            }
            try (redis.clients.jedis.Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                connected = true;
                logInfo("[Redis] 重连成功 {}:{}", settings.host(), settings.port());
            }
            startSubscriber();
        } catch (Exception e) {
            logWarn("[Redis] 重连失败: " + e.getMessage());
        }
    }

    /**
     * Broadcast a nation-related message to all connected servers.
     *
     * @param nationId the nation ID
     * @param action   the action type (e.g., "declare_war", "peace", "treasury_change")
     * @param data     additional JSON data
     * @return true if broadcast successful
     */
    public boolean broadcastNationMessage(UUID nationId, String action, String data) {
        if (!settings.enabled()) {
            return false;
        }

        String message = buildMessage("nation", nationId.toString(), action, data);
        return publish(CHANNEL_NATION_MESSAGE, message);
    }

    /**
     * Broadcast a war status update to all connected servers.
     *
     * @param warKey the war key (composite key like "uuid1:uuid2")
     * @param action the update action (e.g., "started", "ended", "score_update")
     * @param data   additional JSON data
     * @return true if broadcast successful
     */
    public boolean broadcastWarUpdate(String warKey, String action, String data) {
        if (!settings.enabled()) {
            return false;
        }

        String message = buildMessage("war", warKey, action, data);
        return publish(CHANNEL_WAR_UPDATE, message);
    }

    /**
     * Broadcast player data to all servers.
     *
     * @param playerId the player ID
     * @param dataType  the type of data (e.g., "economy", "stats", "online_status")
     * @param data      the data to sync
     * @return true if broadcast successful
     */
    public boolean broadcastPlayerData(UUID playerId, String dataType, Object data) {
        if (!settings.enabled()) {
            return false;
        }

        String message = buildPlayerMessage(playerId, dataType, data);
        return publish(CHANNEL_PLAYER_DATA, message);
    }

    /**
     * Broadcast chat message to all servers.
     *
     * @param senderId  the sender's player ID
     * @param senderName the sender's name
     * @param channel   the chat channel (e.g., "nation", "global")
     * @param targetId  the target ID (nation/clan UUID or "global")
     * @param message   the message content
     * @return true if broadcast successful
     */
    public boolean broadcastChatMessage(UUID senderId, String senderName, String channel, String targetId, String message) {
        return broadcastChatMessage(senderId, senderName, null, channel, targetId, message);
    }

    /**
     * Broadcast a chat message with source server ID (for E-122 source verification).
     */
    public boolean broadcastChatMessage(UUID senderId, String senderName, String serverId, String channel, String targetId, String message) {
        if (!settings.enabled()) {
            return false;
        }

        String data;
        if (serverId != null) {
            data = String.format(
                "{\"senderId\":\"%s\",\"senderName\":\"%s\",\"senderServerId\":\"%s\",\"channel\":\"%s\",\"targetId\":\"%s\",\"message\":\"%s\"}",
                escapeJson(senderId.toString()),
                escapeJson(senderName),
                escapeJson(serverId),
                escapeJson(channel),
                escapeJson(targetId),
                escapeJson(message)
            );
        } else {
            data = String.format(
                "{\"senderId\":\"%s\",\"senderName\":\"%s\",\"channel\":\"%s\",\"targetId\":\"%s\",\"message\":\"%s\"}",
                escapeJson(senderId.toString()),
                escapeJson(senderName),
                escapeJson(channel),
                escapeJson(targetId),
                escapeJson(message)
            );
        }

        String fullMessage = buildMessage("chat", targetId, channel, data);
        return publish(CHANNEL_CHAT, fullMessage);
    }

    /**
     * Request full sync from other servers.
     *
     * @param requestingServerId the server ID requesting sync
     * @param syncType           the type of data to sync (e.g., "nations", "wars")
     * @return true if request published successfully
     */
    public boolean requestSync(UUID requestingServerId, String syncType) {
        if (!settings.enabled()) {
            return false;
        }

        String message = buildMessage("sync", requestingServerId.toString(), syncType, "");
        return publish(CHANNEL_SYNC_REQUEST, message);
    }

    /**
     * Broadcast a message to all servers.
     *
     * @param title   the broadcast title
     * @param message the broadcast message
     * @return true if broadcast successful
     */
    public boolean broadcastServer(String title, String message) {
        if (!settings.enabled()) {
            return false;
        }

        String data = String.format(
            "{\"title\":\"%s\",\"message\":\"%s\"}",
            escapeJson(title),
            escapeJson(message)
        );
        return publish(CHANNEL_BROADCAST, data);
    }

    /**
     * Check if Redis connection is active.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        if (!settings.enabled() || !connected) {
            return false;
        }

        try (redis.clients.jedis.Jedis jedis = jedisPool.getResource()) {
            String result = jedis.ping();
            connected = "PONG".equals(result);
            return connected;
        } catch (Exception e) {
            connected = false;
            return false;
        }
    }

    /**
     * Check if subscribed to channels.
     */
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * Get the current connection status summary.
     *
     * @return status string
     */
    public String getStatus() {
        String subStatus = subscribed ? "订阅中" : "未订阅";
        return settings.summary(connected) + " | " + subStatus;
    }

    /**
     * 构造消息 JSON
     * E-046: 原 String.format + escapeJson 拼接,若调用方传入数据含特殊字符或已是 JSON 字符串,
     * 会破坏外层 JSON 结构,引发 JSON 注入/越权/解析失败。改用 Gson JsonObject 强类型构造,
     * 并接受 data 为合法 JSON 子树(避免双重转义),从而保证整体 JSON 始终合法。
     */
    private String buildMessage(String type, String id, String action, String data) {
        long timestamp = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("messageId", messageId);
        root.addProperty("type", type);
        root.addProperty("id", id);
        root.addProperty("action", action);

        if (data == null || data.isBlank()) {
            root.add("data", com.google.gson.JsonNull.INSTANCE);
        } else {
            // 如果 data 是合法 JSON,嵌入;否则当作字符串字面量
            try {
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(data);
                root.add("data", parsed);
            } catch (Exception ex) {
                root.addProperty("data", data);
            }
        }
        root.addProperty("timestamp", timestamp);
        return new com.google.gson.Gson().toJson(root);
    }

    private String buildPlayerMessage(UUID playerId, String dataType, Object data) {
        long timestamp = System.currentTimeMillis();
        String messageId = UUID.randomUUID().toString();
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("messageId", messageId);
        root.addProperty("type", "player");
        root.addProperty("playerId", playerId.toString());
        root.addProperty("dataType", dataType);

        com.google.gson.JsonElement dataEl;
        if (data == null) {
            dataEl = com.google.gson.JsonNull.INSTANCE;
        } else if (data instanceof String s) {
            // 尝试作为 JSON 解析;失败则作字符串
            try {
                dataEl = com.google.gson.JsonParser.parseString(s);
            } catch (Exception ex) {
                dataEl = new com.google.gson.JsonPrimitive(s);
            }
        } else if (data instanceof Number n) {
            dataEl = new com.google.gson.JsonPrimitive(n);
        } else if (data instanceof Boolean b) {
            dataEl = new com.google.gson.JsonPrimitive(b);
        } else if (data instanceof Map m) {
            dataEl = new com.google.gson.Gson().toJsonTree(m);
        } else {
            dataEl = com.google.gson.JsonNull.INSTANCE;
        }
        root.add("data", dataEl);
        root.addProperty("timestamp", timestamp);
        return new com.google.gson.Gson().toJson(root);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void closePool() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            try {
                jedisPool.close();
            } catch (Exception e) {
                logWarn("[Redis] 关闭连接池时出错", e.getMessage());
            }
        }
        jedisPool = null;
    }

    private void logInfo(String message, Object... args) {
        if (logger != null) {
            LogRecord record = new LogRecord(Level.INFO, args.length > 0 ? String.format(message, args) : message);
            logger.log(record);
        }
    }

    private void logWarn(String message, Object... args) {
        if (logger != null) {
            LogRecord record = new LogRecord(Level.WARNING, args.length > 0 ? String.format(message, args) : message);
            logger.log(record);
        }
    }

    private void logFine(String message) {
        if (logger != null) {
            logger.fine(message);
        }
    }

    private void logError(String message, Throwable t) {
        if (logger != null) {
            LogRecord record = new LogRecord(Level.SEVERE, message);
            record.setThrown(t);
            logger.log(record);
        }
    }

    private void logError(String message) {
        if (logger != null) {
            logger.log(Level.SEVERE, message);
        }
    }
}
