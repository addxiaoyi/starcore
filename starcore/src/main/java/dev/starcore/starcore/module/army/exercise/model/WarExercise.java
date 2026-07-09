package dev.starcore.starcore.module.army.exercise.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.UUID;

/**
 * 军事演习
 * 代表一个国家发起的军事演习活动
 */
public final class WarExercise {
    private final UUID id;
    private final UUID nationId;
    private final String name;
    private final ExerciseType type;
    private final Location location;
    private final int duration;           // 持续时间（分钟）
    private final int maxParticipants;     // 最大参与人数
    private Instant startedAt;
    private Instant endsAt;
    private ExerciseStatus status;

    public WarExercise(
        UUID id,
        UUID nationId,
        String name,
        ExerciseType type,
        Location location,
        int duration,
        int maxParticipants,
        Instant startedAt,
        Instant endsAt,
        ExerciseStatus status
    ) {
        this.id = id;
        this.nationId = nationId;
        this.name = name;
        this.type = type;
        this.location = location;
        this.duration = duration;
        this.maxParticipants = maxParticipants;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.status = status;
    }

    /**
     * 创建新演习
     */
    public static WarExercise create(
        UUID nationId,
        String name,
        ExerciseType type,
        Location location,
        int duration,
        int maxParticipants
    ) {
        Instant now = Instant.now();
        return new WarExercise(
            UUID.randomUUID(),
            nationId,
            name,
            type,
            location,
            duration,
            maxParticipants,
            now,
            now.plusSeconds(duration * 60L),
            ExerciseStatus.PREPARING
        );
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public UUID nationId() {
        return nationId;
    }

    public String name() {
        return name;
    }

    public ExerciseType type() {
        return type;
    }

    public Location location() {
        return location;
    }

    public int duration() {
        return duration;
    }

    public int maxParticipants() {
        return maxParticipants;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public ExerciseStatus status() {
        return status;
    }

    // ==================== State Modifiers ====================

    public void start() {
        this.status = ExerciseStatus.ACTIVE;
    }

    public void pause() {
        this.status = ExerciseStatus.PAUSED;
    }

    public void resume() {
        this.status = ExerciseStatus.ACTIVE;
    }

    public void end() {
        this.status = ExerciseStatus.COMPLETED;
        this.endsAt = Instant.now();
    }

    public void cancel() {
        this.status = ExerciseStatus.CANCELLED;
        this.endsAt = Instant.now();
    }

    // ==================== Utility Methods ====================

    public boolean isActive() {
        return status == ExerciseStatus.ACTIVE;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(endsAt);
    }

    public long remainingMinutes() {
        long remaining = java.time.Duration.between(Instant.now(), endsAt).toMinutes();
        return Math.max(0, remaining);
    }

    public boolean hasCapacity() {
        return maxParticipants > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WarExercise other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("WarExercise{id=%s, name='%s', type=%s, status=%s}",
            id, name, type, status);
    }
}
