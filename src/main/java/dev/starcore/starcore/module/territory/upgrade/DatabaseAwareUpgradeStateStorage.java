package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Database-aware implementation of upgrade state storage.
 * 数据库感知的升级状态存储实现
 */
public class DatabaseAwareUpgradeStateStorage implements UpgradeStateStorage {
    private static final String FILE_NAME = "territory_upgrades.dat";

    private final String namespace;
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Path storagePath;
    private final Logger logger;

    public DatabaseAwareUpgradeStateStorage(
            JavaPlugin plugin,
            String namespace,
            DatabaseService databaseService,
            PersistenceService persistenceService) {
        this.namespace = namespace;
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.storagePath = plugin.getDataFolder().toPath().resolve(FILE_NAME);
        this.logger = plugin.getLogger();

        // 确保目录存在
        try {
            Files.createDirectories(storagePath.getParent());
        } catch (IOException e) {
            logger.warning("Failed to create storage directory: " + e.getMessage());
        }
    }

    @Override
    public void saveAsync(Map<String, String> state) {
        persistenceService.savePropertiesAsync("upgrade", "states.props", toProperties(state));
    }

    @Override
    public void save(Map<String, String> state) {
        try {
            Properties props = toProperties(state);

            // 保存到文件
            try (var output = Files.newOutputStream(storagePath)) {
                props.store(output, "StarCore Territory Upgrades");
            }

            // 如果使用数据库，也保存到数据库
            if (databaseService != null) {
                saveToDatabase(state);
            }

            logger.fine("Saved upgrade state for " + state.size() + " nations");
        } catch (IOException e) {
            logger.warning("Failed to save upgrade state: " + e.getMessage());
        }
    }

    private Properties toProperties(Map<String, String> state) {
        Properties props = new Properties();
        props.putAll(state);
        return props;
    }

    @Override
    public Map<String, String> load() {
        Properties props = new Properties();

        // 尝试从数据库加载
        if (databaseService != null) {
            Map<String, String> dbData = loadFromDatabase();
            if (dbData != null && !dbData.isEmpty()) {
                return dbData;
            }
        }

        // 从文件加载
        if (Files.exists(storagePath)) {
            try (var input = Files.newInputStream(storagePath)) {
                props.load(input);
            } catch (IOException e) {
                logger.warning("Failed to load upgrade state: " + e.getMessage());
            }
        }

        return props.stringPropertyNames().stream()
            .collect(Collectors.toUnmodifiableMap(name -> name, props::getProperty));
    }

    private void saveToDatabase(Map<String, String> state) {
        // 如果数据库支持，可以在这里实现 SQL 存储
        // 目前使用文件存储
    }

    private Map<String, String> loadFromDatabase() {
        // 如果数据库支持，可以在这里实现 SQL 加载
        return null;
    }
}
