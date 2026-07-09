package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 交易税收服务
 * 计算和管理交易相关的税收
 */
public interface TradeTaxService {
    /**
     * 税种类型
     */
    enum TaxType {
        /** 交易手续费 - 每次交易收取 */
        TRANSACTION_FEE("transaction-fee", "交易手续费"),
        /** 关税 - 跨国家交易收取 */
        TARIFF("tariff", "关税"),
        /** 奢侈品税 - 高价值商品交易 */
        LUXURY_TAX("luxury-tax", "奢侈品税"),
        /** 增值税 - 增殖税 */
        VAT("vat", "增值税"),
        /** 资本利得税 - 投机交易 */
        CAPITAL_GAINS("capital-gains", "资本利得税");

        private final String configKey;
        private final String displayName;

        TaxType(String configKey, String displayName) {
            this.configKey = configKey;
            this.displayName = displayName;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 税率配置
     */
    record TaxRateConfig(
        TaxType type,
        BigDecimal rate,           // 税率百分比 (0.0 - 100.0)
        BigDecimal fixedAmount,    // 固定金额
        BigDecimal minAmount,      // 最低税额
        BigDecimal maxAmount,      // 最高税额
        boolean enabled
    ) {
        public static TaxRateConfig disabled(TaxType type) {
            return new TaxRateConfig(type, BigDecimal.ZERO, BigDecimal.ZERO,
                                   BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), false);
        }

        public static TaxRateConfig defaultRate(TaxType type, double rate) {
            return new TaxRateConfig(type, BigDecimal.valueOf(rate), BigDecimal.ZERO,
                                   BigDecimal.ZERO, BigDecimal.valueOf(Double.MAX_VALUE), true);
        }
    }

    /**
     * 计算税额
     * @param resourceId 资源ID
     * @param amount 交易金额
     * @param taxType 税种类型
     * @return 税额
     */
    double calculateTax(String resourceId, double amount, TaxType taxType);

    /**
     * 使用 BigDecimal 计算税额
     * @param resourceId 资源ID
     * @param amount 交易金额
     * @param taxType 税种类型
     * @return 税额
     */
    BigDecimal calculateTaxBD(String resourceId, BigDecimal amount, TaxType taxType);

    /**
     * 计算总税额（所有适用的税种）
     * @param resourceId 资源ID
     * @param amount 交易金额
     * @return 总税额
     */
    double calculateTotalTax(String resourceId, double amount);

    /**
     * 获取税额明细
     * @param resourceId 资源ID
     * @param amount 交易金额
     * @return 各税种明细
     */
    Map<TaxType, Double> getTaxBreakdown(String resourceId, double amount);

    /**
     * 获取国家的税率配置
     */
    Map<TaxType, TaxRateConfig> getNationTaxRates(NationId nationId);

    /**
     * 设置国家的税率配置
     */
    void setNationTaxRate(NationId nationId, TaxType type, TaxRateConfig config);

    /**
     * 重置国家税率到默认值
     */
    void resetNationTaxRates(NationId nationId);

    /**
     * 征收税收到国库
     */
    void collectTax(NationId nationId, double amount, TaxType type);

    /**
     * 获取国家的税收历史
     */
    java.util.List<TaxCollection> getTaxHistory(NationId nationId, int limit);

    /**
     * 获取国家的总税收
     */
    double getTotalTaxCollected(NationId nationId);

    /**
     * 获取特定税种的总税收
     */
    double getTotalTaxCollected(NationId nationId, TaxType type);

    /**
     * 是否为奢侈品（适用奢侈品税）
     */
    boolean isLuxuryGood(String resourceId);

    /**
     * 获取奢侈品种类列表
     */
    java.util.Set<String> getLuxuryGoods();

    /**
     * 添加奢侈品类别
     */
    void addLuxuryGood(String resourceId);

    /**
     * 移除奢侈品类别
     */
    void removeLuxuryGood(String resourceId);

    /**
     * 税收征收记录
     */
    record TaxCollection(
        NationId nationId,
        TaxType type,
        double amount,
        double transactionAmount,
        String resourceId,
        java.time.Instant collectedAt
    ) {}
}
