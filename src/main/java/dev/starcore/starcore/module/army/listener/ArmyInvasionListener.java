package dev.starcore.starcore.module.army.listener;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyState;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.model.BattleResult;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听玩家移动，触发军队自动战斗
 * 当敌方军队接近己方领土或玩家时，自动触发战斗
 */
public final class ArmyInvasionListener implements Listener {
    private final ArmyService armyService;
    private final NationService nationService;
    private final Optional<DiplomacyService> diplomacyService;
    private final MessageService messages;

    // 战斗冷却记录 (攻击者ID ^ 防守者ID -> 上次战斗时间)
    private final ConcurrentHashMap<Long, Long> battleCooldowns = new ConcurrentHashMap<>();
    // 冷却时间（毫秒）: 5分钟
    private static final long BATTLE_COOLDOWN_MS = 5 * 60 * 1000;
    // 检测半径（方块）
    private static final double DETECTION_RADIUS = 50.0;
    // 战斗触发半径（方块）
    private static final double BATTLE_RADIUS = 30.0;

    public ArmyInvasionListener(
        ArmyService armyService,
        NationService nationService,
        Optional<DiplomacyService> diplomacyService,
        MessageService messages
    ) {
        this.armyService = armyService;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨方块移动，忽略仅视角移动
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        Location location = event.getTo();

        // 检查附近是否有敌方军队
        List<ArmyUnit> nearbyArmies = armyService.getArmiesNear(location, DETECTION_RADIUS);

        for (ArmyUnit army : nearbyArmies) {
            // 跳过己方军队
            Optional<Nation> playerNation = nationService.getNationByMember(player.getUniqueId());
            if (playerNation.isPresent() && army.nationId().equals(playerNation.get().id().value())) {
                continue;
            }

            // 检查是否为敌对军队
            if (!isEnemyArmy(army, player)) {
                continue;
            }

            // 检查军队是否处于可战斗状态
            if (!army.canFight()) {
                continue;
            }

            // 触发自动战斗
            triggerAutoBattle(army, location);
            break; // 每次移动只触发一次战斗
        }
    }

    /**
     * 触发自动战斗
     */
    private void triggerAutoBattle(ArmyUnit attacker, Location target) {
        // 获取附近的友军防守
        List<ArmyUnit> nearbyArmies = armyService.getArmiesNear(target, BATTLE_RADIUS);

        for (ArmyUnit defender : nearbyArmies) {
            // 只与友军战斗
            if (defender.nationId().equals(attacker.nationId())) {
                continue;
            }

            // 检查战斗冷却
            long cooldownKey = cooldownKey(attacker.id(), defender.id());
            long lastBattle = battleCooldowns.getOrDefault(cooldownKey, 0L);

            if (System.currentTimeMillis() - lastBattle < BATTLE_COOLDOWN_MS) {
                continue;
            }

            // 检查防守军队是否可战斗
            if (!defender.canFight()) {
                continue;
            }

            // 执行战斗
            try {
                BattleResult result = armyService.attack(attacker.id(), defender.id());

                // 记录冷却
                battleCooldowns.put(cooldownKey, System.currentTimeMillis());

                // 清理过期冷却（每100次清理一次）
                if (battleCooldowns.size() > 1000) {
                    cleanupExpiredCooldowns();
                }

                // 记录战斗结果（可选：发送消息给玩家）
                onBattleResult(result);

            } catch (IllegalStateException | IllegalArgumentException e) {
                // 战斗失败，可能是军队已被消灭
            }

            return; // 每次只触发一场战斗
        }
    }

    /**
     * 检查两支军队是否为敌对关系
     */
    private boolean isEnemyArmy(ArmyUnit army, Player player) {
        Optional<Nation> playerNation = nationService.getNationByMember(player.getUniqueId());

        if (playerNation.isEmpty()) {
            // 玩家没有国家，只有在宣战后才会触发
            return false;
        }

        // 检查外交关系
        if (diplomacyService.isPresent()) {
            var relation = diplomacyService.get().relationBetween(
                playerNation.get().id(),
                new NationId(army.nationId())
            );

            return relation == DiplomacyRelation.WAR
                || relation == DiplomacyRelation.HOSTILE
                || relation == DiplomacyRelation.CEASE_FIRE; // 停火期也可触发
        }

        // 没有外交服务时，默认不同国家即为敌对
        return !playerNation.get().id().value().equals(army.nationId());
    }

    /**
     * 处理战斗结果
     */
    private void onBattleResult(BattleResult result) {
        if (!result.hasWinner()) {
            return;
        }

        // 这里可以添加战斗结果的通知逻辑
        // 例如：发送战斗报告给相关玩家、记录战斗日志等
    }

    /**
     * 计算冷却键
     */
    private long cooldownKey(UUID attackerId, UUID defenderId) {
        return ((long) attackerId.hashCode()) ^ ((long) defenderId.hashCode());
    }

    /**
     * 清理过期的冷却记录
     */
    private void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        battleCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > BATTLE_COOLDOWN_MS);
    }

    /**
     * 检查两点是否为同一方块
     */
    private boolean isSameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld());
    }
}
