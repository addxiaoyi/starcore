package dev.starcore.starcore.module.split.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 分裂请求模型
 * 记录一个完整的国家分裂请求
 */
public record SplitRequest(
    UUID requestId,
    UUID requesterId,
    String requesterName,
    NationId sourceNationId,
    String sourceNationName,
    String newNationName,
    SplitRegion region,
    Instant createdAt,
    SplitRequestStatus status,
    UUID approverId,
    Instant processedAt,
    String reason
) {
    /**
     * 分裂请求状态枚举
     */
    public enum SplitRequestStatus {
        /** 待审批 */
        PENDING,
        /** 已批准 */
        APPROVED,
        /** 已拒绝 */
        REJECTED,
        /** 已取消 */
        CANCELLED,
        /** 已过期 */
        EXPIRED
    }

    /**
     * 创建新的待处理请求
     */
    public static SplitRequest pending(
        UUID requesterId,
        String requesterName,
        NationId sourceNationId,
        String sourceNationName,
        String newNationName,
        SplitRegion region
    ) {
        return new SplitRequest(
            UUID.randomUUID(),
            requesterId,
            requesterName,
            sourceNationId,
            sourceNationName,
            newNationName,
            region,
            Instant.now(),
            SplitRequestStatus.PENDING,
            null,
            null,
            null
        );
    }

    /**
     * 标记为已批准
     */
    public SplitRequest approved(UUID approverId) {
        return new SplitRequest(
            requestId,
            requesterId,
            requesterName,
            sourceNationId,
            sourceNationName,
            newNationName,
            region,
            createdAt,
            SplitRequestStatus.APPROVED,
            approverId,
            Instant.now(),
            reason
        );
    }

    /**
     * 标记为已拒绝
     */
    public SplitRequest rejected(UUID rejecterId, String reason) {
        return new SplitRequest(
            requestId,
            requesterId,
            requesterName,
            sourceNationId,
            sourceNationName,
            newNationName,
            region,
            createdAt,
            SplitRequestStatus.REJECTED,
            rejecterId,
            Instant.now(),
            reason
        );
    }

    /**
     * 标记为已取消
     */
    public SplitRequest cancelled() {
        return new SplitRequest(
            requestId,
            requesterId,
            requesterName,
            sourceNationId,
            sourceNationName,
            newNationName,
            region,
            createdAt,
            SplitRequestStatus.CANCELLED,
            approverId,
            Instant.now(),
            reason
        );
    }

    /**
     * 检查请求是否在待处理状态
     */
    public boolean isPending() {
        return status == SplitRequestStatus.PENDING;
    }

    /**
     * 获取请求持续时间（分钟）
     */
    public long durationMinutes() {
        return java.time.Duration.between(createdAt, Instant.now()).toMinutes();
    }
}
