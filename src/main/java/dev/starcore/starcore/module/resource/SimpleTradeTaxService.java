package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交易税收服务实现
 */
public class SimpleTradeTaxService implements TradeTaxService {
    // 默认税率配置
    private static final double DEFAULT_TRANSACTION_FEE_RATE = 2.0;    // 2%
    private static final double DEFAULT_TARIFF_RATE = 5.0;              // 5%
    private static final double DEFAULT_LUXURY_TAX_RATE = 10.0;         // 10%
    private static final double DEFAULT_VAT_RATE = 3.0;                // 3%
    private static final double DEFAULT_CAPITAL_GAINS_RATE = 15.0;     // 15%

    // 奢侈品列表
    private final Set<String> luxuryGoods;

    // 国家税率配置
    private final Map<NationId, Map<TaxType, TaxRateConfig>> nationTaxRates;

    // 税收历史
    private final Map<NationId, List<TaxCollection>> taxHistories;

    // 国库服务
    private TreasuryService treasuryService;

    public SimpleTradeTaxService() {
        this.luxuryGoods = ConcurrentHashMap.newKeySet();
        this.nationTaxRates = new ConcurrentHashMap<>();
        this.taxHistories = new ConcurrentHashMap<>();

        // 初始化默认奢侈品
        luxuryGoods.add("rare_metal");
        luxuryGoods.add("luxury_goods");

        // 初始化默认税率
        initializeDefaultRates();
    }

    /**
     * 设置国库服务
     */
    public void setTreasuryService(TreasuryService treasuryService) {
        this.treasuryService = treasuryService;
    }

    /**
     * 初始化默认税率
     */
    private void initializeDefaultRates() {
        // 创建默认配置用于无国家的情况
        // 国家特有配置将在 setNationTaxRate 时创建
    }

    @Override
    public double calculateTax(String resourceId, double amount, TaxType taxType) {
        if (amount <= 0) return 0;

        // 获取默认税率（如果没有特定国家配置）
        TaxRateConfig config = getDefaultConfig(taxType);

        return calculateTaxFromConfig(amount, config);
    }

    @Override
    public BigDecimal calculateTaxBD(String resourceId, BigDecimal amount, TaxType taxType) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        // 获取默认税率（如果没有特定国家配置）
        TaxRateConfig config = getDefaultConfig(taxType);

