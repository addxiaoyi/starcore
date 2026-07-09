package dev.starcore.starcore.module.army.prisoner.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 俘虏记录模型
 *
 * @param id 俘虏记录唯一ID
 * @param prisonerId 被俘虏的玩家UUID
 * @param prisonerName 被俘虏的玩家名称
 * @param captorId 俘虏方玩家UUID
 * @param captorName 俘虏方玩家名称
 * @param captorNationId 俘虏方国家ID
 * @param capturedNationId 被俘虏方国家ID
 * @param status 俘虏状态
 * @param captureTime 俘虏时间
 * @param releaseTime 释放时间（可选）
 * @param ransomAmount 赎金金额
 * @param laborHours 劳役小时数
 * @param laborCompleted 已完成劳役小时数
 * @param prisonLocation 监狱位置（世界名:x:y:z格式）
 * @param notes 备注信息
 * @param battleId 关联的战斗记录ID（可选）
 */
public record PrisonerOfWar(
    UUID id,
    UUID prisonerId,
    String prisonerName,
    UUID captorId,
    String captorName,
    UUID captorNationId,
    UUID capturedNationId,
    PrisonerStatus status,
    Instant captureTime,
    Instant releaseTime,
    double ransomAmount,
    int laborHours,
    int laborCompleted,
    String prisonLocation,
    String notes,
    UUID battleId
) {
    /**
     * 创建一个新的俘虏记录
     */
    public static PrisonerOfWar create(
        UUID prisonerId,
        String prisonerName,
        UUID captorId,
        String captorName,
        UUID captorNationId,
        UUID capturedNationId
    ) {
        return new PrisonerOfWar(
            UUID.randomUUID(),
            prisonerId,
            prisonerName,
            captorId,
            captorName,
            captorNationId,
            capturedNationId,
            PrisonerStatus.CAPTURED,
            Instant.now(),
            null,
            0.0,
            0,
            0,
            null,
            "",
            null
        );
    }

    /**
     * 创建一个带战斗关联的俘虏记录
     */
    public static PrisonerOfWar createWithBattle(
        UUID prisonerId,
        String prisonerName,
        UUID captorId,
        String captorName,
        UUID captorNationId,
        UUID capturedNationId,
        UUID battleId
    ) {
        return new PrisonerOfWar(
            UUID.randomUUID(),
            prisonerId,
            prisonerName,
            captorId,
            captorName,
            captorNationId,
            capturedNationId,
            PrisonerStatus.CAPTURED,
            Instant.now(),
            null,
            0.0,
            0,
            0,
            null,
            "",
            battleId
        );
    }

    /**
     * 更新俘虏状态
     */
    public PrisonerOfWar withStatus(PrisonerStatus newStatus) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            captorNationId, capturedNationId, newStatus,
            captureTime, releaseTime, ransomAmount, laborHours,
            laborCompleted, prisonLocation, notes, battleId
        );
    }

    /**
     * 设置释放时间
     */
    public PrisonerOfWar withReleaseTime(Instant time) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            captorNationId, capturedNationId, status,
            captureTime, time, ransomAmount, laborHours,
            laborCompleted, prisonLocation, notes, battleId
        );
    }

    /**
     * 设置赎金
     */
    public PrisonerOfWar withRansom(double amount) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            captorNationId, capturedNationId, status,
            captureTime, releaseTime, amount, laborHours,
            laborCompleted, prisonLocation, notes, battleId
        );
    }

    /**
     * 设置劳役时间
     */
    public PrisonerOfWar withLaborHours(int hours) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            captorNationId, capturedNationId, status,
            captureTime, releaseTime, ransomAmount, hours,
            laborCompleted, prisonLocation, notes, battleId
        );
    }

    /**
     * 增加劳役完成进度
     */
    public PrisonerOfWar addLaborCompleted(int hours) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            captorNationId, capturedNationId, status,
            captureTime, releaseTime, ransomAmount, laborHours,
            laborCompleted + hours, prisonLocation, notes, battleId
        );
    }

    /**
     * 设置监狱位置
     */
    public PrisonerOfWar withPrisonLocation(String location) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            captorNationId, capturedNationId, status,
            captureTime, releaseTime, ransomAmount, laborHours,
            laborCompleted, location, notes, battleId
        );
    }

    /**
     * 设置备注
     */
    public PrisonerOfWar withNotes(String newNotes) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            captorNationId, capturedNationId, status,
            captureTime, releaseTime, ransomAmount, laborHours,
            laborCompleted, prisonLocation, newNotes, battleId
        );
    }

    /**
     * 设置俘虏国ID
     */
    public PrisonerOfWar withCaptorNationId(UUID newCaptorNationId) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, captorName,
            newCaptorNationId, capturedNationId, status,
            captureTime, releaseTime, ransomAmount, laborHours,
            laborCompleted, prisonLocation, notes, battleId
        );
    }

    /**
     * 设置俘虏玩家ID
     */
    public PrisonerOfWar withCaptorId(UUID newCaptorId) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, newCaptorId, captorName,
            captorNationId, capturedNationId, status,
            captureTime, releaseTime, ransomAmount, laborHours,
            laborCompleted, prisonLocation, notes, battleId
        );
    }

    /**
     * 设置俘虏玩家名称
     */
    public PrisonerOfWar withCaptorPlayerName(String newCaptorPlayerName) {
        return new PrisonerOfWar(
            id, prisonerId, prisonerName, captorId, newCaptorPlayerName,
            captorNationId, capturedNationId, status,
            captureTime, releaseTime, ransomAmount, laborHours,
            laborCompleted, prisonLocation, notes, battleId
        );
    }

    /**
     * 检查是否可以被释放
     */
    public boolean canBeReleased() {
        return status == PrisonerStatus.CAPTURED ||
               status == PrisonerStatus.IMPRISONED ||
               status == PrisonerStatus.FORCED_LABOR ||
               status == PrisonerStatus.AWAITING_EXCHANGE;
    }

    /**
     * 检查是否为活跃状态
     */
    public boolean isActive() {
        return status.isActive();
    }

    /**
     * 检查劳役是否完成
     */
    public boolean isLaborCompleted() {
        return laborHours > 0 && laborCompleted >= laborHours;
    }

    /**
     * 获取被俘虏时长（秒）
     */
    public long getCaptivityDurationSeconds() {
        Instant endTime = releaseTime != null ? releaseTime : Instant.now();
        return endTime.getEpochSecond() - captureTime.getEpochSecond();
    }
}
