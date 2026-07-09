package dev.starcore.starcore.module.dungeon;

/**
 * 队伍状态
 */
public enum DungeonPartyState {
    FORMING("组建中"),
    READY("准备就绪"),
    IN_DUNGEON("副本中"),
    COMPLETED("已完成"),
    DISBANDED("已解散");

    private final String displayName;

    DungeonPartyState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
