package dev.starcore.starcore.module.dungeon;

/**
 * 副本难度枚举
 */
public enum DungeonDifficulty {
    EASY("easy", "简单", 1.0),
    NORMAL("normal", "普通", 1.5),
    HARD("hard", "困难", 2.0),
    NIGHTMARE("nightmare", "噩梦", 3.0);

    private final String id;
    private final String displayName;
    private final double mobScaling;

    DungeonDifficulty(String id, String displayName, double mobScaling) {
        this.id = id;
        this.displayName = displayName;
        this.mobScaling = mobScaling;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMobScaling() {
        return mobScaling;
    }

    public static DungeonDifficulty fromId(String id) {
        for (DungeonDifficulty diff : values()) {
            if (diff.id.equalsIgnoreCase(id)) {
                return diff;
            }
        }
        return NORMAL;
    }
}
