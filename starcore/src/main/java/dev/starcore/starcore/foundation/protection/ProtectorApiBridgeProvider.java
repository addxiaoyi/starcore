package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.core.config.ConfigurationService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public final class ProtectorApiBridgeProvider implements ExternalProtectionBridgeProvider {
    @Override
    public String displayName() {
        return "ProtectorAPI";
    }

    @Override
    public boolean configuredEnabled(ConfigurationService configuration) {
        return Objects.requireNonNull(configuration, "configuration").integrationProtectorApiEnabled();
    }

    @Override
    public ExternalProtectionService create(JavaPlugin plugin, ConfigurationService configuration, Logger logger) {
        return new ProtectorApiExternalProtectionService(
            Objects.requireNonNull(plugin, "plugin"),
            Objects.requireNonNull(configuration, "configuration"),
            Objects.requireNonNull(logger, "logger")
        );
    }

    @Override
    public ExternalProtectionService disabledService() {
        return new NoopExternalProtectionService("ProtectorAPI 集成已关闭");
    }
}
