package dev.starcore.starcore.foundation.animation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 加载动画管理器
 * 提供各种加载状态的动画反馈
 */
public final class LoadingAnimationManager {
    private final Plugin plugin;
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;
    private final Map<UUID, LoadingState> activeLoaders = new ConcurrentHashMap<>();

    // 加载动画类型
    public enum LoadingType {
        SPINNER,       // 旋转加载
        PROGRESS,      // 进度条
        DOTS,          // 点点加载
        PULSE,         // 脉冲加载
        COMPUTING,     // 计算中
        SKELETON,      // 骨架屏
        WORDS          // 词语循环
    }

    // 加载状态
    private static class LoadingState {
        final Player player;
        final LoadingType type;
        final String message;
        final int totalSteps;
        final Consumer<Integer> onStep;
        final Consumer<Void> onComplete;
        final Runnable onCancel;
        AtomicInt currentStep;
        AtomicInt currentTick;
        BukkitTask task;

        LoadingState(Player player, LoadingType type, String message, int totalSteps,
                     Consumer<Integer> onStep, Consumer<Void> onComplete, Runnable onCancel) {
            this.player = player;
            this.type = type;
            this.message = message;
            this.totalSteps = totalSteps;
            this.onStep = onStep;
            this.onComplete = onComplete;
            this.onCancel = onCancel;
            this.currentStep = new AtomicInt(0);
            this.currentTick = new AtomicInt(0);
        }
    }

    // 简单的 AtomicInt 实现
    private static class AtomicInt {
        private int value;
        AtomicInt(int initial) { this.value = initial; }
        int get() { return value; }
        int getAndIncrement() { return value++; }
        void set(int v) { value = v; }
    }

    public LoadingAnimationManager(Plugin plugin, GuiAnimationManager animationManager, SoundFeedbackManager soundManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
    }

    /**
     * 播放旋转加载动画
     */
    public void startSpinner(Player player, String message, Consumer<Void> onComplete) {
        startSpinner(player, message, -1, null, onComplete);
    }

    /**
     * 播放旋转加载动画（带步骤）
     */
    public void startSpinner(Player player, String message, int maxDuration,
                            Consumer<Integer> onStep, Consumer<Void> onComplete) {
        // 取消已有的加载动画
        cancelLoader(player);

        String[] spinner = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

        LoadingState state = new LoadingState(player, LoadingType.SPINNER, message,
            maxDuration, onStep, onComplete, () -> {});

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    return;
                }

                int tick = state.currentTick.getAndIncrement();
                int spinnerIndex = tick % spinner.length;

