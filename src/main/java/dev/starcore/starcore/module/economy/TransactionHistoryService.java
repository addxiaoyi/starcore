package dev.starcore.starcore.module.economy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * 交易历史服务接口
 * 用于追踪玩家的所有经济交易活动
 */
public interface TransactionHistoryService {

    /**
     * 获取玩家在指定时间段内的总收入
     *
     * @param playerId 玩家UUID
     * @param period 时间段
     * @return 总收入金额
     */
    BigDecimal getIncome(UUID playerId, Duration period);

    /**
     * 获取玩家在指定时间段内的总支出
     *
     * @param playerId 玩家UUID
     * @param period 时间段
     * @return 总支出金额
     */
    BigDecimal getExpense(UUID playerId, Duration period);

    /**
     * 获取玩家在指定时间段内的净收入
     *
     * @param playerId 玩家UUID
     * @param period 时间段
     * @return 净收入金额
     */
    default BigDecimal getNetIncome(UUID playerId, Duration period) {
        return getIncome(playerId, period).subtract(getExpense(playerId, period));
    }
}
