package dev.starcore.starcore.module.army.mercenary.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.mercenary.MercenaryContract;
import dev.starcore.starcore.module.army.mercenary.MercenaryService;
import dev.starcore.starcore.module.army.mercenary.MercenaryType;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryContractEndedEvent;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryHiredEvent;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryMissionCompletedEvent;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryPromotedEvent;
import dev.starcore.starcore.module.nation.NationService;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;

/**
 * 雇佣兵事件监听器
 * 处理雇佣兵相关的游戏事件
 */
public final class MercenaryListener implements Listener {
    private final MercenaryService mercenaryService;
    private final NationService nationService;
    private final MessageService messages;

    public MercenaryListener(
        MercenaryService mercenaryService,
        NationService nationService,
        MessageService messages
    ) {
        this.mercenaryService = mercenaryService;
        this.nationService = nationService;
        this.messages = messages;
    }

    // ==================== 雇佣兵事件处理 ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onMercenaryHired(MercenaryHiredEvent event) {
        // 雇佣兵被雇佣时的处理
        Player mercenary = Bukkit.getPlayer(event.getMercenaryId());
        if (mercenary != null && mercenary.isOnline()) {
            mercenary.sendMessage(Component.text(
                messages.format("mercenary.event.hired",
                    event.getNationName(),
                    event.getType().displayName(),
                    event.getContractDurationDays()),
                NamedTextColor.GREEN
            ));

            // 如果雇佣兵有国家，发警告
            nationService.getNationByMember(mercenary.getUniqueId()).ifPresent(nation -> {
                mercenary.sendMessage(Component.text(
                    messages.format("mercenary.event.warning-same-nation"),
                    NamedTextColor.YELLOW
                ));
            });
        }

        // 通知雇主
        Player employer = Bukkit.getPlayer(event.getEmployerId());
        if (employer != null && employer.isOnline()) {
            employer.sendMessage(Component.text(
                messages.format("mercenary.event.employer-hired",
                    event.getMercenaryName(),
                    event.getNationName()),
                NamedTextColor.GREEN
            ));
        }

        // 广播给国家成员（可选）
        // nationService.getNationMembers(event.getNationId()).forEach(member -> {...});
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMercenaryPromoted(MercenaryPromotedEvent event) {
        // 雇佣兵晋升时的处理
        Player mercenary = Bukkit.getPlayer(event.getMercenaryId());
        if (mercenary != null && mercenary.isOnline()) {
            mercenary.sendMessage(Component.text(
                messages.format("mercenary.event.promoted",
                    event.getNewRank().displayName()),
                NamedTextColor.GOLD
            ));
        }

        // 通知雇主
        Player employer = Bukkit.getPlayer(event.getEmployerId());
        if (employer != null && employer.isOnline()) {
            employer.sendMessage(Component.text(
                messages.format("mercenary.event.employer-promoted",
                    event.getMercenaryName(),
                    event.getPreviousRank().displayName(),
                    event.getNewRank().displayName()),
                NamedTextColor.GREEN
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onContractEnded(MercenaryContractEndedEvent event) {
        // 合同结束时的处理
        Player mercenary = Bukkit.getPlayer(event.getMercenaryId());
        if (mercenary != null && mercenary.isOnline()) {
            mercenary.sendMessage(Component.text(
                messages.format("mercenary.event.contract-ended",
                    event.getReason().displayName()),
                NamedTextColor.YELLOW
            ));

            // 统计信息
            mercenary.sendMessage(Component.text(
                messages.format("mercenary.event.final-stats",
                    event.getTotalSalaryPaid(),
                    event.getMissionsCompleted()),
                NamedTextColor.GRAY
            ));
        }

        // 通知雇主
        Player employer = Bukkit.getPlayer(event.getEmployerId());
        if (employer != null && employer.isOnline()) {
            employer.sendMessage(Component.text(
                messages.format("mercenary.event.employer-contract-ended",
                    event.getMercenaryName(),
                    event.getReason().displayName()),
                NamedTextColor.YELLOW
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMissionCompleted(MercenaryMissionCompletedEvent event) {
        // 任务完成时的处理
        Player mercenary = Bukkit.getPlayer(event.getMercenaryId());
        if (mercenary != null && mercenary.isOnline()) {
            mercenary.sendMessage(Component.text(
                messages.format("mercenary.event.mission-completed",
                    event.getMissionType(),
                    event.getReward()),
                NamedTextColor.GREEN
            ));
        }
    }

    // ==================== 战斗事件处理 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // 检查受害者是否是雇佣兵
        if (mercenaryService.isMercenary(victim.getUniqueId())) {
            mercenaryService.recordDeath(victim.getUniqueId());

            // 记录雇佣兵死亡
            event.getEntity().sendMessage(Component.text(
                messages.format("mercenary.death.recorded"),
                NamedTextColor.RED
            ));
        }

        // 检查击杀者是否是雇佣兵
        if (killer != null && mercenaryService.isMercenary(killer.getUniqueId())) {
            mercenaryService.recordKill(killer.getUniqueId());

            killer.sendMessage(Component.text(
                messages.format("mercenary.kill.recorded"),
                NamedTextColor.GREEN
            ));

            // 给予奖励
            Optional<MercenaryContract> contract = mercenaryService.getMercenaryContract(killer.getUniqueId());
            contract.ifPresent(c -> {
                int killBonus = c.type().baseCost() / 10;
                c.addSalary(killBonus);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMercenaryDamage(EntityDamageByEntityEvent event) {
        // 雇佣兵战斗时更新位置
        if (event.getDamager() instanceof Player attacker) {
            if (mercenaryService.isMercenary(attacker.getUniqueId())) {
                mercenaryService.getMercenaryContract(attacker.getUniqueId())
                    .ifPresent(contract -> contract.updateLastLocation(attacker.getLocation()));
            }
        }
    }

    // ==================== 玩家进出事件 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 检查玩家是否是雇佣兵
        if (mercenaryService.isMercenary(player.getUniqueId())) {
            mercenaryService.getMercenaryContract(player.getUniqueId())
                .ifPresent(contract -> {
                    contract.updateLastLocation(player.getLocation());

                    // 合同即将到期提醒
                    if (contract.isActive() && contract.getRemainingDays() <= 3) {
                        player.sendMessage(Component.text(
                            messages.format("mercenary.event.contract-expiring",
                                contract.getRemainingDays()),
                            NamedTextColor.YELLOW
                        ));
                    }
                });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 更新雇佣兵最后活跃位置
        if (mercenaryService.isMercenary(player.getUniqueId())) {
            mercenaryService.getMercenaryContract(player.getUniqueId())
                .ifPresent(contract -> contract.updateLastLocation(player.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 雇佣兵复活时更新位置
        if (mercenaryService.isMercenary(player.getUniqueId())) {
            mercenaryService.getMercenaryContract(player.getUniqueId())
                .ifPresent(contract -> {
                    contract.updateLastLocation(event.getRespawnLocation());

                    // 死亡惩罚提醒
                    player.sendMessage(Component.text(
                        messages.format("mercenary.event.respawn-penalty",
                            contract.deaths()),
                        NamedTextColor.RED
                    ));
                });
        }
    }

    }