package dev.starcore.starcore.api.v1.websocket;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket 消息基类
 */
public sealed interface WebSocketMessage
    permits WebSocketMessage.ClientMessage, WebSocketMessage.ServerMessage {

    /**
     * 客户端发送的消息
     */
    record ClientMessage(
        @SerializedName("type")
        String type,

        @SerializedName("id")
        String id,

        @SerializedName("data")
        Object data
    ) implements WebSocketMessage {
        public static ClientMessage ping() {
            return new ClientMessage("ping", UUID.randomUUID().toString(), null);
        }

        public static ClientMessage subscribe(String channel) {
            return new ClientMessage("subscribe", UUID.randomUUID().toString(), new SubscriptionData(channel));
        }

        public static ClientMessage unsubscribe(String channel) {
            return new ClientMessage("unsubscribe", UUID.randomUUID().toString(), new SubscriptionData(channel));
        }
    }

    /**
     * 服务器发送的消息
     */
    sealed interface ServerMessage extends WebSocketMessage
        permits ServerMessage.Response, ServerMessage.Event, ServerMessage.Error, ServerMessage.Pong {}

    /**
     * 响应消息（回复客户端请求）
     */
    record Response(
        @SerializedName("type")
        String type,

        @SerializedName("id")
        String id,

        @SerializedName("success")
        boolean success,

        @SerializedName("data")
        Object data,

        @SerializedName("error")
        String error
    ) implements ServerMessage {
        public static Response ok(String id, Object data) {
            return new Response("response", id, true, data, null);
        }

        public static Response error(String id, String error) {
            return new Response("response", id, false, null, error);
        }
    }

    /**
     * 事件消息（服务器主动推送）
     */
    record Event(
        @SerializedName("type")
        String type,

        @SerializedName("channel")
        String channel,

        @SerializedName("data")
        Object data,

        @SerializedName("timestamp")
        long timestamp
    ) implements ServerMessage {
        public static Event of(String channel, String eventType, Object data) {
            return new Event("event", channel, data, Instant.now().toEpochMilli());
        }
    }

    /**
     * 错误消息
     */
    record Error(
        @SerializedName("type")
        String type,

        @SerializedName("code")
        String code,

        @SerializedName("message")
        String message,

        @SerializedName("timestamp")
        long timestamp
    ) implements ServerMessage {
        public static Error of(String code, String message) {
            return new Error("error", code, message, Instant.now().toEpochMilli());
        }
    }

    /**
     * Pong 消息（回复心跳）
     */
    record Pong(
        @SerializedName("type")
        String type,

        @SerializedName("timestamp")
        long timestamp
    ) implements ServerMessage {
        public static Pong create() {
            return new Pong("pong", Instant.now().toEpochMilli());
        }
    }

    /**
     * 订阅数据
     */
    record SubscriptionData(
        @SerializedName("channel")
        String channel
    ) {}

    /**
     * 常用频道
     */
    final class Channels {
        public static final String NATIONS = "nations";
        public static final String TERRITORIES = "territories";
        public static final String WARS = "wars";
        public static final String FINANCE = "finance";
        public static final String CHAT = "chat";
        public static final String EVENTS = "events";

        private Channels() {}
    }
}
