package dev.starcore.starcore.module.army.wounded.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.model.BattleResult;
import dev.starcore.starcore.module.army.wounded.WoundedService;
import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedRecord;
import dev.starcore.starcore.module.nation.NationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伤兵事件监听器
 * 处理战斗后伤兵生成、玩家死亡与康复等事件
 */
public final class WoundedListener implements Listener {
    private final WoundedService woundedService;
    private final NationService nationService;
    private final MessageService messages;
    private final Plugin plugin;

    // 玩家死亡记录（用于检测战斗死亡）
    private final ConcurrentHashMap<UUID, Long> recentBattleDeaths = new ConcurrentHashMap<>();
    // 战斗死亡检测时间窗口（毫秒）
    private static final long BATTLE_DEATH_WINDOW_MS = 30_000; // 30秒内视为战斗死亡

    public WoundedListener(
        WoundedService woundedService,
        NationService nationService,
        MessageService messages,
        Plugin plugin
    ) {
        this.woundedService = woundedService;
        this.nationService = nationService;
        this.messages = messages;
        this.plugin = plugin;
    }

    /**
     * 处理玩家战斗死亡事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 检查玩家是否属于某个国家
        Optional<dev.starcore.starcore.module.nation.model.Nation> nationOpt =
            nationService.nationOf(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            return; // 无国家玩家不处理
        }

        // 检查是否有战斗死亡标记
        long lastCombat = recentBattleDeaths.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastCombat > BATTLE_DEATH_WINDOW_MS) {
            return; // 非战斗死亡
        }

        // 清理标记
        recentBattleDeaths.remove(player.getUniqueId());
    }

    /**
     * 处理玩家复活事件
     * 提示玩家有伤兵需要治疗
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 检查玩家是否有伤兵
        List<WoundedRecord> playerWounded = woundedService.getPlayerWounded(player.getUniqueId());

        if (!playerWounded.isEmpty()) {
            int totalWounded = playerWounded.stream()
                .mapToInt(WoundedRecord::currentWounded)
                .sum();

            // 延迟发送消息，等玩家复活后
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(Component.text(
                    messages.format("wounded.player.respawn-notice", totalWounded),
                    NamedTextColor.YELLOW
                ));
            }, 20L); // 1秒后发送
        }
    }

    /**
     * 标记玩家为战斗死亡
     * 由战斗系统调用
     */
    public void markBattleDeath(Player player) {
        recentBattleDeaths.put(player.getUniqueId(), System.currentTimeMillis());

        // 清理过期记录
        if (recentBattleDeaths.size() > 1000) {
            cleanupExpiredRecords();
        }
    }

    /**
     * 处理战斗结果，将伤亡转为伤兵
     * @param result 战斗结果
     * @param attacker 攻击方军队
     * @param defender 防守方军队
     */
    public void onBattleResult(BattleResult result, ArmyUnit attacker, ArmyUnit defender) {
        // 计算伤亡
        int attackerLosses = calculateLosses(attacker, result);
        int defenderLosses = calculateLosses(defender, result);

        // 将伤亡转为伤兵
        if (attackerLosses > 0) {
            try {
                woundedService.createWounded(attacker, attackerLosses);
            } catch (Exception e) {
                // 可能是伤兵上限已满，士兵直接死亡
            }
        }

        if (defenderLosses > 0) {
            try {
                woundedService.createWounded(defender, defenderLosses);
            } catch (Exception e) {
                // 可能是伤兵上限已满，士兵直接死亡
            }
        }
    }

    /**
     * 从战斗结果计算伤亡数量
     */
    private int calculateLosses(ArmyUnit army, BattleResult result) {
        if (result == null || !result.hasWinner()) {
            return 0;
        }

        // 简化计算：基于生命值损失计算伤亡
        double healthLoss = 100.0 - army.health();
        int estimatedLosses = (int) (army.soldiers() * (healthLoss / 100.0) * 0.5); // 50%的生命值损失转为伤兵

        return Math.max(0, estimatedLosses);
    }

    /**
     * 清理过期的死亡记录
     */
    private void cleanupExpiredRecords() {
        long now = System.currentTimeMillis();
        recentBattleDeaths.entrySet().removeIf(entry ->
            now - entry.getValue() > BATTLE_DEATH_WINDOW_MS
        );
    }

    // E-057 修复: 玩家退出时清理战斗死亡记录
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        recentBattleDeaths.remove(event.getPlayer().getUniqueId());
    }
}