package dev.starcore.starcore.module.satellite;

import java.util.List;

/**
 * 卫星国关系类型枚举
 * 定义不同层次的附庸/保护关系
 */
public enum SatelliteRelation {
    /**
     * 无关系
     */
    NONE("无", 0),

    /**
     * 自治领 - 高度自治，宗主国仅提供军事保护
     * 卫星国可独立外交、宣战
     */
    DOMINION("自治领", 1),

    /**
     * 附庸国 - 中度依赖，宗主国控制部分外交
     * 附庸国宣战需宗主国同意
     */
    VASSAL("附庸国", 2),

    /**
     * 保护国 - 宗主国提供全面军事保护
     * 被保护国放弃部分主权
     */
    PROTECTORATE("保护国", 3),

    /**
     * 殖民地 - 宗主国全面控制
     * 殖民地几乎无自主权
     */
    COLONY("殖民地", 4);

    private final String displayName;
    private final int level; // 依赖程度级别

    SatelliteRelation(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String displayName() {
        return displayName;
    }

    public int level() {
        return level;
    }

    /**
     * 检查该关系是否允许独立
     * @return 是否可以独立
     */
    public boolean canDeclareIndependence() {
        return this == DOMINION || this == VASSAL || this == PROTECTORATE;
    }

    /**
     * 检查该关系是否需要宗主国批准宣战
     * @return 是否需要批准
     */
    public boolean requiresSuzerainApprovalForWar() {
        return this == VASSAL || this == PROTECTORATE || this == COLONY;
    }

    /**
     * 检查该关系是否允许独立外交
     * @return 是否允许独立外交
     */
    public boolean allowsIndependentDiplomacy() {
        return this == DOMINION;
    }

    /**
     * 检查该关系是否提供军事保护
     * @return 是否提供军事保护
     */
    public boolean providesMilitaryProtection() {
        return this != NONE;
    }

    /**
     * 检查该关系是否提供自动防御
     * @return 是否提供自动防御
     */
    public boolean providesAutomaticDefense() {
        return this == PROTECTORATE || this == COLONY;
    }

    /**
     * 获取该关系的默认贡金税率
     * @return 默认税率 (0.0 - 1.0)
     */
    public double defaultTributeRate() {
        return switch (this) {
            case NONE -> 0.0;
            case DOMINION -> 0.05;      // 5%
            case VASSAL -> 0.10;        // 10%
            case PROTECTORATE -> 0.15;   // 15%
            case COLONY -> 0.25;        // 25%
        };
    }

    /**
     * 获取该关系的最大贡金税率
     * @return 最大税率 (0.0 - 1.0)
     */
    public double maxTributeRate() {
        return switch (this) {
            case NONE -> 0.0;
            case DOMINION -> 0.15;
            case VASSAL -> 0.25;
            case PROTECTORATE -> 0.35;
            case COLONY -> 0.50;
        };
    }

    /**
     * 获取关系的优先级（用于排序）
     * @return 优先级（数值越大优先级越高）
     */
    public int priority() {
        return this.ordinal();
    }

    /**
     * 获取所有可比的关系类型
     * @return 关系列表
     */
    public static List<SatelliteRelation> valuesExcludingNone() {
        return List.of(DOMINION, VASSAL, PROTECTORATE, COLONY);
    }
}
