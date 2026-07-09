package dev.starcore.starcore.module.territory.rent.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.territory.rent.TerritoryRentModule;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 租借领土权限监听器
 * 检查玩家在租借领土上的操作权限
 */
public final class TerritoryRentListener implements Listener {
    private final TerritoryRentModule rentService;
    private final NationService nationService;
    private final TerritoryService territoryService;
    private final MessageService messages;

    // Cooldown for permission denied messages
    private final ConcurrentHashMap<UUID, Long> deniedMessageCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 3000; // 3 seconds

    public TerritoryRentListener(
        TerritoryRentModule rentService,
        NationService nationService,
        TerritoryService territoryService,
        MessageService messages
    ) {
        this.rentService = rentService;
        this.nationService = nationService;
        this.territoryService = territoryService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ChunkCoordinate coord = new ChunkCoordinate(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        // Check if player has permission
        if (!canPlayerInteract(player, coord, "break")) {
            event.setCancelled(true);
            sendDeniedMessage(player, "break");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ChunkCoordinate coord = new ChunkCoordinate(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        // Check if player has permission
        if (!canPlayerInteract(player, coord, "place")) {
            event.setCancelled(true);
            sendDeniedMessage(player, "place");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getClickedBlock() == null) {
            return;
        }

        Chunk chunk = event.getClickedBlock().getChunk();
        ChunkCoordinate coord = new ChunkCoordinate(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());

        // Check if player has permission
        if (!canPlayerInteract(player, coord, "interact")) {
            event.setCancelled(true);
            sendDeniedMessage(player, "interact");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check for chunk changes, not just look direction
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();
        ChunkCoordinate fromCoord = new ChunkCoordinate(
            event.getFrom().getWorld().getName(),
            event.getFrom().getChunk().getX(),
            event.getFrom().getChunk().getZ()
        );
        ChunkCoordinate toCoord = new ChunkCoordinate(
            event.getTo().getWorld().getName(),
            event.getTo().getChunk().getX(),
            event.getTo().getChunk().getZ()
        );

        // Check if entering a leased chunk
        if (!fromCoord.equals(toCoord)) {
            Optional<UUID> leaseHolderId = getLeaseHolder(toCoord);
            if (leaseHolderId.isPresent()) {
                Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
                if (playerNation.isPresent()) {
                    UUID playerNationId = playerNation.get().id().value();
                    UUID holderId = leaseHolderId.get();

                    // Player is not the owner and not the lessee
                    if (!playerNationId.equals(holderId)) {
                        var lease = rentService.getContractForChunk(toCoord.world(), toCoord.x(), toCoord.z());
                        if (lease.isPresent() && lease.get().isActive()) {
                            var lesseeNation = nationService.nationById(new NationId(lease.get().lesseeNationId()));
                            if (lesseeNation.isEmpty() || !lesseeNation.get().id().value().equals(playerNationId)) {
                                // Check if player has nation membership with the lessee
                                if (lesseeNation.isPresent() &&
                                    nationService.nationOf(player.getUniqueId()).isPresent() &&
                                    !nationService.nationOf(player.getUniqueId()).get().id().value().equals(lesseeNation.get().id().value())) {
                                    // Player is not part of the lessee nation
                                    // You could add enter message here if needed
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if a player can interact with a chunk.
     */
    private boolean canPlayerInteract(Player player, ChunkCoordinate coord, String action) {
        // Check if chunk is claimed
        Optional<dev.starcore.starcore.foundation.territory.model.TerritoryClaim> claim =
            territoryService.claimAt(coord);

        if (claim.isEmpty()) {
            // No claim - anyone can interact
            return true;
        }

        // Get claim owner
        UUID ownerId;
        try {
            ownerId = UUID.fromString(claim.get().ownerId());
        } catch (IllegalArgumentException e) {
            return true; // Unknown format, allow
        }

        // Get player's nation
        Optional<Nation> playerNation =
            nationService.nationOf(player.getUniqueId());

        if (playerNation.isEmpty()) {
            // Player has no nation - check if chunk is leased
            return !rentService.isChunkLeased(coord.world(), coord.x(), coord.z());
        }

        NationId playerNationId = playerNation.get().id();

        // Player's nation owns the chunk
        if (ownerId.equals(playerNationId.value())) {
            return true;
        }

        // Check if player's nation is a lessee of this chunk
        if (rentService.canNationAccessChunk(playerNationId, coord.world(), coord.x(), coord.z())) {
            return true;
        }

        // Default: deny
        return false;
    }

    /**
     * Get the nation that holds the lease for a chunk.
     */
    private Optional<UUID> getLeaseHolder(ChunkCoordinate coord) {
        var lease = rentService.getContractForChunk(coord.world(), coord.x(), coord.z());
        if (lease.isEmpty() || !lease.get().isActive()) {
            return Optional.empty();
        }

        var leaseContract = lease.get();
        if (leaseContract.lesseeNationId() != null) {
            return Optional.of(leaseContract.lesseeNationId());
        }
        return Optional.empty();
    }

    /**
     * Send permission denied message with cooldown.
     */
    private void sendDeniedMessage(Player player, String action) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastSent = deniedMessageCooldowns.get(playerId);
        if (lastSent != null && now - lastSent < COOLDOWN_MS) {
            return; // Still in cooldown
        }

        deniedMessageCooldowns.put(playerId, now);

        String msg = messages.format(rentNoPermissionKey(action),
            messages.format("rent.error.no-permission"));
        player.sendMessage(net.kyori.adventure.text.Component.text(msg,
            net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    private String rentNoPermissionKey(String action) {
        return "rent.error.no-permission." + action;
    }
}
