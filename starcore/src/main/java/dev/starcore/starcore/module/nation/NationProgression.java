package dev.starcore.starcore.module.nation;

public final class NationProgression {
    private NationProgression() {
    }

    public static NationLevelProgress progressForExperience(long experience, int maxLevel, long baseExperience, long experienceStep) {
        int level = levelForExperience(experience, maxLevel, baseExperience, experienceStep);
        return progressForLevelAndExperience(level, experience, maxLevel, baseExperience, experienceStep);
    }

    public static NationLevelProgress progressForLevelAndExperience(
        int level,
        long experience,
        int maxLevel,
        long baseExperience,
        long experienceStep
    ) {
        int safeMaxLevel = Math.max(1, maxLevel);
        int safeLevel = Math.max(1, Math.min(level, safeMaxLevel));
        long safeExperience = Math.max(0L, experience);
        if (safeLevel >= safeMaxLevel) {
            return new NationLevelProgress(safeLevel, safeExperience, 0L, 0L, 0L, true);
        }
        long previousLevelsExperience = totalExperienceRequiredForLevelStart(safeLevel, baseExperience, experienceStep);
        long nextLevelExperienceRequired = experienceForNextLevel(safeLevel, baseExperience, experienceStep);
        long currentLevelProgress = Math.max(0L, safeExperience - previousLevelsExperience);
        if (currentLevelProgress > nextLevelExperienceRequired) {
            currentLevelProgress = nextLevelExperienceRequired;
        }
        return new NationLevelProgress(
            safeLevel,
            safeExperience,
            currentLevelProgress,
            nextLevelExperienceRequired,
            Math.max(0L, nextLevelExperienceRequired - currentLevelProgress),
            false
        );
    }

    public static int levelForExperience(long experience, int maxLevel, long baseExperience, long experienceStep) {
        int safeMaxLevel = Math.max(1, maxLevel);
        long remaining = Math.max(0L, experience);
        int level = 1;
        while (level < safeMaxLevel) {
            long required = experienceForNextLevel(level, baseExperience, experienceStep);
            if (remaining < required) {
                break;
            }
            remaining -= required;
            level++;
        }
        return level;
    }

    static long totalExperienceRequiredForLevelStart(int level, long baseExperience, long experienceStep) {
        int safeLevel = Math.max(1, level);
        long total = 0L;
        for (int current = 1; current < safeLevel; current++) {
            long required = experienceForNextLevel(current, baseExperience, experienceStep);
            long updated = total + required;
            if (updated < total) {
                return Long.MAX_VALUE;
            }
            total = updated;
        }
        return total;
    }

    public static long experienceForNextLevel(int currentLevel, long baseExperience, long experienceStep) {
        long level = Math.max(1L, currentLevel);
        long base = Math.max(1L, baseExperience);
        long step = Math.max(0L, experienceStep);
        long scaled = base + (level - 1L) * step;
        return scaled < base ? Long.MAX_VALUE : scaled;
    }

    public static int claimLimitForLevel(int level, int claimsAtLevelOne, int claimsPerLevel, int hardMaxClaims) {
        long safeLevel = Math.max(1L, level);
        long limit = Math.max(0L, claimsAtLevelOne) + (safeLevel - 1L) * Math.max(0L, claimsPerLevel);
        if (hardMaxClaims >= 0) {
            limit = Math.min(limit, hardMaxClaims);
        }
        return limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit;
    }

    public static int resourceDistrictLimitForLevel(int level, int initialLimit, int levelsPerDistrict, int maxLimit) {
        int safeLevel = Math.max(1, level);
        int safeLevelsPerDistrict = Math.max(1, levelsPerDistrict);
        long unlockedByLevel = safeLevel / safeLevelsPerDistrict;
        long limit = Math.max(0L, initialLimit) + unlockedByLevel;
        if (maxLimit >= 0) {
            limit = Math.min(limit, maxLimit);
        }
        return limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit;
    }
}
