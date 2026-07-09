package dev.starcore.starcore.territory;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * 领土存储工厂
 * 提供 TerritoryStorage 实例的便捷创建
 */
public final class TerritoryStorageFactory {

    private TerritoryStorageFactory() {}

    /**
     * 创建领土存储实例
     *
     * @param plugin 插件实例
     * @param databaseService 数据库服务
     * @param persistenceService 持久化服务
     * @return 领土存储实例
     */
    public static TerritoryStorage create(JavaPlugin plugin,
                                           DatabaseService databaseService,
                                           PersistenceService persistenceService) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(databaseService, "databaseService");
        Objects.requireNonNull(persistenceService, "persistenceService");

        Logger logger = plugin.getLogger();
        return new SqlTerritoryStorage(databaseService, persistenceService, logger);
    }

    /**
     * 创建内存存储（仅测试用）
     *
     * @param plugin 插件实例
     * @return 内存领土存储实例
     */
    public static TerritoryStorage createInMemory(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new InMemoryTerritoryStorage();
    }

    /**
     * 内存存储实现（用于测试）
     */
    private static class InMemoryTerritoryStorage implements TerritoryStorage {
        @Override
        public java.util.Collection<Territory> loadTerritories() {
            return java.util.Collections.emptyList();
        }

        @Override
        public void saveTerritories(java.util.Collection<Territory> territories) {
            // 内存存储，不做任何操作
        }

        @Override
        public java.util.Collection<SubRegion> loadSubRegions() {
            return java.util.Collections.emptyList();
        }

        @Override
        public void saveSubRegions(java.util.Collection<SubRegion> subRegions) {
            // 内存存储，不做任何操作
        }

        @Override
        public boolean isUsingSql() {
            return false;
        }
    }
}
