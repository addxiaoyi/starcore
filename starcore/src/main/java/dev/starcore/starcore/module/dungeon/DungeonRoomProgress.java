package dev.starcore.starcore.module.dungeon;

import java.time.Instant;
import java.util.UUID;

/**
 * 房间进度
 */
public record DungeonRoomProgress(
    String roomId,
    RoomStatus status,
    int mobsRemaining,
    int mobsTotal,
    int wavesCompleted,
    int wavesTotal,
    long startTime,
    long endTime,
    Instant lastActivityTime,
    String puzzleSolution,
    boolean puzzleSolved
) {
    /**
     * 创建新进度
     */
    public static DungeonRoomProgress create(String roomId, int mobTotal) {
        return new DungeonRoomProgress(
            roomId,
            RoomStatus.IN_PROGRESS,
            mobTotal,
            mobTotal,
            0,
            1,
            System.currentTimeMillis(),
            0,
            Instant.now(),
            null,
            false
        );
    }

    /**
     * 创建BOSS进度
     */
    public static DungeonRoomProgress createBoss(String roomId, double maxHealth) {
        return new DungeonRoomProgress(
            roomId,
            RoomStatus.IN_PROGRESS,
            0,
            0,
            0,
            1,
            System.currentTimeMillis(),
            0,
            Instant.now(),
            null,
            false
        );
    }

    /**
     * 创建谜题进度
     */
    public static DungeonRoomProgress createPuzzle(String roomId) {
        return new DungeonRoomProgress(
            roomId,
            RoomStatus.IN_PROGRESS,
            0,
            0,
            0,
            1,
            System.currentTimeMillis(),
            0,
            Instant.now(),
            null,
            false
        );
    }

    /**
     * 创建生存进度
     */
    public static DungeonRoomProgress createSurvival(String roomId, int waves) {
        return new DungeonRoomProgress(
            roomId,
            RoomStatus.IN_PROGRESS,
            0,
            0,
            0,
            waves,
            System.currentTimeMillis(),
            0,
            Instant.now(),
            null,
            false
        );
    }

    /**
     * 标记为清除
     */
    public DungeonRoomProgress cleared() {
        return new DungeonRoomProgress(
            roomId,
            RoomStatus.CLEARED,
            mobsRemaining,
            mobsTotal,
            wavesCompleted,
            wavesTotal,
            startTime,
            System.currentTimeMillis(),
            Instant.now(),
            puzzleSolution,
            true
        );
    }

    /**
     * 标记为失败
     */
    public DungeonRoomProgress failed() {
        return new DungeonRoomProgress(
            roomId,
            RoomStatus.FAILED,
            mobsRemaining,
            mobsTotal,
            wavesCompleted,
            wavesTotal,
            startTime,
            System.currentTimeMillis(),
            Instant.now(),
            puzzleSolution,
            false
        );
    }

    /**
     * 击杀怪物
     */
    public DungeonRoomProgress killMob() {
        int newRemaining = Math.max(0, mobsRemaining - 1);
        return new DungeonRoomProgress(
            roomId,
            newRemaining <= 0 ? RoomStatus.CLEARED : status,
            newRemaining,
            mobsTotal,
            wavesCompleted,
            wavesTotal,
            startTime,
            newRemaining <= 0 ? System.currentTimeMillis() : endTime,
            Instant.now(),
            puzzleSolution,
            puzzleSolved
        );
    }

    /**
     * 完成波次
     */
    public DungeonRoomProgress completeWave() {
        int newWaves = wavesCompleted + 1;
        boolean allWavesDone = newWaves >= wavesTotal;
        return new DungeonRoomProgress(
            roomId,
            allWavesDone ? RoomStatus.CLEARED : status,
            mobsRemaining,
            mobsTotal,
            newWaves,
            wavesTotal,
            startTime,
            allWavesDone ? System.currentTimeMillis() : endTime,
            Instant.now(),
            puzzleSolution,
            puzzleSolved
        );
    }

    /**
     * 获取进度百分比
     */
    public double getProgressPercentage() {
        if (status == RoomStatus.CLEARED) return 100;
        if (status == RoomStatus.FAILED) return 0;

        if (mobsTotal > 0) {
            return (double) (mobsTotal - mobsRemaining) / mobsTotal * 100;
        }
        if (wavesTotal > 0) {
            return (double) wavesCompleted / wavesTotal * 100;
        }
        if (puzzleSolved) {
            return 100;
        }
        return 0;
    }

    /**
     * 获取已用时间(秒)
     */
    public long getElapsedSeconds() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }
}
