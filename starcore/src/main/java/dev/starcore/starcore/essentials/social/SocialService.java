package dev.starcore.starcore.essentials.social;
import java.util.Optional;

import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 社交系统服务
 * 管理私聊、屏蔽、好友等社交功能
 */
public final class SocialService {
    // 最近私聊对象 UUID -> UUID
    private final ConcurrentHashMap<UUID, UUID> lastMessaged = new ConcurrentHashMap<>();

    // 屏蔽列表 UUID -> Set<UUID>
    private final ConcurrentHashMap<UUID, Set<UUID>> ignoredPlayers = new ConcurrentHashMap<>();

    // 消息历史（最近10条）UUID -> List<Message>
    private final ConcurrentHashMap<UUID, LinkedList<Message>> messageHistory = new ConcurrentHashMap<>();

    private static final int MAX_HISTORY = 10;

    /**
     * 发送私聊消息
     */
    public boolean sendMessage(Player sender, Player recipient, String message) {
        // 检查是否被屏蔽
        if (isIgnored(recipient.getUniqueId(), sender.getUniqueId())) {
            return false;
        }

        // 记录最近私聊
        lastMessaged.put(sender.getUniqueId(), recipient.getUniqueId());
        lastMessaged.put(recipient.getUniqueId(), sender.getUniqueId());

        // 保存消息历史
        Message msg = new Message(
            sender.getUniqueId(),
            recipient.getUniqueId(),
            message,
            Instant.now()
        );

        addToHistory(sender.getUniqueId(), msg);
        addToHistory(recipient.getUniqueId(), msg);

        // 发送消息
        sender.sendMessage("§7[§e我§7 -> §e" + recipient.getName() + "§7] §f" + message);
        recipient.sendMessage("§7[§e" + sender.getName() + "§7 -> §e我§7] §f" + message);

        return true;
    }

    /**
     * 回复最近的私聊
     */
    public Optional<UUID> getLastMessaged(UUID playerId) {
        return Optional.ofNullable(lastMessaged.get(playerId));
    }

    /**
     * 屏蔽玩家
     */
    public boolean ignorePlayer(UUID playerId, UUID targetId) {
        if (playerId.equals(targetId)) {
            return false;
        }

        Set<UUID> ignored = ignoredPlayers.computeIfAbsent(
            playerId,
            k -> ConcurrentHashMap.newKeySet()
        );

        return ignored.add(targetId);
    }

    /**
     * 取消屏蔽
     */
    public boolean unignorePlayer(UUID playerId, UUID targetId) {
        Set<UUID> ignored = ignoredPlayers.get(playerId);

        if (ignored == null) {
            return false;
        }

        return ignored.remove(targetId);
    }

    /**
     * 检查是否被屏蔽
     */
    public boolean isIgnored(UUID playerId, UUID potentialIgnorer) {
        Set<UUID> ignored = ignoredPlayers.get(playerId);

        if (ignored == null) {
            return false;
        }

        return ignored.contains(potentialIgnorer);
    }

    /**
     * 获取屏蔽列表
     */
    public Set<UUID> getIgnoredPlayers(UUID playerId) {
        Set<UUID> ignored = ignoredPlayers.get(playerId);

        if (ignored == null) {
            return Set.of();
        }

        return new HashSet<>(ignored);
    }

    /**
     * 获取消息历史
     */
    public List<Message> getMessageHistory(UUID playerId) {
        LinkedList<Message> history = messageHistory.get(playerId);

        if (history == null) {
            return List.of();
        }

        return new ArrayList<>(history);
    }

    /**
     * 添加到历史记录
     */
    private void addToHistory(UUID playerId, Message message) {
        LinkedList<Message> history = messageHistory.computeIfAbsent(
            playerId,
            k -> new LinkedList<>()
        );

        history.addFirst(message);

        // 限制历史记录数量
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    /**
     * 清理玩家数据
     */
    public void cleanup(UUID playerId) {
        lastMessaged.remove(playerId);
        ignoredPlayers.remove(playerId);
        messageHistory.remove(playerId);
    }

    /**
     * 加载玩家数据
     */
    public void loadPlayerData(UUID playerId, Set<UUID> ignored) {
        if (ignored != null && !ignored.isEmpty()) {
            ignoredPlayers.put(playerId, ConcurrentHashMap.newKeySet());
            ignoredPlayers.get(playerId).addAll(ignored);
        }
    }

    /**
     * 保存玩家数据
     */
    public Set<UUID> getPlayerData(UUID playerId) {
        return getIgnoredPlayers(playerId);
    }

    /**
     * 消息记录
     */
    public record Message(
        UUID senderId,
        UUID recipientId,
        String content,
        Instant timestamp
    ) {
        public String format() {
            return String.format("[%s] %s -> %s: %s",
                timestamp,
                senderId,
                recipientId,
                content
            );
        }
    }
}
