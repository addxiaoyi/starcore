package dev.starcore.starcore.foundation.feedback;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 操作确认动画管理器
 * 提供安全的操作确认流程，包括倒计时、圆环动画、确认/取消效果
 */
public final class OperationConfirmationManager {
    private final Plugin plugin;
    private final Map<UUID, ConfirmationTask> activeConfirmations = new ConcurrentHashMap<>();

    // 确认超时时间（tick）
    private static final int DEFAULT_TIMEOUT = 20 * 10; // 10秒
    private static final int TICK_INTERVAL = 1;

    public OperationConfirmationManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 确认结果回调
     */
    public interface ConfirmationCallback {
        void onConfirm();
        void onCancel();
    }

    /**
     * 确认任务
     */
    private static class ConfirmationTask {
        final UUID playerId;
        final String operationName;
        final ConfirmationCallback callback;
        final BukkitTask task;
        final long startTime;
        final int totalTicks;

        ConfirmationTask(UUID playerId, String operationName, ConfirmationCallback callback,
                        BukkitTask task, long startTime, int totalTicks) {
            this.playerId = playerId;
            this.operationName = operationName;
            this.callback = callback;
            this.task = task;
            this.startTime = startTime;
            this.totalTicks = totalTicks;
        }
    }

    /**
     * 确认动画类型
     */
    public enum ConfirmationType {
        /** 圆形倒计时环 */
        CIRCULAR_COUNTDOWN,
        /** 缩小确认圈 */
        SHRINKING_CIRCLE,
        /** 闪烁警告 */
        FLASHING_WARNING,
        /** 简单倒计时数字 */
        SIMPLE_COUNTDOWN,
        /** 无动画静默确认 */
        SILENT
    }

    /**
     * 请求玩家确认操作
     * @param player 玩家
     * @param operationName 操作名称（显示在确认提示中）
     * @param callback 确认回调
     * @return 是否成功发起确认（如果已有待确认操作则返回false）
     */
    public boolean requestConfirmation(Player player, String operationName, ConfirmationCallback callback) {
        return requestConfirmation(player, operationName, callback, DEFAULT_TIMEOUT, ConfirmationType.CIRCULAR_COUNTDOWN);
    }

    /**
     * 请求玩家确认操作（可自定义类型）
     */
    public boolean requestConfirmation(Player player, String operationName, ConfirmationCallback callback,
                                       int timeoutTicks, ConfirmationType type) {
        if (activeConfirmations.containsKey(player.getUniqueId())) {
            // 已有待确认操作
            player.sendMessage(Component.text("你还有一个待确认的操作，请先处理后再试。", NamedTextColor.RED));
            return false;
        }

        // 显示确认提示
        showConfirmationPrompt(player, operationName);

        // 根据类型启动不同的动画
        BukkitTask task;
        switch (type) {
            case CIRCULAR_COUNTDOWN -> task = startCircularCountdown(player, operationName, callback, timeoutTicks);
            case SHRINKING_CIRCLE -> task = startShrinkingCircle(player, operationName, callback, timeoutTicks);
            case FLASHING_WARNING -> task = startFlashingWarning(player, operationName, callback, timeoutTicks);
            case SIMPLE_COUNTDOWN -> task = startSimpleCountdown(player, operationName, callback, timeoutTicks);
            case SILENT -> task = startSilentConfirmation(player, operationName, callback, timeoutTicks);
            default -> task = startCircularCountdown(player, operationName, callback, timeoutTicks);
        }

        activeConfirmations.put(player.getUniqueId(),
            new ConfirmationTask(player.getUniqueId(), operationName, callback, task,
                               System.currentTimeMillis(), timeoutTicks));

        return true;
    }

    /**
     * 确认操作
     */
    public void confirm(Player player) {
        ConfirmationTask task = activeConfirmations.remove(player.getUniqueId());
        if (task != null) {
            task.task.cancel();
            executeConfirmation(player, task.operationName, true);
            task.callback.onConfirm();
        }
    }

    /**
     * 取消操作
     */
    public void cancel(Player player) {
        ConfirmationTask task = activeConfirmations.remove(player.getUniqueId());
        if (task != null) {
            task.task.cancel();
            executeCancellation(player, task.operationName);
            task.callback.onCancel();
        }
    }

