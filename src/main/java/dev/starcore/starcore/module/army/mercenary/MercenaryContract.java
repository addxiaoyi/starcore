package dev.starcore.starcore.module.army.mercenary;

import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 雇佣兵合同
 * 代表一个雇佣兵与雇主的合同关系
 */
public final class MercenaryContract {
    private final UUID contractId;
    private final UUID mercenaryId;      // 雇佣兵玩家ID
    private final UUID employerId;        // 雇主玩家ID
    private final UUID employerNationId;  // 雇主国家ID
    private final MercenaryType type;
    private MercenaryRank rank;
    private int experience;              // 经验值
    private int kills;
    private int deaths;
    private int missionsCompleted;
    private int salary;                   // 当前薪资金额
    private Instant hiredAt;
    private Instant contractExpiresAt;
    private ContractStatus status;
    private Location lastLocation;
    private Instant lastActiveAt;

    public MercenaryContract(
        UUID contractId,
        UUID mercenaryId,
        UUID employerId,
        UUID employerNationId,
        MercenaryType type,
        MercenaryRank rank,
        int experience,
        int kills,
        int deaths,
        int missionsCompleted,
        int salary,
        Instant hiredAt,
        Instant contractExpiresAt,
        ContractStatus status,
        Location lastLocation,
        Instant lastActiveAt
    ) {
        this.contractId = Objects.requireNonNull(contractId, "contractId");
        this.mercenaryId = Objects.requireNonNull(mercenaryId, "mercenaryId");
        this.employerId = Objects.requireNonNull(employerId, "employerId");
        this.employerNationId = Objects.requireNonNull(employerNationId, "employerNationId");
        this.type = Objects.requireNonNull(type, "type");
        this.rank = rank;
        this.experience = Math.max(0, experience);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.missionsCompleted = Math.max(0, missionsCompleted);
        this.salary = Math.max(0, salary);
        this.hiredAt = hiredAt != null ? hiredAt : Instant.now();
        this.contractExpiresAt = contractExpiresAt;
        this.status = status != null ? status : ContractStatus.ACTIVE;
        this.lastLocation = lastLocation;
        this.lastActiveAt = lastActiveAt != null ? lastActiveAt : Instant.now();
    }

    /**
     * 创建新合同
     */
    public static MercenaryContract create(
        UUID mercenaryId,
        UUID employerId,
        UUID employerNationId,
        MercenaryType type,
        int durationDays
    ) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(durationDays * 24L * 60 * 60);
        int initialSalary = type.baseCost() * durationDays;

        return new MercenaryContract(
            UUID.randomUUID(),
            mercenaryId,
            employerId,
            employerNationId,
            type,
            MercenaryRank.RECRUIT,
            0,
            0,
            0,
            0,
            initialSalary,
            now,
            expires,
            ContractStatus.ACTIVE,
            null,
            now
        );
    }

    // ==================== Getters ====================

    public UUID contractId() {
        return contractId;
    }

    public UUID mercenaryId() {
        return mercenaryId;
    }

    public UUID employerId() {
        return employerId;
    }

    public UUID employerNationId() {
        return employerNationId;
    }

    public MercenaryType type() {
        return type;
    }

    public MercenaryRank rank() {
        return rank;
    }

    public int experience() {
        return experience;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int missionsCompleted() {
        return missionsCompleted;
    }

    public int salary() {
        return salary;
    }

    public Instant hiredAt() {
        return hiredAt;
    }

    public Instant contractExpiresAt() {
        return contractExpiresAt;
    }

    public ContractStatus status() {
        return status;
    }

    public Location lastLocation() {
        return lastLocation;
    }

    public Instant lastActiveAt() {
        return lastActiveAt;
    }

    public int experienceLevel() {
        return experience / 100;
    }

    public double kdr() {
        return deaths == 0 ? (double) kills : (double) kills / deaths;
    }

    // ==================== Setters / Modifiers ====================

    public void setStatus(ContractStatus status) {
        this.status = Objects.requireNonNull(status, "status");
        updateLastActive();
    }

    public void setRank(MercenaryRank rank) {
        this.rank = Objects.requireNonNull(rank, "rank");
    }

    public void setSalary(int salary) {
        this.salary = Math.max(0, salary);
    }

    public void setContractExpiresAt(Instant expiresAt) {
        this.contractExpiresAt = expiresAt;
    }

    public void updateLastLocation(Location location) {
        this.lastLocation = location;
        updateLastActive();
    }

    public void addKill() {
        this.kills++;
        this.experience += 15;
        updateRank();
        updateLastActive();
    }

    public void addDeath() {
        this.deaths++;
        this.experience -= 10;
        if (this.experience < 0) this.experience = 0;
        updateLastActive();
    }

    public void addMissionCompleted() {
        this.missionsCompleted++;
        this.experience += 50;
        updateRank();
        updateLastActive();
    }

    public void addSalary(int amount) {
        if (amount > 0) {
            this.salary += amount;
        }
    }

    public boolean deductSalary(int amount) {
        if (amount <= 0 || salary < amount) {
            return false;
        }
        this.salary -= amount;
        return true;
    }

    public void addExperience(int amount) {
        this.experience += amount;
        updateRank();
        updateLastActive();
    }

    private void updateRank() {
        MercenaryRank newRank = MercenaryRank.fromExperienceLevel(experienceLevel());
        if (newRank.ordinal() > this.rank.ordinal()) {
            this.rank = newRank;
        }
    }

    private void updateLastActive() {
        this.lastActiveAt = Instant.now();
    }

    public boolean isActive() {
        return status == ContractStatus.ACTIVE && !isExpired();
    }

    public boolean isExpired() {
        return contractExpiresAt != null && Instant.now().isAfter(contractExpiresAt);
    }

    public long getRemainingDays() {
        if (contractExpiresAt == null) {
            return -1;
        }
        long seconds = contractExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, seconds / (24 * 60 * 60));
    }

    public long getRemainingHours() {
        if (contractExpiresAt == null) {
            return -1;
        }
        long seconds = contractExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, seconds / (60 * 60));
    }

    public double getCombatPower() {
        double basePower = type.attackMultiplier() * 10;
        double rankBonus = rank.payMultiplier();
        double experienceBonus = 1.0 + (experienceLevel() * 0.05);
        return basePower * rankBonus * experienceBonus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MercenaryContract other)) return false;
        return contractId.equals(other.contractId);
    }

    @Override
    public int hashCode() {
        return contractId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("MercenaryContract{id=%s, mercenary=%s, type=%s, rank=%s, status=%s, exp=%d}",
            contractId, mercenaryId, type.key(), rank.key(), status, experience);
    }
}
