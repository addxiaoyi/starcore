package dev.starcore.starcore.foundation.feedback;

public record InGameFeedbackProfile(
    boolean enabled,
    String sound,
    float soundVolume,
    float soundPitch,
    String particle,
    int particleCount,
    double particleSpread,
    double particleYOffset,
    String actionBar,
    String title,
    String subtitle,
    int titleFadeInTicks,
    int titleStayTicks,
    int titleFadeOutTicks,
    String bossBar,
    String bossBarColor,
    String bossBarOverlay,
    float bossBarProgress,
    int bossBarDurationTicks
) {
}
