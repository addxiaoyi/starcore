package dev.starcore.starcore.foundation.animation;

import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 屏幕震动管理器 - SSS级用户体验
 * 提供各种场景的屏幕震动反馈，让玩家沉浸在游戏中
 *
 * 支持两种模式：
 * 1. Camera API (Paper 1.21+): 真正的屏幕震动
 * 2. Fake Shake (兼容模式): 通过快速移动视角模拟震动
 */
public final class ScreenShakeManager {
    private final Plugin plugin;

    // 震动类型枚举
    public enum ShakeType {
        // 基础震动
        LIGHT("轻微震动", 0.3f, 200, 0.05f, 3),
        MEDIUM("中等震动", 0.5f, 350, 0.08f, 5),
        HEAVY("剧烈震动", 0.8f, 500, 0.12f, 8),

        // 爆炸相关
        EXPLOSION("爆炸", 0.7f, 400, 0.15f, 6),
        NUCLEAR("核爆", 1.0f, 800, 0.25f, 12),
        TNT("TNT爆炸", 0.75f, 450, 0.18f, 7),
        CREEPER("苦力怕爆炸", 0.65f, 380, 0.14f, 5),
        BED_EXPLOSION("床爆炸(下界)", 0.7f, 420, 0.16f, 6),
        RESPAWN_ANCHOR("重生锚充能", 0.6f, 600, 0.1f, 8),

        // 战斗相关
        HIT("打击", 0.4f, 150, 0.06f, 2),
        CRITICAL_HIT("暴击", 0.6f, 250, 0.1f, 4),
        DEATH("死亡", 0.5f, 400, 0.08f, 5),
        COMBO("连击", 0.35f, 180, 0.06f, 3),
        PARRY("格挡", 0.45f, 200, 0.07f, 3),
        DODGE("闪避", 0.3f, 150, 0.05f, 2),

        // 地震
        EARTHQUAKE("地震", 0.9f, 1200, 0.2f, 15),
        AFTershock("余震", 0.4f, 300, 0.1f, 4),
        LANDSLIDE("滑坡", 0.6f, 500, 0.12f, 6),

        // 魔法/特效
        MAGIC("魔法", 0.3f, 250, 0.05f, 3),
        LIGHTNING("雷电", 0.6f, 200, 0.12f, 3),
        PORTAL("传送门", 0.4f, 500, 0.06f, 4),
        ENCHANT("附魔", 0.35f, 350, 0.05f, 4),
        CURSE("诅咒", 0.5f, 400, 0.08f, 5),

        // 国家/战争
        WAR_DECLARE("宣战", 0.8f, 600, 0.15f, 7),
        VICTORY("胜利", 0.5f, 400, 0.08f, 5),
        DEFEAT("战败", 0.7f, 500, 0.12f, 6),
        SIEGE("围攻", 0.6f, 800, 0.1f, 8),
        SURRENDER("投降", 0.45f, 350, 0.07f, 4),
        SCOUT("侦察", 0.25f, 200, 0.04f, 2),

        // 警报/通知
        ACHIEVEMENT("成就", 0.3f, 300, 0.04f, 3),
        RARE_FIND("稀有发现", 0.35f, 250, 0.05f, 3),
        ALERT("警报", 0.5f, 400, 0.08f, 5),
        EARTHQUAKE_ALERT("紧急警报", 0.8f, 600, 0.15f, 8),
        INTRUSION("入侵警报", 0.7f, 500, 0.12f, 6),
        BORDER_WARNING("边界警告", 0.4f, 300, 0.06f, 4),

        // 交互
        BUTTON("按钮", 0.15f, 100, 0.02f, 1),
        TELEPORT("传送", 0.4f, 300, 0.06f, 3),
        BEACON("信标激活", 0.25f, 200, 0.04f, 2),
        CRAFT("合成", 0.2f, 150, 0.03f, 2),
        TRADE("交易", 0.25f, 200, 0.04f, 2),

