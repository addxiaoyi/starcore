package dev.starcore.starcore.module.army.mercenary;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryContractEndedEvent;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryContractEndedEvent.ContractEndReason;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryHiredEvent;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryMissionCompletedEvent;
import dev.starcore.starcore.module.army.mercenary.event.MercenaryPromotedEvent;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 雇佣兵服务核心类
 * 负责雇佣兵的注册、合同管理、支付等功能
 */
public final class MercenaryService {
    private static final String PERSISTENCE_NAMESPACE = "mercenary";
    private static final String CONTRACTS_FILE = "contracts.dat";
    private static final String SETTINGS_FILE = "mercenary_settings.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final EconomyService economyService;
    private final MessageService messages;
    private final PersistenceService persistenceService;
    private final Optional<DatabaseService> databaseService;

    // 雇佣兵合同缓存 (contractId -> Contract)
    private final ConcurrentHashMap<UUID, MercenaryContract> contracts = new ConcurrentHashMap<>();
    // 玩家雇佣兵映射 (mercenaryId -> contractId)
    private final ConcurrentHashMap<UUID, UUID> mercenaryContracts = new ConcurrentHashMap<>();
    // 国家雇佣兵列表 (nationId -> Set<contractId>)
    private final ConcurrentHashMap<UUID, Set<UUID>> nationContracts = new ConcurrentHashMap<>();
    // 雇佣兵设置 (mercenaryId -> settings)
    private final ConcurrentHashMap<UUID, MercenarySettings> mercenarySettings = new ConcurrentHashMap<>();

    // 配置
    private MercenaryConfig config;

    public MercenaryService(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        EconomyService economyService,
        MessageService messages,
        PersistenceService persistenceService,
        Optional<DatabaseService> databaseService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.economyService = economyService;
        this.messages = messages;
        this.persistenceService = persistenceService;
        this.databaseService = databaseService;

        loadConfig();
        loadContracts();
        startPeriodicTasks();
    }

    // ==================== 配置 ====================

    private void loadConfig() {
        var section = plugin.getConfig().getConfigurationSection("mercenary");
        this.config = MercenaryConfig.fromConfig(section);
    }

    public MercenaryConfig getConfig() {
        return config;
    }

    // ==================== 雇佣兵设置 ====================

    public void setMercenaryAvailable(UUID playerId, boolean available) {
        MercenarySettings settings = mercenarySettings.computeIfAbsent(
            playerId, k -> new MercenarySettings(playerId)
        );
        settings.setAvailable(available);
        saveSettings();
    }

    public boolean isMercenaryAvailable(UUID playerId) {
        return mercenarySettings.getOrDefault(playerId, new MercenarySettings(playerId)).isAvailable();
    }

    public void setMercenaryTypes(UUID playerId, Set<MercenaryType> types) {
        MercenarySettings settings = mercenarySettings.computeIfAbsent(
            playerId, k -> new MercenarySettings(playerId)
        );
        settings.setPreferredTypes(types);
        saveSettings();
    }

    public Set<MercenaryType> getMercenaryTypes(UUID playerId) {
        return mercenarySettings.getOrDefault(playerId, new MercenarySettings(playerId)).getPreferredTypes();
    }

    public MercenarySettings getMercenarySettings(UUID playerId) {
        return mercenarySettings.getOrDefault(playerId, new MercenarySettings(playerId));
    }

    // ==================== 合同管理 ====================

