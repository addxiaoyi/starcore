package dev.starcore.starcore.module.arbitration.model;

/**
 * 仲裁案件类型枚举
 */
public enum ArbitrationCaseType {
    /**
     * 领土边界争议
     */
    TERRITORY_BOUNDARY("territory-boundary", "领土边界争议", 1),

    /**
     * 领土侵占投诉
     */
    TERRITORY_INVASION("territory-invasion", "领土侵占投诉", 2),

    /**
     * 领土所有权争议
     */
    TERRITORY_OWNERSHIP("territory-ownership", "领土所有权争议", 3),

    /**
     * 资源采集权争议
     */
    RESOURCE_RIGHTS("resource-rights", "资源采集权争议", 4),

    /**
     * 军事行动投诉
     */
    MILITARY_ACTIVITY("military-activity", "军事行动投诉", 5),

    /**
     * 外交违约投诉
     */
    DIPLOMATIC_BREACH("diplomatic-breach", "外交违约投诉", 6),

    /**
     * 贸易纠纷
     */
    TRADE_DISPUTE("trade-dispute", "贸易纠纷", 7),

    /**
     * 其他争议
     */
    OTHER("other", "其他争议", 99);

    private final String key;
    private final String displayName;
    private final int priority;

    ArbitrationCaseType(String key, String displayName, int priority) {
        this.key = key;
        this.displayName = displayName;
        this.priority = priority;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int priority() {
        return priority;
    }

    /**
     * 从key获取枚举值
     */
    public static ArbitrationCaseType fromKey(String key) {
        for (ArbitrationCaseType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return OTHER;
    }
}
