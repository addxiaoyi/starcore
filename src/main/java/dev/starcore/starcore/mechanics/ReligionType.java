package dev.starcore.starcore.mechanics;

/**
 * 宗教类型枚举
 */
public enum ReligionType {

    NATURE("自然教", "崇拜自然与生命", "§a"),
    SUN("太阳教", "信仰光明与正义", "§e"),
    MOON("月亮教", "追求智慧与神秘", "§b"),
    WAR("战争教", "崇尚力量与荣耀", "§c"),
    PEACE("和平教", "向往和谐与共存", "§f"),
    DEATH("死亡教", "理解终结与轮回", "§8"),
    OCEAN("海洋教", "敬畏海洋与深渊", "§9"),
    MOUNTAIN("山岳教", "崇拜高山与大地", "§7");

    private final String displayName;
    private final String description;
    private final String colorCode;

    ReligionType(String displayName, String description, String colorCode) {
        this.displayName = displayName;
        this.description = description;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getColorCode() {
        return colorCode;
    }

    /**
     * 获取带颜色的显示名称
     */
    public String getColoredName() {
        return colorCode + displayName;
    }

    /**
     * 获取宗教祝福效果
     */
    public String getBlessingEffect() {
        switch (this) {
            case NATURE:
                return "生命恢复 +20%, 作物生长 +30%";
            case SUN:
                return "伤害 +15%, 不死生物伤害 +30%";
            case MOON:
                return "经验获取 +25%, 附魔效果 +20%";
            case WAR:
                return "攻击力 +20%, 防御力 +15%";
            case PEACE:
                return "交易折扣 10%, 声望获取 +20%";
            case DEATH:
                return "亡灵抗性 +50%, 暗影伤害 +25%";
            case OCEAN:
                return "水下呼吸, 游泳速度 +30%";
            case MOUNTAIN:
                return "挖掘速度 +25%, 跌落伤害 -50%";
            default:
                return "未知效果";
        }
    }

    /**
     * 获取宗教禁忌
     */
    public String getTaboo() {
        switch (this) {
            case NATURE:
                return "不可过度砍伐树木";
            case SUN:
                return "不可在黑暗中长时间停留";
            case MOON:
                return "不可在白天进行重要仪式";
            case WAR:
                return "不可逃避战斗";
            case PEACE:
                return "不可主动发起攻击";
            case DEATH:
                return "不可埋葬尸体";
            case OCEAN:
                return "不可污染水源";
            case MOUNTAIN:
                return "不可破坏山体";
            default:
                return "无特殊禁忌";
        }
    }
}
