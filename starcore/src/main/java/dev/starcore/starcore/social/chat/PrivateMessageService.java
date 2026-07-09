package dev.starcore.starcore.social.chat;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 私聊服务（星际通讯）
 */
public final class PrivateMessageService {
    // 消息历史（玩家UUID -> 消息列表）
    private final Map<UUID, Deque<ChatMessage>> messageHistory = new ConcurrentHashMap<>();

    // 最近聊天对象（玩家UUID -> 对方UUID）
    private final Map<UUID, UUID> lastChatPartner = new ConcurrentHashMap<>();

    // 静音列表（玩家UUID -> 静音的玩家UUID列表）
    private final Map<UUID, Set<UUID>> mutedPlayers = new ConcurrentHashMap<>();

    // 最大消息历史数量
    private final int maxHistorySize = 100;

    // 持久化相关
    private Plugin plugin;
    private File messagesFile;
    private File partnersFile;
    private File mutedFile;

    // 异步保存标记
    private final AtomicBoolean savePending = new AtomicBoolean(false);
    private volatile boolean initialized = false;

    /**
     * 初始化持久化
     */
    public void initialize(Plugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "social");
        if (!dir.exists()) dir.mkdirs();
        this.messagesFile = new File(dir, "private-messages.yml");
        this.partnersFile = new File(dir, "chat-partners.yml");
        this.mutedFile = new File(dir, "muted-players.yml");
        this.initialized = true;
        loadData();
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 加载所有持久化数据
     */
    public void loadData() {
        loadMessages();
        loadPartners();
        loadMuted();
        if (plugin != null) {
            plugin.getLogger().info("已加载私聊数据");
        }
    }

    /**
     * 保存所有持久化数据（同步）
     */
    public void saveData() {
        saveMessages();
        savePartners();
        saveMuted();
    }