        return calculateTaxFromConfigBD(amount, config);
    }

    /**
     * 计算税额（基于配置）
     */
    private double calculateTaxFromConfig(double amount, TaxRateConfig config) {
        if (!config.enabled() || amount <= 0) {
            return 0;
        }

        // 计算百分比税额
        double percentageTax = amount * config.rate().doubleValue() / 100.0;

        // 加上固定金额
        double totalTax = percentageTax + config.fixedAmount().doubleValue();

        // audit B-084: 最小税额保底不应超过交易额本身，否则税款>交易额玩家倒贴
        double minAmount = config.minAmount().doubleValue();
        if (minAmount > 0) {
            totalTax = Math.max(Math.min(minAmount, amount), totalTax);
        }
        // audit B-085: maxAmount<=0 视为"无上限"，避免配置 0 时全部税款被吞
        double maxAmount = config.maxAmount().doubleValue();
        if (maxAmount > 0 && maxAmount < Double.MAX_VALUE) {
            totalTax = Math.min(maxAmount, totalTax);
        }

        return BigDecimal.valueOf(totalTax).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 计算税额（基于配置）- BigDecimal 版本
     */
    private BigDecimal calculateTaxFromConfigBD(BigDecimal amount, TaxRateConfig config) {
        if (!config.enabled() || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 计算百分比税额
        BigDecimal rate = config.rate();
        BigDecimal percentageTax = amount.multiply(rate).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        // 加上固定金额
        BigDecimal totalTax = percentageTax.add(config.fixedAmount());

        // audit B-084: 最小税额保底不应超过交易额本身
        BigDecimal minAmount = config.minAmount();
        if (minAmount.signum() > 0) {
            BigDecimal cappedMin = minAmount.min(amount);
            totalTax = totalTax.max(cappedMin);
        }
        // audit B-085: maxAmount<=0 视为"无上限"，避免配置 0 时全部税款被吞
        BigDecimal maxAmount = config.maxAmount();
        if (maxAmount.signum() > 0
            && maxAmount.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) < 0) {
            totalTax = totalTax.min(maxAmount);
        }

        return totalTax.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public double calculateTotalTax(String resourceId, double amount) {
        if (amount <= 0) return 0;

        double totalTax = 0;

        // 交易手续费（始终收取）
        totalTax += calculateTax(resourceId, amount, TaxType.TRANSACTION_FEE);

        // 增值税（始终收取）
        totalTax += calculateTax(resourceId, amount, TaxType.VAT);

        // 奢侈品税（如果是奢侈品）
        if (isLuxuryGood(resourceId)) {
            totalTax += calculateTax(resourceId, amount, TaxType.LUXURY_TAX);
        }

        return totalTax;
    }

    @Override
    public Map<TaxType, Double> getTaxBreakdown(String resourceId, double amount) {
        Map<TaxType, Double> breakdown = new EnumMap<>(TaxType.class);

        for (TaxType type : TaxType.values()) {
            breakdown.put(type, calculateTax(resourceId, amount, type));
        }

        return breakdown;
    }

    @Override
    public Map<TaxType, TaxRateConfig> getNationTaxRates(NationId nationId) {
        Map<TaxType, TaxRateConfig> rates = nationTaxRates.get(nationId);
        if (rates == null) {
            rates = createDefaultNationRates();
            nationTaxRates.put(nationId, rates);
        }
        return Collections.unmodifiableMap(rates);
    }

    @Override
    public void setNationTaxRate(NationId nationId, TaxType type, TaxRateConfig config) {
        Map<TaxType, TaxRateConfig> rates = nationTaxRates.computeIfAbsent(
            nationId, k -> createDefaultNationRates()
        );
        rates.put(type, config);
    }

    @Override
    public void resetNationTaxRates(NationId nationId) {
        nationTaxRates.put(nationId, createDefaultNationRates());
    }

    @Override
    public void collectTax(NationId nationId, double amount, TaxType type) {
        if (amount <= 0 || nationId == null) return;

        // 设计决策：collectTax 仅把税款存入国库(treasury.deposit)，未从玩家账户 withdraw
        // 建议改为 collectTax(playerId, nationId, amount, type) 传入扣款源
        // 现有 API 形态无法扣源，暂保留语义作为后续架构改造项
        // 创建税收记录
        TaxCollection collection = new TaxCollection(
            nationId,
            type,
            amount,
            0, // 交易金额未提供
            null,
            Instant.now()
        );

        // 添加到历史记录
        List<TaxCollection> history = taxHistories.computeIfAbsent(nationId, k -> new ArrayList<>());
        // audit B-086: history 的 add 与 subList.clear 需原子化，避免并发裁剪异常
        synchronized (history) {
            history.add(0, collection);
            // 限制历史记录数量
            if (history.size() > 1000) {
                history.subList(1000, history.size()).clear();
            }
        }

        // 存入国库
        if (treasuryService != null) {
            treasuryService.deposit(nationId, BigDecimal.valueOf(amount));
        }
    }

    @Override
    public List<TaxCollection> getTaxHistory(NationId nationId, int limit) {
        List<TaxCollection> history = taxHistories.getOrDefault(nationId, List.of());
        return history.stream().limit(limit).toList();
    }

    @Override
    public double getTotalTaxCollected(NationId nationId) {
        List<TaxCollection> history = taxHistories.getOrDefault(nationId, List.of());
        return history.stream().mapToDouble(TaxCollection::amount).sum();
    }

    @Override
    public double getTotalTaxCollected(NationId nationId, TaxType type) {
        List<TaxCollection> history = taxHistories.getOrDefault(nationId, List.of());
        return history.stream()
            .filter(c -> c.type() == type)
            .mapToDouble(TaxCollection::amount)
            .sum();
    }

    @Override
    public boolean isLuxuryGood(String resourceId) {
        return luxuryGoods.contains(resourceId.toLowerCase());
    }

    @Override
    public Set<String> getLuxuryGoods() {
        return Collections.unmodifiableSet(luxuryGoods);
    }

    @Override
    public void addLuxuryGood(String resourceId) {
        luxuryGoods.add(resourceId.toLowerCase());
    }

    @Override
    public void removeLuxuryGood(String resourceId) {
        luxuryGoods.remove(resourceId.toLowerCase());
    }

    /**
     * 获取默认税率配置
     */
    private TaxRateConfig getDefaultConfig(TaxType type) {
        return switch (type) {
            case TRANSACTION_FEE -> TaxRateConfig.defaultRate(type, DEFAULT_TRANSACTION_FEE_RATE);
            case TARIFF -> TaxRateConfig.defaultRate(type, DEFAULT_TARIFF_RATE);
            case LUXURY_TAX -> TaxRateConfig.defaultRate(type, DEFAULT_LUXURY_TAX_RATE);
            case VAT -> TaxRateConfig.defaultRate(type, DEFAULT_VAT_RATE);
            case CAPITAL_GAINS -> TaxRateConfig.defaultRate(type, DEFAULT_CAPITAL_GAINS_RATE);
        };
    }

    /**
     * 创建国家的默认税率配置
     */
    private Map<TaxType, TaxRateConfig> createDefaultNationRates() {
        Map<TaxType, TaxRateConfig> rates = new EnumMap<>(TaxType.class);
        for (TaxType type : TaxType.values()) {
            rates.put(type, getDefaultConfig(type));
        }
        return rates;
    }

    // ==================== 公开访问器 ====================

    /**
     * 计算跨国家关税
     */
    public double calculateTariff(NationId fromNation, NationId toNation, String resourceId, double amount) {
        // 如果是同国，不收取关税
        if (fromNation.equals(toNation)) {
            return 0;
        }

        // 获取进口国关税税率
        Map<TaxType, TaxRateConfig> rates = getNationTaxRates(toNation);
        TaxRateConfig tariffConfig = rates.get(TaxType.TARIFF);

        if (tariffConfig == null || !tariffConfig.enabled()) {
            return 0;
        }

        return calculateTaxFromConfig(amount, tariffConfig);
    }

    /**
     * 计算有效税率（考虑所有因素）
     */
    public double calculateEffectiveTaxRate(String resourceId, double amount, boolean crossNation) {
        // audit B-089: amount<=0 直接 return 0，避免除零产生 NaN/Infinity
        if (amount <= 0) return 0;
        double totalTax = calculateTotalTax(resourceId, amount);
        if (crossNation) {
            // 跨国家交易可能有关税
            totalTax += amount * DEFAULT_TARIFF_RATE / 100.0;
        }
        return (totalTax / amount) * 100.0;
    }

    /**
     * 获取交易净收入（扣除税收后）
     */
    public double getNetProceeds(double grossAmount, String resourceId, boolean isCrossNation) {
        double totalTax = calculateTotalTax(resourceId, grossAmount);
        if (isCrossNation) {
            totalTax += grossAmount * DEFAULT_TARIFF_RATE / 100.0;
        }
        return grossAmount - totalTax;
    }

    /**
     * 获取税率摘要
     */
    public String getTaxSummary(String resourceId) {
        StringBuilder sb = new StringBuilder();
        sb.append("税率摘要 (").append(resourceId).append("):\n");

        for (TaxType type : TaxType.values()) {
            TaxRateConfig config = getDefaultConfig(type);
            if (config.enabled()) {
                sb.append("- ").append(type.getDisplayName())
                  .append(": ").append(config.rate()).append("%\n");
            }
        }

        if (isLuxuryGood(resourceId)) {
            sb.append("注意: 此商品为奢侈品，适用额外税率\n");
        }

        return sb.toString();
    }
}
