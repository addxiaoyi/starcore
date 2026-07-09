package dev.starcore.starcore.module.army.fatigue.storage;

import dev.starcore.starcore.module.army.fatigue.model.PlayerFatigue;
import dev.starcore.starcore.module.army.fatigue.model.FatigueLevel;
import dev.starcore.starcore.module.army.fatigue.model.FatigueType;

import java.util.UUID;

/**
 * 疲劳度状态编解码器
 * 用于将 PlayerFatigue 对象编码为字符串以便持久化
 */
public final class FatigueStateCodec {

    /**
     * 将 PlayerFatigue 编码为字符串
     */
    public String encode(PlayerFatigue fatigue) {
        return String.format("%s|%d|%d|%d|%d|%d|%d|%d",
            fatigue.playerId().toString(),
            fatigue.physicalFatigue(),
            fatigue.mentalFatigue(),
            fatigue.combatFatigue(),
            fatigue.travelFatigue(),
            fatigue.lastActivityTime(),
            fatigue.totalPlayTime(),
            fatigue.lastRestTime()
        );
    }

    /**
     * 从字符串解码为 PlayerFatigue
     */
    public PlayerFatigue decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            String[] parts = data.split("\\|");
            if (parts.length < 8) {
                return null;
            }

            UUID playerId = UUID.fromString(parts[0]);
            int physical = Integer.parseInt(parts[1]);
            int mental = Integer.parseInt(parts[2]);
            int combat = Integer.parseInt(parts[3]);
            int travel = Integer.parseInt(parts[4]);
            long lastActivity = Long.parseLong(parts[5]);
            long totalPlayTime = Long.parseLong(parts[6]);
            long lastRestTime = Long.parseLong(parts[7]);

            return new PlayerFatigue(
                playerId,
                physical,
                mental,
                combat,
                travel,
                lastActivity,
                totalPlayTime,
                lastRestTime
            );
        } catch (Exception e) {
            return null;
        }
    }
}