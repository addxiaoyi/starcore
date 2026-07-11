package dev.starcore.starcore.foundation.territory;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 领土服务测试
 * 确保领土操作的安全性和正确性
 */
class TerritoryServiceTest {

    private static final String NATION_1 = "nation-1-uuid";
    private static final String NATION_2 = "nation-2-uuid";
    private TestableTerritoryService territoryService;

    @BeforeEach
    void setUp() {
        territoryService = new TestableTerritoryService();
    }

    @Nested
    @DisplayName("领土认领测试")
    class ClaimTests {

        @Test
        @DisplayName("新领土应可以认领")
        void newTerritoryCanBeClaimed() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 0, 0);
            boolean result = territoryService.claim(NATION_1, chunk);
            assertTrue(result);
        }

        @Test
        @DisplayName("已认领领土不应重复认领")
        void alreadyClaimedTerritoryCannotBeReclaimed() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 0, 0);
            territoryService.claim(NATION_1, chunk);
            boolean result = territoryService.claim(NATION_2, chunk);
            assertFalse(result);
        }

        @Test
        @DisplayName("应能获取领土所有者")
        void shouldGetTerritoryOwner() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 0, 0);
            territoryService.claim(NATION_1, chunk);
            Optional<TerritoryClaim> claim = territoryService.claimAt(chunk);
            assertTrue(claim.isPresent());
            assertEquals(NATION_1, claim.get().ownerId());
        }

        @Test
        @DisplayName("未认领区域应返回空")
        void unclaimedAreaShouldReturnEmpty() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 999, 999);
            Optional<TerritoryClaim> claim = territoryService.claimAt(chunk);
            assertTrue(claim.isEmpty());
        }

        @Test
        @DisplayName("isClaimed应正确反映认领状态")
        void isClaimedShouldReflectClaimStatus() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 0, 0);
            assertFalse(territoryService.isClaimed(chunk));
            territoryService.claim(NATION_1, chunk);
            assertTrue(territoryService.isClaimed(chunk));
        }
    }

    @Nested
    @DisplayName("领土所有权测试")
    class OwnershipTests {

        @Test
        @DisplayName("应能检查领土所有权")
        void shouldCheckOwnership() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 0, 0);
            territoryService.claim(NATION_1, chunk);
            assertTrue(territoryService.isOwnerOf(NATION_1, chunk));
            assertFalse(territoryService.isOwnerOf(NATION_2, chunk));
        }

        @Test
        @DisplayName("未认领区域所有权检查应返回false")
        void unclaimedOwnershipCheckShouldReturnFalse() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 999, 999);
            assertFalse(territoryService.isOwnerOf(NATION_1, chunk));
        }
    }

    @Nested
    @DisplayName("领土移除测试")
    class UnclaimTests {

        @Test
        @DisplayName("应能移除领土")
        void shouldUnclaimTerritory() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 0, 0);
            territoryService.claim(NATION_1, chunk);
            boolean result = territoryService.unclaim(NATION_1, chunk);
            assertTrue(result);
            assertTrue(territoryService.claimAt(chunk).isEmpty());
        }

        @Test
        @DisplayName("非所有者不能移除领土")
        void nonOwnerCannotUnclaim() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 0, 0);
            territoryService.claim(NATION_1, chunk);
            boolean result = territoryService.unclaim(NATION_2, chunk);
            assertFalse(result);
            assertTrue(territoryService.claimAt(chunk).isPresent());
        }

        @Test
        @DisplayName("移除未认领区域应返回false")
        void unclaimUnclaimedAreaShouldReturnFalse() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", 999, 999);
            boolean result = territoryService.unclaim(NATION_1, chunk);
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("国家领土列表测试")
    class NationTerritoriesTests {

        @Test
        @DisplayName("应能获取国家的所有领土")
        void shouldGetNationTerritories() {
            territoryService.claim(NATION_1, new ChunkCoordinate("world", 0, 0));
            territoryService.claim(NATION_1, new ChunkCoordinate("world", 1, 0));
            territoryService.claim(NATION_2, new ChunkCoordinate("world", 0, 1));

            Collection<TerritoryClaim> claims = territoryService.claimsByOwner(NATION_1);
            assertEquals(2, claims.size());
        }

        @Test
        @DisplayName("无领土国家应返回空列表")
        void nationWithNoTerritoriesShouldReturnEmpty() {
            Collection<TerritoryClaim> claims = territoryService.claimsByOwner(NATION_1);
            assertTrue(claims.isEmpty());
        }
    }

    @Nested
    @DisplayName("边界检查测试")
    class BoundaryTests {

        @Test
        @DisplayName("不同世界应有独立领土")
        void differentWorldsShouldHaveSeparateTerritories() {
            ChunkCoordinate world1Chunk = new ChunkCoordinate("world", 0, 0);
            ChunkCoordinate world2Chunk = new ChunkCoordinate("world_nether", 0, 0);

            territoryService.claim(NATION_1, world1Chunk);
            territoryService.claim(NATION_2, world2Chunk);

            assertTrue(territoryService.isOwnerOf(NATION_1, world1Chunk));
            assertTrue(territoryService.isOwnerOf(NATION_2, world2Chunk));
        }
    }

    @Nested
    @DisplayName("安全性测试")
    class SecurityTests {

        @Test
        @DisplayName("空世界名应安全处理")
        void emptyWorldNameShouldBeHandledSafely() {
            ChunkCoordinate chunk = new ChunkCoordinate("", 0, 0);
            assertTrue(territoryService.claimAt(chunk).isEmpty());
        }

        @Test
        @DisplayName("负坐标应安全处理")
        void negativeCoordinatesShouldBeHandledSafely() {
            ChunkCoordinate chunk = new ChunkCoordinate("world", -100, -100);
            territoryService.claim(NATION_1, chunk);
            assertTrue(territoryService.isOwnerOf(NATION_1, chunk));
        }

        @Test
        @DisplayName("claimCountOf应返回正确数量")
        void claimCountOfShouldReturnCorrectCount() {
            territoryService.claim(NATION_1, new ChunkCoordinate("world", 0, 0));
            territoryService.claim(NATION_1, new ChunkCoordinate("world", 1, 0));
            territoryService.claim(NATION_1, new ChunkCoordinate("world", 2, 0));
            assertEquals(3, territoryService.claimCountOf(NATION_1));
        }
    }

    /**
     * 可测试的领土服务实现
     */
    static class TestableTerritoryService implements TerritoryService {
        private final java.util.Map<String, TerritoryClaim> claims = new java.util.HashMap<>();

        private String key(ChunkCoordinate chunk) {
            return chunk.world() + ":" + chunk.x() + ":" + chunk.z();
        }

        @Override
        public Optional<TerritoryClaim> claimAt(ChunkCoordinate coordinate) {
            return Optional.ofNullable(claims.get(key(coordinate)));
        }

        @Override
        public boolean isClaimed(ChunkCoordinate coordinate) {
            return claims.containsKey(key(coordinate));
        }

        @Override
        public boolean claim(String ownerId, ChunkCoordinate coordinate) {
            String k = key(coordinate);
            if (claims.containsKey(k)) {
                return false;
            }
            claims.put(k, new TerritoryClaim(ownerId, coordinate));
            return true;
        }

        @Override
        public boolean unclaim(String ownerId, ChunkCoordinate coordinate) {
            String k = key(coordinate);
            TerritoryClaim claim = claims.get(k);
            if (claim == null || !claim.ownerId().equals(ownerId)) {
                return false;
            }
            claims.remove(k);
            return true;
        }

        @Override
        public int claimedChunkCount() {
            return claims.size();
        }

        @Override
        public Collection<TerritoryClaim> claimsByOwner(String ownerId) {
            return claims.values().stream()
                .filter(c -> c.ownerId().equals(ownerId))
                .toList();
        }
    }
}
