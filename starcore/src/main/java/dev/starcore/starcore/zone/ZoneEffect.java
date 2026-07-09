package dev.starcore.starcore.zone;

/**
 * 经济区特效枚举
 */
public enum ZoneEffect {
    // 税收相关
    TAX_DOUBLE("税收翻倍", "税收收入翻倍", 1.0),
    TAX_REDUCTION("税收减免", "税收支出减少", -0.1),
    TAX_LUXURY("奢侈品税", "额外奢侈品税收", 0.05),

    // 产出相关
    PRODUCTION_BOOST("产出提升", "基础产出增加", 0.2),
    PRODUCTION_DOUBLE("产出翻倍", "关键资源产量翻倍", 1.0),
    PRODUCTION_EFFICIENCY("效率提升", "生产效率提升", 0.15),

    // 贸易相关
    TRADE_BONUS("贸易红利", "交易收入增加", 0.25),
    TRADE_SPEED("快速交易", "交易速度提升", 0.1),
    NO_TRADE_TAX("免除贸易税", "贸易无需缴税", 1.0),

    // 特殊效果
    LUCK_AURA("幸运光环", "区域内幸运效果", 0.05),
    SPEED_AURA("速度光环", "区域内加速效果", 0.1),
    XP_BOOST("经验提升", "获取经验增加", 0.2),

    // 防御相关
    DEFENSE_BONUS("防御加成", "受到伤害减少", 0.1),
    PEACE_ZONE("和平区域", "禁止PVP", 1.0),
    PROTECTION_SHIELD("保护护盾", "领地保护增强", 0.15);

    private final String displayName;
    private final String description;
    private final double bonus;  // 加成值，正数表示增益，负数表示减益

    ZoneEffect(String displayName, String description, double bonus) {
        this.displayName = displayName;
        this.description = description;
        this.bonus = bonus;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getBonus() { return bonus; }

    /**
     * 获取图标材质
     */
    public String getIcon() {
        return switch (this) {
            case TAX_DOUBLE, TAX_LUXURY -> "GOLD_BLOCK";
            case TAX_REDUCTION -> "PAPER";
            case PRODUCTION_BOOST, PRODUCTION_EFFICIENCY -> "DIAMOND";
            case PRODUCTION_DOUBLE -> "NETHER_STAR";
            case TRADE_BONUS, TRADE_SPEED -> "EMERALD_BLOCK";
            case NO_TRADE_TAX -> "BARRIER";
            case LUCK_AURA -> "GOLDEN_APPLE";
            case SPEED_AURA -> "SUGAR";
            case XP_BOOST -> "EXPERIENCE_BOTTLE";
            case DEFENSE_BONUS -> "SHIELD";
            case PEACE_ZONE -> "DIAMOND_SWORD";
            case PROTECTION_SHIELD -> "END_CRYSTAL";
        };
    }

    /**
     * 获取特效类型
     */
    public EffectType getType() {
        return switch (this) {
            case TAX_DOUBLE, TAX_REDUCTION, TAX_LUXURY -> EffectType.TAX;
            case PRODUCTION_BOOST, PRODUCTION_DOUBLE, PRODUCTION_EFFICIENCY -> EffectType.PRODUCTION;
            case TRADE_BONUS, TRADE_SPEED, NO_TRADE_TAX -> EffectType.TRADE;
            case LUCK_AURA, SPEED_AURA, XP_BOOST -> EffectType.BUFF;
            case DEFENSE_BONUS, PEACE_ZONE, PROTECTION_SHIELD -> EffectType.DEFENSE;
        };
    }

    public enum EffectType {
        TAX("税收"),
        PRODUCTION("产出"),
        TRADE("贸易"),
        BUFF("增益"),
        DEFENSE("防御");

        private final String displayName;

        EffectType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
}
