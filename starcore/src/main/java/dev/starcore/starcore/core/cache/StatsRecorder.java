package dev.starcore.starcore.core.cache;

import java.util.concurrent.atomic.LongAdder;

/**
 * Records cache statistics in a thread-safe, lock-free manner.
 */
final class StatsRecorder {
    static final StatsRecorder NOOP = new StatsRecorder(false);

    private final LongAdder hitCount;
    private final LongAdder missCount;
    private final LongAdder loadSuccessCount;
    private final LongAdder loadFailureCount;
    private final LongAdder totalLoadNanos;
    private final LongAdder evictionCount;
    private final boolean enabled;

    StatsRecorder() {
        this(true);
    }

    private StatsRecorder(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.hitCount = new LongAdder();
            this.missCount = new LongAdder();
            this.loadSuccessCount = new LongAdder();
            this.loadFailureCount = new LongAdder();
            this.totalLoadNanos = new LongAdder();
            this.evictionCount = new LongAdder();
        } else {
            this.hitCount = null;
            this.missCount = null;
            this.loadSuccessCount = null;
            this.loadFailureCount = null;
            this.totalLoadNanos = null;
            this.evictionCount = null;
        }
    }

    void recordHit() {
        if (enabled) hitCount.increment();
    }

    void recordMiss() {
        if (enabled) missCount.increment();
    }

    void recordLoadSuccess(long durationNanos) {
        if (enabled) {
            loadSuccessCount.increment();
            totalLoadNanos.add(durationNanos);
        }
    }

    void recordLoadFailure(long durationNanos) {
        if (enabled) {
            loadFailureCount.increment();
            totalLoadNanos.add(durationNanos);
        }
    }

    void recordEviction() {
        if (enabled) evictionCount.increment();
    }

    void recordEvictions(long count) {
        if (enabled) evictionCount.add(count);
    }

    CacheStatistics toStatistics() {
        if (!enabled) {
            return CacheStatistics.empty();
        }
        return new CacheStatistics(
            hitCount.sum(),
            missCount.sum(),
            loadSuccessCount.sum(),
            loadFailureCount.sum(),
            totalLoadNanos.sum(),
            evictionCount.sum()
        );
    }
}
