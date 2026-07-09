package dev.starcore.starcore.module.army.tunnel;
import java.util.Optional;

import dev.starcore.starcore.module.army.tunnel.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.army.tunnel.event.TunnelCreatedEvent;
import dev.starcore.starcore.module.army.tunnel.event.TunnelCollapsedEvent;
import dev.starcore.starcore.module.army.tunnel.event.PlayerEnterTunnelEvent;
import dev.starcore.starcore.module.army.tunnel.event.PlayerExitTunnelEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tunnel Warfare Service Implementation
 * Manages underground tunnel networks for nations
 */
public final class TunnelServiceImpl implements TunnelService {

    private final Plugin plugin;
    private final NationService nationService;
    private final EconomyService economyService;
    private final MessageService messages;
    private final TunnelConfig config;

    // Tunnel storage
    private final Map<UUID, Tunnel> tunnels = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerTunnels = new ConcurrentHashMap<>(); // player -> tunnel
    private final Map<UUID, UUID> entranceTunnelMap = new ConcurrentHashMap<>(); // entrance -> tunnel
    private final Map<UUID, List<UUID>> nationTunnels = new ConcurrentHashMap<>(); // nation -> tunnels
    private final Map<UUID, Set<NationId>> discoveredBy = new ConcurrentHashMap<>(); // tunnel -> nations that discovered

    // Trap storage
    private final Map<UUID, TrapType> entranceTraps = new ConcurrentHashMap<>();

    // Building tunnels (async construction)
    private final Map<UUID, TunnelBuildTask> buildingTunnels = new ConcurrentHashMap<>();

    public TunnelServiceImpl(
            Plugin plugin,
            NationService nationService,
            EconomyService economyService,
            MessageService messages,
            TunnelConfig config
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.economyService = economyService;
        this.messages = messages;
        this.config = config;

        // Register events
        Bukkit.getPluginManager().registerEvents(new TunnelListener(), plugin);
    }

    // ==================== Tunnel Creation ====================

    @Override
    public Tunnel createTunnel(NationId nationId, String name, TunnelType type, Location entrance) {
        // Validate nation
        Nation nation = nationService.nationById(nationId).orElse(null);
        if (nation == null) {
            throw new IllegalArgumentException("Nation not found: " + nationId);
        }

        // Check tunnel limit
        int currentTunnels = getNationTunnels(nationId).size();
        if (currentTunnels >= config.maxTunnelsPerNation()) {
            throw new IllegalStateException("Maximum tunnels reached: " + config.maxTunnelsPerNation());
        }

        // Check cost
        double cost = config.buildCosts().getOrDefault(type, 1000.0);
        if (!economyService.has(nationId.value(), BigDecimal.valueOf(cost))) {
            throw new IllegalStateException("Insufficient funds. Need: " + cost);
        }

        // Deduct cost
        economyService.withdraw(nationId.value(), BigDecimal.valueOf(cost));

        // Create tunnel
        UUID tunnelId = UUID.randomUUID();
        Instant now = Instant.now();

        Tunnel tunnel = new Tunnel(
            tunnelId,
            nationId,
            name,
            type,
            TunnelState.UNDER_CONSTRUCTION,
            entrance,
            new ArrayList<>(),
            new ArrayList<>(),
            now,
            now,
            config.buildTimes().getOrDefault(type, 300),
            0
        );

        tunnels.put(tunnelId, tunnel);

        // Update nation tunnel index
        nationTunnels.computeIfAbsent(nationId.value(), k -> new ArrayList<>()).add(tunnelId);

        // Create initial entrance
        TunnelEntrance mainEntrance = new TunnelEntrance(
            UUID.randomUUID(),
            entrance.clone(),
            true, // hidden based on type
            tunnelId,
            Instant.now()
        );
        tunnel.entrances().add(mainEntrance);
        entranceTunnelMap.put(mainEntrance.id(), tunnelId);

        // Schedule build completion
        scheduleBuildCompletion(tunnelId);

        // Fire event
        Bukkit.getPluginManager().callEvent(new TunnelCreatedEvent(tunnel));

        return tunnel;
    }

