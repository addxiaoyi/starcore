package dev.starcore.starcore.war;
import java.util.Optional;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 动员系统
 * 负责战争动员、战争基金筹集、征兵等准备工作
 */
public final class MobilizationService {
    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final Logger logger;
    private final MobilizationConfig config;

    // 动员令
    private final ConcurrentHashMap<UUID, Mobilization> mobilizations = new ConcurrentHashMap<>();
    // 国家的动员令索引
    private final ConcurrentHashMap<NationId, UUID> nationMobilizations = new ConcurrentHashMap<>();
    // 战争基金
    private final ConcurrentHashMap<UUID, WarFund> warFunds = new ConcurrentHashMap<>();
    // 征兵记录
    private final ConcurrentHashMap<UUID, List<Conscription>> conscriptions = new ConcurrentHashMap<>();

    public MobilizationService(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        MobilizationConfig config
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.nationService = Objects.requireNonNull(nationService, "nationService");
        this.treasuryService = Objects.requireNonNull(treasuryService, "treasuryService");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = plugin.getLogger();

        startPeriodicTasks();
    }

    /**
     * 发布动员令
     */
    public Mobilization declareMobilization(NationId nationId, UUID warId, MobilizationLevel level) {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(warId, "warId");
        Objects.requireNonNull(level, "level");

        // 检查是否已有动员令
        if (nationMobilizations.containsKey(nationId)) {
            throw new IllegalStateException("Nation already has active mobilization");
        }

        // 计算动员成本
        BigDecimal cost = calculateMobilizationCost(nationId, level);
        if (treasuryService.balance(nationId).compareTo(cost) < 0) {
            throw new IllegalStateException("Insufficient funds for mobilization");
        }

        // 扣除费用
        treasuryService.withdraw(nationId, cost);

        // 创建动员令
        Mobilization mobilization = new Mobilization(
            UUID.randomUUID(),
            nationId,
            warId,
            level,
            Instant.now()
        );

        mobilizations.put(mobilization.id(), mobilization);
        nationMobilizations.put(nationId, mobilization.id());

        logger.info(String.format("Mobilization declared: Nation=%s, Level=%s, Cost=%s",
            nationId, level, cost));

        return mobilization;
    }

    /**
     * 取消动员令
     */
    public void cancelMobilization(NationId nationId) {
        UUID mobilizationId = nationMobilizations.remove(nationId);
        if (mobilizationId != null) {
            Mobilization mobilization = mobilizations.remove(mobilizationId);
            if (mobilization != null) {
                mobilization.cancel();
                logger.info(String.format("Mobilization cancelled: Nation=%s", nationId));
            }
        }
    }

    /**
     * 获取动员令
     */
    public Optional<Mobilization> getMobilization(NationId nationId) {
        UUID mobilizationId = nationMobilizations.get(nationId);
        if (mobilizationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mobilizations.get(mobilizationId));
    }

    /**
     * 创建战争基金
     */
    public WarFund createWarFund(UUID warId, NationId nationId, BigDecimal targetAmount) {
        Objects.requireNonNull(warId, "warId");
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(targetAmount, "targetAmount");

        if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Target amount must be positive");
        }

        WarFund fund = new WarFund(
            UUID.randomUUID(),
            warId,
            nationId,
            targetAmount,
            Instant.now()
        );

        warFunds.put(fund.id(), fund);

        logger.info(String.format("War fund created: Nation=%s, Target=%s", nationId, targetAmount));

