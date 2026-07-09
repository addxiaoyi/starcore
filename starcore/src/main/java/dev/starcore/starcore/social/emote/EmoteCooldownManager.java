package dev.starcore.starcore.social.emote;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作冷却管理器
 */
public class EmoteCooldownManager {
    // D-042: 统一冷却来源 —— EmoteCooldownManager 现作为 EmoteState 的薄壳代理，
    // 不再维护独立的 lastUsed 表，避免与 EmoteService.isOnCooldown 产生双套不一致冷却。
    private EmoteService emoteService;
    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Integer> defaultCooldowns = new ConcurrentHashMap<>();

    public EmoteCooldownManager() {
        // 设置默认冷却时间（秒）
        setDefaultCooldown("wave", 3);
        setDefaultCooldown("bow", 5);
        setDefaultCooldown("salute", 3);
        setDefaultCooldown("laugh", 5);
        setDefaultCooldown("cry", 10);
        setDefaultCooldown("angry", 8);
        setDefaultCooldown("clap", 3);
        setDefaultCooldown("hug", 10);
        setDefaultCooldown("kiss", 15);
        setDefaultCooldown("handshake", 3);
        setDefaultCooldown("dance", 15);
        setDefaultCooldown("sit", 20);
        setDefaultCooldown("sword", 5);
        setDefaultCooldown("shield", 5);
        setDefaultCooldown("attack", 3);
        setDefaultCooldown("point", 2);
        setDefaultCooldown("thumbsup", 3);
        setDefaultCooldown("facepalm", 5);
        setDefaultCooldown("sleep", 30);
        setDefaultCooldown("eat", 5);
        setDefaultCooldown("drink", 5);
        setDefaultCooldown("spin", 10);
    }

    /**
     * 设置动作的默认冷却时间
     */
    public void setDefaultCooldown(String emoteId, int seconds) {
        defaultCooldowns.put(emoteId.toLowerCase(), seconds);
    }

    /**
     * 获取动作的默认冷却时间
     */
    public int getDefaultCooldown(String emoteId) {
        return defaultCooldowns.getOrDefault(emoteId.toLowerCase(), 5);
    }

    /**
     * D-042: 注入 EmoteService，使冷却查询与 EmoteState 保持单一来源。
     */
    public void setEmoteService(EmoteService emoteService) {
        this.emoteService = emoteService;
    }

    /**
     * 开始冷却
     */
    public void startCooldown(UUID playerId, String emoteId) {
        // D-042: 通过 EmoteState.setCurrentEmote 统一记录冷却时间
        if (emoteService != null) {
            EmoteState state = emoteService.getOrCreateState(playerId);
            state.setCurrentEmote(emoteId.toLowerCase(), System.currentTimeMillis(), null);
            return;
        }
        playerCooldowns
            .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
            .put(emoteId.toLowerCase(), System.currentTimeMillis());
    }

    /**
     * 检查是否在冷却中
     */
    public boolean isOnCooldown(UUID playerId, String emoteId) {
        // D-042: 优先查询 EmoteState
        if (emoteService != null) {
            EmoteState state = emoteService.getOrCreateState(playerId);
            return state.isOnCooldown(emoteId.toLowerCase(), getDefaultCooldown(emoteId));
        }
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return false;

        Long lastUsed = cooldowns.get(emoteId.toLowerCase());
        if (lastUsed == null) return false;

        int cooldownTime = getDefaultCooldown(emoteId);
        long elapsed = System.currentTimeMillis() - lastUsed;

        // 如果冷却已过期，清理记录
        if (elapsed >= cooldownTime * 1000L) {
            cooldowns.remove(emoteId.toLowerCase());
            return false;
        }

        return true;
    }

    /**
     * 获取剩余冷却时间（秒）
     */
    public int getRemainingCooldown(UUID playerId, String emoteId) {
        if (emoteService != null) {
            EmoteState state = emoteService.getOrCreateState(playerId);
            return state.getRemainingCooldown(getDefaultCooldown(emoteId));
        }
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return 0;

        Long lastUsed = cooldowns.get(emoteId.toLowerCase());
        if (lastUsed == null) return 0;

        int cooldownTime = getDefaultCooldown(emoteId);
        long elapsed = System.currentTimeMillis() - lastUsed;
        long remaining = (cooldownTime * 1000L) - elapsed;

        return (int) Math.max(0, remaining / 1000);
    }

