package dev.starcore.starcore.module.donation;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.donation.model.DonationData;
import dev.starcore.starcore.module.donation.storage.DonationStateCodec;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * 献金服务实现
 * 处理玩家向国家的献金逻辑
 */
public final class DonationServiceImpl implements DonationService {
    private static final int SCALE = 2;
    private static final String PERSISTENCE_NAMESPACE = "donation";
    private static final String DONATION_STATE_FILE = "donations.dat";
    private static final String PLAYER_TIER_FILE = "player_tiers.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final InternalEconomyService economyService;
    private final MessageService messages;
    private final PersistenceService persistenceService;
    private final DatabaseService databaseService;
    private final EventService eventService;
    private final DonationStateCodec stateCodec;
    private final DonationConfig config;
    private final Logger logger;

    // 玩家累计献金数据 (playerId -> nationId -> data)
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<NationId, DonationData>> playerDonations = new ConcurrentHashMap<>();
    // 玩家全局累计献金 (playerId -> total amount)
    private final ConcurrentHashMap<UUID, BigDecimal> globalPlayerDonations = new ConcurrentHashMap<>();
    // 献金记录 (id -> record)
    private final ConcurrentHashMap<UUID, DonationRecord> donationRecords = new ConcurrentHashMap<>();
    // 已领取奖励记录 (playerId -> nationId -> Set<rewardId>)
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<NationId, Set<String>>> claimedRewards = new ConcurrentHashMap<>();

    public DonationServiceImpl(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        InternalEconomyService economyService,
        MessageService messages,
        PersistenceService persistenceService,
        DatabaseService databaseService,
        EventService eventService,
        DonationConfig config
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.economyService = economyService;
        this.messages = messages;
        this.persistenceService = persistenceService;
        this.databaseService = databaseService;
        this.eventService = eventService;
        this.config = config;
        this.stateCodec = new DonationStateCodec();
        this.logger = plugin.getLogger();

        // 初始化数据库表
        initializeTables();

        // 加载数据
        loadState();
    }

