package dev.starcore.starcore.pvp;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.pvp.command.DuelCommand;
import dev.starcore.starcore.pvp.command.PvPStatsCommand;
import dev.starcore.starcore.pvp.duel.Duel;
import dev.starcore.starcore.pvp.duel.DuelService;
import dev.starcore.starcore.pvp.killstreak.KillStreakService;
import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * PvP模块
 * 包含决斗系统、连杀系统、统计系统
 */
public final class PvPModule implements StarCoreModule, Listener {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "pvp",
        "PvP系统",
        ModuleLayer.FEATURE,
        List.of(),
        List.of(DuelService.class, KillStreakService.class, PvPStatsService.class),
        "PvP decision system, killstreak tracking, and statistics"
    );

    private Plugin plugin;
    private DuelService duelService;
    private KillStreakService killStreakService;
    private PvPStatsService statsService;
    private DatabaseService databaseService;
    private File pvpConfigFile;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.databaseService = context.databaseService();
        plugin.getLogger().info("正在启用 PvP 模块...");

        // 保存默认配置
        saveDefaultConfig();

        // 加载 PvP 配置
        FileConfiguration pvpConfig = loadPvpConfig();

        // 初始化服务
        this.duelService = new DuelService(databaseService, plugin.getLogger(), plugin);
        this.killStreakService = new KillStreakService();
        this.statsService = new PvPStatsService(plugin);

        // 初始化连杀服务（加载持久化数据）
        killStreakService.initialize(plugin);

        // 注入经济服务（用于决斗奖励）
        context.serviceRegistry().find(dev.starcore.starcore.foundation.economy.EconomyService.class)
            .ifPresent(duelService::setEconomyService);

        // 加载配置到服务
        duelService.loadConfig(pvpConfig);
        duelService.start();
        killStreakService.loadConfig(pvpConfig);

        // 注册到服务表，供其他模块/GUI 读取
        context.serviceRegistry().register(PvPStatsService.class, statsService);
        context.serviceRegistry().register(DuelService.class, duelService);
        context.serviceRegistry().register(KillStreakService.class, killStreakService);

        // 注册事件监听器
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 注册命令
        registerCommands();

        // 注册定时任务
        registerScheduledTasks();

        plugin.getLogger().info("✅ PvP 模块已启用");
    }

    private void saveDefaultConfig() {
        pvpConfigFile = new File(plugin.getDataFolder(), "pvp_config.yml");
        if (!pvpConfigFile.exists()) {
            plugin.saveResource("pvp_config.yml", false);
        }
    }

    private FileConfiguration loadPvpConfig() {
        if (pvpConfigFile == null) {
            pvpConfigFile = new File(plugin.getDataFolder(), "pvp_config.yml");
        }
        if (pvpConfigFile.exists()) {
            return YamlConfiguration.loadConfiguration(pvpConfigFile);
        }
        return new YamlConfiguration();
    }

    @Override
    public void disable(StarCoreContext context) {
        plugin.getLogger().info("正在禁用 PvP 模块...");

        // 保存数据
        saveAllData();

        // 停止服务
        if (duelService != null) {
            duelService.stop();
        }

        plugin.getLogger().info("✅ PvP 模块已禁用");
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        // 注册决斗命令
        PluginCommand duelCommand = plugin.getServer().getPluginCommand("duel");
        if (duelCommand != null) {
            DuelCommand duelCommandExecutor = new DuelCommand(duelService);
            duelCommand.setExecutor(duelCommandExecutor);
            duelCommand.setTabCompleter(duelCommandExecutor);
        } else {
            plugin.getLogger().warning("决斗命令未在 plugin.yml 中声明");
        }

        // 注册PvP统计命令
        PluginCommand statsCommand = plugin.getServer().getPluginCommand("pvpstats");
        if (statsCommand != null) {
            PvPStatsCommand statsCommandExecutor = new PvPStatsCommand(statsService);
            statsCommand.setExecutor(statsCommandExecutor);
            statsCommand.setTabCompleter(statsCommandExecutor);
        }
    }

    /**
     * 注册定时任务
     */
    private void registerScheduledTasks() {
        // 定期清理过期的决斗请求
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (duelService != null) {
                // 清理超时的决斗请求
                duelService.cleanupExpiredRequests();
            }
        }, 20 * 60, 20 * 60); // 每分钟检查一次

        // 定期保存统计
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (statsService != null) {
                for (var entry : statsService.getAllStats().entrySet()) {
                    try {
                        statsService.savePlayerStats(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "无法保存玩家 " + entry.getKey() + " 的PvP数据", e);
                    }
                }
            }
        }, 20 * 60 * 5, 20 * 60 * 5); // 每5分钟保存一次
    }

    /**
     * 保存所有数据
     */
    private void saveAllData() {
        plugin.getLogger().info("正在保存 PvP 数据...");

        // 同步保存所有玩家的PvP统计数据
        if (statsService != null) {
            int count = 0;
            for (var entry : statsService.getAllStats().entrySet()) {
                try {
                    statsService.savePlayerStats(entry.getKey(), entry.getValue());
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().warning("无法保存玩家 " + entry.getKey() + " 的PvP数据: " + e.getMessage());
                }
            }
            plugin.getLogger().info("已保存 " + count + " 个玩家的PvP数据");
        }

        // 保存决斗数据
        if (duelService != null) {
            duelService.saveAll();
        }

        // 保存连杀数据
        if (killStreakService != null) {
            killStreakService.saveData();
            plugin.getLogger().info("已保存连杀数据");
        }

        plugin.getLogger().info("PvP 数据保存完成");
    }

    /**
     * 玩家死亡事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) {
            return;
        }

        // 检查是否在决斗中
        Duel duel = duelService.getPlayerDuel(victim.getUniqueId());
        if (duel != null && duel.getState() == Duel.DuelState.IN_PROGRESS) {
            // 决斗中的死亡
            duel.recordKill(killer.getUniqueId());

            // 通知双方
            killer.sendMessage("§a§l✔ 本回合胜利！");
            victim.sendMessage("§c§l✘ 本回合失败！");

            // 检查是否结束决斗
            if (shouldEndDuel(duel, victim.getUniqueId())) {
                // BO 多局制处理
                if (duel.getBestOf() > 1) {
                    duelService.recordRoundWin(duel.getId(), killer.getUniqueId());
                } else {
                    duelService.endDuel(duel.getId(), killer.getUniqueId(), Duel.DuelEndReason.DEATH);
                    killer.sendMessage("§a§l✔ 决斗胜利！");
                    victim.sendMessage("§c§l✘ 决斗失败！");
                }
            }

            // 清空掉落物
            event.getDrops().clear();
            event.setDroppedExp(0);

            // 复活玩家
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    duelService.respawnPlayer(victim, duel.getId());
                }
            }, 5L);

            return;
        }

        // 记录统计
        statsService.recordKill(killer.getUniqueId(), victim.getUniqueId());

        // 处理连杀
        PvPStats killerStats = statsService.getOrCreateStats(killer.getUniqueId());
        PvPStats victimStats = statsService.getOrCreateStats(victim.getUniqueId());

        int killStreak = killerStats.getCurrentKillStreak();
        int endedStreak = victimStats.getCurrentKillStreak();

        // 连杀事件
        var result = killStreakService.addKill(killer.getUniqueId());
        if (result.milestone() != null) {
            var milestone = result.milestone();
            killer.sendMessage("§6§l连杀！ §e" + milestone.displayName() + " §7(" + killStreak + "杀)");
            plugin.getServer().broadcastMessage("§6" + killer.getName() + " §e达成了 §6" + milestone.displayName() + "§e！");

            // 给予连杀奖励
            if (killStreakService.isVengeanceEnabled()) {
                killer.sendMessage("§a+ " + milestone.reward() + " 金币（连杀奖励）");
            }
        }

        // 终结连杀
        if (endedStreak > 0) {
            killStreakService.resetKillStreak(victim.getUniqueId());
            int vengeanceReward = killStreakService.calculateVengeanceReward(endedStreak);
            if (endedStreak >= 3) {
                plugin.getServer().broadcastMessage("§c" + killer.getName() + " §7终结了 §c" + victim.getName() + " §7的 §e" + endedStreak + " §7连杀！");
                if (vengeanceReward > 0) {
                    killer.sendMessage("§a+ " + vengeanceReward + " 金币（终结奖励）");
                }
            }
        }
    }

    /**
     * 玩家受伤事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // 记录伤害统计
        long damage = (long) event.getFinalDamage();
        statsService.getOrCreateStats(attacker.getUniqueId()).addDamage(damage);
        statsService.getOrCreateStats(victim.getUniqueId()).addDamageTaken(damage);

        // 在决斗中记录伤害
        Duel duel = duelService.getPlayerDuel(attacker.getUniqueId());
        if (duel != null && duel.getState() == Duel.DuelState.IN_PROGRESS) {
            duel.recordDamage(attacker.getUniqueId(), damage);
            duel.recordHit(attacker.getUniqueId());
        }
    }

    /**
     * 玩家退出事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 检查是否在决斗中
        Duel duel = duelService.getPlayerDuel(player.getUniqueId());
        if (duel != null && duel.getState() == Duel.DuelState.IN_PROGRESS) {
            // 退出视为投降
            UUID opponent = duel.getOpponent(player.getUniqueId());
            if (opponent != null) {
                duelService.endDuel(duel.getId(), opponent, Duel.DuelEndReason.DISCONNECT);

                Player opponentPlayer = plugin.getServer().getPlayer(opponent);
                if (opponentPlayer != null) {
                    opponentPlayer.sendMessage("§c对手退出了，你获胜了！");
                }
            }
        }

        // 同步保存玩家PvP数据
        try {
            PvPStats stats = statsService.getOrCreateStats(player.getUniqueId());
            statsService.savePlayerStats(player.getUniqueId(), stats);
        } catch (Exception e) {
            plugin.getLogger().warning("无法保存玩家 " + player.getName() + " 的PvP数据: " + e.getMessage());
        }
    }

    // Getters
    public DuelService getDuelService() {
        return duelService;
    }

    public KillStreakService getKillStreakService() {
        return killStreakService;
    }

    public PvPStatsService getStatsService() {
        return statsService;
    }

    /**
     * 检查是否应该结束决斗
     */
    private boolean shouldEndDuel(Duel duel, UUID deadPlayer) {
        if (duel.getState() != Duel.DuelState.IN_PROGRESS) {
            return false;
        }
        return duel.isParticipant(deadPlayer);
    }
}
