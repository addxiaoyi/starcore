package dev.starcore.starcore.crossserver.transport;

import com.rabbitmq.client.*;
import dev.starcore.starcore.crossserver.CrossServerService;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * RabbitMQ传输实现
 * 使用RabbitMQ的Topic Exchange实现跨服消息传递
 *
 * <p>依赖说明：
 * <ul>
 *   <li>RabbitMQ AMQP Client 5.21.0</li>
 *   <li>需要RabbitMQ服务端运行并配置用户权限</li>
 * </ul>
 *
 * <p>路由键规则：
 * <ul>
 *   <li>点对点消息: starcore.sync.{targetServerId}</li>
 *   <li>广播消息: starcore.sync.broadcast</li>
 *   <li>全服消息: starcore.sync.all</li>
 * </ul>
 */
public class RabbitMQTransport {
    private final String serverId;
    private final String rabbitHost;
    private final int rabbitPort;
    private final String username;
    private final String password;
    private final String virtualHost;
    private final String exchangeName;
    private final Logger logger;

    private Consumer<String> messageHandler;
    private boolean connected;
    private Connection connection;
    private Channel channel;
    private String queueName;

    public RabbitMQTransport(String serverId, String rabbitHost, int rabbitPort,
                             String username, String password, Logger logger) {
        this(serverId, rabbitHost, rabbitPort, username, password, "/", logger);
    }

    public RabbitMQTransport(String serverId, String rabbitHost, int rabbitPort,
                             String username, String password, String virtualHost, Logger logger) {
        this.serverId = serverId;
        this.rabbitHost = rabbitHost;
        this.rabbitPort = rabbitPort;
        this.username = username;
        this.password = password;
        this.virtualHost = virtualHost;
        this.exchangeName = "starcore.sync";
        this.logger = logger;
        this.connected = false;
    }

    public void connect() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rabbitHost);
            factory.setPort(rabbitPort);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setVirtualHost(virtualHost);

            // 连接参数配置
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(10000);
            factory.setConnectionTimeout(10000);
            // 心跳间隔使用默认值

            // 创建连接和通道
            this.connection = factory.newConnection("StarCore-" + serverId);
            this.channel = connection.createChannel();

            // 声明Topic Exchange（支持路由键匹配）
            channel.exchangeDeclare(exchangeName, BuiltinExchangeType.TOPIC, true);

            // 为当前服务器创建专属队列
            this.queueName = "starcore.sync." + serverId;
            channel.queueDeclare(queueName, false, true, true, null);

            // 绑定队列到Exchange，支持点对点和广播
            // 绑定自己的服务器ID，接收发送给本服务器的消息
            channel.queueBind(queueName, exchangeName, serverId);
            // 绑定broadcast，接收广播消息
            channel.queueBind(queueName, exchangeName, "broadcast");
            // 绑定all，接收所有消息（用于需要接收所有消息的场景）
            channel.queueBind(queueName, exchangeName, "all");

            // 设置QoS，确保消息顺序
            channel.basicQos(1);

            // 启动消费者
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    String routingKey = delivery.getEnvelope().getRoutingKey();
                    logger.fine("Received message on routing key: " + routingKey);
                    if (messageHandler != null) {
                        messageHandler.accept(message);
                    }
                    // 手动确认消息
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    logger.warning("Error processing RabbitMQ message: " + e.getMessage());
                    // 否定确认，消息会被重新入队
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                }
            };

            // 取消回调
            CancelCallback cancelCallback = (consumerTag) -> {
                logger.warning("Consumer cancelled: " + consumerTag);
            };

            // 开始消费
            channel.basicConsume(queueName, false, deliverCallback, cancelCallback);

            this.connected = true;
            logger.info("RabbitMQ transport connected: " + rabbitHost + ":" + rabbitPort);
        } catch (TimeoutException e) {
            logger.severe("RabbitMQ connection timeout: " + e.getMessage());
            this.connected = false;
        } catch (Exception e) {
            logger.severe("Failed to connect to RabbitMQ: " + e.getMessage());
            this.connected = false;
            cleanup();
        }
    }

    public void disconnect() {
        try {
            // 关闭通道
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (TimeoutException e) {
                    logger.warning("Timeout closing RabbitMQ channel: " + e.getMessage());
                }
            }

            // 关闭连接
            cleanup();

            this.connected = false;
            logger.info("RabbitMQ transport disconnected");
        } catch (Exception e) {
            logger.warning("Error disconnecting from RabbitMQ: " + e.getMessage());
        }
    }

    private void cleanup() {
        try {
            if (channel != null) {
                channel = null;
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            logger.warning("Error during RabbitMQ cleanup: " + e.getMessage());
        } finally {
            connection = null;
        }
    }

    public void send(String targetServer, String message) {
        if (!connected) {
            logger.warning("RabbitMQ not connected, cannot send message");
            return;
        }

        try {
            channel.basicPublish(
                exchangeName,
                targetServer,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes(StandardCharsets.UTF_8)
            );
            logger.fine("Sent message to server: " + targetServer);
        } catch (Exception e) {
            logger.warning("Failed to send RabbitMQ message: " + e.getMessage());
        }
    }

    public void broadcast(String message) {
        if (!connected) {
            logger.warning("RabbitMQ not connected, cannot broadcast message");
            return;
        }

        try {
            channel.basicPublish(
                exchangeName,
                "broadcast",
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes(StandardCharsets.UTF_8)
            );
            logger.fine("Broadcast message to all servers");
        } catch (Exception e) {
            logger.warning("Failed to broadcast RabbitMQ message: " + e.getMessage());
        }
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public boolean isConnected() {
        return connected && connection != null && connection.isOpen() && channel != null && channel.isOpen();
    }

    /**
     * 发送消息到所有服务器（通过 all 路由键）
     */
    public void sendToAll(String message) {
        if (!connected) {
            logger.warning("RabbitMQ not connected, cannot send message");
            return;
        }

        try {
            channel.basicPublish(
                exchangeName,
                "all",
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes(StandardCharsets.UTF_8)
            );
            logger.fine("Sent message to all servers");
        } catch (Exception e) {
            logger.warning("Failed to send RabbitMQ message to all: " + e.getMessage());
        }
    }

    /**
     * 获取当前队列名称
     */
    public String getQueueName() {
        return queueName;
    }

    /**
     * 检查 RabbitMQ 服务器是否可用
     */
    public boolean isServerAvailable() {
        return isConnected();
    }
}
