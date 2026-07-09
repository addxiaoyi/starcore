package dev.starcore.starcore.module.army.navy;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.module.army.navy.model.NavyBattleResult;
import dev.starcore.starcore.module.army.navy.model.NavyUnit;

/**
 * 海军战斗计算器
 * 负责计算海战结果
 */
public final class NavyBattleCalculator {

    /**
     * 战斗预测
     */
    public record BattlePrediction(
        double attackerExpectedDamage,
        double defenderExpectedDamage,
        double attackerWinChance,
        double defenderWinChance,
        double drawChance,
        int estimatedAttackerCasualties,
        int estimatedDefenderCasualties
    ) {
        /**
         * 格式化预测报告
         */
        public String formatPrediction() {
            return String.format("""
                预测结果：
                攻击方预计伤害：%.1f（预计损失 %d 艘舰船）
                防守方预计伤害：%.1f（预计损失 %d 艘舰船）
                攻击方胜率：%.1f%%
                防守方胜率：%.1f%%
                平局概率：%.1f%%
                """,
                attackerExpectedDamage, estimatedAttackerCasualties,
                defenderExpectedDamage, estimatedDefenderCasualties,
                attackerWinChance * 100,
                defenderWinChance * 100,
                drawChance * 100
            );
        }
    }

    /**
     * 计算海战结果
     */
    public NavyBattleResult calculateBattle(NavyUnit attacker, NavyUnit defender) {
        // 计算攻击和防御
        double attackerPower = attacker.effectiveAttack();
        double defenderPower = defender.effectiveDefense();

        // 计算射程修正
        double range = attacker.location().distance(defender.location());
        double rangePenalty = Math.max(0.3, 1.0 - (range / attacker.type().attackRange()) * 0.5);
        attackerPower *= rangePenalty;

        // 考虑兵种克制
        double counterBonus = calculateCounterBonus(attacker.type(), defender.type());
        attackerPower *= counterBonus;

        // 随机因素
        double randomFactor = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        attackerPower *= randomFactor;

        // 计算伤害
        double attackerDamage = attackerPower * 1.5;
        double defenderDamage = defenderPower * 0.8 * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4);

        // 应用伤害
        defender.takeDamage(defenderDamage);
        attacker.takeDamage(attackerDamage * 0.6);

        // 计算伤亡
        int attackerCasualties = calculateCasualties(attacker, attackerDamage * 0.6);
        int defenderCasualties = calculateCasualties(defender, defenderDamage);

        // 判断结果
        boolean attackerAlive = attacker.isAlive();
        boolean defenderAlive = defender.isAlive();

        return NavyBattleResult.create(
            attacker.id(),
            defender.id(),
            attackerDamage,
            defenderDamage,
            attackerCasualties,
            defenderCasualties,
            attackerAlive,
            defenderAlive
        );
    }

    /**
     * 预测战斗结果
     */
    public BattlePrediction predictBattle(NavyUnit attacker, NavyUnit defender) {
        // 基础战力
        double attackerPower = attacker.effectiveAttack();
        double defenderPower = defender.effectiveDefense();

        // 射程修正（假设中等距离）
        double rangePenalty = 0.7;
        attackerPower *= rangePenalty;

        // 兵种克制
        double counterBonus = calculateCounterBonus(attacker.type(), defender.type());
        attackerPower *= counterBonus;

        // 计算预期伤害
        double attackerExpectedDamage = defenderPower * 0.8 * 1.5;
        double defenderExpectedDamage = attackerPower * 0.6;

        // 计算伤亡
        int estimatedAttackerCasualties = calculateCasualties(attacker, defenderExpectedDamage);
        int estimatedDefenderCasualties = calculateCasualties(defender, attackerExpectedDamage);

        // 计算胜率
        double powerRatio = attackerPower / Math.max(defenderPower, 1);
        double totalPower = attackerPower + defenderPower;

        double attackerWinChance;
        double defenderWinChance;
        double drawChance;

        if (totalPower > 0) {
            attackerWinChance = Math.min(0.95, Math.max(0.05, powerRatio * 0.4 + 0.3));
            defenderWinChance = Math.min(0.95, Math.max(0.05, (1.0 / powerRatio) * 0.4 + 0.3));
            drawChance = 1.0 - attackerWinChance - defenderWinChance;
        } else {
            attackerWinChance = 0.33;
            defenderWinChance = 0.33;
            drawChance = 0.34;
        }

        return new BattlePrediction(
            attackerExpectedDamage,
            defenderExpectedDamage,
            attackerWinChance,
            defenderWinChance,
            Math.max(0, drawChance),
            estimatedAttackerCasualties,
            estimatedDefenderCasualties
        );
    }

    /**
     * 计算兵种克制加成
     */
    private double calculateCounterBonus(
        dev.starcore.starcore.module.army.navy.model.NavyType attacker,
        dev.starcore.starcore.module.army.navy.model.NavyType defender
    ) {
        // 护卫舰克制运输船
        if (attacker == dev.starcore.starcore.module.army.navy.model.NavyType.FRIGATE
            && defender == dev.starcore.starcore.module.army.navy.model.NavyType.TRANSPORT) {
            return 1.5;
        }
        // 炮艇克制桨帆战船
        if (attacker == dev.starcore.starcore.module.army.navy.model.NavyType.GUNBOAT
            && defender == dev.starcore.starcore.module.army.navy.model.NavyType.GALLEY) {
            return 1.3;
        }
        // 战列舰克制巡洋舰
        if (attacker == dev.starcore.starcore.module.army.navy.model.NavyType.BATTLESHIP
            && defender == dev.starcore.starcore.module.army.navy.model.NavyType.CRUISER) {
            return 1.2;
        }
        // 巡洋舰克制护卫舰
        if (attacker == dev.starcore.starcore.module.army.navy.model.NavyType.CRUISER
            && defender == dev.starcore.starcore.module.army.navy.model.NavyType.FRIGATE) {
            return 1.2;
        }
        return 1.0;
    }

    /**
     * 计算伤亡数量
     */
    private int calculateCasualties(NavyUnit navy, double damage) {
        if (damage <= 0) {
            return 0;
        }
        double avgDamagePerShip = navy.type().baseHealth();
        return Math.min(navy.ships(), (int) (damage / avgDamagePerShip));
    }
}