    private void initializeTables() {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = """
                CREATE TABLE IF NOT EXISTS starcore_donations (
                    id VARCHAR(36) PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    player_name VARCHAR(255) NOT NULL,
                    nation_id VARCHAR(36) NOT NULL,
                    nation_name VARCHAR(255) NOT NULL,
                    amount DECIMAL(20,2) NOT NULL,
                    message TEXT,
                    tier_id VARCHAR(50),
                    donated_at BIGINT NOT NULL,
                    INDEX idx_player_id (player_id),
                    INDEX idx_nation_id (nation_id),
                    INDEX idx_donated_at (donated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to create donations table: " + e.getMessage());
            }
        });
    }

    @Override
    public DonationResult donate(UUID playerId, NationId nationId, BigDecimal amount) {
        return donate(playerId, nationId, amount, null);
    }

    @Override
    public DonationResult donate(UUID playerId, NationId nationId, BigDecimal amount, String message) {
        // 验证金额
        if (amount == null || amount.signum() <= 0) {
            return DonationResult.failure("donation.error.invalid-amount");
        }

        // 验证国家存在
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return DonationResult.failure("donation.error.nation-not-found");
        }
        Nation nation = nationOpt.get();

        // 验证玩家余额
        BigDecimal balance = economyService.balance(playerId);
        if (balance.compareTo(amount) < 0) {
            return DonationResult.failure("donation.error.insufficient-balance");
        }

        // 验证最低献金额
        if (amount.compareTo(config.minDonationAmount()) < 0) {
            return DonationResult.failure("donation.error.below-minimum");
        }

        // 验证最高献金额
        if (config.maxDonationAmount().signum() > 0 && amount.compareTo(config.maxDonationAmount()) > 0) {
            return DonationResult.failure("donation.error.above-maximum");
        }

        // 扣除玩家余额
        if (!economyService.withdraw(playerId, amount)) {
            return DonationResult.failure("donation.error.withdraw-failed");
        }

        // 存入国家国库（使用事务：若失败则回滚玩家扣款）
        boolean depositSuccess = false;
        try {
            treasuryService.deposit(nationId, amount);
            depositSuccess = true;
        } catch (Exception e) {
            // 回滚玩家扣款
            economyService.deposit(playerId, amount);
            logger.warning("Failed to deposit to treasury, rolled back player withdrawal: " + e.getMessage());
            return DonationResult.failure("donation.error.treasury-deposit-failed");
        }

        // 获取玩家名称
        String playerName = getPlayerName(playerId);

        // 计算献金前后等级
        DonationTier previousTier = getPlayerTier(playerId, nationId);
        BigDecimal previousTotal = getTotalDonations(playerId, nationId);

        // 记录献金
        UUID donationId = UUID.randomUUID();
        Instant now = Instant.now();
        DonationTier newTier = calculateTier(previousTotal.add(amount));

        DonationRecord record = new DonationRecord(
            donationId,
            playerId,
            playerName,
            nationId,
            nation.name(),
            amount,
            message,
            newTier,
            now
        );

        // 更新内存数据
        updatePlayerDonation(playerId, nationId, amount);
        donationRecords.put(donationId, record);

        // 保存到数据库（失败不影响内存数据，但记录日志）
        try {
            saveDonationToDatabase(record);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to persist donation record: " + e.getMessage());
        }

        // 记录事件
        if (eventService != null) {
            String eventMessage = messages.format(
                "command.event.message.donation",
                playerName,
                amount.setScale(SCALE, RoundingMode.DOWN).toPlainString(),
                nation.name()
            );
            String eventContext = String.format(
                "actor=%s;amount=%s;nation=%s;balance=%s;tier=%s",
                playerName,
                amount.setScale(SCALE, RoundingMode.DOWN).toPlainString(),
                nation.name(),
                treasuryService.balance(nationId).setScale(SCALE, RoundingMode.DOWN).toPlainString(),
                newTier.id()
            );
            eventService.record(nationId, "donation.contribute", eventMessage, eventContext);
        }

        // 执行献金奖励（如果满足条件）
        executeDonationRewards(playerId, nationId, newTier);

        return DonationResult.success(record, previousTier, newTier);
    }

    private void updatePlayerDonation(UUID playerId, NationId nationId, BigDecimal amount) {
        // 更新国家特定献金
        playerDonations.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
            .compute(nationId, (k, existing) -> {
                if (existing == null) {
                    return new DonationData(amount, amount, 1);
                }
                return new DonationData(
                    amount.add(existing.totalAmount()),
                    amount.add(existing.totalAmount()),
                    existing.donationCount() + 1
                );
            });

        // 更新全局献金
        globalPlayerDonations.merge(playerId, amount, (oldVal, amt) -> oldVal.add(amt));
    }

    private void executeDonationRewards(UUID playerId, NationId nationId, DonationTier tier) {
        // 自动奖励逻辑：仅对当前达到的等级发放奖励
        // 注意：若玩家一次献金跨越多个等级（如从铜牌到金牌），中间等级的奖励不会被自动发放
        // 这是设计行为，玩家需要手动 claim 中间等级的奖励
        List<DonationReward> rewards = getAvailableRewards(playerId, nationId);
        for (DonationReward reward : rewards) {
            if (isRewardClaimable(playerId, nationId, reward.id())) {
                // 检查是否是自动发放的奖励
                if (config.autoGrantTierRewards() && reward.requiredTier().id().equals(tier.id())) {
                    claimReward(playerId, nationId, reward.id());
                }
            }
        }
    }

    private String getPlayerName(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        // 尝试从国家成员列表获取
        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isPresent()) {
            return nationOpt.get().members().stream()
                .filter(m -> m.playerId().equals(playerId))
                .map(dev.starcore.starcore.module.nation.model.NationMember::lastKnownName)
                .findFirst()
                .orElse("Unknown");
        }
        return "Unknown";
    }

    @Override
    public List<DonationRecord> getPlayerDonations(UUID playerId) {
        return getPlayerDonations(playerId, 100, 0);
    }

    @Override
    public List<DonationRecord> getPlayerDonations(UUID playerId, int limit, int offset) {
        return donationRecords.values().stream()
            .filter(r -> r.playerId().equals(playerId))
            .sorted(Comparator.comparing(DonationRecord::donatedAt).reversed())
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public List<DonationRecord> getNationDonations(NationId nationId) {
        return getNationDonations(nationId, 100, 0);
    }

    @Override
    public List<DonationRecord> getNationDonations(NationId nationId, int limit, int offset) {
        return donationRecords.values().stream()
            .filter(r -> r.nationId().equals(nationId))
            .sorted(Comparator.comparing(DonationRecord::donatedAt).reversed())
            .skip(offset)
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getTotalDonations(UUID playerId, NationId nationId) {
        DonationData data = playerDonations.getOrDefault(playerId, new ConcurrentHashMap<>()).get(nationId);
        return data != null ? data.totalAmount() : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getTotalDonations(UUID playerId) {
        return globalPlayerDonations.getOrDefault(playerId, BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getTotalDonations(NationId nationId) {
        return donationRecords.values().stream()
            .filter(r -> r.nationId().equals(nationId))
            .map(DonationRecord::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public List<DonationRankingEntry> getDonationRanking(NationId nationId, int limit) {
        // 按国家统计玩家献金
        Map<UUID, BigDecimal> playerTotals = new HashMap<>();
        Map<UUID, String> playerNames = new HashMap<>();

        for (DonationRecord record : donationRecords.values()) {
            if (record.nationId().equals(nationId)) {
                playerTotals.merge(record.playerId(), record.amount(), BigDecimal::add);
                playerNames.put(record.playerId(), record.playerName());
            }
        }

        // 排序并获取前N名
        return playerTotals.entrySet().stream()
            .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> {
                int rank = playerTotals.entrySet().stream()
                    .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                    .toList()
                    .indexOf(entry) + 1;
                return new DonationRankingEntry(
                    rank,
                    entry.getKey(),
                    playerNames.getOrDefault(entry.getKey(), "Unknown"),
                    entry.getValue().setScale(SCALE, RoundingMode.DOWN),
                    calculateTier(entry.getValue())
                );
            })
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Integer> getPlayerRanking(UUID playerId, NationId nationId) {
        List<DonationRankingEntry> ranking = getDonationRanking(nationId, Integer.MAX_VALUE);
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).playerId().equals(playerId)) {
                return Optional.of(i + 1);
            }
        }
        return Optional.empty();
    }

    @Override
    public DonationTier getPlayerTier(UUID playerId) {
        BigDecimal total = getTotalDonations(playerId);
        return calculateTier(total);
    }

    @Override
    public DonationTier getPlayerTier(UUID playerId, NationId nationId) {
        BigDecimal total = getTotalDonations(playerId, nationId);
        return calculateTier(total);
    }

    @Override
    public Map<String, DonationTier> getAllTiers() {
        return config.tiers();
    }

    @Override
    public Optional<DonationTier> getTier(String tierId) {
        return Optional.ofNullable(config.tiers().get(tierId));
    }

    @Override
    public DonationTier getTierForAmount(BigDecimal amount) {
        return calculateTier(amount);
    }

    @Override
    public BigDecimal getAmountNeededForTier(UUID playerId, String tierId) {
        DonationTier targetTier = config.tiers().get(tierId);
        if (targetTier == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal currentTotal = getTotalDonations(playerId);
        BigDecimal needed = targetTier.minAmount().subtract(currentTotal);

        return needed.signum() > 0 ? needed.setScale(SCALE, RoundingMode.DOWN) : BigDecimal.ZERO;
    }

    @Override
    public List<DonationReward> getAvailableRewards(UUID playerId, NationId nationId) {
        List<DonationReward> available = new ArrayList<>();
        DonationTier playerTier = getPlayerTier(playerId, nationId);

        for (DonationReward reward : config.rewards()) {
            // 检查是否满足等级要求
            if (playerTier.priority() >= reward.requiredTier().priority()) {
                // 检查是否已领取（如果是一次性奖励）
                if (reward.oneTime() && isRewardClaimed(playerId, nationId, reward.id())) {
                    continue;
                }
                available.add(reward);
            }
        }

        return available;
    }

    @Override
    public boolean claimReward(UUID playerId, NationId nationId, String rewardId) {
        DonationReward reward = config.rewards().stream()
            .filter(r -> r.id().equals(rewardId))
            .findFirst()
            .orElse(null);

        if (reward == null) {
            return false;
        }

        // 检查是否可领取
        if (!isRewardClaimable(playerId, nationId, rewardId)) {
            return false;
        }

        // 先执行奖励（若玩家离线则跳过，奖励丢失但不标记为已领取）
        boolean executed = executeRewardCommands(playerId, reward.commands());

        // 执行成功后才记录已领取，防止奖励丢失但被标记为已领取
        if (executed) {
            recordClaimedReward(playerId, nationId, rewardId);
            return true;
        }
        return false;
    }

    private boolean executeRewardCommands(UUID playerId, List<String> commands) {
        // 允许的命令白名单前缀（安全命令，不含OP权限）
        List<String> ALLOWED_PREFIXES = List.of(
            "msg ", "tell ", "whisper ", "mail send ",
            "title ", "tellraw ", "playsound ", "particle "
        );

        var player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }

        boolean anyExecuted = false;
        for (String command : commands) {
            // 安全检查：只允许白名单内的命令
            boolean allowed = ALLOWED_PREFIXES.stream().anyMatch(p -> command.toLowerCase().startsWith(p));
            if (!allowed) {
                logger.warning("Blocked potentially unsafe donation reward command: " + command);
                continue;
            }

            // 替换占位符（转义玩家名防止注入）
            String safeName = player.getName().replaceAll("[^a-zA-Z0-9_]", "_");
            String executed = command
                .replace("%player%", safeName)
                .replace("%player_uuid%", playerId.toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), executed);
            anyExecuted = true;
        }
        return anyExecuted;
    }

    private void recordClaimedReward(UUID playerId, NationId nationId, String rewardId) {
        claimedRewards.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(nationId, k -> Collections.synchronizedSet(new HashSet<>()))
            .add(rewardId);

        // 保存到数据库
        saveClaimedRewardToDatabase(playerId, nationId, rewardId);
    }

    @Override
    public boolean isRewardClaimable(UUID playerId, NationId nationId, String rewardId) {
        DonationReward reward = config.rewards().stream()
            .filter(r -> r.id().equals(rewardId))
            .findFirst()
            .orElse(null);

        if (reward == null) {
            return false;
        }

        // 检查等级要求
        DonationTier playerTier = getPlayerTier(playerId, nationId);
        if (playerTier.priority() < reward.requiredTier().priority()) {
            return false;
        }

        // 检查一次性奖励是否已领取
        if (reward.oneTime() && isRewardClaimed(playerId, nationId, rewardId)) {
            return false;
        }

        return true;
    }

    private boolean isRewardClaimed(UUID playerId, NationId nationId, String rewardId) {
        Set<String> claimed = claimedRewards.getOrDefault(playerId, new ConcurrentHashMap<>())
            .getOrDefault(nationId, Collections.emptySet());
        return claimed.contains(rewardId);
    }

    @Override
    public List<ClaimedReward> getClaimedRewards(UUID playerId, NationId nationId) {
        Set<String> claimed = claimedRewards.getOrDefault(playerId, new ConcurrentHashMap<>())
            .getOrDefault(nationId, Collections.emptySet());

        return claimed.stream()
            .map(id -> new ClaimedReward(UUID.randomUUID(), playerId, nationId, id, Instant.now()))
            .collect(Collectors.toList());
    }

    @Override
    public boolean deleteDonation(UUID donationId) {
        DonationRecord record = donationRecords.remove(donationId);
        if (record == null) {
            return false;
        }

        // 从数据库删除
        deleteDonationFromDatabase(donationId);

        return true;
    }

    @Override
    public String summary() {
        BigDecimal totalAmount = donationRecords.values().stream()
            .map(DonationRecord::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
        return donationRecords.size() + " donation(s), total " + totalAmount.toPlainString();
    }

    // ==================== 私有辅助方法 ====================

    private DonationTier calculateTier(BigDecimal amount) {
        DonationTier currentTier = null;
        for (DonationTier tier : config.tiers().values()) {
            if (amount.compareTo(tier.minAmount()) >= 0) {
                if (currentTier == null || tier.priority() > currentTier.priority()) {
                    currentTier = tier;
                }
            }
        }
        return currentTier != null ? currentTier : getDefaultTier();
    }

    private DonationTier getDefaultTier() {
        return config.tiers().values().stream()
            .min(Comparator.comparingInt(DonationTier::priority))
            .orElse(new DonationTier("none", "无", BigDecimal.ZERO, BigDecimal.ZERO, List.of(), Map.of(), 0));
    }

    // ==================== 数据库操作 ====================

    private void saveDonationToDatabase(DonationRecord record) throws SQLException {
        var optDs = databaseService.dataSource();
        if (optDs.isEmpty()) {
            throw new SQLException("DataSource not available");
        }
        var ds = optDs.get();
        String sql = """
            INSERT INTO starcore_donations (id, player_id, player_name, nation_id, nation_name, amount, message, tier_id, donated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, record.id().toString());
            stmt.setString(2, record.playerId().toString());
            stmt.setString(3, record.playerName());
            stmt.setString(4, record.nationId().toString());
            stmt.setString(5, record.nationName());
            stmt.setBigDecimal(6, record.amount().setScale(SCALE, RoundingMode.DOWN));
            stmt.setString(7, record.message());
            stmt.setString(8, record.tier().id());
            stmt.setLong(9, record.donatedAt().toEpochMilli());
            stmt.executeUpdate();
        }
    }

    private void saveClaimedRewardToDatabase(UUID playerId, NationId nationId, String rewardId) {
        databaseService.dataSource().ifPresent(ds -> {
            // 创建 claimed_rewards 表（如果不存在）
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS starcore_claimed_rewards (
                    id VARCHAR(36) PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    nation_id VARCHAR(36) NOT NULL,
                    reward_id VARCHAR(64) NOT NULL,
                    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_claim (player_id, nation_id, reward_id)
                )
                """;
            String insertSql = """
                INSERT INTO starcore_claimed_rewards (id, player_id, nation_id, reward_id)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE claimed_at = CURRENT_TIMESTAMP
                """;
            try (Connection conn = ds.getConnection()) {
                // 确保表存在
                try (Statement createStmt = conn.createStatement()) {
                    createStmt.execute(createTableSql);
                }
                // 插入领取记录
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, UUID.randomUUID().toString());
                    stmt.setString(2, playerId.toString());
                    stmt.setString(3, nationId.value().toString());
                    stmt.setString(4, rewardId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save claimed reward to database: " + e.getMessage());
            }
        });
    }

    private void deleteDonationFromDatabase(UUID donationId) {
        databaseService.dataSource().ifPresent(ds -> {
            String sql = "DELETE FROM starcore_donations WHERE id = ?";
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, donationId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete donation from database: " + e.getMessage());
            }
        });
    }

    // ==================== 持久化 ====================

    private void loadState() {
        if (persistenceService == null) {
            return;
        }

        try {
            // 注意：献金记录和玩家等级数据的加载顺序很重要
            // playerDonations 依赖 stateCodec.decodePlayerTier 独立加载，
            // 不依赖 donationRecords 的加载顺序，但确保两者都加载完成后再使用
            // 加载献金记录
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, DONATION_STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                String json = props.getProperty(key);
                try {
                    UUID id = UUID.fromString(key);
                    DonationRecord record = stateCodec.decodeRecord(json);
                    donationRecords.put(id, record);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load donation from key " + key + ": " + e.getMessage());
                }
            }

            // 加载玩家等级数据（在献金记录之后加载，确保内存状态完整）
            var tierProps = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, PLAYER_TIER_FILE);
            for (String key : tierProps.stringPropertyNames()) {
                String json = tierProps.getProperty(key);
                try {
                    stateCodec.decodePlayerTier(key, json, playerDonations, globalPlayerDonations);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load player tier from key " + key + ": " + e.getMessage());
                }
            }

            plugin.getLogger().info("Loaded " + donationRecords.size() + " donations from persistence");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load donation state: " + e.getMessage());
        }
    }

    private void saveAllState() {
        if (persistenceService == null) {
            return;
        }

        try {
            // 保存献金记录
            var props = new java.util.Properties();
            for (Map.Entry<UUID, DonationRecord> entry : donationRecords.entrySet()) {
                String key = entry.getKey().toString();
                String json = stateCodec.encodeRecord(entry.getValue());
                props.setProperty(key, json);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, DONATION_STATE_FILE, props);

            // 保存玩家等级数据
            var tierProps = new java.util.Properties();
            for (Map.Entry<UUID, ConcurrentHashMap<NationId, DonationData>> playerEntry : playerDonations.entrySet()) {
                String playerKey = playerEntry.getKey().toString();
                for (Map.Entry<NationId, DonationData> nationEntry : playerEntry.getValue().entrySet()) {
                    String key = playerKey + ":" + nationEntry.getKey().toString();
                    String json = stateCodec.encodePlayerTierData(nationEntry.getValue());
                    tierProps.setProperty(key, json);
                }
            }
            // 保存全局数据
            for (Map.Entry<UUID, BigDecimal> entry : globalPlayerDonations.entrySet()) {
                String key = "global:" + entry.getKey().toString();
                tierProps.setProperty(key, entry.getValue().setScale(SCALE, RoundingMode.DOWN).toPlainString());
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, PLAYER_TIER_FILE, tierProps);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save donation state: " + e.getMessage());
        }
    }

    /**
     * 保存所有状态（供外部调用，如插件关闭时）
     */
    public void shutdown() {
        saveAllState();
    }
}