    /**
     * 雇佣一个雇佣兵
     * @param mercenaryId 雇佣兵玩家ID
     * @param employerId 雇主玩家ID
     * @param type 雇佣兵类型
     * @param durationDays 合同天数
     * @return 合同对象
     */
    public MercenaryContract hireMercenary(UUID mercenaryId, UUID employerId, MercenaryType type, int durationDays) {
        // 检查雇佣兵是否已有合同
        if (mercenaryContracts.containsKey(mercenaryId)) {
            throw new IllegalStateException("mercenary.already-employed");
        }

        // 检查雇主是否有国家
        Nation employerNation = nationService.getNationByMember(employerId)
            .orElseThrow(() -> new IllegalStateException("mercenary.employer-no-nation"));

        // 检查雇佣兵是否愿意被雇佣
        if (!isMercenaryAvailable(mercenaryId)) {
            throw new IllegalStateException("mercenary.not-available");
        }

        // 检查雇佣兵是否属于其他国家
        nationService.getNationByMember(mercenaryId).ifPresent(nation -> {
            if (!nation.id().value().equals(employerNation.id().value())) {
                throw new IllegalStateException("mercenary.already-in-nation");
            }
        });

        // 计算成本
        int totalCost = type.calculateCost(1, durationDays);
        NationId nationIdObj = employerNation.id();

        // 从国库扣款
        BigDecimal cost = BigDecimal.valueOf(totalCost);
        if (treasuryService.balance(nationIdObj).compareTo(cost) < 0) {
            throw new IllegalStateException("mercenary.insufficient-funds");
        }

        if (!treasuryService.withdraw(nationIdObj, cost)) {
            throw new IllegalStateException("mercenary.withdraw-failed");
        }

        // 创建合同
        MercenaryContract contract = MercenaryContract.create(
            mercenaryId, employerId, employerNation.id().value(), type, durationDays
        );
        contract.addSalary(totalCost);

        // 注册合同
        contracts.put(contract.contractId(), contract);
        mercenaryContracts.put(mercenaryId, contract.contractId());
        nationContracts.computeIfAbsent(employerNation.id().value(), k -> ConcurrentHashMap.newKeySet())
            .add(contract.contractId());

        // 持久化
        saveContract(contract);

        // 发送事件
        Player mercenaryPlayer = Bukkit.getPlayer(mercenaryId);
        String mercenaryName = mercenaryPlayer != null ? mercenaryPlayer.getName() : mercenaryId.toString();
        Player employerPlayer = Bukkit.getPlayer(employerId);
        String employerName = employerPlayer != null ? employerPlayer.getName() : employerId.toString();

        Bukkit.getPluginManager().callEvent(new MercenaryHiredEvent(
            mercenaryId, employerId, employerNation.id().value(),
            mercenaryName, employerName, employerNation.name(),
            type, contract.rank(), durationDays, totalCost
        ));

        return contract;
    }

    /**
     * 终止合同
     */
    public void terminateContract(UUID contractId, ContractEndReason reason) {
        MercenaryContract contract = contracts.get(contractId);
        if (contract == null) {
            return;
        }

        contract.setStatus(ContractStatus.TERMINATED);
        removeContract(contract, reason);
    }

    /**
     * 解雇雇佣兵
     */
    public void dismissMercenary(UUID mercenaryId) {
        UUID contractId = mercenaryContracts.get(mercenaryId);
        if (contractId == null) {
            throw new IllegalStateException("mercenary.no-active-contract");
        }

        MercenaryContract contract = contracts.get(contractId);
        if (contract != null) {
            contract.setStatus(ContractStatus.TERMINATED);
            removeContract(contract, ContractEndReason.DISMISSED);
        }
    }

    /**
     * 雇佣兵辞职
     */
    public void resignMercenary(UUID mercenaryId) {
        UUID contractId = mercenaryContracts.get(mercenaryId);
        if (contractId == null) {
            throw new IllegalStateException("mercenary.no-active-contract");
        }

        MercenaryContract contract = contracts.get(contractId);
        if (contract != null) {
            contract.setStatus(ContractStatus.TERMINATED);
            removeContract(contract, ContractEndReason.TERMINATED_BY_MERCENARY);
        }
    }

