package dev.starcore.starcore.foundation.animation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * GUI 动画管理器 - SSS级用户体验
 * 提供菜单动画、过渡效果、粒子特效、声音反馈、加载动画
 */
public final class GuiAnimationManager {
    private final Plugin plugin;
    private final Map<UUID, BukkitTask> activeAnimations = new ConcurrentHashMap<>();

    // 动画配置
    private boolean enableMenuAnimations = true;
    private boolean enableParticleEffects = true;
    private boolean enableSoundEffects = true;
    private boolean enableTransitionEffects = true;

    public GuiAnimationManager(Plugin plugin) {
        this.plugin = plugin;
    }

    // ==================== 配置方法 ====================

    public void setEnableMenuAnimations(boolean enable) { this.enableMenuAnimations = enable; }
    public void setEnableParticleEffects(boolean enable) { this.enableParticleEffects = enable; }
    public void setEnableSoundEffects(boolean enable) { this.enableSoundEffects = enable; }
    public void setEnableTransitionEffects(boolean enable) { this.enableTransitionEffects = enable; }

    // ==================== 成功/失败动画 ====================

    /**
     * 播放成功动画
     */
    public void playSuccessAnimation(Player player, String message) {
        if (!enableMenuAnimations) {
            player.sendMessage(Component.text(message, NamedTextColor.GREEN));
            return;
        }

        // Title 动画
        player.showTitle(Title.title(
            Component.text("OK", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
            Component.text(message, NamedTextColor.GREEN),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
            )
        ));

        // 粒子效果
        Location loc = player.getLocation().add(0, 1, 0);
        if (enableParticleEffects) {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 30, 0.5, 0.5, 0.5, 0.1);
        }

        // 音效
        if (enableSoundEffects) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    /**
     * 播放失败动画
     */
    public void playFailureAnimation(Player player, String message) {
        if (!enableMenuAnimations) {
            player.sendMessage(Component.text(message, NamedTextColor.RED));
            return;
        }

        // Title 动画
        player.showTitle(Title.title(
            Component.text("X", NamedTextColor.RED).decorate(TextDecoration.BOLD),
            Component.text(message, NamedTextColor.RED),
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
            )
        ));