        // 自然/环境
        THUNDER("雷暴", 0.65f, 600, 0.1f, 6),
        STORM("暴风雨", 0.5f, 800, 0.08f, 8),
        RAIN("下雨", 0.2f, 300, 0.03f, 4),
        VOLCANIC("火山喷发", 0.9f, 1000, 0.2f, 10),

        // 建筑/破坏
        BUILD("建造", 0.2f, 150, 0.03f, 2),
        DESTROY("破坏", 0.4f, 250, 0.06f, 3),
        STRUCTURE("结构变化", 0.5f, 400, 0.08f, 5),

        // 特殊事件
        RARE_SPAWN("稀有生物生成", 0.6f, 500, 0.1f, 5),
        BOSS_APPEAR("Boss出现", 0.8f, 700, 0.15f, 8),
        BOSS_DEATH("Boss死亡", 1.0f, 1000, 0.2f, 10),
        DRAGON_FIGHT("龙战斗", 0.75f, 600, 0.12f, 7),
        WITHER_SPAWN("凋零生成", 0.85f, 800, 0.18f, 9),

        // PvP 战斗
        PVP_START("PvP开始", 0.5f, 400, 0.08f, 5),
        FLAG_CAPTURE("夺旗", 0.55f, 350, 0.09f, 4),
        BASE_ATTACK("基地攻击", 0.65f, 450, 0.11f, 6),
        RANK_UP("段位提升", 0.7f, 600, 0.12f, 7);

        private final String displayName;
        private final float intensity;      // 震动强度 (0-1)
        private final int duration;         // 持续时间 (ms)
        private final float shakeAmplitude; // 晃动幅度
        private final int frequency;        // 震动频率

        ShakeType(String displayName, float intensity, int duration, float shakeAmplitude, int frequency) {
            this.displayName = displayName;
            this.intensity = intensity;
            this.duration = duration;
            this.shakeAmplitude = shakeAmplitude;
            this.frequency = frequency;
        }

        public String getDisplayName() { return displayName; }
        public float getIntensity() { return intensity; }
        public int getDuration() { return duration; }
        public float getShakeAmplitude() { return shakeAmplitude; }
        public int getFrequency() { return frequency; }
    }

    // 震动配置
    public record ShakeConfig(
        float intensity,
        int durationMs,
        float amplitude,
        int frequency,
        boolean cameraShake,
        boolean soundEnabled,
        boolean particleEnabled
    ) {
        public static ShakeConfig from(ShakeType type) {
            return new ShakeConfig(
                type.getIntensity(),
                type.getDuration(),
                type.getShakeAmplitude(),
                type.getFrequency(),
                true,  // 默认使用 camera shake
                true,  // 默认播放声音
                type == ShakeType.EXPLOSION || type == ShakeType.NUCLEAR || type == ShakeType.LIGHTNING
            );
        }

        public static ShakeConfig custom(float intensity, int durationMs, float amplitude, int frequency) {
            return new ShakeConfig(intensity, durationMs, amplitude, frequency, true, true, false);
        }
    }

    // 玩家震动状态
    private final Map<UUID, ShakeState> activeShakes = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerShakePreferences> playerPrefs = new ConcurrentHashMap<>();

    // 震动状态
    private static class ShakeState {
        final ShakeConfig config;
        final long startTime;
        final Location origin;
        final boolean cameraMode;
        Object cameraHandle; // Camera API handle

        ShakeState(ShakeConfig config, Location origin, boolean cameraMode) {
            this.config = config;
            this.startTime = System.currentTimeMillis();
            this.origin = origin.clone();
            this.cameraMode = cameraMode;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > config.durationMs();
        }

        float getProgress() {
            return Math.min(1f, (float)(System.currentTimeMillis() - startTime) / config.durationMs());
        }
    }

    // 玩家偏好设置
    private static class PlayerShakePreferences {
        boolean enabled = true;
        float intensityMultiplier = 1.0f;
        Set<ShakeType> disabledTypes = new HashSet<>();

        boolean canShake(ShakeType type) {
            return enabled && !disabledTypes.contains(type) && intensityMultiplier > 0;
        }
    }

