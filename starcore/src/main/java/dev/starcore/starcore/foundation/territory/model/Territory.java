package dev.starcore.starcore.foundation.territory.model;

/**
 * 领地信息
 * 简化的领地数据，用于跨边界通知
 */
public record Territory(
    String nationName,
    String ownerId
) {
    public static Territory fromClaim(TerritoryClaim claim, String nationName) {
        return new Territory(nationName, claim.ownerId());
    }
}
