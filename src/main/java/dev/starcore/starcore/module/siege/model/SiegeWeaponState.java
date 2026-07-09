package dev.starcore.starcore.module.siege.model;

/**
 * 攻城器械状态
 */
public enum SiegeWeaponState {
    /**
     * 存储中 - 可移动，可部署
     */
    STORED("stored", "Stored", true, 1.0),

    /**
     * 已部署 - 可射击
     */
    DEPLOYED("deployed", "Deployed", false, 1.0),

    /**
     * 待机 - 部署后待命
     */
    IDLE("idle", "Idle", false, 0.8),

    /**
     * 战斗中 - 正在射击
     */
    FIGHTING("fighting", "Fighting", false, 1.2),

    /**
     * 维修中 - 不能移动或射击
     */
    REPAIRING("repairing", "Repairing", false, 0.0),

    /**
     * 已摧毁 - 完全不可用
     */
    DESTROYED("destroyed", "Destroyed", false, 0.0);

    private final String key;
    private final String displayName;
    private final boolean canMove;
    private final double damageMultiplier;

    SiegeWeaponState(String key, String displayName, boolean canMove, double damageMultiplier) {
        this.key = key;
        this.displayName = displayName;
        this.canMove = canMove;
        this.damageMultiplier = damageMultiplier;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean canMove() {
        return canMove;
    }

    public double damageMultiplier() {
        return damageMultiplier;
    }

    /**
     * 是否处于可用状态
     */
    public boolean isOperational() {
        return this != DESTROYED && this != REPAIRING;
    }

    /**
     * 是否可以射击
     */
    public boolean canFire() {
        return this == DEPLOYED || this == IDLE || this == FIGHTING;
    }

    /**
     * 从字符串解析
     */
    public static SiegeWeaponState fromString(String str) {
        for (SiegeWeaponState state : values()) {
            if (state.key.equalsIgnoreCase(str) || state.name().equalsIgnoreCase(str)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown siege weapon state: " + str);
    }
}