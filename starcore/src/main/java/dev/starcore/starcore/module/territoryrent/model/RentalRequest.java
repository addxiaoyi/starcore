package dev.starcore.starcore.module.territoryrent.model;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 租借请求模型
 * 表示一个待处理的租借请求
 */
public record RentalRequest(
    UUID id,
    UUID requesterId,
    String requesterNationId,
    String targetNationId,
    ChunkCoordinate coordinate,
    BigDecimal dailyRent,
    int durationDays,
    Instant createdAt,
    RequestStatus status,
    UUID processedBy,
    Instant processedAt,
    String rejectionReason
) {
    public RentalRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(requesterNationId, "requesterNationId");
        Objects.requireNonNull(targetNationId, "targetNationId");
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(dailyRent, "dailyRent");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 计算总租金
     */
    public BigDecimal totalRent() {
        return dailyRent.multiply(BigDecimal.valueOf(durationDays));
    }

    /**
     * 检查是否已过期（超过7天未处理）
     */
    public boolean isExpired() {
        return Instant.now().minusSeconds(7 * 24 * 60 * 60).isAfter(createdAt);
    }

    /**
     * 检查请求是否可被处理
     */
    public boolean canBeProcessed() {
        return status == RequestStatus.PENDING;
    }

    public static class Builder {
        private UUID id;
        private UUID requesterId;
        private String requesterNationId;
        private String targetNationId;
        private ChunkCoordinate coordinate;
        private BigDecimal dailyRent;
        private int durationDays;
        private Instant createdAt = Instant.now();
        private RequestStatus status = RequestStatus.PENDING;
        private UUID processedBy;
        private Instant processedAt;
        private String rejectionReason;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder requesterId(UUID requesterId) { this.requesterId = requesterId; return this; }
        public Builder requesterNationId(String requesterNationId) { this.requesterNationId = requesterNationId; return this; }
        public Builder targetNationId(String targetNationId) { this.targetNationId = targetNationId; return this; }
        public Builder coordinate(ChunkCoordinate coordinate) { this.coordinate = coordinate; return this; }
        public Builder dailyRent(BigDecimal dailyRent) { this.dailyRent = dailyRent; return this; }
        public Builder durationDays(int durationDays) { this.durationDays = durationDays; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder status(RequestStatus status) { this.status = status; return this; }
        public Builder processedBy(UUID processedBy) { this.processedBy = processedBy; return this; }
        public Builder processedAt(Instant processedAt) { this.processedAt = processedAt; return this; }
        public Builder rejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; return this; }

        public RentalRequest build() {
            return new RentalRequest(id, requesterId, requesterNationId, targetNationId, coordinate,
                dailyRent, durationDays, createdAt, status, processedBy, processedAt, rejectionReason);
        }
    }

    /**
     * 请求状态
     */
    public enum RequestStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        CANCELLED,
        EXPIRED
    }
}