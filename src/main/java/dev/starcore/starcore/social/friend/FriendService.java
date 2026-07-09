package dev.starcore.starcore.social.friend;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 好友服务（星链系统）
 *
 * 支持两种持久化模式：
 * 1. YAML 文件持久化（默认，兼容性更好）
 * 2. 数据库持久化（通过 SqlSocialStateStorage）
 */
public final class FriendService {
    // 好友关系（玩家UUID -> 好友UUID列表）
    private final Map<UUID, Set<UUID>> friendships = new ConcurrentHashMap<>();

    // 好友请求（目标UUID -> 请求者UUID列表）
    private final Map<UUID, Set<UUID>> friendRequests = new ConcurrentHashMap<>();

    // 黑名单（玩家UUID -> 黑名单UUID列表）
    private final Map<UUID, Set<UUID>> blacklist = new ConcurrentHashMap<>();

    // 在线状态追踪（玩家UUID -> 是否在线）
    private final Map<UUID, Boolean> onlineStatus = new ConcurrentHashMap<>();

    // 好友上限
    private final int maxFriends = 100;

    // 数据库存储引用（可选）
    private dev.starcore.starcore.social.SqlSocialStateStorage sqlStorage;

    /**
     * 设置数据库存储（用于数据库持久化）
     */
    public void setSqlStorage(dev.starcore.starcore.social.SqlSocialStateStorage sqlStorage) {
        this.sqlStorage = sqlStorage;
    }

    /**
     * 检查数据库是否可用
     */
    public boolean isDatabaseAvailable() {
        return sqlStorage != null && sqlStorage.isDatabaseAvailable();
    }

    /**
     * 发送好友请求
     */
    public boolean sendFriendRequest(UUID senderId, UUID targetId) {
        // 不能添加自己
        if (senderId.equals(targetId)) {
            throw new IllegalArgumentException("不能添加自己为好友");
        }

        // 检查是否已经是好友
        if (isFriend(senderId, targetId)) {
            throw new IllegalStateException("你们已经是好友了");
        }

        // 检查双向黑名单：目标拉黑发送者 或 发送者拉黑目标，均拒绝
        if (isBlacklisted(targetId, senderId) || isBlacklisted(senderId, targetId)) {
            throw new IllegalStateException("无法发送好友请求");
        }

        // 检查反向请求：若 target 已向 sender 发出请求，自动结为好友
        Set<UUID> reverse = friendRequests.get(senderId);
        if (reverse != null && reverse.remove(targetId)) {
            // 互为请求，直接建立好友关系
            return acceptFriendRequest(senderId, targetId);
        }

        // 检查好友上限
        if (getFriendCount(senderId) >= maxFriends) {
            throw new IllegalStateException("好友数量已达上限");
        }

        // 添加请求
        friendRequests.computeIfAbsent(targetId, k -> new CopyOnWriteArraySet<>())
            .add(senderId);

        // 持久化请求（DB 模式即时落盘，避免重启丢失）
        saveFriendRequestToDatabase(targetId, senderId);

        return true;
    }

    /**
     * 接受好友请求
     */
    public boolean acceptFriendRequest(UUID playerId, UUID requesterId) {
        // 检查请求是否存在 && 原子移除（CopyOnWriteArraySet.remove 是 CAS 语义）
        Set<UUID> requests = friendRequests.get(playerId);
        if (requests == null || !requests.remove(requesterId)) {
            throw new IllegalStateException("好友请求不存在或已被处理");
        }

        // 双方均校验上限，避免单边好友
        if (getFriendCount(playerId) >= maxFriends) {
            // 回滚请求
            requests.add(requesterId);
            throw new IllegalStateException("你的好友数量已达上限");
        }
        if (getFriendCount(requesterId) >= maxFriends) {
            requests.add(requesterId);
            throw new IllegalStateException("对方好友数量已达上限");
        }

        // 添加好友关系（双向）——CopyOnWriteArraySet 去重，重复加入幂等
        friendships.computeIfAbsent(playerId, k -> new CopyOnWriteArraySet<>()).add(requesterId);
        friendships.computeIfAbsent(requesterId, k -> new CopyOnWriteArraySet<>()).add(playerId);

        // 持久化：删除请求、写入双向好友关系
        deleteFriendRequestFromDatabase(playerId, requesterId);
        saveFriendToDatabase(playerId, requesterId);
        saveFriendToDatabase(requesterId, playerId);

        return true;
    }

    /**
     * 拒绝好友请求
     */
    public boolean rejectFriendRequest(UUID playerId, UUID requesterId) {
        Set<UUID> requests = friendRequests.get(playerId);
        boolean removed = requests != null && requests.remove(requesterId);
        if (removed) {
            deleteFriendRequestFromDatabase(playerId, requesterId);
        }
        return removed;
    }

