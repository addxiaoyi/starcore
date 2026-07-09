package dev.starcore.starcore.module.nation;

public record NationLevelProgress(
    int level,
    long totalExperience,
    long currentLevelProgress,
    long nextLevelExperienceRequired,
    long remainingExperienceToNextLevel,
    boolean maxLevelReached
) {
}
