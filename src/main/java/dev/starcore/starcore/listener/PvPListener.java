package dev.starcore.starcore.listener;

import dev.starcore.starcore.pvp.duel.DuelService;
import dev.starcore.starcore.pvp.duel.Duel;
import dev.starcore.starcore.pvp.killstreak.KillStreakService;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.social.party.PartyService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/**
 * PvP事件监听器
 */
public final class PvPListener implements Listener {
    private final DuelService duelService;
    private final KillStreakService killStreakService;
    private final PvPStatsService statsService;
    private final PartyService partyService;

    public PvPListener(DuelService duelService, KillStreakService killStreakService,
                       PvPStatsService statsService, PartyService partyService) {
        this.duelService = duelService;
        this.killStreakService = killStreakService;
        this.statsService = statsService;
        this.partyService = partyService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        // 检查是否在同一派对（友军保护）
        if (partyService.areInSameParty(attackerId, victimId)) {
            if (!partyService.getPlayerParty(attackerId).isFriendlyFire()) {
                event.setCancelled(true);
                attacker.sendMessage("§c不能攻击队友！");
                return;
            }
        }

        // 记录决斗伤害
        Duel duel = duelService.getPlayerDuel(attackerId);
        if (duel != null && duel.getState() == Duel.DuelState.IN_PROGRESS) {
            duelService.recordDamage(attackerId, victimId, (int) event.getFinalDamage());
        }

        // 记录PvP伤害
        statsService.recordDamage(attackerId, (int) event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        UUID victimId = victim.getUniqueId();

        // 检查受害者是否在决斗中（修复：应该检查 victim 而不是 killer）
        Duel duel = duelService.getPlayerDuel(victimId);
        if (duel != null && duel.getState() == Duel.DuelState.IN_PROGRESS) {
            // 决斗中的死亡
            if (killer != null) {
                UUID killerId = killer.getUniqueId();
                // 决斗获胜 - 不移除战利品，直接结束
                event.getDrops().clear(); // 决斗不掉落物品
                event.setDroppedExp(0); // 决斗不掉落经验

                // BO 多局制处理
                if (duel.getBestOf() > 1) {
                    duelService.recordRoundWin(duel.getId(), killerId);
                } else {
                    duelService.endDuel(duel.getId(), killerId, Duel.DuelEndReason.DEATH);
                }

                statsService.recordDuelWin(killerId);
                statsService.recordDuelLoss(victimId);
            }
            return;
        }

        // 非决斗死亡
        if (killer == null) return;

        UUID killerId = killer.getUniqueId();

        // 记录击杀
        statsService.recordKill(killerId, victimId);

        // 连杀系统
        var result = killStreakService.addKill(killerId);
        int lostStreak = killStreakService.resetKillStreak(victimId);

        // 连杀里程碑
        if (result.milestone() != null) {
            String message = String.format(
                "§6§l[连杀] §e%s §f达成 §c%s §f(%d连杀)！",
                killer.getName(),
                result.milestone().displayName(),
                result.currentStreak()
            );
            killer.getServer().broadcastMessage(message);
        }

        // 终结连杀
        if (lostStreak >= 5) {
            String message = String.format(
                "§c§l[终结] §e%s §f终结了 §e%s §f的 §c%d连杀！",
                killer.getName(),
                victim.getName(),
                lostStreak
            );
            killer.getServer().broadcastMessage(message);
        }
    }

    /**
     * 处理决斗中的玩家复活
     */
    public void respawnInDuel(Player player) {
        UUID playerId = player.getUniqueId();
        Duel duel = duelService.getPlayerDuel(playerId);
        if (duel != null && duel.getState() == Duel.DuelState.IN_PROGRESS) {
            duelService.respawnPlayer(player, duel.getId());
        }
    }
}