    /**
     * 删除好友
     */
    public boolean removeFriend(UUID playerId, UUID friendId) {
        boolean removed1 = false;
        boolean removed2 = false;

        Set<UUID> friends1 = friendships.get(playerId);
        if (friends1 != null) {
            removed1 = friends1.remove(friendId);
        }

        Set<UUID> friends2 = friendships.get(friendId);
        if (friends2 != null) {
            removed2 = friends2.remove(playerId);
        }

        if (removed1 || removed2) {
            // 持久化删除双向好友记录
            deleteFriendFromDatabase(playerId, friendId);
        }
        return removed1 || removed2;
    }

    /**
     * 检查是否是好友
     */
    public boolean isFriend(UUID playerId, UUID targetId) {
        Set<UUID> friends = friendships.get(playerId);
        return friends != null && friends.contains(targetId);
    }

    /**
     * 获取好友列表
     */
    public Set<UUID> getFriends(UUID playerId) {
        Set<UUID> friends = friendships.get(playerId);
        return friends != null ? new HashSet<>(friends) : Collections.emptySet();
    }

    /**
     * 获取好友数量
     */
    public int getFriendCount(UUID playerId) {
        Set<UUID> friends = friendships.get(playerId);
        return friends != null ? friends.size() : 0;
    }

    /**
     * 获取好友请求列表
     */
    public Set<UUID> getFriendRequests(UUID playerId) {
        Set<UUID> requests = friendRequests.get(playerId);
        return requests != null ? new HashSet<>(requests) : Collections.emptySet();
    }

    /**
     * 添加到黑名单
     */
    public boolean addToBlacklist(UUID playerId, UUID targetId) {
        // 如果是好友，先删除（含DB）
        removeFriend(playerId, targetId);

        blacklist.computeIfAbsent(playerId, k -> new CopyOnWriteArraySet<>())
            .add(targetId);

        saveBlacklistToDatabase(playerId, targetId);
        return true;
    }

    /**
     * 从黑名单移除
     */
    public boolean removeFromBlacklist(UUID playerId, UUID targetId) {
        Set<UUID> list = blacklist.get(playerId);
        boolean removed = list != null && list.remove(targetId);
        if (removed) {
            deleteBlacklistFromDatabase(playerId, targetId);
        }
        return removed;
    }

    /**
     * 检查是否在黑名单
     */
    public boolean isBlacklisted(UUID playerId, UUID targetId) {
        Set<UUID> list = blacklist.get(playerId);
        return list != null && list.contains(targetId);
    }

    /**
     * 获取黑名单
     */
    public Set<UUID> getBlacklist(UUID playerId) {
        Set<UUID> list = blacklist.get(playerId);
        return list != null ? new HashSet<>(list) : Collections.emptySet();
    }

    /**
     * 获取在线好友
     */
    public Set<UUID> getOnlineFriends(UUID playerId, Set<UUID> onlinePlayers) {
        Set<UUID> friends = getFriends(playerId);
        Set<UUID> online = new HashSet<>();

        for (UUID friendId : friends) {
            if (onlinePlayers.contains(friendId)) {
                online.add(friendId);
            }
        }

        return online;
    }

    /**
     * 清理玩家数据
     */
    public void cleanup(UUID playerId) {
        friendships.remove(playerId);
        friendRequests.remove(playerId);
        blacklist.remove(playerId);
        onlineStatus.remove(playerId);

        // 反向清理其他玩家 map 中的残留引用，避免单向死 UUID
        for (Set<UUID> friends : friendships.values()) {
            friends.remove(playerId);
        }
        for (Set<UUID> reqs : friendRequests.values()) {
            reqs.remove(playerId);
        }
        for (Set<UUID> bl : blacklist.values()) {
            bl.remove(playerId);
        }
    }

    // ========== 持久化支持 ==========

    /**
     * 导出所有好友关系（用于保存）。返回 玩家 -> 好友集合 的快照。
     */
    public Map<UUID, Set<UUID>> exportFriendships() {
        Map<UUID, Set<UUID>> snapshot = new java.util.HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : friendships.entrySet()) {
            snapshot.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        return snapshot;
    }

    /**
     * 直接建立双向好友关系（用于加载，跳过校验）。
     */
    public void loadFriendship(UUID a, UUID b) {
        friendships.computeIfAbsent(a, k -> new CopyOnWriteArraySet<>()).add(b);
        friendships.computeIfAbsent(b, k -> new CopyOnWriteArraySet<>()).add(a);
    }

    /**
     * 从数据库加载所有好友关系
     */
    public void loadFromDatabase() {
        if (sqlStorage != null) {
            sqlStorage.loadAllFriendRelations(friendships);
        }
    }

    /**
     * 保存单个好友关系到数据库
     */
    public void saveFriendToDatabase(UUID playerId, UUID friendId) {
        if (sqlStorage != null) {
            sqlStorage.saveFriendRelation(playerId, friendId, System.currentTimeMillis());
        }
    }

    /**
     * 从数据库删除单个好友关系
     */
    public void deleteFriendFromDatabase(UUID playerId, UUID friendId) {
        if (sqlStorage != null) {
            sqlStorage.deleteFriendRelation(playerId, friendId);
        }
    }

    /**
     * 批量保存所有好友关系到数据库
     */
    public void saveAllToDatabase() {
        if (sqlStorage != null) {
            sqlStorage.saveAllFriends(friendships);
        }
    }

