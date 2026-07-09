package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.social.friend.FriendService;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 关系网络包装器
 * 将 FriendService 包装为 RelationshipNetwork 接口
 * 用于数据库不可用时的降级方案
 */
public class RelationshipNetworkWrapper implements RelationshipNetwork {

    private final FriendService friendService;

    public RelationshipNetworkWrapper(FriendService friendService) {
        this.friendService = friendService;
    }

    @Override
    public Relationship getRelationship(UUID player1, UUID player2) {
        Set<UUID> friends = friendService.getFriends(player1);
        boolean isFriend = friends.contains(player2);

        RelationshipType type = isFriend ? RelationshipType.FRIEND : RelationshipType.STRANGER;
        int strength = isFriend ? 50 : 0;

        return new Relationship(
            player1, player2, type, strength,
            System.currentTimeMillis(), System.currentTimeMillis(),
            new ArrayList<>()
        );
    }

    @Override
    public Map<UUID, Relationship> getAllRelationships(UUID playerId) {
        Map<UUID, Relationship> result = new HashMap<>();
        Set<UUID> friends = friendService.getFriends(playerId);

        for (UUID friendId : friends) {
            result.put(friendId, getRelationship(playerId, friendId));
        }

        return result;
    }

    @Override
    public Set<UUID> getSocialCircle(UUID playerId, int minStrength) {
        Set<UUID> friends = friendService.getFriends(playerId);
        // 好友关系默认为50强度，超过minStrength的都会被包含
        return friends.stream()
            .filter(f -> 50 >= minStrength)
            .collect(Collectors.toSet());
    }

    @Override
    public void setRelationship(UUID player1, UUID player2, RelationshipType type, int strength) {
        // 降级模式下不持久化关系
        // 如果是好友类型且强度足够，直接添加到好友关系
        if (type == RelationshipType.FRIEND && strength >= 50) {
            // 使用反射或直接添加来添加好友关系
            addFriendDirectly(player1, player2);
            addFriendDirectly(player2, player1);
        } else if (strength < 20) {
            // 低强度关系移除好友
            removeFriendDirectly(player1, player2);
            removeFriendDirectly(player2, player1);
        }
    }

    private void addFriendDirectly(UUID playerId, UUID friendId) {
        try {
            // 直接访问 friendships map
            java.lang.reflect.Field field = FriendService.class.getDeclaredField("friendships");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, java.util.Set<UUID>> friendships = (Map<UUID, java.util.Set<UUID>>) field.get(friendService);
            friendships.computeIfAbsent(playerId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(friendId);
        } catch (Exception e) {
            // 降级模式下忽略错误
        }
    }

    private void removeFriendDirectly(UUID playerId, UUID friendId) {
        try {
            // 直接访问 friendships map
            java.lang.reflect.Field field = FriendService.class.getDeclaredField("friendships");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, java.util.Set<UUID>> friendships = (Map<UUID, java.util.Set<UUID>>) field.get(friendService);
            Set<UUID> friends = friendships.get(playerId);
            if (friends != null) {
                friends.remove(friendId);
            }
        } catch (Exception e) {
            // 降级模式下忽略错误
        }
    }

    @Override
    public void increaseStrength(UUID player1, UUID player2, int amount) {
        // 降级模式下简化为添加好友
        addFriendDirectly(player1, player2);
        addFriendDirectly(player2, player1);
    }

    @Override
    public void decreaseStrength(UUID player1, UUID player2, int amount) {
        // 降级模式下简化为移除好友
        removeFriendDirectly(player1, player2);
        removeFriendDirectly(player2, player1);
    }

    @Override
    public void removeRelationship(UUID player1, UUID player2) {
        friendService.removeFriend(player1, player2);
    }

    @Override
    public Set<UUID> getFriends(UUID playerId) {
        return friendService.getFriends(playerId);
    }

    @Override
    public Set<UUID> getEnemies(UUID playerId) {
        // 降级模式下没有敌人数据
        return Collections.emptySet();
    }

    @Override
    public Optional<UUID> getBestFriend(UUID playerId) {
        return getFriends(playerId).stream().findFirst();
    }

    @Override
    public Optional<UUID> getWorstEnemy(UUID playerId) {
        // 降级模式下没有敌人数据
        return Optional.empty();
    }

    @Override
    public int calculateInfluenceScore(UUID playerId) {
        // 影响力基于好友数量
        int friendCount = friendService.getFriendCount(playerId);
        return friendCount * 10; // 每个好友10点影响力
    }

    @Override
    public Set<UUID> getMutualFriends(UUID player1, UUID player2) {
        Set<UUID> friends1 = friendService.getFriends(player1);
        Set<UUID> friends2 = friendService.getFriends(player2);

        Set<UUID> mutual = new HashSet<>(friends1);
        mutual.retainAll(friends2);
        return mutual;
    }

    @Override
    public int getSocialDistance(UUID from, UUID to) {
        // BFS查找社交距离
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();

        visited.add(from);
        queue.offer(from);

        int distance = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();

            for (int i = 0; i < levelSize; i++) {
                UUID current = queue.poll();

                if (current.equals(to)) {
                    return distance;
                }

                // 添加好友到下一层
                Set<UUID> friends = friendService.getFriends(current);
                for (UUID friend : friends) {
                    if (!visited.contains(friend)) {
                        visited.add(friend);
                        queue.offer(friend);
                    }
                }
            }

            distance++;
        }

        return -1; // 无法到达
    }

    @Override
    public void applyDecay(long deltaMillis) {
        // 降级模式下不应用衰减
    }
}