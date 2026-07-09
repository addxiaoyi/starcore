package dev.starcore.starcore.module.army.exercise.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.exercise.Exercise;
import dev.starcore.starcore.module.army.exercise.ExerciseBattleResult;
import dev.starcore.starcore.module.army.exercise.ExerciseParticipant;
import dev.starcore.starcore.module.army.exercise.ExerciseService;
import dev.starcore.starcore.module.army.exercise.ExerciseState;
import dev.starcore.starcore.module.army.exercise.event.ExerciseBattleEvent;
import dev.starcore.starcore.module.army.exercise.event.ExerciseEndedEvent;
import dev.starcore.starcore.module.army.exercise.event.ExerciseJoinedEvent;
import dev.starcore.starcore.module.army.exercise.event.ExerciseStartedEvent;
import dev.starcore.starcore.module.nation.NationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演习事件监听器
 * 处理演习相关的事件，如战斗、玩家移动、离开等
 */
public final class ExerciseListener implements Listener {
    private final ExerciseService exerciseService;
    private final NationService nationService;
    private final MessageService messages;

    // 玩家参与状态追踪
    private final Map<UUID, UUID> playerExerciseMap = new ConcurrentHashMap<>();
    // 战斗冷却
    private final Map<String, Long> battleCooldowns = new ConcurrentHashMap<>();
    // 冷却时间（毫秒）
    private static final long BATTLE_COOLDOWN_MS = 30000; // 30秒
    // 检测半径（方块）
    private static final double DETECTION_RADIUS = 30.0;

    public ExerciseListener(
        ExerciseService exerciseService,
        NationService nationService,
        MessageService messages
    ) {
        this.exerciseService = exerciseService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 注册玩家参与演习
     */
    public void registerParticipation(UUID playerId, UUID exerciseId) {
        playerExerciseMap.put(playerId, exerciseId);
    }

    /**
     * 取消玩家参与演习
     */
    public void unregisterParticipation(UUID playerId) {
        playerExerciseMap.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExerciseStarted(ExerciseStartedEvent event) {
        Exercise exercise = event.getExercise();

        // 广播演习开始消息
        String message = messages.format("exercise.broadcast.started",
            exercise.name(),
            exercise.type().displayName(),
            exercise.participantCount());

        for (UUID nationId : exercise.getNationIds()) {
            nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId))
                .ifPresent(nation -> {
                    for (var member : nation.members()) {
                        Player player = Bukkit.getPlayer(member.playerId());
                        if (player != null) {
                            player.sendMessage(Component.text(message, NamedTextColor.GOLD));
                        }
                    }
                });
        }

        // 通知所有参与者
        for (var participant : exercise.participants()) {
            Player player = Bukkit.getPlayer(participant.nationId());
            if (player != null) {
                player.sendMessage(Component.text(
                    messages.format("exercise.started.notify", exercise.name()),
                    NamedTextColor.GREEN
                ));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onExerciseEnded(ExerciseEndedEvent event) {
        Exercise exercise = event.getExercise();
        var result = event.getResult();

        // 广播演习结束消息
        String message = messages.format("exercise.broadcast.ended",
            exercise.name(),
            event.reason());

        for (UUID nationId : exercise.getNationIds()) {
            nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId))
                .ifPresent(nation -> {
                    for (var member : nation.members()) {
                        Player player = Bukkit.getPlayer(member.playerId());
                        if (player != null) {
                            player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
                            // 显示结果
                            player.sendMessage(Component.text(result.formatReport(), NamedTextColor.WHITE));
                        }
                    }
                });
        }

        // 清理玩家参与状态
        playerExerciseMap.entrySet().removeIf(entry -> entry.getValue().equals(exercise.id()));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onExerciseJoined(ExerciseJoinedEvent event) {
        Exercise exercise = event.getExercise();

        // 广播玩家加入消息
        for (UUID nationId : exercise.getNationIds()) {
            nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId))
                .ifPresent(nation -> {
                    for (var member : nation.members()) {
                        Player player = Bukkit.getPlayer(member.playerId());
                        if (player != null) {
                            player.sendMessage(Component.text(
                                messages.format("exercise.broadcast.joined",
                                    event.getNationName(),
                                    exercise.name(),
                                    event.getSoldierCount()),
                                NamedTextColor.GRAY
                            ));
                        }
                    }
                });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExerciseBattle(ExerciseBattleEvent event) {
        ExerciseBattleResult result = event.getResult();

        // 检查是否有胜者
        if (!result.hasWinner()) {
            return;
        }

        // 广播战斗结果
        String message = messages.format("exercise.broadcast.battle",
            result.attackerNationName(),
            result.defenderNationName(),
            result.winnerName(),
            result.attackerLosses(),
            result.defenderLosses());

        // 获取演习信息以确定参与者
        exerciseService.getExercise(result.exerciseId()).ifPresent(exercise -> {
            for (UUID nationId : exercise.getNationIds()) {
                nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId))
                    .ifPresent(nation -> {
                        for (var member : nation.members()) {
                            Player player = Bukkit.getPlayer(member.playerId());
                            if (player != null) {
                                player.sendMessage(Component.text(message, NamedTextColor.RED));
                            }
                        }
                    });
            }
        });
    }

