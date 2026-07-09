package dev.starcore.starcore.audit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审计统计数据
 */
public final class AuditStatistics {
    private final Map<String, ActionStats> statsByType = new ConcurrentHashMap<>();
    private final AtomicInteger totalActions = new AtomicInteger(0);
    private final AtomicInteger totalBlocked = new AtomicInteger(0);

    public void add(String actionType, int count, int blockedCount) {
        statsByType.put(actionType, new ActionStats(count, blockedCount));
        totalActions.addAndGet(count);
        totalBlocked.addAndGet(blockedCount);
    }

    public int getTotalActions() {
        return totalActions.get();
    }

    public int getTotalBlocked() {
        return totalBlocked.get();
    }

    public double getBlockedPercentage() {
        int total = totalActions.get();
        return total == 0 ? 0.0 : (totalBlocked.get() * 100.0 / total);
    }

    public Map<String, ActionStats> getStatsByType() {
        return Map.copyOf(statsByType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 审计统计报告 ===\n");
        sb.append(String.format("总操作数: %d\n", totalActions.get()));
        sb.append(String.format("拦截数量: %d (%.2f%%)\n", totalBlocked.get(), getBlockedPercentage()));
        sb.append("\n按操作类型统计:\n");

        statsByType.forEach((type, stats) -> {
            sb.append(String.format("  %s: %d 次 (拦截: %d)\n",
                type, stats.count(), stats.blockedCount()));
        });

        return sb.toString();
    }

    public record ActionStats(int count, int blockedCount) {
        public double blockedPercentage() {
            return count == 0 ? 0.0 : (blockedCount * 100.0 / count);
        }
    }
}
