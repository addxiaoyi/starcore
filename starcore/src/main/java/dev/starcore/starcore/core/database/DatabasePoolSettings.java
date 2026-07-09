package dev.starcore.starcore.core.database;

public record DatabasePoolSettings(
    int maximumPoolSize,
    int minimumIdle,
    long connectionTimeoutMs,
    long idleTimeoutMs,
    long maxLifetimeMs,
    long keepaliveTimeMs,
    long validationTimeoutMs,
    long leakDetectionThresholdMs
) {
}
