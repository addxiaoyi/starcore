package dev.starcore.starcore.module.arbitration.model;

/**
 * 仲裁裁决结果枚举
 */
public enum ArbitrationResult {
    /**
     * 支持申诉方 - 争议领土归申诉方所有
     */
    CLAIMANT_FAVOR("claimant_favor", "支持申诉方", 1),

    /**
     * 支持被申诉方 - 驳回申诉
     */
    RESPONDENT_FAVOR("respondent_favor", "支持被申诉方", 2),

    /**
     * 分割裁决 - 争议领土在双方之间分割
     */
    SPLIT_DECISION("split", "分割裁决", 3),

    /**
     * 中立裁决 - 维持现状
     */
    NEUTRAL("neutral", "中立裁决", 4),

    /**
     * 部分支持 - 部分支持申诉方的诉求
     */
    PARTIAL("partial", "部分支持", 5),

    /**
     * 和解 - 双方达成和解
     */
    SETTLED("settled", "和解", 6),

    /**
     * 无效案件 - 案件无效或证据不足
     */
    INVALID("invalid", "无效案件", 7),

    /**
     * 驳回 - 驳回案件（程序问题）
     */
    DISMISSED("dismissed", "驳回", 8);

    private final String key;
    private final String displayName;
    private final int value;

    ArbitrationResult(String key, String displayName, int value) {
        this.key = key;
        this.displayName = displayName;
        this.value = value;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int value() {
        return value;
    }

    /**
     * 裁决是否对申诉方有利
     */
    public boolean favorsClaimant() {
        return this == CLAIMANT_FAVOR || this == PARTIAL;
    }

    /**
     * 裁决是否对被申诉方有利
     */
    public boolean favorsRespondent() {
        return this == RESPONDENT_FAVOR;
    }

    /**
     * 是否有领土转让
     */
    public boolean involvesTransfer() {
        return this == CLAIMANT_FAVOR || this == RESPONDENT_FAVOR || this == SPLIT_DECISION;
    }

    /**
     * 从key获取枚举值
     */
    public static ArbitrationResult fromKey(String key) {
        for (ArbitrationResult result : values()) {
            if (result.key.equalsIgnoreCase(key)) {
                return result;
            }
        }
        return NEUTRAL;
    }
}
