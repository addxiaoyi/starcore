package dev.starcore.starcore.foundation.epoch;

import java.time.Duration;
import java.time.Instant;

public record EpochSnapshot(
    boolean enabled,
    long epochNumber,
    Instant epochStart,
    Instant nextEpochStart,
    Duration epochDuration,
    Duration remaining
) {
}
