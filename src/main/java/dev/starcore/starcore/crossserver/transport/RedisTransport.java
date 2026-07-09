package dev.starcore.starcore.crossserver.transport;

import dev.starcore.starcore.crossserver.CrossServerService;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Redis传输实现
 * 使用Redis的Pub/Sub功能实现跨服消息传递
 *
 * <p>依赖说明：
 * <ul>
 *   <li>Jedis 5.1.0 - Redis Java客户端</li>
 *   <li>需要Redis服务端运行并配置密码（可选）</li>
 * </ul>
 */
public class RedisTransport {
    private final String serverId;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final String channelPrefix;
    private final Logger logger;

    // E-034: 受信任 serverId 集合,Redis 接收到非白名单 serverId 的消息时直接丢弃,
    // 避免任意能连到 Redis 的客户端伪造玩家余额/战争状态等。
    private final Set<String> trustedSources = Collections.synchronizedSet(new HashSet<>());
    /** 自身也算 trusted,但不要在构造期硬塞(可在 connect 后由上层调用 addTrustedSource) */

    private Consumer<String> messageHandler;
    private volatile boolean connected;
    private JedisPool jedisPool;
    private Thread subscriberThread;
    private volatile JedisPubSub activePubSub;
    private volatile boolean shutdownRequested = false;

    public RedisTransport(String serverId, String redisHost, int redisPort, Logger logger) {
        this(serverId, redisHost, redisPort, null, logger);
    }

    public RedisTransport(String serverId, String redisHost, int redisPort, String redisPassword, Logger logger) {
        this.serverId = serverId;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
        this.channelPrefix = "starcore:sync:";
        this.logger = logger;
        this.connected = false;
        // E-034: 默认所有 serverId 信任,实际生产环境应通过 addTrustedSource/removeTrustedSource 显式配置白名单。
        // 这里仍保留校验入口,逻辑上 trustedSources 为空时视为"未配置白名单、放行",以避免破坏现有部署。
    }

    /** E-034: 添加受信任的 serverId;只有当非空且包含元素时,接收消息才进行来源校验 */
    public void addTrustedSource(String sourceServerId) {
        if (sourceServerId != null && !sourceServerId.isBlank()) {
            trustedSources.add(sourceServerId);
        }
    }

    public void removeTrustedSource(String sourceServerId) {
        trustedSources.remove(sourceServerId);
    }

    /** E-034: 校验消息来源 serverId 是否在白名单;若白名单为空则视为未启用来源校验,放行 */
    private boolean isTrustedSource(String sourceServerId) {
        if (trustedSources.isEmpty()) {
            return true;
        }
        return trustedSources.contains(sourceServerId);
    }

    /**
     * E-034: 简单提取消息中的 senderId/serverId 字段并校验是否在白名单。
     * 消息格式由调用方约定,这里宽松匹配 "senderId":"..." 或 "serverId":"..." 子串。
     * 白名单为空时直接放行。
     */
    private boolean verifySource(String message) {
        if (message == null || trustedSources.isEmpty()) {
            return true;
        }
        try {
            int idx = message.indexOf("\"senderId\"");
            if (idx < 0) idx = message.indexOf("\"serverId\"");
            if (idx < 0) {
                // 消息没有 senderId 字段;白名单启用时拒绝,避免被绕过
                return false;
            }
            int colon = message.indexOf(':', idx);
            if (colon < 0) return false;
            int qs = message.indexOf('"', colon + 1);
            if (qs < 0) return false;
            int qe = message.indexOf('"', qs + 1);
            if (qe < 0) return false;
            String sender = message.substring(qs + 1, qe);
            return isTrustedSource(sender);
        } catch (Exception e) {
            return false;
        }
    }