    @Override
    public TunnelEntrance addEntrance(UUID tunnelId, Location location, boolean isHidden) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) {
            throw new IllegalArgumentException("Tunnel not found: " + tunnelId);
        }

        if (tunnel.entrances().size() >= config.maxEntrancesPerTunnel()) {
            throw new IllegalStateException("Maximum entrances reached: " + config.maxEntrancesPerTunnel());
        }

        TunnelEntrance entrance = new TunnelEntrance(
            UUID.randomUUID(),
            location.clone(),
            isHidden,
            tunnelId,
            Instant.now()
        );

        tunnel.entrances().add(entrance);
        entranceTunnelMap.put(entrance.id(), tunnelId);
        tunnel.setLastUpdated(Instant.now());

        return entrance;
    }

    @Override
    public boolean removeEntrance(UUID tunnelId, UUID entranceId) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) return false;

        boolean removed = tunnel.entrances().removeIf(e -> e.id().equals(entranceId));
        if (removed) {
            entranceTunnelMap.remove(entranceId);
            entranceTraps.remove(entranceId);
            tunnel.setLastUpdated(Instant.now());
        }
        return removed;
    }

    // ==================== Tunnel Management ====================

    @Override
    public Optional<Tunnel> getTunnel(UUID tunnelId) {
        return Optional.ofNullable(tunnels.get(tunnelId));
    }

    @Override
    public List<Tunnel> getNationTunnels(NationId nationId) {
        List<UUID> tunnelIds = nationTunnels.getOrDefault(nationId.value(), Collections.emptyList());
        return tunnelIds.stream()
            .map(tunnels::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public List<Tunnel> getTunnelsAt(Location location) {
        return tunnels.values().stream()
            .filter(tunnel -> isLocationNearEntrance(location, tunnel))
            .collect(Collectors.toList());
    }

    @Override
    public List<Tunnel> getVisibleTunnels(UUID playerId, NationId playerNation) {
        if (playerNation == null) {
            return Collections.emptyList();
        }

        return tunnels.values().stream()
            .filter(tunnel -> {
                // Own nation tunnels always visible
                if (tunnel.nationId().value().equals(playerNation.value())) {
                    return true;
                }
                // Check if discovered
                Set<NationId> discoverers = discoveredBy.get(tunnel.id());
                return discoverers != null && discoverers.contains(playerNation);
            })
            .collect(Collectors.toList());
    }

    @Override
    public void updateTunnelState(UUID tunnelId, TunnelState state) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel != null) {
            tunnel.setState(state);
            tunnel.setLastUpdated(Instant.now());
        }
    }

    @Override
    public void discoverTunnel(UUID tunnelId, NationId discoverer) {
        discoveredBy.computeIfAbsent(tunnelId, k -> ConcurrentHashMap.newKeySet())
            .add(discoverer);
    }

    @Override
    public boolean isDiscoveredBy(UUID tunnelId, NationId nationId) {
        Set<NationId> discoverers = discoveredBy.get(tunnelId);
        return discoverers != null && discoverers.contains(nationId);
    }

    // ==================== Tunnel Navigation ====================

    @Override
    public boolean enterTunnel(UUID playerId, UUID entranceId) {
        UUID tunnelId = entranceTunnelMap.get(entranceId);
        if (tunnelId == null) return false;

        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) return false;

        // Check if tunnel is accessible
        if (tunnel.state() == TunnelState.COLLAPSED || tunnel.state() == TunnelState.DESTROYED) {
            return false;
        }

        // Record player entry
        playerTunnels.put(playerId, tunnelId);

        // Apply tunnel effects
        applyTunnelEffects(playerId, tunnel);

        // Fire event
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Bukkit.getPluginManager().callEvent(new PlayerEnterTunnelEvent(player, tunnel));

            // Particle effect
            player.spawnParticle(Particle.PORTAL, player.getLocation(), 20);
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 0.5f);
        }

        return true;
    }

    @Override
    public Location exitTunnel(UUID playerId) {
        UUID tunnelId = playerTunnels.remove(playerId);
        if (tunnelId == null) return null;

        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) return null;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return null;

        // Find nearest surface location from entrance
        Location exitLoc = findExitLocation(tunnel);
        if (exitLoc != null) {
            // Teleport player
            player.teleport(exitLoc);

            // Remove tunnel effects
            removeTunnelEffects(playerId);

            // Fire event
            Bukkit.getPluginManager().callEvent(new PlayerExitTunnelEvent(player, tunnel));

            // Effects
            player.spawnParticle(Particle.PORTAL, exitLoc, 30);
            player.playSound(exitLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            return exitLoc;
        }

        return null;
    }

    @Override
    public Optional<UUID> getPlayerTunnel(UUID playerId) {
        return Optional.ofNullable(playerTunnels.get(playerId));
    }

    // ==================== Tunnel Combat ====================

    @Override
    public int ambush(UUID tunnelId, UUID attackerNation, Location targetArea, double range) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) return 0;

        // Only secret/military tunnels can ambush
        if (tunnel.type() != TunnelType.SECRET && tunnel.type() != TunnelType.MILITARY) {
            return 0;
        }

        // Find players in range
        int ambushCount = 0;
        for (UUID playerId : playerTunnels.keySet()) {
            if (!playerTunnels.get(playerId).equals(tunnelId)) {
                continue;
            }

            Player target = Bukkit.getPlayer(playerId);
            if (target == null) continue;

            // Check if same nation
            Optional<Nation> targetNation = nationService.getNationByMember(playerId);
            if (targetNation.isPresent() && targetNation.get().id().value().equals(attackerNation)) {
                continue; // Don't ambush allies
            }

            // Check range
            if (target.getLocation().distance(targetArea) <= range) {
                // Apply ambush damage
                target.damage(config.maxAmbushRange() * 0.5);
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

                ambushCount++;
            }
        }

        return ambushCount;
    }

    @Override
    public boolean setTrap(UUID tunnelId, UUID entranceId, TrapType trap) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) return false;

        // Only military/secret tunnels can have traps
        if (tunnel.type() != TunnelType.SECRET && tunnel.type() != TunnelType.MILITARY) {
            return false;
        }

        // Verify entrance belongs to tunnel
        boolean hasEntrance = tunnel.entrances().stream()
            .anyMatch(e -> e.id().equals(entranceId));
        if (!hasEntrance) return false;

        entranceTraps.put(entranceId, trap);
        return true;
    }

    @Override
    public boolean triggerTrap(UUID playerId, UUID tunnelId) {
        UUID playerTunnelId = playerTunnels.get(playerId);
        if (playerTunnelId == null || !playerTunnelId.equals(tunnelId)) {
            return false;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        // Find trap at player's current entrance
        for (UUID entranceId : entranceTunnelMap.keySet()) {
            if (!entranceTunnelMap.get(entranceId).equals(tunnelId)) continue;

            TrapType trap = entranceTraps.get(entranceId);
            if (trap == null) continue;

            // Check if player is near this entrance
            Location entranceLoc = getEntranceLocation(entranceId);
            if (entranceLoc != null && entranceLoc.distance(player.getLocation()) <= 3.0) {
                applyTrapEffect(player, trap);
                return true;
            }
        }

        return false;
    }

    // ==================== Tunnel Destruction ====================

    @Override
    public boolean collapseSection(UUID tunnelId, Location center, double radius) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null || tunnel.state() == TunnelState.COLLAPSED) {
            return false;
        }

        // Animate collapse
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Create particle effect
            for (double x = -radius; x <= radius; x += 1.0) {
                for (double y = -radius; y <= radius; y += 1.0) {
                    for (double z = -radius; z <= radius; z += 1.0) {
                        Location loc = center.clone().add(x, y, z);
                        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 5,
                            Material.COBBLESTONE.createBlockData());
                    }
                }
            }

            // Play sound
            center.getWorld().playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.5f);

            // Collapse blocks
            collapseBlocks(center, radius);
        });

        // Update tunnel state
        tunnel.setState(TunnelState.COLLAPSED);
        tunnel.setLastUpdated(Instant.now());

        // Fire event
        Bukkit.getPluginManager().callEvent(new TunnelCollapsedEvent(tunnel, center, radius));

        return true;
    }

    @Override
    public boolean destroyTunnel(UUID tunnelId) {
        Tunnel tunnel = tunnels.remove(tunnelId);
        if (tunnel == null) return false;

        // Remove from nation index
        nationTunnels.getOrDefault(tunnel.nationId().value(), new ArrayList<>())
            .remove(tunnelId);

        // Clear entrances
        tunnel.entrances().forEach(e -> {
            entranceTunnelMap.remove(e.id());
            entranceTraps.remove(e.id());
        });

        // Remove players from tunnel
        playerTunnels.entrySet().removeIf(e -> e.getValue().equals(tunnelId));

        // Remove discoveries
        discoveredBy.remove(tunnelId);

        return true;
    }

    // ==================== Utility ====================

    @Override
    public List<TunnelEntrance> getAllEntrances(NationId nationId) {
        return getNationTunnels(nationId).stream()
            .flatMap(t -> t.entrances().stream())
            .collect(Collectors.toList());
    }

    @Override
    public void saveAll() {
        if (plugin.getConfig().contains("tunnels")) {
            plugin.getConfig().set("tunnels", null);
        }

        for (Tunnel tunnel : tunnels.values()) {
            String path = "tunnels." + tunnel.id().toString();
            plugin.getConfig().set(path + ".nationId", tunnel.nationId().value().toString());
            plugin.getConfig().set(path + ".name", tunnel.name());
            plugin.getConfig().set(path + ".type", tunnel.type().name());
            plugin.getConfig().set(path + ".state", tunnel.state().name());
            plugin.getConfig().set(path + ".createdAt", tunnel.createdAt().toEpochMilli());
            plugin.getConfig().set(path + ".buildTime", tunnel.buildTime());
            plugin.getConfig().set(path + ".progress", tunnel.progress());

            // Save entrances
            List<Map<String, Object>> entrances = new ArrayList<>();
            for (TunnelEntrance entrance : tunnel.entrances()) {
                Map<String, Object> eMap = new HashMap<>();
                eMap.put("id", entrance.id().toString());
                eMap.put("world", entrance.location().getWorld().getName());
                eMap.put("x", entrance.location().getBlockX());
                eMap.put("y", entrance.location().getBlockY());
                eMap.put("z", entrance.location().getBlockZ());
                eMap.put("hidden", entrance.isHidden());
                eMap.put("createdAt", entrance.createdAt().toEpochMilli());
                entrances.add(eMap);
            }
            plugin.getConfig().set(path + ".entrances", entrances);
        }

        // Save discoveries
        for (Map.Entry<UUID, Set<NationId>> entry : discoveredBy.entrySet()) {
            String path = "tunnel-discoveries." + entry.getKey().toString();
            List<String> discoverers = entry.getValue().stream()
                .map(n -> n.value().toString())
                .collect(Collectors.toList());
            plugin.getConfig().set(path, discoverers);
        }

        plugin.saveConfig();
    }

    @Override
    public void loadAll() {
        ConfigurationSection tunnelsSection = plugin.getConfig().getConfigurationSection("tunnels");
        if (tunnelsSection == null) return;

        for (String key : tunnelsSection.getKeys(false)) {
            String path = "tunnels." + key;

            UUID tunnelId = UUID.fromString(key);
            UUID nationId = UUID.fromString(tunnelsSection.getString(path + ".nationId"));
            String name = tunnelsSection.getString(path + ".name");
            TunnelType type = TunnelType.valueOf(tunnelsSection.getString(path + ".type"));
            TunnelState state = TunnelState.valueOf(tunnelsSection.getString(path + ".state"));
            Instant createdAt = Instant.ofEpochMilli(tunnelsSection.getLong(path + ".createdAt"));
            int buildTime = tunnelsSection.getInt(path + ".buildTime");
            int progress = tunnelsSection.getInt(path + ".progress");

            // Load entrances
            List<TunnelEntrance> entrances = new ArrayList<>();
            List<?> rawEntrances = tunnelsSection.getList(path + ".entrances");
            if (rawEntrances != null) {
                for (Object e : rawEntrances) {
                    if (e instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> eMap = (Map<String, Object>) e;
                        UUID eId = UUID.fromString((String) eMap.get("id"));
                        String world = (String) eMap.get("world");
                        int x = (int) eMap.get("x");
                        int y = (int) eMap.get("y");
                        int z = (int) eMap.get("z");
                        boolean hidden = (boolean) eMap.get("hidden");
                        Instant eCreatedAt = Instant.ofEpochMilli((long) eMap.get("createdAt"));

                        Location loc = new Location(
                            Bukkit.getWorld(world), x, y, z
                        );
                        TunnelEntrance entrance = new TunnelEntrance(eId, loc, hidden, tunnelId, eCreatedAt);
                        entrances.add(entrance);
                        entranceTunnelMap.put(eId, tunnelId);
                    }
                }
            }

            // Main entrance for tunnel state
            Location mainEntrance = entrances.isEmpty() ? null : entrances.get(0).location();

            Tunnel tunnel = new Tunnel(
                tunnelId,
                new NationId(nationId),
                name,
                type,
                state,
                mainEntrance,
                entrances,
                new ArrayList<>(),
                createdAt,
                Instant.now(),
                buildTime,
                progress
            );

            tunnels.put(tunnelId, tunnel);
            nationTunnels.computeIfAbsent(nationId, k -> new ArrayList<>()).add(tunnelId);
        }

        // Load discoveries
        ConfigurationSection discSection = plugin.getConfig().getConfigurationSection("tunnel-discoveries");
        if (discSection != null) {
            for (String key : discSection.getKeys(false)) {
                UUID tunnelId = UUID.fromString(key);
                List<String> discoverers = discSection.getStringList(key);
                Set<NationId> discSet = ConcurrentHashMap.newKeySet();
                for (String d : discoverers) {
                    discSet.add(new NationId(UUID.fromString(d)));
                }
                discoveredBy.put(tunnelId, discSet);
            }
        }
    }

    // ==================== Private Helpers ====================

    private void scheduleBuildCompletion(UUID tunnelId) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) return;

        buildingTunnels.put(tunnelId, new TunnelBuildTask(tunnelId, System.currentTimeMillis()));

        // Schedule periodic progress updates
        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            TunnelBuildTask task = buildingTunnels.get(tunnelId);
            if (task == null) {
                return;
            }

            long elapsed = System.currentTimeMillis() - task.startTime();
            int progress = (int) ((elapsed / (tunnel.buildTime() * 1000L)) * 100);
            tunnel.setProgress(Math.min(progress, 100));

            if (progress >= 100) {
                // Build complete
                tunnel.setState(TunnelState.ACTIVE);
                tunnel.setProgress(100);
                buildingTunnels.remove(tunnelId);
            }
        }, 20L, 20L).getTaskId();

        // Store task id for cancellation
        buildingTunnels.get(tunnelId).setTaskId(taskId);
    }

    private boolean isLocationNearEntrance(Location loc, Tunnel tunnel) {
        for (TunnelEntrance entrance : tunnel.entrances()) {
            if (entrance.location().getWorld().equals(loc.getWorld())) {
                double dist = entrance.location().distance(loc);
                if (dist <= 50) return true;
            }
        }
        return false;
    }

    private void applyTunnelEffects(UUID playerId, Tunnel tunnel) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        // Darkness effect for all tunnels
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false));

        // Speed boost in escape tunnels
        if (tunnel.type() == TunnelType.ESCAPE) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false));
        }

        // Mining fatigue in secret tunnels (discourages unauthorized mining)
        if (tunnel.type() == TunnelType.SECRET) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, Integer.MAX_VALUE, 1, false, false));
        }
    }

    private void removeTunnelEffects(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    private Location findExitLocation(Tunnel tunnel) {
        if (tunnel.entrances().isEmpty()) {
            return tunnel.entrance();
        }

        // Find first non-hidden entrance for surface exit
        for (TunnelEntrance entrance : tunnel.entrances()) {
            if (!entrance.isHidden() || tunnel.type() == TunnelType.SECRET) {
                return findSurfaceLocation(entrance.location());
            }
        }

        // Fall back to first entrance
        return findSurfaceLocation(tunnel.entrances().get(0).location());
    }

    private Location findSurfaceLocation(Location underground) {
        Location surface = underground.clone();

        // Find surface Y level
        int surfaceY = underground.getWorld().getHighestBlockYAt(surface);
        if (surfaceY > underground.getY()) {
            surfaceY = (int) underground.getY();
        }

        surface.setY(surfaceY);
        return surface;
    }

    private void applyTrapEffect(Player player, TrapType trap) {
        player.damage(trap.damage());

        switch (trap) {
            case PRESSURE_PLATE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, trap.duration() * 20, 2));
            }
            case FALL_TRAP -> {
                player.setVelocity(new Vector(0, -2, 0));
            }
            case CEILING_COLLAPSE -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, trap.duration() * 20, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, trap.duration() * 20, 0));
                collapseBlocks(player.getLocation(), config.collapseRadius());
            }
            case POISON_GAS -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, trap.duration() * 20, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, trap.duration() * 20, 0));
            }
            case DEAFENING_ROCKS -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, trap.duration() * 20, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, trap.duration() * 20, 1));
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
    }

    private void collapseBlocks(Location center, double radius) {
        for (double x = -radius; x <= radius; x += 1.0) {
            for (double y = -radius; y <= radius; y += 1.0) {
                for (double z = -radius; z <= radius; z += 1.0) {
                    Location loc = center.clone().add(x, y, z);
                    Block block = loc.getBlock();

                    // Only collapse replaceable blocks
                    Material type = block.getType();
                    if (type.isSolid() || type == Material.CAVE_AIR || type == Material.AIR) {
                        continue;
                    }

                    // Drop block
                    block.breakNaturally();
                }
            }
        }
    }

    private Location getEntranceLocation(UUID entranceId) {
        for (Tunnel tunnel : tunnels.values()) {
            for (TunnelEntrance entrance : tunnel.entrances()) {
                if (entrance.id().equals(entranceId)) {
                    return entrance.location();
                }
            }
        }
        return null;
    }

    // ==================== Tunnel Defense Bonus ====================

    @Override
    public double getTunnelDefenseBonus(UUID tunnelId) {
        Tunnel tunnel = tunnels.get(tunnelId);
        if (tunnel == null) {
            return 0.0;
        }

        // Defense bonus based on tunnel type
        double baseBonus = switch (tunnel.type()) {
            case SECRET -> 0.25;     // 25% defense bonus for secret tunnels
            case MILITARY -> 0.15;  // 15% defense bonus for military tunnels
            case ESCAPE -> 0.10;    // 10% defense bonus for escape tunnels
            case SUPPLY -> 0.05;    // 5% defense bonus for supply tunnels
        };

        // Additional bonus for traps
        double trapBonus = 0.0;
        for (TunnelEntrance entrance : tunnel.entrances()) {
            TrapType trap = entranceTraps.get(entrance.id());
            if (trap != null) {
                trapBonus += 0.05; // Each trap adds 5% defense
            }
        }

        return baseBonus + trapBonus;
    }

    // ==================== Build Task Record ====================

    private static class TunnelBuildTask {
        private final UUID tunnelId;
        private final long startTime;
        private int taskId;

        public TunnelBuildTask(UUID tunnelId, long startTime) {
            this.tunnelId = tunnelId;
            this.startTime = startTime;
        }

        public long startTime() { return startTime; }
        public int taskId() { return taskId; }
        public void setTaskId(int id) { this.taskId = id; }
    }

    // ==================== Internal Listener ====================

    private class TunnelListener implements org.bukkit.event.Listener {

        @org.bukkit.event.EventHandler(ignoreCancelled = true)
        public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
            // Check for trap triggers
            UUID playerId = event.getPlayer().getUniqueId();
            Optional<UUID> tunnelId = getPlayerTunnel(playerId);
            if (tunnelId.isPresent()) {
                triggerTrap(playerId, tunnelId.get());
            }
        }

        @org.bukkit.event.EventHandler(ignoreCancelled = true)
        public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
            // Remove tunnel effects on quit
            UUID playerId = event.getPlayer().getUniqueId();
            if (playerTunnels.containsKey(playerId)) {
                removeTunnelEffects(playerId);
                playerTunnels.remove(playerId);
            }
        }
    }
}
