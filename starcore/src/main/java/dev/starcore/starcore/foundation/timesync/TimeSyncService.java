package dev.starcore.starcore.foundation.timesync;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TimeSyncService {
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long REAL_SECONDS_PER_DAY = 86_400L;
    private static final long MINECRAFT_TICKS_PER_DAY = 24_000L;
    private static final long REAL_SIX_AM_SECONDS = 21_600L;

    private final JavaPlugin plugin;
    private final ConfigurationService configuration;
    private final MessageService messages;
    private BukkitTask task;
    private ZoneId lastZoneId;
    private String lastLocalTime;
    private long lastMinecraftTicks = -1L;
    private int lastWorldCount;

    public TimeSyncService(JavaPlugin plugin, ConfigurationService configuration, MessageService messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public void start() {
        stop();
        if (!configuration.timeSyncEnabled()) {
            plugin.getLogger().info("STARCORE real time sync disabled.");
            return;
        }
        syncNowSafely();
        long intervalTicks = configuration.timeSyncIntervalTicks();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::syncNowSafely, intervalTicks, intervalTicks);
        plugin.getLogger().info("STARCORE real time sync enabled: " + summary());
    }

    public void restart() {
        stop();
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean running() {
        return task != null && !task.isCancelled();
    }

    public String summary() {
        if (!configuration.timeSyncEnabled()) {
            return messages.format("time-sync.summary.disabled");
        }
        String zoneName = lastZoneId == null ? configuration.timeSyncZoneId().getId() : lastZoneId.getId();
        String tickText = lastMinecraftTicks < 0L ? messages.format("time-sync.summary.unsynced") : String.valueOf(lastMinecraftTicks);
        String localTime = lastLocalTime == null ? messages.format("time-sync.summary.unsynced") : lastLocalTime;
        String state = running() ? messages.format("time-sync.summary.running") : messages.format("time-sync.summary.stopped");
        return messages.format("time-sync.summary.active", state, zoneName, localTime, tickText, lastWorldCount);
    }

    public static long minecraftTicksFor(LocalTime localTime) {
        Objects.requireNonNull(localTime, "localTime");
        long shiftedSeconds = Math.floorMod(localTime.toSecondOfDay() - REAL_SIX_AM_SECONDS, REAL_SECONDS_PER_DAY);
        return shiftedSeconds * MINECRAFT_TICKS_PER_DAY / REAL_SECONDS_PER_DAY;
    }

    private void syncNowSafely() {
        try {
            syncNow();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("STARCORE real time sync failed: " + exception.getMessage());
        }
    }

    private void syncNow() {
        ZoneId zoneId = configuration.timeSyncZoneId();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        long minecraftTicks = minecraftTicksFor(now.toLocalTime());
        List<World> worlds = targetWorlds();
        for (World world : worlds) {
            world.setTime(minecraftTicks);
        }
        this.lastZoneId = zoneId;
        this.lastLocalTime = LOCAL_TIME_FORMATTER.format(now);
        this.lastMinecraftTicks = minecraftTicks;
        this.lastWorldCount = worlds.size();
    }

    private List<World> targetWorlds() {
        List<String> configuredWorlds = configuration.timeSyncWorlds();
        Set<String> configuredNames = configuredWorlds.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
        boolean useWhitelist = !configuredNames.isEmpty();
        boolean allowNetherEnd = configuration.timeSyncAllowNetherEnd();
        return Bukkit.getWorlds().stream()
            .filter(world -> !useWhitelist || configuredNames.contains(world.getName().toLowerCase(Locale.ROOT)))
            .filter(world -> allowNetherEnd || world.getEnvironment() == World.Environment.NORMAL)
            .toList();
    }
}
