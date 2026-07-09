package dev.starcore.starcore.social.simulation;

/**
 * 八卦验证状态枚举
 */
public enum VerificationStatus {
    /**
     * 已验证为真
     */
    VERIFIED("已验证", "§a✓", 1.0),

    /**
     * 未验证
     */
    UNVERIFIED("未验证", "§7?", 0.0),

    /**
     * 已辟谣 (假新闻)
     */
    DEBUNKED("已辟谣", "§c✗", -1.0),

    /**
     * 待验证
     */
    PENDING("待验证", "§e⏳", 0.0),

    /**
     * 争议中
     */
    DISPUTED("争议中", "§6⚠", 0.5);

    private final String displayName;
    private final String emoji;
    private final double truthScore;

    VerificationStatus(String displayName, String emoji, double truthScore) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.truthScore = truthScore;
    }

    public String displayName() {
        return displayName;
    }

    public String emoji() {
        return emoji;
    }

    public double truthScore() {
        return truthScore;
    }

    /**
     * 获取状态颜色代码
     */
    public String getColor() {
        return switch (this) {
            case VERIFIED -> "§a";
            case DEBUNKED -> "§c";
            case PENDING -> "§e";
            case DISPUTED -> "§6";
            case UNVERIFIED -> "§7";
        };
    }

    /**
     * 获取状态描述
     */
    public String getDescription() {
        return switch (this) {
            case VERIFIED -> "该八卦已被多人验证为真实";
            case DEBUNKED -> "该八卦已被证实为谣言";
            case PENDING -> "该八卦正在等待社区验证";
            case DISPUTED -> "该八卦存在争议,真假难辨";
            case UNVERIFIED -> "该八卦尚未经过任何验证";
        };
    }
}
