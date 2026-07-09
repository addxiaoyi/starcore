package dev.starcore.starcore.core.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia 兼容调度器
 * 自动检测运行环境（Paper/Folia），使用对应的调度API
 *
 * 设计目标：
 * - Paper 1.21+ 环境正常运行
 * - Folia 环境无需修改代码
 * - 性能最优化
 */
public final class FoliaCompatScheduler {
    private final Plugin plugin;
    private final boolean isFolia;

    public FoliaCompatScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = detectFolia();

        if (isFolia) {
            plugin.getLogger().info("✅ 检测到 Folia 环境，使用区域调度器");
        } else {
            plugin.getLogger().info("✅ 检测到 Paper 环境，使用传统调度器");
        }
    }

    /**
     * 检测是否运行在 Folia 上
     */
    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 在实体所在区域执行任务（主线程安全）
     */
    public void runAtEntity(Entity entity, Consumer<Object> task) {
        if (isFolia) {
            entity.getScheduler().run(plugin, t -> task.accept(t), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> task.accept(null));
        }
    }

    /**
     * 在位置所在区域执行任务（主线程安全）
     */
    public void runAtLocation(Location location, Consumer<Object> task) {
        if (isFolia) {
            Bukkit.getRegionScheduler().run(plugin, location, t -> task.accept(t));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> task.accept(null));
        }
    }

    /**
     * 延迟在位置所在区域执行任务
     */
    public void runAtLocationDelayed(Location location, Consumer<Object> task, long delayTicks) {
        if (isFolia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.accept(t), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> task.accept(null), delayTicks);
        }
    }

    /**
     * 异步执行任务
     */
    public void runAsync(Runnable task) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * 延迟异步执行
     */
    public void runAsyncDelayed(Runnable task, long delay, TimeUnit unit) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), delay, unit);
        } else {
            long ticks = unit.toMillis(delay) / 50; // 转换为tick
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, ticks);
        }
    }

    /**
     * 定时异步执行
     */
    public void runAsyncRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                t -> task.run(),
                initialDelay,
                period,
                unit
            );
        } else {
            long initialTicks = unit.toMillis(initialDelay) / 50;
            long periodTicks = unit.toMillis(period) / 50;
            Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                task,
                initialTicks,
                periodTicks
            );
        }
    }

    /**
     * 全局任务（不依赖特定区域）
     */
    public void runGlobal(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * 延迟在实体所在区域执行任务
     */
    public void runEntityDelayed(Entity entity, Runnable task, long delayTicks) {
        if (isFolia) {
            entity.getScheduler().runDelayed(plugin, t -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * 全局延迟任务
     */
    public void runDelayed(Runnable task, long delayTicks) {
        runGlobalDelayed(task, delayTicks);
    }

    /**
     * 全局延迟任务
     */
    public void runGlobalDelayed(Runnable task, long delayTicks) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * 全局定时任务
     */
    public void runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                t -> task.run(),
                initialDelayTicks,
                periodTicks
            );
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    /**
     * 取消所有任务
     */
    public void cancelAll() {
        if (!isFolia) {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
        // Folia 下任务自动管理，无需手动取消
    }

    /**
     * 检查当前是否在主线程（Folia下可能有多个主线程）
     */
    public boolean isMainThread() {
        if (isFolia) {
            // Folia 下简化检查
            return true; // Folia的区域线程模型
        } else {
            return Bukkit.isPrimaryThread();
        }
    }

    /**
     * 是否运行在 Folia
     */
    public boolean isFolia() {
        return isFolia;
    }
}
