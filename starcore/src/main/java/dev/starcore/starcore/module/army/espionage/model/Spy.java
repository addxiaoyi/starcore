package dev.starcore.starcore.module.army.espionage.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 间谍数据模型
 */
public final class Spy {
    private final UUID id;
    private final UUID ownerId;           // 所属国家ID
    private final String ownerName;       // 所属国家名称
    private final UUID trainerId;         // 训练者UUID
    private final SpyType type;
    private final int experience;
    private int missionsCompleted;
    private int missionsFailed;
    private Instant lastMissionAt;
    private Instant recruitedAt;          // 招募时间
    private double morale;                // 间谍士气 (0-100)

    public Spy(UUID id, UUID ownerId, String ownerName, UUID trainerId, SpyType type,
               int experience, int missionsCompleted, int missionsFailed,
               Instant recruitedAt, Instant lastMissionAt, double morale) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.trainerId = trainerId;
        this.type = type;
        this.experience = experience;
        this.missionsCompleted = missionsCompleted;
        this.missionsFailed = missionsFailed;
        this.recruitedAt = recruitedAt;
        this.lastMissionAt = lastMissionAt;
        this.morale = morale;
    }

    public static Spy create(UUID ownerId, String ownerName, UUID trainerId, SpyType type) {
        return new Spy(
            UUID.randomUUID(),
            ownerId,
            ownerName,
            trainerId,
            type,
            0,
            0,
            0,
            Instant.now(),
            null,
            100.0
        );
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public UUID trainerId() {
        return trainerId;
    }

    public SpyType type() {
        return type;
    }

    public int experience() {
        return experience;
    }

    public int missionsCompleted() {
        return missionsCompleted;
    }

    public int missionsFailed() {
        return missionsFailed;
    }

    public Instant recruitedAt() {
        return recruitedAt;
    }

    public Instant lastMissionAt() {
        return lastMissionAt;
    }

    public double morale() {
        return morale;
    }

    public void setMorale(double morale) {
        this.morale = Math.max(0, Math.min(100, morale));
    }

    /**
     * 计算间谍有效隐蔽等级
     * 基础隐蔽 + 经验加成 + 士气加成
     */
    public double effectiveStealth() {
        double expBonus = Math.min(experience * 0.001, 0.2); // 最多20%经验加成
        double moraleBonus = (morale - 50) * 0.002; // 士气50时为0，高于50加成，低于50惩罚
        return type.stealthBonus() + expBonus + moraleBonus;
    }

    /**
     * 是否可以执行任务
     */
    public boolean canOperate() {
        return morale >= 20 && experience >= getMinExperienceRequired();
    }

    /**
     * 获取执行任务所需最低经验
     */
    public int getMinExperienceRequired() {
        return switch (type) {
            case BASIC -> 0;
            case PROFESSIONAL -> 50;
            case ELITE -> 150;
            case MASTER -> 300;
        };
    }

    /**
     * 增加经验
     */
    public void addExperience(int amount) {
        // 经验值无上限，但不影响隐蔽能力计算
    }

    /**
     * 完成任务
     */
    public void missionSucceeded() {
        this.missionsCompleted++;
        this.lastMissionAt = Instant.now();
        this.morale = Math.min(100, morale + 5);
    }

    /**
     * 任务失败
     */
    public void missionFailed() {
        this.missionsFailed++;
        this.lastMissionAt = Instant.now();
        this.morale = Math.max(0, morale - 15);
    }

    /**
     * 每日维护检查
     */
    public void dailyMaintenanceCheck() {
        this.morale = Math.max(0, morale - 2);
    }

    /**
     * 间谍是否已经阵亡/失效
     */
    public boolean isDead() {
        return morale <= 0;
    }

    /**
     * 计算任务成功率
     */
    public double calculateSuccessChance(EspionageOperation operation) {
        double baseChance = 0.5;
        double stealthBonus = effectiveStealth() * 0.3;
        double expBonus = Math.min(experience * 0.001, 0.15);
        double moraleFactor = (morale - 30) / 70.0 * 0.1; // 士气30时-0.01，100时+0.1
        double operationDifficulty = operation.difficulty() * 0.1;

        return Math.max(0.1, Math.min(0.95, baseChance + stealthBonus + expBonus + moraleFactor - operationDifficulty));
    }

    /**
     * 任务持续时间（刻）
     */
    public long missionDurationTicks() {
        long baseDuration = switch (type) {
            case BASIC -> 12000;    // 10分钟
            case PROFESSIONAL -> 7200;  // 6分钟
            case ELITE -> 3600;    // 3分钟
            case MASTER -> 1800;   // 1.5分钟
        };

        // 经验越高，持续时间越短
        int reduction = (int) Math.min(experience * 2, baseDuration * 0.5);
        return Math.max(600, baseDuration - reduction); // 最少30秒
    }
}
