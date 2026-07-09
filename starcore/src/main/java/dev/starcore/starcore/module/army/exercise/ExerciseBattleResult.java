package dev.starcore.starcore.module.army.exercise;

import java.time.Instant;
import java.util.UUID;

/**
 * 演习战斗结果
 * 记录一次演习中的战斗结果
 */
public record ExerciseBattleResult(
    UUID battleId,
    UUID exerciseId,
    UUID attackerNationId,
    String attackerNationName,
    UUID defenderNationId,
    String defenderNationName,
    int attackerLosses,
    int defenderLosses,
    UUID winnerId,
    String winnerName,
    int attackerKills,
    int defenderKills,
    double attackerMoraleChange,
    double defenderMoraleChange,
    Instant timestamp
) {
    /**
     * 创建战斗结果
     */
    public static ExerciseBattleResult create(
        UUID exerciseId,
        UUID attackerNationId,
        String attackerNationName,
        UUID defenderNationId,
        String defenderNationName,
        int attackerLosses,
        int defenderLosses,
        UUID winnerId,
        String winnerName,
        int attackerKills,
        int defenderKills,
        double attackerMoraleChange,
        double defenderMoraleChange
    ) {
        return new ExerciseBattleResult(
            UUID.randomUUID(),
            exerciseId,
            attackerNationId,
            attackerNationName,
            defenderNationId,
            defenderNationName,
            attackerLosses,
            defenderLosses,
            winnerId,
            winnerName,
            attackerKills,
            defenderKills,
            attackerMoraleChange,
            defenderMoraleChange,
            Instant.now()
        );
    }

    /**
     * 是否平局
     */
    public boolean isDraw() {
        return winnerId == null;
    }

    /**
     * 获取攻击方是否获胜
     */
    public boolean attackerWon() {
        return winnerId != null && winnerId.equals(attackerNationId);
    }

    /**
     * 获取防守方是否获胜
     */
    public boolean defenderWon() {
        return winnerId != null && winnerId.equals(defenderNationId);
    }

    /**
     * 检查是否有胜者
     */
    public boolean hasWinner() {
        return winnerId != null;
    }

    /**
     * 格式化战斗报告
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 演习战斗报告 =====\n");
        sb.append("时间: ").append(timestamp).append("\n");
        sb.append("\n");
        sb.append("攻击方: ").append(attackerNationName).append("\n");
        sb.append("  - 伤亡: ").append(attackerLosses).append(" 士兵\n");
        sb.append("  - 击杀: ").append(attackerKills).append("\n");
        sb.append("  - 士气变化: ").append(String.format("%+.1f%%", attackerMoraleChange)).append("\n");
        sb.append("\n");
        sb.append("防守方: ").append(defenderNationName).append("\n");
        sb.append("  - 伤亡: ").append(defenderLosses).append(" 士兵\n");
        sb.append("  - 击杀: ").append(defenderKills).append("\n");
        sb.append("  - 士气变化: ").append(String.format("%+.1f%%", defenderMoraleChange)).append("\n");
        sb.append("\n");
        if (isDraw()) {
            sb.append("结果: 平局\n");
        } else {
            sb.append("胜利者: ").append(winnerName).append("\n");
        }
        return sb.toString();
    }
}