    /**
     * 检查玩家是否有待确认操作
     */
    public boolean hasPendingConfirmation(Player player) {
        return activeConfirmations.containsKey(player.getUniqueId());
    }

    /**
     * 获取待确认操作的剩余时间（秒）
     */
    public int getRemainingSeconds(Player player) {
        ConfirmationTask task = activeConfirmations.get(player.getUniqueId());
        if (task == null) return 0;
        return (int) ((task.startTime + task.totalTicks * 50 - System.currentTimeMillis()) / 1000);
    }

    /**
     * 取消所有待确认操作
     */
    public void cancelAll() {
        activeConfirmations.values().forEach(task -> task.task.cancel());
        activeConfirmations.clear();
    }

    // ==================== 显示确认提示 ====================

    private void showConfirmationPrompt(Player player, String operationName) {
        player.showTitle(Title.title(
            Component.text("⚠ ", NamedTextColor.YELLOW).append(Component.text("操作确认", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)),
            Component.text("你正在执行: " + operationName, NamedTextColor.GRAY)
                .append(Component.text("\n点击任意位置确认 或 等待 " + (DEFAULT_TIMEOUT / 20) + " 秒自动取消", NamedTextColor.DARK_GRAY)),
            Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofSeconds(5),
                Duration.ofMillis(300)
            )
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.8f);
    }

    // ==================== 圆形倒计时动画 ====================

