package dev.starcore.starcore.pvp.duel;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 决斗服务 - 包含数据库持久化
 * D-136: 已知问题（待后续深度审计）：
 * 1) 邀请无 TTL - cleanupExpiredRequests() 方法已实现但需调用者主动调用
 * 2) 跨服传送忽略 - 需要增加 BungeeCord/代理层支持
 * 3) 队友补血回血 - 需要增加反作弊检测
 * 4) 队长退出/场地内开始战斗前阶段处理 place-portal - 需要状态机完善
 */
public final class DuelService {
    private static final String TABLE_DUELS = "duel_history";
    private static final String TABLE_STATS = "duel_player_stats";

    // 所有决斗（决斗ID -> 决斗）
    private final Map<UUID, Duel> duels = new ConcurrentHashMap<>();

    // 玩家决斗映射（玩家UUID -> 决斗ID）
    private final Map<UUID, UUID> playerDuels = new ConcurrentHashMap<>();

    // 决斗请求（目标UUID -> 挑战者UUID -> 决斗请求）
    private final Map<UUID, Map<UUID, DuelRequest>> duelRequests = new ConcurrentHashMap<>();

    // 竞技场列表
    private final Map<UUID, DuelArena> arenas = new ConcurrentHashMap<>();

    // 观众（决斗ID -> 观众UUID列表）
    private final Map<UUID, Set<UUID>> spectators = new ConcurrentHashMap<>();

    // 玩家原始位置（用于决斗结束后传送回去）
    private final Map<UUID, Location> playerLocations = new ConcurrentHashMap<>();

    // 玩家统计数据（玩家UUID -> 统计数据）
    private final Map<UUID, PlayerDuelStats> playerStats = new ConcurrentHashMap<>();

    // 数据库服务
    private final DatabaseService databaseService;
    private final Logger logger;
    private volatile boolean databaseEnabled = false;

    // 同步锁对象
    private final Object duelLock = new Object();

    // 可配置的请求超时时间（毫秒）
    private long requestTimeoutMs = 60000;

    // 可配置的竞技场不可用提示
    private String arenaUnavailableMessage = "没有可用的竞技场";

    // 插件引用（用于调度任务）
    private org.bukkit.plugin.Plugin plugin;

    // 配置文件
    private FileConfiguration config;

    // 决斗配置
    private DuelConfig duelConfig;

    // Kit 装备系统
    private Map<String, DuelKit> kits = new ConcurrentHashMap<>();

    // 玩家原始装备快照
    private final Map<UUID, PlayerSnapshot> playerSnapshots = new ConcurrentHashMap<>();

    // 玩家分数（BO 制）
    private final Map<UUID, Integer> playerScores = new ConcurrentHashMap<>();

    // 决斗超时任务
    private final Map<UUID, BukkitTask> duelTimers = new ConcurrentHashMap<>();

    // 奖励配置
    private double winBaseReward = 10.0;
    private double wagerRewardRatio = 1.0;
    private boolean rewardsEnabled = true;

    // 经济服务
    private EconomyService economyService;

    public DuelService() {
        this(null, null);
    }

    public DuelService(DatabaseService databaseService, Logger logger) {
        this(databaseService, logger, null);
    }

    public DuelService(DatabaseService databaseService, Logger logger, org.bukkit.plugin.Plugin plugin) {
        this.databaseService = databaseService;
        this.logger = logger;
        this.plugin = plugin;
        loadDefaultKits();
    }

    /**
     * 设置经济服务（用于真实奖励）
     */
    public void setEconomyService(EconomyService economyService) {
        this.economyService = economyService;
    }

    /**
     * 获取经济服务
     */
    public EconomyService getEconomyService() {
        return economyService;
    }

