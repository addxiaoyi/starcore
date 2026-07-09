package dev.starcore.starcore.foundation.animation.listener;

import dev.starcore.starcore.foundation.animation.ScreenShake;
import dev.starcore.starcore.foundation.animation.ScreenShakeManager;
import dev.starcore.starcore.achievement.AchievementUnlockedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * 成就震动监听器
 * 在玩家获得成就时触发屏幕震动和特效
 */
public final class AchievementShakeListener implements Listener {
    private final Plugin plugin;

    public AchievementShakeListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAchievement(AchievementUnlockedEvent event) {
        Player player = event.getPlayer();
        Component title = event.getTitle();

        // 根据成就稀有度选择震动类型
        ScreenShakeManager.ShakeType type = getShakeTypeForAchievement(event);
        ScreenShake.shake(player, type);

        // 显示成就消息
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("成就解锁!", NamedTextColor.GOLD),
                    title,
                    net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofMillis(500)
                    )
                ));
            }
        }, 1L);
    }

    private ScreenShakeManager.ShakeType getShakeTypeForAchievement(AchievementUnlockedEvent event) {
        // 根据成就稀有度判断震动类型
        var frameType = event.getFrameType();

        if (frameType == dev.starcore.starcore.achievement.Achievement.FrameType.CHALLENGE) {
            return ScreenShakeManager.ShakeType.RARE_FIND;
        }
        if (frameType == dev.starcore.starcore.achievement.Achievement.FrameType.GOAL) {
            return ScreenShakeManager.ShakeType.VICTORY;
        }

        return ScreenShakeManager.ShakeType.ACHIEVEMENT;
    }
}