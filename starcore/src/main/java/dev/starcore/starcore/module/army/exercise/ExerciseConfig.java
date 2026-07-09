package dev.starcore.starcore.module.army.exercise;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 演习配置
 */
public final class ExerciseConfig {
    private final int minParticipants;
    private final int maxParticipants;
    private final int maxDurationMinutes;
    private final int minLevel;
    private final int minSoldiersPerParticipant;
    private final int maxSoldiersPerParticipant;
    private final double moraleGainPerVictory;
    private final double moralePenaltyForDefeat;
    private final double experienceGainPerParticipant;
    private final boolean autoStart;
    private final boolean rewardsEnabled;

    public ExerciseConfig(
        int minParticipants,
        int maxParticipants,
        int maxDurationMinutes,
        int minLevel,
        int minSoldiersPerParticipant,
        int maxSoldiersPerParticipant,
        double moraleGainPerVictory,
        double moralePenaltyForDefeat,
        double experienceGainPerParticipant,
        boolean autoStart,
        boolean rewardsEnabled
    ) {
        this.minParticipants = minParticipants;
        this.maxParticipants = maxParticipants;
        this.maxDurationMinutes = maxDurationMinutes;
        this.minLevel = minLevel;
        this.minSoldiersPerParticipant = minSoldiersPerParticipant;
        this.maxSoldiersPerParticipant = maxSoldiersPerParticipant;
        this.moraleGainPerVictory = moraleGainPerVictory;
        this.moralePenaltyForDefeat = moralePenaltyForDefeat;
        this.experienceGainPerParticipant = experienceGainPerParticipant;
        this.autoStart = autoStart;
        this.rewardsEnabled = rewardsEnabled;
    }

    public static ExerciseConfig defaults() {
        return new ExerciseConfig(
            2,      // minParticipants
            10,     // maxParticipants
            60,     // maxDurationMinutes
            0,      // minLevel
            50,     // minSoldiersPerParticipant
            500,    // maxSoldiersPerParticipant
            5.0,    // moraleGainPerVictory
            -5.0,   // moralePenaltyForDefeat
            100,    // experienceGainPerParticipant
            false,  // autoStart
            true    // rewardsEnabled
        );
    }

    public static ExerciseConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        return new ExerciseConfig(
            section.getInt("min-participants", 2),
            section.getInt("max-participants", 10),
            section.getInt("max-duration-minutes", 60),
            section.getInt("min-level", 0),
            section.getInt("min-soldiers-per-participant", 50),
            section.getInt("max-soldiers-per-participant", 500),
            section.getDouble("morale-gain-per-victory", 5.0),
            section.getDouble("morale-penalty-for-defeat", -5.0),
            section.getDouble("experience-gain-per-participant", 100.0),
            section.getBoolean("auto-start", false),
            section.getBoolean("rewards-enabled", true)
        );
    }

    public int minParticipants() {
        return minParticipants;
    }

    public int maxParticipants() {
        return maxParticipants;
    }

    public int maxDurationMinutes() {
        return maxDurationMinutes;
    }

    public int minLevel() {
        return minLevel;
    }

    public int minSoldiersPerParticipant() {
        return minSoldiersPerParticipant;
    }

    public int maxSoldiersPerParticipant() {
        return maxSoldiersPerParticipant;
    }

    public double moraleGainPerVictory() {
        return moraleGainPerVictory;
    }

    public double moralePenaltyForDefeat() {
        return moralePenaltyForDefeat;
    }

    public double experienceGainPerParticipant() {
        return experienceGainPerParticipant;
    }

    public boolean autoStart() {
        return autoStart;
    }

    public boolean rewardsEnabled() {
        return rewardsEnabled;
    }
}
