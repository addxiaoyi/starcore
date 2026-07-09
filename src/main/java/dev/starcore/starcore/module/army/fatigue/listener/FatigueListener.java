package dev.starcore.starcore.module.army.fatigue.listener;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.module.army.fatigue.FatigueConfig;
import dev.starcore.starcore.module.army.fatigue.FatigueService;
import dev.starcore.starcore.module.army.fatigue.model.FatigueLevel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 疲劳度事件监听器
 * 监听玩家行为以触发疲劳度变化
 */
public final class FatigueListener implements Listener {

    private final FatigueService fatigueService;
    private final FatigueConfig config;

    public FatigueListener(FatigueService fatigueService, FatigueConfig config) {
        this.fatigueService = fatigueService;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!config.enabled()) return;
        fatigueService.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!config.enabled()) return;
        // 保存玩家数据
        fatigueService.saveAll();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.enabled()) return;
        Player player = event.getPlayer();

        // 检查强制休息
        if (fatigueService.isInForcedRest(player.getUniqueId())) {
            // 阻止移动
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom());
                player.sendMessage("你正在强制休息中，无法移动!");
            }
            return;
        }

        // 计算旅行疲劳
        fatigueService.onPlayerMove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!config.enabled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // 被攻击时增加战斗疲劳
        if (event.getFinalDamage() > 0) {
            fatigueService.addCombatFatigue(victim, (int) event.getFinalDamage());
        }

        // 攻击者也可能增加疲劳
        if (event.getDamager() instanceof Player attacker) {
            fatigueService.addCombatFatigue(attacker, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        if (!config.enabled()) return;
        Player player = event.getPlayer();

        // 检查疲劳惩罚
        double expModifier = fatigueService.getExpModifier(player.getUniqueId());
        if (expModifier < 1.0) {
            // 根据疲劳等级降低经验获取
            int originalAmount = event.getAmount();
            int newAmount = (int) (originalAmount * expModifier);
            event.setAmount(newAmount);
        }
    }

    /**
     * 检查并应用疲劳效果
     */
    private void applyFatigueEffects(Player player) {
        if (!config.enabled()) return;

        double speedMod = fatigueService.getSpeedModifier(player.getUniqueId());
        double attackMod = fatigueService.getAttackModifier(player.getUniqueId());

        // 速度惩罚会在其他地方应用，这里只记录日志
        if (speedMod < 1.0 || attackMod < 1.0) {
            FatigueLevel level = fatigueService.getFatigueLevel(player.getUniqueId());
            // 严重疲劳时随机显示提示
            if (level == FatigueLevel.SEVERELY_FATIGUED || level == FatigueLevel.EXHAUSTED) {
                if (ThreadLocalRandom.current().nextDouble() < 0.01) { // 1% 几率
                    player.sendMessage("你太累了，行动变得迟缓...");
                }
            }
        }
    }
}