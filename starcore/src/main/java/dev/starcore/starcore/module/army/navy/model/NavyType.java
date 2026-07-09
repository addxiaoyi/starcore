package dev.starcore.starcore.module.army.navy.model;

/**
 * 海军舰船类型
 */
public enum NavyType {
    /**
     * 桨帆战船 - 轻型快速船只
     * 攻击: 8, 防御: 5, 生命: 80, 机动: 3.0, 运输: 50
     */
    GALLEY("galley", 8, 5, 80, 3.0, 50, 80),

    /**
     * 护卫舰 - 中型战船
     * 攻击: 12, 防御: 10, 生命: 120, 机动: 2.0, 运输: 100
     */
    FRIGATE("frigate", 12, 10, 120, 2.0, 100, 150),

    /**
     * 战列舰 - 重型主力舰
     * 攻击: 20, 防御: 18, 生命: 200, 机动: 1.0, 运输: 150
     */
    BATTLESHIP("battleship", 20, 18, 200, 1.0, 150, 300),

    /**
     * 运输船 - 运输专用
     * 攻击: 2, 防御: 3, 生命: 60, 机动: 1.5, 运输: 500
     */
    TRANSPORT("transport", 2, 3, 60, 1.5, 500, 50),

    /**
     * 巡洋舰 - 快速攻击舰
     * 攻击: 15, 防御: 8, 生命: 100, 机动: 2.5, 运输: 80
     */
    CRUISER("cruiser", 15, 8, 100, 2.5, 80, 200),

    /**
     * 炮艇 - 轻型火力支援
     * 攻击: 6, 防御: 4, 生命: 50, 机动: 3.5, 运输: 20
     */
    GUNBOAT("gunboat", 6, 4, 50, 3.5, 20, 40);

    private final String key;
    private final int baseAttack;
    private final int baseDefense;
    private final int baseHealth;
    private final double mobility;
    private final int transportCapacity;
    private final int costPerUnit;

    NavyType(String key, int baseAttack, int baseDefense, int baseHealth,
             double mobility, int transportCapacity, int costPerUnit) {
        this.key = key;
        this.baseAttack = baseAttack;
        this.baseDefense = baseDefense;
        this.baseHealth = baseHealth;
        this.mobility = mobility;
        this.transportCapacity = transportCapacity;
        this.costPerUnit = costPerUnit;
    }

    public String key() {
        return key;
    }

    public int baseAttack() {
        return baseAttack;
    }

    public int baseDefense() {
        return baseDefense;
    }

    public int baseHealth() {
        return baseHealth;
    }

    public double mobility() {
        return mobility;
    }

    public int transportCapacity() {
        return transportCapacity;
    }

    public int costPerUnit() {
        return costPerUnit;
    }

    /**
     * 计算总成本
     */
    public int totalCost(int ships) {
        return costPerUnit * ships;
    }

    /**
     * 是否为运输舰船
     */
    public boolean isTransport() {
        return this == TRANSPORT;
    }

    /**
     * 是否为战斗舰船
     */
    public boolean isCombat() {
        return this != TRANSPORT;
    }

    /**
     * 获取最大有效攻击距离（方块）
     */
    public int attackRange() {
        return switch (this) {
            case GUNBOAT -> 30;
            case GALLEY -> 20;
            case FRIGATE -> 50;
            case CRUISER -> 60;
            case BATTLESHIP -> 80;
            case TRANSPORT -> 10;
        };
    }

    /**
     * 从字符串解析
     */
    public static NavyType fromString(String str) {
        for (NavyType type : values()) {
            if (type.key.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown navy type: " + str);
    }
}