    public void connect() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            // 配置连接池参数
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);

            // 创建连接池
            if (redisPassword != null && !redisPassword.isEmpty()) {
                this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);
            } else {
                this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000);
            }

            // 验证连接
            try (Jedis jedis = jedisPool.getResource()) {
                String pong = jedis.ping();
                if (!"PONG".equals(pong)) {
                    throw new RuntimeException("Redis ping failed");
                }
            }

            // 启动订阅线程
            this.subscriberThread = new Thread(() -> {
                // E-036: 包装重连循环,异常时 sleep 后重试,避免订阅线程结束造成"假在线"
                while (!shutdownRequested && !Thread.currentThread().isInterrupted()) {
                    try (Jedis subscriberJedis = jedisPool.getResource()) {
                        JedisPubSub pubSub = new JedisPubSub() {
                            @Override
                            public void onMessage(String channel, String message) {
                                logger.fine("Received message on channel: " + channel);
                                if (messageHandler != null) {
                                    // E-034: 仅在白名单启用时校验来源;消息体若包含 senderId 字段则校验,
                                    // 否则放行(向后兼容)
                                    if (!verifySource(message)) {
                                        logger.warning("Discarding Redis message from untrusted source: " + message);
                                        return;
                                    }
                                    try {
                                        messageHandler.accept(message);
                                    } catch (Exception ex) {
                                        logger.severe("Message handler threw: " + ex.getMessage());
                                    }
                                }
                            }

                            @Override
                            public void onSubscribe(String channel, int subscribedChannels) {
                                logger.info("Subscribed to Redis channel: " + channel);
                            }

                            @Override
                            public void onUnsubscribe(String channel, int subscribedChannels) {
                                logger.info("Unsubscribed from Redis channel: " + channel);
                            }

                            @Override
                            public void onPMessage(String pattern, String channel, String message) {
                                logger.fine("Received (pattern) message on channel: " + channel);
                                if (messageHandler != null) {
                                    if (!verifySource(message)) {
                                        logger.warning("Discarding Redis message from untrusted source: " + message);
                                        return;
                                    }
                                    try {
                                        messageHandler.accept(message);
                                    } catch (Exception ex) {
                                        logger.severe("Message handler threw: " + ex.getMessage());
                                    }
                                }
                            }
                        };
                        activePubSub = pubSub;

                        // E-035: 原普通 subscribe + 字面 "*" 不会匹配 send() 发到 starcore:sync:serverA 这类
                        // 真实频道。改用 psubscribe 模式订阅 glob 通配
                        subscriberJedis.psubscribe(pubSub, channelPrefix + "*");

                        // psubscribe 通常阻塞,返回后说明订阅结束
                        activePubSub = null;
                    } catch (Exception e) {
                        if (!Thread.currentThread().isInterrupted() && !shutdownRequested) {
                            logger.warning("Redis subscription error: " + e.getMessage()
                                    + " — will retry in 5 seconds");
                        }
                    }
                    if (shutdownRequested || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "Redis-Subscriber-" + serverId);
            subscriberThread.setDaemon(true);
            subscriberThread.start();

            this.connected = true;
            logger.info("Redis transport connected: " + redisHost + ":" + redisPort);
        } catch (Exception e) {
            logger.severe("Failed to connect to Redis: " + e.getMessage());
            this.connected = false;
            cleanup();
        }
    }

    public void disconnect() {
        shutdownRequested = true;
        try {
            // 中断订阅线程
            // E-036: 显式取消 pubSub 让阻塞 socket read 立即返回
            JedisPubSub ps = activePubSub;
            if (ps != null && ps.isSubscribed()) {
                try {
                    ps.punsubscribe();
                } catch (Exception ignored) {
                    // ignore
                }
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

            // 关闭连接池
            cleanup();

            this.connected = false;
            logger.info("Redis transport disconnected");
        } catch (Exception e) {
            logger.warning("Error disconnecting from Redis: " + e.getMessage());
        }
    }

    private void cleanup() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            try {
                jedisPool.close();
            } catch (Exception e) {
                logger.warning("Error closing JedisPool: " + e.getMessage());
            }
            jedisPool = null;
        }
    }

    /** E-037: send 在断连/失败时返回 false,让调用方知道消息未送达,可自行重试/持久化备份 */
    public boolean send(String targetServer, String message) {
        if (!connected || jedisPool == null || jedisPool.isClosed()) {
            logger.warning("Redis not connected, cannot send message");
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String channel = channelPrefix + targetServer;
            jedis.publish(channel, message);
            logger.fine("Sent message to server: " + targetServer);
            return true;
        } catch (Exception e) {
            logger.warning("Failed to send Redis message: " + e.getMessage());
            return false;
        }
    }

    /** E-037: broadcast 同样返回 boolean */
    public boolean broadcast(String message) {
        if (!connected || jedisPool == null || jedisPool.isClosed()) {
            logger.warning("Redis not connected, cannot broadcast message");
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String channel = channelPrefix + "broadcast";
            jedis.publish(channel, message);
            logger.fine("Broadcast message to all servers");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to broadcast Redis message: " + e.getMessage());
            return false;
        }
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * 检查 Redis 服务器是否可用
     */
    public boolean ping() {
        if (jedisPool == null || jedisPool.isClosed()) {
            return false;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
}
