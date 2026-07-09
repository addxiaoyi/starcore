package dev.starcore.starcore.performance;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对象池
 * 复用对象减少GC压力
 */
public final class ObjectPool<T> {
    private final Queue<T> pool = new ConcurrentLinkedQueue<>();
    private final ObjectFactory<T> factory;
    private final int maxSize;
    private final AtomicInteger currentSize = new AtomicInteger(0);

    // 统计
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalReused = new AtomicInteger(0);

    public ObjectPool(ObjectFactory<T> factory, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
    }

    /**
     * 获取对象
     */
    public T acquire() {
        T object = pool.poll();

        if (object == null) {
            // 创建新对象
            object = factory.create();
            totalCreated.incrementAndGet();
            currentSize.incrementAndGet();
        } else {
            // 复用对象
            totalReused.incrementAndGet();
        }

        // 重置对象状态
        factory.reset(object);

        return object;
    }

    /**
     * 归还对象
     */
    public void release(T object) {
        if (object == null) {
            return;
        }

        // 检查池大小
        if (currentSize.get() < maxSize) {
            pool.offer(object);
        } else {
            // 池已满，销毁对象
            factory.destroy(object);
            currentSize.decrementAndGet();
        }
    }

    /**
     * 清空对象池
     */
    public void clear() {
        T object;
        while ((object = pool.poll()) != null) {
            factory.destroy(object);
            currentSize.decrementAndGet();
        }
    }

    /**
     * 获取统计信息
     */
    public PoolStats getStats() {
        return new PoolStats(
            currentSize.get(),
            pool.size(),
            totalCreated.get(),
            totalReused.get()
        );
    }

    /**
     * 对象工厂
     */
    public interface ObjectFactory<T> {
        /**
         * 创建对象
         */
        T create();

        /**
         * 重置对象状态
         */
        default void reset(T object) {}

        /**
         * 销毁对象
         */
        default void destroy(T object) {}
    }

    /**
     * 对象池统计
     */
    public record PoolStats(
        int totalSize,
        int availableSize,
        int totalCreated,
        int totalReused
    ) {
        public double getReuseRate() {
            int total = totalCreated + totalReused;
            return total > 0 ? (double) totalReused / total * 100 : 0;
        }
    }
}
