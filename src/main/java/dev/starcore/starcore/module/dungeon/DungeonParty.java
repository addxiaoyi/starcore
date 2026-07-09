package dev.starcore.starcore.module.dungeon;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 副本队伍
 */
public class DungeonParty {
    private final UUID partyId;
    private final UUID leaderId;
    private final Set<UUID> memberIds;
    private final String dungeonId;
    private final DungeonDefinition definition;
    private final long createdAt;
    private DungeonPartyState state;
    private Map<UUID, PlayerDungeonState> playerStates;

    public DungeonParty(UUID partyId, UUID leaderId, List<Player> members, String dungeonId, DungeonDefinition definition) {
        this.partyId = partyId;
        this.leaderId = leaderId;
        this.memberIds = new HashSet<>();
        members.forEach(p -> memberIds.add(p.getUniqueId()));
        this.dungeonId = dungeonId;
        this.definition = definition;
        this.createdAt = System.currentTimeMillis();
        this.state = DungeonPartyState.FORMING;
        this.playerStates = new ConcurrentHashMap<>();
    }

    /**
     * 获取队伍ID
     */
    public UUID getPartyId() {
        return partyId;
    }

    /**
     * 获取队长ID
     */
    public UUID getLeaderId() {
        return leaderId;
    }

    /**
     * 检查是否是队长
     */
    public boolean isLeader(UUID playerId) {
        return leaderId.equals(playerId);
    }

    /**
     * 获取成员ID列表
     */
    public Set<UUID> getMemberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    /**
     * 获取成员数量
     */
    public int getMemberCount() {
        return memberIds.size();
    }

    /**
     * 添加成员
     */
    public boolean addMember(UUID playerId) {
        if (memberIds.size() < definition.maxPlayers()) {
            return memberIds.add(playerId);
        }
        return false;
    }

    /**
     * 移除成员
     */
    public boolean removeMember(UUID playerId) {
        if (playerId.equals(leaderId)) {
            // 队长不能移除自己，需要先转移队长
            return false;
        }
        return memberIds.remove(playerId);
    }

    /**
     * 检查成员是否存在
     */
    public boolean hasMember(UUID playerId) {
        return memberIds.contains(playerId);
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
     * 获取队伍状态
     */
    public DungeonPartyState getState() {
        return state;
    }

    /**
     * 设置队伍状态
     */
    public void setState(DungeonPartyState state) {
        this.state = state;
    }

    /**
     * 获取创建时间
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取玩家状态
     */
    public Map<UUID, PlayerDungeonState> getPlayerStates() {
        return Collections.unmodifiableMap(playerStates);
    }

    /**
     * 保存玩家状态
     */
    public void savePlayerState(UUID playerId, PlayerDungeonState state) {
        playerStates.put(playerId, state);
    }

    /**
     * 获取玩家状态
     */
    public PlayerDungeonState getPlayerState(UUID playerId) {
        return playerStates.get(playerId);
    }

    /**
     * 移除玩家状态
     */
    public void removePlayerState(UUID playerId) {
        playerStates.remove(playerId);
    }

    /**
     * 检查是否可以进入副本
     */
    public boolean canEnter() {
        return state == DungeonPartyState.READY &&
               memberIds.size() >= definition.minPlayers() &&
               memberIds.size() <= definition.maxPlayers();
    }

    /**
     * 检查所有成员是否存活
     */
    public boolean allMembersAlive() {
        return playerStates.values().stream()
            .allMatch(PlayerDungeonState::isAlive);
    }

    /**
     * 获取所有存活的成员
     */
    public Set<UUID> getAliveMembers() {
        Set<UUID> alive = new HashSet<>();
        for (UUID playerId : memberIds) {
            PlayerDungeonState state = playerStates.get(playerId);
            if (state != null && state.isAlive()) {
                alive.add(playerId);
            }
        }
        return alive;
    }

    @Override
    public String toString() {
        return "DungeonParty{" +
            "partyId=" + partyId +
            ", leaderId=" + leaderId +
            ", dungeonId='" + dungeonId + '\'' +
            ", memberCount=" + memberIds.size() +
            ", state=" + state +
            '}';
    }
}
