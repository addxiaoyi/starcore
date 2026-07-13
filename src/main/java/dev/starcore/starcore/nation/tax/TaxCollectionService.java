package dev.starcore.starcore.nation.tax;

import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.module.economy.TransactionHistoryService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.module.resource.ResourceTradeService;
import dev.starcore.starcore.module.treasury.TaxationService.*;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 税收收集服务（适配器模式）
 *
 * 本类是 TaxationService 的适配器，用于兼容旧代码。
 * 实际税收逻辑委托给 TreasuryService (内部使用 TaxationService)。
 *
 * @deprecated 请使用 TreasuryService 的税收功能
 */
@Deprecated
public class TaxCollectionService {

    private final Plugin plugin;
    private final InternalEconomyService economyService;
    private final TreasuryService treasuryService;
    private final Collection<Nation> nations;
    private final TerritoryService territoryService;
    private final ResourceService resourceService;
    private final ResourceTradeService tradeService;

    // 缓存 UUID -> NationId 映射
    private final Map<UUID, NationId> uuidToNationId = new ConcurrentHashMap<>();

    // 税收记录
    private final Map<UUID, TaxRecord> taxRecords = new ConcurrentHashMap<>();

    // 手动设置的税率缓存（用于旧命令 /tax set）
    private final Map<UUID, Double> manualTaxRates = new ConcurrentHashMap<>();

    // 是否启用
    private boolean enabled = true;

    public TaxCollectionService(
            Plugin plugin,
            InternalEconomyService economyService,
            TreasuryService treasuryService,
            Collection<Nation> nations,
            TerritoryService territoryService,
            ResourceService resourceService,
            ResourceTradeService tradeService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.treasuryService = treasuryService;
        this.nations = nations;
        this.territoryService = territoryService;
        this.resourceService = resourceService;
        this.tradeService = tradeService;
    }

    /**
     * 启动自动收集
     *
     * @deprecated 自动收集由 TreasuryModule 通过 TaxationService 处理
     */
    @Deprecated
    public void startAutoCollection() {
        // 税收自动收集由 TreasuryModule 通过 TaxationService 处理
        // 此方法保留以兼容旧代码
        plugin.getLogger().info("[TaxCollectionService] 自动收集已迁移到 TaxationService，请使用 TreasuryService");
    }

    /**
     * 注册Nation经济（兼容旧代码）
     *
     * @deprecated Nation经济由 TreasuryModule 管理
     */
    @Deprecated
    public void registerNation(UUID nationId, Object economy) {
        // 不再使用 NationEconomy，直接缓存映射
        uuidToNationId.put(nationId, NationId.of(nationId));
    }

    /**
     * 注销Nation
     */
    public void unregisterNation(UUID nationId) {
        uuidToNationId.remove(nationId);
        taxRecords.remove(nationId);
        manualTaxRates.remove(nationId);
    }

    /**
     * 获取 Nation 经济（兼容旧代码）
     *
     * @deprecated 返回 null，请使用 TreasuryService.balance()
     */
    @Deprecated
    public Object getNationEconomy(UUID nationId) {
        // 返回 null，兼容旧代码检查
        return null;
    }

    /**
     * 添加Nation成员
     */
    public void addMember(UUID nationId, UUID playerId) {
        uuidToNationId.put(nationId, NationId.of(nationId));
    }

    /**
     * 移除Nation成员
     */
    public void removeMember(UUID nationId, UUID playerId) {
        // 不需要操作
    }

    /**
     * 收集所有Nation的税款
     */
    public void collectAllTaxes() {
        // 复制一份快照避免并发增删 Nation 抛 ConcurrentModificationException
        Nation[] snapshot;
        try {
            snapshot = nations.toArray(new Nation[0]);
        } catch (Throwable t) {
            // 退化为迭代（保留原行为）
            snapshot = nations.toArray(new Nation[0]);
        }
        for (Nation nation : snapshot) {
            try {
                collectNationTax(nation.id().value());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Failed to collect tax for nation " + nation.id() + ": " + e.getMessage());
            }
        }
    }

