package dev.starcore.starcore.module.nation.listener;

import dev.starcore.starcore.foundation.feedback.InGameFeedbackService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.Territory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听玩家进入/离开领地，显示提示信息
 */
public final class TerritoryEnterListener implements Listener {
    private final TerritoryService territoryService;
    private final NationService nationService;
    private final InGameFeedbackService feedbackService;
    private final MessageService messages;

    // 记录玩家当前所在的领地，避免重复提示
    private final ConcurrentHashMap<UUID, ChunkPosition> playerLastChunk = new ConcurrentHashMap<>();

    public TerritoryEnterListener(
        TerritoryService territoryService,
        NationService nationService,
        InGameFeedbackService feedbackService,
        MessageService messages
    ) {
        this.territoryService = territoryService;
        this.nationService = nationService;
        this.feedbackService = feedbackService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨区块移动
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();

        if (fromChunk.equals(toChunk)) {
            return;
        }

        Player player = event.getPlayer();
        ChunkPosition fromPos = ChunkPosition.of(fromChunk);
        ChunkPosition toPos = ChunkPosition.of(toChunk);

        // 检查玩家上次记录的区块
        ChunkPosition lastPos = playerLastChunk.put(player.getUniqueId(), toPos);

        // 如果上次记录和本次相同，跳过（理论上不会发生）
        if (toPos.equals(lastPos)) {
            return;
        }

        // 获取新区块的领地信息
        Optional<Territory> toTerritory = getTerritoryFromChunk(toChunk);
        Optional<Territory> fromTerritory = lastPos != null ?
            getTerritoryFromPosition(fromPos) : Optional.empty();

        // 判断是否进入了不同的领地
        if (isSameTerritory(fromTerritory, toTerritory)) {
            return;
        }

        // 显示领地提示
        showTerritoryNotification(player, toTerritory);
    }

    private boolean isSameTerritory(Optional<Territory> from, Optional<Territory> to) {
        if (from.isEmpty() && to.isEmpty()) {
            return true;
        }
        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }
        return from.get().nationName().equals(to.get().nationName());
    }

    private void showTerritoryNotification(Player player, Optional<Territory> territory) {
        if (territory.isEmpty()) {
            // 进入荒野
            feedbackService.emit("territory.enter.wilderness", player, player.getLocation());
            return;
        }

        String nationName = territory.get().nationName();
        Optional<Nation> nation = nationService.getNationByName(nationName);

        if (nation.isEmpty()) {
            return;
        }

        // 判断玩家与该国家的关系
        String relationKey = determineRelationKey(player, nation.get());

        // 发送视觉反馈（Title + 粒子 + 音效）
        feedbackService.emit("territory.enter." + relationKey, player, player.getLocation());
    }

    private String determineRelationKey(Player player, Nation nation) {
        UUID playerId = player.getUniqueId();

        // 检查玩家是否是该国家成员
        if (nation.isMember(playerId)) {
            return "own";  // 自己的领地
        }

        // 检查玩家所属国家
        Optional<Nation> playerNation = nationService.getNationByMember(playerId);

        if (playerNation.isEmpty()) {
            return "neutral";  // 无国家玩家进入
        }

        // 检查外交关系（需要外交模块支持）
        // 这里简化处理，后续可以接入外交模块
        return "neutral";
    }

    private Optional<Territory> getTerritoryFromChunk(Chunk chunk) {
        // Convert Chunk to ChunkCoordinate and get territory claim
        var coord = new dev.starcore.starcore.foundation.territory.model.ChunkCoordinate(
            chunk.getWorld().getName(),
            chunk.getX(),
            chunk.getZ()
        );
        var claim = territoryService.claimAt(coord);
        if (claim.isEmpty()) {
            return Optional.empty();
        }
        // Create Territory from claim
        return Optional.of(new Territory(claim.get().ownerId(), chunk.getWorld().getName()));
    }

    private Optional<Territory> getTerritoryFromPosition(ChunkPosition pos) {
        var coord = new dev.starcore.starcore.foundation.territory.model.ChunkCoordinate(
            pos.world,
            pos.x,
            pos.z
        );
        var claim = territoryService.claimAt(coord);
        if (claim.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Territory(claim.get().ownerId(), pos.world));
    }

    /**
     * 玩家退出时清理记录
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        playerLastChunk.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 区块位置记录
     */
    private static final class ChunkPosition {
        final String world;
        final int x;
        final int z;

        private ChunkPosition(String world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        static ChunkPosition of(Chunk chunk) {
            return new ChunkPosition(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkPosition other)) return false;
            return x == other.x && z == other.z && world.equals(other.world);
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + z;
            return result;
        }
    }
}
