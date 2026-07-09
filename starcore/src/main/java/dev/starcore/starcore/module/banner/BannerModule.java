package dev.starcore.starcore.module.banner;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.banner.command.BannerCommand;
import dev.starcore.starcore.module.banner.config.BannerConfig;
import dev.starcore.starcore.module.banner.listener.BannerListener;
import dev.starcore.starcore.module.banner.model.BannerPattern;
import dev.starcore.starcore.module.banner.model.NationBanner;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.banner.event.BannerCreatedEvent;
import dev.starcore.starcore.module.banner.event.BannerUpdatedEvent;
import dev.starcore.starcore.module.banner.event.BannerResetEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Banner module implementation
 */
public final class BannerModule implements StarCoreModule, BannerService {
    private static final String FILE_NAME = "banners.json";
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "banner",
        "国家旗帜",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(BannerService.class),
        "Manages nation banners with customizable patterns and colors."
    );

    private final Map<UUID, NationBanner> banners = new ConcurrentHashMap<>();
    private StarCoreContext context;
    private JavaPlugin plugin;
    private PersistenceService persistenceService;
    private ConfigurationService configurationService;
    private MessageService messageService;
    private DatabaseService databaseService;
    private NationService nationService;
    private BannerConfig bannerConfig;
    private BannerCommand bannerCommand;
    private BannerListener bannerListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        this.plugin = context.plugin();
        this.persistenceService = context.persistenceService();
        this.configurationService = context.configuration();
        this.messageService = context.serviceRegistry().require(MessageService.class);
        this.databaseService = context.databaseService();
        this.nationService = context.serviceRegistry().require(NationService.class);

        // Ensure namespace exists
        persistenceService.ensureNamespace(metadata().id());

        // Initialize banner config
        this.bannerConfig = new BannerConfig(configurationService);

        // Register service
        context.serviceRegistry().register(BannerService.class, this);

        // Load existing banners
        loadBanners();

        // Register command
        this.bannerCommand = new BannerCommand(this, nationService, messageService);
        registerCommand("banner", bannerCommand);

        // Register event listener
        this.bannerListener = new BannerListener(this, nationService);
        plugin.getServer().getPluginManager().registerEvents(bannerListener, plugin);

        plugin.getLogger().info("Banner module enabled with " + banners.size() + " banners.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // Save all banners before disabling
        save();

        // Unregister service
        context.serviceRegistry().unregister(BannerService.class);

        plugin.getLogger().info("Banner module disabled.");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = plugin.getServer().getPluginCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        }
    }

    @Override
    public Optional<NationBanner> getBannerByUUID(UUID nationId) {
        return Optional.ofNullable(banners.get(nationId));
    }

    @Override
    public NationBanner createDefaultBanner(UUID nationId) {
        NationBanner banner = NationBanner.createDefault(nationId);
        banners.put(nationId, banner);
        save();

        // Fire event
        fireBannerCreatedEvent(nationId, banner);

        return banner;
    }

    @Override
    public NationBanner updatePattern(UUID nationId, String pattern) {
        NationBanner existing = banners.get(nationId);
        if (existing == null) {
            existing = createDefaultBanner(nationId);
        }

        BannerPattern patternEnum = BannerPattern.fromKey(pattern);
        NationBanner updated = new NationBanner(
            existing.nationId(),
            patternEnum.key(),
            existing.baseColor(),
            existing.patternColor(),
            existing.createdAt(),
            System.currentTimeMillis()
        );

        NationBanner oldBanner = existing;
        banners.put(nationId, updated);
        save();

        // Fire event
        fireBannerUpdatedEvent(nationId, oldBanner, updated, "pattern");

        return updated;
    }

    @Override
    public NationBanner updateBaseColor(UUID nationId, String baseColor) {
        NationBanner existing = banners.get(nationId);
        if (existing == null) {
            existing = createDefaultBanner(nationId);
        }

        NationBanner oldBanner = existing;
        NationBanner updated = new NationBanner(
            existing.nationId(),
            existing.pattern(),
            baseColor.toUpperCase(),
            existing.patternColor(),
            existing.createdAt(),
            System.currentTimeMillis()
        );

        banners.put(nationId, updated);
        save();

        // Fire event
        fireBannerUpdatedEvent(nationId, oldBanner, updated, "baseColor");

        return updated;
    }

    @Override
    public NationBanner updatePatternColor(UUID nationId, String patternColor) {
        NationBanner existing = banners.get(nationId);
        if (existing == null) {
            existing = createDefaultBanner(nationId);
        }

        NationBanner oldBanner = existing;
        NationBanner updated = new NationBanner(
            existing.nationId(),
            existing.pattern(),
            existing.baseColor(),
            patternColor.toUpperCase(),
            existing.createdAt(),
            System.currentTimeMillis()
        );

        banners.put(nationId, updated);
        save();

        // Fire event
        fireBannerUpdatedEvent(nationId, oldBanner, updated, "patternColor");

        return updated;
    }

    @Override
    public NationBanner updateDesign(UUID nationId, String pattern, String baseColor, String patternColor) {
        NationBanner existing = banners.get(nationId);
        if (existing == null) {
            existing = createDefaultBanner(nationId);
        }

        NationBanner oldBanner = existing;
        NationBanner updated = new NationBanner(
            existing.nationId(),
            pattern,
            baseColor.toUpperCase(),
            patternColor.toUpperCase(),
            existing.createdAt(),
            System.currentTimeMillis()
        );

        banners.put(nationId, updated);
        save();

        // Fire event
        fireBannerUpdatedEvent(nationId, oldBanner, updated, "design");

        return updated;
    }

    @Override
    public NationBanner resetToDefault(UUID nationId) {
        NationBanner oldBanner = banners.get(nationId);
        NationBanner reset = NationBanner.createDefault(nationId);
        banners.put(nationId, reset);
        save();

        // Fire reset event
        fireBannerResetEvent(nationId, oldBanner, reset);

        return reset;
    }

    @Override
    public double getPatternCost(String pattern) {
        BannerPattern patternEnum = BannerPattern.fromKey(pattern);
        return patternEnum.cost();
    }

    @Override
    public Collection<NationBanner> getAllBanners() {
        return Collections.unmodifiableCollection(banners.values());
    }

    @Override
    public boolean deleteBanner(UUID nationId) {
        NationBanner removed = banners.remove(nationId);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    @Override
    public boolean hasCustomBanner(UUID nationId) {
        NationBanner banner = banners.get(nationId);
        return banner != null && !banner.isDefault();
    }

    @Override
    public void save() {
        Path filePath = getBannersFilePath();
        try {
            filePath.getParent().toFile().mkdirs();
            String json = serializeBanners();
            try (OutputStream os = Files.newOutputStream(filePath)) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save banners: " + e.getMessage());
        }
    }

    private void loadBanners() {
        Path filePath = getBannersFilePath();
        if (!Files.exists(filePath)) {
            plugin.getLogger().info("No banners file found, starting fresh.");
            return;
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] data = is.readAllBytes();
            String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            deserializeBanners(json);
            plugin.getLogger().info("Loaded " + banners.size() + " banners from storage.");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load banners: " + e.getMessage());
        }
    }

    private Path getBannersFilePath() {
        return plugin.getDataFolder().toPath()
            .resolve(metadata().id())
            .resolve(FILE_NAME);
    }

    private String serializeBanners() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"banners\":[");
        boolean first = true;
        for (NationBanner banner : banners.values()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{");
            sb.append("\"nationId\":\"").append(banner.nationId().toString()).append("\",");
            sb.append("\"pattern\":\"").append(escapeJson(banner.pattern())).append("\",");
            sb.append("\"baseColor\":\"").append(escapeJson(banner.baseColor())).append("\",");
            sb.append("\"patternColor\":\"").append(escapeJson(banner.patternColor())).append("\",");
            sb.append("\"createdAt\":").append(banner.createdAt()).append(",");
            sb.append("\"updatedAt\":").append(banner.updatedAt());
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private void deserializeBanners(String json) {
        banners.clear();

        // Simple JSON parsing without external library
        try {
            // Extract banners array
            int bannersStart = json.indexOf("\"banners\":[");
            if (bannersStart == -1) return;

            int arrayStart = json.indexOf("[", bannersStart);
            int arrayEnd = findMatchingBracket(json, arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return;

            String arrayContent = json.substring(arrayStart + 1, arrayEnd);
            if (arrayContent.trim().isEmpty()) return;

            // Parse each banner object
            int pos = 0;
            while (pos < arrayContent.length()) {
                int objStart = arrayContent.indexOf("{", pos);
                if (objStart == -1) break;
                int objEnd = findMatchingBracket(arrayContent, objStart);
                if (objEnd == -1) break;

                String objStr = arrayContent.substring(objStart, objEnd + 1);
                NationBanner banner = parseBanner(objStr);
                if (banner != null) {
                    banners.put(banner.nationId(), banner);
                }
                pos = objEnd + 1;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error parsing banners JSON: " + e.getMessage());
        }
    }

    private int findMatchingBracket(String s, int openIndex) {
        if (s.charAt(openIndex) != '{' && s.charAt(openIndex) != '[') {
            return -1;
        }
        char open = s.charAt(openIndex);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;

        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private NationBanner parseBanner(String objStr) {
        try {
            String nationId = extractString(objStr, "nationId");
            String pattern = extractString(objStr, "pattern");
            String baseColor = extractString(objStr, "baseColor");
            String patternColor = extractString(objStr, "patternColor");
            long createdAt = extractLong(objStr, "createdAt");
            long updatedAt = extractLong(objStr, "updatedAt");

            if (nationId == null) return null;

            return new NationBanner(
                UUID.fromString(nationId),
                pattern != null ? pattern : "plain",
                baseColor != null ? baseColor : "WHITE",
                patternColor != null ? patternColor : "BLACK",
                createdAt > 0 ? createdAt : System.currentTimeMillis(),
                updatedAt > 0 ? updatedAt : System.currentTimeMillis()
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Error parsing banner: " + e.getMessage());
            return null;
        }
    }

    private String extractString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) return null;
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private long extractLong(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) return 0;
        start += searchKey.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) return 0;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    @Override
    public String summary() {
        long customCount = banners.values().stream().filter(b -> !b.isDefault()).count();
        return "Banner module: " + banners.size() + " banners total, " + customCount + " custom designs";
    }

    /**
     * Get the plugin instance
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Get the context
     */
    public StarCoreContext getContext() {
        return context;
    }

    /**
     * Get the message service
     */
    public MessageService getMessageService() {
        return messageService;
    }

    /**
     * Get banner config
     */
    public BannerConfig getBannerConfig() {
        return bannerConfig;
    }

    /**
     * Get nation service
     */
    public NationService getNationService() {
        return nationService;
    }

    /**
     * Fire banner created event
     */
    private void fireBannerCreatedEvent(UUID nationId, NationBanner banner) {
        String nationName = "";
        Optional<Nation> nationOpt = nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId));
        if (nationOpt.isPresent()) {
            nationName = nationOpt.get().name();
        }

        BannerCreatedEvent event = new BannerCreatedEvent(nationId, nationName, banner, null);
        Bukkit.getServer().getPluginManager().callEvent(event);
    }

    /**
     * Fire banner updated event
     */
    private void fireBannerUpdatedEvent(UUID nationId, NationBanner oldBanner, NationBanner newBanner, String updateType) {
        String nationName = "";
        Optional<Nation> nationOpt = nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId));
        if (nationOpt.isPresent()) {
            nationName = nationOpt.get().name();
        }

        BannerUpdatedEvent event = new BannerUpdatedEvent(nationId, nationName, oldBanner, newBanner, null, updateType);
        Bukkit.getServer().getPluginManager().callEvent(event);
    }

    /**
     * Fire banner reset event
     */
    private void fireBannerResetEvent(UUID nationId, NationBanner oldBanner, NationBanner newBanner) {
        String nationName = "";
        Optional<Nation> nationOpt = nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId));
        if (nationOpt.isPresent()) {
            nationName = nationOpt.get().name();
        }

        BannerResetEvent event = new BannerResetEvent(nationId, nationName, oldBanner, newBanner, null);
        Bukkit.getServer().getPluginManager().callEvent(event);
    }
}
