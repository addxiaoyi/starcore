package dev.starcore.starcore.module.army.exercise.model;

/**
 * 演习结果报告
 * 在演习结束时生成
 */
public final class ExerciseReport {
    private final String name;
    private final String type;
    private final int durationMinutes;
    private final int totalParticipants;
    private final int totalBattles;
    private final String endReason;
    private final String winnerName;
    private final java.util.List<ParticipantData> participants;

    public ExerciseReport(
        String name,
        String type,
        int durationMinutes,
        int totalParticipants,
        int totalBattles,
        String endReason,
        String winnerName,
        java.util.List<ParticipantData> participants
    ) {
        this.name = name;
        this.type = type;
        this.durationMinutes = durationMinutes;
        this.totalParticipants = totalParticipants;
        this.totalBattles = totalBattles;
        this.endReason = endReason;
        this.winnerName = winnerName;
        this.participants = participants != null ? java.util.List.copyOf(participants) : java.util.List.of();
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public int durationMinutes() {
        return durationMinutes;
    }

    public int totalParticipants() {
        return totalParticipants;
    }

    public int totalBattles() {
        return totalBattles;
    }

    public String endReason() {
        return endReason;
    }

    public String winnerName() {
        return winnerName;
    }

    public java.util.List<ParticipantData> participants() {
        return participants;
    }

    /**
     * 格式化报告
     */
    public String formatReport() {
        return ExerciseReportGenerator.generateReport(
            name,
            type,
            durationMinutes,
            totalParticipants,
            participants.stream().mapToInt(ParticipantData::initialSoldiers).sum(),
            totalBattles,
            endReason,
            winnerName,
            participants
        );
    }

    /**
     * 参与者数据
     */
    public record ParticipantData(
        java.util.UUID nationId,
        String nationName,
        String role,
        int initialSoldiers,
        int casualties,
        int kills,
        int wins,
        int losses,
        double morale,
        double experienceGained,
        int rank
    ) {
        public int effectiveSoldiers() {
            return Math.max(0, initialSoldiers - casualties);
        }

        public double survivalRate() {
            if (initialSoldiers == 0) return 0;
            return (double) effectiveSoldiers() / initialSoldiers;
        }
    }
}