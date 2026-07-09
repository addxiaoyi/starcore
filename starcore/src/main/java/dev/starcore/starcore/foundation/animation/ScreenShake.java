package dev.starcore.starcore.foundation.animation;

import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 屏幕震动静态入口类
 * 提供便捷的静态方法调用屏幕震动效果
 *
 * 使用示例:
 * <pre>
 * // 简单调用
 * ScreenShake.shake(player, ScreenShakeManager.ShakeType.EXPLOSION);
 *
 * // 震动所有玩家
 * ScreenShake.shakeAll(ScreenShake.ShakeType.EARTHQUAKE);
 *
 * // 震动并显示消息
 * ScreenShake.shakeWithMessage(player, ScreenShakeManager.ShakeType.ALERT, "警告！敌人入侵！");
 * </pre>
 */
public final class ScreenShake {
    private static ScreenShakeManager manager;

    private ScreenShake() {} // 禁止实例化

    /**
     * 初始化震动管理器（由插件在 onEnable 时调用）
     */
    public static void init(ScreenShakeManager manager) {
        ScreenShake.manager = manager;
    }

    /**
     * 获取震动管理器实例
     */
    public static ScreenShakeManager getManager() {
        return manager;
    }

    /**
     * 检查震动管理器是否已初始化
     */
    public static boolean isAvailable() {
        return manager != null;
    }

    // ==================== 基础震动方法 ====================

    /**
     * 为玩家播放震动效果
     */
    public static void shake(Player player, ScreenShakeManager.ShakeType type) {
        if (manager != null && player != null) {
            manager.shake(player, type);
        }
    }

    /**
     * 为玩家播放自定义配置的震动效果
     */
    public static void shake(Player player, ScreenShakeManager.ShakeType type, ScreenShakeManager.ShakeConfig config) {
        if (manager != null && player != null) {
            manager.shake(player, type, config);
        }
    }

    /**
     * 为所有在线玩家播放震动效果
     */
    public static void shakeAll(ScreenShakeManager.ShakeType type) {
        if (manager != null) {
            manager.shakeAll(type);
        }
    }

    /**
     * 为玩家播放震动并显示 ActionBar 消息
     */
    public static void shakeWithMessage(Player player, ScreenShakeManager.ShakeType type, String message) {
        if (manager != null && player != null) {
            manager.shake(player, type);
            player.sendActionBar(net.kyori.adventure.text.Component.text(message,
                net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    /**
     * 为所有玩家播放震动并显示 ActionBar 消息
     */
    public static void shakeAllWithMessage(ScreenShakeManager.ShakeType type, String message) {
        if (manager != null) {
            manager.shakeAllWithMessage(type, message);
        }
    }

    /**
     * 在指定位置播放震动，影响范围内玩家
     */
    public static void shakeAt(ScreenShakeManager.ShakeType type, org.bukkit.Location location, double radius) {
        if (manager != null && location != null) {
            manager.shakeAt(type, location, radius);
        }
    }

    // ==================== 便捷震动方法 ====================

    /**
     * 轻微震动反馈
     */
    public static void lightShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.LIGHT);
    }

    /**
     * 爆炸震动
     */
    public static void explosionShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.EXPLOSION);
    }

    /**
     * 核爆震动
     */
    public static void nuclearShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.NUCLEAR);
    }

    /**
     * 战斗打击震动
     */
    public static void hitShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.HIT);
    }

    /**
     * 暴击震动
     */
    public static void criticalShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.CRITICAL_HIT);
    }

    /**
     * 死亡震动
     */
    public static void deathShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.DEATH);
    }

    /**
     * 雷电震动
     */
    public static void lightningShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.LIGHTNING);
    }

    /**
     * 宣战震动
     */
    public static void warDeclareShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.WAR_DECLARE);
    }

    /**
     * 宣战震动（全局）
     */
    public static void warDeclareShakeAll() {
        shakeAll(ScreenShakeManager.ShakeType.WAR_DECLARE);
    }

    /**
     * 胜利震动
     */
    public static void victoryShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.VICTORY);
    }

    /**
     * 战败震动
     */
    public static void defeatShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.DEFEAT);
    }

    /**
     * 围攻震动
     */
    public static void siegeShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.SIEGE);
    }

    /**
     * 成就震动
     */
    public static void achievementShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.ACHIEVEMENT);
    }

    /**
     * 稀有发现震动
     */
    public static void rareFindShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.RARE_FIND);
    }

    /**
     * 警报震动
     */
    public static void alertShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.ALERT);
    }

    /**
     * 紧急警报震动
     */
    public static void alertShakeAll() {
        shakeAll(ScreenShakeManager.ShakeType.EARTHQUAKE_ALERT);
    }

    /**
     * 传送震动
     */
    public static void teleportShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.TELEPORT);
    }

    /**
     * 信标激活震动
     */
    public static void beaconShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.BEACON);
    }

    /**
     * 魔法震动
     */
    public static void magicShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.MAGIC);
    }

    /**
     * 传送门震动
     */
    public static void portalShake(Player player) {
        shake(player, ScreenShakeManager.ShakeType.PORTAL);
    }

    // ==================== 玩家设置方法 ====================

    /**
     * 停止玩家的震动
     */
    public static void stop(Player player) {
        if (manager != null) {
            manager.stopShake(player);
        }
    }

    /**
     * 停止所有玩家的震动
     */
    public static void stopAll() {
        if (manager != null) {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                manager.stopShake(player);
            }
        }
    }

    /**
     * 检查玩家是否正在震动
     */
    public static boolean isShaking(Player player) {
        return manager != null && manager.isShaking(player);
    }

    /**
     * 启用/禁用震动反馈
     */
    public static void setEnabled(Player player, boolean enabled) {
        if (manager != null) {
            manager.setEnabled(player, enabled);
        }
    }

    /**
     * 设置震动强度
     */
    public static void setIntensity(Player player, float intensity) {
        if (manager != null) {
            manager.setIntensityMultiplier(player, intensity);
        }
    }

    /**
     * 禁用特定类型的震动
     */
    public static void disableType(Player player, ScreenShakeManager.ShakeType... types) {
        if (manager != null) {
            for (ScreenShakeManager.ShakeType type : types) {
                manager.disableType(player, type);
            }
        }
    }

    /**
     * 启用特定类型的震动
     */
    public static void enableType(Player player, ScreenShakeManager.ShakeType... types) {
        if (manager != null) {
            for (ScreenShakeManager.ShakeType type : types) {
                manager.enableType(player, type);
            }
        }
    }

    /**
     * 获取当前震动中的玩家数量
     */
    public static int getActiveShakeCount() {
        return manager != null ? manager.getActiveShakeCount() : 0;
    }
}
