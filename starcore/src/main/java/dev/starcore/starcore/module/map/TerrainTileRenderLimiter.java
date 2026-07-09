package dev.starcore.starcore.module.map;

import java.util.concurrent.atomic.AtomicInteger;

final class TerrainTileRenderLimiter {
    private final AtomicInteger activeJobs = new AtomicInteger();

    boolean tryAcquire(int maxJobs) {
        if (maxJobs <= 0) {
            return false;
        }
        while (true) {
            int current = activeJobs.get();
            if (current >= maxJobs) {
                return false;
            }
            if (activeJobs.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    void release() {
        activeJobs.updateAndGet(current -> Math.max(0, current - 1));
    }

    int activeJobs() {
        return activeJobs.get();
    }

    void reset() {
        activeJobs.set(0);
    }
}
