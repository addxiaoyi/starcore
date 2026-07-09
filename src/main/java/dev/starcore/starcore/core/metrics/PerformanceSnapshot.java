package dev.starcore.starcore.core.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of performance metrics at a point in time.
 *
 * @param timestamp when this snapshot was taken
 * @param uptime how long STARCORE has been running
 * @param operations operation statistics by name
 * @param caches cache statistics by name
 */
public record PerformanceSnapshot(
    Instant timestamp,
    Duration uptime,
    Map<String, OperationStats> operations,
    Map<String, CacheStats> caches
) {
    /**
     * Returns a summary report suitable for logging or display.
     *
     * @return formatted summary
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== STARCORE Performance Snapshot ===\n");
        sb.append("Timestamp: ").append(timestamp).append("\n");
        sb.append("Uptime: ").append(formatDuration(uptime)).append("\n\n");

        if (!operations.isEmpty()) {
            sb.append("--- Operations ---\n");
            operations.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().calls(), a.getValue().calls()))
                .limit(10)
                .forEach(entry -> {
                    String name = entry.getKey();
                    OperationStats stats = entry.getValue();
                    sb.append(String.format("  %s: %d calls, avg=%s, min=%s, max=%s\n",
                        name,
                        stats.calls(),
                        formatDuration(stats.averageDuration()),
                        formatDuration(stats.minDuration()),
                        formatDuration(stats.maxDuration())));
                });
            sb.append("\n");
        }

        if (!caches.isEmpty()) {
            sb.append("--- Caches ---\n");
            caches.forEach((name, stats) -> {
                sb.append(String.format("  %s: %.1f%% hit rate (%d hits, %d misses)\n",
                    name,
                    stats.hitRate() * 100,
                    stats.hits(),
                    stats.misses()));
            });
        }

        return sb.toString();
    }

    private String formatDuration(Duration duration) {
        if (duration.toNanos() < 1_000) {
            return duration.toNanos() + "ns";
        } else if (duration.toNanos() < 1_000_000) {
            return String.format("%.2fμs", duration.toNanos() / 1000.0);
        } else if (duration.toMillis() < 1000) {
            return String.format("%.2fms", duration.toNanos() / 1_000_000.0);
        } else {
            return String.format("%.2fs", duration.toMillis() / 1000.0);
        }
    }
}