    /**
     * 雇佣兵完成任务
     */
    public void completeMission(UUID mercenaryId, String missionType, int reward) {
        UUID contractId = mercenaryContracts.get(mercenaryId);
        if (contractId == null) {
            return;
        }

        MercenaryContract contract = contracts.get(contractId);
        if (contract == null || !contract.isActive()) {
            return;
        }

        contract.addMissionCompleted();
        contract.addSalary(reward);

        // 发送事件
        Nation nation = nationService.nationById(new NationId(contract.employerNationId())).orElse(null);
        String nationName = nation != null ? nation.name() : "Unknown";

        Player mercenaryPlayer = Bukkit.getPlayer(mercenaryId);
        String mercenaryName = mercenaryPlayer != null ? mercenaryPlayer.getName() : mercenaryId.toString();

        Bukkit.getPluginManager().callEvent(new MercenaryMissionCompletedEvent(
            contractId, mercenaryId, contract.employerNationId(),
            mercenaryName, nationName, missionType, reward, 50
        ));

        saveContract(contract);
    }

    /**
     * 雇佣兵击杀
     */
    public void recordKill(UUID mercenaryId) {
        UUID contractId = mercenaryContracts.get(mercenaryId);
        if (contractId == null) return;

        MercenaryContract contract = contracts.get(contractId);
        if (contract == null || !contract.isActive()) return;

        MercenaryRank previousRank = contract.rank();
        contract.addKill();

        // 检查晋升
        if (contract.rank() != previousRank) {
            Player mercenaryPlayer = Bukkit.getPlayer(mercenaryId);
            String mercenaryName = mercenaryPlayer != null ? mercenaryPlayer.getName() : mercenaryId.toString();

            Nation nation = nationService.nationById(new NationId(contract.employerNationId())).orElse(null);
            String nationName = nation != null ? nation.name() : "Unknown";
            Player employerPlayer = Bukkit.getPlayer(contract.employerId());
            String employerName = employerPlayer != null ? employerPlayer.getName() : contract.employerId().toString();

            Bukkit.getPluginManager().callEvent(new MercenaryPromotedEvent(
                mercenaryId, contract.employerId(), contract.employerNationId(),
                mercenaryName, employerName, nationName,
                contract.type(), previousRank, contract.rank(), 15
            ));
        }

        saveContract(contract);
    }

    /**
     * 雇佣兵死亡
     */
    public void recordDeath(UUID mercenaryId) {
        UUID contractId = mercenaryContracts.get(mercenaryId);
        if (contractId == null) return;

        MercenaryContract contract = contracts.get(contractId);
        if (contract == null || !contract.isActive()) return;

        contract.addDeath();

        // 连续死亡可能导致合同终止
        if (contract.deaths() >= config.maxDeathBeforeTermination()) {
            contract.setStatus(ContractStatus.TERMINATED);
            removeContract(contract, ContractEndReason.DEATH);
        } else {
            saveContract(contract);
        }
    }

    /**
     * 直接保存合同（用于外部调用如续约）
     */
    public void saveContractDirectly(MercenaryContract contract) {
        saveContract(contract);
    }

    // ==================== 查询方法 ====================

    public Optional<MercenaryContract> getContract(UUID contractId) {
        return Optional.ofNullable(contracts.get(contractId));
    }

    public Optional<MercenaryContract> getMercenaryContract(UUID mercenaryId) {
        UUID contractId = mercenaryContracts.get(mercenaryId);
        return contractId != null ? Optional.ofNullable(contracts.get(contractId)) : Optional.empty();
    }