    /**
     * 收集单个Nation的税款
     *
     * 使用 TaxationService (通过 TreasuryService) 征收所得税
     */
    @Deprecated
    public TaxCollectionResult collectNationTax(UUID nationId) {
        NationId nid = NationId.of(nationId);

        // 获取手动设置的税率
        Double manualRate = manualTaxRates.get(nationId);

        // 构建税收上下文（使用真实数据）
        List<PlayerIncome> playerIncomes = buildPlayerIncomes(nationId);

        // ============================================================
        // [FIXED] 获取真实税收数据
        // ============================================================
        int claimCount = getClaimCount(nid);
        int businessCount = getBusinessCount(nid);  // 现在使用真实商业数据
        BigDecimal totalTradeValue = getTotalTradeValue(nid);  // 现在使用真实贸易数据

        TaxContext context = new TaxContext(
            claimCount,        // 真实领土数量
            playerIncomes.size(),
            businessCount,     // 真实商业活动数量
            totalTradeValue,   // 真实贸易额
            playerIncomes
        );

        // 尝试使用手动设置的税率征收所得税
        BigDecimal totalAmount = BigDecimal.ZERO;
        int collected = 0;
        int failed = 0;

        if (manualRate != null && manualRate > 0 && !playerIncomes.isEmpty()) {
            // 使用真实收入计算税额（不再基于余额）
            for (PlayerIncome playerIncome : playerIncomes) {
                // [FIXED] 使用真实收入而非余额计算税额
                BigDecimal income = playerIncome.income();
                if (income == null || income.signum() <= 0) {
                    // 设计决策：无交易记录的新玩家/未启用 TransactionHistoryService 时税基为 0
                    // 长期方案：设最低税基或对资产税与所得税分离；当前保守跳过避免误扣
                    continue;  // 无收入则跳过（不再使用余额估算）
                }

                BigDecimal taxAmount = income.multiply(BigDecimal.valueOf(manualRate))
                    .setScale(2, RoundingMode.DOWN);

                if (taxAmount.signum() > 0) {
                    // 从玩家扣款
                    if (economyService.withdraw(playerIncome.playerId(), taxAmount)) {
                        // 存入国库
                        treasuryService.deposit(nid, taxAmount);
                        totalAmount = totalAmount.add(taxAmount);
                        collected++;
                        // 通知玩家被收税金额（审计 A-018 改进玩家体验）
                        try {
                            org.bukkit.entity.Player online = plugin.getServer().getPlayer(playerIncome.playerId());
                            if (online != null) {
                                online.sendMessage(net.kyori.adventure.text.Component.text(
                                    "§7[税收] 已向你征收所得税 §e" + taxAmount.toPlainString() + "§7 金币"));
                            }
                        } catch (Throwable ignored) {
                            // Bukkit 组件 API 在部分平台不可用，忽略
                        }
                    } else {
                        failed++;
                    }
                }
            }
        }

        // 记录税收
        if (totalAmount.signum() > 0) {
            TaxRecord record = taxRecords.computeIfAbsent(nationId, TaxRecord::new);
            // 收集本次参与计税的玩家列表，便于精确统计唯一成员数
            java.util.List<UUID> taxedPlayers = new java.util.ArrayList<>();
            for (PlayerIncome pi : playerIncomes) {
                if (pi.income() != null && pi.income().signum() > 0) {
                    taxedPlayers.add(pi.playerId());
                }
            }
            record.addCollection(totalAmount, taxedPlayers);
        }

        return new TaxCollectionResult(collected, failed, totalAmount.doubleValue());
    }

    /**
     * 构建玩家收入列表
     *
     * 使用 TransactionHistoryService 获取真实收入数据（如果可用）
     */
    @Deprecated
    private List<PlayerIncome> buildPlayerIncomes(UUID nationId) {
        List<PlayerIncome> incomes = new ArrayList<>();
        // 设计决策：当前直接遍历 nations 列表 O(n) 查找
        // 性能优化：可建立 nationId -> Nation 索引，但需 NationService 配合维护

        // 尝试获取交易历史服务
        TransactionHistoryService transactionService = null;
        try {
            transactionService = plugin.getServer().getServicesManager()
                .load(TransactionHistoryService.class);
        } catch (Exception e) {
            // 服务不可用
        }

        for (Nation nation : nations) {
            if (nation.id().value().equals(nationId)) {
                for (var member : nation.members()) {
                    UUID playerId = member.playerId();
                    BigDecimal balance = economyService.balance(playerId);

                    // [FIXED] 使用真实收入数据
                    BigDecimal estimatedIncome = getRealPlayerIncome(playerId, transactionService);

                    incomes.add(new PlayerIncome(
                        playerId,
                        estimatedIncome,
                        balance
                    ));
                }
                break;
            }
        }

        return incomes;
    }

