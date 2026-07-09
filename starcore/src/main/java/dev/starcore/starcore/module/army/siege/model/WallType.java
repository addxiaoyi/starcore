package dev.starcore.starcore.module.army.siege.model;

/**
 * 城墙类型
 */
public enum WallType {
    /**
     * 木墙 - 基础城墙
     */
    WOODEN_WALL("wooden-wall", "木墙", 5000, 0.8),

    /**
     * 石墙 - 普通城墙
     */
    STONE_WALL("stone-wall", "石墙", 10000, 1.0),

    /**
     * 强化石墙 - 坚固城墙
     */
    REINFORCED_STONE_WALL("reinforced-stone-wall", "强化石墙", 15000, 1.2),

    /**
     * 铁墙 - 高级城墙
     */
    IRON_WALL("iron-wall", "铁墙", 20000, 1.5),

    /**
     * 城门 - 可被破坏的城门
     */
    GATE("gate", "城门", 5000, 0.5),

    /**
     * 强化城门
     */
    REINFORCED_GATE("reinforced-gate", "强化城门", 8000, 0.7),

    /**
     * 塔楼 - 防御塔
     */
    TOWER("tower", "塔楼", 12000, 1.3);

    private final String key;
    private final String displayName;
    private final int baseHealth;
    private final double defenseMultiplier;

    WallType(String key, String displayName, int baseHealth, double defenseMultiplier) {
        this.key = key;
        this.displayName = displayName;
        this.baseHealth = baseHealth;
        this.defenseMultiplier = defenseMultiplier;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int baseHealth() {
        return baseHealth;
    }

    public double defenseMultiplier() {
        return defenseMultiplier;
    }

    /**
     * 是否是城门类型
     */
    public boolean isGate() {
        return this == GATE || this == REINFORCED_GATE;
    }

    /**
     * 计算实际耐久度
     */
    public int actualHealth(int level) {
        return (int) (baseHealth * (1.0 + (level - 1) * 0.2) * defenseMultiplier);
    }

    /**
     * 从字符串解析
     */
    public static WallType fromString(String str) {
        for (WallType type : values()) {
            if (type.key.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown wall type: " + str);
    }
}