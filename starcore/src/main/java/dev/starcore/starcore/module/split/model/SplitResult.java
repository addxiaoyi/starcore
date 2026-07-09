package dev.starcore.starcore.module.split.model;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.UUID;

/**
 * 分裂结果模型
 * 返回分裂操作的结果信息
 */
public record SplitResult(
    boolean success,
    SplitResultType type,
    String message,
    NationId originalNationId,
    String originalNationName,
    NationId newNationId,
    String newNationName,
    int transferredChunks,
    double cost,
    SplitRequest request
) {
    /**
     * 分裂结果类型枚举
     */
    public enum SplitResultType {
        /** 请求创建成功 */
        REQUEST_CREATED,
        /** 分裂成功完成 */
        SPLIT_COMPLETED,
        /** 请求已批准 */
        REQUEST_APPROVED,
        /** 请求已拒绝 */
        REQUEST_REJECTED,
        /** 请求已取消 */
        REQUEST_CANCELLED,
        /** 请求已过期 */
        REQUEST_EXPIRED,
        /** 失败 */
        FAILED,
        /** 权限不足 */
        PERMISSION_DENIED,
        /** 条件不满足 */
        CONDITION_NOT_MET
    }

    /**
     * 创建成功结果
     */
    public static SplitResult success(SplitResultType type, String message, NationId originalNationId, String originalNationName) {
        return new SplitResult(true, type, message, originalNationId, originalNationName, null, null, 0, 0, null);
    }

    /**
     * 创建分裂完成结果
     */
    public static SplitResult splitCompleted(
        Nation originalNation,
        Nation newNation,
        int transferredChunks,
        double cost
    ) {
        return new SplitResult(
            true,
            SplitResultType.SPLIT_COMPLETED,
            "国家分裂成功完成",
            originalNation.id(),
            originalNation.name(),
            newNation.id(),
            newNation.name(),
            transferredChunks,
            cost,
            null
        );
    }

    /**
     * 创建请求结果
     */
    public static SplitResult requestCreated(SplitRequest request, double cost) {
        return new SplitResult(
            true,
            SplitResultType.REQUEST_CREATED,
            "分裂请求已创建，等待国家领导人审批",
            request.sourceNationId(),
            request.sourceNationName(),
            null,
            request.newNationName(),
            request.region().chunkCount(),
            cost,
            request
        );
    }

    /**
     * 创建失败结果
     */
    public static SplitResult failure(String message) {
        return new SplitResult(false, SplitResultType.FAILED, message, null, null, null, null, 0, 0, null);
    }

    /**
     * 创建权限拒绝结果
     */
    public static SplitResult permissionDenied(String message) {
        return new SplitResult(false, SplitResultType.PERMISSION_DENIED, message, null, null, null, null, 0, 0, null);
    }

    /**
     * 创建条件不满足结果
     */
    public static SplitResult conditionNotMet(String message) {
        return new SplitResult(false, SplitResultType.CONDITION_NOT_MET, message, null, null, null, null, 0, 0, null);
    }

    /**
     * 检查是否为成功结果
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取简要描述
     */
    public String brief() {
        return String.format("[%s] %s", type.name(), message);
    }
}
