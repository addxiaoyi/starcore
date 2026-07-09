package dev.starcore.starcore.module.business.service;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.business.BusinessService;
import dev.starcore.starcore.module.business.BusinessService.BusinessCategory;
import dev.starcore.starcore.module.business.BusinessService.BusinessStatistics;
import dev.starcore.starcore.module.business.BusinessService.BusinessTransaction;
import dev.starcore.starcore.module.business.BusinessService.BusinessTransactionType;
import dev.starcore.starcore.module.business.storage.BusinessDatabaseStorage;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 商业服务实现
 * 追踪玩家商业活动，提供交易数据供税务系统使用
 */
public final class BusinessServiceImpl implements StarCoreModule, BusinessService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "business",
        "商业系统",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(BusinessService.class),
        "Tracks player business activities for tax calculation."
    );

    private static final int MAX_CACHE_SIZE = 10000;
    private static final Duration CACHE_EXPIRY = Duration.ofHours(1);

    private final ConcurrentMap<UUID, List<BusinessTransaction>> playerTransactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<BusinessTransaction>> nationTransactions = new ConcurrentHashMap<>();

    // 缓存统计结果
    private final ConcurrentMap<String, CachedStatistics> statisticsCache = new ConcurrentHashMap<>();

    private JavaPlugin plugin;
    private BusinessDatabaseStorage storage;
    private StarCoreContext context;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.context = context;
        this.storage = new BusinessDatabaseStorage(context.databaseService(), plugin.getLogger());

        // 加载历史数据
        loadData();

        plugin.getLogger().info("STARCORE Business module enabled.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存数据
        if (storage != null) {
            storage.saveAll(playerTransactions, nationTransactions);
        }
        plugin.getLogger().info("STARCORE Business module disabled.");
    }

    // ==================== 数据加载/保存 ====================

    private void loadData() {
        try {
            var data = storage.loadAllData();
            playerTransactions.clear();
            nationTransactions.clear();

            // 统一使用 ArrayList 而非混用 CopyOnWriteArrayList
            for (BusinessTransaction tx : data.playerTransactions()) {
                playerTransactions.computeIfAbsent(tx.playerId(), k -> new ArrayList<>()).add(tx);
            }

            for (BusinessTransaction tx : data.nationTransactions()) {
                nationTransactions.computeIfAbsent(tx.nationId().toString(), k -> new ArrayList<>()).add(tx);
            }

            plugin.getLogger().info("Loaded " + playerTransactions.size() + " player transactions");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load business data, starting fresh: " + e.getMessage());
            // 内存中已有空 Map，继续运行
        }
    }

    // ==================== BusinessService 实现 ====================

    @Override
    public boolean recordTransaction(UUID playerId, NationId nationId, BusinessTransactionType type,
                                   BigDecimal amount, BusinessCategory category) {
        if (playerId == null || nationId == null || type == null || amount == null) {
            return false;
        }

        // 验证金额必须为正数，防止税务计算错误
        if (amount.signum() <= 0) {
            plugin.getLogger().warning("Rejected negative/zero transaction: " + amount);
            return false;
        }

        // 注意：此方法无玩家反馈机制，商业交易成功时不会通知玩家
        // 调用方（如命令处理器）应负责向玩家显示交易已记录

        BusinessTransaction transaction = new BusinessTransaction(
            UUID.randomUUID(),
            playerId,
            nationId,
            type,
            category != null ? category : BusinessCategory.OTHER,
            amount,
            Instant.now().toEpochMilli()
        );

        // 添加到玩家交易列表
        playerTransactions.computeIfAbsent(playerId, k -> new ArrayList<>())
            .add(0, transaction);

        // 添加到国家交易列表
        String nationKey = nationId.toString();
        nationTransactions.computeIfAbsent(nationKey, k -> new ArrayList<>())
            .add(0, transaction);

        // 限制内存中的交易数量
        trimTransactions(playerId);
        trimNationTransactions(nationKey);

        // 清除相关缓存
        invalidateCache(nationId);

        // 异步保存到数据库
        storage.saveTransaction(transaction);

        return true;
    }

    @Override
    public BigDecimal getTransactionVolume(NationId nationId, BusinessTransactionType type, int days) {
        String cacheKey = nationId + ":" + (type != null ? type.name() : "ALL") + ":" + days;
        CachedStatistics cached = statisticsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.statistics.totalVolume();
        }

        long cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L).toEpochMilli();
        List<BusinessTransaction> transactions = nationTransactions.getOrDefault(nationId.toString(), List.of());

        BigDecimal total = transactions.stream()
            .filter(tx -> tx.timestamp() > cutoff)
            .filter(tx -> type == null || tx.type() == type)
            .map(BusinessTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 缓存结果
        cacheStatistics(cacheKey, new BusinessStatistics(
            0, total, BigDecimal.ZERO, BigDecimal.ZERO, 0, null
        ));

        return total;
    }

    @Override
    public int getTransactionCount(NationId nationId, int days) {
        long cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L).toEpochMilli();
        List<BusinessTransaction> transactions = nationTransactions.getOrDefault(nationId.toString(), List.of());

        return (int) transactions.stream()
            .filter(tx -> tx.timestamp() > cutoff)
            .count();
    }

    @Override
    public int getActiveBusinessCount(NationId nationId, int days) {
        // 注意：此方法返回的是"活跃交易者"（去重玩家数），
        // 而非"活跃商业实体"数量。方法名可能具有误导性。
        long cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L).toEpochMilli();
        List<BusinessTransaction> transactions = nationTransactions.getOrDefault(nationId.toString(), List.of());

        Set<UUID> activePlayers = transactions.stream()
            .filter(tx -> tx.timestamp() > cutoff)
            .map(BusinessTransaction::playerId)
            .collect(Collectors.toSet());

        return activePlayers.size();
    }

    @Override
    public BusinessStatistics getStatistics(NationId nationId, int days) {
        String cacheKey = "stats:" + nationId + ":" + days;
        CachedStatistics cached = statisticsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.statistics;
        }

        long cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L).toEpochMilli();
        List<BusinessTransaction> transactions = nationTransactions.getOrDefault(nationId.toString(), List.of());

        List<BusinessTransaction> filtered = transactions.stream()
            .filter(tx -> tx.timestamp() > cutoff)
            .toList();

        int count = filtered.size();
        BigDecimal totalVolume = filtered.stream()
            .map(BusinessTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算收入和支出
        Map<BusinessTransactionType, BigDecimal> typeAmounts = new HashMap<>();
        for (BusinessTransaction tx : filtered) {
            typeAmounts.merge(tx.type(), tx.amount(), BigDecimal::add);
        }

        BigDecimal income = typeAmounts.getOrDefault(BusinessTransactionType.SALE, BigDecimal.ZERO);
        BigDecimal expense = typeAmounts.getOrDefault(BusinessTransactionType.PURCHASE, BigDecimal.ZERO);

        // 找出最活跃的类别
        Map<BusinessCategory, Long> categoryCounts = filtered.stream()
            .collect(Collectors.groupingBy(BusinessTransaction::category, Collectors.counting()));
        BusinessCategory topCategory = categoryCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);

        int activeCount = filtered.stream()
            .map(BusinessTransaction::playerId)
            .collect(Collectors.toSet())
            .size();

        BusinessStatistics stats = new BusinessStatistics(
            count, totalVolume, income, expense, activeCount, topCategory
        );

        cacheStatistics(cacheKey, stats);

        return stats;
    }

    @Override
    public List<BusinessTransaction> getPlayerTransactions(UUID playerId, int limit) {
        List<BusinessTransaction> transactions = playerTransactions.getOrDefault(playerId, List.of());
        return transactions.stream().limit(limit).toList();
    }

    @Override
    public List<BusinessTransaction> getNationTransactions(NationId nationId, int limit) {
        List<BusinessTransaction> transactions = nationTransactions.getOrDefault(nationId.toString(), List.of());
        return transactions.stream().limit(limit).toList();
    }

    @Override
    public BigDecimal getPlayerIncome(UUID playerId, int days) {
        long cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L).toEpochMilli();
        List<BusinessTransaction> transactions = playerTransactions.getOrDefault(playerId, List.of());

        return transactions.stream()
            .filter(tx -> tx.timestamp() > cutoff)
            .filter(tx -> tx.type() == BusinessTransactionType.SALE || tx.type() == BusinessTransactionType.SERVICE)
            .map(BusinessTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal getPlayerExpense(UUID playerId, int days) {
        long cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L).toEpochMilli();
        List<BusinessTransaction> transactions = playerTransactions.getOrDefault(playerId, List.of());

        return transactions.stream()
            .filter(tx -> tx.timestamp() > cutoff)
            .filter(tx -> tx.type() == BusinessTransactionType.PURCHASE)
            .map(BusinessTransaction::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 辅助方法 ====================

    private void trimTransactions(UUID playerId) {
        List<BusinessTransaction> list = playerTransactions.get(playerId);
        if (list != null && list.size() > MAX_CACHE_SIZE) {
            // CopyOnWriteArrayList的synchronized块仅保护trim操作本身，
            // recordTransaction中的add操作是独立的volatile写入，可并发进行
            synchronized (list) {
                while (list.size() > MAX_CACHE_SIZE) {
                    list.remove(list.size() - 1);
                }
            }
        }
    }

    private void trimNationTransactions(String nationId) {
        List<BusinessTransaction> list = nationTransactions.get(nationId);
        if (list != null && list.size() > MAX_CACHE_SIZE) {
            synchronized (list) {
                while (list.size() > MAX_CACHE_SIZE) {
                    list.remove(list.size() - 1);
                }
            }
        }
    }

    private void invalidateCache(NationId nationId) {
        String prefix = nationId.toString();
        statisticsCache.keySet().removeIf(key -> key.contains(prefix));
    }

    private void cacheStatistics(String key, BusinessStatistics stats) {
        if (statisticsCache.size() > 1000) {
            statisticsCache.clear();
        }
        statisticsCache.put(key, new CachedStatistics(stats, Instant.now().plus(CACHE_EXPIRY)));
    }

    private record CachedStatistics(BusinessStatistics statistics, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    // 加载数据的结果包装
    private record LoadedData(List<BusinessTransaction> playerTransactions, List<BusinessTransaction> nationTransactions) {}
}
