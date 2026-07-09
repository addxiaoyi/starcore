package dev.starcore.starcore.module.diplomacy;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;

/**
 * 战争记录 - 追踪战争宣告时间
 */
public record WarRecord(
    NationId declarer,    // 宣战国
    NationId target,       // 被宣战国
    Instant declaredAt,    // 宣战时间
    NationId attacker      // 当前攻击方（可能随战斗改变）
) {
    public WarRecord(NationId declarer, NationId target, Instant declaredAt) {
        this(declarer, target, declaredAt, declarer);
    }

    /**
     * 战争持续时间（秒）
     */
    public long durationSeconds() {
        return Instant.now().getEpochSecond() - declaredAt.getEpochSecond();
    }

    /**
     * 战争持续时间（格式化）
     */
    public String durationFormatted() {
        long seconds = durationSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "天" + hours + "小时";
        } else if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        } else {
            return minutes + "分钟";
        }
    }
}
