package dev.starcore.starcore.module.army.model;

import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * 战斗结果
 */
public record BattleResult(
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
     * 战斗结果
     */
    public enum BattleOutcome {
        ATTACKER_VICTORY,      // 攻击方胜利
        DEFENDER_VICTORY,      // 防守方胜利
        DRAW,                  // 平局
        BOTH_DESTROYED         // 同归于尽
    }

    /**
     * 创建战斗结果
     */
    public static BattleResult create(
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
            description = "双方全灭";
        } else if (!defenderAlive) {
            outcome = BattleOutcome.ATTACKER_VICTORY;
            description = "攻击方获胜";
        } else if (!attackerAlive) {
            outcome = BattleOutcome.DEFENDER_VICTORY;
            description = "防守方获胜";
        } else if (Math.abs(attackerDamage - defenderDamage) < 10) {
            outcome = BattleOutcome.DRAW;
            description = "平局";
        } else if (attackerDamage > defenderDamage) {
            outcome = BattleOutcome.ATTACKER_VICTORY;
            description = "攻击方占优";
        } else {
            outcome = BattleOutcome.DEFENDER_VICTORY;
            description = "防守方占优";
        }

        return new BattleResult(
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
     * 格式化战斗报告
     */
    public String formatReport() {
        return String.format("""
            战斗结果：%s
            攻击方伤害：%.1f（伤亡 %d）
            防守方伤害：%.1f（伤亡 %d）
            """,
            description,
            attackerDamage, attackerCasualties,
            defenderDamage, defenderCasualties
        );
    }

    /**
     * 发送战斗报告给玩家
     * @param attackerMessage 发送给攻击方的消息（可为null）
     * @param defenderMessage 发送给防守方的消息（可为null）
     */
    public void sendReport(Component attackerMessage, Component defenderMessage) {
        if (attackerMessage != null) {
            // attackerMessage 应该包含战斗结果详情
        }
        if (defenderMessage != null) {
            // defenderMessage 应该包含战斗结果详情
        }
    }
}
