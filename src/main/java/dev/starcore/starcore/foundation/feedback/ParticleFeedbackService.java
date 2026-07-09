package dev.starcore.starcore.foundation.feedback;

import java.util.concurrent.ThreadLocalRandom;
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

/**
 * 粒子特效反馈服务
 * 提供各种操作反馈的粒子效果，包括成功、失败、奖励、警告等
 */
public final class ParticleFeedbackService {
    private final Plugin plugin;
    private final Map<UUID, BukkitTask> activeFeedbacks = new ConcurrentHashMap<>();

    public ParticleFeedbackService(Plugin plugin) {
        this.plugin = plugin;
    }

    // ==================== 反馈类型枚举 ====================

    /**
     * 反馈类型
     */
    public enum FeedbackType {
        /** 成功反馈 - 绿色粒子爆发 */
        SUCCESS,
        /** 失败反馈 - 红色烟雾 */
        FAILURE,
        /** 警告反馈 - 橙色闪烁 */
        WARNING,
        /** 奖励反馈 - 金色光芒 */
        REWARD,
        /** 稀有奖励 - 彩虹光效 */
        RARE_REWARD,
        /** 升级反馈 - 紫色魔法 */
        UPGRADE,
        /** 治疗反馈 - 红色心形 */
        HEAL,
        /** 能量反馈 - 蓝色电弧 */
        ENERGY,
        /** 火焰反馈 - 橙红色 */
        FIRE,
        /** 冰冻反馈 - 白色雪花 */
        FROST,
        /** 魔法反馈 - 紫色星尘 */
        MAGIC,
        /** 自然反馈 - 绿色树叶 */
        NATURE,
        /** 爆炸反馈 - 火焰烟雾 */
        EXPLOSION,
        /** 水波反馈 - 蓝色气泡 */
        WATER,
        /** 毒液反馈 - 绿色滴落 */
        POISON,
        /** 闪电反馈 - 白色闪光 */
        LIGHTNING,
        /** 心脏跳动反馈 */
        HEARTBEAT,
        /** 传送反馈 - 紫黑粒子 */
        TELEPORT,
        /** 建造反馈 - 棕色方块 */
        BUILD,
        /** 破坏反馈 - 灰色碎片 */
        BREAK,
        /** 旗帜反馈 - 多色旗帜 */
        BANNER,
        /** 皇冠反馈 - 金色皇冠 */
        CROWN,
        /** 星星反馈 - 白色闪烁 */
        STARS,
        /** 灵魂反馈 - 蓝色灵魂 */
        SOUL,
        /** 凋零反馈 - 黑色烟雾 */
        WITHER,
        /** 龙息反馈 - 紫红粒子 */
        DRAGON_BREATH,
        /** 下界反馈 - 红色火焰 */
        NETHER,
        /** 末地反馈 - 紫色星光 */
        END
    }

    /**
     * 反馈强度
     */
    public enum FeedbackIntensity {
        LOW(0.5, 10, 0.3f),
        MEDIUM(1.0, 20, 0.6f),
        HIGH(1.5, 40, 1.0f),
        EPIC(2.0, 60, 1.5f);

        private final double particleMultiplier;
        private final int baseParticleCount;
        private final float soundVolume;

        FeedbackIntensity(double particleMultiplier, int baseParticleCount, float soundVolume) {
            this.particleMultiplier = particleMultiplier;
            this.baseParticleCount = baseParticleCount;
            this.soundVolume = soundVolume;
        }

        public double getParticleMultiplier() { return particleMultiplier; }
        public int getBaseParticleCount() { return baseParticleCount; }
        public float getSoundVolume() { return soundVolume; }
    }

    // ==================== 基础反馈方法 ====================

    /**
     * 播放反馈效果
     */
    public void playFeedback(Player player, FeedbackType type) {
        playFeedback(player, type, FeedbackIntensity.MEDIUM, null);
    }

    /**
     * 播放反馈效果（带消息）
     */
    public void playFeedback(Player player, FeedbackType type, String message) {
        playFeedback(player, type, FeedbackIntensity.MEDIUM, message);
    }

