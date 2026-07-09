package dev.starcore.starcore.foundation.animation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动画播放器 - SSS级用户体验
 * 提供各种炫酷的动画效果和过渡
 */
public final class AnimationPlayer {
    private final Plugin plugin;

    public AnimationPlayer(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 播放进度动画
     */
    public void playProgressAnimation(
        Player player,
        String message,
        int durationSeconds,
        Runnable onComplete
    ) {
        AtomicInteger countdown = new AtomicInteger(durationSeconds);
        AtomicInteger taskIdRef = new AtomicInteger(-1);

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int remaining = countdown.getAndDecrement();

            if (remaining <= 0) {
                int currentTaskId = taskIdRef.get();
                if (currentTaskId != -1) {
                    plugin.getServer().getScheduler().cancelTask(currentTaskId);
                }
                onComplete.run();
                return;
            }

            // 显示进度条
            String progressBar = createProgressBar(
                (durationSeconds - remaining),
                durationSeconds,
                20
            );

            player.sendActionBar(Component.text(
                message + " " + progressBar + " " + remaining + "s",
                NamedTextColor.YELLOW
            ));

        }, 0L, 20L).getTaskId();

        taskIdRef.set(taskId);
    }

    /**
     * 播放成功动画
     */
    public void playSuccessAnimation(Player player, String message) {
        // Title 动画
        player.showTitle(Title.title(
            Component.text("✓", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD),
            Component.text(message, NamedTextColor.GREEN),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
            )
        ));

        // 粒子效果
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(
            org.bukkit.Particle.HAPPY_VILLAGER,  // Updated from VILLAGER_HAPPY
            loc,
            30,
            0.5, 0.5, 0.5,
            0.1
        );

        // 音效
        player.playSound(
            player.getLocation(),
            Sound.ENTITY_PLAYER_LEVELUP,
            1.0f,
            1.2f
        );
    }

    /**
     * 播放失败动画
     */
    public void playFailureAnimation(Player player, String message) {
        // Title 动画
        player.showTitle(Title.title(
            Component.text("✗", NamedTextColor.RED)
                .decorate(TextDecoration.BOLD),
            Component.text(message, NamedTextColor.RED),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
            )
        ));

        // 粒子效果
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(
            org.bukkit.Particle.LARGE_SMOKE,  // Updated from SMOKE_LARGE
            loc,
            20,
            0.3, 0.3, 0.3,
            0.05
        );

        // 音效
        player.playSound(
            player.getLocation(),
            Sound.ENTITY_VILLAGER_NO,
            1.0f,
            0.8f
        );
    }

    /**
     * 播放传送动画
     */
    public void playTeleportAnimation(Player player, Location from, Location to) {
        // 起点粒子
        from.getWorld().spawnParticle(
            Particle.PORTAL,
            from.add(0, 1, 0),
            50,
            0.5, 1.0, 0.5,
            0.5
        );

        // 传送音效
        player.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // 延迟显示终点粒子
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            to.getWorld().spawnParticle(
                Particle.PORTAL,
                to.add(0, 1, 0),
                50,
                0.5, 1.0, 0.5,
                0.5
            );

            player.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        }, 2L);
    }

    /**
     * 播放战斗动画
     */
    public void playBattleAnimation(Location location) {
        // 爆炸粒子
        location.getWorld().spawnParticle(
            org.bukkit.Particle.EXPLOSION,  // Updated from EXPLOSION_LARGE
            location,
            3,
            1.0, 0.5, 1.0,
            0
        );

        // 火焰粒子
        location.getWorld().spawnParticle(
            Particle.FLAME,
            location,
            30,
            1.0, 0.5, 1.0,
            0.1
        );

        // 战斗音效
        location.getWorld().playSound(
            location,
            Sound.ENTITY_GENERIC_EXPLODE,
            1.0f,
            1.0f
        );
    }

    /**
     * 播放国策激活动画
     */
    public void playPolicyActivationAnimation(Player player, String policyName) {
        // 华丽的 Title
        player.showTitle(Title.title(
            Component.text("⚡ 国策激活 ⚡", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD),
            Component.text(policyName, NamedTextColor.YELLOW),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(3),
                Duration.ofMillis(500)
            )
        ));

        // 发光粒子
        Location loc = player.getLocation().add(0, 2, 0);
        player.getWorld().spawnParticle(
            org.bukkit.Particle.TOTEM_OF_UNDYING,  // Updated from TOTEM
            loc,
            50,
            0.5, 0.5, 0.5,
            0.2
        );

        // 激活音效
        player.playSound(
            player.getLocation(),
            Sound.BLOCK_BELL_USE,
            1.0f,
            1.5f
        );
    }

    /**
     * 创建进度条
     */
    private String createProgressBar(int current, int max, int length) {
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

    /**
     * 播放数字滚动动画
     */
    public void playNumberCountAnimation(
        Player player,
        int from,
        int to,
        Duration duration,
        String prefix
    ) {
        long steps = duration.toMillis() / 50; // 每50ms更新一次
        int increment = (to - from) / (int) steps;

        AtomicInteger current = new AtomicInteger(from);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            int value = current.addAndGet(increment);

            if ((increment > 0 && value >= to) || (increment < 0 && value <= to)) {
                player.sendActionBar(Component.text(
                    prefix + to,
                    NamedTextColor.GOLD
                ));
                task.cancel();
                return;
            }

            player.sendActionBar(Component.text(
                prefix + value,
                NamedTextColor.YELLOW
            ));

        }, 0L, 1L);
    }
}
