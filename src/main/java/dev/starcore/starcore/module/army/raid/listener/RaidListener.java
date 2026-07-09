package dev.starcore.starcore.module.army.raid.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.raid.NightRaidService;
import dev.starcore.starcore.module.army.raid.event.RaidEndEvent;
import dev.starcore.starcore.module.army.raid.event.RaidStartEvent;
import dev.starcore.starcore.module.army.raid.event.RaidAlertEvent;
import dev.starcore.starcore.module.army.raid.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 突袭事件监听器
 * 处理突袭相关的游戏事件
 */
public final class RaidListener implements Listener {
    private final NightRaidService raidService;
    private final NationService nationService;
    private final MessageService messages;

    public RaidListener(NightRaidService raidService, NationService nationService, MessageService messages) {
        this.raidService = raidService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 监听突袭开始事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRaidStart(RaidStartEvent event) {
        Raid raid = event.getRaid();

        // 获取攻击方和防守方名称
        String attackerName = nationService.nationById(raid.attackerNationId())
            .map(Nation::name)
            .orElse("Unknown");
        String defenderName = nationService.nationById(raid.targetNationId())
            .map(Nation::name)
            .orElse("Unknown");

        // 向所有在线玩家广播
        String message = messages.format("raid.broadcast.start", attackerName, defenderName);
        broadcastMessage(message, NamedTextColor.RED);

        // 向防守方成员发送特别通知
        notifyNationMembers(raid.targetNationId(),
            messages.format("raid.broadcast.target", attackerName),
            NamedTextColor.DARK_RED);
    }

    /**
     * 监听突袭结束事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRaidEnd(RaidEndEvent event) {
        Raid raid = event.getRaid();
        RaidResult result = raid.determineResult();

        String attackerName = nationService.nationById(raid.attackerNationId())
            .map(Nation::name)
            .orElse("Unknown");
        String defenderName = nationService.nationById(raid.targetNationId())
            .map(Nation::name)
            .orElse("Unknown");

        String resultMessage;
        NamedTextColor color;
        if (result == RaidResult.ATTACKER_VICTORY) {
            resultMessage = messages.format("raid.broadcast.attacker-victory", attackerName, defenderName);
            color = NamedTextColor.DARK_RED;
        } else if (result == RaidResult.DEFENDER_VICTORY) {
            resultMessage = messages.format("raid.broadcast.defender-victory", attackerName, defenderName);
            color = NamedTextColor.DARK_GREEN;
        } else {
            resultMessage = messages.format("raid.broadcast.draw", attackerName, defenderName);
            color = NamedTextColor.YELLOW;
        }

        broadcastMessage(resultMessage, color);

        // 显示突袭统计
        String statsMessage = messages.format("raid.broadcast.stats",
            raid.attackerTotalPoints(), raid.defenderTotalPoints());
        broadcastMessage(statsMessage, NamedTextColor.GRAY);
    }

    /**
     * 监听突袭警报事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRaidAlert(RaidAlertEvent event) {
        RaidAlert alert = event.getAlert();

        // 向目标国家成员发送警报
        notifyNationMembers(alert.targetNationId(), event.getMessage(), NamedTextColor.RED);
    }

    /**
     * 监听玩家加入事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 检查是否有待处理的警报
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return;
        }

        Optional<RaidAlert> alertOpt = raidService.getLatestAlert(nationOpt.get().id());
        if (alertOpt.isPresent() && !alertOpt.get().acknowledged()) {
            RaidAlert alert = alertOpt.get();
            String attackerName = nationService.nationById(alert.attackerNationId())
                .map(Nation::name)
                .orElse("Unknown");

            // 延迟发送，确保玩家已经完成登录
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(Component.text(
                        messages.format("raid.alert.welcome", attackerName, alert.targetLocation(), alert.remainingSeconds()),
                        NamedTextColor.RED
                    ));
                }
            }, 20L); // 延迟 1 秒
        }
    }

    /**
     * 监听玩家离开事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 更新玩家状态
        Optional<RaidParticipant> participantOpt = raidService.getParticipant(player.getUniqueId());
        if (participantOpt.isPresent()) {
            // 玩家离开突袭
            UUID raidId = findRaidIdForPlayer(player.getUniqueId());
            if (raidId != null) {
                raidService.leaveRaid(raidId, player);
            }
        }
    }

    /**
     * 监听玩家死亡事件（突袭中的战斗）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // 检查受害者是否在突袭中
        Optional<RaidParticipant> participantOpt = raidService.getParticipant(victim.getUniqueId());
        if (participantOpt.isEmpty()) {
            return;
        }

        UUID raidId = findRaidIdForPlayer(victim.getUniqueId());
        if (raidId == null) {
            return;
        }

        Optional<Raid> raidOpt = raidService.getRaid(raidId);
        if (raidOpt.isEmpty() || !raidOpt.get().isActive()) {
            return;
        }

        Raid raid = raidOpt.get();

        // 检查攻击者
        Player killer = victim.getKiller();
        if (killer != null) {
            Optional<RaidParticipant> killerOpt = raidService.getParticipant(killer.getUniqueId());
            if (killerOpt.isPresent()) {
                boolean victimIsAttacker = raid.isAttacker(victim.getUniqueId());
                boolean killerIsAttacker = raid.isAttacker(killer.getUniqueId());

                // 记录击杀
                if (victimIsAttacker != killerIsAttacker) {
                    raid.recordKill(killer.getUniqueId(), victim.getUniqueId(), killerIsAttacker);

                    // 发送通知
                    Component killMessage = Component.text(
                        messages.format("raid.kill.message",
                            killer.getName(),
                            victim.getName(),
                            killerIsAttacker ? "攻击方" : "防御方"),
                        killerIsAttacker ? NamedTextColor.GREEN : NamedTextColor.RED
                    );
                    broadcastToRaidParticipants(raid, killMessage);
                }
            }
        }

        // 记录死亡
        if (raid.isAttacker(victim.getUniqueId())) {
            RaidParticipant participant = raid.getAttacker(victim.getUniqueId());
            if (participant != null) {
                raid.removeAttacker(victim.getUniqueId());
                raid.addDefender(RaidParticipant.create(
                    victim.getUniqueId(),
                    victim.getName(),
                    participant.nationId()
                ).withDeath());
            }
        } else if (raid.isDefender(victim.getUniqueId())) {
            RaidParticipant participant = raid.getDefender(victim.getUniqueId());
            if (participant != null) {
                raid.removeDefender(victim.getUniqueId());
                raid.addAttacker(RaidParticipant.create(
                    victim.getUniqueId(),
                    victim.getName(),
                    participant.nationId()
                ).withDeath());
            }
        }

        // 检查是否需要结束突袭
        if (raid.attackerCount() < raidService.getConfig().minRaidParticipants()) {
            raidService.endRaid(raidId, "Not enough attackers");
        } else if (raid.defenderCount() < raidService.getConfig().minRaidParticipants()) {
            raidService.endRaid(raidId, "Not enough defenders");
        }
    }

    /**
     * 监听玩家移动事件（检测突袭区域边界）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨方块移动
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();

        // 检查玩家是否在突袭中
        Optional<RaidParticipant> participantOpt = raidService.getParticipant(player.getUniqueId());
        if (participantOpt.isEmpty()) {
            return;
        }

        UUID raidId = findRaidIdForPlayer(player.getUniqueId());
        if (raidId == null) {
            return;
        }

        Optional<Raid> raidOpt = raidService.getRaid(raidId);
        if (raidOpt.isEmpty()) {
            return;
        }

        Raid raid = raidOpt.get();
        if (!raid.isActive()) {
            return;
        }

        // 检查是否离开突袭区域（超出战斗半径）
        double distance = event.getTo().distance(raid.location());
        double maxRadius = raidService.getConfig().raidDurationMinutes() * 100.0; // 每分钟允许100格

        if (distance > maxRadius) {
            // 玩家离开战斗区域，可能被视为逃跑
            // 可以选择强制传送回突袭区域或离开突袭
        }
    }

    /**
     * 监听玩家伤害事件（突袭中的PVP）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // 检查受害者是否在突袭中
        Optional<RaidParticipant> victimParticipant = raidService.getParticipant(victim.getUniqueId());
        if (victimParticipant.isEmpty()) {
            return;
        }

        UUID victimRaidId = findRaidIdForPlayer(victim.getUniqueId());
        if (victimRaidId == null) {
            return;
        }

        Optional<Raid> raidOpt = raidService.getRaid(victimRaidId);
        if (raidOpt.isEmpty() || !raidOpt.get().isActive()) {
            return;
        }

        // 检查攻击者
        if (event.getDamager() instanceof Player attacker) {
            Optional<RaidParticipant> attackerParticipant = raidService.getParticipant(attacker.getUniqueId());

            if (attackerParticipant.isPresent()) {
                UUID attackerRaidId = findRaidIdForPlayer(attacker.getUniqueId());

                // 只有在不同阵营时才启用PVP
                if (!victimRaidId.equals(attackerRaidId)) {
                    // 不在同一个突袭中，不允许伤害
                    event.setCancelled(true);
                    return;
                }

                Raid victimRaid = raidOpt.get();
                boolean victimIsAttacker = victimRaid.isAttacker(victim.getUniqueId());
                boolean attackerIsAttacker = victimRaid.isAttacker(attacker.getUniqueId());

                // 不同阵营才允许PVP
                if (victimIsAttacker == attackerIsAttacker) {
                    // 同一阵营不能互相伤害
                    event.setCancelled(true);
                }
            }
        }
    }

    // ==================== 辅助方法 ====================

    private void broadcastMessage(String message, NamedTextColor color) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text(message, color));
        }
    }

    private void broadcastToRaidParticipants(Raid raid, Component message) {
        for (RaidParticipant attacker : raid.attackers()) {
            Player player = Bukkit.getPlayer(attacker.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
        for (RaidParticipant defender : raid.defenders()) {
            Player player = Bukkit.getPlayer(defender.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    private void notifyNationMembers(NationId nationId, String message, NamedTextColor color) {
        Nation nation = nationService.nationById(nationId).orElse(null);
        if (nation == null) {
            return;
        }

        for (UUID memberId : nation.members().stream().map(m -> m.playerId()).toList()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(Component.text(message, color));
            }
        }
    }

    private boolean isSameBlock(org.bukkit.Location from, org.bukkit.Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld());
    }

    private UUID findRaidIdForPlayer(UUID playerId) {
        List<Raid> playerRaids = raidService.getPlayerActiveRaids(playerId);
        return playerRaids.isEmpty() ? null : playerRaids.get(0).id();
    }

    private org.bukkit.plugin.Plugin plugin;

    public void setPlugin(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }
}