package dev.starcore.starcore.module.army.weather.event;

import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.weather.WeatherTacticsService;
import dev.starcore.starcore.module.weather.model.WeatherType;

import java.util.UUID;

/**
 * 天气战斗优势事件
 * 在战斗计算前触发，用于计算天气对战斗的影响
 */
public record WeatherBattleAdvantageEvent(
    UUID attackerId,
    UUID defenderId,
    WeatherType weather,
    WeatherBattleModifier modifier
) {

    // ==================== 访问方法 ====================

    /**
     * 获取攻击方ID
     */
    public UUID attackerId() {
        return attackerId;
    }

    /**
     * 获取防守方ID
     */
    public UUID defenderId() {
        return defenderId;
    }

    /**
     * 获取天气类型
     */
    public WeatherType weather() {
        return weather;
    }

    /**
     * 获取天气战斗修正
     */
    public WeatherBattleModifier modifier() {
        return modifier;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取攻击方天气优势
     */
    public double getAttackerAdvantage() {
        return modifier.attackerBonus();
    }

    /**
     * 获取防守方天气优势
     */
    public double getDefenderAdvantage() {
        return modifier.defenderBonus();
    }

    /**
     * 获取攻击方胜率
     */
    public double getAttackerWinChance() {
        return modifier.attackerWinChance();
    }

    /**
     * 检查攻击方是否有优势
     */
    public boolean attackerHasAdvantage() {
        return modifier.attackerBonus() > modifier.defenderBonus();
    }

    /**
     * 检查防守方是否有优势
     */
    public boolean defenderHasAdvantage() {
        return modifier.defenderBonus() > modifier.attackerBonus();
    }

    /**
     * 获取天气影响描述
     */
    public String getWeatherImpactDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("天气: ").append(weather.getDisplayName()).append("\n");

        double diff = modifier.attackerBonus() - modifier.defenderBonus();
        if (diff > 0.2) {
            sb.append("攻击方获得显著天气优势！\n");
        } else if (diff > 0.05) {
            sb.append("攻击方获得轻微天气优势。\n");
        } else if (diff < -0.2) {
            sb.append("防守方获得显著天气优势！\n");
        } else if (diff < -0.05) {
            sb.append("防守方获得轻微天气优势。\n");
        } else {
            sb.append("双方天气条件相当。\n");
        }

        sb.append("攻击方加成: ").append(formatPercent(modifier.attackerBonus())).append("\n");
        sb.append("防守方加成: ").append(formatPercent(modifier.defenderBonus())).append("\n");
        sb.append("攻击方预计胜率: ").append(formatPercent(getAttackerWinChance())).append("\n");

        return sb.toString();
    }

    /**
     * 获取详细战斗报告
     */
    public String getDetailedReport() {
        return String.format("""
            === 天气战斗优势报告 ===

            天气条件: %s
            攻击方天气加成: %s
            防守方天气加成: %s
            攻击方预计胜率: %s

            修正详情:
            %s
            """,
            weather.getDisplayName(),
            formatPercent(modifier.attackerBonus()),
            formatPercent(modifier.defenderBonus()),
            formatPercent(getAttackerWinChance()),
            modifier.formatDescription()
        );
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建无天气影响的战斗优势事件
     */
    public static WeatherBattleAdvantageEvent noWeatherEffect(UUID attackerId, UUID defenderId) {
        WeatherBattleModifier modifier = new WeatherBattleModifier(
            1.0, 1.0, 1.0, "无天气影响"
        );
        return new WeatherBattleAdvantageEvent(
            attackerId,
            defenderId,
            dev.starcore.starcore.module.weather.model.WeatherType.CLEAR,
            modifier
        );
    }

    // ==================== 辅助方法 ====================

    private static String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    @Override
    public String toString() {
        return String.format("WeatherBattleAdvantageEvent{attacker=%s, defender=%s, weather=%s, advantage=%.2f}",
            attackerId.toString().substring(0, 8),
            defenderId.toString().substring(0, 8),
            weather.getDisplayName(),
            modifier.attackerBonus() - modifier.defenderBonus());
    }

    // ==================== 内部类 ====================

    /**
     * 天气战斗修正记录
     */
    public record WeatherBattleModifier(
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
}