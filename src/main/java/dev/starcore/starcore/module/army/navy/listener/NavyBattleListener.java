package dev.starcore.starcore.module.army.navy.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.navy.NavyService;
import dev.starcore.starcore.module.army.navy.model.NavyBattleResult;
import dev.starcore.starcore.module.army.navy.model.NavyUnit;
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
 * 监听玩家移动，触发舰队自动海战
 * 当敌方舰队接近时，自动触发海战
 */
public final class NavyBattleListener implements Listener {
    private final NavyService navyService;
    private final NationService nationService;
    private final Optional<DiplomacyService> diplomacyService;
    private final MessageService messages;

    // 战斗冷却记录 (攻击者ID ^ 防守者ID -> 上次战斗时间)
    private final ConcurrentHashMap<Long, Long> battleCooldowns = new ConcurrentHashMap<>();
    // 冷却时间（毫秒）: 5分钟
    private static final long BATTLE_COOLDOWN_MS = 5 * 60 * 1000;
    // 检测半径（方块）- 海战范围更大
    private static final double DETECTION_RADIUS = 100.0;
    // 海战触发半径（方块）
    private static final double BATTLE_RADIUS = 60.0;

    public NavyBattleListener(
        NavyService navyService,
        NationService nationService,
        Optional<DiplomacyService> diplomacyService,
        MessageService messages
    ) {
        this.navyService = navyService;
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

        // 检查附近是否有敌方舰队
        List<NavyUnit> nearbyNavies = navyService.getNaviesNear(location, DETECTION_RADIUS);

        for (NavyUnit navy : nearbyNavies) {
            // 跳过己方舰队
            Optional<Nation> playerNation = nationService.getNationByMember(player.getUniqueId());
            if (playerNation.isPresent() && navy.nationId().equals(playerNation.get().id().value())) {
                continue;
            }

            // 检查是否为敌对舰队
            if (!isEnemyNavy(navy, player)) {
                continue;
            }

            // 检查舰队是否处于可战斗状态
            if (!navy.canFight()) {
                continue;
            }

            // 触发自动海战
            triggerAutoBattle(navy, location);
            break; // 每次移动只触发一次战斗
        }
    }

    /**
     * 触发自动海战
     */
    private void triggerAutoBattle(NavyUnit attacker, Location target) {
        // 获取附近的友军舰队
        List<NavyUnit> nearbyNavies = navyService.getNaviesNear(target, BATTLE_RADIUS);

        for (NavyUnit defender : nearbyNavies) {
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

            // 检查防守舰队是否可战斗
            if (!defender.canFight()) {
                continue;
            }

            // 执行海战
            try {
                NavyBattleResult result = navyService.engage(attacker.id(), defender.id());

                // 记录冷却
                battleCooldowns.put(cooldownKey, System.currentTimeMillis());

                // 清理过期冷却（每100次清理一次）
                if (battleCooldowns.size() > 1000) {
                    cleanupExpiredCooldowns();
                }

                // 记录海战结果
                onBattleResult(result);

            } catch (IllegalStateException | IllegalArgumentException e) {
                // 战斗失败，可能是舰队已被消灭
            }

            return; // 每次只触发一场战斗
        }
    }

    /**
     * 检查两支舰队是否为敌对关系
     */
    private boolean isEnemyNavy(NavyUnit navy, Player player) {
        Optional<Nation> playerNation = nationService.getNationByMember(player.getUniqueId());

        if (playerNation.isEmpty()) {
            // 玩家没有国家，只有在宣战后才会触发
            return false;
        }

        // 检查外交关系
        if (diplomacyService.isPresent()) {
            var relation = diplomacyService.get().relationBetween(
                playerNation.get().id(),
                new NationId(navy.nationId())
            );

            return relation == DiplomacyRelation.WAR
                || relation == DiplomacyRelation.HOSTILE
                || relation == DiplomacyRelation.CEASE_FIRE; // 停火期也可触发
        }

        // 没有外交服务时，默认不同国家即为敌对
        return !playerNation.get().id().value().equals(navy.nationId());
    }

    /**
     * 处理海战结果
     */
    private void onBattleResult(NavyBattleResult result) {
        if (!result.hasWinner()) {
            return;
        }

        // 这里可以添加海战结果的通知逻辑
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
