package dev.starcore.starcore.module.army.doctrine.model;

/**
 * 军事学说类型枚举
 * 定义国家可选择的军事学说及其战斗加成效果
 */
public enum DoctrineType {
    /**
     * 闪电战学说 - 强调机动性和快速突击
     * 骑兵攻击+20%，移动速度+25%，但防御-10%
     */
    BLITZKRLEG("blitzkrieg", "闪电战", "强调机动性和快速突击", 0.20, 0.25, 0, -0.10, 0),

    /**
     * 堡垒防御学说 - 强调领土防守
     * 防御+25%，生命值+15%，但攻击-15%
     */
    FORTRESS("fortress", "堡垒防御", "强调领土防守", 0, 0, 0, 0.25, 0.15),

    /**
     * 炮兵先导学说 - 强调远程火力
     * 远程攻击+30%，但近战防御-15%
     */
    ARTILLERY("artillery", "炮兵先导", "强调远程火力", 0.30, 0, -0.15, 0, 0),

    /**
     * 总体战学说 - 全面战争策略
     * 所有加成+10%，但士气消耗+25%
     */
    TOTAL_WAR("total_war", "总体战", "全面战争策略", 0.10, 0.10, 0.10, 0.10, 0),

    /**
     * 精英部队学说 - 强调精锐单位
     * 单位质量+30%，但成本+50%
     */
    ELITE_FORCES("elite_forces", "精英部队", "强调精锐单位", 0.30, 0, 0, 0.10, 0.30),

    /**
     * 游击战学说 - 强调非对称作战
     * 伏击成功率+40%，撤退成功率+50%，但正面战斗-20%
     */
    GUERRILLA("guerrilla", "游击战", "非对称作战", -0.20, 0.40, 0.40, 0, 0),

    /**
     * 海军霸权学说 - 强调海上力量
     * 海上作战+40%，但陆地战斗-15%
     */
    SEA_POWER("sea_power", "海军霸权", "强调海上力量", 0, 0, 0.40, 0, 0),

    /**
     * 空军优先学说 - 强调空中优势
     * 空战+35%，但地面单位-10%
     */
    AIR_POWER("air_power", "空军优先", "强调空中优势", 0.35, 0, 0, -0.10, 0),

    /**
     * 经济军事学说 - 强调持久战
     * 维持成本-30%，资源效率+20%，但战斗力-15%
     */
    ECONOMIC_MILITARY("economic_military", "经济军事", "强调持久战", -0.15, 0, 0, 0, 0.20),

    /**
     * 无学说 - 默认设置，无任何加成或惩罚
     */
    NONE("none", "无", "无特殊学说", 0, 0, 0, 0, 0);

    private final String key;
    private final String displayName;
    private final String description;
    private final double attackBonus;
    private final double mobilityBonus;
    private final double ambushBonus;
    private final double defenseBonus;
    private final double costEfficiency;

    DoctrineType(String key, String displayName, String description,
                 double attackBonus, double mobilityBonus, double ambushBonus,
                 double defenseBonus, double costEfficiency) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.attackBonus = attackBonus;
        this.mobilityBonus = mobilityBonus;
        this.ambushBonus = ambushBonus;
        this.defenseBonus = defenseBonus;
        this.costEfficiency = costEfficiency;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * 攻击加成（百分比）
     */
    public double attackBonus() {
        return attackBonus;
    }

    /**
     * 机动性加成（百分比）
     */
    public double mobilityBonus() {
        return mobilityBonus;
    }

    /**
     * 伏击/突袭加成（百分比）
     */
    public double ambushBonus() {
        return ambushBonus;
    }

    /**
     * 防御加成（百分比）
     */
    public double defenseBonus() {
        return defenseBonus;
    }

    /**
     * 成本效率（百分比，正值表示省钱，负值表示多花钱）
     */
    public double costEfficiency() {
        return costEfficiency;
    }

    /**
     * 计算最终攻击加成后的攻击力
     */
    public int applyAttackBonus(int baseAttack) {
        return (int) Math.floor(baseAttack * (1 + attackBonus));
    }

    /**
     * 计算最终防御加成后的防御力
     */
    public int applyDefenseBonus(int baseDefense) {
        return (int) Math.floor(baseDefense * (1 + defenseBonus));
    }

    /**
     * 计算成本调整因子
     */
    public double costMultiplier() {
        return 1 + costEfficiency;
    }

    /**
     * 计算士气消耗调整因子（用于总体战等）
     */
    public double moraleConsumptionMultiplier() {
        return switch (this) {
            case TOTAL_WAR -> 1.25;
            case FORTRESS -> 0.90;
            case GUERRILLA -> 0.85;
            default -> 1.0;
        };
    }

    /**
     * 从字符串解析学说类型
     */
    public static DoctrineType fromString(String str) {
        if (str == null || str.isEmpty()) {
            return NONE;
        }
        for (DoctrineType type : values()) {
            if (type.key.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown doctrine type: " + str);
    }

    /**
     * 获取所有可用学说的键名列表
     */
    public static String[] getAvailableKeys() {
        DoctrineType[] types = values();
        String[] keys = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            keys[i] = types[i].key;
        }
        return keys;
    }
}
