package dev.starcore.starcore.module.military.storage;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.List;
import java.util.Optional;

/**
 * 军事数据存储接口
 */
public interface MilitaryStorage {

    /**
     * 保存战斗记录
     */
    void saveBattleRecord(BattleRecord record);

    /**
     * 获取战斗记录
     */
    Optional<BattleRecord> getBattleRecord(String battleId);

    /**
     * 获取国家的所有战斗记录
     */
    List<BattleRecord> getBattleRecords(NationId nationId);

    /**
     * 删除战斗记录
     */
    void deleteBattleRecord(String battleId);

    /**
     * 初始化存储表
     */
    void initializeTables();

    // ==================== 数据模型 ====================

    record BattleRecord(
        String battleId,
        NationId attackerId,
        NationId defenderId,
        String location,
        String status,
        int attackerCasualties,
        int defenderCasualties,
        long startTime,
        long endTime,
        NationId winner
    ) {}
}
