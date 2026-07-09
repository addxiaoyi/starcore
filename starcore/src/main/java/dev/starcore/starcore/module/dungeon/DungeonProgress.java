package dev.starcore.starcore.module.dungeon;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 副本进度
 * 跟踪玩家在副本中的整体进度
 */
public class DungeonProgress {
    private final UUID playerId;
    private final UUID instanceId;
    private final String dungeonId;
    private String currentRoomId;
    private int currentRoomIndex;
    private int completedRooms;
    private int failedRooms;
    private long startTime;
    private long endTime;
    private boolean alive;
    private Instant deathTime;
    private int deaths;
    private final Map<String, Integer> roomAttempts;
    private DungeonCompletionResult result;

    public DungeonProgress(UUID playerId, UUID instanceId, String dungeonId) {
        this.playerId = playerId;
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
        this.currentRoomIndex = 0;
        this.completedRooms = 0;
        this.failedRooms = 0;
        this.alive = true;
        this.roomAttempts = new HashMap<>();
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 获取玩家ID
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * 获取实例ID
     */
    public UUID getInstanceId() {
        return instanceId;
    }

    /**
     * 获取副本ID
     */
    public String getDungeonId() {
        return dungeonId;
    }

    /**
     * 获取当前房间ID
     */
    public String getCurrentRoomId() {
        return currentRoomId;
    }

    /**
     * 设置当前房间ID
     */
    public void setCurrentRoomId(String roomId) {
        this.currentRoomId = roomId;
    }

    /**
     * 获取当前房间索引
     */
    public int getCurrentRoomIndex() {
        return currentRoomIndex;
    }

    /**
     * 设置当前房间索引
     */
    public void setCurrentRoomIndex(int index) {
        this.currentRoomIndex = index;
    }

    /**
     * 增加房间索引
     */
    public void advanceRoom() {
        this.currentRoomIndex++;
    }

    /**
     * 获取已完成房间数
     */
    public int getCompletedRooms() {
        return completedRooms;
    }

    /**
     * 增加完成房间数
     */
    public void incrementCompletedRooms() {
        this.completedRooms++;
    }

    /**
     * 获取失败房间数
     */
    public int getFailedRooms() {
        return failedRooms;
    }

    /**
     * 增加失败房间数
     */
    public void incrementFailedRooms() {
        this.failedRooms++;
    }

    /**
     * 获取开始时间
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * 获取结束时间
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * 设置结束时间
     */
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    /**
     * 获取存活状态
     */
    public boolean isAlive() {
        return alive;
    }

    /**
     * 设置存活状态
     */
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    /**
     * 死亡处理
     */
    public void handleDeath() {
        this.alive = false;
        this.deathTime = Instant.now();
        this.deaths++;
    }

    /**
     * 复活处理
     */
    public void handleRespawn() {
        this.alive = true;
    }

    /**
     * 获取死亡时间
     */
    public Instant getDeathTime() {
        return deathTime;
    }

    /**
     * 获取死亡次数
     */
    public int getDeaths() {
        return deaths;
    }

    /**
     * 获取房间尝试次数
     */
    public int getRoomAttempts(String roomId) {
        return roomAttempts.getOrDefault(roomId, 0);
    }

    /**
     * 增加房间尝试次数
     */
    public void incrementRoomAttempts(String roomId) {
        roomAttempts.merge(roomId, 1, Integer::sum);
    }

    /**
     * 获取结果
     */
    public DungeonCompletionResult getResult() {
        return result;
    }

    /**
     * 设置结果
     */
    public void setResult(DungeonCompletionResult result) {
        this.result = result;
        this.endTime = System.currentTimeMillis();
    }

    /**
     * 获取总用时(秒)
     */
    public long getTotalSeconds() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    /**
     * 计算完成百分比
     */
    public double getCompletionPercentage(int totalRooms) {
        if (totalRooms <= 0) return 0;
        return (double) completedRooms / totalRooms * 100;
    }
}
