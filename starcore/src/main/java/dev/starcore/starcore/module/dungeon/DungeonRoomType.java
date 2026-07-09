package dev.starcore.starcore.module.dungeon;

/**
 * 副本房间类型
 */
public enum DungeonRoomType {
    SPAWNER("刷怪房间"),
    TRAP("陷阱房间"),
    PUZZLE("解谜房间"),
    BOSS("BOSS房间"),
    SURVIVAL("生存挑战"),
    TREASURE("宝藏房间"),
    REST("休息区");

    private final String displayName;

    DungeonRoomType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