    /**
     * 异步保存所有持久化数据
     */
    /**
     * 异步保存所有持久化数据。
     * D-039: 使用 dirty 标记 + 串行调度，避免 CAS 在持续高并发私聊下吞掉保存请求。
     */
    public void saveDataAsync() {
        if (!initialized || plugin == null) return;
        saveDirty.set(true);
        if (!savePending.compareAndSet(false, true)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 循环消除 dirty：保存期间如有新写入，再次保存。
                while (saveDirty.get()) {
                    saveDirty.set(false);
                    try {
                        saveMessages();
                        savePartners();
                        saveMuted();
                    } catch (Exception e) {
                        if (plugin != null) {
                            plugin.getLogger().warning("异步保存私聊数据失败: " + e.getMessage());
                        }
                        break;
                    }
                }
            } finally {
                savePending.set(false);
            }
        });
    }

    private final java.util.concurrent.atomic.AtomicBoolean saveDirty = new java.util.concurrent.atomic.AtomicBoolean(false);

    // ========== 消息历史持久化 ==========

    private void loadMessages() {
        if (messagesFile == null || !messagesFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(messagesFile);
            for (String key : yml.getKeys(false)) {
                UUID playerId;
                try {
                    playerId = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                // D-035/D-041: yml.getList(key) 老路径以 List<MessageData> 形式存（不会真正序列化），
                // 已改写为 List<Map<String,Object>>。兼容旧格式失败时静默跳过。
                List<?> messagesList = yml.getList(key);
                if (messagesList == null) continue;

                Deque<ChatMessage> history = new ConcurrentLinkedDeque<>();
                for (Object obj : messagesList) {
                    ChatMessage msg = deserializeMessage(obj);
                    if (msg != null) {
                        history.addLast(msg);
                    }
                }
                if (!history.isEmpty()) {
                    messageHistory.put(playerId, history);
                }
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("加载私聊消息失败: " + ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ChatMessage deserializeMessage(Object obj) {
        if (obj instanceof MessageData data) return data.toMessage();
        if (obj instanceof java.util.Map<?, ?> map) {
            try {
                Object idObj = map.get("id");
                Object senderObj = map.get("senderId");
                Object targetObj = map.get("targetId");
                Object msgObj = map.get("message");
                Object tsObj = map.get("timestamp");
                if (idObj == null || senderObj == null || targetObj == null || msgObj == null) return null;
                long ts = tsObj instanceof Number ? ((Number) tsObj).longValue() : 0L;
                return new ChatMessage(
                    UUID.fromString(String.valueOf(idObj)),
                    UUID.fromString(String.valueOf(senderObj)),
                    UUID.fromString(String.valueOf(targetObj)),
                    String.valueOf(msgObj),
                    ts
                );
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }

    private void saveMessages() {
        if (messagesFile == null || !initialized) return;
        try {
            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, Deque<ChatMessage>> entry : messageHistory.entrySet()) {
                // D-035: 以 List<Map<String,Object>> 形式序列化，避免 MessageData 不可被 Bukkit yml 序列化。
                List<Map<String, Object>> serialized = new ArrayList<>();
                for (ChatMessage msg : entry.getValue()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", msg.id().toString());
                    m.put("senderId", msg.senderId().toString());
                    m.put("targetId", msg.targetId().toString());
                    m.put("message", msg.message());
                    m.put("timestamp", msg.timestamp());
                    serialized.add(m);
                }
                yml.set(entry.getKey().toString(), serialized);
            }
            yml.save(messagesFile);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("保存私聊消息失败: " + ex.getMessage());
            }
        }
    }

    // ========== 最近聊天对象持久化 ==========

    private void loadPartners() {
        if (partnersFile == null || !partnersFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(partnersFile);
            for (String key : yml.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    String partnerStr = yml.getString(key);
                    if (partnerStr != null) {
                        lastChatPartner.put(playerId, UUID.fromString(partnerStr));
                    }
                } catch (IllegalArgumentException ex) {
                    continue;
                }
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("加载最近聊天对象失败: " + ex.getMessage());
            }
        }
    }

    private void savePartners() {
        if (partnersFile == null || !initialized) return;
        try {
            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, UUID> entry : lastChatPartner.entrySet()) {
                yml.set(entry.getKey().toString(), entry.getValue().toString());
            }
            yml.save(partnersFile);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("保存最近聊天对象失败: " + ex.getMessage());
            }
        }
    }

    // ========== 静音列表持久化 ==========

    private void loadMuted() {
        if (mutedFile == null || !mutedFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(mutedFile);
            for (String key : yml.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(key);
                    List<String> mutedList = yml.getStringList(key);
                    Set<UUID> muted = ConcurrentHashMap.newKeySet();
                    for (String mutedStr : mutedList) {
                        try {
                            muted.add(UUID.fromString(mutedStr));
                        } catch (IllegalArgumentException e) {
                            // 跳过无效的UUID
                        }
                    }
                    if (!muted.isEmpty()) {
                        mutedPlayers.put(playerId, muted);
                    }
                } catch (IllegalArgumentException ex) {
                    continue;
                }
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("加载静音列表失败: " + ex.getMessage());
            }
        }
    }

    private void saveMuted() {
        if (mutedFile == null || !initialized) return;
        try {
            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, Set<UUID>> entry : mutedPlayers.entrySet()) {
                List<String> mutedList = new ArrayList<>();
                for (UUID mutedId : entry.getValue()) {
                    mutedList.add(mutedId.toString());
                }
                yml.set(entry.getKey().toString(), mutedList);
            }
            yml.save(mutedFile);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("保存静音列表失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 发送私聊消息
     */
    public boolean sendMessage(UUID senderId, UUID targetId, String message) {
        // 检查双向静音：对方屏蔽我 / 我屏蔽对方都禁止发送
        if (isMuted(targetId, senderId)) {
            throw new IllegalStateException("你已被对方屏蔽");
        }
        if (isMuted(senderId, targetId)) {
            throw new IllegalStateException("你已屏蔽对方，无法发送消息");
        }

        // 创建消息
        ChatMessage chatMessage = new ChatMessage(
            UUID.randomUUID(),
            senderId,
            targetId,
            message,
            System.currentTimeMillis()
        );

        // 保存到双方的消息历史
        addToHistory(senderId, chatMessage);
        addToHistory(targetId, chatMessage);

        // 更新最近聊天对象
        lastChatPartner.put(senderId, targetId);
        lastChatPartner.put(targetId, senderId);

        // 异步保存
        saveDataAsync();

        return true;
    }

    /**
     * 快速回复（发送给最近聊天的人）
     */
    public boolean quickReply(UUID senderId, String message) {
        UUID targetId = lastChatPartner.get(senderId);
        if (targetId == null) {
            throw new IllegalStateException("没有最近的聊天对象");
        }
        // D-037: 提前校验静音关系，给玩家清晰反馈
        if (isMuted(targetId, senderId)) {
            throw new IllegalStateException("对方已屏蔽你，无法发送");
        }
        return sendMessage(senderId, targetId, message);
    }

    /**
     * 获取消息历史
     */
    public List<ChatMessage> getMessageHistory(UUID playerId, int limit) {
        Deque<ChatMessage> history = messageHistory.get(playerId);
        if (history == null) {
            return Collections.emptyList();
        }

        List<ChatMessage> messages = new ArrayList<>(history);
        Collections.reverse(messages); // 最新的在前

        if (limit > 0 && messages.size() > limit) {
            messages = messages.subList(0, limit);
        }

        return messages;
    }

    /**
     * 获取与特定玩家的聊天历史
     */
    public List<ChatMessage> getConversation(UUID playerId, UUID otherId, int limit) {
        Deque<ChatMessage> history = messageHistory.get(playerId);
        if (history == null) {
            return Collections.emptyList();
        }

        List<ChatMessage> conversation = history.stream()
            .filter(msg ->
                (msg.senderId().equals(playerId) && msg.targetId().equals(otherId)) ||
                (msg.senderId().equals(otherId) && msg.targetId().equals(playerId))
            )
            .toList();

        List<ChatMessage> result = new ArrayList<>(conversation);
        Collections.reverse(result);

        if (limit > 0 && result.size() > limit) {
            result = result.subList(0, limit);
        }

        return result;
    }

    /**
     * 添加到消息历史
     */
    private void addToHistory(UUID playerId, ChatMessage message) {
        Deque<ChatMessage> history = messageHistory.computeIfAbsent(
            playerId,
            k -> new ConcurrentLinkedDeque<>()
        );

        // D-040: synchronized 限制截断操作的竞态；防止 ConcurrentLinkedDeque.size() 大并发误差导致的过度删除
        synchronized (history) {
            history.addFirst(message);
            while (history.size() > maxHistorySize) {
                history.removeLast();
            }
        }
    }

    /**
     * 静音玩家
     */
    public boolean mutePlayer(UUID playerId, UUID targetId) {
        mutedPlayers.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
            .add(targetId);
        saveDataAsync();
        return true;
    }

    /**
     * 取消静音
     */
    public boolean unmutePlayer(UUID playerId, UUID targetId) {
        Set<UUID> muted = mutedPlayers.get(playerId);
        if (muted != null) {
            boolean removed = muted.remove(targetId);
            if (removed) {
                saveDataAsync();
            }
            return removed;
        }
        return false;
    }

    /**
     * 检查是否静音
     */
    public boolean isMuted(UUID playerId, UUID targetId) {
        Set<UUID> muted = mutedPlayers.get(playerId);
        return muted != null && muted.contains(targetId);
    }

    /**
     * 获取静音列表
     */
    public Set<UUID> getMutedPlayers(UUID playerId) {
        Set<UUID> muted = mutedPlayers.get(playerId);
        return muted != null ? new HashSet<>(muted) : Collections.emptySet();
    }

    /**
     * 获取最近聊天对象
     */
    public UUID getLastChatPartner(UUID playerId) {
        return lastChatPartner.get(playerId);
    }

    /**
     * 清除消息历史
     */
    public boolean clearHistory(UUID playerId) {
        messageHistory.remove(playerId);
        return true;
    }

    /**
     * 清除与特定玩家的聊天历史
     */
    public boolean clearConversation(UUID playerId, UUID otherId) {
        Deque<ChatMessage> history = messageHistory.get(playerId);
        if (history == null) return false;

        history.removeIf(msg ->
            (msg.senderId().equals(playerId) && msg.targetId().equals(otherId)) ||
            (msg.senderId().equals(otherId) && msg.targetId().equals(playerId))
        );

        return true;
    }

    /**
     * 清理玩家数据
     */
    public void cleanup(UUID playerId) {
        messageHistory.remove(playerId);
        lastChatPartner.remove(playerId);
        mutedPlayers.remove(playerId);
        // D-038: 反向清理对方 map 中关于该玩家的残留引用
        for (Deque<ChatMessage> history : messageHistory.values()) {
            history.removeIf(msg -> msg.senderId().equals(playerId) || msg.targetId().equals(playerId));
        }
        lastChatPartner.values().removeIf(id -> id != null && id.equals(playerId));
        for (Set<UUID> muted : mutedPlayers.values()) {
            muted.remove(playerId);
        }
        saveDataAsync();
    }

    /**
     * 聊天消息记录
     */
    public record ChatMessage(
        UUID id,
        UUID senderId,
        UUID targetId,
        String message,
        long timestamp
    ) {}

    /**
     * 消息数据序列化类
     */
    public static class MessageData {
        private String id;
        private String senderId;
        private String targetId;
        private String message;
        private long timestamp;

        public MessageData() {}

        public static MessageData fromMessage(ChatMessage msg) {
            MessageData data = new MessageData();
            data.id = msg.id().toString();
            data.senderId = msg.senderId().toString();
            data.targetId = msg.targetId().toString();
            data.message = msg.message();
            data.timestamp = msg.timestamp();
            return data;
        }

        public ChatMessage toMessage() {
            try {
                if (id == null || senderId == null || targetId == null || message == null) {
                    return null;
                }
                return new ChatMessage(
                    UUID.fromString(id),
                    UUID.fromString(senderId),
                    UUID.fromString(targetId),
                    message,
                    timestamp
                );
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}