    public ScreenShakeManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 为玩家播放指定类型的屏幕震动
     */
    public void shake(Player player, ShakeType type) {
        shake(player, type, ShakeConfig.from(type));
    }

    /**
     * 为玩家播放自定义配置的屏幕震动
     */
    public void shake(Player player, ShakeType type, ShakeConfig config) {
        if (player == null || !player.isOnline()) return;

        PlayerShakePreferences prefs = playerPrefs.computeIfAbsent(
            player.getUniqueId(), k -> new PlayerShakePreferences()
        );

        if (!prefs.canShake(type)) return;

        // 应用玩家偏好
        float finalIntensity = config.intensity() * prefs.intensityMultiplier;
        ShakeConfig finalConfig = new ShakeConfig(
            finalIntensity,
            config.durationMs(),
            config.amplitude(),
            config.frequency(),
            config.cameraShake(),
            config.soundEnabled(),
            config.particleEnabled()
        );

        // 检查是否正在震动，如果是则叠加或替换
        ShakeState existing = activeShakes.get(player.getUniqueId());
        if (existing != null && !existing.isExpired()) {
            // 如果新震动更强，则替换
            if (finalIntensity > existing.config.intensity()) {
                stopShake(player, false);
            } else {
                return; // 忽略较弱的震动
            }
        }

        // 启动震动
        startShake(player, finalConfig, type);
    }

