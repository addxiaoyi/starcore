package dev.starcore.starcore.module.military;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.List;
import java.util.Optional;

/**
 * 军事指挥服务接口
 * 提供统一的军事指挥功能
 */
public interface MilitaryService {

    /**
     * 获取国家战况概览
     */
    MilitaryOverview getOverview(NationId nationId);

    /**
     * 获取所有正在进行的战斗
     */
    List<BattleSummary> getActiveBattles(NationId nationId);

    /**
     * 获取军事联盟列表
     */
    List<AllianceSummary> getAlliances(NationId nationId);

    /**
     * 获取防御态势
     */
    DefenseStatus getDefenseStatus(NationId nationId);

    /**
     * 获取攻击态势
     */
    OffensiveStatus getOffensiveStatus(NationId nationId);

    /**
     * 检查是否可以发起攻击
     */
    boolean canAttack(NationId nationId);

    /**
     * 获取军事建议
     */
    List<MilitarySuggestion> getSuggestions(NationId nationId);

    // ==================== 内部类 ====================

    /**
     * 军事概览
     */
    record MilitaryOverview(
        NationId nationId,
        int totalArmies,
        int activeBattles,
        int allies,
        int enemies,
        int provinces,
        double militaryPower,
        long lastUpdated
    ) {}

    /**
     * 战斗摘要
     */
    record BattleSummary(
        String battleId,
        String location,
        String enemyNation,
        String status,
        int friendlyCasualties,
        int enemyCasualties,
        long startTime,
        double progress
    ) {}

    /**
     * 联盟摘要
     */
    record AllianceSummary(
        NationId allyId,
        String allyName,
        String allianceType,
        long since
    ) {}

    /**
     * 防御状态
     */
    record DefenseStatus(
        int totalDefense,
        int activeDefense,
        int underAttack,
        List<String> weakPoints,
        double defenseMorale
    ) {}

    /**
     * 进攻状态
     */
    record OffensiveStatus(
        int totalOffensive,
        int activeOffensive,
        int victories,
        int defeats,
        double winRate
    ) {}

    /**
     * 军事建议
     */
    record MilitarySuggestion(
        String priority,
        String title,
        String description,
        String action
    ) {}
}
