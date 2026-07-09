package dev.starcore.starcore.module.army.exercise.model;

import java.util.List;

/**
 * 演习报告生成器
 * 生成格式化的演习结果报告
 */
public final class ExerciseReportGenerator {

    private ExerciseReportGenerator() {
        // Utility class
    }

    /**
     * 生成格式化报告
     */
    public static String generateReport(
        String name,
        String type,
        int durationMinutes,
        int totalParticipants,
        int totalSoldiers,
        int totalBattles,
        String endReason,
        String winnerName,
        List<dev.starcore.starcore.module.army.exercise.model.ExerciseReport.ParticipantData> participants
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("           军事演习报告\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append("【基本信息】\n");
        sb.append("名称: ").append(name).append("\n");
        sb.append("类型: ").append(type).append("\n");
        sb.append("持续时间: ").append(durationMinutes).append(" 分钟\n");
        sb.append("参与国家: ").append(totalParticipants).append("\n");
        sb.append("总兵力: ").append(totalSoldiers).append("\n");
        sb.append("战斗次数: ").append(totalBattles).append("\n");
        sb.append("结束原因: ").append(endReason).append("\n");

        sb.append("\n【最终结果】\n");
        if (winnerName != null && !winnerName.isEmpty()) {
            sb.append("胜利者: ").append(winnerName).append("\n");
        } else {
            sb.append("结果: 平局\n");
        }

        sb.append("\n【参战方排名】\n");
        for (int i = 0; i < participants.size(); i++) {
            ExerciseReport.ParticipantData p = participants.get(i);
            sb.append(String.format("第%d名: %s\n", p.rank(), p.nationName()));
            sb.append(String.format("  - 初始兵力: %d, 伤亡: %d, 剩余: %d\n",
                p.initialSoldiers(), p.casualties(), p.effectiveSoldiers()));
            sb.append(String.format("  - 击杀: %d, 胜负: %d胜/%d负\n",
                p.kills(), p.wins(), p.losses()));
            sb.append(String.format("  - 士气变化: %+.1f, 获得经验: %.0f\n",
                p.morale(), p.experienceGained()));
            sb.append(String.format("  - 存活率: %.1f%%\n", p.survivalRate() * 100));
        }

        sb.append("\n═══════════════════════════════════════\n");
        return sb.toString();
    }
}
