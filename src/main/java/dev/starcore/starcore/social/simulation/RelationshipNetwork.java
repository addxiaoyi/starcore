package dev.starcore.starcore.social.simulation;
import java.util.Optional;

import java.util.*;

/**
 * 关系网络接口
 *
 * 管理玩家之间的复杂社会关系:
 * - 关系类型 (朋友/敌人/恋人/竞争对手等)
 * - 关系强度 (0-100)
 * - 关系历史
 * - 社交圈子
 */
public interface RelationshipNetwork {

    // ==================== 关系操作 ====================

    /**
     * 获取两个玩家之间的关系
     */
    Relationship getRelationship(UUID player1, UUID player2);

    /**
     * 获取玩家的所有关系
     */
    Map<UUID, Relationship> getAllRelationships(UUID playerId);

    /**
     * 获取玩家的社交圈子 (高关系强度的玩家)
     */
    Set<UUID> getSocialCircle(UUID playerId, int minStrength);

    /**
     * 建立/更新关系
     */
    void setRelationship(UUID player1, UUID player2, RelationshipType type, int strength);

    /**
     * 增加关系强度
     */
    void increaseStrength(UUID player1, UUID player2, int amount);

    /**
     * 减少关系强度
     */
    void decreaseStrength(UUID player1, UUID player2, int amount);

    /**
     * 移除关系
     */
    void removeRelationship(UUID player1, UUID player2);

    // ==================== 关系查询 ====================

    /**
     * 获取所有朋友
     */
    Set<UUID> getFriends(UUID playerId);

    /**
     * 获取所有敌人
     */
    Set<UUID> getEnemies(UUID playerId);

    /**
     * 获取最好朋友
     */
    Optional<UUID> getBestFriend(UUID playerId);

    /**
     * 获取最大敌人
     */
    Optional<UUID> getWorstEnemy(UUID playerId);

    // ==================== 社交分析 ====================

    /**
     * 计算社交影响力分数
     */
    int calculateInfluenceScore(UUID playerId);

    /**
     * 获取共同好友
     */
    Set<UUID> getMutualFriends(UUID player1, UUID player2);

    /**
     * 获取社交距离 (通过几个朋友连接)
     */
    int getSocialDistance(UUID from, UUID to);

    // ==================== 关系衰减 ====================

    /**
     * 对所有关系应用时间衰减
     */
    void applyDecay(long deltaMillis);

    // ==================== 数据类 ====================

    enum RelationshipType {
        STRANGER(0, "陌生人"),
        ACQUAINTANCE(20, "熟人"),
        FRIEND(50, "朋友"),
        CLOSE_FRIEND(80, "挚友"),
        BEST_FRIEND(100, "闺蜜/兄弟"),
        RIVAL(-20, "竞争对手"),
        ENEMY(-50, "敌人"),
        NEMESIS(-100, "死敌"),
        LOVER(90, "恋人"),
        EX_LOVER(0, "前任");

        private final int baseStrength;
        private final String displayName;

        RelationshipType(int base, String name) {
            this.baseStrength = base;
            this.displayName = name;
        }

        public int baseStrength() { return baseStrength; }
        public String displayName() { return displayName; }
    }

    record Relationship(
        UUID player1,
        UUID player2,
        RelationshipType type,
        int strength,
        long lastInteraction,
        long createdAt,
        List<Interaction> history
    ) {
        public boolean isPositive() { return strength > 0; }
        public boolean isNegative() { return strength < 0; }
        public boolean isStrong() { return Math.abs(strength) >= 70; }
    }

    record Interaction(
        long timestamp,
        String action,
        int strengthChange,
        String description
    ) {}
}
