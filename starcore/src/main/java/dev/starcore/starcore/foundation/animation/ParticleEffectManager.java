package dev.starcore.starcore.foundation.animation;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 粒子特效管理器
 * 提供各种高级粒子效果
 */
public final class ParticleEffectManager {
    private final Plugin plugin;
    private final Map<UUID, List<BukkitRunnable>> activeEffects = new ConcurrentHashMap<>();

    // 预设粒子效果配置
    public enum ParticlePreset {
        HEART(Particle.HEART, 10, 0.5, 0.5, 0.5, 0.02, null),
        MAGIC(Particle.WITCH, 15, 0.3, 0.3, 0.3, 0.01, null),
        FIRE(Particle.FLAME, 20, 0.3, 0.3, 0.3, 0.02, null),
        SPARKLE(Particle.END_ROD, 15, 0.2, 0.2, 0.2, 0.01, null),
        RAINBOW(Particle.NOTE, 10, 0.3, 0.3, 0.3, 0.01, null),
        EXPLOSION(Particle.EXPLOSION_EMITTER, 3, 0, 0, 0, 0, null),
        SMOKE(Particle.LARGE_SMOKE, 15, 0.4, 0.4, 0.4, 0.02, null),
        CRITICAL(Particle.CRIT, 20, 0.5, 0.5, 0.5, 0.1, null),
        ENCHANT(Particle.ENCHANTED_HIT, 20, 0.5, 0.5, 0.5, 0.05, null),
        SLIME(Particle.ITEM_SNOWBALL, 10, 0.4, 0.4, 0.4, 0.02, null),
        SNOW(Particle.SNOWFLAKE, 15, 0.3, 0.3, 0.3, 0.02, null),
        WATER(Particle.SPLASH, 12, 0.3, 0.3, 0.3, 0.02, null),
        LAVA(Particle.LAVA, 10, 0.3, 0.3, 0.3, 0.02, null),
        PORTAL(Particle.PORTAL, 30, 0.5, 1.0, 0.5, 0.1, null),
        HAPPY(Particle.HAPPY_VILLAGER, 20, 0.4, 0.4, 0.4, 0.02, null),
        ANGRY(Particle.ANGRY_VILLAGER, 15, 0.3, 0.3, 0.3, 0.05, null),

        // 战斗专用
        ATTACK_SLASH(Particle.SWEEP_ATTACK, 15, 0.5, 0.3, 0.5, 0.1, null),
        SHIELD_BLOCK(Particle.BLOCK, 20, 0.4, 0.4, 0.4, 0.05, Material.IRON_BLOCK),
        BATTLE_CRIT(Particle.CRIT, 30, 0.6, 0.6, 0.6, 0.15, null),
        VICTORY_BURST(Particle.FIREWORK, 10, 0.8, 0.8, 0.8, 0.3, null),
        DEFEAT_FADE(Particle.ASH, 20, 0.5, 0.5, 0.5, 0.1, null),
        BATTLE_CLOUD(Particle.WARPED_SPORE, 25, 0.6, 0.6, 0.6, 0.08, null),
        VICTORY_GLORY(Particle.FLASH, 5, 0, 0, 0, 0, null),
        COMBAT_SMOKE(Particle.LAVA, 15, 0.4, 0.4, 0.4, 0.02, null);

        private final Particle particle;
        private final int count;
        private final double offsetX, offsetY, offsetZ, speed;
        private final Material material;

        ParticlePreset(Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed, Material material) {
            this.particle = particle;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.speed = speed;
            this.material = material;
        }

        public Particle getParticle() { return particle; }
        public int getCount() { return count; }
        public double getOffsetX() { return offsetX; }
        public double getOffsetY() { return offsetY; }
        public double getOffsetZ() { return offsetZ; }
        public double getSpeed() { return speed; }
        public Material getMaterial() { return material; }
    }

    public ParticleEffectManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 播放预设粒子效果
     */
    public void playPreset(Player player, ParticlePreset preset) {
        Location loc = player.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(
            preset.getParticle(), loc,
            preset.getCount(), preset.getOffsetX(), preset.getOffsetY(), preset.getOffsetZ(), preset.getSpeed()
        );
    }

    /**
     * 播放预设粒子效果（带回调）
     */
    public void playPreset(Player player, ParticlePreset preset, Runnable onComplete) {
        playPreset(player, preset);
        Bukkit.getScheduler().runTaskLater(plugin, () -> onComplete.run(), 10L);
    }