        return fund;
    }

    /**
     * 捐献到战争基金
     */
    public void contributeToWarFund(UUID fundId, UUID contributorId, String contributorName, BigDecimal amount) {
        WarFund fund = warFunds.get(fundId);
        if (fund == null) {
            throw new IllegalArgumentException("War fund not found");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        fund.addContribution(contributorId, contributorName, amount);

        logger.fine(String.format("War fund contribution: %s donated %s to fund %s",
            contributorName, amount, fundId));
    }

    /**
     * 征兵
     */
    public Conscription conscript(NationId nationId, UUID warId, int targetSoldiers) {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(warId, "warId");

        if (targetSoldiers <= 0) {
            throw new IllegalArgumentException("Target soldiers must be positive");
        }

        // 检查是否有动员令
        Optional<Mobilization> mobilizationOpt = getMobilization(nationId);
        if (mobilizationOpt.isEmpty()) {
            throw new IllegalStateException("No active mobilization");
        }

        Mobilization mobilization = mobilizationOpt.get();
        int maxSoldiers = mobilization.level().maxConscripts();
        if (targetSoldiers > maxSoldiers) {
            throw new IllegalArgumentException("Exceeds mobilization level limit");
        }

        // 计算征兵成本
        BigDecimal cost = config.conscriptionCostPerSoldier().multiply(BigDecimal.valueOf(targetSoldiers));
        if (treasuryService.balance(nationId).compareTo(cost) < 0) {
            throw new IllegalStateException("Insufficient funds for conscription");
        }

        // 扣除费用
        treasuryService.withdraw(nationId, cost);

        // 创建征兵记录
        Conscription conscription = new Conscription(
            UUID.randomUUID(),
            nationId,
            warId,
            targetSoldiers,
            0,
            Instant.now(),
            Instant.now().plus(config.conscriptionDuration())
        );

        conscriptions.computeIfAbsent(warId, k -> new ArrayList<>()).add(conscription);

        logger.info(String.format("Conscription started: Nation=%s, Target=%d soldiers, Cost=%s",
            nationId, targetSoldiers, cost));

        return conscription;
    }

    /**
     * 获取战争的征兵记录
     */
    public List<Conscription> getConscriptions(UUID warId) {
        return conscriptions.getOrDefault(warId, Collections.emptyList());
    }

    /**
     * 获取国家的征兵记录
     */
    public List<Conscription> getConscriptionsOfNation(NationId nationId) {
        return conscriptions.values().stream()
            .flatMap(List::stream)
            .filter(c -> c.nationId().equals(nationId))
            .collect(Collectors.toList());
    }

    /**
     * 计算动员成本
     */
    private BigDecimal calculateMobilizationCost(NationId nationId, MobilizationLevel level) {
        // 基础成本 * 等级系数
        return config.baseMobilizationCost().multiply(BigDecimal.valueOf(level.costMultiplier()));
    }

    /**
     * 启动定时任务
     */
    private void startPeriodicTasks() {
        // 每小时更新一次
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateConscriptions();
        }, 20L * 60 * 60, 20L * 60 * 60);
    }

    /**
     * 更新征兵进度
     */
    private void updateConscriptions() {
        Instant now = Instant.now();

        for (List<Conscription> warConscriptions : conscriptions.values()) {
            for (Conscription conscription : warConscriptions) {
                if (conscription.isCompleted()) {
                    continue;
                }

                // 更新进度
                conscription.updateProgress(now);

                if (conscription.isCompleted()) {
                    logger.info(String.format("Conscription completed: Nation=%s, Soldiers=%d",
                        conscription.nationId(), conscription.currentSoldiers()));
                }
            }
        }
    }

    /**
     * 动员配置
     */
    public record MobilizationConfig(
        BigDecimal baseMobilizationCost,
        BigDecimal conscriptionCostPerSoldier,
        Duration conscriptionDuration
    ) {
        public static MobilizationConfig defaults() {
            return new MobilizationConfig(
                new BigDecimal("10000"),      // 基础动员成本
                new BigDecimal("100"),        // 每个士兵成本
                Duration.ofHours(24)          // 征兵需要24小时
            );
        }
    }
}

/**
 * 动员等级
 */
enum MobilizationLevel {
    /**
     * 部分动员 - 影响较小，成本较低
     */
    PARTIAL("部分动员", 1.0, 1000, 5),

    /**
     * 全面动员 - 全国范围动员
     */
    FULL("全面动员", 2.0, 5000, 10),

    /**
     * 总动员 - 战时最高动员状态
     */
    TOTAL("总动员", 3.0, 10000, 20);

    private final String displayName;
    private final double costMultiplier;    // 成本系数
    private final int maxConscripts;        // 最大征兵数
    private final int productionBonus;      // 生产加成(%)

    MobilizationLevel(String displayName, double costMultiplier, int maxConscripts, int productionBonus) {
        this.displayName = displayName;
        this.costMultiplier = costMultiplier;
        this.maxConscripts = maxConscripts;
        this.productionBonus = productionBonus;
    }

    public String displayName() {
        return displayName;
    }

    public double costMultiplier() {
        return costMultiplier;
    }

    public int maxConscripts() {
        return maxConscripts;
    }

    public int productionBonus() {
        return productionBonus;
    }
}
