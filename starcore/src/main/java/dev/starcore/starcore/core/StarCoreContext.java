package dev.starcore.starcore.core;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleManager;
import dev.starcore.starcore.core.platform.PlatformAdapter;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.permission.InternalPermissionService;
import org.bukkit.plugin.java.JavaPlugin;

public record StarCoreContext(
    JavaPlugin plugin,
    PlatformAdapter platformAdapter,
    ConfigurationService configuration,
    StarCoreScheduler scheduler,
    StarCoreEventBus eventBus,
    PersistenceService persistenceService,
    DatabaseService databaseService,
    InternalPermissionService permissionService,
    InternalEconomyService economyService,
    ModuleManager moduleManager,
    ServiceRegistry serviceRegistry
) {
}
