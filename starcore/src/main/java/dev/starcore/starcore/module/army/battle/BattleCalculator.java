package dev.starcore.starcore.module.army.battle;

import dev.starcore.starcore.module.army.model.ArmyType;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.army.model.BattleResult;

/**
 * 战斗计算器
 * 负责计算两支军队之间的战斗结果
 */
public final class BattleCalculator {
    private static final double COUNTER_BONUS = 1.3;  // 克制加成 30%
    private static final double MIN_DAMAGE = 1.0;     // 最小伤害

    /**
     * 计算一次战斗
     * 双方同时造成伤害
     */
    public BattleResult calculateBattle(ArmyUnit attacker, ArmyUnit defender) {
        if (!attacker.canFight()) {
            throw new IllegalStateException("Attacker cannot fight");
        }
        if (!defender.isAlive()) {
            throw new IllegalStateException("Defender is not alive");
        }

        // 记录战前状态
        int attackerSoldiersBefore = attacker.soldiers();
        int defenderSoldiersBefore = defender.soldiers();

        // 计算伤害
        double attackerDamage = calculateDamage(attacker, defender);
        double defenderDamage = calculateDamage(defender, attacker);

        // 应用伤害
        defender.takeDamage(attackerDamage);
        attacker.takeDamage(defenderDamage);

        // 计算伤亡
        int attackerCasualties = attackerSoldiersBefore - attacker.soldiers();
        int defenderCasualties = defenderSoldiersBefore - defender.soldiers();

        // 士气影响
        updateMorale(attacker, defenderCasualties, attackerCasualties);
        updateMorale(defender, attackerCasualties, defenderCasualties);

        // 生成战斗结果
        return BattleResult.create(
            attacker.id(),
            defender.id(),
            attackerDamage,
            defenderDamage,
            attackerCasualties,
            defenderCasualties,
            attacker.isAlive(),
            defender.isAlive()
        );
    }

    /**
     * 计算单方伤害
     */
    private double calculateDamage(ArmyUnit attacker, ArmyUnit defender) {
        // 基础伤害 = 攻击力 - 防御力 * 0.5
        double baseDamage = attacker.effectiveAttack() - defender.effectiveDefense() * 0.5;

        // 克制加成
        if (attacker.type().counters(defender.type())) {
            baseDamage *= COUNTER_BONUS;
        }

        // 确保最小伤害
        return Math.max(baseDamage, MIN_DAMAGE);
    }

    /**
     * 更新士气
     */
    private void updateMorale(ArmyUnit unit, int enemyCasualties, int ownCasualties) {
        double moraleDelta = 0;

        // 杀敌提升士气
        if (enemyCasualties > 0) {
            moraleDelta += Math.min(10, enemyCasualties * 0.1);
        }

        // 损失降低士气
        if (ownCasualties > 0) {
            moraleDelta -= Math.min(20, ownCasualties * 0.2);
        }

        unit.changeMorale(moraleDelta);
    }

    /**
     * 预测战斗结果（不实际执行）
     */
    public BattlePrediction predictBattle(ArmyUnit attacker, ArmyUnit defender) {
        double attackerDamage = calculateDamage(attacker, defender);
        double defenderDamage = calculateDamage(defender, attacker);

        // 估算战斗轮数
        int roundsToDefeat = defender.soldiers() > 0 ?
            (int) Math.ceil(defender.soldiers() * defender.health() / attackerDamage) : 0;
        int roundsToDefeatAttacker = attacker.soldiers() > 0 ?
            (int) Math.ceil(attacker.soldiers() * attacker.health() / defenderDamage) : 0;

        double attackerWinChance = calculateWinChance(attacker, defender);

        return new BattlePrediction(
            attackerDamage,
            defenderDamage,
            roundsToDefeat,
            roundsToDefeatAttacker,
            attackerWinChance
        );
    }

    /**
     * 计算胜率
     */
    private double calculateWinChance(ArmyUnit attacker, ArmyUnit defender) {
        double attackerStrength = attacker.combatRating();
        double defenderStrength = defender.combatRating();

        if (attackerStrength + defenderStrength == 0) {
            return 0.5;
        }

        return attackerStrength / (attackerStrength + defenderStrength);
    }

    /**
     * 战斗预测结果
     */
    public record BattlePrediction(
        double estimatedAttackerDamage,
        double estimatedDefenderDamage,
        int roundsToDefeatDefender,
        int roundsToDefeatAttacker,
        double attackerWinChance
    ) {
        public String formatPrediction() {
            return String.format("""
                预计攻击方伤害：%.1f
                预计防守方伤害：%.1f
                预计击败防守方：%d 回合
                预计击败攻击方：%d 回合
                攻击方胜率：%.1f%%
                """,
                estimatedAttackerDamage,
                estimatedDefenderDamage,
                roundsToDefeatDefender,
                roundsToDefeatAttacker,
                attackerWinChance * 100
            );
        }
    }
}