    /**
     * 玩家移动时检查是否进入演习区域
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨方块移动，忽略仅视角移动
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();

        // 检查玩家是否参与演习
        UUID exerciseId = playerExerciseMap.get(player.getUniqueId());
        if (exerciseId == null) {
            return;
        }

        Optional<Exercise> exerciseOpt = exerciseService.getExercise(exerciseId);
        if (exerciseOpt.isEmpty() || !exerciseOpt.get().state().isActive()) {
            playerExerciseMap.remove(player.getUniqueId());
            return;
        }

        Exercise exercise = exerciseOpt.get();

        // 检查是否在演习区域内
        if (!isInExerciseZone(event.getTo(), exercise)) {
            return;
        }

        // 检查附近的敌对玩家
        for (var participant : exercise.participants()) {
            if (participant.nationId().equals(getPlayerNationId(player))) {
                continue; // 跳过己方
            }

            Player target = Bukkit.getPlayer(participant.nationId());
            if (target != null && target.getLocation().distance(event.getTo()) <= DETECTION_RADIUS) {
                // 触发战斗
                triggerExerciseBattle(exercise, player, target);
                break;
            }
        }
    }

    /**
     * 玩家退出时处理
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        UUID exerciseId = playerExerciseMap.remove(playerId);

        if (exerciseId != null) {
            Optional<Exercise> exerciseOpt = exerciseService.getExercise(exerciseId);
            exerciseOpt.ifPresent(exercise -> {
                if (exercise.state().isActive()) {
                    // 玩家退出可能影响演习
                    handlePlayerDisconnection(exercise, playerId);
                }
            });
        }
    }

    /**
     * 玩家死亡时处理
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID exerciseId = playerExerciseMap.get(player.getUniqueId());

        if (exerciseId != null) {
            Optional<Exercise> exerciseOpt = exerciseService.getExercise(exerciseId);
            exerciseOpt.ifPresent(exercise -> {
                if (exercise.state().isActive()) {
                    // 处理演习中的死亡
                    handleExerciseDeath(exercise, player, event);
                }
            });
        }
    }

    // ==================== Private Methods ====================

    private UUID getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId())
            .map(n -> n.id().value())
            .orElse(null);
    }

    private boolean isSameBlock(org.bukkit.Location from, org.bukkit.Location to) {
        if (from == null || to == null) {
            return false;
        }
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld());
    }

    private boolean isInExerciseZone(org.bukkit.Location location, Exercise exercise) {
        if (location.getWorld() == null) {
            return false;
        }

        // 如果演习设置了区域，检查是否在区域内
        if (exercise.world().isPresent() && exercise.radius() > 0) {
            String worldName = exercise.world().orElse("");
            if (!location.getWorld().getName().equals(worldName)) {
                return false;
            }

            double centerX = exercise.centerX().orElse(0);
            double centerZ = exercise.centerZ().orElse(0);
            double dx = location.getX() - centerX;
            double dz = location.getZ() - centerZ;
            return Math.sqrt(dx * dx + dz * dz) <= exercise.radius();
        }

        // 默认允许在演习世界中的任何地方
        return true;
    }

    private void triggerExerciseBattle(Exercise exercise, Player attacker, Player defender) {
        String cooldownKey = exercise.id().toString() + "_" + cooldownKey(attacker.getUniqueId(), defender.getUniqueId());

        // 检查冷却
        long lastBattle = battleCooldowns.getOrDefault(cooldownKey, 0L);
        if (System.currentTimeMillis() - lastBattle < BATTLE_COOLDOWN_MS) {
            return;
        }

        UUID attackerNationId = getPlayerNationId(attacker);
        UUID defenderNationId = getPlayerNationId(defender);

        if (attackerNationId == null || defenderNationId == null) {
            return;
        }

        // 执行战斗
        ExerciseBattleResult result = exerciseService.processBattle(
            exercise.id(),
            attackerNationId,
            defenderNationId
        );

        if (result != null) {
            battleCooldowns.put(cooldownKey, System.currentTimeMillis());

            // 触发战斗事件
            ExerciseBattleEvent battleEvent = new ExerciseBattleEvent(result);
            Bukkit.getPluginManager().callEvent(battleEvent);

            // 应用战斗结果到玩家
            applyBattleResultToPlayer(attacker, result, true);
            applyBattleResultToPlayer(defender, result, false);
        }
    }

    private void applyBattleResultToPlayer(Player player, ExerciseBattleResult result, boolean isAttacker) {
        int losses = isAttacker ? result.attackerLosses() : result.defenderLosses();
        double moraleChange = isAttacker ? result.attackerMoraleChange() : result.defenderMoraleChange();

        String message;
        if (result.attackerWon() == isAttacker) {
            message = messages.format("exercise.battle.victory", losses);
        } else if (result.isDraw()) {
            message = messages.format("exercise.battle.draw", losses);
        } else {
            message = messages.format("exercise.battle.defeat", losses);
        }

        player.sendMessage(Component.text(message, NamedTextColor.GOLD));
    }

    private void handlePlayerDisconnection(Exercise exercise, UUID playerId) {
        // 通知其他参与者
        nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(playerId))
            .ifPresent(nation -> {
                for (var participant : exercise.participants()) {
                    if (participant.nationId().equals(playerId)) {
                        // 广播玩家退出
                        String message = messages.format("exercise.broadcast.disconnect", nation.name());
                        for (UUID nationId : exercise.getNationIds()) {
                            nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId))
                                .ifPresent(n -> {
                                    for (var member : n.members()) {
                                        Player player = Bukkit.getPlayer(member.playerId());
                                        if (player != null) {
                                            player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
                                        }
                                    }
                                });
                        }
                        break;
                    }
                }
            });
    }

    private void handleExerciseDeath(Exercise exercise, Player player, PlayerDeathEvent event) {
        UUID playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            return;
        }

        // 减少士兵数量
        Optional<ExerciseParticipant> participant = exercise.getParticipant(playerNationId);
        if (participant.isPresent()) {
            int deathPenalty = Math.min(5, participant.get().soldierCount() / 20); // 最多损失5%士兵
            participant.get().addCasualties(deathPenalty);

            // 广播死亡消息
            String message = messages.format("exercise.broadcast.death",
                player.getName(),
                participant.get().nationName(),
                deathPenalty);

            for (UUID nationId : exercise.getNationIds()) {
                nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId))
                    .ifPresent(nation -> {
                        for (var member : nation.members()) {
                            Player p = Bukkit.getPlayer(member.playerId());
                            if (p != null) {
                                p.sendMessage(Component.text(message, NamedTextColor.DARK_RED));
                            }
                        }
                    });
            }
        }
    }

    private long cooldownKey(UUID a, UUID b) {
        return ((long) a.hashCode()) ^ ((long) b.hashCode());
    }
}
