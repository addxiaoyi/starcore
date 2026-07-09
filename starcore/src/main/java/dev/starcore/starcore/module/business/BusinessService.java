package dev.starcore.starcore.module.business;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 商业服务接口
 * 提供商业活动追踪、交易额计算、营业税收取等功能
 *
 * 集成到税务系统 (TaxationServiceImpl) 提供真实商业数据
 */
public interface BusinessService {

    /**
     * 记录一笔商业交易
     *
     * @param playerId  玩家UUID
     * @param nationId  国家UUID
     * @param type      交易类型
     * @param amount    交易金额
     * @param category  商品类别
     * @return 是否记录成功
     */
    boolean recordTransaction(UUID playerId, NationId nationId, BusinessTransactionType type,
                            BigDecimal amount, BusinessCategory category);

    /**
     * 获取玩家在指定时间段内的商业交易总额
     *
     * @param nationId 国家UUID
     * @param type     交易类型 (可空，获取所有类型)
     * @param days     时间范围(天)
     * @return 交易总额
     */
    BigDecimal getTransactionVolume(NationId nationId, BusinessTransactionType type, int days);

    /**
     * 获取玩家在指定时间段内的交易次数
     *
     * @param nationId 国家UUID
     * @param days     时间范围(天)
     * @return 交易次数
     */
    int getTransactionCount(NationId nationId, int days);

    /**
     * 获取国家活跃商家数量
     *
     * @param nationId 国家UUID
     * @param days     时间范围(天)
     * @return 有交易的玩家数量
     */
    int getActiveBusinessCount(NationId nationId, int days);

    /**
     * 获取国家的商业活动统计
     *
     * @param nationId 国家UUID
     * @param days    时间范围(天)
     * @return 商业统计
     */
    BusinessStatistics getStatistics(NationId nationId, int days);

    /**
     * 获取玩家的交易历史
     *
     * @param playerId 玩家UUID
     * @param limit    返回数量限制
     * @return 交易列表
     */
    List<BusinessTransaction> getPlayerTransactions(UUID playerId, int limit);

    /**
     * 获取国家的交易历史
     *
     * @param nationId 国家UUID
     * @param limit    返回数量限制
     * @return 交易列表
     */
    List<BusinessTransaction> getNationTransactions(NationId nationId, int limit);

    /**
     * 获取玩家在指定时间段内的收入
     * 用于税务系统
     *
     * @param playerId 玩家UUID
     * @param days    时间范围(天)
     * @return 总收入
     */
    BigDecimal getPlayerIncome(UUID playerId, int days);

    /**
     * 获取玩家在指定时间段内的支出
     * 用于税务系统
     *
     * @param playerId 玩家UUID
     * @param days    时间范围(天)
     * @return 总支出
     */
    BigDecimal getPlayerExpense(UUID playerId, int days);

    /**
     * 商业交易类型
     */
    enum BusinessTransactionType {
        SALE("销售"),
        PURCHASE("采购"),
        TRADE("贸易"),
        SERVICE("服务"),
        OTHER("其他");

        private final String displayName;

        BusinessTransactionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 商业类别
     */
    enum BusinessCategory {
        MATERIAL("原材料"),
        FOOD("食品"),
        EQUIPMENT("装备"),
        TOOLS("工具"),
        RESOURCE("资源"),
        LUXURY("奢侈品"),
        SERVICE("服务"),
        OTHER("其他");

        private final String displayName;

        BusinessCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 商业交易记录
     */
    record BusinessTransaction(
        UUID transactionId,
        UUID playerId,
        NationId nationId,
        BusinessTransactionType type,
        BusinessCategory category,
        BigDecimal amount,
        long timestamp
    ) {}

    /**
     * 商业统计
     */
    record BusinessStatistics(
        int transactionCount,
        BigDecimal totalVolume,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        int activeBusinessCount,
        BusinessCategory topCategory
    ) {}
}
