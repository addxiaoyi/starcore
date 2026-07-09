package dev.starcore.starcore.module.army.doctrine.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 国家军事学说数据记录
 * 记录每个国家当前采用的学说及变更历史
 */
public record NationDoctrine(
    UUID nationId,
    DoctrineType doctrine,
    long adoptedAt,
    long switchCount,
    String adoptedBy
) {
    /**
     * 创建新的国家学说记录
     */
    public static NationDoctrine create(UUID nationId, DoctrineType doctrine, String adoptedBy) {
        return new NationDoctrine(
            nationId,
            doctrine,
            Instant.now().toEpochMilli(),
            0L,
            adoptedBy
        );
    }

    /**
     * 切换到新学说
     */
    public NationDoctrine switchTo(DoctrineType newDoctrine, String switchedBy) {
        return new NationDoctrine(
            nationId,
            newDoctrine,
            Instant.now().toEpochMilli(),
            switchCount + 1,
            switchedBy
        );
    }

    /**
     * 检查是否可以切换学说（基于冷却时间）
     */
    public boolean canSwitch(long cooldownMs) {
        if (switchCount == 0) {
            return true;
        }
        return Instant.now().toEpochMilli() - adoptedAt >= cooldownMs;
    }

    /**
     * 获取距离上次切换的冷却剩余时间（毫秒）
     */
    public long getRemainingCooldownMs(long cooldownMs) {
        if (switchCount == 0) {
            return 0;
        }
        long elapsed = Instant.now().toEpochMilli() - adoptedAt;
        long remaining = cooldownMs - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * 检查是否采用了任何学说
     */
    public boolean hasDoctrine() {
        return doctrine != null && doctrine != DoctrineType.NONE;
    }
}