    /**
     * 设置插件引用
     */
    public void setPlugin(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载配置
     */
    public void loadConfig(FileConfiguration config) {
        if (config == null) return;
        this.config = config;

        // 加载决斗配置
        this.duelConfig = new DuelConfig(
            config.getInt("duel.request-timeout", 60) * 1000L,
            config.getInt("duel.preparation-time", 5),
            config.getInt("duel.timeout", 600),
            config.getDouble("duel.max-wager", 10000.0),
            config.getDouble("duel.min-wager", 0.0),
            config.getBoolean("duel.wager-enabled", true),
            config.getBoolean("best-of.enabled", true),
            config.getInt("best-of.round-interval", 3),
            config.getInt("best-of.round-timeout", 300)
        );

        // 更新请求超时
        this.requestTimeoutMs = duelConfig.requestTimeout();

        // 加载奖励配置
        this.winBaseReward = config.getDouble("rewards.win-base", 10.0);
        this.wagerRewardRatio = config.getDouble("rewards.wager-ratio", 1.0);
        this.rewardsEnabled = config.getBoolean("rewards.enabled", true);

        // 加载 Kit 配置
        loadKitsFromConfig(config);
    }

    /**
     * 从配置文件加载 Kit
     */
    private void loadKitsFromConfig(FileConfiguration config) {
        if (config == null) return;

        ConfigurationSection kitSection = config.getConfigurationSection("kits");
        if (kitSection == null) return;

        for (String kitName : kitSection.getKeys(false)) {
            String path = "kits." + kitName + ".";
            if (!config.getBoolean(path + "enabled", true)) continue;

            String displayName = config.getString(path + "name", kitName);
            List<String> effectsStr = config.getStringList(path + "effects");

            List<PotionEffectConfig> effects = new ArrayList<>();
            for (String effectStr : effectsStr) {
                String[] parts = effectStr.split(":");
                if (parts.length >= 3) {
                    try {
                        PotionEffectType type = PotionEffectType.getByName(parts[0]);
                        if (type == null) {
                            // 尝试通过命名空间方式获取 (1.21+)
                            try {
                                Registry<PotionEffectType> registry = Bukkit.getRegistry(PotionEffectType.class);
                                type = registry.get(NamespacedKey.minecraft(parts[0].toLowerCase()));
                            } catch (UnsupportedOperationException e) {
                                logger.fine("Registry access not supported for potion effect: " + parts[0]);
                            }
                        }
                        int level = Integer.parseInt(parts[1]);
                        int duration = Integer.parseInt(parts[2]) * 20; // 转 tick
                        if (type != null) {
                            effects.add(new PotionEffectConfig(type, duration, level));
                        }
                    } catch (NumberFormatException e) {
                        logger.fine("Invalid effect format in kit '" + kitName + "': " + effectStr);
                    }
                }
            }

            Map<Integer, ItemStack> armor = new HashMap<>();
            String[] armorKeys = {"helmet", "chestplate", "leggings", "boots"};
            int[] armorSlots = {39, 38, 37, 36};
            for (int i = 0; i < armorKeys.length; i++) {
                String material = config.getString(path + "armor." + armorKeys[i]);
                if (material != null && !material.isEmpty()) {
                    try {
                        armor.put(armorSlots[i], new ItemStack(getMaterial(material)));
                    } catch (IllegalArgumentException e) {
                        logger.fine("Invalid armor material '" + material + "' in kit '" + kitName + "'");
                    }
                }
            }

            List<ItemStack> weapons = new ArrayList<>();
            List<String> weaponStrs = config.getStringList(path + "weapons");
            for (String weaponStr : weaponStrs) {
                try {
                    String[] parts = weaponStr.split(":");
                    Material mat = getMaterial(parts[0]);
                    int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    weapons.add(new ItemStack(mat, amount));
                } catch (IllegalArgumentException e) {
                    logger.fine("Invalid weapon entry '" + weaponStr + "' in kit '" + kitName + "'");
                }
            }

            kits.put(kitName.toLowerCase(), new DuelKit(kitName, displayName, armor, weapons, effects));
        }
    }

    /**
     * 加载默认 Kit
     */
    private void loadDefaultKits() {
        if (!kits.isEmpty()) return;

        kits.put("default", new DuelKit("default", "默认", Map.of(
            39, new ItemStack(Material.DIAMOND_HELMET),
            38, new ItemStack(Material.DIAMOND_CHESTPLATE),
            37, new ItemStack(Material.DIAMOND_LEGGINGS),
            36, new ItemStack(Material.DIAMOND_BOOTS)
        ), List.of(new ItemStack(Material.DIAMOND_SWORD)), List.of(
            new PotionEffectConfig(PotionEffectType.FIRE_RESISTANCE, 300 * 20, 1)
        )));

        kits.put("classic", new DuelKit("classic", "经典", Map.of(
            39, new ItemStack(Material.IRON_HELMET),
            38, new ItemStack(Material.IRON_CHESTPLATE),
            37, new ItemStack(Material.IRON_LEGGINGS),
            36, new ItemStack(Material.IRON_BOOTS)
        ), List.of(new ItemStack(Material.IRON_SWORD)), List.of()));

        kits.put("op", new DuelKit("op", "OP装备", Map.of(
            39, new ItemStack(Material.NETHERITE_HELMET),
            38, new ItemStack(Material.NETHERITE_CHESTPLATE),
            37, new ItemStack(Material.NETHERITE_LEGGINGS),
            36, new ItemStack(Material.NETHERITE_BOOTS)
        ), List.of(new ItemStack(Material.NETHERITE_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 64)), List.of(
            new PotionEffectConfig(PotionEffectType.FIRE_RESISTANCE, 600 * 20, 1),
            new PotionEffectConfig(PotionEffectType.STRENGTH, 300 * 20, 1),
            new PotionEffectConfig(PotionEffectType.SPEED, 300 * 20, 1)
        )));
    }

    /**
     * 设置请求超时时间（毫秒）
     */
    public void setRequestTimeoutMs(long timeoutMs) {
        this.requestTimeoutMs = Math.max(5000, timeoutMs);
    }

    /**
     * 获取请求超时时间（毫秒）
     */
    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /**
     * 设置竞技场不可用时的提示信息
     */
    public void setArenaUnavailableMessage(String message) {
        this.arenaUnavailableMessage = message != null ? message : "没有可用的竞技场";
    }

    /**
     * 获取竞技场不可用时的提示信息
     */
    public String getArenaUnavailableMessage() {
        return arenaUnavailableMessage;
    }

    /**
     * 检查是否有可用竞技场
     */
    public boolean hasAvailableArena() {
        return arenas.values().stream().anyMatch(DuelArena::isAvailable);
    }

    /**
     * 获取可用竞技场数量
     */
    public int getAvailableArenaCount() {
        return (int) arenas.values().stream().filter(DuelArena::isAvailable).count();
    }

    /**
     * 启动服务，初始化数据库表
     */
    public void start() {
        initializeDatabase();
        loadPlayerStats();
    }

    /**
     * 停止服务，保存所有数据
     */
    public void stop() {
        // 取消所有定时器
        for (BukkitTask task : duelTimers.values()) {
            task.cancel();
        }
        duelTimers.clear();
        saveAll();
    }

    // ==================== 数据库初始化 ====================

    private void initializeDatabase() {
        if (databaseService == null || !databaseService.isRunning()) {
            return;
        }

        databaseEnabled = true;

        try {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement()) {

                    // 创建决斗历史表
                    String createDuelTable = "CREATE TABLE IF NOT EXISTS " + TABLE_DUELS + " (" +
                        "duel_id VARCHAR(36) PRIMARY KEY, " +
                        "challenger_id VARCHAR(36) NOT NULL, " +
                        "opponent_id VARCHAR(36) NOT NULL, " +
                        "arena_id VARCHAR(36), " +
                        "wager DECIMAL(15,2) DEFAULT 0, " +
                        "kit_name VARCHAR(32) DEFAULT 'default', " +
                        "best_of INT DEFAULT 1, " +
                        "challenger_score INT DEFAULT 0, " +
                        "opponent_score INT DEFAULT 0, " +
                        "state VARCHAR(20) NOT NULL, " +
                        "winner_id VARCHAR(36), " +
                        "end_reason VARCHAR(20), " +
                        "challenger_damage INT DEFAULT 0, " +
                        "opponent_damage INT DEFAULT 0, " +
                        "challenger_hits INT DEFAULT 0, " +
                        "opponent_hits INT DEFAULT 0, " +
                        "duration_seconds BIGINT DEFAULT 0, " +
                        "created_at BIGINT NOT NULL, " +
                        "started_at BIGINT, " +
                        "ended_at BIGINT)";
                    stmt.execute(createDuelTable);

                    // 创建玩家统计数据表
                    String createStatsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_STATS + " (" +
                        "player_id VARCHAR(36) PRIMARY KEY, " +
                        "total_duels INT DEFAULT 0, " +
                        "wins INT DEFAULT 0, " +
                        "losses INT DEFAULT 0, " +
                        "draws INT DEFAULT 0, " +
                        "total_damage_dealt INT DEFAULT 0, " +
                        "total_damage_taken INT DEFAULT 0, " +
                        "total_wager_won DECIMAL(15,2) DEFAULT 0, " +
                        "total_wager_lost DECIMAL(15,2) DEFAULT 0, " +
                        "longest_win_streak INT DEFAULT 0, " +
                        "current_win_streak INT DEFAULT 0, " +
                        "updated_at BIGINT NOT NULL)";
                    stmt.execute(createStatsTable);

                    // 创建索引
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_duels_challenger ON " + TABLE_DUELS + " (challenger_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_duels_opponent ON " + TABLE_DUELS + " (opponent_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_duels_created ON " + TABLE_DUELS + " (created_at)");

                } catch (Exception e) {
                    logger.warning("Failed to initialize duel database tables: " + e.getMessage());
                    databaseEnabled = false;
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to initialize duel database: " + e.getMessage());
            databaseEnabled = false;
        }
    }

    // ==================== 决斗请求 ====================

    /**
     * 发送决斗请求
     */
    public DuelRequest sendDuelRequest(UUID challengerId, UUID opponentId, double wager, String kitName, int bestOf) {
        // 检查是否已在决斗中
        if (playerDuels.containsKey(challengerId)) {
            throw new IllegalStateException("你已在决斗中");
        }
        if (playerDuels.containsKey(opponentId)) {
            throw new IllegalStateException("对方已在决斗中");
        }

        // 验证赌注
        if (duelConfig.wagerEnabled()) {
            if (wager < duelConfig.minWager()) {
                throw new IllegalStateException("赌注金额不能低于 " + duelConfig.minWager());
            }
            if (wager > duelConfig.maxWager()) {
                throw new IllegalStateException("赌注金额不能超过 " + duelConfig.maxWager());
            }
        }

        // 创建请求
        DuelRequest request = new DuelRequest(
            UUID.randomUUID(),
            challengerId,
            opponentId,
            wager,
            kitName != null ? kitName : "default",
            bestOf > 0 ? bestOf : 1,
            System.currentTimeMillis(),
            requestTimeoutMs
        );

        // 保存请求
        duelRequests.computeIfAbsent(opponentId, k -> new ConcurrentHashMap<>())
            .put(challengerId, request);

        return request;
    }

    /**
     * 发送决斗请求（兼容旧接口，默认 BO1）
     */
    public DuelRequest sendDuelRequest(UUID challengerId, UUID opponentId, double wager, String kitName) {
        return sendDuelRequest(challengerId, opponentId, wager, kitName, 1);
    }

    /**
     * 发送决斗请求（最简接口，默认 kit 和 BO1）
     */
    public DuelRequest sendDuelRequest(UUID challengerId, UUID opponentId, double wager) {
        return sendDuelRequest(challengerId, opponentId, wager, "default", 1);
    }

    /**
     * 接受决斗请求 - 添加同步锁防止竞态条件
     */
    public synchronized Duel acceptDuelRequest(UUID opponentId, UUID challengerId) {
        // Double-check 防止玩家已在决斗中
        if (playerDuels.containsKey(challengerId)) {
            throw new IllegalStateException("挑战者已在决斗中");
        }
        if (playerDuels.containsKey(opponentId)) {
            throw new IllegalStateException("你已在决斗中");
        }

        // 获取请求
        Map<UUID, DuelRequest> requests = duelRequests.get(opponentId);
        if (requests == null) {
            throw new IllegalStateException("决斗请求不存在");
        }

        DuelRequest request = requests.remove(challengerId);
        if (request == null) {
            throw new IllegalStateException("决斗请求不存在");
        }

        // 检查是否超时（可配置）
        if (System.currentTimeMillis() - request.timestamp() > requestTimeoutMs) {
            throw new IllegalStateException("决斗请求已超时");
        }

        // 查找可用竞技场
        DuelArena arena = findAvailableArena();
        if (arena == null) {
            // 提供更详细的反馈
            int available = getAvailableArenaCount();
            String feedback = available > 0
                ? arenaUnavailableMessage + "（可能正在使用中）"
                : arenaUnavailableMessage + "（所有竞技场都在维护中）";
            throw new IllegalStateException(feedback);
        }

        // 创建决斗
        UUID duelId = UUID.randomUUID();
        Duel duel = new Duel(duelId, challengerId, opponentId, arena, request.wager(), request.kitName(), request.bestOf());

        // 占用竞技场
        arena.occupy(duelId);

        // 保存
        duels.put(duelId, duel);
        playerDuels.put(challengerId, duelId);
        playerDuels.put(opponentId, duelId);

        // 保存到数据库
        saveDuel(duel);

        return duel;
    }

    /**
     * 拒绝决斗请求
     */
    public boolean rejectDuelRequest(UUID opponentId, UUID challengerId) {
        Map<UUID, DuelRequest> requests = duelRequests.get(opponentId);
        if (requests != null) {
            return requests.remove(challengerId) != null;
        }
        return false;
    }

    /**
     * 获取某玩家收到的所有挑战者 UUID（用于 /duel accept|deny 无参时自动选择）
     */
    public Set<UUID> getPendingChallengers(UUID opponentId) {
        Map<UUID, DuelRequest> requests = duelRequests.get(opponentId);
        if (requests == null || requests.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(requests.keySet());
    }

    /**
     * 清理超时的决斗请求
     */
    public void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        for (Map<UUID, DuelRequest> requests : duelRequests.values()) {
            requests.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp() > requestTimeoutMs);
        }
    }

    // ==================== 决斗管理 ====================

    /**
     * 开始决斗
     */
    public void startDuel(UUID duelId, org.bukkit.entity.Player challenger, org.bukkit.entity.Player opponent) {
        Duel duel = duels.get(duelId);
        if (duel == null) {
            throw new IllegalStateException("决斗不存在");
        }

        // 保存玩家原始位置和装备
        savePlayerSnapshot(challenger);
        savePlayerSnapshot(opponent);
        playerLocations.put(challenger.getUniqueId(), challenger.getLocation());
        playerLocations.put(opponent.getUniqueId(), opponent.getLocation());

        // 应用 Kit 装备
        applyKit(challenger, duel.getKitName());
        applyKit(opponent, duel.getKitName());

        // 初始化分数
        playerScores.put(challenger.getUniqueId(), 0);
        playerScores.put(opponent.getUniqueId(), 0);

        // 传送到竞技场
        DuelArena arena = duel.getArena();
        challenger.teleport(arena.getSpawn1());
        opponent.teleport(arena.getSpawn2());

        // 发送准备信息
        String msg = "§7你将与 §f" + (challenger.equals(opponent) ? "" : opponent.getName()) + " §7进行决斗";
        challenger.sendMessage(msg);
        opponent.sendMessage(msg);

        // 开始准备倒计时
        startCountdown(duelId, challenger, opponent, duel);
    }

    /**
     * 开始准备倒计时
     */
    private void startCountdown(UUID duelId, Player challenger, Player opponent, Duel duel) {
        if (plugin == null) return;

        new BukkitRunnable() {
            int countdown = duelConfig.preparationTime();

            @Override
            public void run() {
                // 检查决斗是否还存在
                Duel currentDuel = duels.get(duelId);
                if (currentDuel == null || currentDuel.getState() == Duel.DuelState.FINISHED) {
                    cancel();
                    return;
                }

                if (countdown <= 0) {
                    // 开始决斗
                    currentDuel.start();
                    updateDuelStart(duelId);

                    // 发送开始消息
                    String msg = "§a§l决斗开始！";
                    challenger.sendMessage(msg);
                    opponent.sendMessage(msg);

                    // 播放音效
                    playSound(challenger, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    playSound(opponent, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    // 启动超时定时器
                    startDuelTimer(duelId, currentDuel);

                    cancel();
                    return;
                }

                // 发送倒计时
                String msg = "§e决斗将在 §f" + countdown + " §e秒后开始...";
                challenger.sendMessage(msg);
                opponent.sendMessage(msg);

                // 最后3秒播放提示音
                if (countdown <= 3) {
                    playSound(challenger, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, (float) (1.0 + (3 - countdown) * 0.2));
                    playSound(opponent, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, (float) (1.0 + (3 - countdown) * 0.2));
                }

                countdown--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * 播放音效
     */
    private void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * 启动决斗超时定时器
     */
    private void startDuelTimer(UUID duelId, Duel duel) {
        if (plugin == null) return;

        int timeout = duelConfig.timeout();
        if (timeout <= 0) return;

        BukkitTask task = new BukkitRunnable() {
            int remaining = timeout;
            int checkInterval = 60; // 每分钟检查一次

            @Override
            public void run() {
                Duel currentDuel = duels.get(duelId);
                if (currentDuel == null || currentDuel.getState() != Duel.DuelState.IN_PROGRESS) {
                    cancel();
                    duelTimers.remove(duelId);
                    return;
                }

                remaining--;

                // 30秒警告
                if (remaining == 30) {
                    Player challenger = Bukkit.getPlayer(currentDuel.getChallenger());
                    Player opponent = Bukkit.getPlayer(currentDuel.getOpponent());
                    String warning = "§e决斗还剩 §f30 §e秒！";
                    if (challenger != null) challenger.sendMessage(warning);
                    if (opponent != null) opponent.sendMessage(warning);
                }

                // 超时
                if (remaining <= 0) {
                    cancel();
                    duelTimers.remove(duelId);

                    // 根据分数判定获胜者
                    UUID winner = determineWinnerByScore(currentDuel);

                    Player challenger = Bukkit.getPlayer(currentDuel.getChallenger());
                    Player opponent = Bukkit.getPlayer(currentDuel.getOpponent());
                    String timeoutMsg = "§c决斗超时！";
                    if (challenger != null) challenger.sendMessage(timeoutMsg);
                    if (opponent != null) opponent.sendMessage(timeoutMsg);

                    endDuel(duelId, winner, Duel.DuelEndReason.TIMEOUT);
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L); // 每分钟检查一次

        duelTimers.put(duelId, task);
    }

    /**
     * 根据分数判定获胜者
     */
    private UUID determineWinnerByScore(Duel duel) {
        int challengerScore = playerScores.getOrDefault(duel.getChallenger(), 0);
        int opponentScore = playerScores.getOrDefault(duel.getOpponent(), 0);

        if (challengerScore > opponentScore) {
            return duel.getChallenger();
        } else if (opponentScore > challengerScore) {
            return duel.getOpponent();
        }
        return null; // 平局
    }

    /**
     * 结束决斗
     */
    public void endDuel(UUID duelId, UUID winner, Duel.DuelEndReason reason) {
        Duel duel = duels.get(duelId);
        if (duel == null) return;

        // 取消超时定时器
        BukkitTask timer = duelTimers.remove(duelId);
        if (timer != null) timer.cancel();

        // 记录统计数据
        Duel.DuelStats stats = duel.getStats();
        updatePlayerStats(duel, winner, reason, stats);

        // 处理奖励
        if (rewardsEnabled && winner != null) {
            giveRewards(duel, winner);
        }

        // 结束决斗
        duel.end(winner, reason);

        // 传送玩家回原地并恢复装备
        returnPlayersToOriginalLocation(duel);

        // 释放竞技场
        duel.getArena().release();

        // 移除玩家映射
        playerDuels.remove(duel.getChallenger());
        playerDuels.remove(duel.getOpponent());

        // 清理玩家分数
        playerScores.remove(duel.getChallenger());
        playerScores.remove(duel.getOpponent());

        // 清理装备快照
        playerSnapshots.remove(duel.getChallenger());
        playerSnapshots.remove(duel.getOpponent());

        // 清理观众
        spectators.remove(duelId);

        // 更新数据库
        updateDuelEnd(duelId, winner, reason, stats);

        // 检查 BO 多局制下一轮
        if (duel.getBestOf() > 1) {
            handleBestOfNextRound(duel, winner);
        }
    }

    /**
     * 给予奖励
     */
    private void giveRewards(Duel duel, UUID winnerId) {
        if (!rewardsEnabled) return;

        Player winner = Bukkit.getPlayer(winnerId);
        if (winner == null || !winner.isOnline()) return;

        double reward = winBaseReward;

        // 赌注奖励
        if (duel.getWager() > 0) {
            reward += duel.getWager() * wagerRewardRatio;
        }

        // 使用经济服务发放真实奖励
        if (economyService != null) {
            BigDecimal rewardAmount = BigDecimal.valueOf(reward);
            if (economyService.deposit(winnerId, rewardAmount)) {
                winner.sendMessage("§a§l+ " + String.format("%.2f", reward) + " 金币（决斗胜利奖励）");
                winner.playSound(winner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

                // 赌注处理：从失败者扣除
                UUID loserId = duel.getOpponent(winnerId);
                if (duel.getWager() > 0 && loserId != null) {
                    BigDecimal wagerAmount = BigDecimal.valueOf(duel.getWager());
                    if (economyService.withdraw(loserId, wagerAmount)) {
                        Player loser = Bukkit.getPlayer(loserId);
                        if (loser != null) {
                            loser.sendMessage("§c- " + String.format("%.2f", duel.getWager()) + " 金币（决斗失败）");
                        }
                    }
                }
                return;
            }
        }

        // 如果没有经济服务，至少显示奖励信息
        winner.sendMessage("§a§l+ " + String.format("%.2f", reward) + " 金币（决斗胜利奖励）");
        playSound(winner, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    /**
     * 处理 BO 多局制的下一轮
     */
    private void handleBestOfNextRound(Duel duel, UUID lastRoundWinner) {
        // BO 决斗结束后重置分数，等待下一场
        playerScores.remove(duel.getChallenger());
        playerScores.remove(duel.getOpponent());
    }

    /**
     * 记录一回合胜利（用于 BO 制）
     */
    public void recordRoundWin(UUID duelId, UUID winner) {
        Duel duel = duels.get(duelId);
        if (duel == null) return;

        int currentScore = playerScores.getOrDefault(winner, 0);
        playerScores.put(winner, currentScore + 1);

        int winsNeeded = getWinsNeeded(duel.getBestOf());
        int newScore = currentScore + 1;

        Player winnerPlayer = Bukkit.getPlayer(winner);
        Player loser = Bukkit.getPlayer(duel.getOpponent(winner));

        if (newScore >= winsNeeded) {
            // 决斗结束 - 最终胜利
            playVictoryAnimation(winnerPlayer, loser, newScore);
            sendMsg(winnerPlayer, "§a§l本回合胜利！");
            sendMsg(loser, "§c§l本回合失败！");
            endDuel(duelId, winner, Duel.DuelEndReason.DEATH);
        } else {
            // 显示当前比分
            int loserScore = playerScores.getOrDefault(duel.getOpponent(winner), 0);
            sendMsg(winnerPlayer, "§a§l本回合胜利！当前比分: " + newScore + "-" + loserScore);
            sendMsg(loser, "§c§l本回合失败！当前比分: " + loserScore + "-" + newScore);

            // 播放回合胜利动画
            playRoundWinAnimation(winnerPlayer, loser, newScore, loserScore);

            // 准备下一回合
            UUID finalWinner = winner;
            new BukkitRunnable() {
                int countdown = duelConfig.roundInterval();

                @Override
                public void run() {
                    if (countdown <= 0) {
                        resetForNextRound(winnerPlayer, loser, duel);
                        cancel();
                        return;
                    }

                    // 最后5秒发送标题提示
                    if (countdown <= 5) {
                        sendTitle(winnerPlayer, "§e" + countdown, "§f下一回合即将开始");
                        sendTitle(loser, "§e" + countdown, "§f下一回合即将开始");
                        playSound(winnerPlayer, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                        playSound(loser, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    } else {
                        String msg = "§e下一回合将在 §f" + countdown + " §e秒后开始...";
                        sendMsg(winnerPlayer, msg);
                        sendMsg(loser, msg);
                    }

                    countdown--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }
    }

    /**
     * 播放回合胜利动画
     */
    private void playRoundWinAnimation(Player winner, Player loser, int winnerScore, int loserScore) {
        if (winner != null && winner.isOnline()) {
            sendTitle(winner, "§a§l回合胜利!", "§f当前比分: §a" + winnerScore + "§f-" + "§c" + loserScore);
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
        if (loser != null && loser.isOnline()) {
            sendTitle(loser, "§c§l本回合失败", "§f当前比分: §a" + loserScore + "§f-" + "§c" + winnerScore);
            loser.playSound(loser.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
        }
    }

    /**
     * 播放最终胜利动画
     */
    private void playVictoryAnimation(Player winner, Player loser, int finalScore) {
        if (winner != null && winner.isOnline()) {
            sendTitle(winner, "§6§l最终胜利!", "§a你赢得了 BO" + duelConfig.bestOfEnabled() + " 决斗!");
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            // 播放庆祝粒子效果
            winner.getWorld().spawnParticle(Particle.FIREWORK, winner.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        }
        if (loser != null && loser.isOnline()) {
            sendTitle(loser, "§c§l失败", "§7最佳成绩: §f" + finalScore + " 回合");
            loser.playSound(loser.getLocation(), Sound.ENTITY_GENERIC_DEATH, 0.5f, 1.0f);
        }
    }

    /**
     * 发送标题消息
     */
    private void sendTitle(Player player, String title, String subtitle) {
        if (player == null || !player.isOnline()) return;
        player.sendTitle(title, subtitle, 10, 40, 10);
    }

    private int getWinsNeeded(int bestOf) {
        return switch (bestOf) {
            case 3 -> 2;
            case 5 -> 3;
            case 1 -> 1;
            default -> (bestOf + 1) / 2;
        };
    }

    private void resetForNextRound(Player p1, Player p2, Duel duel) {
        if (p1 != null && p1.isOnline()) {
            p1.setHealth(p1.getMaxHealth());
            applyKit(p1, duel.getKitName());
            p1.teleport(duel.getArena().getSpawn1());
        }
        if (p2 != null && p2.isOnline()) {
            p2.setHealth(p2.getMaxHealth());
            applyKit(p2, duel.getKitName());
            p2.teleport(duel.getArena().getSpawn2());
        }

        duel.resetRoundStats();

        String msg = "§a§l回合开始！";
        sendMsg(p1, msg);
        sendMsg(p2, msg);

        // 重启超时计时器
        startDuelTimer(duel.getId(), duel);
    }

    private void sendMsg(Player player, String msg) {
        if (player != null && player.isOnline()) {
            player.sendMessage(msg);
        }
    }

    /**
     * 取消决斗
     */
    public boolean cancelDuel(UUID duelId) {
        Duel duel = duels.get(duelId);
        if (duel == null) return false;

        duel.setState(Duel.DuelState.CANCELLED);
        duel.getArena().release();

        playerDuels.remove(duel.getChallenger());
        playerDuels.remove(duel.getOpponent());

        // 更新数据库
        updateDuelEnd(duelId, null, Duel.DuelEndReason.ADMIN, null);

        return true;
    }

    /**
     * 认输
     */
    public void forfeit(UUID playerId) {
        UUID duelId = playerDuels.get(playerId);
        if (duelId == null) {
            throw new IllegalStateException("你不在决斗中");
        }

        Duel duel = duels.get(duelId);
        if (duel == null) return;

        UUID winner = duel.getOpponent(playerId);
        Player loser = Bukkit.getPlayer(playerId);
        Player winnerP = Bukkit.getPlayer(winner);

        sendMsg(loser, "§c你认输了！");
        sendMsg(winnerP, "§a对手认输了，你获胜了！");

        endDuel(duelId, winner, Duel.DuelEndReason.FORFEIT);
    }

    // ==================== Kit 系统 ====================

    /**
     * 应用 Kit 装备
     */
    public void applyKit(Player player, String kitName) {
        DuelKit kit = kits.getOrDefault(kitName != null ? kitName.toLowerCase() : "default", kits.get("default"));
        if (kit == null) return;

        PlayerInventory inv = player.getInventory();

        // 清除装备和物品
        inv.clear();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // 设置装备
        for (Map.Entry<Integer, ItemStack> entry : kit.armor().entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue());
        }

        // 设置武器
        for (int i = 0; i < kit.weapons().size() && i < 9; i++) {
            inv.setItem(i, kit.weapons().get(i));
        }

        // 应用药水效果
        for (PotionEffectConfig effect : kit.effects()) {
            player.addPotionEffect(new PotionEffect(effect.type(), effect.duration(), effect.level()));
        }
    }

    /**
     * 获取可用 Kit 列表
     */
    public Collection<DuelKit> getAvailableKits() {
        return kits.values();
    }

    /**
     * 获取指定 Kit
     */
    public DuelKit getKit(String kitName) {
        return kits.get(kitName != null ? kitName.toLowerCase() : "default");
    }

    // ==================== 玩家装备快照 ====================

    /**
     * 保存玩家装备快照
     */
    private void savePlayerSnapshot(Player player) {
        PlayerInventory inv = player.getInventory();
        PlayerSnapshot snapshot = new PlayerSnapshot(
            inv.getContents().clone(),
            inv.getArmorContents().clone(),
            inv.getExtraContents().clone(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getExp(),
            new ArrayList<>(player.getActivePotionEffects())
        );
        playerSnapshots.put(player.getUniqueId(), snapshot);
    }

    /**
     * 恢复玩家装备快照
     */
    private void restorePlayerSnapshot(Player player) {
        PlayerSnapshot snapshot = playerSnapshots.get(player.getUniqueId());
        if (snapshot == null) return;

        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setContents(snapshot.inventory());
        inv.setArmorContents(snapshot.armor());
        inv.setExtraContents(snapshot.extra());

        player.setHealth(snapshot.health());
        player.setFoodLevel(snapshot.food());
        player.setSaturation(snapshot.saturation());
        player.setExp(snapshot.exp());

        // 清除并恢复药水效果
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        for (PotionEffect effect : snapshot.effects()) {
            player.addPotionEffect(effect);
        }
    }

    /**
     * 传送玩家回原地并恢复装备
     */
    private void returnPlayersToOriginalLocation(Duel duel) {
        UUID c1 = duel.getChallenger();
        UUID c2 = duel.getOpponent();

        Location loc1 = playerLocations.remove(c1);
        Location loc2 = playerLocations.remove(c2);

        Player p1 = Bukkit.getPlayer(c1);
        Player p2 = Bukkit.getPlayer(c2);

        if (p1 != null && p1.isOnline()) {
            restorePlayerSnapshot(p1);
            if (loc1 != null) p1.teleport(loc1);
        }

        if (p2 != null && p2.isOnline()) {
            restorePlayerSnapshot(p2);
            if (loc2 != null) p2.teleport(loc2);
        }
    }

    /**
     * 处理玩家复活
     */
    public void respawnPlayer(Player player, UUID duelId) {
        Duel duel = duels.get(duelId);
        if (duel == null) return;

        restorePlayerSnapshot(player);

        Location originalLoc = playerLocations.get(player.getUniqueId());
        if (originalLoc != null) {
            player.teleport(originalLoc);
        }

        playerSnapshots.remove(player.getUniqueId());
    }

    // ==================== 观众管理 ====================

    /**
     * 添加观众
     */
    public boolean addSpectator(UUID duelId, UUID spectatorId) {
        Duel duel = duels.get(duelId);
        if (duel == null || duel.getState() != Duel.DuelState.IN_PROGRESS) {
            return false;
        }

        spectators.computeIfAbsent(duelId, k -> ConcurrentHashMap.newKeySet())
            .add(spectatorId);

        return true;
    }

    /**
     * 移除观众
     */
    public boolean removeSpectator(UUID duelId, UUID spectatorId) {
        Set<UUID> specs = spectators.get(duelId);
        if (specs != null) {
            return specs.remove(spectatorId);
        }
        return false;
    }

    /**
     * 获取观众列表
     */
    public Set<UUID> getSpectators(UUID duelId) {
        Set<UUID> specs = spectators.get(duelId);
        return specs != null ? new HashSet<>(specs) : Collections.emptySet();
    }

    // ==================== 战斗统计 ====================

    /**
     * 记录伤害
     */
    public void recordDamage(UUID attackerId, UUID victimId, int damage) {
        UUID duelId = playerDuels.get(attackerId);
        if (duelId == null) return;

        Duel duel = duels.get(duelId);
        if (duel == null) return;

        duel.recordDamage(attackerId, damage);
        duel.recordHit(attackerId);
    }

    // ==================== 竞技场管理 ====================

    /**
     * 添加竞技场
     */
    public void addArena(DuelArena arena) {
        arenas.put(arena.getId(), arena);
    }

    /**
     * 移除竞技场
     */
    public void removeArena(UUID arenaId) {
        arenas.remove(arenaId);
    }

    /**
     * 查找可用竞技场
     */
    private DuelArena findAvailableArena() {
        return arenas.values().stream()
            .filter(DuelArena::isAvailable)
            .findFirst()
            .orElse(null);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取玩家决斗
     */
    public Duel getPlayerDuel(UUID playerId) {
        UUID duelId = playerDuels.get(playerId);
        return duelId != null ? duels.get(duelId) : null;
    }

    /**
     * 获取决斗请求列表
     */
    public List<DuelRequest> getDuelRequests(UUID playerId) {
        Map<UUID, DuelRequest> requests = duelRequests.get(playerId);
        if (requests == null) return Collections.emptyList();

        return new ArrayList<>(requests.values());
    }

    /**
     * 获取所有进行中的决斗
     */
    public List<Duel> getActiveDuels() {
        return duels.values().stream()
            .filter(d -> d.getState() == Duel.DuelState.IN_PROGRESS)
            .toList();
    }

    /**
     * 获取玩家原始位置
     */
    public Location getPlayerLocation(UUID playerId) {
        return playerLocations.remove(playerId);
    }

    /**
     * 获取玩家决斗统计
     */
    public PlayerDuelStats getPlayerStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, this::loadPlayerStatsFromDb);
    }

    /**
     * 获取玩家决斗历史
     */
    public List<DuelHistory> getPlayerHistory(UUID playerId, int limit, int offset) {
        if (!databaseEnabled) {
            return Collections.emptyList();
        }

        List<DuelHistory> history = new ArrayList<>();
        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "SELECT * FROM " + TABLE_DUELS + " WHERE challenger_id = ? OR opponent_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, playerId.toString());
                    stmt.setInt(3, limit);
                    stmt.setInt(4, offset);

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            history.add(new DuelHistory(
                                UUID.fromString(rs.getString("duel_id")),
                                UUID.fromString(rs.getString("challenger_id")),
                                UUID.fromString(rs.getString("opponent_id")),
                                rs.getString("arena_id") != null ? UUID.fromString(rs.getString("arena_id")) : null,
                                rs.getDouble("wager"),
                                Duel.DuelState.valueOf(rs.getString("state")),
                                rs.getString("winner_id") != null ? UUID.fromString(rs.getString("winner_id")) : null,
                                rs.getString("end_reason") != null ? Duel.DuelEndReason.valueOf(rs.getString("end_reason")) : null,
                                rs.getInt("challenger_damage"),
                                rs.getInt("opponent_damage"),
                                rs.getInt("challenger_hits"),
                                rs.getInt("opponent_hits"),
                                rs.getLong("duration_seconds"),
                                rs.getLong("created_at"),
                                rs.getLong("started_at"),
                                rs.getLong("ended_at")
                            ));
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to load duel history: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to load duel history: " + e.getMessage());
        }
        return history;
    }

    // ==================== 持久化 ====================

    /**
     * 保存所有决斗数据
     */
    public void saveAll() {
        if (!databaseEnabled) {
            return;
        }

        // 保存活跃决斗
        for (Duel duel : duels.values()) {
            if (duel.getState() == Duel.DuelState.IN_PROGRESS) {
                saveDuel(duel);
            }
        }

        // 保存玩家统计
        for (Map.Entry<UUID, PlayerDuelStats> entry : playerStats.entrySet()) {
            savePlayerStats(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 保存决斗到数据库
     */
    private void saveDuel(Duel duel) {
        if (!databaseEnabled) return;

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "INSERT OR REPLACE INTO " + TABLE_DUELS + " (duel_id, challenger_id, opponent_id, arena_id, wager, state, winner_id, end_reason, challenger_damage, opponent_damage, challenger_hits, opponent_hits, duration_seconds, created_at, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, duel.getId().toString());
                    stmt.setString(2, duel.getChallenger().toString());
                    stmt.setString(3, duel.getOpponent().toString());
                    stmt.setString(4, duel.getArena() != null ? duel.getArena().getId().toString() : null);
                    stmt.setDouble(5, duel.getWager());
                    stmt.setString(6, duel.getState().name());
                    stmt.setString(7, duel.getWinner() != null ? duel.getWinner().toString() : null);
                    stmt.setString(8, duel.getEndReason() != null ? duel.getEndReason().name() : null);

                    Duel.DuelStats stats = duel.getStats();
                    stmt.setInt(9, stats.challengerDamage());
                    stmt.setInt(10, stats.opponentDamage());
                    stmt.setInt(11, stats.challengerHits());
                    stmt.setInt(12, stats.opponentHits());
                    stmt.setLong(13, stats.duration());

                    stmt.setLong(14, duel.getCreatedTime());
                    stmt.setLong(15, duel.getStartTime() > 0 ? duel.getStartTime() : 0);
                    stmt.setLong(16, duel.getEndTime() > 0 ? duel.getEndTime() : 0);

                    stmt.executeUpdate();
                } catch (Exception e) {
                    logger.warning("SQL error in saveDuel: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to save duel: " + e.getMessage());
        }
    }

    /**
     * 更新决斗开始时间
     */
    private void updateDuelStart(UUID duelId) {
        if (!databaseEnabled) return;

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "UPDATE " + TABLE_DUELS + " SET started_at = ? WHERE duel_id = ?";
                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, System.currentTimeMillis());
                    stmt.setString(2, duelId.toString());
                    stmt.executeUpdate();
                } catch (Exception e) {
                    logger.warning("SQL error in updateDuelStart: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to update duel start: " + e.getMessage());
        }
    }

    /**
     * 更新决斗结束
     */
    private void updateDuelEnd(UUID duelId, UUID winner, Duel.DuelEndReason reason, Duel.DuelStats stats) {
        if (!databaseEnabled) return;

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "UPDATE " + TABLE_DUELS + " SET winner_id = ?, end_reason = ?, challenger_damage = ?, opponent_damage = ?, challenger_hits = ?, opponent_hits = ?, duration_seconds = ?, ended_at = ?, state = ? WHERE duel_id = ?";
                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, winner != null ? winner.toString() : null);
                    stmt.setString(2, reason != null ? reason.name() : null);

                    if (stats != null) {
                        stmt.setInt(3, stats.challengerDamage());
                        stmt.setInt(4, stats.opponentDamage());
                        stmt.setInt(5, stats.challengerHits());
                        stmt.setInt(6, stats.opponentHits());
                        stmt.setLong(7, stats.duration());
                    } else {
                        stmt.setInt(3, 0);
                        stmt.setInt(4, 0);
                        stmt.setInt(5, 0);
                        stmt.setInt(6, 0);
                        stmt.setLong(7, 0);
                    }

                    stmt.setLong(8, System.currentTimeMillis());
                    stmt.setString(9, Duel.DuelState.FINISHED.name());
                    stmt.setString(10, duelId.toString());

                    stmt.executeUpdate();
                } catch (Exception e) {
                    logger.warning("SQL error in updateDuelEnd: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to update duel end: " + e.getMessage());
        }
    }

    /**
     * 加载玩家统计数据
     */
    private void loadPlayerStats() {
        if (!databaseEnabled) return;

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "SELECT * FROM " + TABLE_STATS;
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {

                    while (rs.next()) {
                        UUID playerId = UUID.fromString(rs.getString("player_id"));
                        playerStats.put(playerId, new PlayerDuelStats(
                            playerId,
                            rs.getInt("total_duels"),
                            rs.getInt("wins"),
                            rs.getInt("losses"),
                            rs.getInt("draws"),
                            rs.getInt("total_damage_dealt"),
                            rs.getInt("total_damage_taken"),
                            rs.getDouble("total_wager_won"),
                            rs.getDouble("total_wager_lost"),
                            rs.getInt("longest_win_streak"),
                            rs.getInt("current_win_streak")
                        ));
                    }
                } catch (Exception e) {
                    logger.warning("SQL error loading player stats: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to load player stats: " + e.getMessage());
        }
    }

    /**
     * 从数据库加载单个玩家统计
     */
    private PlayerDuelStats loadPlayerStatsFromDb(UUID playerId) {
        if (!databaseEnabled) {
            return new PlayerDuelStats(playerId);
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "SELECT * FROM " + TABLE_STATS + " WHERE player_id = ?";
                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            playerStats.put(playerId, new PlayerDuelStats(
                                playerId,
                                rs.getInt("total_duels"),
                                rs.getInt("wins"),
                                rs.getInt("losses"),
                                rs.getInt("draws"),
                                rs.getInt("total_damage_dealt"),
                                rs.getInt("total_damage_taken"),
                                rs.getDouble("total_wager_won"),
                                rs.getDouble("total_wager_lost"),
                                rs.getInt("longest_win_streak"),
                                rs.getInt("current_win_streak")
                            ));
                        }
                    }
                } catch (Exception e) {
                    logger.warning("SQL error loading stats for " + playerId + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to load player stats for " + playerId + ": " + e.getMessage());
        }

        return playerStats.getOrDefault(playerId, new PlayerDuelStats(playerId));
    }

    /**
     * 更新玩家统计
     */
    private void updatePlayerStats(Duel duel, UUID winner, Duel.DuelEndReason reason, Duel.DuelStats stats) {
        UUID winnerId = duel.getChallenger();
        UUID loserId = duel.getOpponent();
        boolean isDraw = winner == null;

        if (!isDraw) {
            winnerId = winner;
            loserId = duel.getOpponent(winner);
        }

        // 更新胜者统计 - 传入 duel 引用以便正确计算伤害归属
        updateSinglePlayerStats(winnerId, duel.getChallenger(), true, duel.getWager(), stats, isDraw);
        // 更新败者统计
        updateSinglePlayerStats(loserId, duel.getChallenger(), false, duel.getWager(), stats, isDraw);
    }

    private void updateSinglePlayerStats(UUID playerId, UUID duelChallenger, boolean won, double wager, Duel.DuelStats stats, boolean isDraw) {
        PlayerDuelStats current = playerStats.computeIfAbsent(playerId, PlayerDuelStats::new);

        int challengerDamage = stats != null ? stats.challengerDamage() : 0;
        int opponentDamage = stats != null ? stats.opponentDamage() : 0;
        int damageDealt = playerId.equals(duelChallenger) ? challengerDamage : opponentDamage;
        int damageTaken = playerId.equals(duelChallenger) ? opponentDamage : challengerDamage;

        int newWins = current.wins() + (won && !isDraw ? 1 : 0);
        int newLosses = current.losses() + (!won && !isDraw ? 1 : 0);
        int newDraws = current.draws() + (isDraw ? 1 : 0);

        int newStreak = won && !isDraw ? current.currentWinStreak() + 1 : 0;
        int longestStreak = Math.max(current.longestWinStreak(), newStreak);

        double newWagerWon = current.totalWagerWon() + (won && !isDraw ? wager : 0);
        double newWagerLost = current.totalWagerLost() + (!won && !isDraw ? wager : 0);

        PlayerDuelStats updated = new PlayerDuelStats(
            playerId,
            current.totalDuels() + 1,
            newWins,
            newLosses,
            newDraws,
            current.totalDamageDealt() + damageDealt,
            current.totalDamageTaken() + damageTaken,
            newWagerWon,
            newWagerLost,
            longestStreak,
            newStreak
        );

        playerStats.put(playerId, updated);
        savePlayerStats(playerId, updated);
    }

    /**
     * 保存玩家统计到数据库
     */
    private void savePlayerStats(UUID playerId, PlayerDuelStats stats) {
        if (!databaseEnabled) return;

        try {
            databaseService.dataSource().ifPresent(ds -> {
                String sql = "INSERT OR REPLACE INTO " + TABLE_STATS + " (player_id, total_duels, wins, losses, draws, total_damage_dealt, total_damage_taken, total_wager_won, total_wager_lost, longest_win_streak, current_win_streak, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (Connection conn = ds.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.setInt(2, stats.totalDuels());
                    stmt.setInt(3, stats.wins());
                    stmt.setInt(4, stats.losses());
                    stmt.setInt(5, stats.draws());
                    stmt.setInt(6, stats.totalDamageDealt());
                    stmt.setInt(7, stats.totalDamageTaken());
                    stmt.setDouble(8, stats.totalWagerWon());
                    stmt.setDouble(9, stats.totalWagerLost());
                    stmt.setInt(10, stats.longestWinStreak());
                    stmt.setInt(11, stats.currentWinStreak());
                    stmt.setLong(12, System.currentTimeMillis());

                    stmt.executeUpdate();
                } catch (Exception e) {
                    logger.warning("SQL error in savePlayerStats: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to save player stats: " + e.getMessage());
        }
    }

    // ==================== 决斗请求 ====================

    /**
     * 决斗请求
     */
    public record DuelRequest(
        UUID id,
        UUID challengerId,
        UUID opponentId,
        double wager,
        String kitName,
        int bestOf,
        long timestamp,
        long timeout
    ) {}

    // ==================== 玩家统计 ====================

    /**
     * 玩家决斗统计
     */
    public record PlayerDuelStats(
        UUID playerId,
        int totalDuels,
        int wins,
        int losses,
        int draws,
        int totalDamageDealt,
        int totalDamageTaken,
        double totalWagerWon,
        double totalWagerLost,
        int longestWinStreak,
        int currentWinStreak
    ) {
        public PlayerDuelStats(UUID playerId) {
            this(playerId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public double winRate() {
            return totalDuels > 0 ? (double) wins / totalDuels * 100 : 0;
        }

        public double netProfit() {
            return totalWagerWon - totalWagerLost;
        }
    }

    // ==================== 决斗历史 ====================

    /**
     * 决斗历史记录
     */
    public record DuelHistory(
        UUID duelId,
        UUID challengerId,
        UUID opponentId,
        UUID arenaId,
        double wager,
        String kitName,
        int bestOf,
        Duel.DuelState state,
        UUID winnerId,
        Duel.DuelEndReason endReason,
        int challengerScore,
        int opponentScore,
        int challengerDamage,
        int opponentDamage,
        int challengerHits,
        int opponentHits,
        long durationSeconds,
        long createdAt,
        long startedAt,
        long endedAt
    ) {
        public DuelHistory(UUID duelId, UUID challengerId, UUID opponentId, UUID arenaId,
                          double wager, Duel.DuelState state, UUID winnerId,
                          Duel.DuelEndReason endReason, int challengerDamage,
                          int opponentDamage, int challengerHits, int opponentHits,
                          long durationSeconds, long createdAt, long startedAt, long endedAt) {
            this(duelId, challengerId, opponentId, arenaId, wager, "default", 1,
                 state, winnerId, endReason, 0, 0, challengerDamage, opponentDamage,
                 challengerHits, opponentHits, durationSeconds, createdAt, startedAt, endedAt);
        }

        public boolean isWinner(UUID playerId) {
            return winnerId != null && winnerId.equals(playerId);
        }

        public boolean isDraw() {
            return winnerId == null;
        }

        public String getScore() {
            return challengerScore + " - " + opponentScore;
        }
    }

    // ==================== 配置记录类型 ====================

    /**
     * 决斗配置
     */
    public record DuelConfig(
        long requestTimeout,
        int preparationTime,
        int timeout,
        double maxWager,
        double minWager,
        boolean wagerEnabled,
        boolean bestOfEnabled,
        int roundInterval,
        int roundTimeout
    ) {}

    /**
     * Kit 装备配置
     */
    public record DuelKit(
        String id,
        String displayName,
        Map<Integer, ItemStack> armor,
        List<ItemStack> weapons,
        List<PotionEffectConfig> effects
    ) {}

    /**
     * 药水效果配置
     */
    public record PotionEffectConfig(
        PotionEffectType type,
        int duration,
        int level
    ) {}

    /**
     * 玩家装备快照
     */
    public record PlayerSnapshot(
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack[] extra,
        double health,
        int food,
        float saturation,
        float exp,
        List<PotionEffect> effects
    ) {}

    /**
     * 获取材质（兼容 1.21+ 的废弃 API）
     */
    private Material getMaterial(String name) {
        // 尝试直接获取 (1.21+)
        try {
            Registry<Material> registry = Bukkit.getRegistry(Material.class);
            Material mat = registry.get(NamespacedKey.minecraft(name.toLowerCase()));
            if (mat != null) return mat;
        } catch (Exception e) {
            logger.fine("Registry access failed for material '" + name + "': " + e.getMessage());
        }

        // 尝试通过 key 获取
        try {
            Material mat = Material.valueOf(name.toUpperCase());
            return mat;
        } catch (Exception e) {
            // 尝试通过命名空间 key 获取
            try {
                NamespacedKey key = NamespacedKey.fromString(name);
                if (key == null) {
                    key = NamespacedKey.minecraft(name.toLowerCase());
                }
                return Material.valueOf(key.getKey().toUpperCase());
            } catch (Exception ex) {
                logger.fine("Invalid Material key '" + name + "': " + ex.getMessage());
            }
        }
        return Material.AIR;
    }
}
