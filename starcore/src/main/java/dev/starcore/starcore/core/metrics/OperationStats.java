package dev.starcore.starcore.core.metrics;

import java.time.Duration;

/**
 * Statistics for a tracked operation.
 *
 * @param calls total number of times the operation was executed
 * @param averageDuration average execution duration
 * @param minDuration minimum observed duration
 * @param maxDuration maximum observed duration
 */
public record OperationStats(
    long calls,
    Duration averageDuration,
    Duration minDuration,
    Duration maxDuration
) {
}
