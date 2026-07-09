package dev.starcore.starcore.module.army.exercise.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 演习参与记录
 */
public record ExerciseParticipant(
    UUID exerciseId,
    UUID playerId,
    String playerName,
    Instant joinedAt,
    ParticipantRole role,
    int score,
    boolean active
) {
    /**
     * 创建新参与者
     */
    public static ExerciseParticipant join(UUID exerciseId, UUID playerId, String playerName, ParticipantRole role) {
        return new ExerciseParticipant(
            exerciseId,
            playerId,
            playerName,
            Instant.now(),
            role,
            0,
            true
        );
    }

    /**
     * 增加分数
     */
    public ExerciseParticipant addScore(int points) {
        return new ExerciseParticipant(
            exerciseId,
            playerId,
            playerName,
            joinedAt,
            role,
            score + points,
            active
        );
    }

    /**
     * 标记为不活跃
     */
    public ExerciseParticipant inactive() {
        return new ExerciseParticipant(
            exerciseId,
            playerId,
            playerName,
            joinedAt,
            role,
            score,
            false
        );
    }

    /**
     * 更改角色
     */
    public ExerciseParticipant withRole(ParticipantRole newRole) {
        return new ExerciseParticipant(
            exerciseId,
            playerId,
            playerName,
            joinedAt,
            newRole,
            score,
            active
        );
    }
}