        // 粒子效果
        Location loc = player.getLocation().add(0, 1, 0);
        if (enableParticleEffects) {
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 20, 0.3, 0.3, 0.3, 0.05);
        }

        // 音效
        if (enableSoundEffects) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }
    }

    // ==================== 菜单打开动画 ====================

    /**
     * 播放菜单打开动画（缩放+粒子效果）
     */
    public void playMenuOpenAnimation(Player player, String menuName) {
        if (!enableMenuAnimations || !enableSoundEffects) return;

        Location loc = player.getLocation();

        // 菜单打开音效
        player.playSound(loc, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);

        // 粒子光环效果
        if (enableParticleEffects) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                spawnParticleRing(player, Particle.WITCH, 15, 1.5, 0.3);
            }, 2L);
        }

        // ActionBar 提示
        player.sendActionBar(Component.text(menuName + " 已打开", NamedTextColor.GREEN));
    }

    /**
     * 播放菜单关闭动画
     */
    public void playMenuCloseAnimation(Player player) {
        if (!enableMenuAnimations || !enableSoundEffects) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 0.8f);
    }

    /**
     * 播放菜单项点击动画
     */
    public void playItemClickAnimation(Player player, ItemStack item) {
        if (!enableMenuAnimations) return;

        Location loc = player.getLocation();

        // 点击音效
        if (enableSoundEffects) {
            player.playSound(loc, Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
        }

        // 粒子效果
        if (enableParticleEffects) {
            spawnParticleBurst(player, Particle.COMPOSTER, 8, 0.3);
        }
    }

    // ==================== 过渡动画 ====================

    /**
     * 播放页面切换过渡动画
     */
    public void playPageTransition(Player player, String fromPage, String toPage, Consumer<Void> onMidpoint) {
        if (!enableTransitionEffects) {
            onMidpoint.accept(null);
            return;
        }

        Location loc = player.getLocation();

        // 第一阶段：淡出效果
        player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);

        if (enableParticleEffects) {
            spawnParticleSwirl(player, Particle.PORTAL, 10, 0.5);
        }

        // 中间点执行操作
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            onMidpoint.accept(null);

            // 第二阶段：淡入效果
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.2f);

                if (enableParticleEffects) {
                    spawnParticleSwirl(player, Particle.ASH, 10, 0.5);
                }

                // ActionBar 显示页面信息
                player.sendActionBar(Component.text(
                    ">> " + toPage,
                    NamedTextColor.GOLD
                ));
            }, 2L);
        }, 5L);
    }

    /**
     * 播放平滑过渡动画（多个步骤）
     */
    public void playSmoothTransition(Player player, List<String> steps, Consumer<Integer> onStep) {
        if (!enableTransitionEffects || steps.isEmpty()) {
            if (!steps.isEmpty()) onStep.accept(0);
            return;
        }

        AtomicInteger currentStep = new AtomicInteger(0);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int step = currentStep.getAndIncrement();

            if (step >= steps.size()) {
                return;
            }

            // 显示当前步骤
            player.sendActionBar(Component.text(
                "[" + (step + 1) + "/" + steps.size() + "] " + steps.get(step),
                NamedTextColor.YELLOW
            ));

            // 播放音效
            if (enableSoundEffects) {
                player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.2f, 1.3f);
            }

            // 执行步骤回调
            onStep.accept(step);

        }, 0L, 15L); // 每15tick(0.75秒)执行一步

        activeAnimations.put(player.getUniqueId(), task);

        // 完成后的清理
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeAnimations.remove(player.getUniqueId());
            player.sendActionBar(Component.text("完成!", NamedTextColor.GREEN));
            if (enableSoundEffects) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
            }
        }, steps.size() * 15L + 10L);
    }

    // ==================== 加载动画 ====================

    /**
     * 播放环形加载动画
     */
    public void playLoadingAnimation(Player player, String message, int durationSeconds, Consumer<Void> onComplete) {
        AtomicInteger ticks = new AtomicInteger(0);
        int totalTicks = durationSeconds * 20;
        String[] spinner = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int current = ticks.getAndIncrement();

            if (current >= totalTicks) {
                onComplete.accept(null);
                return;
            }

            int spinnerIndex = (current / 2) % spinner.length;
            int remaining = (totalTicks - current) / 20;

            player.sendActionBar(Component.text(
                spinner[spinnerIndex] + " " + message + " (" + remaining + "s)",
                NamedTextColor.YELLOW
            ));

            // 周期性粒子效果
            if (current % 10 == 0 && enableParticleEffects) {
                Location loc = player.getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.2, 0.2, 0.2, 0.01);
            }

        }, 0L, 1L);

        activeAnimations.put(player.getUniqueId(), task);
    }

    /**
     * 播放进度条加载动画
     */
    public void playProgressLoadingAnimation(Player player, String message, int totalSteps, Consumer<Integer> onStep) {
        AtomicInteger currentStep = new AtomicInteger(0);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int step = currentStep.getAndIncrement();

            if (step >= totalSteps) {
                activeAnimations.remove(player.getUniqueId());
                return;
            }

            // 执行步骤
            onStep.accept(step);

            // 显示进度条
            String progressBar = createProgressBar(step, totalSteps, 20);
            player.sendActionBar(Component.text(
                message + " " + progressBar + " " + (step * 100 / totalSteps) + "%",
                NamedTextColor.GOLD
            ));

            // 音效反馈
            if (enableSoundEffects && step % 5 == 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.1f, 1.0f);
            }

        }, 0L, 3L); // 每3tick执行一步

        activeAnimations.put(player.getUniqueId(), task);
    }

    /**
     * 播放骨架加载动画（逐个填充物品槽）
     */
    public void playSkeletonLoadingAnimation(Player player, List<Integer> targetSlots, int intervalTicks) {
        Set<Integer> filledSlots = new CopyOnWriteArraySet<>();
        AtomicInteger currentIndex = new AtomicInteger(0);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int index = currentIndex.getAndIncrement();

            if (index >= targetSlots.size()) {
                activeAnimations.remove(player.getUniqueId());
                // 完成后播放成功效果
                if (enableSoundEffects) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.2f);
                }
                return;
            }

            filledSlots.add(targetSlots.get(index));

            // 这里需要通过事件或回调来更新实际GUI
            // 由于GUI更新的复杂性，这里仅提供加载指示

        }, 0L, intervalTicks);

        activeAnimations.put(player.getUniqueId(), task);
    }

    // ==================== 粒子特效 ====================

    /**
     * 生成环形粒子
     */
    private void spawnParticleRing(Player player, Particle particle, int count, double radius, double height) {
        Location loc = player.getLocation();

        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, height, z);
            loc.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 生成爆发粒子效果
     */
    private void spawnParticleBurst(Player player, Particle particle, int count, double radius) {
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(particle, loc, count, radius, radius, radius, 0.05);
    }

    /**
     * 生成漩涡粒子效果
     */
    private void spawnParticleSwirl(Player player, Particle particle, int count, double radius) {
        Location loc = player.getLocation();

        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double yOffset = (i % 5) * 0.2;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, 1 + yOffset, z);
            loc.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 播放粒子特效（根据材质类型）
     */
    public void playMaterialParticleEffect(Player player, Material material) {
        if (!enableParticleEffects) return;

        Location loc = player.getLocation().add(0, 1, 0);

        Particle particle = switch (material) {
            case GOLD_INGOT, GOLD_BLOCK -> Particle.FLAME;
            case DIAMOND, DIAMOND_BLOCK -> Particle.END_ROD;
            case EMERALD, EMERALD_BLOCK -> Particle.HAPPY_VILLAGER;
            case IRON_INGOT, IRON_BLOCK -> Particle.DUST;
            case REDSTONE, REDSTONE_BLOCK -> Particle.DUST;
            case LAPIS_LAZULI, LAPIS_BLOCK -> Particle.NOTE;
            case NETHER_STAR -> Particle.TOTEM_OF_UNDYING;
            case ENDER_PEARL -> Particle.PORTAL;
            case BLAZE_POWDER -> Particle.FLAME;
            case SUGAR -> Particle.CLOUD;
            case GHAST_TEAR -> Particle.ENCHANTED_HIT;
            default -> Particle.HAPPY_VILLAGER;
        };

        player.getWorld().spawnParticle(particle, loc, 10, 0.3, 0.3, 0.3, 0.05);
    }

    /**
     * 播放稀有物品获得特效
     */
    public void playRareItemEffect(Player player, String itemName) {
        Location loc = player.getLocation();

        // Title 动画
        player.showTitle(Title.title(
            Component.text("* ", NamedTextColor.GOLD).append(Component.text(itemName, NamedTextColor.GOLD))
                .decorate(TextDecoration.BOLD),
            Component.text("Rare item obtained!", NamedTextColor.YELLOW),
            Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
            )
        ));

        // 豪华粒子效果
        if (enableParticleEffects) {
            spawnParticleRing(player, Particle.FLAME, 20, 1.0, 0.5);
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }

        // 音效
        if (enableSoundEffects) {
            player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
    }

    // ==================== 声音反馈 ====================

    /**
     * 播放成功音效
     */
    public void playSuccessSound(Player player) {
        if (!enableSoundEffects) return;
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    /**
     * 播放失败音效
     */
    public void playFailureSound(Player player) {
        if (!enableSoundEffects) return;
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.8f);
    }

    /**
     * 播放警告音效
     */
    public void playWarningSound(Player player) {
        if (!enableSoundEffects) return;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
    }

    /**
     * 播放奖励音效
     */
    public void playRewardSound(Player player) {
        if (!enableSoundEffects) return;
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.3f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.3f, 1.5f);
        }, 5L);
    }

    /**
     * 播放菜单导航音效
     */
    public void playNavigateSound(Player player) {
        if (!enableSoundEffects) return;
        player.playSound(player.getLocation(), Sound.UI_LOOM_SELECT_PATTERN, 0.3f, 1.2f);
    }

    /**
     * 播放选中音效
     */
    public void playSelectSound(Player player) {
        if (!enableSoundEffects) return;
        player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.4f, 1.3f);
    }

    // ==================== 进度条 ====================

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

    // ==================== 清理方法 ====================

    /**
     * 取消玩家的所有动画
     */
    public void cancelPlayerAnimations(Player player) {
        BukkitTask task = activeAnimations.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 取消所有动画
     */
    public void cancelAllAnimations() {
        activeAnimations.values().forEach(BukkitTask::cancel);
        activeAnimations.clear();
    }

    public boolean isTransitionEnabled() { return enableTransitionEffects; }
}