    // ========== 好友请求持久化 ==========

    /**
     * 导出所有好友请求（目标UUID -> 请求者UUID列表）
     */
    public Map<UUID, Set<UUID>> exportFriendRequests() {
        Map<UUID, Set<UUID>> snapshot = new java.util.HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : friendRequests.entrySet()) {
            snapshot.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        return snapshot;
    }

    /**
     * 加载单个好友请求（用于恢复数据，跳过校验）
     */
    public void loadFriendRequest(UUID targetId, UUID requesterId) {
        friendRequests.computeIfAbsent(targetId, k -> new CopyOnWriteArraySet<>()).add(requesterId);
    }

    /**
     * 从数据库加载所有好友请求
     */
    public void loadFriendRequestsFromDatabase() {
        if (sqlStorage != null) {
            sqlStorage.loadAllFriendRequests(friendRequests);
        }
    }

    /**
     * 保存单个好友请求到数据库
     */
    public void saveFriendRequestToDatabase(UUID targetId, UUID senderId) {
        if (sqlStorage != null) {
            sqlStorage.saveFriendRequest(targetId, senderId, System.currentTimeMillis());
        }
    }

    /**
     * 从数据库删除单个好友请求
     */
    public void deleteFriendRequestFromDatabase(UUID targetId, UUID senderId) {
        if (sqlStorage != null) {
            sqlStorage.deleteFriendRequest(targetId, senderId);
        }
    }

    /**
     * 清除所有待处理的好友请求
     */
    public void clearFriendRequests(UUID playerId) {
        friendRequests.remove(playerId);
    }

    // ========== 黑名单持久化 ==========

    /**
     * 导出所有黑名单（玩家UUID -> 黑名单UUID列表）
     */
    public Map<UUID, Set<UUID>> exportBlacklist() {
        Map<UUID, Set<UUID>> snapshot = new java.util.HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : blacklist.entrySet()) {
            snapshot.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        return snapshot;
    }

    /**
     * 加载单个黑名单条目（用于恢复数据）
     */
    public void loadBlacklistEntry(UUID playerId, UUID blockedId) {
        blacklist.computeIfAbsent(playerId, k -> new CopyOnWriteArraySet<>()).add(blockedId);
    }

    /**
     * 从数据库加载所有黑名单
     */
    public void loadBlacklistFromDatabase() {
        if (sqlStorage != null) {
            sqlStorage.loadAllBlacklist(blacklist);
        }
    }

    /**
     * 保存单个黑名单条目到数据库
     */
    public void saveBlacklistToDatabase(UUID playerId, UUID blockedId) {
        if (sqlStorage != null) {
            sqlStorage.saveBlacklistEntry(playerId, blockedId, System.currentTimeMillis());
        }
    }

    /**
     * 从数据库删除单个黑名单条目
     */
    public void deleteBlacklistFromDatabase(UUID playerId, UUID blockedId) {
        if (sqlStorage != null) {
            sqlStorage.deleteBlacklistEntry(playerId, blockedId);
        }
    }

    /**
     * 清除玩家的黑名单
     */
    public void clearBlacklist(UUID playerId) {
        blacklist.remove(playerId);
    }

    // ========== 在线状态追踪持久化 ==========

    /**
     * 导出所有在线状态数据（UUID -> true/false）
     * 用于保存玩家最后在线状态
     */
    public Map<UUID, Boolean> exportOnlineStatus() {
        return new java.util.HashMap<>(onlineStatus);
    }

    /**
     * 加载玩家在线状态（用于恢复数据）
     */
    public void loadOnlineStatus(UUID playerId, boolean isOnline) {
        onlineStatus.put(playerId, isOnline);
    }

    /**
     * 设置玩家在线状态。调用方应在监听 PlayerJoin/PlayerQuit 事件时同步调用，
     * 否则 onlineStatus 会陈旧。同时持久化到 DB 以保证多服一致。
     */
    public void setOnlineStatus(UUID playerId, boolean isOnline) {
        onlineStatus.put(playerId, isOnline);
        saveOnlineStatusToDatabase(playerId, isOnline);
    }

    /**
     * 获取玩家在线状态
     */
    public boolean getOnlineStatus(UUID playerId) {
        return onlineStatus.getOrDefault(playerId, false);
    }

    /**
     * 从数据库加载所有玩家在线状态
     */
    public void loadOnlineStatusFromDatabase() {
        if (sqlStorage != null) {
            sqlStorage.loadAllPlayerStatus(onlineStatus);
        }
    }

    /**
     * 保存单个玩家在线状态到数据库
     */
    public void saveOnlineStatusToDatabase(UUID playerId, boolean isOnline) {
        if (sqlStorage != null) {
            sqlStorage.savePlayerStatus(playerId, isOnline);
        }
    }

    /**
     * 获取所有在线状态（导出用）
     */
    public Map<UUID, Boolean> getAllOnlineStatus() {
        return new java.util.HashMap<>(onlineStatus);
    }
}
