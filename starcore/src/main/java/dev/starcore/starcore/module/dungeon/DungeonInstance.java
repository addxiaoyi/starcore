package dev.starcore.starcore.module.dungeon;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

/**
 * 副本实例
 * 代表一个正在运行的副本实例
 */
public class DungeonInstance {
    private final UUID instanceId;
    private final String dungeonId;
    private final DungeonDefinition definition;
    private final String worldName;
    private final Set<UUID> players;
    private final DungeonInstanceState state;
    private final Instant startTime;
    private Instant endTime;
    private final Map<String, DungeonRoomProgress> roomProgress;
    private final DungeonSnapshot initialSnapshot;
    private String currentRoomId;
    private String templateWorld;
    private DungeonParty party;
    private UUID nationId;

    public DungeonInstance(
        UUID instanceId,
        String dungeonId,
        DungeonDefinition definition,
        String worldName,
        Set<UUID> players,
        DungeonParty party
    ) {
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
        this.definition = definition;
        this.worldName = worldName;
        this.players = new HashSet<>(players);
        this.party = party;
        this.state = DungeonInstanceState.WAITING;
        this.startTime = Instant.now();
        this.roomProgress = new ConcurrentHashMap<>();
        this.initialSnapshot = null;
        this.templateWorld = definition.templateWorld();
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
     * 获取副本定义
     */
    public DungeonDefinition getDefinition() {
        return definition;
    }

    /**
     * 获取世界名称
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * 获取玩家列表
     */
    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    /**
     * 添加玩家
     */
    public boolean addPlayer(UUID playerId) {
        if (players.size() < definition.maxPlayers()) {
            return players.add(playerId);
        }
        return false;
    }

    /**
     * 移除玩家
     */
    public boolean removePlayer(UUID playerId) {
        return players.remove(playerId);
    }

    /**
     * 检查玩家是否在副本中
     */
    public boolean hasPlayer(UUID playerId) {
        return players.contains(playerId);
    }

    /**
     * 获取玩家数量
     */
    public int getPlayerCount() {
        return players.size();
    }

    /**
     * 获取状态
     */
    public DungeonInstanceState getState() {
        return state;
    }

    /**
     * 设置状态
     */
    public void setState(DungeonInstanceState state) {
        // State mutation handled in service
    }

    /**
     * 获取开始时间
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * 获取结束时间
     */
    public Instant getEndTime() {
        return endTime;
    }

    /**
     * 获取已用时间(秒)
     */
    public long getElapsedSeconds() {
        return java.time.Duration.between(startTime,
            endTime != null ? endTime : Instant.now()).getSeconds();
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
     * 获取房间进度
     */
    public Map<String, DungeonRoomProgress> getRoomProgress() {
        return Collections.unmodifiableMap(roomProgress);
    }

    /**
     * 获取房间进度
     */
    public DungeonRoomProgress getRoomProgress(String roomId) {
        return roomProgress.get(roomId);
    }

    /**
     * 更新房间进度
     */
    public void updateRoomProgress(String roomId, DungeonRoomProgress progress) {
        roomProgress.put(roomId, progress);
    }

    /**
     * 获取初始快照
     */
    public DungeonSnapshot getInitialSnapshot() {
        return initialSnapshot;
    }

    /**
     * 获取模板世界
     */
    public String getTemplateWorld() {
        return templateWorld;
    }

    /**
     * 获取队伍
     */
    public DungeonParty getParty() {
        return party;
    }

    /**
     * 获取国家ID
     */
    public UUID getNationId() {
        return nationId;
    }

    /**
     * 设置国家ID
     */
    public void setNationId(UUID nationId) {
        this.nationId = nationId;
    }

    /**
     * 检查是否完成
     */
    public boolean isCompleted() {
        return roomProgress.values().stream()
            .allMatch(p -> p.status() == RoomStatus.CLEARED);
    }

    /**
     * 获取下一个房间
     * @return 下一个未完成的房间，如果全部完成则返回空 Optional
     */
    public Optional<DungeonRoom> getNextRoom() {
        List<DungeonRoom> rooms = definition.rooms();
        if (rooms == null || rooms.isEmpty()) {
            return Optional.empty();
        }
        for (DungeonRoom room : rooms) {
            DungeonRoomProgress progress = roomProgress.get(room.id());
            if (progress == null || progress.status() != RoomStatus.CLEARED) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取下一个房间，如果不存在则抛出异常
     * @return 下一个房间
     * @throws IllegalStateException 如果没有可用房间
     */
    public DungeonRoom getNextRoomOrThrow() {
        return getNextRoom()
            .orElseThrow(() -> new IllegalStateException(
                "No next room available for dungeon instance: " + instanceId));
    }

    /**
     * 获取当前房间
     * @return 当前房间，如果未找到则返回空 Optional
     */
    public Optional<DungeonRoom> getCurrentRoom() {
        if (currentRoomId != null) {
            return definition.rooms().stream()
                .filter(r -> r.id().equals(currentRoomId))
                .findFirst();
        }
        return Optional.ofNullable(definition.getStartRoom());
    }

    /**
     * 获取当前房间，如果不存在则抛出异常
     * @return 当前房间
     * @throws IllegalStateException 如果没有可用房间
     */
    public DungeonRoom getCurrentRoomOrThrow() {
        return getCurrentRoom()
            .orElseThrow(() -> new IllegalStateException(
                "No current room available for dungeon instance: " + instanceId));
    }

    @Override
    public String toString() {
        return "DungeonInstance{" +
            "instanceId=" + instanceId +
            ", dungeonId='" + dungeonId + '\'' +
            ", state=" + state +
            ", players=" + players.size() +
            '}';
    }
}
