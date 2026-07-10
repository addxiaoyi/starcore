package dev.starcore.starcore.foundation.util;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 统一的随机数提供器
 * 避免在代码中重复创建 new Random() 对象
 *
 * 性能说明：
 * - 对于简单的随机数，使用 ThreadLocalRandom（线程本地，无竞争）
 * - 对于需要可重现的随机序列，使用共享的 Random 实例
 * - 不再在方法内部创建新的 Random 实例
 */
public final class RandomProvider {

    /** 共享的 Random 实例，用于需要可重现性的场景 */
    private static final Random SHARED_RANDOM = new Random();

    /** 禁止实例化 */
    private RandomProvider() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取线程本地的随机数生成器（推荐）
     * 适用于大多数场景，无锁竞争，性能最优
     */
    public static Random threadLocal() {
        return ThreadLocalRandom.current();
    }

    /**
     * 获取共享的随机数生成器
     * 适用于需要可重现随机序列的场景（如模拟、测试）
     */
    public static Random shared() {
        return SHARED_RANDOM;
    }

    /**
     * 便捷方法：生成 0 到 bound 之间的随机整数
     */
    public static int nextInt(int bound) {
        return threadLocal().nextInt(bound);
    }

    /**
     * 便捷方法：生成 0 到 bound 之间的随机整数
     */
    public static int nextInt(int origin, int bound) {
        return threadLocal().nextInt(origin, bound);
    }

    /**
     * 便捷方法：生成随机布尔值
     */
    public static boolean nextBoolean() {
        return threadLocal().nextBoolean();
    }

    /**
     * 便捷方法：生成随机 double (0.0 到 1.0 之间)
     */
    public static double nextDouble() {
        return threadLocal().nextDouble();
    }

    /**
     * 便捷方法：从数组中随机选择一个元素
     */
    @SafeVarargs
    public static <T> T randomElement(T... array) {
        if (array == null || array.length == 0) {
            return null;
        }
        return array[nextInt(array.length)];
    }

    /**
     * 便捷方法：从列表中随机选择一个元素
     */
    public static <T> T randomElement(java.util.List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(nextInt(list.size()));
    }

    /**
     * 便捷方法：从 Set 中随机选择一个元素
     */
    public static <T> T randomElement(java.util.Set<T> set) {
        if (set == null || set.isEmpty()) {
            return null;
        }
        int index = nextInt(set.size());
        int i = 0;
        for (T element : set) {
            if (i++ == index) {
                return element;
            }
        }
        return null;
    }
}
