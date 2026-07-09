package dev.starcore.starcore.module.emergency;

import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 紧急状态持久化存储
 * 使用 properties 文件存储紧急状态数据
 */
public final class DatabaseAwareEmergencyStateStorage implements EmergencyStateStorage {

    private static final String FILE_NAME = "emergencies.properties";

    private final Plugin plugin;
    private final StarCoreScheduler scheduler;
    private final Path storagePath;

    public DatabaseAwareEmergencyStateStorage(Plugin plugin, StarCoreScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.storagePath = plugin.getDataFolder().toPath().resolve("emergency").resolve(FILE_NAME);
    }

    /**
     * 加载紧急状态
     */
    public Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(storagePath)) {
            return properties;
        }

        try (InputStream is = Files.newInputStream(storagePath)) {
            properties.load(is);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load emergency states: " + e.getMessage());
        }

        return properties;
    }

    /**
     * 同步保存紧急状态
     */
    public void save(Properties properties) {
        try {
            Files.createDirectories(storagePath.getParent());
            try (OutputStream os = Files.newOutputStream(storagePath)) {
                properties.store(os, "StarCore Emergency States");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save emergency states: " + e.getMessage());
        }
    }

    /**
     * 异步保存紧急状态
     */
    public void saveAsync(Properties properties) {
        scheduler.runAsync(() -> save(properties));
    }

    @Override
    public void close() {
        // Nothing to close for file-based storage
    }
}