    /**
     * 获取玩家真实收入
     *
     * 优先从 TransactionHistoryService 获取，如果没有则返回0
     */
    private BigDecimal getRealPlayerIncome(UUID playerId, TransactionHistoryService transactionService) {
        if (transactionService != null) {
            try {
                // 获取最近30天的真实收入
                BigDecimal income = transactionService.getIncome(playerId, Duration.ofDays(30));
                if (income != null && income.signum() > 0) {
                    return income.setScale(2, RoundingMode.DOWN);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get income for " + playerId + ": " + e.getMessage());
            }
        }

        // 无交易历史数据时返回0（不再使用不准确的余额估算）
        return BigDecimal.ZERO;
    }

    /**
     * 获取手动设置的税率
     */
    public double getManualTaxRate(UUID nationId) {
        return manualTaxRates.getOrDefault(nationId, 0.0);
    }

    /**
     * 设置手动税率（用于旧命令 /tax set）
     */
    public void setManualTaxRate(UUID nationId, double rate) {
        manualTaxRates.put(nationId, Math.max(0, Math.min(1.0, rate)));
    }

    /**
     * 获取国家拥有的领土数量
     */
    private int getClaimCount(NationId nationId) {
        try {
            if (territoryService != null) {
                // 使用 claimsByOwner 方法，将 NationId 转为 String
                return territoryService.claimsByOwner(nationId.toString()).size();
            }
        } catch (Exception e) {
            // 服务不可用时返回默认值
        }
        return 0;
    }

    /**
     * 获取国家商业活动数量
     * [FIXED] 从 ResourceTradeService 获取真实贸易路线数据
     */
    private int getBusinessCount(NationId nationId) {
        if (tradeService != null) {
            try {
                // 获取该国家的活跃贸易路线数量
                var routes = tradeService.getTradeRoutes(nationId);
                if (routes != null) {
                    return routes.size();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get business count for " + nationId + ": " + e.getMessage());
            }
        }
        return 0;
    }

    /**
     * 获取国家贸易总额
     * @deprecated 需要 ResourceTradeService 支持
     */
    @Deprecated
    private BigDecimal getTotalTradeValue(NationId nationId) {
        // 优先调用 ResourceTradeService.calculateTradeVolume 的真实贸易额接口（ последних 30 天）
        if (tradeService != null) {
            try {
                BigDecimal volume = tradeService.calculateTradeVolume(nationId, Duration.ofDays(30));
                if (volume != null && volume.signum() > 0) {
                    return volume;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get trade volume for " + nationId + ": " + e.getMessage());
            }
        }
        // 设计决策：已使用真实贸易额接口；接口不可用时返回 0，不再用 stockpile 估算
        return BigDecimal.ZERO;
    }

    /**
     * 获取税收记录
     */
    public TaxRecord getTaxRecord(UUID nationId) {
        return taxRecords.get(nationId);
    }

    /**
     * 设置收集间隔（兼容旧代码）
     */
    public void setCollectionInterval(long ticks) {
        // 不再使用
    }

    /**
     * 启用/禁用自动收集
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 数据类 ====================

    /**
     * 税收记录
     */
    public static class TaxRecord {
        private final UUID nationId;
        private long totalCollections = 0;
        // 改用 Set<UUID> 统计唯一成员数，而非每次成员人数的累加
        private final Set<UUID> uniqueMembers = new HashSet<>();
        // 改用 BigDecimal 累计避免大额税收长期累计丢精度
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private long lastCollectionTime = 0;

        public TaxRecord(UUID nationId) {
            this.nationId = nationId;
        }

        public void addCollection(java.math.BigDecimal amount, java.util.Collection<UUID> members) {
            totalCollections++;
            if (members != null) {
                uniqueMembers.addAll(members);
            }
            totalAmount = totalAmount.add(amount);
            lastCollectionTime = System.currentTimeMillis();
        }

        /** 兼容旧 API：以 double 累加（内部转换为 BigDecimal） */
        public void addCollection(double amount, int members) {
            totalCollections++;
            // 注意：兼容旧调用，members 这里无法精确去重，仅作为累计计数保留
            totalAmount = totalAmount.add(java.math.BigDecimal.valueOf(amount));
            lastCollectionTime = System.currentTimeMillis();
        }

        public UUID getNationId() {
            return nationId;
        }

        public long getTotalCollections() {
            return totalCollections;
        }

        public int getTotalMembers() {
            return uniqueMembers.size();
        }

        public double getTotalAmount() {
            return totalAmount.doubleValue();
        }

        /** 精确的 BigDecimal 总额（用于上游财务汇总） */
        public java.math.BigDecimal getTotalAmountExact() {
            return totalAmount;
        }

        public long getLastCollectionTime() {
            return lastCollectionTime;
        }

        public double getAveragePerCollection() {
            return totalCollections > 0
                ? totalAmount.divide(java.math.BigDecimal.valueOf(totalCollections), 2, java.math.RoundingMode.HALF_UP).doubleValue()
                : 0;
        }

        @Override
        public String toString() {
            return String.format(
                "TaxRecord[collections=%d, members=%d, total=%.2f]",
                totalCollections, uniqueMembers.size(), totalAmount.doubleValue()
            );
        }
    }

    /**
     * 税收收集结果
     */
    public record TaxCollectionResult(
        int collected,
        int failed,
        double totalAmount
    ) {
        @Override
        public String toString() {
            return String.format(
                "收集成功: %d人, 失败: %d人, 总额: %.2f",
                collected, failed, totalAmount
            );
        }
    }
}