                if (maxDuration > 0 && tick >= maxDuration) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    return;
                }

                player.sendActionBar(Component.text(
                    spinner[spinnerIndex] + " " + message,
                    NamedTextColor.YELLOW
                ));
            }
        }.runTaskTimer(plugin, 0L, 2L);

        state.task = task;
        activeLoaders.put(player.getUniqueId(), state);
    }

    /**
     * 播放进度条加载动画
     */
    public void startProgress(Player player, String message, int totalSteps, Consumer<Integer> onStep, Consumer<Void> onComplete) {
        // 取消已有的加载动画
        cancelLoader(player);

        LoadingState state = new LoadingState(player, LoadingType.PROGRESS, message,
            totalSteps, onStep, onComplete, () -> {});

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    return;
                }

                int step = state.currentStep.getAndIncrement();

                if (step >= totalSteps) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    player.sendActionBar(Component.text("✓ " + message + " 完成!", NamedTextColor.GREEN));
                    soundManager.playSuccess(player);
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    return;
                }

                // 执行步骤回调
                if (onStep != null) {
                    onStep.accept(step);
                }

                // 显示进度条
                String progressBar = createProgressBar(step, totalSteps, 20);
                int percent = (step * 100) / totalSteps;
                player.sendActionBar(Component.text(
                    message + " " + progressBar + " " + percent + "%",
                    NamedTextColor.GOLD
                ));
            }
        }.runTaskTimer(plugin, 0L, 3L);

        state.task = task;
        activeLoaders.put(player.getUniqueId(), state);
    }

    /**
     * 播放点点加载动画
     */
    public void startDots(Player player, String message, int durationSeconds, Consumer<Void> onComplete) {
        cancelLoader(player);

        LoadingState state = new LoadingState(player, LoadingType.DOTS, message,
            durationSeconds * 20, null, onComplete, () -> {});

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    return;
                }

                int tick = state.currentTick.getAndIncrement();

                if (tick >= state.totalSteps) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    return;
                }

                int dots = (tick / 5) % 4;
                String dotStr = ".".repeat(dots);
                player.sendActionBar(Component.text(
                    message + dotStr,
                    NamedTextColor.YELLOW
                ));
            }
        }.runTaskTimer(plugin, 0L, 1L);

        state.task = task;
        activeLoaders.put(player.getUniqueId(), state);
    }

    /**
     * 播放脉冲加载动画
     */
    public void startPulse(Player player, String message, int durationSeconds, Consumer<Void> onComplete) {
        cancelLoader(player);

        LoadingState state = new LoadingState(player, LoadingType.PULSE, message,
            durationSeconds * 20, null, onComplete, () -> {});

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    return;
                }

                int tick = state.currentTick.getAndIncrement();

                if (tick >= state.totalSteps) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    return;
                }

                // 脉冲效果
                int pulse = (tick / 8) % 4;
                String pulseStr = "▌".repeat(pulse + 1);
                player.sendActionBar(Component.text(
                    message + " " + pulseStr,
                    pulse % 2 == 0 ? NamedTextColor.YELLOW : NamedTextColor.GOLD
                ));
            }
        }.runTaskTimer(plugin, 0L, 1L);

        state.task = task;
        activeLoaders.put(player.getUniqueId(), state);
    }

    /**
     * 播放词语加载动画
     */
    public void startWords(Player player, String[] words, int intervalTicks, int maxTicks, Consumer<Void> onComplete) {
        cancelLoader(player);

        LoadingState state = new LoadingState(player, LoadingType.WORDS, String.join("/", words),
            maxTicks, null, onComplete, () -> {});

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    return;
                }

                int tick = state.currentTick.getAndIncrement();

                if (tick >= state.totalSteps) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    return;
                }

                int wordIndex = (tick / intervalTicks) % words.length;
                player.sendActionBar(Component.text(
                    "⟳ " + words[wordIndex],
                    NamedTextColor.YELLOW
                ));
            }
        }.runTaskTimer(plugin, 0L, 1L);

        state.task = task;
        activeLoaders.put(player.getUniqueId(), state);
    }

    /**
     * 播放计算中动画
     */
    public void startComputing(Player player, Consumer<Void> onComplete) {
        String[] messages = {"计算中", "处理中", "分析中", "加载中", "更新中"};
        startWords(player, messages, 10, 200, onComplete);
    }

    /**
     * 播放多步骤加载动画
     */
    public void startMultiStep(Player player, List<String> steps, Consumer<Integer> onStep, Consumer<Void> onComplete) {
        if (steps.isEmpty()) {
            if (onComplete != null) {
                onComplete.accept(null);
            }
            return;
        }

        // 替换状态
        LoadingState state = activeLoaders.get(player.getUniqueId());
        if (state != null) {
            state.task.cancel();
        }

        int[] currentStep = {0};

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    return;
                }

                if (currentStep[0] >= steps.size()) {
                    cancel();
                    activeLoaders.remove(player.getUniqueId());
                    player.sendActionBar(Component.text("✓ 全部完成!", NamedTextColor.GREEN));
                    soundManager.playSuccess(player);
                    if (onComplete != null) {
                        onComplete.accept(null);
                    }
                    return;
                }

                String step = steps.get(currentStep[0]);
                String progressBar = createProgressBar(currentStep[0], steps.size(), 15);

                player.sendActionBar(Component.text(
                    "[" + (currentStep[0] + 1) + "/" + steps.size() + "] " + step + " " + progressBar,
                    NamedTextColor.GOLD
                ));

                if (onStep != null) {
                    onStep.accept(currentStep[0]);
                }

                currentStep[0]++;
            }
        }.runTaskTimer(plugin, 0L, 15L); // 每0.75秒一步

        activeLoaders.put(player.getUniqueId(), new LoadingState(
            player, LoadingType.PROGRESS, "", steps.size(), onStep, onComplete, () -> {}
        ));
        activeLoaders.get(player.getUniqueId()).task = task;
    }

    /**
     * 播放数据加载动画（带模拟数据处理）
     */
    public void startDataLoading(Player player, String dataType, int approximateDuration, Consumer<Void> onComplete) {
        String[] messages = {
            "连接数据库...",
            "查询 " + dataType + "...",
            "处理数据...",
            "格式化结果...",
            "完成!"
        };

        startMultiStep(player, Arrays.asList(messages), null, onComplete);
    }

    /**
     * 取消玩家的加载动画
     */
    public void cancelLoader(Player player) {
        LoadingState state = activeLoaders.remove(player.getUniqueId());
        if (state != null && state.task != null) {
            state.task.cancel();
            state.onCancel.run();
        }

        // 清除 ActionBar
        player.sendActionBar(Component.empty());
    }

    /**
     * 取消所有加载动画
     */
    public void cancelAll() {
        activeLoaders.values().forEach(state -> {
            if (state.task != null) {
                state.task.cancel();
            }
        });
        activeLoaders.clear();
    }

    /**
     * 检查玩家是否有活动的加载动画
     */
    public boolean hasActiveLoader(Player player) {
        return activeLoaders.containsKey(player.getUniqueId());
    }

    /**
     * 创建进度条
     */
    private String createProgressBar(int current, int max, int length) {
        if (max <= 0) return "[--------]";

        int filled = (int) ((double) current / max * length);
        StringBuilder bar = new StringBuilder("[");

        for (int i = 0; i < length; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        bar.append("]");
        return bar.toString();
    }
}
