package dev.starcore.starcore.foundation.animation.listener;

import dev.starcore.starcore.foundation.animation.ScreenShake;
import dev.starcore.starcore.foundation.animation.ScreenShakeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * 战斗震动监听器
 * 在玩家受到伤害、死亡时触发屏幕震动
 */
public final class CombatShakeListener implements Listener {
    private final Plugin plugin;
    private final ScreenShakeManager shakeManager;

    public CombatShakeListener(Plugin plugin, ScreenShakeManager shakeManager) {
        this.plugin = plugin;
        this.shakeManager = shakeManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // 根据伤害类型选择震动效果
        ScreenShakeManager.ShakeType type = determineShakeType(event);

        // 计算伤害比例来调整震动强度
        float damageRatio = (float) event.getFinalDamage() / 20f; // 以满血20点为基准
        damageRatio = Math.min(1f, Math.max(0.2f, damageRatio));

        if (damageRatio > 0.7f) {
            // 致命伤害 - 暴击震动
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.CRITICAL_HIT);
        } else if (damageRatio > 0.3f) {
            // 重伤害
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.HIT);
        } else {
            // 轻微伤害
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.LIGHT);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // 被玩家攻击时，受害者震动
        ScreenShake.shake(victim, ScreenShakeManager.ShakeType.HIT);

        // 如果是暴击，额外震动
        // 注意：Bukkit 没有直接的暴击 API，需要通过元数据判断
        // 这里使用伤害值来估算
        if (event.getFinalDamage() >= 8) {
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.CRITICAL_HIT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // 死亡震动
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ScreenShake.shake(victim, ScreenShakeManager.ShakeType.DEATH);
        }, 1L);

        // 如果有击杀者，让击杀者也感受到震动
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            ScreenShake.shake(killer, ScreenShakeManager.ShakeType.VICTORY);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 复活后轻微震动（表示重生）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ScreenShake.shake(player, ScreenShakeManager.ShakeType.LIGHT);
        }, 5L);
    }

    private ScreenShakeManager.ShakeType determineShakeType(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();

        return switch (cause) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> ScreenShakeManager.ShakeType.EXPLOSION;
            case LIGHTNING -> ScreenShakeManager.ShakeType.LIGHTNING;
            case FALL -> ScreenShakeManager.ShakeType.LIGHT;
            case CONTACT, PROJECTILE -> ScreenShakeManager.ShakeType.HIT;
            case MAGIC -> ScreenShakeManager.ShakeType.MAGIC;
            case SUICIDE, VOID -> ScreenShakeManager.ShakeType.DEATH;
            default -> ScreenShakeManager.ShakeType.HIT;
        };
    }
}