    /**
     * 为所有在线玩家播放屏幕震动
     */
    public void shakeAll(ShakeType type) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            shake(player, type);
        }
    }

    /**
     * 为所有在线玩家播放屏幕震动（带消息）
     */
    public void shakeAllWithMessage(ShakeType type, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            shake(player, type);
            player.sendActionBar(Component.text(message, NamedTextColor.RED));
        }
    }

    /**
     * 在指定位置播放屏幕震动（影响范围内所有玩家）
     */
    public void shakeAt(ShakeType type, Location location, double radius) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) <= radius) {
                shake(player, type);
            }
        }
    }

    /**
     * 停止玩家的屏幕震动
     */
    public void stopShake(Player player) {
        stopShake(player, true);
    }

    private void stopShake(Player player, boolean resetCamera) {
        ShakeState state = activeShakes.remove(player.getUniqueId());
        if (state != null && resetCamera && state.cameraMode && state.cameraHandle != null) {
            resetCamera(state, player);
        }
    }

    /**
     * 检查玩家是否正在震动
     */
    public boolean isShaking(Player player) {
        ShakeState state = activeShakes.get(player.getUniqueId());
        return state != null && !state.isExpired();
    }

    /**
     * 启用/禁用玩家的震动反馈
     */
    public void setEnabled(Player player, boolean enabled) {
        playerPrefs.computeIfAbsent(player.getUniqueId(), k -> new PlayerShakePreferences())
            .enabled = enabled;

        if (!enabled) {
            stopShake(player);
        }
    }

    /**
     * 设置玩家震动强度倍数
     */
    public void setIntensityMultiplier(Player player, float multiplier) {
        playerPrefs.computeIfAbsent(player.getUniqueId(), k -> new PlayerShakePreferences())
            .intensityMultiplier = Math.max(0f, Math.min(2f, multiplier));
    }

    /**
     * 禁用特定类型的震动
     */
    public void disableType(Player player, ShakeType type) {
        playerPrefs.computeIfAbsent(player.getUniqueId(), k -> new PlayerShakePreferences())
            .disabledTypes.add(type);
    }

    /**
     * 启用特定类型的震动
     */
    public void enableType(Player player, ShakeType type) {
        PlayerShakePreferences prefs = playerPrefs.get(player.getUniqueId());
        if (prefs != null) {
            prefs.disabledTypes.remove(type);
        }
    }

    // ==================== 内部实现 ====================

    private void startShake(Player player, ShakeConfig config, ShakeType type) {
        Location origin = player.getLocation();
        boolean useCamera = config.cameraShake() && hasCameraSupport();

        ShakeState state = new ShakeState(config, origin, useCamera);
        activeShakes.put(player.getUniqueId(), state);

        // 播放音效
        if (config.soundEnabled()) {
            playShakeSound(player, type);
        }

        // 播放粒子
        if (config.particleEnabled()) {
            playShakeParticles(player, type);
        }

        // 启动震动循环
        if (useCamera) {
            startCameraShake(player, state);
        } else {
            startFakeShake(player, state);
        }
    }

    private boolean hasCameraSupport() {
        // Paper 1.21+ 支持 Camera API
        try {
            Class.forName("org.bukkit.camera.Camera");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void startCameraShake(Player player, ShakeState state) {
        try {
            // 使用 Paper Camera API
            Class<?> cameraClass = Class.forName("org.bukkit.camera.Camera");
            Class<?> cameraManagerClass = Class.forName("org.bukkit.camera.CameraManager");

            // 获取 CameraManager 并创建相机
            Object cameraManager = player.getClass()
                .getMethod("getCameraManager")
                .invoke(player);

            Object camera = cameraManagerClass
                .getMethod("getCamera", player.getClass())
                .invoke(cameraManager, player);

            state.cameraHandle = camera;

            // 使用调度器模拟震动效果
            int[] tickCounter = new int[1];
            int[] cancelled = new int[1];
            cancelled[0] = 0;

            plugin.getServer().getScheduler().runTaskTimer(plugin, l -> {
                if (cancelled[0] != 0 || !player.isOnline() || state.isExpired()) {
                    if (cancelled[0] == 0) {
                        cancelled[0] = 1;
                    }
                    activeShakes.remove(player.getUniqueId());
                    resetCamera(state, player);
                    return;
                }

                try {
                    // 计算震动偏移
                    float progress = state.getProgress();
                    float decay = 1f - progress; //逐渐减弱
                    float amplitude = state.config.amplitude() * decay * state.config.intensity();

                    // 生成随机震动偏移
                    float offsetX = (float)(ThreadLocalRandom.current().nextDouble() * 2 - 1) * amplitude;
                    float offsetY = (float)(ThreadLocalRandom.current().nextDouble() * 2 - 1) * amplitude * 0.5f;
                    float offsetZ = (float)(ThreadLocalRandom.current().nextDouble() * 2 - 1) * amplitude;

                    // 获取当前相机偏移并叠加
                    Vector currentOffset = getCameraOffset(state);
                    Vector newOffset = new Vector(
                        currentOffset.getX() + offsetX,
                        currentOffset.getY() + offsetY,
                        currentOffset.getZ() + offsetZ
                    );
                    setCameraOffset(state, newOffset);

                    // 应用相机变换
                    applyCameraTransform(camera, newOffset);

                } catch (Exception ex) {
                    // 降级到假震动
                    cancelled[0] = 1;
                    activeShakes.remove(player.getUniqueId());
                    startFakeShake(player, state);
                }
            }, 0L, (long)(50f / state.config.frequency()));
            return;

        } catch (Exception e) {
            // Camera API 不可用，降级到假震动
            startFakeShake(player, state);
        }
    }

    private void startFakeShake(Player player, ShakeState state) {
        Location baseLocation = state.origin.clone();
        int totalTicks = state.config.durationMs() / 50;

        int[] cancelled = new int[1];
        final int[] tickCount = new int[1]; // 用数组包装以便在lambda中修改

        plugin.getServer().getScheduler().runTaskTimer(plugin, l -> {
            if (cancelled[0] != 0 || !player.isOnline() || tickCount[0] >= totalTicks) {
                if (cancelled[0] == 0) {
                    cancelled[0] = 1;
                }
                activeShakes.remove(player.getUniqueId());

                // 重置玩家位置
                if (player.isOnline()) {
                    player.teleport(baseLocation);
                }
                return;
            }

            float progress = (float) tickCount[0] / totalTicks;
            float decay = 1f - progress;
            float amplitude = state.config.amplitude() * decay * state.config.intensity();

            // 生成随机偏移
            double offsetX = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * amplitude;
            double offsetY = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * amplitude * 0.5;
            double offsetZ = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * amplitude;

            // 移动玩家（轻微偏移视角）
            Location newLoc = baseLocation.clone().add(offsetX, offsetY, offsetZ);
            player.teleport(newLoc);

            tickCount[0]++;
        }, 0L, 1L);
    }

    private Vector getCameraOffset(ShakeState state) {
        if (state.cameraHandle == null) return new Vector();
        try {
            Object offset = state.cameraHandle.getClass().getField("shakeOffset").get(state.cameraHandle);
            if (offset instanceof Vector) return (Vector) offset;
            return new Vector();
        } catch (Exception e) {
            return new Vector();
        }
    }

    private void setCameraOffset(ShakeState state, Vector offset) {
        if (state.cameraHandle == null) return;
        try {
            state.cameraHandle.getClass().getField("shakeOffset").set(state.cameraHandle, offset);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 反射访问失败，忽略
        }
    }

    private void applyCameraTransform(Object camera, Vector offset) {
        if (camera == null) return;
        try {
            camera.getClass().getMethod("setRotation", float.class, float.class)
                .invoke(camera, offset.getX() * 10, offset.getZ() * 10);
        } catch (ReflectiveOperationException e) {
            // 反射调用失败，忽略
        }
    }

    private void resetCamera(ShakeState state, Player player) {
        if (state == null || !player.isOnline()) return;

        try {
            if (state.cameraHandle != null) {
                Class<?> cameraManagerClass = Class.forName("org.bukkit.camera.CameraManager");
                Object cameraManager = player.getClass()
                    .getMethod("getCameraManager")
                    .invoke(player);
                cameraManagerClass.getMethod("resetCamera", player.getClass())
                    .invoke(cameraManager, player);
            }
        } catch (ReflectiveOperationException e) {
            // Camera API 不可用或方法调用失败，忽略
        }
    }

    private void playShakeSound(Player player, ShakeType type) {
        Sound sound = switch (type) {
            // 爆炸类
            case EXPLOSION, NUCLEAR -> Sound.ENTITY_GENERIC_EXPLODE;
            case TNT -> Sound.ENTITY_TNT_PRIMED;
            case CREEPER -> Sound.ENTITY_CREEPER_PRIMED;
            case BED_EXPLOSION, RESPAWN_ANCHOR -> Sound.ENTITY_GENERIC_BURN;
            case EARTHQUAKE, LANDSLIDE -> Sound.ENTITY_ENDER_DRAGON_GROWL;

            // 战斗类
            case HIT -> Sound.ENTITY_WITHER_HURT;
            case CRITICAL_HIT -> Sound.ENTITY_WITHER_BREAK_BLOCK;
            case DEATH -> Sound.ENTITY_WITHER_DEATH;
            case COMBO -> Sound.ENTITY_EVOKER_CAST_SPELL;
            case PARRY -> Sound.BLOCK_ANVIL_USE;
            case DODGE -> Sound.ENTITY_SHEEP_SHEAR;

            // 雷电/魔法类
            case LIGHTNING, THUNDER -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            case MAGIC, ENCHANT -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            case CURSE -> Sound.ENTITY_WITCH_CELEBRATE;
            case PORTAL -> Sound.ENTITY_ENDERMAN_TELEPORT;

            // 战争类
            case WAR_DECLARE -> Sound.ENTITY_WITHER_SPAWN;
            case VICTORY -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case DEFEAT -> Sound.ENTITY_WITHER_DEATH;
            case SIEGE -> Sound.ENTITY_GENERIC_EXPLODE;
            case SURRENDER -> Sound.ENTITY_VILLAGER_TRADE;
            case SCOUT -> Sound.ENTITY_PARROT_AMBIENT;

            // 警报类
            case ALERT, BORDER_WARNING -> Sound.BLOCK_NOTE_BLOCK_BELL;
            case EARTHQUAKE_ALERT -> Sound.BLOCK_BEACON_DEACTIVATE;
            case INTRUSION -> Sound.BLOCK_NOTE_BLOCK_BASS;

            // 交互类
            case ACHIEVEMENT -> Sound.ENTITY_PLAYER_LEVELUP;
            case RARE_FIND -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case BEACON -> Sound.BLOCK_BEACON_ACTIVATE;
            case BUTTON -> Sound.BLOCK_STONE_BUTTON_CLICK_ON;
            case TELEPORT -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case CRAFT -> Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN;
            case TRADE -> Sound.ENTITY_VILLAGER_AMBIENT;

            // 环境类
            case STORM -> Sound.WEATHER_RAIN;
            case RAIN -> Sound.WEATHER_RAIN;
            case VOLCANIC -> Sound.ENTITY_GENERIC_EXPLODE;

            // 建筑/破坏类
            case BUILD -> Sound.BLOCK_STONE_PLACE;
            case DESTROY -> Sound.BLOCK_STONE_BREAK;
            case STRUCTURE -> Sound.BLOCK_BEACON_DEACTIVATE;

            // 特殊事件类
            case RARE_SPAWN -> Sound.ENTITY_WITHER_SPAWN;
            case BOSS_APPEAR -> Sound.ENTITY_WITHER_SPAWN;
            case BOSS_DEATH -> Sound.ENTITY_WITHER_DEATH;
            case DRAGON_FIGHT -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            case WITHER_SPAWN -> Sound.ENTITY_WITHER_SPAWN;

            // PvP 类
            case PVP_START -> Sound.ENTITY_GENERIC_EXPLODE;
            case FLAG_CAPTURE -> Sound.ENTITY_ITEM_PICKUP;
            case BASE_ATTACK -> Sound.ENTITY_GENERIC_EXPLODE;
            case RANK_UP -> Sound.ENTITY_PLAYER_LEVELUP;

            // 基础震动
            default -> Sound.ENTITY_GENERIC_HURT;
        };

        float volume = type.getIntensity() * 1.2f;
        float pitch = 0.8f + (float)(ThreadLocalRandom.current().nextDouble() * 0.4);

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private void playShakeParticles(Player player, ShakeType type) {
        Location loc = player.getLocation();

        switch (type) {
            // 爆炸类
            case EXPLOSION, NUCLEAR, TNT, CREEPER, BED_EXPLOSION, RESPAWN_ANCHOR, VOLCANIC -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.EXPLOSION_EMITTER, loc, 3, 0.5, 0.5, 0.5, 0
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.FLAME, loc, 30, 1.0, 0.5, 1.0, 0.05
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc, 20, 1.0, 0.5, 1.0, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.LAVA, loc, 15, 0.8, 0.3, 0.8, 0
                );
            }
            // 雷电类
            case LIGHTNING, THUNDER -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.FLASH, loc, 2, 0, 0, 0, 0
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.ELECTRIC_SPARK, loc, 25, 0.5, 1.0, 0.5, 0.1
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.WHITE_ASH, loc, 10, 0.5, 0.5, 0.5, 0
                );
            }
            // 战争/警报类
            case WAR_DECLARE, SIEGE, BOSS_APPEAR, WITHER_SPAWN -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.WITCH, loc, 20, 1.0, 0.5, 1.0, 0.03
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc, 15, 0.8, 0.5, 0.8, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.FLAME, loc, 20, 0.8, 0.4, 0.8, 0.03
                );
            }
            // 胜利/成就类
            case VICTORY, ACHIEVEMENT, RANK_UP -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.END_ROD, loc, 15, 0.5, 0.5, 0.5, 0.01
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.HAPPY_VILLAGER, loc, 20, 0.5, 0.5, 0.5, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.ITEM_SNOWBALL, loc, 10, 0.3, 0.3, 0.3, 0.01
                );
            }
            // 魔法/附魔类
            case MAGIC, ENCHANT, CURSE -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.ENCHANTED_HIT, loc, 15, 0.5, 0.5, 0.5, 0.03
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.WITCH, loc, 10, 0.4, 0.4, 0.4, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SOUL, loc, 8, 0.3, 0.3, 0.3, 0.01
                );
            }
            // 传送类
            case PORTAL, TELEPORT -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.PORTAL, loc, 25, 0.8, 1.0, 0.8, 0.05
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.REVERSE_PORTAL, loc, 15, 0.5, 0.6, 0.5, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.WHITE_ASH, loc, 10, 0.3, 0.3, 0.3, 0
                );
            }
            // 地震类
            case EARTHQUAKE, LANDSLIDE, AFTershock -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.BLOCK, loc, 30, 1.0, 0.2, 1.0, 0.03,
                    org.bukkit.Material.DIRT.createBlockData()
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.BLOCK, loc, 20, 0.8, 0.2, 0.8, 0.02,
                    org.bukkit.Material.GRAVEL.createBlockData()
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc, 15, 0.6, 0.3, 0.6, 0.02
                );
            }
            // Boss 死亡/龙战斗类
            case BOSS_DEATH, DRAGON_FIGHT -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.DRAGON_BREATH, loc, 30, 1.0, 0.8, 1.0, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.END_ROD, loc, 25, 0.8, 0.8, 0.8, 0.01
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.EXPLOSION_EMITTER, loc, 2, 0.5, 0.5, 0.5, 0
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc, 20, 0.8, 0.5, 0.8, 0.02
                );
            }
            // 信标/建筑类
            case BEACON, BUILD -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.END_ROD, loc, 15, 0.5, 0.8, 0.5, 0.01
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.HAPPY_VILLAGER, loc, 10, 0.4, 0.4, 0.4, 0.02
                );
            }
            // 破坏类
            case DESTROY, STRUCTURE -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.BLOCK, loc, 25, 0.8, 0.8, 0.8, 0.03,
                    org.bukkit.Material.STONE.createBlockData()
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.ITEM, loc, 15, 0.5, 0.5, 0.5, 0.01,
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.COBBLESTONE)
                );
            }
            // 战斗类
            case CRITICAL_HIT, COMBO, PARRY -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.CRIT, loc, 10, 0.3, 0.3, 0.3, 0.05
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.CRIT, loc, 5, 0.2, 0.2, 0.2, 0.03
                );
            }
            // 天气类
            case STORM, RAIN -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.DRIPPING_WATER, loc, 20, 0.5, 0.5, 0.5, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SPLASH, loc, 15, 0.4, 0.4, 0.4, 0.01
                );
            }
            // 警报类
            case ALERT, EARTHQUAKE_ALERT, INTRUSION, BORDER_WARNING -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.FLAME, loc, 15, 0.5, 0.5, 0.5, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc, 10, 0.4, 0.4, 0.4, 0.02
                );
            }
            // 稀有发现类
            case RARE_FIND, RARE_SPAWN -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.END_ROD, loc, 20, 0.6, 0.6, 0.6, 0.01
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.NAUTILUS, loc, 15, 0.5, 0.8, 0.5, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.WHITE_ASH, loc, 10, 0.4, 0.4, 0.4, 0
                );
            }
            // 战败/投降类
            case DEFEAT, SURRENDER -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc, 20, 0.6, 0.6, 0.6, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.ASH, loc, 15, 0.5, 0.5, 0.5, 0.01
                );
            }
            // PvP 类
            case PVP_START, FLAG_CAPTURE, BASE_ATTACK -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.FLAME, loc, 15, 0.5, 0.5, 0.5, 0.03
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc, 10, 0.4, 0.4, 0.4, 0.02
                );
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.DUST, loc, 12, 0.3, 0.3, 0.3, 0.01,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f)
                );
            }
            // 默认粒子
            default -> {
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SMOKE, loc.add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.02
                );
            }
        }
    }

    /**
     * 清理过期震动状态（定期调用）
     */
    public void cleanupExpired() {
        activeShakes.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                return true;
            }
            return false;
        });
    }

    /**
     * 获取当前震动中的玩家数量
     */
    public int getActiveShakeCount() {
        cleanupExpired();
        return activeShakes.size();
    }
}
