package dev.starcore.starcore.module.army.tunnel.listener;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.tunnel.TunnelService;
import dev.starcore.starcore.module.army.tunnel.event.PlayerEnterTunnelEvent;
import dev.starcore.starcore.module.army.tunnel.event.PlayerExitTunnelEvent;
import dev.starcore.starcore.module.army.tunnel.model.Tunnel;
import dev.starcore.starcore.module.nation.NationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tunnel Warfare Listener
 * Handles player interactions and movement in tunnel areas
 */
public final class TunnelListener implements Listener {

    private final TunnelService tunnelService;
    private final NationService nationService;
    private final MessageService messages;

    // Track players near tunnel entrances for visual effects
    private final ConcurrentHashMap<UUID, Long> nearEntranceCooldown = new ConcurrentHashMap<>();
    private static final long ENTRANCE_EFFECT_COOLDOWN_MS = 5000;

    public TunnelListener(TunnelService tunnelService, NationService nationService, MessageService messages) {
        this.tunnelService = tunnelService;
        this.nationService = nationService;
        this.messages = messages;
    }

    // ==================== Player Events ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // Remove player from tunnel tracking
        Optional<UUID> currentTunnel = tunnelService.getPlayerTunnel(playerId);
        currentTunnel.ifPresent(tunnelId -> {
            tunnelService.exitTunnel(playerId);
        });

        nearEntranceCooldown.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process block movement, ignore head rotation
        if (isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player is in a tunnel
        Optional<UUID> currentTunnel = tunnelService.getPlayerTunnel(playerId);
        if (currentTunnel.isPresent()) {
            handlePlayerInTunnelMove(player, currentTunnel.get());
            return;
        }

        // Check if player is near a tunnel entrance
        handlePlayerNearEntrance(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // If player is in a tunnel, exit it on teleport
        Optional<UUID> currentTunnel = tunnelService.getPlayerTunnel(playerId);
        if (currentTunnel.isPresent()) {
            tunnelService.exitTunnel(playerId);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player is in a tunnel and clicks a block
        Optional<UUID> currentTunnel = tunnelService.getPlayerTunnel(playerId);
        if (currentTunnel.isPresent() && event.getClickedBlock() != null) {
            handleTunnelBlockInteraction(player, currentTunnel.get(), event);
        }
    }

    // ==================== Combat Events ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }

        UUID defenderId = defender.getUniqueId();

        // Check if defender is in a tunnel (defense bonus)
        Optional<UUID> defenderTunnel = tunnelService.getPlayerTunnel(defenderId);
        if (defenderTunnel.isPresent()) {
            double defenseBonus = tunnelService.getTunnelDefenseBonus(defenderTunnel.get());
            if (defenseBonus > 0) {
                // Apply defense bonus (reduce damage)
                double originalDamage = event.getFinalDamage();
                double reducedDamage = originalDamage * (1.0 - defenseBonus);
                event.setDamage(reducedDamage);

                // Visual feedback
                defender.sendActionBar(Component.text(
                    messages.format("tunnel.defense.bonus", String.format("%.0f%%", defenseBonus * 100)),
                    NamedTextColor.GREEN
                ));
            }
        }
    }

    // ==================== Tunnel Entry/Exit Events ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerEnterTunnel(PlayerEnterTunnelEvent event) {
        Player player = event.getPlayer();
        Tunnel tunnel = event.getTunnel();

        // Send title notification using Adventure API
        player.showTitle(net.kyori.adventure.title.Title.title(
            Component.text("进入地道", NamedTextColor.DARK_GRAY),
            Component.text(tunnel.type().displayName() + " - " + tunnel.name()),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(500),
                java.time.Duration.ofSeconds(2),
                java.time.Duration.ofMillis(500)
            )
        ));

        // Play sound
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 0.5f);

        // Spawn particles
        player.spawnParticle(Particle.PORTAL, player.getLocation(), 30);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerExitTunnel(PlayerExitTunnelEvent event) {
        Player player = event.getPlayer();

        // Send title notification using Adventure API
        player.showTitle(net.kyori.adventure.title.Title.title(
            Component.text("离开地道", NamedTextColor.DARK_GRAY),
            Component.text("已安全返回地面"),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(500),
                java.time.Duration.ofSeconds(2),
                java.time.Duration.ofMillis(500)
            )
        ));

        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Spawn particles
        player.spawnParticle(Particle.PORTAL, player.getLocation(), 30);
    }

    // ==================== Private Helpers ====================

    private void handlePlayerInTunnelMove(Player player, UUID tunnelId) {
        // In-tunnel movement effects (subtle)
        if (ThreadLocalRandom.current().nextDouble() < 0.02) { // 2% chance per movement
            player.spawnParticle(Particle.DUST, player.getLocation().add(0, 0.5, 0), 3);
        }
    }

    private void handlePlayerNearEntrance(Player player) {
        UUID playerId = player.getUniqueId();

        // Check cooldown
        long now = System.currentTimeMillis();
        Long lastEffect = nearEntranceCooldown.get(playerId);
        if (lastEffect != null && now - lastEffect < ENTRANCE_EFFECT_COOLDOWN_MS) {
            return;
        }

        // Check if near any tunnel entrance
        var tunnels = tunnelService.getTunnelsAt(player.getLocation());
        if (!tunnels.isEmpty()) {
            Tunnel nearest = tunnels.get(0);

            // Check if it's the player's own nation's tunnel
            var nation = nationService.nationOf(playerId);
            boolean isOwner = nation.isPresent() &&
                nation.get().id().value().equals(nearest.nationId().value());

            if (isOwner) {
                // Show subtle hint for own tunnels
                nearEntranceCooldown.put(playerId, now);
            }
        }
    }

    private void handleTunnelBlockInteraction(Player player, UUID tunnelId, PlayerInteractEvent event) {
        // Prevent block interactions in certain tunnel types for balance
        Optional<Tunnel> tunnelOpt = tunnelService.getTunnel(tunnelId);
        if (tunnelOpt.isEmpty()) {
            return;
        }

        Tunnel tunnel = tunnelOpt.get();

        // Secret tunnels prevent block breaking by non-owners
        if (tunnel.isSecret()) {
            event.setCancelled(true);
        }
    }

    private boolean isSameBlock(org.bukkit.Location from, org.bukkit.Location to) {
        if (from == null || to == null) {
            return false;
        }
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld());
    }
}