    /**
     * 播放反馈效果（带强度）
     */
    public void playFeedback(Player player, FeedbackType type, FeedbackIntensity intensity) {
        playFeedback(player, type, intensity, null);
    }

    /**
     * 播放反馈效果（完整参数）
     */
    public void playFeedback(Player player, FeedbackType type, FeedbackIntensity intensity, String message) {
        if (player == null || !player.isOnline()) return;

        Location loc = player.getLocation().add(0, 1, 0);

        // 播放粒子效果
        spawnFeedbackParticles(player.getWorld(), loc, type, intensity);

        // 播放音效
        playFeedbackSound(player, type, intensity);

        // 显示消息
        if (message != null && !message.isEmpty()) {
            showFeedbackMessage(player, type, message);
        }
    }

    /**
     * 播放反馈效果（带回调）
     */
    public void playFeedback(Player player, FeedbackType type, FeedbackIntensity intensity,
                            String message, Runnable onComplete) {
        playFeedback(player, type, intensity, message);

        if (onComplete != null) {
            int delay = getFeedbackDuration(type, intensity);
            Bukkit.getScheduler().runTaskLater(plugin, onComplete, delay);
        }
    }

    // ==================== 持续反馈效果 ====================

    /**
     * 播放持续反馈效果（循环播放直到取消）
     */
    public void startContinuousFeedback(Player player, FeedbackType type) {
        startContinuousFeedback(player, type, FeedbackIntensity.MEDIUM, 20); // 每秒一次
    }

