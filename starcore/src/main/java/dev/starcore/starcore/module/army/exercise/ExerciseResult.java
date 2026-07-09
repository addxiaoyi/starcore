package dev.starcore.starcore.module.army.exercise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演习最终结果
 * 在演习结束时生成，记录整体结果
 */
public record ExerciseResult(
    UUID exerciseId,
    String exerciseName,
    ExerciseType type,
    ExerciseState endState,
    String endReason,
    Instant startedAt,
    Instant endedAt,
    int durationMinutes,
    int totalParticipants,
    int totalSoldiers,
    int totalBattles,
    List<ParticipantResult> participantResults,
    UUID winnerId,
    String winnerName,
    Map<String, Object> statistics
) {
    /**
     * 参与者结果
     */
    public record ParticipantResult(
        UUID nationId,
        String nationName,
        ExerciseRole role,
        int initialSoldiers,
        int casualties,
        int kills,
        int battles,
        int wins,
        int losses,
        double finalMorale,
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

        public double winRate() {
            if (battles == 0) return 0;
            return (double) wins / battles;
        }
    }

    /**
     * 创建结果
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 结果构建器
     */
    public static class Builder {
        private UUID exerciseId;
        private String exerciseName;
        private ExerciseType type;
        private ExerciseState endState;
        private String endReason;
        private Instant startedAt;
        private Instant endedAt;
        private int totalBattles;
        private List<ParticipantResult> participantResults = new ArrayList<>();
        private UUID winnerId;
        private String winnerName;
        private Map<String, Object> statistics = new ConcurrentHashMap<>();

        public Builder exerciseId(UUID exerciseId) {
            this.exerciseId = exerciseId;
            return this;
        }

        public Builder exerciseName(String name) {
            this.exerciseName = name;
            return this;
        }

        public Builder type(ExerciseType type) {
            this.type = type;
            return this;
        }

        public Builder endState(ExerciseState state) {
            this.endState = state;
            return this;
        }

        public Builder endReason(String reason) {
            this.endReason = reason;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder endedAt(Instant endedAt) {
            this.endedAt = endedAt;
            return this;
        }

        public Builder totalBattles(int totalBattles) {
            this.totalBattles = totalBattles;
            return this;
        }

        public Builder addParticipantResult(ParticipantResult result) {
            this.participantResults.add(result);
            return this;
        }

        public Builder addParticipantResults(List<ParticipantResult> results) {
            this.participantResults.addAll(results);
            return this;
        }

        public Builder winner(UUID winnerId, String winnerName) {
            this.winnerId = winnerId;
            this.winnerName = winnerName;
            return this;
        }

        public Builder addStatistic(String key, Object value) {
            this.statistics.put(key, value);
            return this;
        }

        public ExerciseResult build() {
            int durationMinutes = 0;
            if (startedAt != null && endedAt != null) {
                durationMinutes = (int) java.time.Duration.between(startedAt, endedAt).toMinutes();
            }

            int totalSoldiers = participantResults.stream()
                .mapToInt(ParticipantResult::initialSoldiers)
                .sum();

            int totalParticipants = participantResults.size();

            return new ExerciseResult(
                exerciseId,
                exerciseName,
                type,
                endState,
                endReason,
                startedAt,
                endedAt,
                durationMinutes,
                totalParticipants,
                totalSoldiers,
                totalBattles,
                Collections.unmodifiableList(participantResults),
                winnerId,
                winnerName,
                Collections.unmodifiableMap(statistics)
            );
        }
    }

    /**
     * 是否正常结束
     */
    public boolean isNormalEnd() {
        return endState == ExerciseState.COMPLETED;
    }

    /**
     * 是否被取消
     */
    public boolean isCancelled() {
        return endState == ExerciseState.CANCELLED || endState == ExerciseState.TIMEOUT;
    }

    /**
     * 获取最高击杀参与者
     */
    public Optional<ParticipantResult> topKiller() {
        return participantResults.stream()
            .max(Comparator.comparingInt(ParticipantResult::kills));
    }

    /**
     * 获取最高存活率参与者
     */
    public Optional<ParticipantResult> topSurvivor() {
        return participantResults.stream()
            .filter(r -> r.initialSoldiers() > 0)
            .max(Comparator.comparingDouble(ParticipantResult::survivalRate));
    }

    /**
     * 格式化完整报告
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("§6═══════════════════════════════════════\n");
        sb.append("§e              演习结果报告              \n");
        sb.append("§6═══════════════════════════════════════\n");
        sb.append("\n");
        sb.append("§f演习名称: §a").append(exerciseName).append("§r\n");
        sb.append("§f演习类型: §b").append(type.displayName()).append("§r\n");
        sb.append("§f持续时间: §e").append(durationMinutes).append(" 分钟§r\n");
        sb.append("§f参与国家: §c").append(totalParticipants).append("§r\n");
        sb.append("§f总士兵数: §c").append(totalSoldiers).append("§r\n");
        sb.append("§f战斗次数: §c").append(totalBattles).append("§r\n");
        sb.append("§f结束原因: §e").append(endReason).append("§r\n");
        sb.append("\n");

        if (winnerName != null) {
            sb.append("§6§l[胜利者] §f").append(winnerName).append("§r\n");
            sb.append("\n");
        }

        sb.append("§e─── 参与排名 ───\n");
        participantResults.stream()
            .sorted(Comparator.comparingInt(ParticipantResult::rank))
            .forEach(r -> {
                String rankIcon = switch (r.rank()) {
                    case 1 -> "§6🥇 ";
                    case 2 -> "§f🥈 ";
                    case 3 -> "§6🥉 ";
                    default -> "§7   ";
                };
                sb.append(rankIcon).append("§f").append(r.rank()).append(". §a")
                    .append(r.nationName()).append("§r\n");
                sb.append("     §7士兵: §f").append(r.effectiveSoldiers()).append("/")
                    .append(r.initialSoldiers())
                    .append(" §7击杀: §c").append(r.kills())
                    .append(" §7战绩: §a").append(r.wins()).append("§7-§c").append(r.losses())
                    .append(" §7士气: §e").append(String.format("%.1f%%", r.finalMorale())).append("§r\n");
            });

        sb.append("\n");
        sb.append("§6═══════════════════════════════════════\n");
        return sb.toString();
    }
}