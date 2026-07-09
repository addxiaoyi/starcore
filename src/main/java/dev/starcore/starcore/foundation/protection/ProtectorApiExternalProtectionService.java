package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public final class ProtectorApiExternalProtectionService implements ExternalProtectionService {
    private static final String BRIDGE_KEY = ProtectorApiBridgeProvider.class.getName();
    private final Server server;
    private final BridgeSettings settings;
    private final Logger logger;
    private volatile ProtectorApiBridgeContract.Binding cachedBinding;
    private volatile ClassLoader cachedLoader;

    public ProtectorApiExternalProtectionService(JavaPlugin plugin, ConfigurationService configuration, Logger logger) {
        this(
            Objects.requireNonNull(plugin, "plugin").getServer(),
            new ConfigBridgeSettings(Objects.requireNonNull(configuration, "configuration")),
            logger
        );
    }

    ProtectorApiExternalProtectionService(Server server, BridgeSettings settings, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Optional<ProtectionConflict> findClaimConflict(ChunkClaimSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (!settings.enabled() || !settings.blockClaimsInProtectedAreas()) {
            return Optional.empty();
        }
        Plugin protectorPlugin = server.getPluginManager().getPlugin(ProtectorApiBridgeContract.PLUGIN_NAME);
        if (protectorPlugin == null || !protectorPlugin.isEnabled()) {
            return Optional.empty();
        }
        ProtectorApiBridgeContract.Binding binding = bindingFor(protectorPlugin).orElse(null);
        if (binding == null) {
            return Optional.empty();
        }
        for (ChunkCoordinate coordinate : selection.coordinates()) {
            Optional<ProtectionConflict> conflict = findChunkConflict(binding, coordinate);
            if (conflict.isPresent()) {
                return conflict;
            }
        }
        return Optional.empty();
    }

    @Override
    public String summary() {
        return currentStatus().displaySummary();
    }

    @Override
    public List<ExternalProtectionBridgeStatus> bridgeStatuses() {
        return List.of(currentStatus());
    }

    private ExternalProtectionBridgeStatus currentStatus() {
        if (!settings.enabled()) {
            return status("disabled", false, "ProtectorAPI 集成已关闭");
        }
        Plugin protectorPlugin = server.getPluginManager().getPlugin(ProtectorApiBridgeContract.PLUGIN_NAME);
        if (protectorPlugin == null || !protectorPlugin.isEnabled()) {
            return status("missing", false, "ProtectorAPI 未安装或未启用");
        }
        ProtectorApiBridgeContract.Binding binding = bindingFor(protectorPlugin).orElse(null);
        if (binding == null) {
            return status("error", false, "ProtectorAPI 已发现，但桥接初始化失败");
        }
        try {
            Collection<?> modules = (Collection<?>) binding.getAllAvailableProtectionModules().invoke(null);
            if (modules == null || modules.isEmpty()) {
                return status("active", true, "ProtectorAPI 已连接，当前没有已注册保护模块");
            }
            List<String> names = new ArrayList<>();
            for (Object module : modules) {
                names.add(String.valueOf(binding.getPluginName().invoke(module)));
            }
            String joined = String.join(", ", names.stream().limit(5).toList());
            if (names.size() > 5) {
                joined += " +" + (names.size() - 5);
            }
            return status("active", true, "ProtectorAPI 已连接，保护模块 " + names.size() + " 个: " + joined);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return status("error", false, "ProtectorAPI 已连接，但状态读取失败");
        }
    }

    private ExternalProtectionBridgeStatus status(String stateKey, boolean active, String summary) {
        return new ExternalProtectionBridgeStatus(BRIDGE_KEY, "ProtectorAPI", stateKey, active, summary);
    }

    private Optional<ProtectionConflict> findChunkConflict(ProtectorApiBridgeContract.Binding binding, ChunkCoordinate coordinate) {
        World world = server.getWorld(coordinate.world());
        if (world == null) {
            return Optional.empty();
        }
        List<Integer> heights = configuredHeights(world);
        Integer surfaceHeight = settings.sampleSurfaceHeight()
            ? Math.clamp(world.getHighestBlockYAt((coordinate.x() * ChunkClaimSelection.CHUNK_SIZE) + 8, (coordinate.z() * ChunkClaimSelection.CHUNK_SIZE) + 8), world.getMinHeight(), world.getMaxHeight() - 1)
            : null;
        for (ClaimConflictSampler.BlockProbe probe : ClaimConflictSampler.probesFor(
            coordinate,
            settings.edgeInsetBlocks(),
            heights,
            surfaceHeight
        )) {
            Location location = new Location(world, probe.blockX() + 0.5D, probe.blockY(), probe.blockZ() + 0.5D);
            ProtectionConflict conflict = findConflictAt(binding, coordinate, location);
            if (conflict != null) {
                return Optional.of(conflict);
            }
        }
        return Optional.empty();
    }

    private ProtectionConflict findConflictAt(ProtectorApiBridgeContract.Binding binding, ChunkCoordinate coordinate, Location location) {
        try {
            Object module = binding.findModule().invoke(null, location);
            if (module == null) {
                return null;
            }
            String providerName = String.valueOf(binding.getPluginName().invoke(module));
            Object range = binding.getProtectionRangeInfo().invoke(module, location);
            if (range == null) {
                return new ProtectionConflict(providerName, "", "", coordinate);
            }
            String displayName = String.valueOf(binding.getDisplayName().invoke(range));
            String id = String.valueOf(binding.getId().invoke(range));
            return new ProtectionConflict(providerName, displayName, id, coordinate);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logger.warning("Failed to query ProtectorAPI conflict bridge: " + exception.getMessage());
            return null;
        }
    }

    private List<Integer> configuredHeights(World world) {
        List<Integer> heights = new ArrayList<>();
        int min = world.getMinHeight();
        int max = world.getMaxHeight() - 1;
        for (Integer configured : settings.sampleYLevels()) {
            if (configured == null) {
                continue;
            }
            heights.add(Math.clamp(configured, min, max));
        }
        if (heights.isEmpty()) {
            heights.add(Math.clamp(64, min, max));
        }
        return heights;
    }

    private Optional<ProtectorApiBridgeContract.Binding> bindingFor(Plugin protectorPlugin) {
        ClassLoader loader = protectorPlugin.getClass().getClassLoader();
        ProtectorApiBridgeContract.Binding cached = cachedBinding;
        if (cached != null && loader == cachedLoader) {
            return Optional.of(cached);
        }
        synchronized (this) {
            cached = cachedBinding;
            if (cached != null && loader == cachedLoader) {
                return Optional.of(cached);
            }
            try {
                ProtectorApiBridgeContract.Binding binding = ProtectorApiBridgeContract.bind(loader);
                cachedBinding = binding;
                cachedLoader = loader;
                return Optional.of(binding);
            } catch (ReflectiveOperationException exception) {
                logger.warning("Unable to initialize ProtectorAPI reflection bridge: " + exception.getMessage());
                cachedBinding = null;
                cachedLoader = null;
                return Optional.empty();
            }
        }
    }

    interface BridgeSettings {
        boolean enabled();

        boolean blockClaimsInProtectedAreas();

        boolean sampleSurfaceHeight();

        List<Integer> sampleYLevels();

        int edgeInsetBlocks();
    }

    private record ConfigBridgeSettings(ConfigurationService configuration) implements BridgeSettings {
        @Override
        public boolean enabled() {
            return configuration.integrationProtectorApiEnabled();
        }

        @Override
        public boolean blockClaimsInProtectedAreas() {
            return configuration.integrationProtectorApiBlockClaimsInProtectedAreas();
        }

        @Override
        public boolean sampleSurfaceHeight() {
            return configuration.integrationProtectorApiSampleSurfaceHeight();
        }

        @Override
        public List<Integer> sampleYLevels() {
            return configuration.integrationProtectorApiSampleYLevels();
        }

        @Override
        public int edgeInsetBlocks() {
            return configuration.integrationProtectorApiEdgeInsetBlocks();
        }
    }
}