    /**
     * 播放持续反馈效果（自定义间隔）
     */
    public void startContinuousFeedback(Player player, FeedbackType type, FeedbackIntensity intensity, int intervalTicks) {
        if (player == null || !player.isOnline()) return;

        // 取消之前的持续反馈
        stopContinuousFeedback(player);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    activeFeedbacks.remove(player.getUniqueId());
                    return;
                }
                spawnFeedbackParticles(player.getWorld(), player.getLocation().add(0, 1, 0), type, intensity);
            }
        }.runTaskTimer(plugin, 0L, intervalTicks);

        activeFeedbacks.put(player.getUniqueId(), task);
    }

    /**
     * 停止玩家的持续反馈效果
     */
    public void stopContinuousFeedback(Player player) {
        BukkitTask task = activeFeedbacks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 停止所有持续反馈效果
     */
    public void stopAllContinuousFeedback() {
        activeFeedbacks.values().forEach(BukkitTask::cancel);
        activeFeedbacks.clear();
    }

    // ==================== 特殊反馈效果 ====================

    /**
     * 播放奖励序列效果（多个阶段）
     */
    public void playRewardSequence(Player player, int itemCount, Runnable onComplete) {
        if (player == null || !player.isOnline()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // 第一阶段：金色粒子环绕
        player.showTitle(Title.title(
            Component.text("★ ", NamedTextColor.GOLD).append(Component.text("奖励!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)),
            Component.empty(),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))
        ));

        Location loc = player.getLocation().add(0, 1.5, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);

        // 环绕粒子
        spawnRewardOrbit(player.getWorld(), loc, itemCount);

        if (onComplete != null) {
            Bukkit.getScheduler().runTaskLater(plugin, onComplete, 30L);
        }
    }

    /**
     * 播放升级序列效果
     */
    public void playUpgradeSequence(Player player, String upgradeName, Runnable onComplete) {
        if (player == null || !player.isOnline()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // 显示升级标题
        player.showTitle(Title.title(
            Component.text("▲ ", NamedTextColor.DARK_PURPLE).append(Component.text("升级!", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)),
            Component.text(upgradeName, NamedTextColor.DARK_PURPLE),
            Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
            )
        ));

        Location loc = player.getLocation().add(0, 1, 0);

        // 紫色魔法粒子螺旋上升
        spawnUpgradeSpiral(player.getWorld(), loc);

        // 升级音效
        player.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.3f);
            if (onComplete != null) onComplete.run();
        }, 40L);
    }

    /**
     * 播放连击/计数效果
     */
    public void playComboEffect(Player player, int comboCount) {
        if (player == null || !player.isOnline()) return;

        Location loc = player.getLocation();

        // 根据连击数调整效果强度
        FeedbackIntensity intensity;
        if (comboCount >= 50) {
            intensity = FeedbackIntensity.EPIC;
        } else if (comboCount >= 20) {
            intensity = FeedbackIntensity.HIGH;
        } else if (comboCount >= 10) {
            intensity = FeedbackIntensity.MEDIUM;
        } else {
            intensity = FeedbackIntensity.LOW;
        }

        // 显示连击数
        Component comboText = Component.text()
            .append(Component.text("x" + comboCount + " ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(Component.text("COMBO!", NamedTextColor.YELLOW))
            .build();

        player.showTitle(Title.title(
            comboText,
            Component.text("连击加成!", NamedTextColor.GRAY),
            Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofSeconds(1),
                Duration.ofMillis(200)
            )
        ));

        // 粒子效果
        spawnComboParticles(player.getWorld(), loc, comboCount);

        // 音效（音调随连击数升高）
        float pitch = Math.min(2.0f, 1.0f + (comboCount / 20.0f));
        player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.3f * intensity.getSoundVolume(), pitch);
    }

    /**
     * 播放经验值获得效果
     */
    public void playXpGainEffect(Player player, int amount) {
        if (player == null || !player.isOnline()) return;

        Location loc = player.getLocation().add(0, 1, 0);

        // 绿色粒子上升
        for (int i = 0; i < Math.min(amount / 10, 10); i++) {
            double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5;
            double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5;
            Location particleLoc = loc.clone().add(x, 0, z);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, particleLoc, 1, 0, 0.1, 0, 0.05);
        }

        // 显示经验值
        player.sendActionBar(Component.text()
            .append(Component.text("+" + amount + " XP", NamedTextColor.AQUA))
            .build()
        );

        player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2f, 1.2f);
    }

    /**
     * 播放货币获得效果
     */
    public void playMoneyGainEffect(Player player, double amount, String currencyName) {
        if (player == null || !player.isOnline()) return;

        Location loc = player.getLocation();

        // 金色粒子
        player.getWorld().spawnParticle(Particle.END_ROD, loc.add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.02);

        // 显示获得货币
        Component moneyText = Component.text()
            .append(Component.text("+" + String.format("%.2f", amount), NamedTextColor.GOLD))
            .append(Component.text(" " + currencyName, NamedTextColor.YELLOW))
            .build();

        player.showTitle(Title.title(
            Component.text("$ ", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
            moneyText,
            Title.Times.times(
                Duration.ofMillis(200),
                Duration.ofSeconds(1),
                Duration.ofMillis(300)
            )
        ));

        player.playSound(loc, Sound.ENTITY_VILLAGER_TRADE, 0.3f, 1.2f);
    }

    // ==================== 粒子生成辅助方法 ====================

    private void spawnFeedbackParticles(org.bukkit.World world, Location loc, FeedbackType type, FeedbackIntensity intensity) {
        int count = (int) (intensity.getBaseParticleCount() * intensity.getParticleMultiplier());
        double spread = intensity.getParticleMultiplier() * 0.4;

        switch (type) {
            case SUCCESS -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, loc, count, spread, spread, spread, 0.05);
                world.spawnParticle(Particle.WHITE_ASH, loc, count / 3, 0.2, 0.2, 0.2, 0);
            }
            case FAILURE -> {
                world.spawnParticle(Particle.SMOKE, loc, count, spread, spread, spread, 0.03);
                world.spawnParticle(Particle.LARGE_SMOKE, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.02);
            }
            case WARNING -> {
                world.spawnParticle(Particle.FLAME, loc, count / 2, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.SMOKE, loc, count / 3, spread * 0.5, spread * 0.5, spread * 0.5, 0.02);
            }
            case REWARD -> {
                world.spawnParticle(Particle.END_ROD, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.ITEM_SNOWBALL, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
            }
            case RARE_REWARD -> {
                // 彩虹粒子
                Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.AQUA, Color.BLUE, Color.PURPLE};
                for (int i = 0; i < count; i++) {
                    int colorIndex = i % colors.length;
                    Color color = colors[colorIndex];
                    spawnColoredDust(world, loc, color, 1);
                }
                world.spawnParticle(Particle.WHITE_ASH, loc, count / 4, 0.3, 0.3, 0.3, 0);
            }
            case UPGRADE -> {
                world.spawnParticle(Particle.ENCHANTED_HIT, loc, count, spread, spread, spread, 0.05);
                world.spawnParticle(Particle.WITCH, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.02);
                world.spawnParticle(Particle.END_ROD, loc, count / 3, spread * 0.3, spread * 0.3, spread * 0.3, 0.01);
            }
            case HEAL -> {
                world.spawnParticle(Particle.HEART, loc, count, spread * 0.5, spread * 0.5, spread * 0.5, 0);
                world.spawnParticle(Particle.HAPPY_VILLAGER, loc, count / 2, spread * 0.3, spread * 0.3, spread * 0.3, 0.02);
            }
            case ENERGY -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, count, spread, spread, spread, 0.05);
                world.spawnParticle(Particle.WHITE_ASH, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.02);
            }
            case FIRE -> {
                world.spawnParticle(Particle.FLAME, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.LAVA, loc, count / 2, spread * 0.3, spread * 0.3, spread * 0.3, 0);
                world.spawnParticle(Particle.SMOKE, loc, count / 3, spread * 0.4, spread * 0.4, spread * 0.4, 0.02);
            }
            case FROST -> {
                world.spawnParticle(Particle.SNOWFLAKE, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.SNOWFLAKE, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0);
                world.spawnParticle(Particle.BLOCK, loc, count / 4, spread * 0.3, spread * 0.3, spread * 0.3, 0.02,
                    org.bukkit.Material.ICE.createBlockData());
            }
            case MAGIC -> {
                world.spawnParticle(Particle.WITCH, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.ENCHANTED_HIT, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.03);
                world.spawnParticle(Particle.SOUL, loc, count / 3, spread * 0.3, spread * 0.3, spread * 0.3, 0.01);
            }
            case NATURE -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.ITEM_SNOWBALL, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
                world.spawnParticle(Particle.CHERRY_LEAVES, loc, count / 3, spread * 0.4, spread * 0.4, spread * 0.4, 0.01);
            }
            // ==================== 新增粒子类型 ====================
            case EXPLOSION -> {
                world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.5, 0.5, 0.5, 0);
                world.spawnParticle(Particle.FLAME, loc, count, spread, spread, spread, 0.03);
                world.spawnParticle(Particle.SMOKE, loc, count, spread * 0.8, spread * 0.8, spread * 0.8, 0.02);
                world.spawnParticle(Particle.LAVA, loc, count / 3, spread * 0.5, spread * 0.3, spread * 0.5, 0);
            }
            case WATER -> {
                world.spawnParticle(Particle.BUBBLE, loc, count, spread * 0.6, spread * 0.6, spread * 0.6, 0.02);
                world.spawnParticle(Particle.BUBBLE_COLUMN_UP, loc, count / 2, spread * 0.3, 0.5, spread * 0.3, 0.01);
                world.spawnParticle(Particle.DRIPPING_WATER, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.02);
            }
            case POISON -> {
                world.spawnParticle(Particle.ITEM_SNOWBALL, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.SNEEZE, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
                world.spawnParticle(Particle.ITEM_SLIME, loc, count / 3, spread * 0.4, spread * 0.6, spread * 0.4, 0.01);
            }
            case LIGHTNING -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, count * 2, spread * 1.2, spread * 1.5, spread * 1.2, 0.1);
                world.spawnParticle(Particle.WHITE_ASH, loc, count, 0.5, 0.5, 0.5, 0);
                world.spawnParticle(Particle.FLASH, loc, 3, 0, 0, 0, 0);
            }
            case HEARTBEAT -> {
                world.spawnParticle(Particle.HEART, loc, count, spread * 0.3, spread * 0.3, spread * 0.3, 0);
                world.spawnParticle(Particle.DUST, loc, count / 2, spread * 0.2, spread * 0.2, spread * 0.2, 0,
                    new org.bukkit.Particle.DustOptions(Color.RED, 1.0f));
            }
            case TELEPORT -> {
                world.spawnParticle(Particle.PORTAL, loc, count, spread, spread, spread, 0.05);
                world.spawnParticle(Particle.REVERSE_PORTAL, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.02);
                world.spawnParticle(Particle.WHITE_ASH, loc, count / 3, spread * 0.3, spread * 0.3, spread * 0.3, 0);
            }
            case BUILD -> {
                world.spawnParticle(Particle.BLOCK, loc, count, spread * 0.5, spread * 0.5, spread * 0.5, 0.02,
                    org.bukkit.Material.BROWN_CONCRETE.createBlockData());
                world.spawnParticle(Particle.ITEM_SNOWBALL, loc, count / 2, spread * 0.3, spread * 0.3, spread * 0.3, 0.01);
            }
            case BREAK -> {
                world.spawnParticle(Particle.BLOCK, loc, count * 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.03,
                    org.bukkit.Material.GRAY_CONCRETE.createBlockData());
                world.spawnParticle(Particle.ITEM, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01,
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.COBBLESTONE));
            }
            case BANNER -> {
                Color[] bannerColors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE, Color.PURPLE};
                for (int i = 0; i < count; i++) {
                    int colorIndex = i % bannerColors.length;
                    spawnColoredDust(world, loc, bannerColors[colorIndex], 1);
                }
                world.spawnParticle(Particle.END_ROD, loc, count / 3, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
            }
            case CROWN -> {
                world.spawnParticle(Particle.END_ROD, loc, count, spread, spread, spread, 0.01);
                world.spawnParticle(Particle.ITEM_SNOWBALL, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
                spawnColoredDust(world, loc, Color.fromRGB(255, 215, 0), count / 4);
            }
            case STARS -> {
                world.spawnParticle(Particle.END_ROD, loc, count, spread * 0.3, spread * 0.5, spread * 0.3, 0.005);
                world.spawnParticle(Particle.NAUTILUS, loc, count / 2, spread * 0.4, spread * 0.6, spread * 0.4, 0.02);
                spawnColoredDust(world, loc, Color.WHITE, count / 4);
            }
            case SOUL -> {
                world.spawnParticle(Particle.SOUL, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
                world.spawnParticle(Particle.WHITE_ASH, loc, count / 3, spread * 0.3, spread * 0.3, spread * 0.3, 0);
            }
            case WITHER -> {
                world.spawnParticle(Particle.SMOKE, loc, count, spread, spread, spread, 0.03);
                world.spawnParticle(Particle.SOUL, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.02);
                world.spawnParticle(Particle.ASH, loc, count, spread * 0.8, spread * 0.8, spread * 0.8, 0.01);
                spawnColoredDust(world, loc, Color.fromRGB(50, 50, 50), count / 4);
            }
            case DRAGON_BREATH -> {
                world.spawnParticle(Particle.DRAGON_BREATH, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.END_ROD, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
                world.spawnParticle(Particle.SMOKE, loc, count / 3, spread * 0.4, spread * 0.4, spread * 0.4, 0.02);
            }
            case NETHER -> {
                world.spawnParticle(Particle.FLAME, loc, count, spread, spread, spread, 0.02);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
                world.spawnParticle(Particle.SMOKE, loc, count / 3, spread * 0.4, spread * 0.4, spread * 0.4, 0.02);
                spawnColoredDust(world, loc, Color.fromRGB(200, 50, 0), count / 4);
            }
            case END -> {
                world.spawnParticle(Particle.PORTAL, loc, count, spread, spread, spread, 0.05);
                world.spawnParticle(Particle.END_ROD, loc, count / 2, spread * 0.5, spread * 0.5, spread * 0.5, 0.01);
                world.spawnParticle(Particle.REVERSE_PORTAL, loc, count / 3, spread * 0.3, spread * 0.3, spread * 0.3, 0.01);
                spawnColoredDust(world, loc, Color.fromRGB(180, 0, 255), count / 4);
            }
        }
    }

    private void spawnColoredDust(org.bukkit.World world, Location loc, Color color, int count) {
        // 使用 DUST 粒子配合颜色
        world.spawnParticle(Particle.DUST, loc, count, 0.1, 0.1, 0.1, 0,
            new org.bukkit.Particle.DustOptions(color, 1.0f));
    }

    private void spawnRewardOrbit(org.bukkit.World world, Location loc, int itemCount) {
        for (int i = 0; i < 20; i++) {
            double angle = 2 * Math.PI * i / 20;
            double x = Math.cos(angle) * 1.0;
            double z = Math.sin(angle) * 1.0;
            Location particleLoc = loc.clone().add(x, 0.5, z);
            world.spawnParticle(Particle.END_ROD, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void spawnUpgradeSpiral(org.bukkit.World world, Location loc) {
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 40;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                double angle = ticks * 0.2;
                double radius = 0.3 + (ticks * 0.02);
                double y = ticks * 0.05;

                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location particleLoc = loc.clone().add(x, 1.0 + y, z);
                world.spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.ENCHANTED_HIT, particleLoc, 1, 0.05, 0.05, 0.05, 0.01);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnComboParticles(org.bukkit.World world, Location loc, int comboCount) {
        int intensity = Math.min(comboCount / 5, 10);
        for (int i = 0; i < intensity; i++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double radius = ThreadLocalRandom.current().nextDouble() * 1.0;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = loc.clone().add(x, 1.0 + ThreadLocalRandom.current().nextDouble(), z);
            world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    // ==================== 音效辅助方法 ====================

    private void playFeedbackSound(Player player, FeedbackType type, FeedbackIntensity intensity) {
        Location loc = player.getLocation();
        float volume = intensity.getSoundVolume();

        switch (type) {
            case SUCCESS -> player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume, 1.2f);
            case FAILURE -> player.playSound(loc, Sound.ENTITY_VILLAGER_NO, volume, 0.8f);
            case WARNING -> player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, volume, 0.7f);
            case REWARD -> {
                player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, volume, 1.3f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(loc, Sound.BLOCK_BELL_USE, volume * 0.5f, 1.5f);
                    }
                }, 5L);
            }
            case RARE_REWARD -> {
                player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, volume, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, volume, 1.5f);
                    }
                }, 10L);
            }
            case UPGRADE -> player.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, volume, 1.5f);
            case HEAL -> player.playSound(loc, Sound.ENTITY_GENERIC_DRINK, volume * 0.5f, 1.3f);
            case ENERGY -> player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, volume * 0.5f, 1.2f);
            case FIRE -> player.playSound(loc, Sound.ITEM_FIRECHARGE_USE, volume * 0.5f, 1.0f);
            case FROST -> player.playSound(loc, Sound.BLOCK_GLASS_BREAK, volume * 0.3f, 1.5f);
            case MAGIC -> player.playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, volume, 1.4f);
            case NATURE -> player.playSound(loc, Sound.BLOCK_GRASS_PLACE, volume * 0.5f, 1.2f);
            // ==================== 新增音效 ====================
            case EXPLOSION -> {
                player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, volume, 0.9f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(loc, Sound.ENTITY_TNT_PRIMED, volume * 0.3f, 0.8f);
                    }
                }, 3L);
            }
            case WATER -> player.playSound(loc, Sound.ENTITY_GENERIC_SPLASH, volume * 0.5f, 1.2f);
            case POISON -> player.playSound(loc, Sound.ENTITY_WITCH_DRINK, volume * 0.3f, 0.8f);
            case LIGHTNING -> {
                player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, volume, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, volume * 0.5f, 0.7f);
                    }
                }, 2L);
            }
            case HEARTBEAT -> player.playSound(loc, Sound.ENTITY_WITHER_HURT, volume * 0.2f, 0.5f);
            case TELEPORT -> player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, volume * 0.5f, 1.0f);
            case BUILD -> player.playSound(loc, Sound.BLOCK_STONE_PLACE, volume * 0.5f, 1.0f);
            case BREAK -> player.playSound(loc, Sound.BLOCK_STONE_BREAK, volume * 0.5f, 1.0f);
            case BANNER -> {
                player.playSound(loc, Sound.ENTITY_ARMOR_STAND_PLACE, volume * 0.5f, 1.2f);
                player.playSound(loc, Sound.ENTITY_ITEM_PICKUP, volume * 0.3f, 1.5f);
            }
            case CROWN -> {
                player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, volume, 1.5f);
                player.playSound(loc, Sound.BLOCK_BELL_USE, volume * 0.3f, 1.8f);
            }
            case STARS -> player.playSound(loc, Sound.ENTITY_STRAY_AMBIENT, volume * 0.3f, 1.5f);
            case SOUL -> player.playSound(loc, Sound.BLOCK_SOUL_SAND_PLACE, volume * 0.4f, 0.9f);
            case WITHER -> player.playSound(loc, Sound.ENTITY_WITHER_HURT, volume * 0.6f, 0.6f);
            case DRAGON_BREATH -> {
                player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_SHOOT, volume * 0.5f, 0.8f);
                player.playSound(loc, Sound.ENTITY_WITHER_HURT, volume * 0.3f, 0.5f);
            }
            case NETHER -> player.playSound(loc, Sound.BLOCK_NETHER_BRICKS_PLACE, volume * 0.4f, 0.9f);
            case END -> player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, volume * 0.4f, 1.2f);
        }
    }

    // ==================== 消息显示辅助方法 ====================

    private void showFeedbackMessage(Player player, FeedbackType type, String message) {
        NamedTextColor color = switch (type) {
            case SUCCESS -> NamedTextColor.GREEN;
            case FAILURE -> NamedTextColor.RED;
            case WARNING -> NamedTextColor.YELLOW;
            case REWARD, RARE_REWARD, CROWN -> NamedTextColor.GOLD;
            case UPGRADE -> NamedTextColor.DARK_PURPLE;
            case HEAL, HEARTBEAT -> NamedTextColor.RED;
            case ENERGY -> NamedTextColor.AQUA;
            case FIRE, NETHER, DRAGON_BREATH -> NamedTextColor.GOLD;
            case FROST -> NamedTextColor.WHITE;
            case MAGIC -> NamedTextColor.DARK_PURPLE;
            case NATURE -> NamedTextColor.GREEN;
            // ==================== 新增颜色映射 ====================
            case EXPLOSION -> NamedTextColor.DARK_RED;
            case WATER -> NamedTextColor.BLUE;
            case POISON -> NamedTextColor.DARK_GREEN;
            case LIGHTNING -> NamedTextColor.YELLOW;
            case TELEPORT -> NamedTextColor.DARK_PURPLE;
            case BUILD -> NamedTextColor.GRAY;
            case BREAK -> NamedTextColor.GRAY;
            case BANNER -> NamedTextColor.DARK_RED;
            case STARS -> NamedTextColor.WHITE;
            case SOUL -> NamedTextColor.AQUA;
            case WITHER -> NamedTextColor.DARK_GRAY;
            case END -> NamedTextColor.DARK_PURPLE;
        };

        player.sendMessage(Component.text()
            .append(Component.text("► ", color))
            .append(Component.text(message, color))
            .build());
    }

    // ==================== 工具方法 ====================

    private int getFeedbackDuration(FeedbackType type, FeedbackIntensity intensity) {
        return switch (type) {
            case SUCCESS, FAILURE, BREAK, BUILD -> 15;
            case WARNING, LIGHTNING, HEARTBEAT -> 20;
            case REWARD, WATER -> 30;
            case RARE_REWARD, TELEPORT, BANNER -> 40;
            case UPGRADE, DRAGON_BREATH, END -> 50;
            case POISON, WITHER, NETHER -> 35;
            case CROWN, STARS -> 60;
            case SOUL -> 45;
            default -> (int) (25 * intensity.getParticleMultiplier());
        };
    }
}