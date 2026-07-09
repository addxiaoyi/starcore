package dev.starcore.starcore.module.territoryrent.model;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 租借记录模型
 * 表示一个有效的领土租借
 */
public record Rental(
    UUID id,
    UUID requestId,
    String ownerNationId,
    String tenantNationId,
    ChunkCoordinate coordinate,
    BigDecimal dailyRent,
    int durationDays,
    Instant startTime,
    Instant endTime,
    RentalStatus status,
    Instant createdAt,
    int permissionLevel,
    BigDecimal totalPaid
) {
    public Rental {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerNationId, "ownerNationId");
        Objects.requireNonNull(tenantNationId, "tenantNationId");
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(dailyRent, "dailyRent");
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取剩余天数
     */
    public int remainingDays() {
        long secondsRemaining = endTime.getEpochSecond() - Instant.now().getEpochSecond();
        if (secondsRemaining <= 0) {
            return 0;
        }
        return (int) Math.ceil(secondsRemaining / (24.0 * 60 * 60));
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        return Instant.now().isAfter(endTime);
    }

    /**
     * 计算总租金
     */
    public BigDecimal totalRent() {
        return dailyRent.multiply(BigDecimal.valueOf(durationDays));
    }

    /**
     * 获取已付金额
     */
    public BigDecimal unpaidAmount() {
        return totalRent().subtract(totalPaid);
    }

    public static class Builder {
        private UUID id;
        private UUID requestId;
        private String ownerNationId;
        private String tenantNationId;
        private ChunkCoordinate coordinate;
        private BigDecimal dailyRent;
        private int durationDays;
        private Instant startTime;
        private Instant endTime;
        private RentalStatus status;
        private Instant createdAt;
        private int permissionLevel = 2;
        private BigDecimal totalPaid = BigDecimal.ZERO;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder requestId(UUID requestId) { this.requestId = requestId; return this; }
        public Builder ownerNationId(String ownerNationId) { this.ownerNationId = ownerNationId; return this; }
        public Builder tenantNationId(String tenantNationId) { this.tenantNationId = tenantNationId; return this; }
        public Builder coordinate(ChunkCoordinate coordinate) { this.coordinate = coordinate; return this; }
        public Builder dailyRent(BigDecimal dailyRent) { this.dailyRent = dailyRent; return this; }
        public Builder durationDays(int durationDays) { this.durationDays = durationDays; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder endTime(Instant endTime) { this.endTime = endTime; return this; }
        public Builder status(RentalStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder permissionLevel(int permissionLevel) { this.permissionLevel = permissionLevel; return this; }
        public Builder totalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; return this; }

        public Rental build() {
            return new Rental(id, requestId, ownerNationId, tenantNationId, coordinate,
                dailyRent, durationDays, startTime, endTime, status, createdAt, permissionLevel, totalPaid);
        }
    }
}