package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.core.config.ConfigurationService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public final class ExternalProtectionServices {
    private static final List<ExternalProtectionBridgeProvider> BUILTIN_PROVIDERS = List.of(
        new ProtectorApiBridgeProvider()
    );

    private ExternalProtectionServices() {
    }

    public static ExternalProtectionService create(JavaPlugin plugin, ConfigurationService configuration, Logger logger) {
        return create(plugin, configuration, logger, discoverProviders(logger));
    }

    static ExternalProtectionService create(
        JavaPlugin plugin,
        ConfigurationService configuration,
        Logger logger,
        Iterable<ExternalProtectionBridgeProvider> providers
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(providers, "providers");

        List<ExternalProtectionService> activeServices = new ArrayList<>();
        List<ExternalProtectionService> inactiveServices = new ArrayList<>();
        for (ExternalProtectionBridgeProvider provider : providers) {
            registerProviderService(provider, plugin, configuration, logger, activeServices, inactiveServices);
        }
        if (activeServices.isEmpty() && inactiveServices.isEmpty()) {
            return new NoopExternalProtectionService("external.unconfigured", "外部保护桥", "unconfigured", false, "未配置外部保护桥");
        }
        if (activeServices.size() == 1 && inactiveServices.isEmpty()) {
            return activeServices.get(0);
        }
        if (activeServices.isEmpty() && inactiveServices.size() == 1) {
            return inactiveServices.get(0);
        }
        return new CompositeExternalProtectionService(activeServices, inactiveServices);
    }

    static List<ExternalProtectionBridgeProvider> discoverProviders(Logger logger) {
        return discoverProviders(ExternalProtectionServices.class.getClassLoader(), BUILTIN_PROVIDERS, logger);
    }

    static List<ExternalProtectionBridgeProvider> discoverProviders(
        ClassLoader classLoader,
        List<ExternalProtectionBridgeProvider> fallbackProviders
    ) {
        return discoverProviders(classLoader, fallbackProviders, Logger.getAnonymousLogger());
    }

    private static List<ExternalProtectionBridgeProvider> discoverProviders(
        ClassLoader classLoader,
        List<ExternalProtectionBridgeProvider> fallbackProviders,
        Logger logger
    ) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(fallbackProviders, "fallbackProviders");
        Objects.requireNonNull(logger, "logger");

        LinkedHashMap<String, ExternalProtectionBridgeProvider> discovered = new LinkedHashMap<>();
        try {
            ServiceLoader.load(ExternalProtectionBridgeProvider.class, classLoader)
                .forEach(provider -> discovered.putIfAbsent(provider.key(), provider));
        } catch (ServiceConfigurationError error) {
            logger.warning("Failed to discover STARCORE external protection bridge providers: " + error.getMessage());
        }
        for (ExternalProtectionBridgeProvider fallbackProvider : fallbackProviders) {
            discovered.putIfAbsent(fallbackProvider.key(), fallbackProvider);
        }
        return discovered.values().stream()
            .sorted(Comparator.comparingInt(ExternalProtectionBridgeProvider::order)
                .thenComparing(ExternalProtectionBridgeProvider::key))
            .toList();
    }

    private static void registerProviderService(
        ExternalProtectionBridgeProvider provider,
        JavaPlugin plugin,
        ConfigurationService configuration,
        Logger logger,
        List<ExternalProtectionService> activeServices,
        List<ExternalProtectionService> inactiveServices
    ) {
        Objects.requireNonNull(provider, "provider");
        String providerKey = provider.key();
        String providerName = providerDisplayName(provider);
        try {
            if (provider.configuredEnabled(configuration)) {
                ExternalProtectionService service = provider.create(plugin, configuration, logger);
                if (service == null) {
                    logger.warning("External protection bridge '" + providerName + "' returned null during initialization; falling back to no-op.");
                    inactiveServices.add(failureService(providerKey, providerName, "桥接初始化失败"));
                    return;
                }
                activeServices.add(new GuardedExternalProtectionService(providerKey, providerName, service, logger));
                return;
            }
            ExternalProtectionService disabledService = provider.disabledService();
            if (disabledService == null) {
                inactiveServices.add(new NoopExternalProtectionService(providerKey, providerName, "disabled", false, providerName + " 集成已关闭"));
                return;
            }
            inactiveServices.add(new GuardedExternalProtectionService(providerKey, providerName, disabledService, logger));
        } catch (RuntimeException exception) {
            logger.warning("Failed to initialize STARCORE external protection bridge '" + providerName + "': " + exception.getMessage());
            inactiveServices.add(failureService(providerKey, providerName, "桥接初始化失败"));
        }
    }

    private static NoopExternalProtectionService failureService(String providerKey, String providerName, String reason) {
        return new NoopExternalProtectionService(providerKey, providerName, "error", false, providerName + " " + reason);
    }

    private static String providerDisplayName(ExternalProtectionBridgeProvider provider) {
        String name = provider.displayName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        name = provider.key();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String simpleName = provider.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "UnknownBridge" : simpleName;
    }
}
