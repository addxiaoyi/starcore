package dev.starcore.starcore.module.tournament;

/**
 * 比赛状态
 */
public enum TournamentStatus {
    WAITING("等待中", true),
    IN_PROGRESS("进行中", false),
    COMPLETED("已完成", false),
    CANCELLED("已取消", false);

    private final String displayName;
    private final boolean joinable;

    TournamentStatus(String displayName, boolean joinable) {
        this.displayName = displayName;
        this.joinable = joinable;
    }

    public String displayName() { return displayName; }
    public boolean isJoinable() { return joinable; }
    public boolean isFinished() { return this == COMPLETED || this == CANCELLED; }
}
