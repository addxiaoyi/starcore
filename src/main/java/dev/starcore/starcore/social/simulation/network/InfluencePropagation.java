package dev.starcore.starcore.social.simulation.network;

import java.util.*;

/**
 * 影响力传播算法
 */
public class InfluencePropagation {
    
    /**
     * 计算玩家的影响力范围（n度人脉）
     */
    public static Set<UUID> getInfluenceSphere(SocialNetwork network, UUID center, int degrees) {
        Set<UUID> result = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> distance = new HashMap<>();
        
        queue.add(center);
        distance.put(center, 0);
        result.add(center);
        
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDist = distance.get(current);
            
            if (currentDist >= degrees) continue;
            
            for (UUID neighbor : network.getNeighbors(current)) {
                if (!distance.containsKey(neighbor)) {
                    distance.put(neighbor, currentDist + 1);
                    queue.add(neighbor);
                    result.add(neighbor);
                }
            }
        }
        
        return result;
    }

    /**
     * 传播影响力
     */
    public static void propagateInfluence(SocialNetwork network, UUID source, double influence) {
        network.getNode(source).addInfluence(influence);
        
        for (UUID neighbor : network.getNeighbors(source)) {
            network.getNode(neighbor).addInfluence(influence * 0.5);
        }
    }
}