    public List<MercenaryContract> getNationContracts(UUID nationId) {
        Set<UUID> contractIds = nationContracts.getOrDefault(nationId, Collections.emptySet());
        return contractIds.stream()
            .map(contracts::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public List<MercenaryContract> getActiveContracts() {
        return contracts.values().stream()
            .filter(MercenaryContract::isActive)
            .collect(Collectors.toList());
    }

    public List<MercenaryContract> getAvailableMercenaries() {
        return contracts.values().stream()
            .filter(c -> c.isActive() && c.status() == ContractStatus.ACTIVE)
            .collect(Collectors.toList());
    }

    public List<MercenaryContract> getMercenariesByType(MercenaryType type) {
        return contracts.values().stream()
            .filter(c -> c.isActive() && c.type() == type)
            .collect(Collectors.toList());
    }

    public int getNationMercenaryCount(UUID nationId) {
        return (int) getNationContracts(nationId).stream()
            .filter(MercenaryContract::isActive)
            .count();
    }

    public boolean isMercenary(UUID playerId) {
        return mercenaryContracts.containsKey(playerId);
    }

    // ==================== 内部方法 ====================

    private void removeContract(MercenaryContract contract, ContractEndReason reason) {
        // 从所有缓存中移除
        contracts.remove(contract.contractId());
        mercenaryContracts.remove(contract.mercenaryId());

        Set<UUID> nationContractSet = nationContracts.get(contract.employerNationId());
        if (nationContractSet != null) {
            nationContractSet.remove(contract.contractId());
        }

        // 发送事件
        Nation nation = nationService.nationById(new NationId(contract.employerNationId())).orElse(null);
        String nationName = nation != null ? nation.name() : "Unknown";

        Player mercenaryPlayer = Bukkit.getPlayer(contract.mercenaryId());
        String mercenaryName = mercenaryPlayer != null ? mercenaryPlayer.getName() : contract.mercenaryId().toString();

        Player employerPlayer = Bukkit.getPlayer(contract.employerId());
        String employerName = employerPlayer != null ? employerPlayer.getName() : contract.employerId().toString();

        Bukkit.getPluginManager().callEvent(new MercenaryContractEndedEvent(
            contract.mercenaryId(), contract.employerId(), contract.employerNationId(),
            mercenaryName, employerName, nationName,
            contract.type(), contract.rank(), reason,
            contract.salary(), contract.missionsCompleted()
        ));

        // 删除持久化数据
        deleteContract(contract.contractId());
    }

    // ==================== 持久化 ====================

    private void loadContracts() {
        if (persistenceService == null) {
            loadContractsFromDatabase();
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, CONTRACTS_FILE);
            for (String key : props.stringPropertyNames()) {
                try {
                    MercenaryContract contract = MercenaryContractCodec.decode(props.getProperty(key));
                    registerLoadedContract(contract);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load contract: " + key + " - " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + contracts.size() + " mercenary contracts");

            // 加载设置
            var settingsProps = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SETTINGS_FILE);
            for (String key : settingsProps.stringPropertyNames()) {
                try {
                    UUID playerId = UUID.fromString(key);
                    MercenarySettings settings = MercenarySettingsCodec.decode(playerId, settingsProps.getProperty(key));
                    mercenarySettings.put(playerId, settings);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load mercenary data: " + e.getMessage());
            loadContractsFromDatabase();
        }
    }

    private void loadContractsFromDatabase() {
        if (databaseService.isEmpty()) return;

        databaseService.get().dataSource().ifPresent(ds -> {
            String sql = "SELECT * FROM mercenary_contracts WHERE status = 'ACTIVE'";
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    try {
                        MercenaryContract contract = MercenaryContractCodec.fromResultSet(rs);
                        registerLoadedContract(contract);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load contract from DB: " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load contracts from database: " + e.getMessage());
            }
        });
    }

    private void registerLoadedContract(MercenaryContract contract) {
        contracts.put(contract.contractId(), contract);
        mercenaryContracts.put(contract.mercenaryId(), contract.contractId());
        nationContracts.computeIfAbsent(contract.employerNationId(), k -> ConcurrentHashMap.newKeySet())
            .add(contract.contractId());
    }

    private void saveContract(MercenaryContract contract) {
        if (persistenceService == null) {
            saveContractToDatabase(contract);
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, CONTRACTS_FILE);
            props.setProperty(contract.contractId().toString(), MercenaryContractCodec.encode(contract));
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, CONTRACTS_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save contract: " + e.getMessage());
            saveContractToDatabase(contract);
        }
    }

    private void saveContractToDatabase(MercenaryContract contract) {
        if (databaseService.isEmpty()) return;

        databaseService.get().dataSource().ifPresent(ds -> {
            String sql = """
                INSERT OR REPLACE INTO mercenary_contracts
                (contract_id, mercenary_id, employer_id, nation_id, type, rank, experience,
                 kills, deaths, missions_completed, salary, hired_at, expires_at, status, last_location, last_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = ds.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, contract.contractId().toString());
                pstmt.setString(2, contract.mercenaryId().toString());
                pstmt.setString(3, contract.employerId().toString());
                pstmt.setString(4, contract.employerNationId().toString());
                pstmt.setString(5, contract.type().key());
                pstmt.setString(6, contract.rank().key());
                pstmt.setInt(7, contract.experience());
                pstmt.setInt(8, contract.kills());
                pstmt.setInt(9, contract.deaths());
                pstmt.setInt(10, contract.missionsCompleted());
                pstmt.setInt(11, contract.salary());
                pstmt.setLong(12, contract.hiredAt().getEpochSecond());
                pstmt.setLong(13, contract.contractExpiresAt() != null ? contract.contractExpiresAt().getEpochSecond() : 0);
                pstmt.setString(14, contract.status().key());
                pstmt.setString(15, locationToString(contract.lastLocation()));
                pstmt.setLong(16, contract.lastActiveAt().getEpochSecond());

                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save contract to database: " + e.getMessage());
            }
        });
    }

    private void deleteContract(UUID contractId) {
        if (persistenceService == null) {
            deleteContractFromDatabase(contractId);
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, CONTRACTS_FILE);
            props.remove(contractId.toString());
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, CONTRACTS_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to delete contract: " + e.getMessage());
        }
    }

    private void deleteContractFromDatabase(UUID contractId) {
        if (databaseService.isEmpty()) return;

        databaseService.get().dataSource().ifPresent(ds -> {
            String sql = "DELETE FROM mercenary_contracts WHERE contract_id = ?";
            try (Connection conn = ds.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, contractId.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete contract from database: " + e.getMessage());
            }
        });
    }

    private void saveSettings() {
        if (persistenceService == null) return;

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SETTINGS_FILE);
            for (Map.Entry<UUID, MercenarySettings> entry : mercenarySettings.entrySet()) {
                props.setProperty(entry.getKey().toString(), MercenarySettingsCodec.encode(entry.getValue()));
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, SETTINGS_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save mercenary settings: " + e.getMessage());
        }
    }

    // ==================== 定时任务 ====================

    private void startPeriodicTasks() {
        // 每分钟检查合同过期
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkExpiredContracts();
        }, 20L * 60, 20L * 60);

        // 每5分钟保存所有数据
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAllContracts();
        }, 20L * 60 * 5, 20L * 60 * 5);
    }

    private void checkExpiredContracts() {
        Instant now = Instant.now();
        for (MercenaryContract contract : new CopyOnWriteArrayList<>(contracts.values())) {
            if (contract.isActive() && contract.contractExpiresAt() != null
                && now.isAfter(contract.contractExpiresAt())) {

                contract.setStatus(ContractStatus.EXPIRED);
                removeContract(contract, ContractEndReason.COMPLETED);
            }
        }
    }

    private void saveAllContracts() {
        for (MercenaryContract contract : contracts.values()) {
            saveContract(contract);
        }
    }

    // ==================== 工具方法 ====================

    private String locationToString(Location loc) {
        if (loc == null) return "";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    /**
     * 关闭服务，保存所有数据
     */
    public void shutdown() {
        saveAllContracts();
        saveSettings();
    }
}