    /**
     * 播放预设粒子效果（指定位置）
     */
    public void playPresetAt(Player player, Location location, ParticlePreset preset) {
        Material mat = preset.getMaterial();
        if (mat != null && preset.getParticle() == Particle.BLOCK) {
            location.getWorld().spawnParticle(preset.getParticle(), location,
                preset.getCount(), preset.getOffsetX(), preset.getOffsetY(), preset.getOffsetZ(), preset.getSpeed(), mat.createBlockData());
        } else {
            location.getWorld().spawnParticle(preset.getParticle(), location,
                preset.getCount(), preset.getOffsetX(), preset.getOffsetY(), preset.getOffsetZ(), preset.getSpeed());
        }
    }

    /**
     * 播放环形粒子动画
     */
    public void playRingEffect(Player player, Particle particle, int count, double radius) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 40;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // 旋转角度
                double angle = ticks * 0.3;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location particleLoc = loc.clone().add(x, 1.0, z);
                loc.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);

                // 另一半
                Location particleLoc2 = loc.clone().add(-x, 1.0, -z);
                loc.getWorld().spawnParticle(particle, particleLoc2, 1, 0, 0, 0, 0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放螺旋上升粒子动画
     */
    public void playSpiralEffect(Player player, Particle particle, int duration) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) {
                    cancel();
                    return;
                }

                double angle = ticks * 0.2;
                double radius = 0.5 + (ticks * 0.02);
                double y = (ticks * 0.1);

                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location particleLoc = loc.clone().add(x, 1.0 + y, z);
                loc.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放心形粒子动画
     */
    public void playHeartEffect(Player player) {
        Location loc = player.getLocation().add(0, 1.5, 0);

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 60;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // 心形参数方程
                double t = ticks * 0.1;
                double x = 16 * Math.pow(Math.sin(t), 3);
                double y = 13 * Math.cos(t) - 5 * Math.cos(2*t) - 2 * Math.cos(3*t) - Math.cos(4*t);

                // 缩放和偏移
                double scale = 0.03;
                Location heartLoc = loc.clone().add(x * scale, y * scale * 0.5 + (ticks * 0.01), 0);
                loc.getWorld().spawnParticle(Particle.HEART, heartLoc, 1, 0, 0, 0, 0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放星星粒子动画
     */
    public void playStarEffect(Player player) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 80;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // 生成5个方向的粒子
                for (int i = 0; i < 5; i++) {
                    double angle = (i * 72 + ticks * 5) * Math.PI / 180;
                    double distance = ticks * 0.05;

                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;

                    Location starLoc = loc.clone().add(x, 1.0 + (ticks * 0.02), z);
                    loc.getWorld().spawnParticle(Particle.END_ROD, starLoc, 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放爆炸光环效果
     */
    public void playExplosionEffect(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        // 中心爆炸
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);

        // 外圈光环
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 20;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                double radius = ticks * 0.15;
                int particles = (int) (10 * radius);

                for (int i = 0; i < particles; i++) {
                    double angle = 2 * Math.PI * i / particles;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    Location ringLoc = loc.clone().add(x, 0, z);
                    loc.getWorld().spawnParticle(Particle.FLAME, ringLoc, 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放下雪效果
     */
    public void playSnowEffect(Player player, int duration) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) {
                    cancel();
                    return;
                }

                // 随机位置下雪
                double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
                double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
                Location snowLoc = loc.clone().add(x, 3, z);

                loc.getWorld().spawnParticle(Particle.SNOWFLAKE, snowLoc, 3, 0.1, 0.1, 0.1, 0.02);
                loc.getWorld().spawnParticle(Particle.SNOWFLAKE, snowLoc, 1, 0.05, 0.05, 0.05, 0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /**
     * 播放火焰光环效果
     */
    public void playFireHaloEffect(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 60;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // 环绕火焰
                double angle = ticks * 0.2;
                double x = Math.cos(angle) * 0.8;
                double z = Math.sin(angle) * 0.8;

                Location fireLoc = loc.clone().add(x, 0, z);
                loc.getWorld().spawnParticle(Particle.FLAME, fireLoc, 2, 0.1, 0.1, 0.1, 0.02);
                loc.getWorld().spawnParticle(Particle.LAVA, fireLoc, 1, 0.05, 0.05, 0.05, 0);

                // 头顶星光
                if (ticks % 5 == 0) {
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 2.5, 0), 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放魔法光环效果
     */
    public void playMagicAuraEffect(Player player, int duration) {
        Location loc = player.getLocation().add(0, 1, 0);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) {
                    cancel();
                    return;
                }

                // 主光环
                double angle = ticks * 0.15;
                double x = Math.cos(angle) * 1.0;
                double z = Math.sin(angle) * 1.0;

                Location auraLoc = loc.clone().add(x, Math.sin(ticks * 0.1) * 0.3, z);
                loc.getWorld().spawnParticle(Particle.WITCH, auraLoc, 1, 0, 0, 0, 0);
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, auraLoc, 1, 0.1, 0.1, 0.1, 0.02);

                // 随机火花
                if (ticks % 3 == 0) {
                    double rx = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2;
                    double rz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2;
                    loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(rx, 0.5 + ThreadLocalRandom.current().nextDouble(), rz), 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放彩虹粒子效果
     */
    public void playRainbowEffect(Player player, int duration) {
        Location loc = player.getLocation();

        Color[] colors = {
            Color.RED, Color.fromRGB(255, 165, 0), Color.YELLOW, Color.GREEN,
            Color.fromRGB(0, 255, 255), Color.BLUE, Color.PURPLE
        };

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) {
                    cancel();
                    return;
                }

                int colorIndex = ticks % colors.length;
                Color color = colors[colorIndex];

                double angle = ticks * 0.2;
                double x = Math.cos(angle) * 0.8;
                double z = Math.sin(angle) * 0.8;

                Location rainbowLoc = loc.clone().add(x, 1.0, z);

                // 使用 NOTE 粒子模拟彩色效果，通过偏移值控制颜色
                double redOffset = color.getRed() / 255.0;
                double greenOffset = color.getGreen() / 255.0;
                double blueOffset = color.getBlue() / 255.0;
                loc.getWorld().spawnParticle(Particle.NOTE, rainbowLoc, 1, redOffset, greenOffset, blueOffset, 1);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 播放上升粒子效果
     */
    public void playRisingEffect(Player player, Particle particle, int count, double height) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 40;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                double y = ticks * (height / maxTicks);

                for (int i = 0; i < count; i++) {
                    double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5;
                    double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5;
                    Location riseLoc = loc.clone().add(x, 1 + y, z);
                    loc.getWorld().spawnParticle(particle, riseLoc, 1, 0, 0, 0, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放下落粒子效果
     */
    public void playFallingEffect(Player player, Particle particle, int count) {
        Location loc = player.getLocation();

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 30;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                double y = 4 - (ticks * 4.0 / maxTicks);

                for (int i = 0; i < count; i++) {
                    double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2;
                    double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2;
                    Location fallLoc = loc.clone().add(x, y, z);
                    loc.getWorld().spawnParticle(particle, fallLoc, 1, 0.1, 0.1, 0.1, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 取消玩家的所有粒子效果
     */
    public void cancelPlayerEffects(Player player) {
        List<BukkitRunnable> effects = activeEffects.remove(player.getUniqueId());
        if (effects != null) {
            effects.forEach(BukkitRunnable::cancel);
        }
    }

    /**
     * 取消所有粒子效果
     */
    public void cancelAllEffects() {
        activeEffects.values().forEach(effects -> effects.forEach(BukkitRunnable::cancel));
        activeEffects.clear();
    }

    // ==================== 战斗专用效果 ====================

    /**
     * 播放攻击斩击效果
     */
    public void playAttackSlash(Player player) {
        Location loc = player.getLocation();
        playPresetAt(player, loc.add(0, 1, 0), ParticlePreset.ATTACK_SLASH);

        // 额外斩击粒子
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 5) { cancel(); return; }
                double offset = ticks * 0.3;
                Location slashLoc = loc.clone().add(Math.cos(player.getLocation().getYaw() * Math.PI / 180) * offset, 1.2, Math.sin(player.getLocation().getYaw() * Math.PI / 180) * offset);
                loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, slashLoc, 3, 0.2, 0.1, 0.2, 0.05);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 播放盾牌格挡效果
     */
    public void playShieldBlock(Player player) {
        Location loc = player.getLocation();
        playPresetAt(player, loc.clone().add(0, 1, 0), ParticlePreset.SHIELD_BLOCK);

        // 金属碰撞火花
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 8) { cancel(); return; }
                double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                double dist = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;
                Location sparkLoc = loc.clone().add(Math.cos(angle) * dist, 1.2, Math.sin(angle) * dist);
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, sparkLoc, 3, 0.1, 0.1, 0.1, 0.1);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /**
     * 播放心碎效果（暴击）
     */
    public void playCriticalHit(Player player) {
        Location loc = player.getLocation();
        playPresetAt(player, loc.clone().add(0, 1.5, 0), ParticlePreset.BATTLE_CRIT);

        // 暴击光芒
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 15) { cancel(); return; }
                double scale = 1.0 - (ticks * 0.06);
                for (int i = 0; i < 6; i++) {
                    double angle = i * (Math.PI * 2 / 6) + ticks * 0.2;
                    double x = Math.cos(angle) * scale;
                    double z = Math.sin(angle) * scale;
                    Location burstLoc = loc.clone().add(x, 1.5, z);
                    loc.getWorld().spawnParticle(Particle.CRIT, burstLoc, 2, 0.1, 0.1, 0.1, 0.1);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 播放骑兵冲锋效果
     */
    public void playCavalryCharge(Player player) {
        Location loc = player.getLocation().add(0, 0.5, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 30) { cancel(); return; }

                double forwardX = Math.cos(player.getLocation().getYaw() * Math.PI / 180);
                double forwardZ = Math.sin(player.getLocation().getYaw() * Math.PI / 180);

                for (int i = 0; i < 4; i++) {
                    double offset = i * 0.5;
                    Location dustLoc = loc.clone().add(forwardX * offset, 0, forwardZ * offset);
                    loc.getWorld().spawnParticle(Particle.SNOWFLAKE, dustLoc, 3, 0.2, 0.1, 0.2, 0.02);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播放战吼效果
     */
    public void playWarCry(Player player) {
        Location loc = player.getLocation().add(0, 1.5, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 20) { cancel(); return; }

                double radius = ticks * 0.2;
                for (int i = 0; i < 6; i++) {
                    double angle = i * (Math.PI * 2 / 6) + ticks * 0.3;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location soulLoc = loc.clone().add(x, Math.sin(ticks * 0.2) * 0.2, z);
                    loc.getWorld().spawnParticle(Particle.SOUL, soulLoc, 2, 0.1, 0.1, 0.1, 0.05);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 播攻城冲击效果
     */
    public void playSiegeImpact(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 20) { cancel(); return; }

                double radius = ticks * 0.3;
                int particles = (int) (8 * radius);

                for (int i = 0; i < particles; i++) {
                    double angle = i * (Math.PI * 2 / particles);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location impactLoc = loc.clone().add(x, 0, z);
                    loc.getWorld().spawnParticle(Particle.LAVA, impactLoc, 1, 0.1, 0.1, 0.1, 0.02);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playPreset(player, ParticlePreset.SMOKE);
        }, 10L);
    }

    /**
     * 播放战场迷雾效果（持续）
     */
    public void playBattleCloud(Player player) {
        Location loc = player.getLocation().add(0, 0.5, 0);

        BukkitRunnable effect = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60) { cancel(); return; }

                for (int i = 0; i < 5; i++) {
                    double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
                    double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 4;
                    Location cloudLoc = loc.clone().add(x, 0, z);
                    loc.getWorld().spawnParticle(Particle.WARPED_SPORE, cloudLoc, 2, 0.3, 0.2, 0.3, 0.02);
                }

                ticks++;
            }
        };
        effect.runTaskTimer(plugin, 0L, 3L);

        activeEffects.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(effect);
    }

    /**
     * 播放胜利爆发效果
     */
    public void playVictoryBurst(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        loc.getWorld().spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40) { cancel(); return; }

                double radius = ticks * 0.15;
                Color[] colors = {Color.fromRGB(255, 215, 0), Color.fromRGB(255, 255, 0), Color.WHITE};

                for (int i = 0; i < 12; i++) {
                    double angle = i * (Math.PI * 2 / 12);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Color color = colors[i % colors.length];

                    Location burstLoc = loc.clone().add(x, 0, z);
                    loc.getWorld().spawnParticle(Particle.END_ROD, burstLoc, 1,
                        color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0, 0.5);
                }

                if (ticks % 3 == 0) {
                    loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, ticks * 0.1, 0), 2, 0.2, 0.2, 0.2, 0.1);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playPreset(player, ParticlePreset.VICTORY_GLORY);
        }, 30L);
    }

    /**
     * 播放失败消散效果
     */
    public void playDefeatFade(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        playPresetAt(player, loc, ParticlePreset.DEFEAT_FADE);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 50) { cancel(); return; }

                for (int i = 0; i < 3; i++) {
                    double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2;
                    double y = ThreadLocalRandom.current().nextDouble() * 2;
                    double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2;
                    Location ashLoc = loc.clone().add(x, y, z);
                    loc.getWorld().spawnParticle(Particle.ASH, ashLoc, 2, 0.1, 0.1, 0.1, 0.02);
                }

                if (ticks % 10 == 0) {
                    loc.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.3, 0.3, 0.3, 0.05);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 播放战斗胜利完整效果（结合声音和粒子）
     */
    public void playBattleVictoryFull(Player player) {
        playVictoryBurst(player);
        playWarCry(player);
    }

    /**
     * 播放战斗失败完整效果
     */
    public void playBattleDefeatFull(Player player) {
        playDefeatFade(player);
    }

    /**
     * 播放行军效果
     */
    public void playMarchingBoots(Player player) {
        Location loc = player.getLocation().add(0, 0.1, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 20) { cancel(); return; }

                // 尘土粒子
                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0, 0), 3, 0.2, 0.05, 0.2, 0.02);
                loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc.clone().add((ThreadLocalRandom.current().nextDouble()-0.5)*0.5, 0.1, (ThreadLocalRandom.current().nextDouble()-0.5)*0.5), 2, 0.1, 0.05, 0.1, 0.01);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /**
     * 播放旗帜飘扬效果
     */
    public void playBattleFlag(Player player) {
        Location loc = player.getLocation().add(0, 2, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 30) { cancel(); return; }

                for (int i = 0; i < 3; i++) {
                    double wave = Math.sin(ticks * 0.3 + i * 0.5) * 0.2;
                    double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8;
                    double z = wave;
                    Location flagLoc = loc.clone().add(x, 0, z);
                    loc.getWorld().spawnParticle(Particle.END_ROD, flagLoc, 1, 0.05, 0.1, 0.05, 0.02);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * 播放投降旗帜效果
     */
    public void playSurrenderFlag(Player player) {
        Location loc = player.getLocation().add(0, 2, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 25) { cancel(); return; }

                // 白色旗帜缓缓飘落
                for (int i = 0; i < 2; i++) {
                    double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                    double y = 0.5 - ticks * 0.02;
                    double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
                    Location whiteFlagLoc = loc.clone().add(x, y, z);
                    loc.getWorld().spawnParticle(Particle.WHITE_ASH, whiteFlagLoc, 1, 0.05, 0.05, 0.05, 0.02);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /**
     * 播放血迹四溅效果
     */
    public void playBloodSplatter(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 15) { cancel(); return; }

                for (int i = 0; i < 5; i++) {
                    double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
                    double dist = 0.3 + ThreadLocalRandom.current().nextDouble() * 0.7;
                    double x = Math.cos(angle) * dist;
                    double z = Math.sin(angle) * dist;
                    double y = 0.5 + ThreadLocalRandom.current().nextDouble() * 1.0;
                    Location bloodLoc = loc.clone().add(x, y, z);
                    loc.getWorld().spawnParticle(Particle.DUST, bloodLoc, 1,
                        Color.fromRGB(139, 0, 0).getRed() / 255.0,
                        Color.fromRGB(139, 0, 0).getGreen() / 255.0,
                        Color.fromRGB(139, 0, 0).getBlue() / 255.0,
                        0.3);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    /**
     * 播放战鼓节奏效果
     */
    public void playBattleDrum(Player player) {
        Location loc = player.getLocation().add(0, 0.5, 0);

        new BukkitRunnable() {
            int ticks = 0;
            final int DRUM_INTERVAL = 8;

            @Override
            public void run() {
                if (ticks >= 40) { cancel(); return; }

                // 鼓点节奏效果
                if (ticks % DRUM_INTERVAL == 0) {
                    loc.getWorld().spawnParticle(Particle.POOF, loc.clone().add(0, 0.5, 0), 3, 0.1, 0.1, 0.1, 0.1);
                    loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.3, 0), 2, 0.15, 0.15, 0.15, 0.05);
                }

                // 地面震动粒子
                if (ticks % DRUM_INTERVAL < 3) {
                    double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
                    double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.5;
                    Location dustLoc = loc.clone().add(x, 0.05, z);
                    loc.getWorld().spawnParticle(Particle.DUST, dustLoc, 1, 0.1, 0.02, 0.1, 0.02);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
