package dev.starcore.starcore.social.simulation.network;

import java.util.UUID;

/**
 * 社交网络节点
 */
public class RelationshipNode {
    private final UUID playerId;
    private double influence;

    public RelationshipNode(UUID playerId) {
        this.playerId = playerId;
        this.influence = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public double getInfluence() {
        return influence;
    }

    public void setInfluence(double influence) {
        this.influence = influence;
    }

    public void addInfluence(double delta) {
        this.influence += delta;
    }
}
