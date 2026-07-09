package dev.starcore.starcore.module.tournament;

/**
 * 比赛类型
 */
public enum TournamentType {
    PVP_1V1("PvP单挑", "1v1个人对决", true, false),
    PVP_FFA("PvP乱斗", "多人自由战斗", false, true),
    PVP_TEAM("团队赛", "团队对战", true, false),
    SPEEDRUN("速通挑战", "完成目标竞速", false, false),
    PARKOUR("跑酷挑战", "跑酷竞速", false, false),
    ELIMINATION("淘汰赛", "生存淘汰", true, false);

    private final String displayName;
    private final String description;
    private final boolean bracketBased;
    private final boolean ffa;

    TournamentType(String displayName, String description, boolean bracketBased, boolean ffa) {
        this.displayName = displayName;
        this.description = description;
        this.bracketBased = bracketBased;
        this.ffa = ffa;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public boolean isBracketBased() { return bracketBased; }
    public boolean isFFA() { return ffa; }
}
