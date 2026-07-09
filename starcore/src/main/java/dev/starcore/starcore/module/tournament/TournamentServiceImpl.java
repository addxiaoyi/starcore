package dev.starcore.starcore.module.tournament;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.tournament.event.*;
import dev.starcore.starcore.module.tournament.model.Bracket;
import dev.starcore.starcore.module.tournament.model.BracketMatch;
import dev.starcore.starcore.module.tournament.storage.DatabaseAwareTournamentStorage;
import dev.starcore.starcore.module.tournament.storage.TournamentStorage;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 锦标赛核心服务实现
 * 管理所有比赛的核心逻辑
 */
public class TournamentServiceImpl implements TournamentService {
    private static final long SAVE_INTERVAL_TICKS = 20 * 60 * 5; // 5分钟
    private static final long CHECK_INTERVAL_TICKS = 20; // 1秒

    private final JavaPlugin plugin;
    private final StarCoreScheduler scheduler;
    private final TournamentStorage storage;
    private final DatabaseAwareTournamentStorage dbStorage;
    private final boolean useDatabase;

    // 活跃比赛（ID -> Tournament）
    private final Map<String, Tournament> activeTournaments = new ConcurrentHashMap<>();

    // 对阵表（比赛ID -> 对阵）
    private final Map<String, Bracket> brackets = new ConcurrentHashMap<>();

    // 玩家-比赛映射（Player UUID -> Tournament ID）
    private final Map<UUID, String> playerTournament = new ConcurrentHashMap<>();

    // 定时任务
    private BukkitTask saveTask;
    private BukkitTask checkTask;

    // 配置缓存
    private final Map<TournamentType, TournamentConfig> configCache = new EnumMap<>(TournamentType.class);

    // 观战者集合（比赛ID -> 观战者UUID集合）
    private final Map<String, Set<UUID>> spectators = new ConcurrentHashMap<>();

    // 经济服务（可选，用于奖励发放）
    private EconomyService economyService;

    // 观战者保存位置（玩家UUID -> 原始位置）
    private final Map<UUID, Location> spectatorOriginalLocations = new ConcurrentHashMap<>();

    public TournamentServiceImpl(JavaPlugin plugin, StarCoreScheduler scheduler) {
        this(plugin, scheduler, null);
    }

    public TournamentServiceImpl(JavaPlugin plugin, StarCoreScheduler scheduler, DatabaseService databaseService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.useDatabase = databaseService != null;

        if (useDatabase) {
            this.dbStorage = new DatabaseAwareTournamentStorage(plugin, databaseService);
            this.dbStorage.ensureDatabaseTable();
            this.storage = null;
        } else {
            this.storage = new TournamentStorage(plugin);
            this.dbStorage = null;
        }

        // 初始化配置缓存
        initializeConfigCache();

        // 加载保存的数据
        loadData();

        // 启动定时任务
        startTasks();
    }

    private void initializeConfigCache() {
        for (TournamentType type : TournamentType.values()) {
            configCache.put(type, TournamentConfig.forType(type));
        }
    }

    // ==================== 生命周期 ====================

    @Override
    public TournamentConfig getConfig(TournamentType type) {
        return configCache.getOrDefault(type, TournamentConfig.defaults(type.displayName(), type.description()));
    }

    @Override
    public Tournament createTournament(String name, TournamentType type, Player creator) {
        String id = generateTournamentId();
        TournamentConfig config = getConfig(type);

        Tournament tournament = new Tournament(id, name, type, creator.getUniqueId(), config, Instant.now());

        // 设置总轮数（对于淘汰赛）
        if (type.isBracketBased()) {
            int totalRounds = calculateTotalRounds(config.maxPlayers());
            tournament.setTotalRounds(totalRounds);
        }

        // 让创建者自动加入比赛
        tournament.addParticipant(creator.getUniqueId());
        activeTournaments.put(id, tournament);
        playerTournament.put(creator.getUniqueId(), id);

        // 发布创建事件
        TournamentCreatedEvent event = new TournamentCreatedEvent(tournament, creator);
        Bukkit.getPluginManager().callEvent(event);

        plugin.getLogger().info("Created tournament: " + name + " (ID: " + id + ") by " + creator.getName());
        return tournament;
    }

