package dev.starcore.starcore.social.simulation.network;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 社交网络
 */
public class SocialNetwork {
    private final Map<UUID, RelationshipNode> nodes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> edges = new ConcurrentHashMap<>();

    public void addNode(UUID playerId) {
        nodes.putIfAbsent(playerId, new RelationshipNode(playerId));
        edges.putIfAbsent(playerId, new HashSet<>());
    }

    public void addEdge(UUID from, UUID to) {
        addNode(from);
        addNode(to);
        edges.get(from).add(to);
        edges.get(to).add(from);
    }

    public RelationshipNode getNode(UUID playerId) {
        return nodes.get(playerId);
    }

    public Set<UUID> getNeighbors(UUID playerId) {
        return edges.getOrDefault(playerId, Set.of());
    }

    public Set<UUID> getAllNodes() {
        return new HashSet<>(nodes.keySet());
    }
}
