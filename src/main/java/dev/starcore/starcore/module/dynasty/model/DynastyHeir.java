package dev.starcore.starcore.module.dynasty.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 王朝继承人数据模型
 * 存储继承人的信息和继承优先级
 *
 * @param playerId 玩家ID
 * @param playerName 玩家名称
 * @param birthOrder 出生顺序
 * @param inheritancePriority 继承优先级 (0表示无继承权)
 * @param gender 性别 (MALE/FEMALE/UNKNOWN)
 * @param isLegitimate 是否嫡出
 * @param claimStrength 继承权强度 (0.0 - 1.0)
 * @param appointedBy 在位者指定 (UUID)
 * @param appointedAt 指定时间
 * @param disinherited 是否被剥夺继承权
 * @param disinheritReason 剥夺原因
 */
public record DynastyHeir(
    UUID playerId,
    String playerName,
    int birthOrder,
    int inheritancePriority,
    Gender gender,
    boolean isLegitimate,
    double claimStrength,
    UUID appointedBy,
    Instant appointedAt,
    boolean disinherited,
    String disinheritReason
) {
    /**
     * 性别枚举
     */
    public enum Gender {
        MALE("男"),
        FEMALE("女"),
        UNKNOWN("未知");

        private final String displayName;

        Gender(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 创建新继承人的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 检查继承人是否有有效继承权
     */
    public boolean hasValidClaim() {
        return inheritancePriority > 0 && !disinherited && claimStrength > 0;
    }

    /**
     * 继承人构建器
     */
    public static class Builder {
        private UUID playerId;
        private String playerName;
        private int birthOrder;
        private int inheritancePriority = 1;
        private Gender gender = Gender.UNKNOWN;
        private boolean isLegitimate = true;
        private double claimStrength = 1.0;
        private UUID appointedBy;
        private Instant appointedAt;
        private boolean disinherited = false;
        private String disinheritReason;

        public Builder playerId(UUID playerId) { this.playerId = playerId; return this; }
        public Builder playerName(String playerName) { this.playerName = playerName; return this; }
        public Builder birthOrder(int birthOrder) { this.birthOrder = birthOrder; return this; }
        public Builder inheritancePriority(int inheritancePriority) { this.inheritancePriority = inheritancePriority; return this; }
        public Builder gender(Gender gender) { this.gender = gender; return this; }
        public Builder isLegitimate(boolean isLegitimate) { this.isLegitimate = isLegitimate; return this; }
        public Builder claimStrength(double claimStrength) { this.claimStrength = claimStrength; return this; }
        public Builder appointedBy(UUID appointedBy) { this.appointedBy = appointedBy; return this; }
        public Builder appointedAt(Instant appointedAt) { this.appointedAt = appointedAt; return this; }
        public Builder disinherited(boolean disinherited) { this.disinherited = disinherited; return this; }
        public Builder disinheritReason(String disinheritReason) { this.disinheritReason = disinheritReason; return this; }

        public DynastyHeir build() {
            return new DynastyHeir(
                playerId,
                playerName,
                birthOrder,
                inheritancePriority,
                gender,
                isLegitimate,
                claimStrength,
                appointedBy,
                appointedAt != null ? appointedAt : Instant.now(),
                disinherited,
                disinheritReason
            );
        }
    }
}