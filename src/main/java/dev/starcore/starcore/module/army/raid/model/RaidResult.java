package dev.starcore.starcore.module.army.raid.model;

/**
 * 突袭结果枚举
 */
public enum RaidResult {
    ATTACKER_VICTORY("攻击方胜利", 0),
    DEFENDER_VICTORY("防御方胜利", 1),
    DRAW("平局", 2),
    IN_PROGRESS("进行中", -1);

    private final String displayName;
    private final int value;

    RaidResult(String displayName, int value) {
        this.displayName = displayName;
        this.value = value;
    }

    public String displayName() { return displayName; }
    public int value() { return value; }

    public boolean isVictory() {
        return this == ATTACKER_VICTORY || this == DEFENDER_VICTORY;
    }
}