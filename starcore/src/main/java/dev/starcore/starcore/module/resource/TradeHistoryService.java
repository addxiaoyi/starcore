package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.TradeRecord;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 交易历史记录服务
 * 管理交易记录的持久化和查询
 */
public interface TradeHistoryService {
    /**
     * 保存交易记录
     */
    void saveRecord(TradeRecord record);

    /**
     * 批量保存交易记录
     */
    void saveRecords(Collection<TradeRecord> records);

    /**
     * 获取交易记录
     */
    Optional<TradeRecord> getRecord(UUID recordId);

    /**
     * 获取玩家的所有交易记录
     */
    List<TradeRecord> getPlayerRecords(UUID playerId);

    /**
     * 获取玩家的交易记录（分页）
     */
    List<TradeRecord> getPlayerRecords(UUID playerId, int offset, int limit);

    /**
     * 获取国家的所有交易记录
     */
    List<TradeRecord> getNationRecords(NationId nationId);

    /**
     * 获取国家的交易记录（分页）
     */
    List<TradeRecord> getNationRecords(NationId nationId, int offset, int limit);

    /**
     * 获取资源的交易记录
     */
    List<TradeRecord> getResourceRecords(String resourceId);

    /**
     * 获取资源的交易记录（分页）
     */
    List<TradeRecord> getResourceRecords(String resourceId, int offset, int limit);

    /**
     * 获取时间范围内的交易记录
     */
    List<TradeRecord> getRecordsByTimeRange(Instant startTime, Instant endTime);

    /**
     * 获取玩家的交易记录（按时间范围）
     */
    List<TradeRecord> getPlayerRecordsByTimeRange(UUID playerId, Instant startTime, Instant endTime);

    /**
     * 获取玩家的交易总额
     */
    double getPlayerTotalTradeValue(UUID playerId);

    /**
     * 获取玩家的交易次数
     */
    int getPlayerTradeCount(UUID playerId);

    /**
     * 获取国家间交易记录
     */
    List<TradeRecord> getNationToNationRecords(NationId nation1, NationId nation2);

    /**
     * 删除旧记录（用于维护）
     */
    int deleteOldRecords(Instant before);

    /**
     * 获取统计数据
     */
    TradeStatistics getStatistics(Instant startTime, Instant endTime);

    /**
     * 获取玩家统计数据
     */
    TradeStatistics getPlayerStatistics(UUID playerId, Instant startTime, Instant endTime);

    /**
     * 交易统计数据
     */
    record TradeStatistics(
        int totalTrades,
        long totalVolume,
        double totalValue,
        double totalTax,
        double averagePrice,
        double highestPrice,
        double lowestPrice,
        Instant oldestTrade,
        Instant newestTrade
    ) {}
}
