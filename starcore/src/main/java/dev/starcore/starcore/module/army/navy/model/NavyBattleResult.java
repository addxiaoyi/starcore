package dev.starcore.starcore.module.army.navy.model;

import java.util.UUID;

/**
 * 海战结果
 */
public record NavyBattleResult(
    UUID attackerId,
    UUID defenderId,
    double attackerDamage,
    double defenderDamage,
    int attackerCasualties,
    int defenderCasualties,
    BattleOutcome outcome,
    String description
) {

    /**
     * 海战结果
     */
    public enum BattleOutcome {
        ATTACKER_VICTORY,      // 攻击方胜利
        DEFENDER_VICTORY,      // 防守方胜利
        DRAW,                  // 平局
        BOTH_DESTROYED,        // 同归于尽
        ATTACKER_REPELLED      // 攻击方被击退
    }

    /**
     * 创建海战结果
     */
    public static NavyBattleResult create(
        UUID attackerId,
        UUID defenderId,
        double attackerDamage,
        double defenderDamage,
        int attackerCasualties,
        int defenderCasualties,
        boolean attackerAlive,
        boolean defenderAlive
    ) {
        BattleOutcome outcome;
        String description;

        if (!attackerAlive && !defenderAlive) {
            outcome = BattleOutcome.BOTH_DESTROYED;
            description = "双方舰队全灭";
        } else if (!defenderAlive) {
            outcome = BattleOutcome.ATTACKER_VICTORY;
            description = "攻击方舰队获胜";
        } else if (!attackerAlive) {
            outcome = BattleOutcome.DEFENDER_VICTORY;
            description = "防守方舰队获胜";
        } else if (Math.abs(attackerDamage - defenderDamage) < 10) {
            outcome = BattleOutcome.DRAW;
            description = "海战平局";
        } else if (attackerDamage > defenderDamage * 1.5) {
            outcome = BattleOutcome.ATTACKER_VICTORY;
            description = "攻击方舰队大胜";
        } else if (defenderDamage > attackerDamage * 1.5) {
            outcome = BattleOutcome.DEFENDER_VICTORY;
            description = "防守方舰队大胜";
        } else if (attackerDamage > defenderDamage) {
            outcome = BattleOutcome.ATTACKER_VICTORY;
            description = "攻击方舰队占优";
        } else {
            outcome = BattleOutcome.ATTACKER_REPELLED;
            description = "攻击方被击退";
        }

        return new NavyBattleResult(
            attackerId,
            defenderId,
            attackerDamage,
            defenderDamage,
            attackerCasualties,
            defenderCasualties,
            outcome,
            description
        );
    }

    /**
     * 是否有明确胜者
     */
    public boolean hasWinner() {
        return outcome == BattleOutcome.ATTACKER_VICTORY || outcome == BattleOutcome.DEFENDER_VICTORY;
    }

    /**
     * 获胜方ID
     */
    public UUID winnerId() {
        return switch (outcome) {
            case ATTACKER_VICTORY -> attackerId;
            case DEFENDER_VICTORY -> defenderId;
            default -> null;
        };
    }

    /**
     * 失败方ID
     */
    public UUID loserId() {
        return switch (outcome) {
            case ATTACKER_VICTORY -> defenderId;
            case DEFENDER_VICTORY -> attackerId;
            default -> null;
        };
    }

    /**
     * 格式化海战报告
     */
    public String formatReport() {
        return String.format("""
            海战结果：%s
            攻击舰队伤害：%.1f（损失 %d 艘舰船）
            防守舰队伤害：%.1f（损失 %d 艘舰船）
            """,
            description,
            attackerDamage, attackerCasualties,
            defenderDamage, defenderCasualties
        );
    }
}
