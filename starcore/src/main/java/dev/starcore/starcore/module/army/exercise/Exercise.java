package dev.starcore.starcore.module.army.exercise;

import java.time.Instant;
import java.util.*;
import java.util.Optional;

/**
 * 演习数据模型
 * 代表一场完整的军事演习
 */
public final class Exercise {
    private final UUID id;
    private final String name;
    private final UUID organizerId;
    private final ExerciseType type;
    private ExerciseState state;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
    private final ExerciseConfig config;

    // 参与方
    private final List<ExerciseParticipant> participants;
    // 红方阵营
    private UUID redSideId;
    // 蓝方阵营
    private UUID blueSideId;

    // 战斗统计
    private int totalBattles;
    private int redSideWins;
    private int blueSideWins;

    // 地点（可选）
    private String world;
    private int centerX;
    private int centerZ;
    private int radius;

    public Exercise(
        UUID id,
        String name,
        UUID organizerId,
        ExerciseType type,
        ExerciseState state,
        Instant createdAt,
        ExerciseConfig config
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId");
        this.type = Objects.requireNonNull(type, "type");
        this.state = Objects.requireNonNull(state, "state");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.config = Objects.requireNonNull(config, "config");
        this.participants = new ArrayList<>();
        this.totalBattles = 0;
        this.redSideWins = 0;
        this.blueSideWins = 0;
    }

    /**
     * 创建新演习
     */
    public static Exercise create(String name, UUID organizerId, ExerciseType type, ExerciseConfig config) {
        return new Exercise(
            UUID.randomUUID(),
            name,
            organizerId,
            type,
            ExerciseState.PREPARING,
            Instant.now(),
            config
        );
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID organizerId() {
        return organizerId;
    }

    public ExerciseType type() {
        return type;
    }

    public ExerciseState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public ExerciseConfig config() {
        return config;
    }

    public List<ExerciseParticipant> participants() {
        return Collections.unmodifiableList(participants);
    }

    public Optional<UUID> redSideId() {
        return Optional.ofNullable(redSideId);
    }

    public Optional<UUID> blueSideId() {
        return Optional.ofNullable(blueSideId);
    }

    public int totalBattles() {
        return totalBattles;
    }

    public int redSideWins() {
        return redSideWins;
    }

    public int blueSideWins() {
        return blueSideWins;
    }

    public Optional<String> world() {
        return Optional.ofNullable(world);
    }

    public OptionalInt centerX() {
        return centerX == 0 ? OptionalInt.empty() : OptionalInt.of(centerX);
    }

    public OptionalInt centerZ() {
        return centerZ == 0 ? OptionalInt.empty() : OptionalInt.of(centerZ);
    }

    public int radius() {
        return radius;
    }

    // ==================== Setters/Modifiers ====================

    public void setState(ExerciseState state) {
        this.state = Objects.requireNonNull(state, "state");
        if (state == ExerciseState.IN_PROGRESS && this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        if (state.isTerminal() && this.endedAt == null) {
            this.endedAt = Instant.now();
        }
    }

    public void setRedSideId(UUID redSideId) {
        this.redSideId = redSideId;
    }

    public void setBlueSideId(UUID blueSideId) {
        this.blueSideId = blueSideId;
    }

    public void setLocation(String world, int centerX, int centerZ, int radius) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public void incrementTotalBattles() {
        this.totalBattles++;
    }

    public void incrementRedSideWins() {
        this.redSideWins++;
    }

    public void incrementBlueSideWins() {
        this.blueSideWins++;
    }

    // ==================== Participant Management ====================

    /**
     * 添加参与者
     */
    public boolean addParticipant(ExerciseParticipant participant) {
        if (participants.stream().anyMatch(p -> p.nationId().equals(participant.nationId()))) {
            return false;
        }
        return participants.add(participant);
    }

    /**
     * 移除参与者
     */
    public boolean removeParticipant(UUID nationId) {
        return participants.removeIf(p -> p.nationId().equals(nationId));
    }

    /**
     * 获取参与者
     */
    public Optional<ExerciseParticipant> getParticipant(UUID nationId) {
        return participants.stream()
            .filter(p -> p.nationId().equals(nationId))
            .findFirst();
    }

    /**
     * 获取参与者数量
     */
    public int participantCount() {
        return participants.size();
    }

    /**
     * 获取总士兵数
     */
    public int totalSoldiers() {
        return participants.stream()
            .mapToInt(ExerciseParticipant::soldierCount)
            .sum();
    }

    /**
     * 获取参与国家ID列表
     */
    public List<UUID> getNationIds() {
        return participants.stream()
            .map(ExerciseParticipant::nationId)
            .toList();
    }

    /**
     * 检查是否有足够的参与者
     */
    public boolean hasEnoughParticipants() {
        return participants.size() >= config.minParticipants();
    }

    /**
     * 检查参与者是否已满
     */
    public boolean isFull() {
        return participants.size() >= config.maxParticipants();
    }

    /**
     * 获取演练时长（分钟）
     */
    public int getDurationMinutes() {
        if (startedAt == null) {
            return 0;
        }
        Instant end = endedAt != null ? endedAt : Instant.now();
        return (int) java.time.Duration.between(startedAt, end).toMinutes();
    }

    /**
     * 获取剩余时间（分钟）
     */
    public int getRemainingMinutes() {
        if (startedAt == null || !state.isActive()) {
            return config.maxDurationMinutes();
        }
        Instant end = endedAt != null ? endedAt : Instant.now();
        int elapsed = (int) java.time.Duration.between(startedAt, end).toMinutes();
        return Math.max(0, config.maxDurationMinutes() - elapsed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Exercise exercise)) return false;
        return id.equals(exercise.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Exercise{id=%s, name='%s', type=%s, state=%s, participants=%d}",
            id, name, type, state, participants.size());
    }
}