    @Override
    public boolean joinTournament(String tournamentId, Player player) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            player.sendMessage("§c比赛不存在: " + tournamentId);
            return false;
        }

        if (tournament.getStatus() != TournamentStatus.WAITING) {
            player.sendMessage("§c比赛已开始或已结束，无法加入");
            return false;
        }

        if (tournament.getParticipants().size() >= tournament.getConfig().maxPlayers()) {
            player.sendMessage("§c比赛已满员");
            return false;
        }

        if (tournament.getParticipants().contains(player.getUniqueId())) {
            player.sendMessage("§e你已经在比赛中");
            return false;
        }

        // 检查玩家是否在其他比赛中
        if (playerTournament.containsKey(player.getUniqueId())) {
            player.sendMessage("§c请先离开当前比赛: /tournament leave");
            return false;
        }

        tournament.addParticipant(player.getUniqueId());
        playerTournament.put(player.getUniqueId(), tournamentId);

        // 发布玩家加入事件
        TournamentPlayerJoinEvent joinEvent = new TournamentPlayerJoinEvent(
            tournament, player,
            tournament.getParticipants().size(),
            tournament.getConfig().maxPlayers()
        );
        Bukkit.getPluginManager().callEvent(joinEvent);

        player.sendMessage("§a成功加入比赛: §e" + tournament.getName());
        broadcastToTournament(tournament, "§a" + player.getName() + " §7加入了比赛 (§e" +
            tournament.getParticipants().size() + "/" + tournament.getConfig().maxPlayers() + "§7)");

        // 自动开始检查
        if (tournament.getParticipants().size() >= tournament.getConfig().minPlayers()) {
            checkAutoStart(tournament);
        }

        return true;
    }

    @Override
    public boolean leaveTournament(Player player) {
        String tournamentId = playerTournament.remove(player.getUniqueId());
        if (tournamentId == null) {
            return false;
        }

        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return true;
        }

        tournament.removeParticipant(player.getUniqueId());

        // 发布玩家离开事件
        TournamentPlayerLeaveEvent leaveEvent = new TournamentPlayerLeaveEvent(
            tournament, player, false, tournament.getAliveParticipants().size()
        );
        Bukkit.getPluginManager().callEvent(leaveEvent);

        if (tournament.getStatus() == TournamentStatus.WAITING) {
            broadcastToTournament(tournament, "§e" + player.getName() + " §7离开了比赛");
        }

        // 检查是否需要取消比赛
        if (tournament.getParticipants().isEmpty()) {
            cancelTournament(tournamentId, "没有足够玩家");
        } else if (tournament.getParticipants().size() == 1 &&
                   tournament.getStatus() == TournamentStatus.WAITING) {
            // 只剩创建者一人时也取消比赛
            cancelTournament(tournamentId, "玩家不足");
        } else if (tournament.getParticipants().size() < tournament.getConfig().minPlayers() &&
                   tournament.getStatus() == TournamentStatus.WAITING) {
            // 通知玩家无法开始
            broadcastToTournament(tournament, "§c玩家不足，比赛无法开始");
        }

        player.sendMessage("§e已离开比赛");
        return true;
    }

    @Override
    public boolean startTournament(String tournamentId) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return false;
        }

        if (tournament.getStatus() != TournamentStatus.WAITING) {
            return false;
        }

        if (tournament.getParticipants().size() < tournament.getConfig().minPlayers()) {
            return false;
        }

        tournament.setStatus(TournamentStatus.IN_PROGRESS);
        tournament.setStartTime(Instant.now());

        broadcastToTournament(tournament, "§6§l===== 比赛开始！ =====");
        broadcastToTournament(tournament, "§a类型: §f" + tournament.getType().displayName());
        broadcastToTournament(tournament, "§a参赛者: §f" + tournament.getParticipants().size());

        // 对于淘汰赛，生成对阵表
        if (tournament.getType().isBracketBased()) {
            generateBracket(tournament);
            broadcastToTournament(tournament, "§e对阵表已生成！");
        }

        // 广播开始消息
        for (UUID participantId : tournament.getParticipants()) {
            Player p = Bukkit.getPlayer(participantId);
            if (p != null) {
                p.sendTitle("§6比赛开始！", "§a祝你好运！", 10, 40, 10);
            }
        }

        // 发布开始事件
        TournamentStartedEvent startEvent = new TournamentStartedEvent(tournament);
        Bukkit.getPluginManager().callEvent(startEvent);

        plugin.getLogger().info("Tournament started: " + tournament.getName());
        return true;
    }

    @Override
    public boolean cancelTournament(String tournamentId, String reason) {
        Tournament tournament = activeTournaments.remove(tournamentId);
        if (tournament == null) {
            return false;
        }

        // 先设置状态再通知（避免状态不一致）
        tournament.setStatus(TournamentStatus.CANCELLED);

        // 移除所有玩家的比赛关联并通知
        for (UUID participantId : tournament.getParticipants()) {
            playerTournament.remove(participantId);

            Player p = Bukkit.getPlayer(participantId);
            if (p != null) {
                p.sendMessage("§c比赛已取消: " + reason);
            }
        }

        storage.addToHistory(tournament);

        plugin.getLogger().info("Tournament cancelled: " + tournament.getName() + " - " + reason);
        return true;
    }

    @Override
    public void finishTournament(String tournamentId, UUID winnerId) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return;
        }

        tournament.setStatus(TournamentStatus.COMPLETED);
        tournament.setWinner(winnerId);

        // 广播胜利消息
        String winnerName = winnerId != null ?
            Bukkit.getPlayer(winnerId) != null ? Bukkit.getPlayer(winnerId).getName() : winnerId.toString()
            : "无人";

        broadcastToTournament(tournament, "§6§l===== 比赛结束！ =====");
        broadcastToTournament(tournament, "§e冠军: §a" + winnerName);
        broadcastToTournament(tournament, "§e奖金池: §a" + tournament.getConfig().prizePool());

        // 显示排名
        showRankings(tournament);

        // 发放奖励
        distributeRewards(tournament, winnerId);

        // 传送所有参与者返回
        teleportParticipantsBack(tournament);

        // 清除观战者并清理
        for (UUID specId : List.copyOf(spectators.getOrDefault(tournamentId, Set.of()))) {
            Player p = Bukkit.getPlayer(specId);
            if (p != null) {
                teleportBack(p);
            }
        }
        spectators.remove(tournamentId);

        // 从活跃列表移除
        activeTournaments.remove(tournamentId);

        // 添加到历史
        if (useDatabase && dbStorage != null) {
            dbStorage.saveTournament(tournament);
            dbStorage.addToHistory(tournament);
        } else {
            storage.addToHistory(tournament);
        }

        // 移除所有玩家的比赛关联
        for (UUID participantId : tournament.getParticipants()) {
            playerTournament.remove(participantId);
        }

        // 发布结束事件
        TournamentEndedEvent endEvent = new TournamentEndedEvent(
            tournament, TournamentStatus.COMPLETED, winnerId, "比赛结束"
        );
        Bukkit.getPluginManager().callEvent(endEvent);

        plugin.getLogger().info("Tournament finished: " + tournament.getName() + " - Winner: " + winnerName);
    }

    // ==================== 奖励系统 ====================

    /**
     * 分发比赛奖励
     */
    private void distributeRewards(Tournament tournament, UUID winnerId) {
        double prizePool = tournament.getConfig().prizePool();
        if (prizePool <= 0 && (tournament.getConfig().rewardItems() == null || tournament.getConfig().rewardItems().isEmpty())) {
            plugin.getLogger().info("No rewards configured for tournament: " + tournament.getName());
            return;
        }

        // 计算奖励分配
        RewardDistribution distribution = calculateRewardDistribution(tournament, prizePool);

        // 发放第一名奖励
        if (winnerId != null) {
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner != null) {
                // 发放金币奖励
                if (distribution.winnerGold() > 0) {
                    depositReward(winner, distribution.winnerGold(), "锦标赛冠军奖励");
                }
                // 发放物品奖励
                for (RewardItem item : distribution.winnerItems()) {
                    giveRewardItem(winner, item);
                }
                // 发送消息
                // 分隔
                winner.sendMessage("§6§l========== 恭喜夺冠！ ==========");
                winner.sendMessage("§e你获得了 §a" + formatGold(distribution.winnerGold()) + " §e金币奖励！");
                if (!distribution.winnerItems().isEmpty()) {
                    winner.sendMessage("§e以及特殊物品奖励！");
                }
                winner.sendMessage("§6==============================");
                // 分隔

                // 夺冠特效
                playVictoryEffects(winner);
            }
        }

        // 发放参与奖励（前50%玩家）
        int participantReward = distribution.participantGold();
        if (participantReward > 0) {
            for (UUID participantId : tournament.getParticipants()) {
                if (participantId.equals(winnerId)) continue; // 冠军已经发放了

                Player participant = Bukkit.getPlayer(participantId);
                if (participant != null) {
                    depositReward(participant, participantReward, "锦标赛参与奖励");
                    participant.sendMessage("§a感谢参与锦标赛，你获得了 §e" + formatGold(participantReward) + " §a金币奖励！");
                }
            }
        }

        // 发放击杀榜奖励
        distributeKillRewards(tournament);

        plugin.getLogger().info("Distributed rewards for tournament: " + tournament.getName());
    }

    /**
     * 计算奖励分配
     */
    private RewardDistribution calculateRewardDistribution(Tournament tournament, double prizePool) {
        double winnerPercent;
        double participantPercent;
        List<RewardItem> winnerItems = new ArrayList<>();

        // 根据比赛类型设置奖励比例
        switch (tournament.getType()) {
            case PVP_1V1:
                winnerPercent = 0.7; // 冠军70%
                participantPercent = 30;
                break;
            case PVP_FFA:
                winnerPercent = 0.5; // 冠军50%
                participantPercent = 50;
                break;
            case PVP_TEAM:
                winnerPercent = 0.6;
                participantPercent = 40;
                break;
            case ELIMINATION:
                winnerPercent = 0.6;
                participantPercent = 40;
                break;
            case SPEEDRUN:
            case PARKOUR:
                winnerPercent = 0.8; // 速通/跑酷冠军80%
                participantPercent = 20;
                break;
            default:
                winnerPercent = 0.5;
                participantPercent = 50;
        }

        // 计算金币奖励
        double winnerGold = prizePool * winnerPercent / 100.0;
        int participantGold = (int) (prizePool * participantPercent / 100.0 / Math.max(1, tournament.getParticipants().size()));

        // 获取物品奖励
        List<TournamentConfig.RewardItemConfig> configItems = tournament.getConfig().rewardItems();
        if (configItems != null) {
            winnerItems = configItems.stream()
                .map(i -> new RewardItem(i.material(), i.amount()))
                .toList();
        }

        return new RewardDistribution((int) winnerGold, participantGold, winnerItems);
    }

    /**
     * 发放金币奖励
     */
    private void depositReward(Player player, int amount, String reason) {
        if (economyService != null) {
            BigDecimal gold = BigDecimal.valueOf(amount);
            economyService.deposit(player.getUniqueId(), gold);
            plugin.getLogger().info("Deposited " + amount + " gold to " + player.getName() + " (" + reason + ")");
        } else {
            plugin.getLogger().warning("Economy service not available, cannot deposit reward to " + player.getName());
        }
    }

    /**
     * 发放物品奖励
     */
    private void giveRewardItem(Player player, RewardItem item) {
        try {
            Material material = Material.valueOf(item.material().toUpperCase());
            ItemStack itemStack = new ItemStack(material, item.amount());

            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(itemStack);
            if (!overflow.isEmpty()) {
                // 背包满了，掉落在地上
                player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
            }

            player.sendMessage("§6获得物品: §e" + item.amount() + "x " + formatMaterialName(material));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give reward item: " + item.material());
        }
    }

    /**
     * 发放击杀榜奖励
     */
    private void distributeKillRewards(Tournament tournament) {
        // 获取击杀最多的玩家（排除冠军）
        UUID winnerId = tournament.getWinner();
        List<Map.Entry<UUID, Integer>> sorted = tournament.getKills().entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .limit(3)
            .toList();

        int reward = 50; // 基础击杀奖励
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (entry.getKey().equals(winnerId)) continue; // 冠军已获得大头
            if (entry.getValue() <= 0) continue; // 没有击杀

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                int goldReward = reward * rank;
                depositReward(player, goldReward, "击杀榜第" + rank + "名");
                player.sendMessage("§6击杀榜第" + rank + "名: §e+" + formatGold(goldReward) + " §7(" + entry.getValue() + " kills)");
            }
            rank++;
        }
    }

    /**
     * 播放夺冠特效
     */
    private void playVictoryEffects(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        // 烟花特效
        for (int i = 0; i < 5; i++) {
            world.spawn(loc.clone().add(randomOffset(3), 0, randomOffset(3)), Firework.class, fw -> {
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.fromRGB(random(255), random(255), random(255)))
                    .withFade(Color.YELLOW)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .build());
                meta.setPower(2);
                fw.setFireworkMeta(meta);
            });
        }

        // 音符盒特效
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        // 冠军标题
        player.sendTitle("§6§l冠军!", "§e恭喜获得锦标赛胜利!", 20, 60, 20);
    }

    private double randomOffset(double range) {
        return (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * range;
    }

    private int random(int max) {
        return (int) (ThreadLocalRandom.current().nextDouble() * max);
    }

    private String formatGold(int amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(amount);
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
            }
        }
        return result.toString().trim();
    }

    /**
     * 传送参与者返回原位置
     */
    private void teleportParticipantsBack(Tournament tournament) {
        for (UUID participantId : tournament.getParticipants()) {
            Player player = Bukkit.getPlayer(participantId);
            if (player != null) {
                teleportBack(player);
            }
        }
    }

    private void teleportBack(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        Location originalLoc = spectatorOriginalLocations.remove(player.getUniqueId());
        if (originalLoc == null) {
            originalLoc = playerSpawnLocations.remove(player.getUniqueId());
        }
        if (originalLoc != null) {
            player.teleport(originalLoc);
        } else {
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world != null) {
                player.teleport(world.getSpawnLocation());
            }
        }
    }

    /**
     * 清除观战者
     */
    private void clearSpectators(String tournamentId) {
        Set<UUID> tournamentSpectators = spectators.remove(tournamentId);
        if (tournamentSpectators == null) return;

        for (UUID spectatorId : tournamentSpectators) {
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator != null) {
                // 移除效果
                spectator.removePotionEffect(PotionEffectType.INVISIBILITY);
                spectator.removePotionEffect(PotionEffectType.SLOWNESS);

                // 恢复位置
                Location originalLoc = spectatorOriginalLocations.remove(spectatorId);
                if (originalLoc != null) {
                    spectator.teleport(originalLoc);
                } else {
                    World world = Bukkit.getWorlds().get(0);
                    if (world != null) {
                        spectator.teleport(world.getSpawnLocation());
                    }
                }

                spectator.sendMessage("§c比赛已结束，已离开观战区域");
            }
        }
    }

    // 玩家出生位置（玩家UUID -> 位置）
    private final Map<UUID, Location> playerSpawnLocations = new ConcurrentHashMap<>();

    /**
     * 保存玩家出生位置
     */
    public void savePlayerSpawnLocation(Player player) {
        playerSpawnLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    /**
     * 奖励分配记录
     */
    private record RewardDistribution(int winnerGold, int participantGold, List<RewardItem> winnerItems) {}

    /**
     * 奖励物品记录
     */
    private record RewardItem(String material, int amount) {}

    /**
     * 设置经济服务（用于奖励发放）
     */
    public void setEconomyService(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Override
    public boolean teleportToSpectatorArea(Player player, String tournamentId) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            player.sendMessage("§c比赛不存在: " + tournamentId);
            return false;
        }

        if (tournament.getStatus() == TournamentStatus.WAITING) {
            player.sendMessage("§c比赛尚未开始，无法观战");
            return false;
        }

        if (tournament.getStatus().isFinished()) {
            player.sendMessage("§c比赛已结束，无法观战");
            return false;
        }

        // 保存玩家原始位置
        spectatorOriginalLocations.put(player.getUniqueId(), player.getLocation().clone());

        // 添加到观战者集合
        spectators.computeIfAbsent(tournamentId, k -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());

        // 获取观战区域
        Location spectatorLoc = tournament.getConfig().spectatorLocation();
        if (spectatorLoc == null) {
            // 如果没有配置观战区域，生成一个默认位置（在比赛区域上空）
            spectatorLoc = generateDefaultSpectatorLocation(tournament);
        }

        // 传送到观战区域
        player.teleport(spectatorLoc);

        // 给观战者一些效果
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 2, false, false));

        // 通知玩家
        player.sendMessage("§a已传送至观战区域: §e" + tournament.getName());
        player.sendMessage("§7使用 §e/tournament leave §7离开观战");

        // 广播观战消息
        for (UUID participantId : tournament.getParticipants()) {
            Player p = Bukkit.getPlayer(participantId);
            if (p != null && !p.equals(player)) {
                p.sendMessage("§7[观战] §e" + player.getName() + " §7开始观战");
            }
        }

        return true;
    }

    @Override
    public Optional<Location> getSpectatorLocation(String tournamentId) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return Optional.empty();
        }
        Location loc = tournament.getConfig().spectatorLocation();
        if (loc != null) {
            return Optional.of(loc);
        }
        // 生成默认位置
        return Optional.ofNullable(generateDefaultSpectatorLocation(tournament));
    }

    /**
     * 检查玩家是否为观战者
     */
    public boolean isSpectator(UUID playerId, String tournamentId) {
        Set<UUID> tournamentSpectators = spectators.get(tournamentId);
        return tournamentSpectators != null && tournamentSpectators.contains(playerId);
    }

    /**
     * 获取比赛的观战者列表
     */
    public List<Player> getSpectators(String tournamentId) {
        Set<UUID> tournamentSpectators = spectators.get(tournamentId);
        if (tournamentSpectators == null) {
            return Collections.emptyList();
        }
        return tournamentSpectators.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 离开观战
     */
    public boolean leaveSpectator(Player player) {
        // 找到玩家所在的比赛
        String tournamentId = null;
        for (Map.Entry<String, Set<UUID>> entry : spectators.entrySet()) {
            if (entry.getValue().remove(player.getUniqueId())) {
                tournamentId = entry.getKey();
                break;
            }
        }

        if (tournamentId == null) {
            return false;
        }

        // 移除观战效果
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        // 恢复原始位置
        Location originalLoc = spectatorOriginalLocations.remove(player.getUniqueId());
        if (originalLoc != null) {
            player.teleport(originalLoc);
        } else {
            // 如果没有保存的位置，传送到世界出生点
            World world = Bukkit.getWorlds().get(0);
            if (world != null) {
                player.teleport(world.getSpawnLocation());
            }
        }

        player.sendMessage("§a已离开观战区域");

        // 通知比赛参与者
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament != null) {
            for (UUID participantId : tournament.getParticipants()) {
                Player p = Bukkit.getPlayer(participantId);
                if (p != null) {
                    p.sendMessage("§7[观战] §e" + player.getName() + " §7离开了观战");
                }
            }
        }

        return true;
    }

    /**
     * 生成默认观战位置（比赛区域中心点上方20格）
     */
    private Location generateDefaultSpectatorLocation(Tournament tournament) {
        // 从比赛配置获取场地中心点
        Location arenaCenter = tournament.getConfig().spectatorLocation();
        if (arenaCenter == null) {
            // 使用世界出生点
            World world = Bukkit.getWorlds().get(0);
            if (world != null) {
                Location spawn = world.getSpawnLocation().clone();
                spawn.setY(spawn.getY() + 30); // 在出生点上方30格
                return spawn;
            }
            return null;
        }
        // 在原有位置上方30格
        Location loc = arenaCenter.clone();
        loc.setY(loc.getY() + 30);
        return loc;
    }

    /**
     * 设置观战区域位置
     */
    public boolean setSpectatorLocation(String tournamentId, Location location) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return false;
        }

        // 创建新的配置，保留其他属性
        TournamentConfig oldConfig = tournament.getConfig();
        TournamentConfig newConfig = new TournamentConfig(
            oldConfig.displayName(),
            oldConfig.description(),
            oldConfig.maxPlayers(),
            oldConfig.minPlayers(),
            oldConfig.waitTime(),
            oldConfig.maxDuration(),
            oldConfig.tags(),
            oldConfig.prizePool(),
            location,
            oldConfig.rewardItems()
        );

        // 由于TournamentConfig是不可变record，我们需要更新配置
        // 但 Tournament 类没有提供 setConfig 方法，所以我们直接修改字段
        // 这里需要通过反射或者修改Tournament类来支持
        // 为了简化，我们记录到一个单独的映射中
        customSpectatorLocations.put(tournamentId, location);

        plugin.getLogger().info("Set custom spectator location for tournament: " + tournamentId);
        return true;
    }

    // 自定义观战位置（比赛ID -> 位置）
    private final Map<String, Location> customSpectatorLocations = new ConcurrentHashMap<>();

    // ==================== 查询方法 ====================

    @Override
    public List<Tournament> getActiveTournaments() {
        return new ArrayList<>(activeTournaments.values());
    }

    @Override
    public Optional<Tournament> getTournament(String id) {
        return Optional.ofNullable(activeTournaments.get(id));
    }

    @Override
    public Optional<Tournament> getPlayerTournament(Player player) {
        String tournamentId = playerTournament.get(player.getUniqueId());
        if (tournamentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeTournaments.get(tournamentId));
    }

    @Override
    public boolean isPlayerInTournament(UUID playerId) {
        return playerTournament.containsKey(playerId);
    }

    @Override
    public int getPlayerRank(String tournamentId, UUID playerId) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return -1;
        }

        List<Map.Entry<UUID, Integer>> sorted = tournament.getKills().entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .toList();

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(playerId)) {
                return i + 1;
            }
        }

        return sorted.size() + 1;
    }

    // ==================== 比赛逻辑 ====================

    @Override
    public void recordKill(String tournamentId, UUID killer, UUID victim) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return;
        }

        if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
            return;
        }

        tournament.recordKill(killer, victim);

        // 发布击杀事件（缓存玩家引用避免重复查询）
        Player killerPlayer = killer != null ? Bukkit.getPlayer(killer) : null;
        Player victimPlayer = victim != null ? Bukkit.getPlayer(victim) : null;
        int totalKills = tournament.getKills().getOrDefault(killer, 0);
        try {
            TournamentKillEvent killEvent = new TournamentKillEvent(
                tournament, killerPlayer, victimPlayer, totalKills, tournament.getAliveParticipants().size()
            );
            Bukkit.getPluginManager().callEvent(killEvent);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to publish TournamentKillEvent: " + e.getMessage());
        }

        // 检查比赛是否结束
        checkTournamentEnd(tournament);
    }

    @Override
    public void recordCompletion(String tournamentId, UUID player, long timeMillis) {
        Tournament tournament = activeTournaments.get(tournamentId);
        if (tournament == null) {
            return;
        }

        tournament.recordCompletion(player, timeMillis);

        // 检查比赛是否结束（所有人都完成了）
        if (tournament.getCompletions().size() >= tournament.getParticipants().size()) {
            finishSpeedrunTournament(tournament);
        }
    }

    // ==================== 快照/统计 ====================

    @Override
    public Collection<TournamentSnapshot> getActiveSnapshots() {
        return activeTournaments.values().stream()
            .map(t -> new TournamentSnapshot(
                t.getId(),
                t.getName(),
                t.getType(),
                t.getStatus(),
                t.getParticipants().size(),
                t.getConfig().maxPlayers(),
                t.getCreatedAt().toEpochMilli()
            ))
            .toList();
    }

    @Override
    public TournamentStats getStats() {
        int activeCount = activeTournaments.size();
        int totalParticipants = activeTournaments.values().stream()
            .mapToInt(t -> t.getParticipants().size())
            .sum();
        long totalMatches = activeTournaments.values().stream()
            .mapToLong(t -> t.getMatches().size())
            .sum();
        double totalPrizePool = activeTournaments.values().stream()
            .mapToDouble(t -> t.getConfig().prizePool())
            .sum();

        return new TournamentStats(activeCount, totalParticipants, totalMatches, totalPrizePool);
    }

    // ==================== 对阵表方法 ====================

    /**
     * 获取比赛的对阵表
     */
    public Optional<Bracket> getBracket(String tournamentId) {
        return Optional.ofNullable(brackets.get(tournamentId));
    }

    /**
     * 设置比赛结果
     */
    public void setMatchResult(String tournamentId, int round, int matchNumber, UUID winnerId) {
        Bracket bracket = brackets.get(tournamentId);
        if (bracket != null) {
            bracket.advanceWinner(round, matchNumber, winnerId);
        }
    }

    // ==================== 私有方法 ====================

    private String generateTournamentId() {
        return "tour_" + System.currentTimeMillis() + "_" + (int)(ThreadLocalRandom.current().nextDouble() * 10000);
    }

    private int calculateTotalRounds(int playerCount) {
        return (int) Math.ceil(Math.log(playerCount) / Math.log(2));
    }

    private void generateBracket(Tournament tournament) {
        // 生成对阵表
        List<UUID> participants = new ArrayList<>(tournament.getParticipants());
        Collections.shuffle(participants);

        // 创建对阵表对象
        int totalRounds = calculateTotalRounds(participants.size());
        Bracket bracket = new Bracket(tournament.getId(), totalRounds);
        bracket.setFirstRound(participants);

        // 存储对阵表
        brackets.put(tournament.getId(), bracket);

        plugin.getLogger().info("Generated bracket for tournament: " + tournament.getName() + " with " + totalRounds + " rounds");
    }

    private void checkTournamentEnd(Tournament tournament) {
        // FFA 模式：剩余1人
        if (tournament.getType().isFFA()) {
            if (tournament.getAliveParticipants().size() <= 1) {
                UUID winner = tournament.getAliveParticipants().stream().findFirst().orElse(null);
                finishTournament(tournament.getId(), winner);
            }
            return;
        }

        // 淘汰赛：剩余1人
        if (tournament.getType() == TournamentType.ELIMINATION) {
            if (tournament.getAliveParticipants().size() <= 1) {
                UUID winner = tournament.getAliveParticipants().stream().findFirst().orElse(null);
                finishTournament(tournament.getId(), winner);
            }
            return;
        }

        // 速通/跑酷：所有玩家完成或超时（由 recordCompletion 调用 finishSpeedrunTournament 处理）
    }

    private void finishSpeedrunTournament(Tournament tournament) {
        // 按完成时间排序
        List<Map.Entry<UUID, Long>> sorted = tournament.getCompletions().entrySet().stream()
            .sorted((a, b) -> Long.compare(a.getValue(), b.getValue()))
            .toList();

        UUID winner = sorted.isEmpty() ? null : sorted.get(0).getKey();
        finishTournament(tournament.getId(), winner);
    }

    private void checkAutoStart(Tournament tournament) {
        if (tournament.getParticipants().size() >= tournament.getConfig().minPlayers()) {
            // 可以开始比赛
            if (tournament.getParticipants().size() == tournament.getConfig().maxPlayers()) {
                // 满员，自动开始
                startTournament(tournament.getId());
            }
        }
    }

    private void showRankings(Tournament tournament) {
        List<Map.Entry<UUID, Integer>> sorted = tournament.getKills().entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .toList();

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (rank > 10) break;

            Player p = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : entry.getKey().toString().substring(0, 8);
            broadcastToTournament(tournament, "§e#" + rank + " §f" + name + " §7- §c" + entry.getValue() + " kills");
            rank++;
        }
    }

    private void broadcastToTournament(Tournament tournament, String message) {
        for (UUID participantId : tournament.getParticipants()) {
            Player p = Bukkit.getPlayer(participantId);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    // ==================== 持久化 ====================

    private void loadData() {
        try {
            Map<String, TournamentStorage.TournamentData> saved = storage.loadActiveTournaments();
            for (Map.Entry<String, TournamentStorage.TournamentData> entry : saved.entrySet()) {
                try {
                    Tournament tournament = entry.getValue().toTournament();
                    if (!tournament.getStatus().isFinished()) {
                        activeTournaments.put(tournament.getId(), tournament);

                        // 恢复玩家映射
                        for (UUID playerId : tournament.getParticipants()) {
                            playerTournament.put(playerId, tournament.getId());
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load tournament " + entry.getKey() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load tournament data", e);
        }
    }

    private void startTasks() {
        // 定期保存任务
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAllTournaments();
        }, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        // 定期检查任务
        checkTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkActiveTournaments();
        }, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    private void checkActiveTournaments() {
        long now = System.currentTimeMillis();

        for (Tournament tournament : activeTournaments.values()) {
            if (tournament.getStatus() != TournamentStatus.IN_PROGRESS) {
                continue;
            }

            // 检查超时
            if (tournament.getStartTime() != null) {
                long elapsed = (now - tournament.getStartTime().toEpochMilli()) / 1000;
                if (elapsed > tournament.getConfig().maxDuration()) {
                    // 超时，强制结束
                    plugin.getLogger().info("Tournament timed out: " + tournament.getName());
                    finishTournament(tournament.getId(), null);
                }
            }
        }
    }

    @Override
    public void saveAllTournaments() {
        Map<String, TournamentStorage.TournamentData> data = new HashMap<>();
        for (Map.Entry<String, Tournament> entry : activeTournaments.entrySet()) {
            data.put(entry.getKey(), TournamentStorage.TournamentData.fromTournament(entry.getValue()));
        }
        storage.saveActiveTournaments(data);
    }

    @Override
    public void cancelAllMatches() {
        for (String id : new ArrayList<>(activeTournaments.keySet())) {
            cancelTournament(id, "服务器关闭");
        }

        if (saveTask != null) {
            saveTask.cancel();
        }
        if (checkTask != null) {
            checkTask.cancel();
        }
    }

    @Override
    public void shutdown() {
        cancelAllMatches();
        saveAllTournaments();
    }
}
