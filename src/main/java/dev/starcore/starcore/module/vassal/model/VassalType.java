package dev.starcore.starcore.module.vassal.model;

/**
 * 宗藩关系类型
 * Represents the type of vassal relationship
 */
public enum VassalType {
    /**
     * 完全藩属 - 宗主国完全控制，藩属几乎没有自主权
     */
    FULL_VASSAL("完全藩属", 1.0, true),

    /**
     * 半独立藩属 - 有一定自主权但仍需服从宗主国
     */
    PROTECTED_VASSAL("半独立藩属", 0.6, false),

    /**
     * 附庸国 - 高度自治，仅名义上服从
     */
    TRIBUTARY("附庸国", 0.3, false),

    /**
     * 自治领 - 特殊地位的藩属
     */
    DOMINION("自治领", 0.5, false);

    private final String displayName;
    private final double tributeRate; // 贡金比例 (0.0 - 1.0)
    private final boolean requireFullSubmission; // 是否需要完全臣服

    VassalType(String displayName, double tributeRate, boolean requireFullSubmission) {
        this.displayName = displayName;
        this.tributeRate = tributeRate;
        this.requireFullSubmission = requireFullSubmission;
    }

    public String displayName() {
        return displayName;
    }

    public double tributeRate() {
        return tributeRate;
    }

    public boolean requireFullSubmission() {
        return requireFullSubmission;
    }
}
