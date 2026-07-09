package dev.starcore.starcore.module.army.weather;

import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsBoost;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsEffect;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.model.WeatherType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 天气战术服务接口
 * 提供军队在特定天气条件下的战术加成和战斗效果
 */
public interface WeatherTacticsService {

    // ==================== 核心战术方法 ====================

    /**
     * 获取军队在当前天气下的战术效果
     *
     * @param armyId 军队ID
     * @return 战术效果，如果没有则返回默认效果
     */
    WeatherTacticsEffect getTacticsEffect(UUID armyId);

    /**
     * 获取军队在指定天气下的战术效果
     *
     * @param armyId    军队ID
     * @param weather   天气类型
     * @return 战术效果
     */
    WeatherTacticsEffect getTacticsEffect(UUID armyId, WeatherType weather);

    /**
     * 获取兵种对特定天气的适应性等级
     *
     * @param weatherType 天气类型
     * @param attackType  攻击类型 (infantry/cavalry/archer/siege/defensive)
     * @return 适应性等级 (0.0 - 2.0)
     */
    double getUnitAdaptation(WeatherType weatherType, String attackType);

    /**
     * 计算天气对战斗结果的修正
     *
     * @param attacker 攻击方军队
     * @param defender 防守方军队
     * @param weather  当前天气
     * @return 天气修正系数
     */
    WeatherBattleModifier calculateBattleModifier(ArmyUnit attacker, ArmyUnit defender, WeatherType weather);

    /**
     * 检查国家是否解锁了天气战术科技
     *
     * @param nationId 国家ID
     * @param tacticsType 战术类型
     * @return 是否解锁
     */
    boolean hasUnlockedTactics(NationId nationId, String tacticsType);

    /**
     * 解锁国家的天气战术
     *
     * @param nationId     国家ID
     * @param tacticsType  战术类型
     * @param level        解锁等级
     * @return 是否成功解锁
     */
    boolean unlockTactics(NationId nationId, String tacticsType, int level);

    // ==================== 战术加成管理 ====================

    /**
     * 获取国家当前所有的天气战术加成
     *
     * @param nationId 国家ID
     * @return 战术加成映射
     */
    Map<WeatherType, WeatherTacticsBoost> getNationTacticsBoosts(NationId nationId);

    /**
     * 设置国家的天气战术加成
     *
     * @param nationId 国家ID
     * @param weather  天气类型
     * @param boost    战术加成
     * @return 是否成功设置
     */
    boolean setTacticsBoost(NationId nationId, WeatherType weather, WeatherTacticsBoost boost);

    /**
     * 获取天气战术的当前等级
     *
     * @param nationId    国家ID
     * @param tacticsType 战术类型
     * @return 当前等级，0 表示未解锁
     */
    int getTacticsLevel(NationId nationId, String tacticsType);

    /**
     * 升级天气战术
     *
     * @param nationId    国家ID
     * @param tacticsType 战术类型
     * @return 升级后的等级，失败返回 -1
     */
    int upgradeTactics(NationId nationId, String tacticsType);

    // ==================== 战术效果查询 ====================

    /**
     * 获取特定天气的所有战术信息
     *
     * @param weather 天气类型
     * @return 战术信息
     */
    WeatherTacticsInfo getTacticsInfo(WeatherType weather);

    /**
     * 获取所有可用的战术类型
     *
     * @return 战术类型列表
     */
    String[] getAvailableTacticsTypes();

    /**
     * 获取战术升级所需成本
     *
     * @param tacticsType 战术类型
     * @param currentLevel 当前等级
     * @return 升级成本
     */
    double getUpgradeCost(String tacticsType, int currentLevel);

    // ==================== 战术事件 ====================

    /**
     * 触发天气战术事件
     *
     * @param nationId 国家ID
     * @param weather  天气类型
     * @param effect   战术效果
     */
    void triggerTacticsEvent(NationId nationId, WeatherType weather, WeatherTacticsEffect effect);

    /**
     * 获取服务摘要
     *
     * @return 摘要信息
     */
    String summary();

    // ==================== 内部数据访问 ====================

    /**
     * 获取国家解锁的所有战术
     *
     * @param nationId 国家ID
     * @return 战术类型到等级的映射
     */
    Map<String, Integer> getUnlockedTactics(NationId nationId);

    /**
     * 检查是否需要保存状态
     *
     * @return 是否脏
     */
    boolean isDirty();

    /**
     * 标记状态已保存
     */
    void markClean();

    // ==================== 内部类 ====================

    /**
     * 天气战斗修正器
     */
    record WeatherBattleModifier(
        double attackerBonus,
        double defenderBonus,
        double moraleModifier,
        String description
    ) {
        public double attackerWinChance() {
            double total = attackerBonus + defenderBonus;
            if (total == 0) return 0.5;
            return attackerBonus / total;
        }

        public String formatDescription() {
            return String.format("天气修正: %s | 攻击方 %.1f%% | 防守方 %.1f%%",
                description, attackerBonus * 100, defenderBonus * 100);
        }
    }

    /**
     * 天气战术信息
     */
    record WeatherTacticsInfo(
        WeatherType weather,
        String description,
        double baseAttackModifier,
        double baseDefenseModifier,
        double movementModifier,
        String[] effectiveUnits,
        String[] weakUnits
    ) {
    }
}