    /**
     * 获取玩家所有冷却中的动作
     */
    public Map<String, Integer> getPlayerCooldowns(UUID playerId) {
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return Collections.emptyMap();

        Map<String, Integer> result = new HashMap<>();
        List<String> expired = new ArrayList<>();

        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            int remaining = getRemainingCooldown(playerId, entry.getKey());
            if (remaining > 0) {
                result.put(entry.getKey(), remaining);
            } else {
                expired.add(entry.getKey());
            }
        }

        // 清理已过期的记录
        expired.forEach(cooldowns::remove);

        return result;
    }

    /**
     * 清除玩家的所有冷却
     */
    public void clearPlayerCooldowns(UUID playerId) {
        playerCooldowns.remove(playerId);
    }

    /**
     * 清除玩家的单个动作冷却
     */
    public void clearCooldown(UUID playerId, String emoteId) {
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns != null) {
            cooldowns.remove(emoteId.toLowerCase());
        }
    }

    /**
     * 清理所有过期冷却
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();

        for (Map<String, Long> cooldowns : playerCooldowns.values()) {
            List<String> expired = new ArrayList<>();

            for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
                int cooldownTime = getDefaultCooldown(entry.getKey());
                if (now - entry.getValue() >= cooldownTime * 1000L) {
                    expired.add(entry.getKey());
                }
            }

            expired.forEach(cooldowns::remove);
        }
    }

    /**
     * 获取冷却中的玩家数量
     */
    public int getActiveCooldownCount() {
        return playerCooldowns.size();
    }

    /**
     * 格式化冷却时间为可读字符串
     */
    public String formatCooldownTime(int seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            if (remainingSeconds == 0) {
                return minutes + "分钟";
            }
            return minutes + "分" + remainingSeconds + "秒";
        }
    }

    /**
     * 序列化冷却数据
     */
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();

        List<Map<String, Object>> cooldownList = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Long>> playerEntry : playerCooldowns.entrySet()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("playerId", playerEntry.getKey().toString());

            List<String> cooldownEntries = new ArrayList<>();
            for (Map.Entry<String, Long> cooldownEntry : playerEntry.getValue().entrySet()) {
                cooldownEntries.add(cooldownEntry.getKey() + ":" + cooldownEntry.getValue());
            }
            playerData.put("cooldowns", cooldownEntries);

            cooldownList.add(playerData);
        }

        data.put("playerCooldowns", cooldownList);
        return data;
    }

    /**
     * 反序列化冷却数据
     */
    @SuppressWarnings("unchecked")
    public void deserialize(Map<String, Object> data) {
        List<Map<String, Object>> cooldownList =
            (List<Map<String, Object>>) data.get("playerCooldowns");
        if (cooldownList == null) return;

        for (Map<String, Object> playerData : cooldownList) {
            try {
                UUID playerId = UUID.fromString((String) playerData.get("playerId"));
                List<String> cooldownEntries = (List<String>) playerData.get("cooldowns");

                Map<String, Long> cooldowns = new ConcurrentHashMap<>();
                for (String entry : cooldownEntries) {
                    // D-047: 容忍单行损坏；用 lastIndexOf(":") 避免 emoteId 含 ":" 拆错位
                    int idx = entry.lastIndexOf(':');
                    if (idx <= 0 || idx >= entry.length() - 1) continue;
                    String key = entry.substring(0, idx).toLowerCase();
                    try {
                        cooldowns.put(key, Long.parseLong(entry.substring(idx + 1)));
                    } catch (NumberFormatException nfex) {
                        // 跳过损坏条目，避免整批数据被吞
                    }
                }

                playerCooldowns.put(playerId, cooldowns);
            } catch (Exception ex) {
                // 跳过损坏的玩家条目
            }
        }
    }
}
