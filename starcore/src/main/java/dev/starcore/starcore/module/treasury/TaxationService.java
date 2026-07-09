package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 税收服务接口
 * 管理多种税种的配置和征收
 */
public interface TaxationService {

    // ==================== 税种类型 ====================

    enum TaxType {
        /** 土地税 - 按领土面积征收 */
        LAND_TAX("land-tax", "土地税", "按领土面积征收的固定税"),
        /** 商业税 - 按商业活动征收 */
        BUSINESS_TAX("business-tax", "商业税", "按商业活动或交易征收"),
        /** 所得税 - 按成员收入征收 */
        INCOME_TAX("income-tax", "所得税", "按成员个人收入征收"),
        /** 关税 - 按对外贸易征收 */
        TARIFF("tariff", "关税", "按对外贸易征收");

        private final String configKey;
        private final String displayName;
        private final String description;

        TaxType(String configKey, String displayName, String description) {
            this.configKey = configKey;
            this.displayName = displayName;
            this.description = description;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    // ==================== 税种配置 ====================

    /**
     * 税种配置记录
     */
    record TaxConfig(
        TaxType type,
        boolean enabled,
        BigDecimal fixedAmount,
        BigDecimal percentRate,
        BigDecimal minimumBalance,
        int collectionIntervalMinutes,
        boolean autoCollect
    ) {
        public static TaxConfig disabled(TaxType type) {
            return new TaxConfig(type, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1440, false);
        }
    }

    /**
     * 获取国家所有税种配置
     */
    Map<TaxType, TaxConfig> getTaxConfigs(NationId nationId);

    /**
     * 获取特定税种配置
     */
    TaxConfig getTaxConfig(NationId nationId, TaxType type);

    /**
     * 设置税种配置
     */
    void setTaxConfig(NationId nationId, TaxType type, TaxConfig config);

    // ==================== 税收征收 ====================

    /**
     * 征收指定税种
     * @return 征收金额
     */
    BigDecimal collectTax(NationId nationId, TaxType type, TaxContext context);

    /**
     * 征收所有启用税种
     * @return 各税种征收金额汇总
     */
    Map<TaxType, BigDecimal> collectAllTaxes(NationId nationId, TaxContext context);

    // ==================== 税种上下文 ====================

    /**
     * 税收征收上下文
     */
    record TaxContext(
        int claimCount,
        int memberCount,
        int businessCount,
        BigDecimal totalTradeValue,
        List<PlayerIncome> playerIncomes
    ) {
        public static TaxContext empty() {
            return new TaxContext(0, 0, 0, BigDecimal.ZERO, List.of());
        }
    }

    /**
     * 玩家收入记录
     */
    record PlayerIncome(
        UUID playerId,
        BigDecimal income,
        BigDecimal balance
    ) {}

    // ==================== 税收入账记录 ====================

    /**
     * 税收入账记录
     */
    record TaxRevenue(
        NationId nationId,
        TaxType type,
        BigDecimal amount,
        Instant collectedAt,
        TaxContext context
    ) {}

    /**
     * 获取国家税收历史
     */
    List<TaxRevenue> getTaxHistory(NationId nationId, TaxType type, int limit);
}
