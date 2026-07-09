package dev.starcore.starcore.module.arbitration.model;

/**
 * 仲裁案件状态枚举
 */
public enum ArbitrationStatus {
    /**
     * 待分配 - 案件已提交，等待仲裁员接受
     */
    PENDING("pending", "待分配", 0),

    /**
     * 审理中 - 仲裁员已接受，正在审理
     */
    IN_PROGRESS("in_progress", "审理中", 1),

    /**
     * 等待答辩 - 已分配仲裁员，等待被告提交答辩
     */
    AWAITING_DEFENSE("awaiting_defense", "等待答辩", 2),

    /**
     * 证据收集 - 双方可以提交证据
     */
    EVIDENCE_GATHERING("evidence_gathering", "证据收集中", 3),

    /**
     * 裁决中 - 仲裁员正在做出裁决
     */
    RULING("ruling", "裁决中", 4),

    /**
     * 已完成 - 裁决已做出
     */
    COMPLETED("completed", "已完成", 5),

    /**
     * 已撤销 - 申诉方撤诉
     */
    WITHDRAWN("withdrawn", "已撤销", 6),

    /**
     * 已过期 - 超过审理期限
     */
    EXPIRED("expired", "已过期", 7),

    /**
     * 已驳回 - 案件不符合受理条件
     */
    REJECTED("rejected", "已驳回", 8);

    private final String key;
    private final String displayName;
    private final int order;

    ArbitrationStatus(String key, String displayName, int order) {
        this.key = key;
        this.displayName = displayName;
        this.order = order;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int order() {
        return order;
    }

    /**
     * 是否为终态
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == WITHDRAWN || this == EXPIRED || this == REJECTED;
    }

    /**
     * 是否可以进行裁决
     */
    public boolean canRule() {
        return this == RULING || this == EVIDENCE_GATHERING;
    }

    /**
     * 从key获取枚举值
     */
    public static ArbitrationStatus fromKey(String key) {
        for (ArbitrationStatus status : values()) {
            if (status.key.equalsIgnoreCase(key)) {
                return status;
            }
        }
        return PENDING;
    }
}
