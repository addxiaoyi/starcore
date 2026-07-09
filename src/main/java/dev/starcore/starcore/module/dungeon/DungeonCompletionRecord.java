package dev.starcore.starcore.module.dungeon;

import java.time.Instant;
import java.util.UUID;

/**
 * 副本完成记录
 */
public record DungeonCompletionRecord(
    UUID recordId,
    String dungeonId,
    DungeonDifficulty difficulty,
    UUID playerId,
    UUID nationId,
    Instant completedAt,
    long durationSeconds,
    int deaths,
    int roomDeaths,
    DungeonCompletionResult result,
    DungeonRewards rewards,
    boolean firstClear
) {
    /**
     * 创建成功记录
     */
    public static DungeonCompletionRecord success(
        String dungeonId,
        DungeonDifficulty difficulty,
        UUID playerId,
        UUID nationId,
        long durationSeconds,
        int deaths,
        DungeonRewards rewards
    ) {
        return new DungeonCompletionRecord(
            UUID.randomUUID(),
            dungeonId,
            difficulty,
            playerId,
            nationId,
            Instant.now(),
            durationSeconds,
            deaths,
            0,
            DungeonCompletionResult.SUCCESS,
            rewards,
            false
        );
    }

    /**
     * 创建失败记录
     */
    public static DungeonCompletionRecord failed(
        String dungeonId,
        DungeonDifficulty difficulty,
        UUID playerId,
        UUID nationId,
        long durationSeconds,
        DungeonCompletionResult result
    ) {
        return new DungeonCompletionRecord(
            UUID.randomUUID(),
            dungeonId,
            difficulty,
            playerId,
            nationId,
            Instant.now(),
            durationSeconds,
            0,
            0,
            result,
            null,
            false
        );
    }

    /**
     * 检查是否成功
     */
    public boolean isSuccess() {
        return result == DungeonCompletionResult.SUCCESS;
    }

    /**
     * 检查是否失败
     */
    public boolean isFailure() {
        return result != DungeonCompletionResult.SUCCESS;
    }
}
