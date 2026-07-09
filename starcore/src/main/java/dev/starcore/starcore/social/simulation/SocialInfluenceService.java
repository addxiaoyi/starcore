package dev.starcore.starcore.social.simulation;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * 社会影响力服务
 *
 * 管理玩家在社会中的影响力:
 * - 影响力分数计算
 * - 影响力传播
 * - 影响力衰减
 * - 社会地位
 */
public interface SocialInfluenceService {

    /**
     * 获取玩家影响力
     */
    int getInfluence(UUID playerId);

    /**
     * 获取玩家的社会地位
     */
    SocialStatus getStatus(UUID playerId);

    /**
     * 增加影响力
     */
    void addInfluence(UUID playerId, int amount, String source);

    /**
     * 广播消息到影响力范围
     */
    void broadcastToInfluenceSphere(UUID source, String message, int maxReach);

    /**
     * 获取影响力范围 (能收到消息的玩家)
     */
    Set<UUID> getInfluenceSphere(UUID playerId, int levels);

    /**
     * 启动影响力衰减
     */
    void startDecayTask();

    /**
     * 停止衰减
     */
    void stopDecayTask();

    enum SocialStatus {
        NOBODY("无名小卒", 0, "§7"),
        REGIONAL("地区人物", 100, "§a"),
        KNOWN("知名人士", 500, "§b"),
        INFLUENTIAL("有影响力", 2000, "§d"),
        POWER_BROKER("权力掮客", 5000, "§6"),
        KINGMAKER("造王者", 10000, "§c"),
        LEGENDARY("传奇人物", 50000, "§4");

        private final String name;
        private final int threshold;
        private final String colorCode;

        SocialStatus(String name, int threshold, String colorCode) {
            this.name = name;
            this.threshold = threshold;
            this.colorCode = colorCode;
        }

        public String getName() { return name; }
        public int getThreshold() { return threshold; }
        public String getColor() { return colorCode; }
        public String color() { return colorCode; }  // 兼容其他代码

        public static SocialStatus fromInfluence(int influence) {
            SocialStatus[] values = values();
            for (int i = values.length - 1; i >= 0; i--) {
                if (influence >= values[i].threshold) {
                    return values[i];
                }
            }
            return NOBODY;
        }
    }
}
