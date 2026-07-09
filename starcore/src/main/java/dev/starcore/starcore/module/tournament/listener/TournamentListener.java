package dev.starcore.starcore.module.tournament.listener;

import dev.starcore.starcore.module.tournament.Tournament;
import dev.starcore.starcore.module.tournament.TournamentStatus;
import dev.starcore.starcore.module.tournament.TournamentType;
import dev.starcore.starcore.module.tournament.TournamentService;
import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 锦标赛事件监听器
 * 处理比赛相关的玩家事件
 */
public class TournamentListener implements Listener {

    private final JavaPlugin plugin;
    private final TournamentService tournamentService;

    // 击杀冷却（防止重复记录）
    private final Map<UUID, Long> killCooldowns = new ConcurrentHashMap<>();
    private static final long KILL_COOLDOWN_MS = 1000;

    public TournamentListener(JavaPlugin plugin, TournamentService tournamentService) {
        this.plugin = plugin;
        this.tournamentService = tournamentService;
    }

    /**
     * 玩家伤害事件 - 检查 PvP 限制
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 只处理玩家之间的伤害
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // 检查受害者是否在比赛中
        var tournamentOpt = tournamentService.getPlayerTournament(victim);
        if (tournamentOpt.isEmpty()) {
            return;
        }

        Tournament tournament = tournamentOpt.get();

        // 只对特定比赛类型启用 PvP
        switch (tournament.getType()) {
            case SPEEDRUN, PARKOUR -> {
                // 竞速类型不允许 PvP
                event.setCancelled(true);
            }
            default -> {
                // PvP 类型允许伤害
            }
        }
    }

    /**
     * 玩家死亡事件 - 记录淘汰
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // 检查受害者是否在比赛中
        var tournamentOpt = tournamentService.getPlayerTournament(victim);
        if (tournamentOpt.isEmpty()) {
            return;
        }

        Tournament tournament = tournamentOpt.get();

        // 只在比赛进行中处理
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return;
        }

        // 获取击杀者
        Player killer = victim.getKiller();

        // 记录死亡和击杀
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (killer != null) {
                // 检查击杀冷却
                UUID killerId = killer.getUniqueId();
                long now = System.currentTimeMillis();
                Long lastKill = killCooldowns.get(killerId);
                if (lastKill != null && now - lastKill < KILL_COOLDOWN_MS) {
                    return; // 冷却中，跳过
                }
                killCooldowns.put(killerId, now);

                // 记录击杀
                tournamentService.recordKill(tournament.getId(), killer.getUniqueId(), victim.getUniqueId());

                // 广播击杀消息
                broadcastKill(tournament, killer, victim);
            } else {
                // 非玩家击杀
                tournamentService.recordKill(tournament.getId(), null, victim.getUniqueId());
            }

            // 清理掉落物（比赛模式下）
            event.getDrops().clear();
            event.setDroppedExp(0);

        }, 1L);
    }

    /**
     * 玩家重生事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 检查玩家是否在比赛中
        var tournamentOpt = tournamentService.getPlayerTournament(player);
        if (tournamentOpt.isEmpty()) {
            return;
        }

        Tournament tournament = tournamentOpt.get();

        // 只在比赛进行中处理
        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return;
        }

        // PvP 乱斗和淘汰赛：死亡后提示淘汰
        if (tournament.getType() == TournamentType.PVP_FFA ||
            tournament.getType() == TournamentType.ELIMINATION) {

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 检查玩家是否已被移除
                if (!tournament.getAliveParticipants().contains(player.getUniqueId())) {
                    player.sendMessage("§c你已被淘汰！");
                    player.sendMessage("§7使用 §e/tournament leave §7离开比赛观战");
                }
            }, 1L);
        }

        // 1v1：死亡后移出比赛
        if (tournament.getType() == TournamentType.PVP_1V1) {
            tournamentService.leaveTournament(player);
            player.sendMessage("§c你输掉了比赛！");
        }
    }

    /**
     * 玩家退出事件 - 处理离开比赛
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 检查玩家是否在比赛中
        var tournamentOpt = tournamentService.getPlayerTournament(player);
        if (tournamentOpt.isEmpty()) {
            return;
        }

        Tournament tournament = tournamentOpt.get();

        // 仅在比赛进行中处理（WAITING 状态为正常离开，不视为弃权）
        if (tournament.getStatus() == TournamentStatus.IN_PROGRESS) {
            // 比赛进行中退出视为弃权
            tournamentService.recordKill(tournament.getId(), null, player.getUniqueId());

            // 广播弃权消息
            for (UUID participantId : tournament.getParticipants()) {
                Player p = Bukkit.getPlayer(participantId);
                if (p != null) {
                    p.sendMessage("§c" + player.getName() + " §7离开了比赛（弃权）");
                }
            }
        }

        // 移除参与者
        tournamentService.leaveTournament(player);
    }

    // ==================== 辅助方法 ====================

    private void broadcastKill(Tournament tournament, Player killer, Player victim) {
        String message = String.format("§c%s §7被 §a%s §7击杀！", victim.getName(), killer.getName());

        for (UUID participantId : tournament.getParticipants()) {
            Player p = Bukkit.getPlayer(participantId);
            if (p != null) {
                p.sendMessage(message);
            }
        }

        // 检查剩余人数
        int alive = tournament.getAliveParticipants().size();
        if (alive <= 3) {
            for (UUID participantId : tournament.getParticipants()) {
                Player p = Bukkit.getPlayer(participantId);
                if (p != null) {
                    p.sendMessage("§6§l剩余 §e" + alive + " §6§l名玩家！");
                }
            }
        }
    }
}
