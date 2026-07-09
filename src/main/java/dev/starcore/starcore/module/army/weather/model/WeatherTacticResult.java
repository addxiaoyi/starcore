package dev.starcore.starcore.module.army.weather.model;

import dev.starcore.starcore.module.weather.model.WeatherType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 天气战术执行结果
 * 记录一次天气战术执行后的详细结果
 */
public final class WeatherTacticResult {

    private final UUID tacticId;
    private final UUID nationId;
    private final UUID armyId;
    private final WeatherTacticType tacticType;
    private final WeatherType weatherDuringTactic;
    private final boolean success;
    private final double finalSuccessRate;
    private final Instant startTime;
    private final Instant endTime;
    private final long durationSeconds;

    // 战斗统计
    private final int enemyCasualties;
    private final int ownCasualties;
    private final double damageDealt;
    private final double damageTaken;
    private final double moraleChange;
    private final double territoryGained;

    // 战术特定效果
    private final Map<String, Double> tacticalBonuses;

    // 结果描述
    private final String resultMessage;

    public WeatherTacticResult(
        WeatherTacticState state,
        WeatherType weatherDuringTactic,
        boolean success,
        int enemyCasualties,
        int ownCasualties,
        double damageDealt,
        double damageTaken,
        double territoryGained
    ) {
        this.tacticId = state.tacticId();
        this.nationId = state.nationId();
        this.armyId = state.armyId();
        this.tacticType = state.tacticType();
        this.weatherDuringTactic = weatherDuringTactic;
        this.success = success;
        this.finalSuccessRate = state.successRate();
        this.startTime = state.startTime();
        this.endTime = state.endTime() != null ? state.endTime() : Instant.now();
        this.durationSeconds = (this.endTime.toEpochMilli() - this.startTime.toEpochMilli()) / 1000;
        this.enemyCasualties = enemyCasualties;
        this.ownCasualties = ownCasualties;
        this.damageDealt = damageDealt;
        this.damageTaken = damageTaken;
        this.territoryGained = territoryGained;
        this.tacticalBonuses = new HashMap<>();

        // 计算士气变化
        this.moraleChange = calculateMoraleChange();

        // 生成结果消息
        this.resultMessage = generateResultMessage();
    }

    private double calculateMoraleChange() {
        double change = 0;

        // 胜利获得士气
        if (success) {
            change += 10;
        } else {
            change -= 15;
        }

        // 伤亡影响
        if (ownCasualties > enemyCasualties) {
            change -= 5;
        } else if (enemyCasualties > ownCasualties * 2) {
            change += 5;
        }

        // 战术加成
        change += switch (tacticType) {
            case AMBUSH -> success ? 15 : -10;
            case DEFENSIVE -> success ? 5 : -5;
            case ATTRITION -> success ? 10 : -10;
            case RETREAT -> success ? 5 : -20;
            case PURSUIT -> success ? 20 : -15;
            case REINFORCE -> success ? 10 : -5;
        };

        // 天气影响
        change += WeatherBattleModifier.getMoraleModifier(weatherDuringTactic);

        return change;
    }

    private String generateResultMessage() {
        StringBuilder sb = new StringBuilder();

        // 战术名称
        sb.append("【").append(tacticType.displayName()).append("】");

        // 结果
        if (success) {
            sb.append("成功! ");
        } else {
            sb.append("失败! ");
        }

        // 天气信息
        sb.append("天气: ").append(weatherDuringTactic.getDisplayName()).append(" ");

        // 伤亡统计
        sb.append(String.format("敌军伤亡: %d, 我军伤亡: %d ", enemyCasualties, ownCasualties));

        // 战果
        if (territoryGained > 0) {
            sb.append(String.format("占领领土: %.1f ", territoryGained));
        }

        // 士气变化
        if (moraleChange > 0) {
            sb.append(String.format("士气 +%.0f", moraleChange));
        } else if (moraleChange < 0) {
            sb.append(String.format("士气 %.0f", moraleChange));
        }

        return sb.toString();
    }

    // Getters
    public UUID tacticId() {
        return tacticId;
    }

    public UUID nationId() {
        return nationId;
    }

    public UUID armyId() {
        return armyId;
    }

    public WeatherTacticType tacticType() {
        return tacticType;
    }

    public WeatherType weatherDuringTactic() {
        return weatherDuringTactic;
    }

    public boolean success() {
        return success;
    }

    public double finalSuccessRate() {
        return finalSuccessRate;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public long durationSeconds() {
        return durationSeconds;
    }

    public int enemyCasualties() {
        return enemyCasualties;
    }

    public int ownCasualties() {
        return ownCasualties;
    }

    public double damageDealt() {
        return damageDealt;
    }

    public double damageTaken() {
        return damageTaken;
    }

    public double moraleChange() {
        return moraleChange;
    }

    public double territoryGained() {
        return territoryGained;
    }

    public Map<String, Double> tacticalBonuses() {
        return Map.copyOf(tacticalBonuses);
    }

    public String resultMessage() {
        return resultMessage;
    }

    /**
     * 获取战斗效率（杀敌/损失比）
     */
    public double getKillRatio() {
        if (ownCasualties == 0) {
            return enemyCasualties > 0 ? Double.MAX_VALUE : 0;
        }
        return (double) enemyCasualties / ownCasualties;
    }

    /**
     * 获取综合评分（用于历史记录和统计）
     */
    public int getOverallScore() {
        int score = 0;

        // 胜利加分
        if (success) {
            score += 100;
        } else {
            score -= 50;
        }

        // 伤亡比加分
        score += Math.min(50, (int) (getKillRatio() * 20));

        // 战术效率
        score += (int) (finalSuccessRate * 30);

        // 领土收益
        score += (int) (territoryGained * 10);

        return score;
    }

    /**
     * 获取格式化报告
     */
    public String getFormattedReport() {
        return String.format("""
            ========== 天气战术报告 ==========
            战术: %s
            天气: %s
            结果: %s (成功率: %.0f%%)
            持续时间: %d 秒

            --- 战斗统计 ---
            敌军伤亡: %d
            我军伤亡: %d
            杀敌/损失比: %.2f

            --- 伤害统计 ---
            造成伤害: %.1f
            承受伤害: %.1f

            --- 效果 ---
            士气变化: %+.0f
            领土变化: %+.1f
            综合评分: %d

            --- 详细描述 ---
            %s
            """,
            tacticType.displayName(),
            weatherDuringTactic.getDisplayName(),
            success ? "成功" : "失败",
            finalSuccessRate * 100,
            durationSeconds,
            enemyCasualties,
            ownCasualties,
            getKillRatio(),
            damageDealt,
            damageTaken,
            moraleChange,
            territoryGained,
            getOverallScore(),
            resultMessage
        );
    }
}
