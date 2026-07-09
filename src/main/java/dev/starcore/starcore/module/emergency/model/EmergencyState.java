package dev.starcore.starcore.module.emergency.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 紧急状态数据模型
 * 记录国家紧急状态的完整信息
 */
public final class EmergencyState {

    /**
     * 紧急状态类型枚举
     */
    public enum EmergencyType {
        /**
         * 战争紧急状态 - 受到敌方入侵或面临战争威胁
         */
        WAR("战争紧急", "当国家面临战争威胁或正在遭受入侵时激活"),

        /**
         * 自然灾害紧急状态 - 洪水、地震、瘟疫等
         */
        NATURAL_DISASTER("自然灾害紧急", "当国家面临自然灾害威胁时激活"),

        /**
         * 经济危机紧急状态 - 经济崩溃、通货膨胀等
         */
        ECONOMIC_CRISIS("经济危机紧急", "当国家经济面临严重危机时激活"),

        /**
         * 内乱紧急状态 - 叛乱、暴动等
         */
        CIVIL_UNREST("内乱紧急", "当国家内部发生叛乱或暴动时激活"),

        /**
         * 戒严状态 - 高度戒备、限制公民自由
         */
        MARTIAL_LAW("戒严状态", "最高级别紧急状态，完全军事管制"),

        /**
         * 公共卫生紧急状态 - 瘟疫、传染病等
         */
        PUBLIC_HEALTH("公共卫生紧急", "当国家面临公共卫生危机时激活"),

        /**
         * 能源危机紧急状态 - 能源短缺
         */
        ENERGY_CRISIS("能源危机紧急", "当国家面临能源短缺时激活");

        private final String displayName;
        private final String description;

        EmergencyType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        /**
         * 获取紧急级别的权重（用于比较优先级）
         */
        public int severity() {
            return switch (this) {
                case MARTIAL_LAW -> 6;
                case WAR -> 5;
                case NATURAL_DISASTER, PUBLIC_HEALTH -> 4;
                case ECONOMIC_CRISIS, ENERGY_CRISIS -> 3;
                case CIVIL_UNREST -> 2;
            };
        }

        /**
         * 检查是否可与其他紧急状态叠加
         */
        public boolean canStackWith(EmergencyType other) {
            // 戒严状态不能与其他状态叠加
            if (this == MARTIAL_LAW || other == MARTIAL_LAW) {
                return false;
            }
            // 同类型不能叠加
            if (this == other) {
                return false;
            }
            return true;
        }
    }

    private final UUID id;
    private final NationId nationId;
    private final EmergencyType type;
    private final String reason;
    private final Instant declaredAt;
    private Instant expiresAt;
    private final String declaredBy;
    private Instant cancelledAt;
    private String cancelledBy;

    public EmergencyState(
        NationId nationId,
        EmergencyType type,
        String reason,
        int durationMinutes,
        String declaredBy
    ) {
        this.id = UUID.randomUUID();
        this.nationId = nationId;
        this.type = type;
        this.reason = reason;
        this.declaredAt = Instant.now();
        this.expiresAt = declaredAt.plus(Duration.ofMinutes(durationMinutes));
        this.declaredBy = declaredBy;
        this.cancelledAt = null;
        this.cancelledBy = null;
    }

    /**
     * 完整构造函数（用于反序列化）
     */
    public EmergencyState(
        UUID id,
        NationId nationId,
        EmergencyType type,
        String reason,
        Instant declaredAt,
        Instant expiresAt,
        String declaredBy,
        Instant cancelledAt,
        String cancelledBy
    ) {
        this.id = id;
        this.nationId = nationId;
        this.type = type;
        this.reason = reason;
        this.declaredAt = declaredAt;
        this.expiresAt = expiresAt;
        this.declaredBy = declaredBy;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
    }

    public UUID id() {
        return id;
    }

    public NationId nationId() {
        return nationId;
    }

    public EmergencyType type() {
        return type;
    }

    public String reason() {
        return reason;
    }

    public Instant declaredAt() {
        return declaredAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String declaredBy() {
        return declaredBy;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public String cancelledBy() {
        return cancelledBy;
    }

    /**
     * 检查紧急状态是否已过期
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * 检查紧急状态是否已取消
     */
    public boolean isCancelled() {
        return cancelledAt != null;
    }

    /**
     * 检查紧急状态是否活跃
     */
    public boolean isActive() {
        return !isCancelled() && !isExpired();
    }

    /**
     * 获取剩余时间（分钟）
     */
    public long remainingMinutes() {
        if (isExpired() || isCancelled()) {
            return 0;
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return Math.max(0, remaining.toMinutes());
    }

    /**
     * 获取剩余时间百分比（0.0 - 1.0）
     */
    public double remainingPercentage() {
        if (isCancelled()) {
            return 0.0;
        }
        Duration total = Duration.between(declaredAt, expiresAt);
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (total.isZero() || total.isNegative()) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) remaining.toMillis() / total.toMillis()));
    }

    /**
     * 取消紧急状态
     */
    public void cancel(String cancelledByPlayer) {
        this.cancelledAt = Instant.now();
        this.cancelledBy = cancelledByPlayer;
    }

    /**
     * 延长紧急状态时间
     */
    public void extend(int additionalMinutes) {
        if (!isActive()) {
            throw new IllegalStateException("Cannot extend an inactive emergency state");
        }
        // 延长是从当前时间开始计算
        this.expiresAt = Instant.now().plus(Duration.ofMinutes(additionalMinutes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmergencyState that = (EmergencyState) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmergencyState{" +
            "id=" + id +
            ", nationId=" + nationId +
            ", type=" + type +
            ", reason='" + reason + '\'' +
            ", declaredAt=" + declaredAt +
            ", expiresAt=" + expiresAt +
            ", declaredBy='" + declaredBy + '\'' +
            ", cancelledAt=" + cancelledAt +
            '}';
    }
}