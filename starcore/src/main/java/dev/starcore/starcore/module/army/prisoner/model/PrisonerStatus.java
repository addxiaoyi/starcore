package dev.starcore.starcore.module.army.prisoner.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 俘虏状态枚举
 */
public enum PrisonerStatus {
    /** 被俘虏中 */
    CAPTURED("captured", "被俘虏"),
    /** 正在被关押 */
    IMPRISONED("imprisoned", "关押中"),
    /** 正在被劳役 */
    FORCED_LABOR("forced_labor", "劳役中"),
    /** 等待交换 */
    AWAITING_EXCHANGE("awaiting_exchange", "等待交换"),
    /** 已获释 */
    RELEASED("released", "已获释"),
    /** 逃跑中 */
    ESCAPED("escaped", "逃跑中"),
    /** 已死亡 */
    DECEASED("deceased", "已死亡");

    private final String key;
    private final String displayName;

    PrisonerStatus(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 检查是否为活跃状态（被俘虏/关押/劳役/等待交换）
     */
    public boolean isActive() {
        return this == CAPTURED ||
               this == IMPRISONED ||
               this == FORCED_LABOR ||
               this == AWAITING_EXCHANGE;
    }

    public static PrisonerStatus fromKey(String key) {
        for (PrisonerStatus status : values()) {
            if (status.key.equalsIgnoreCase(key)) {
                return status;
            }
        }
        return CAPTURED;
    }
}
