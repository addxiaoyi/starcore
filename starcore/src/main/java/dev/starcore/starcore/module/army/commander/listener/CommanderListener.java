package dev.starcore.starcore.module.army.commander.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.commander.CommanderConfig;
import dev.starcore.starcore.module.army.commander.CommanderService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.model.BattleResult;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指挥官事件监听器
 * 处理指挥官相关事件，如战斗、击杀、领土防守等
 */
public final class CommanderListener implements Listener {
    private final CommanderService commanderService;
    private final ArmyService armyService;
    private final NationService nationService;
    private final MessageService messages;
    private final CommanderConfig config;

    // 玩家参与的战斗记录 (playerId -> lastBattleTime)
    private final ConcurrentHashMap<UUID, Long> playerBattles = new ConcurrentHashMap<>();
    // 战斗冷却时间（毫秒）
    private static final long BATTLE_COOLDOWN_MS = 60_000; // 1分钟

    public CommanderListener(
        CommanderService commanderService,
        ArmyService armyService,
        NationService nationService,
        MessageService messages,
        CommanderConfig config
    ) {
        this.commanderService = commanderService;
        this.armyService = armyService;
        this.nationService = nationService;
        this.messages = messages;
        this.config = config;
    }

    /**
     * 玩家加入时加载指挥官数据
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        commanderService.loadPlayerData(event.getPlayer().getUniqueId());
    }

    /**
     * 玩家退出时保存指挥官数据
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        commanderService.savePlayerData(event.getPlayer().getUniqueId());
    }

    /**
     * 玩家死亡时处理经验奖励/惩罚
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        UUID playerId = player.getUniqueId();

        // 检查击杀者是否有国家
        if (killer != null) {
            Optional<Nation> killerNation = nationService.getNationByMember(killer.getUniqueId());
            Optional<Nation> victimNation = nationService.getNationByMember(playerId);

            if (killerNation.isPresent() && victimNation.isPresent()) {
                // 检查是否在交战状态
                if (!killerNation.get().id().equals(victimNation.get().id())) {
                    // 给予击杀经验
                    commanderService.addExperience(killer.getUniqueId(), config.expPerKill());

                    killer.sendMessage(Component.text(
                        messages.format("commander.exp.kill-bonus", config.expPerKill()),
                        NamedTextColor.YELLOW
                    ));
                }
            }
        }

        // 死亡惩罚
        int deathPenalty = Math.max(1, commanderService.getExperience(playerId) / 20);
        commanderService.consumeExperience(playerId, deathPenalty);
    }

    /**
     * 处理战斗结束事件 - 给予经验
     */
    public void onBattleEnd(BattleResult result) {
        if (!result.hasWinner()) {
            return;
        }

        UUID winnerId = result.winnerId();
        UUID loserId = result.loserId();

        if (winnerId == null || loserId == null) {
            return;
        }

        // 获取胜利方国家
        Optional<Nation> winnerNation = nationService.getNationByMember(winnerId);
        Optional<Nation> loserNation = nationService.getNationByMember(loserId);

        // 给予胜利经验
        if (winnerNation.isPresent()) {
            // 给予指挥官经验（如果胜利方有指挥官加成）
            int winnerExp = config.expPerVictory();
            commanderService.addExperience(winnerId, winnerExp);
        }

        // 给予参与战斗经验
        if (loserNation.isPresent()) {
            int loserExp = config.expFromBattle();
            commanderService.addExperience(loserId, loserExp);
        }
    }

    /**
     * 处理创建军队事件
     */
    public void onArmyCreated(UUID playerId, ArmyUnit army) {
        if (!config.enabled()) {
            return;
        }

        // 只有创建者的指挥官获得经验
        commanderService.addExperience(playerId, config.expPerArmyCreated());

        Player player = Optional.ofNullable(
            org.bukkit.Bukkit.getPlayer(playerId)
        ).orElse(null);

        if (player != null) {
            player.sendMessage(Component.text(
                messages.format("commander.exp.army-created", config.expPerArmyCreated()),
                NamedTextColor.YELLOW
            ));
        }
    }

    /**
     * 处理领土防守成功事件
     */
    public void onTerritoryDefended(UUID playerId) {
        if (!config.enabled()) {
            return;
        }

        // 检查冷却
        long lastDefended = playerBattles.getOrDefault(playerId, 0L);
        if (System.currentTimeMillis() - lastDefended < BATTLE_COOLDOWN_MS) {
            return;
        }

        commanderService.addExperience(playerId, config.expPerTerritoryDefended());
        playerBattles.put(playerId, System.currentTimeMillis());

        Player player = Optional.ofNullable(
            org.bukkit.Bukkit.getPlayer(playerId)
        ).orElse(null);

        if (player != null) {
            player.sendMessage(Component.text(
                messages.format("commander.exp.territory-defended", config.expPerTerritoryDefended()),
                NamedTextColor.YELLOW
            ));
        }
    }

    /**
     * 检查玩家是否参与战斗并给予经验
     */
    public void onBattleParticipation(UUID playerId) {
        // 检查冷却
        long lastBattle = playerBattles.getOrDefault(playerId, 0L);
        if (System.currentTimeMillis() - lastBattle < BATTLE_COOLDOWN_MS) {
            return;
        }

        commanderService.addExperience(playerId, config.expFromBattle());
        playerBattles.put(playerId, System.currentTimeMillis());
    }
}