    private BukkitTask startCircularCountdown(Player player, String operationName,
                                              ConfirmationCallback callback, int totalTicks) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalTicks) {
                    cancel();
                    activeConfirmations.remove(player.getUniqueId());
                    if (ticks >= totalTicks) {
                        executeCancellation(player, operationName);
                        callback.onCancel();
                    }
                    return;
                }

                // 更新进度
                double progress = 1.0 - (double) ticks / totalTicks;
                int remainingSeconds = (totalTicks - ticks) / 20;

                // 显示进度条
                String progressBar = createProgressBar(progress, 20);
                player.sendActionBar(Component.text()
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text(progressBar, NamedTextColor.YELLOW))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text(remainingSeconds + "s", NamedTextColor.WHITE))
                    .append(Component.text(" | 点击确认 ", NamedTextColor.GRAY))
                    .build()
                );

                // 每10tick播放一次粒子
                if (ticks % 10 == 0) {
                    Location loc = player.getLocation().add(0, 1.5, 0);
                    double radius = 0.5 + progress * 0.5;
                    spawnProgressRing(player.getWorld(), loc, radius, progress);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }

    // ==================== 缩小圆圈动画 ====================

    private BukkitTask startShrinkingCircle(Player player, String operationName,
                                           ConfirmationCallback callback, int totalTicks) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalTicks) {
                    cancel();
                    activeConfirmations.remove(player.getUniqueId());
                    if (ticks >= totalTicks) {
                        executeCancellation(player, operationName);
                        callback.onCancel();
                    }
                    return;
                }

                double progress = 1.0 - (double) ticks / totalTicks;
                int remainingSeconds = (totalTicks - ticks) / 20;

                player.sendActionBar(Component.text()
                    .append(Component.text("⏳ " + remainingSeconds + "s | ", NamedTextColor.YELLOW))
                    .append(Component.text(operationName, NamedTextColor.WHITE))
                    .build()
                );

                // 缩小圆环效果
                if (ticks % 5 == 0) {
                    Location loc = player.getLocation().add(0, 1, 0);
                    double radius = progress * 1.5;
                    spawnShrinkingRing(player.getWorld(), loc, radius);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }

    // ==================== 闪烁警告动画 ====================

    private BukkitTask startFlashingWarning(Player player, String operationName,
                                           ConfirmationCallback callback, int totalTicks) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalTicks) {
                    cancel();
                    activeConfirmations.remove(player.getUniqueId());
                    if (ticks >= totalTicks) {
                        executeCancellation(player, operationName);
                        callback.onCancel();
                    }
                    return;
                }

                double progress = 1.0 - (double) ticks / totalTicks;
                int remainingSeconds = (totalTicks - ticks) / 20;

                // 闪烁效果
                NamedTextColor color = (ticks / 10) % 2 == 0 ? NamedTextColor.YELLOW : NamedTextColor.RED;

                player.sendActionBar(Component.text()
                    .append(Component.text("⚠ ", color))
                    .append(Component.text(remainingSeconds + "s | ", NamedTextColor.WHITE))
                    .append(Component.text(operationName, color))
                    .build()
                );

                // 闪烁粒子
                if (ticks % 8 == 0) {
                    Location loc = player.getLocation().add(0, 1.5, 0);
                    player.getWorld().spawnParticle(Particle.SMOKE, loc, 3, 0.2, 0.2, 0.2, 0.02);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }

    // ==================== 简单倒计时动画 ====================

    private BukkitTask startSimpleCountdown(Player player, String operationName,
                                            ConfirmationCallback callback, int totalTicks) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalTicks) {
                    cancel();
                    activeConfirmations.remove(player.getUniqueId());
                    if (ticks >= totalTicks) {
                        executeCancellation(player, operationName);
                        callback.onCancel();
                    }
                    return;
                }

                int remainingSeconds = (totalTicks - ticks) / 20;

                player.sendActionBar(Component.text()
                    .append(Component.text("⏱ " + remainingSeconds + "s ", NamedTextColor.YELLOW))
                    .append(Component.text(operationName, NamedTextColor.GRAY))
                    .build()
                );

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }

    // ==================== 静默确认动画 ====================

    private BukkitTask startSilentConfirmation(Player player, String operationName,
                                               ConfirmationCallback callback, int totalTicks) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalTicks) {
                    cancel();
                    activeConfirmations.remove(player.getUniqueId());
                    if (ticks >= totalTicks) {
                        callback.onCancel();
                    }
                    return;
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }

    // ==================== 确认/取消执行效果 ====================

    private void executeConfirmation(Player player, String operationName, boolean confirmed) {
        Location loc = player.getLocation().add(0, 1, 0);

        if (confirmed) {
            // 成功确认效果
            player.showTitle(Title.title(
                Component.text("✓ ", NamedTextColor.GREEN).append(Component.text("已确认", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)),
                Component.text(operationName, NamedTextColor.DARK_GREEN),
                Title.Times.times(
                    Duration.ofMillis(200),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(300)
                )
            ));

            // 绿色粒子爆发
            spawnConfirmationBurst(player.getWorld(), loc, true);
            player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);

            // 延迟清除actionbar
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendActionBar(Component.empty());
                }
            }, 25L);

        } else {
            // 取消效果
            player.showTitle(Title.title(
                Component.text("✗ ", NamedTextColor.RED).append(Component.text("已取消", NamedTextColor.RED).decorate(TextDecoration.BOLD)),
                Component.text(operationName, NamedTextColor.DARK_GRAY),
                Title.Times.times(
                    Duration.ofMillis(200),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(300)
                )
            ));

            // 红色粒子烟雾
            spawnConfirmationBurst(player.getWorld(), loc, false);
            player.playSound(loc, Sound.UI_BUTTON_CLICK, 0.3f, 0.6f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendActionBar(Component.empty());
                }
            }, 25L);
        }
    }

    private void executeCancellation(Player player, String operationName) {
        executeConfirmation(player, operationName, false);
    }

    // ==================== 粒子效果辅助方法 ====================

    private void spawnProgressRing(org.bukkit.World world, Location loc, double radius, double progress) {
        int particles = (int) (16 * progress);
        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = loc.clone().add(x, 0, z);
            world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    private void spawnShrinkingRing(org.bukkit.World world, Location loc, double radius) {
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = loc.clone().add(x, 0, z);
            world.spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    private void spawnConfirmationBurst(org.bukkit.World world, Location loc, boolean success) {
        if (success) {
            // 绿色成功爆发
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 30, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticle(Particle.WHITE_ASH, loc, 5, 0.3, 0.3, 0.3, 0);
        } else {
            // 红色取消烟雾
            world.spawnParticle(Particle.SMOKE, loc, 20, 0.4, 0.4, 0.4, 0.05);
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ==================== 工具方法 ====================

    private String createProgressBar(double progress, int length) {
        int filled = (int) (progress * length);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        return bar.toString();
    }
}
