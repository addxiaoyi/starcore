package dev.starcore.starcore.foundation.message;

import dev.starcore.starcore.core.config.ConfigurationService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SimpleMessageService implements MessageService {
    private static final String DEFAULT_LOCALE = "zh_cn";
    private static final String RESOURCE_DIRECTORY = "lang/";
    private static final String RESOURCE_PREFIX = "messages_";
    private static final String RESOURCE_SUFFIX = ".yml";

    private final JavaPlugin plugin;
    private final ConfigurationService configuration;
    private Map<String, String> activeMessages = new HashMap<>();
    private Map<String, String> fallbackMessages = new HashMap<>();

    public SimpleMessageService() {
        this.plugin = null;
        this.configuration = null;
    }

    public SimpleMessageService(JavaPlugin plugin, ConfigurationService configuration) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        reload();
    }

    @Override
    public String format(String key, Object... args) {
        String pattern = activeMessages.get(key);
        if (pattern == null || pattern.isBlank()) {
            pattern = fallbackMessages.get(key);
        }
        if (pattern == null || pattern.isBlank()) {
            pattern = key;
        }
        return MessageFormatter.format(pattern, args);
    }

    @Override
    public void reload() {
        if (plugin == null || configuration == null) {
            return;
        }
        String fallbackResource = resourceName(DEFAULT_LOCALE);
        saveBundledResourceIfNeeded(fallbackResource);
        fallbackMessages = loadBundledMessages(fallbackResource);

        String locale = normalizeLocale(configuration.locale());
        String activeResource = resourceName(locale);
        saveBundledResourceIfNeeded(activeResource);
        File activeFile = new File(plugin.getDataFolder(), activeResource);
        activeMessages = activeFile.exists() ? loadMessages(activeFile, activeResource) : fallbackMessages;
    }

    private void saveBundledResourceIfNeeded(String resourceName) {
        File target = new File(plugin.getDataFolder(), resourceName);
        if (target.exists()) {
            return;
        }
        try (InputStream input = plugin.getResource(resourceName)) {
            if (input != null) {
                plugin.saveResource(resourceName, false);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Unable to prepare language file " + resourceName + ": " + exception.getMessage());
        }
    }

    private Map<String, String> loadBundledMessages(String resourceName) {
        try (InputStream input = plugin.getResource(resourceName)) {
            if (input == null) {
                return new HashMap<>();
            }
            return YamlMessageLoader.load(input);
        } catch (Exception exception) {
            plugin.getLogger().warning("Unable to load bundled language defaults " + resourceName + ": " + exception.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, String> loadMessages(File file, String resourceName) {
        try {
            return YamlMessageLoader.load(file.toPath());
        } catch (Exception exception) {
            plugin.getLogger().warning("Unable to load language file " + resourceName + ": " + exception.getMessage());
            return new HashMap<>();
        }
    }

    private static String resourceName(String locale) {
        return RESOURCE_DIRECTORY + RESOURCE_PREFIX + locale + RESOURCE_SUFFIX;
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        return locale.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
