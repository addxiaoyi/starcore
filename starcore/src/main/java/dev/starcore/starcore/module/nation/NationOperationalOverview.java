package dev.starcore.starcore.module.nation;

public record NationOperationalOverview(
    String founderName,
    int memberCount,
    NationLevelProgress levelProgress,
    int claimCount,
    int claimLimit,
    int cityStateCount,
    int cityStateLimit,
    int resourceDistrictCount,
    int resourceDistrictLimit
) {
    public int level() {
        return levelProgress == null ? 1 : levelProgress.level();
    }

    public long experience() {
        return levelProgress == null ? 0L : levelProgress.totalExperience();
    }

    public long currentLevelProgress() {
        return levelProgress == null ? 0L : levelProgress.currentLevelProgress();
    }

    public long nextLevelExperienceRequired() {
        return levelProgress == null ? 0L : levelProgress.nextLevelExperienceRequired();
    }

    public long remainingExperienceToNextLevel() {
        return levelProgress == null ? 0L : levelProgress.remainingExperienceToNextLevel();
    }

    public boolean maxLevelReached() {
        return levelProgress != null && levelProgress.maxLevelReached();
    }
}
