package dev.starcore.starcore.module.army.tunnel;

import dev.starcore.starcore.module.army.tunnel.model.Tunnel;
import dev.starcore.starcore.module.army.tunnel.model.TunnelState;
import dev.starcore.starcore.module.army.tunnel.model.TunnelType;
import dev.starcore.starcore.module.army.tunnel.model.TunnelEntrance;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tunnel Warfare Service Interface
 * Provides underground tunnel network management for nations
 */
public interface TunnelService {

    // ==================== Tunnel Creation ====================

    /**
     * Create a new tunnel network for a nation
     */
    Tunnel createTunnel(NationId nationId, String name, TunnelType type, Location entrance);

    /**
     * Add an entrance to an existing tunnel
     */
    TunnelEntrance addEntrance(UUID tunnelId, Location location, boolean isHidden);

    /**
     * Remove an entrance from a tunnel
     */
    boolean removeEntrance(UUID tunnelId, UUID entranceId);

    // ==================== Tunnel Management ====================

    /**
     * Get a tunnel by ID
     */
    Optional<Tunnel> getTunnel(UUID tunnelId);

    /**
     * Get all tunnels for a nation
     */
    List<Tunnel> getNationTunnels(NationId nationId);

    /**
     * Get all tunnels at a location (for discovery)
     */
    List<Tunnel> getTunnelsAt(Location location);

    /**
     * Get tunnels visible to a player (nation-based + discovered)
     */
    List<Tunnel> getVisibleTunnels(UUID playerId, NationId playerNation);

    /**
     * Update tunnel state
     */
    void updateTunnelState(UUID tunnelId, TunnelState state);

    /**
     * Set tunnel as discovered by a nation
     */
    void discoverTunnel(UUID tunnelId, NationId discoverer);

    /**
     * Check if tunnel is discovered by a nation
     */
    boolean isDiscoveredBy(UUID tunnelId, NationId nationId);

    // ==================== Tunnel Navigation ====================

    /**
     * Enter a tunnel at entrance
     */
    boolean enterTunnel(UUID playerId, UUID entranceId);

    /**
     * Exit tunnel to surface
     */
    Location exitTunnel(UUID playerId);

    /**
     * Get current tunnel for player
     */
    Optional<UUID> getPlayerTunnel(UUID playerId);

    // ==================== Tunnel Combat ====================

    /**
     * Ambush players in a tunnel section
     */
    int ambush(UUID tunnelId, UUID attackerNation, Location targetArea, double range);

    /**
     * Set tunnel trap
     */
    boolean setTrap(UUID tunnelId, UUID entranceId, TrapType trap);

    /**
     * Trigger trap on player
     */
    boolean triggerTrap(UUID playerId, UUID tunnelId);

    // ==================== Tunnel Destruction ====================

    /**
     * Collapse tunnel section
     */
    boolean collapseSection(UUID tunnelId, Location center, double radius);

    /**
     * Destroy entire tunnel
     */
    boolean destroyTunnel(UUID tunnelId);

    // ==================== Utility ====================

    /**
     * Get tunnel defense bonus for a tunnel
     */
    double getTunnelDefenseBonus(UUID tunnelId);

    /**
     * Get all known tunnel entrances (for map rendering)
     */
    List<TunnelEntrance> getAllEntrances(NationId nationId);

    /**
     * Save all tunnel data
     */
    void saveAll();

    /**
     * Load tunnel data
     */
    void loadAll();

    // ==================== Inner Classes ====================

    record TunnelConfig(
        int maxTunnelsPerNation,
        int maxEntrancesPerTunnel,
        double tunnelDepth,
        double maxAmbushRange,
        double collapseRadius,
        Map<TunnelType, Double> buildCosts,
        Map<TunnelType, Integer> buildTimes
    ) {
        public static TunnelConfig fromConfig(org.bukkit.configuration.ConfigurationSection config) {
            if (config == null) {
                return defaultConfig();
            }
            return new TunnelConfig(
                config.getInt("max-tunnels-per-nation", 5),
                config.getInt("max-entrances-per-tunnel", 10),
                config.getDouble("tunnel-depth", 20.0),
                config.getDouble("max-ambush-range", 30.0),
                config.getDouble("collapse-radius", 5.0),
                loadBuildCosts(config.getConfigurationSection("build-costs")),
                loadBuildTimes(config.getConfigurationSection("build-times"))
            );
        }

        public static TunnelConfig defaultConfig() {
            return new TunnelConfig(
                5, 10, 20.0, 30.0, 5.0,
                Map.of(
                    TunnelType.SUPPLY, 1000.0,
                    TunnelType.MILITARY, 2000.0,
                    TunnelType.SECRET, 3000.0,
                    TunnelType.ESCAPE, 1500.0
                ),
                Map.of(
                    TunnelType.SUPPLY, 300,
                    TunnelType.MILITARY, 600,
                    TunnelType.SECRET, 900,
                    TunnelType.ESCAPE, 450
                )
            );
        }

        private static Map<TunnelType, Double> loadBuildCosts(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return Map.of(
                    TunnelType.SUPPLY, 1000.0,
                    TunnelType.MILITARY, 2000.0,
                    TunnelType.SECRET, 3000.0,
                    TunnelType.ESCAPE, 1500.0
                );
            }
            Map<TunnelType, Double> costs = new EnumMap<>(TunnelType.class);
            for (TunnelType type : TunnelType.values()) {
                costs.put(type, section.getDouble(type.name().toLowerCase(), 1000.0));
            }
            return costs;
        }

        private static Map<TunnelType, Integer> loadBuildTimes(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return Map.of(
                    TunnelType.SUPPLY, 300,
                    TunnelType.MILITARY, 600,
                    TunnelType.SECRET, 900,
                    TunnelType.ESCAPE, 450
                );
            }
            Map<TunnelType, Integer> times = new EnumMap<>(TunnelType.class);
            for (TunnelType type : TunnelType.values()) {
                times.put(type, section.getInt(type.name().toLowerCase(), 300));
            }
            return times;
        }
    }

    enum TrapType {
        PRESSURE_PLATE("Pressure Plate", 5.0, 10),
        FALL_TRAP("Fall Trap", 15.0, 20),
        CEILING_COLLAPSE("Ceiling Collapse", 25.0, 30),
        POISON_GAS("Poison Gas", 8.0, 15),
        DEAFENING_ROCKS("Deafening Rocks", 12.0, 18);

        private final String displayName;
        private final double damage;
        private final int duration;

        TrapType(String displayName, double damage, int duration) {
            this.displayName = displayName;
            this.damage = damage;
            this.duration = duration;
        }

        public String displayName() { return displayName; }
        public double damage() { return damage; }
        public int duration() { return duration; }
    }
}
