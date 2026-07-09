package dev.starcore.starcore.foundation.economy;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

final class DatabaseAwareBalanceStorage implements InternalEconomyService.BalanceStorage {
    private final DatabaseService databaseService;
    private final InternalEconomyService.BalanceStorage fileStorage;
    private final InternalEconomyService.BalanceStorage sqlStorage;
    private volatile boolean sqlPreferred;
    private final Logger logger;

    DatabaseAwareBalanceStorage(DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.fileStorage = new InternalEconomyService.PersistenceBalanceStorage(persistenceService);
        this.sqlStorage = new SqlBalanceStorage(databaseService, persistenceService, logger);
        this.sqlPreferred = databaseService.isRunning();
        this.logger = logger;
    }

    @Override
    public Properties load() {
        return activeStorage().load();
    }

    @Override
    public void save(Properties properties) {
        activeStorage().save(properties);
    }

    @Override
    public void saveAsync(Properties properties) {
        activeStorage().saveAsync(properties);
    }

    private InternalEconomyService.BalanceStorage activeStorage() {
        if (sqlPreferred || databaseService.isRunning()) {
            if (!sqlPreferred) {
                // E-027 修复: 首次切换到 SQL 存储时，把 fileStorage 中的所有余额一次性迁移到 SQL，
                // 避免玩家余额在切换后丢失。
                sqlPreferred = true;
                migrateToSql();
            }
            return sqlStorage;
        }
        return fileStorage;
    }

    /** E-027 修复: 将 fileStorage 的全部余额迁移到 sqlStorage，仅执行一次。 */
    private void migrateToSql() {
        Properties fileProps = fileStorage.load();
        if (!fileProps.isEmpty()) {
            sqlStorage.save(fileProps);
            logger.info("Migrated " + fileProps.size() + " player balances from file storage to SQL.");
        }
    }
}
