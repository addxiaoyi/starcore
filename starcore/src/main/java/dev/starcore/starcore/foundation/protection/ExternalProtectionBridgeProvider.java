package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.core.config.ConfigurationService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public interface ExternalProtectionBridgeProvider {
    default String key() {
        return getClass().getName();
    }

    default String displayName() {
        String simpleName = getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? key() : simpleName;
    }

    default int order() {
        return 0;
    }

    boolean configuredEnabled(ConfigurationService configuration);

    ExternalProtectionService create(JavaPlugin plugin, ConfigurationService configuration, Logger logger);

    ExternalProtectionService disabledService();
}
