package dev.starcore.starcore.core.config;

import dev.starcore.starcore.foundation.feedback.InGameFeedbackProfile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigurationService {
    // 默认密钥，插件启动时必须更改
    private static final String DEFAULT_MAP_WEB_ACCESS_SECRET = "CHANGE_THIS_SECRET_ON_FIRST_START";
    private static final String DEFAULT_REST_API_SIGNING_SECRET = "CHANGE_THIS_SECRET_ON_FIRST_START";

    // 标记默认密钥是否已配置（用于安全检查）
    private static final Set<String> DEFAULT_SECRET_VALUES = Set.of(
        "change-this-secret",
        "CHANGE_THIS_SECRET_ON_FIRST_START",
        "CHANGE_THIS_SECRET"
    );
    private static final List<String> DEFAULT_MAP_AVATAR_UPSTREAMS = List.of(
        "https://crafatar.com/avatars/{uuid}?size=64&overlay",
        "https://minotar.net/avatar/{uuid}/64.png",
        "https://mc-heads.net/avatar/{uuid}/64"
    );
    private static final int DEFAULT_MAP_TERRAIN_TILE_PIXELS = 256;
    private static final List<Integer> DEFAULT_MAP_TERRAIN_DIRTY_TILE_SIZES = List.of(256, 128, 64, 32, 16, 8, 4, 2, 1);
    private static final List<Integer> DEFAULT_MAP_TERRAIN_PREWARM_TILE_SIZES = List.of(128, 64, 32, 16);
    private static final List<String> DEFAULT_RESOURCE_MIGRATION_OFFICER_ROLES = List.of("marshal");
    private static final List<String> DEFAULT_TREASURY_WITHDRAW_OFFICER_ROLES = List.of("treasurer");
    private static final List<String> DEFAULT_DIPLOMACY_SET_OFFICER_ROLES = List.of("diplomat");
    private static final List<String> DEFAULT_WAR_DECLARE_OFFICER_ROLES = List.of("marshal");
    private static final List<String> DEFAULT_WAR_END_OFFICER_ROLES = List.of("marshal", "diplomat");
    private static final List<String> DEFAULT_POLICY_SET_OFFICER_ROLES = List.of("steward");
    private static final List<String> DEFAULT_POLICY_CLEAR_OFFICER_ROLES = List.of("steward");
    private static final List<String> DEFAULT_TECHNOLOGY_UNLOCK_OFFICER_ROLES = List.of("steward");
    private static final List<String> DEFAULT_TECHNOLOGY_REVOKE_OFFICER_ROLES = List.of("steward");
    private static final Map<String, List<String>> DEFAULT_LEDGER_CATEGORY_EVENT_TYPES = Map.ofEntries(
        Map.entry("resource-income", List.of("treasury.resource-income")),
        Map.entry("income", List.of("treasury.income")),
        Map.entry("reward", List.of("treasury.reward")),
        Map.entry("tax", List.of("treasury.tax")),
        Map.entry("deposit", List.of("treasury.deposit")),
        Map.entry("withdraw", List.of("treasury.withdraw"))
    );
    private static final Map<String, List<String>> DEFAULT_LEDGER_CATEGORY_PREFIXES = Map.ofEntries(
        Map.entry("finance", List.of("treasury.", "resource.")),
        Map.entry("nation", List.of("nation.")),
        Map.entry("territory", List.of("territory.")),
        Map.entry("city-state", List.of("city_state.")),
        Map.entry("treasury", List.of("treasury.")),
        Map.entry("resource", List.of("resource.")),
        Map.entry("policy", List.of("policy.")),
        Map.entry("technology", List.of("technology.")),
        Map.entry("diplomacy", List.of("diplomacy.")),
        Map.entry("war", List.of("war.")),
        Map.entry("officer", List.of("officer."))
    );
    private static final List<String> DEFAULT_LEDGER_CATEGORY_KEYS = List.of(
        "all", "finance", "resource-income", "income", "reward", "tax", "deposit", "withdraw",
        "treasury", "resource", "nation", "territory", "city-state", "policy", "technology", "diplomacy", "war", "officer"
    );
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JavaPlugin plugin;

    public ConfigurationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public String locale() {
        return config().getString("locale", "zh_cn");
    }

    public int asyncThreads() {
        return Math.max(1, config().getInt("core.async-threads", 4));
    }

    public boolean debug() {
        return config().getBoolean("core.debug", false);
    }

    public boolean epochEnabled() {
        return config().getBoolean("epoch.enabled", true);
    }

    public Instant epochStartTime() {
        String raw = config().getString("epoch.start-time", "1970-01-01T00:00:00Z");
        if (raw == null || raw.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return Instant.EPOCH;
        }
    }

    public Duration epochDuration() {
        long amount = Math.max(1L, config().getLong("epoch.length.amount", 1L));
        String unit = config().getString("epoch.length.unit", "days");
        return switch (normalizeTimeUnit(unit)) {
            case "seconds" -> Duration.ofSeconds(amount);
            case "minutes" -> Duration.ofMinutes(amount);
            case "hours" -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        };
    }

    public boolean timeSyncEnabled() {
        return config().getBoolean("time-sync.enabled", false);
    }

    public ZoneId timeSyncZoneId() {
        String raw = config().getString("time-sync.timezone", "Asia/Shanghai");
        if (raw == null || raw.isBlank()) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    public List<String> timeSyncWorlds() {
        return config().getStringList("time-sync.worlds").stream()
            .map(String::trim)
            .filter(world -> !world.isBlank())
            .toList();
    }

    public int timeSyncIntervalTicks() {
        return Math.clamp(config().getInt("time-sync.interval-ticks", 200), 20, 72_000);
    }

    public boolean timeSyncAllowNetherEnd() {
        return config().getBoolean("time-sync.allow-nether-end", false);
    }

    public int maxNationsPerPlayer() {
        return Math.max(0, config().getInt("nation.limits.max-nations-per-player", 1));
    }

    public int maxCityStatesPerPlayer() {
        return Math.max(0, config().getInt("nation.limits.max-city-states-per-player", 3));
    }

    public int maxCityStatesPerNation() {
        return Math.max(0, config().getInt("nation.limits.max-city-states-per-nation", 3));
    }

    public int maxClaimsPerNation() {
        return config().getInt("nation.limits.max-claims-per-nation", 64);
    }

    public int maxMembersPerNation() {
        return config().getInt("nation.limits.max-members-per-nation", 50);
    }

    public BigDecimal nationCreateCost() {
        return money("nation.economy.nation-create-cost", "500.00");
    }

    public BigDecimal cityStateCreateCost() {
        return money("nation.economy.city-state-create-cost", "250.00");
    }

    public BigDecimal claimCost() {
        return money("nation.economy.claim-cost", "100.00");
    }

    public boolean leaderOnlyClaim() {
        return config().getBoolean("nation.economy.leader-only-claim", true);
    }

    public BigDecimal nationDailyIncomeBaseAmount() {
        return money("nation.economy.daily-income.base-amount", "100.00");
    }

    public BigDecimal nationDailyIncomePerMember() {
        return money("nation.economy.daily-income.per-member", "25.00");
    }

    public BigDecimal nationDailyIncomePerClaim() {
        return money("nation.economy.daily-income.per-claim", "5.00");
    }

    public boolean nationDailyIncomeAutoEnabled() {
        return config().getBoolean("nation.economy.daily-income.auto-enabled", false);
    }

    public boolean nationTaxEnabled() {
        return config().getBoolean("nation.economy.tax.enabled", false);
    }

    public BigDecimal nationTaxFixedAmount() {
        return money("nation.economy.tax.fixed-amount", "0.00");
    }

    public BigDecimal nationTaxBalancePercent() {
        return money("nation.economy.tax.balance-percent", "0.00").min(new BigDecimal("100.00"));
    }

    public BigDecimal nationTaxMinimumBalance() {
        return money("nation.economy.tax.minimum-balance", "0.00");
    }

    public boolean nationTaxSkipInsufficientMembers() {
        return config().getBoolean("nation.economy.tax.skip-insufficient-members", true);
    }

    public boolean nationTaxAutoEnabled() {
        return config().getBoolean("nation.economy.tax.auto-enabled", false);
    }

    public Duration nationTaxAutoInterval() {
        long amount = Math.max(1L, config().getLong("nation.economy.tax.auto-amount", 1L));
        String unit = config().getString("nation.economy.tax.auto-interval", "days");
        return switch (normalizeTimeUnit(unit)) {
            case "seconds" -> Duration.ofSeconds(amount);
            case "minutes" -> Duration.ofMinutes(amount);
            case "hours" -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        };
    }

    public Duration nationDailyIncomeAutoInterval() {
        long amount = Math.max(1L, config().getLong("nation.economy.daily-income.auto-amount", 1L));
        String unit = config().getString("nation.economy.daily-income.auto-interval", "days");
        return switch (normalizeTimeUnit(unit)) {
            case "seconds" -> Duration.ofSeconds(amount);
            case "minutes" -> Duration.ofMinutes(amount);
            case "hours" -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        };
    }

    public boolean nationResourcesEnabled() {
        return config().getBoolean("nation.resources.enabled", true);
    }

    public BigDecimal nationResourceMigrationCost() {
        return money("nation.resources.migration-cost", "100000.00");
    }

    public int nationResourceForceMigrationHours() {
        return Math.clamp(config().getInt("nation.resources.force-migration-hours", 4), 1, 168);
    }

    public boolean nationResourceLevelClaimLimitEnabled() {
        return config().getBoolean("nation.resources.level.use-level-claim-limit", true);
    }

    public int nationResourceMaxLevel() {
        return Math.clamp(config().getInt("nation.resources.level.max-level", 100), 1, 1000);
    }

    public long nationResourceLevelBaseExperience() {
        return Math.max(1L, config().getLong("nation.resources.level.base-experience", 1000L));
    }

    public long nationResourceLevelExperienceStep() {
        return Math.max(0L, config().getLong("nation.resources.level.experience-step", 250L));
    }

    public int nationResourceClaimsAtLevelOne() {
        return Math.clamp(config().getInt("nation.resources.level.claims-at-level-1", 20), 0, 1_000_000);
    }

    public int nationResourceClaimsPerLevel() {
        return Math.clamp(config().getInt("nation.resources.level.claims-per-level", 5), 0, 1_000_000);
    }

    public int nationResourceHardMaxClaimsPerNation() {
        return config().getInt("nation.resources.level.hard-max-claims-per-nation", -1);
    }

    public int nationLevelForExperience(long experience) {
        return dev.starcore.starcore.module.nation.NationProgression.levelForExperience(
            experience,
            nationResourceMaxLevel(),
            nationResourceLevelBaseExperience(),
            nationResourceLevelExperienceStep()
        );
    }

    public int nationClaimLimitForExperience(long experience) {
        return dev.starcore.starcore.module.nation.NationProgression.claimLimitForLevel(
            nationLevelForExperience(experience),
            nationResourceClaimsAtLevelOne(),
            nationResourceClaimsPerLevel(),
            nationResourceHardMaxClaimsPerNation()
        );
    }

    public int nationResourceDistrictLevelsPerDistrict() {
        return Math.clamp(config().getInt("nation.resources.resource-districts.levels-per-district", 10), 1, 1000);
    }

    public int nationResourceDistrictInitialLimit() {
        return Math.clamp(config().getInt("nation.resources.resource-districts.initial-limit", 1), 0, 1000);
    }

    public int nationResourceDistrictMaxLimit() {
        return config().getInt("nation.resources.resource-districts.max-limit", 11);
    }

    public int nationResourceDistrictLimitForLevel(int level) {
        return dev.starcore.starcore.module.nation.NationProgression.resourceDistrictLimitForLevel(
            level,
            nationResourceDistrictInitialLimit(),
            nationResourceDistrictLevelsPerDistrict(),
            nationResourceDistrictMaxLimit()
        );
    }

    public int nationResourceRefreshIntervalTicks() {
        return Math.clamp(config().getInt("nation.resources.refresh.check-interval-ticks", 1200), 20, 172_800);
    }

    public int nationResourceRefreshBaseCooldownMinutes() {
        return Math.clamp(config().getInt("nation.resources.refresh.base-cooldown-minutes", 60), 1, 100_800);
    }

    public int nationResourceRefreshMinCooldownMinutes() {
        return Math.clamp(config().getInt("nation.resources.refresh.min-cooldown-minutes", 20), 1, 100_800);
    }

    public int nationResourceRefreshMaxCooldownMinutes() {
        return Math.clamp(config().getInt("nation.resources.refresh.max-cooldown-minutes", 180), 1, 100_800);
    }

    public int nationResourceRefreshBaseAmount() {
        return Math.clamp(config().getInt("nation.resources.refresh.base-resource-amount", 32), 1, 4096);
    }

    public double nationResourceRefreshRichnessAmountMultiplier() {
        return Math.clamp(config().getDouble("nation.resources.refresh.richness-amount-multiplier", 1.0D), 0.01D, 100.0D);
    }

    public long nationResourceRefreshBaseExperience() {
        return Math.max(1L, config().getLong("nation.resources.refresh.base-experience", 120L));
    }

    public boolean nationResourceRefreshTreasuryIncomeEnabled() {
        return config().getBoolean("nation.resources.refresh.treasury-income.enabled", true);
    }

    public BigDecimal nationResourceRefreshTreasuryBaseIncome() {
        return money("nation.resources.refresh.treasury-income.base-income", "250.00");
    }

    public BigDecimal nationResourceRefreshTreasuryIncomePerBlock() {
        return money("nation.resources.refresh.treasury-income.income-per-generated-block", "15.00");
    }

    public double nationResourceRefreshTreasuryRichnessMultiplier() {
        return Math.clamp(config().getDouble("nation.resources.refresh.treasury-income.richness-multiplier", 1.0D), 0.0D, 100.0D);
    }

    public int nationResourceRefreshMaxBlocksPerCycle() {
        return Math.clamp(config().getInt("nation.resources.refresh.max-blocks-per-cycle", 96), 1, 2048);
    }

    public Material nationResourceBeaconMaterial() {
        String raw = config().getString("nation.resources.beacon.material", "BEACON");
        Material material = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isBlock() ? Material.BEACON : material;
    }

    public boolean nationResourceBeaconDisplayEnabled() {
        return config().getBoolean("nation.resources.beacon.display-enabled", true);
    }

    public double nationResourceBeaconDisplayYOffset() {
        return Math.clamp(config().getDouble("nation.resources.beacon.display-y-offset", 2.35D), 0.5D, 8.0D);
    }

    public InGameFeedbackProfile nationResourceFeedbackProfile(String eventKey) {
        return inGameFeedbackProfile("nation.resources.feedback", eventKey);
    }

    public InGameFeedbackProfile nationClaimFeedbackProfile(String eventKey) {
        return inGameFeedbackProfile("nation.claims.feedback", eventKey);
    }

    public InGameFeedbackProfile nationOperationFeedbackProfile(String eventKey) {
        return inGameFeedbackProfile("nation.operations.feedback", eventKey);
    }

    public InGameFeedbackProfile nationStrategyFeedbackProfile(String eventKey) {
        return inGameFeedbackProfile("nation.strategy.feedback", eventKey);
    }

    public InGameFeedbackProfile nationDiplomacyFeedbackProfile(String eventKey) {
        return inGameFeedbackProfile("nation.diplomacy.feedback", eventKey);
    }

    public InGameFeedbackProfile nationOfficerFeedbackProfile(String eventKey) {
        return inGameFeedbackProfile("nation.officers.feedback", eventKey);
    }

    public List<String> nationResourceMigrationOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.resource-district-migration-roles", DEFAULT_RESOURCE_MIGRATION_OFFICER_ROLES);
    }

    public List<String> nationTreasuryWithdrawOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.treasury-withdraw-roles", DEFAULT_TREASURY_WITHDRAW_OFFICER_ROLES);
    }

    public List<String> nationDiplomacySetOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.diplomacy-set-roles", DEFAULT_DIPLOMACY_SET_OFFICER_ROLES);
    }

    public List<String> nationWarDeclareOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.war-declare-roles", DEFAULT_WAR_DECLARE_OFFICER_ROLES);
    }

    public List<String> nationWarEndOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.war-end-roles", DEFAULT_WAR_END_OFFICER_ROLES);
    }

    public List<String> nationPolicySetOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.policy-set-roles", DEFAULT_POLICY_SET_OFFICER_ROLES);
    }

    public List<String> nationPolicyClearOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.policy-clear-roles", DEFAULT_POLICY_CLEAR_OFFICER_ROLES);
    }

    public List<String> nationTechnologyUnlockOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.technology-unlock-roles", DEFAULT_TECHNOLOGY_UNLOCK_OFFICER_ROLES);
    }

    public List<String> nationTechnologyRevokeOfficerRoles() {
        return normalizedOfficerRoles("nation.officers.technology-revoke-roles", DEFAULT_TECHNOLOGY_REVOKE_OFFICER_ROLES);
    }

    public InGameFeedbackProfile nationTreasuryFeedbackProfile(String eventKey) {
        return inGameFeedbackProfile("nation.treasury.feedback", eventKey);
    }

    public BigDecimal nationTreasuryFeedbackMinimumAmount() {
        return money("nation.treasury.feedback.minimum-amount", "0.00");
    }

    public InGameFeedbackProfile inGameFeedbackProfile(String rootPath, String eventKey) {
        String key = normalizeFeedbackKey(eventKey);
        String root = rootPath == null ? "" : rootPath.trim();
        if (root.isBlank()) {
            root = "feedback";
        }
        String defaultPath = root + ".default";
        String eventPath = root + "." + key;
        boolean globalEnabled = config().getBoolean(root + ".enabled", true);
        boolean enabled = config().getBoolean(eventPath + ".enabled", globalEnabled);
        return new InGameFeedbackProfile(
            enabled,
            feedbackName(eventPath, defaultPath, "sound", ""),
            (float) feedbackDouble(eventPath, defaultPath, "sound-volume", 0.8D, 0.0D, 4.0D),
            (float) feedbackDouble(eventPath, defaultPath, "sound-pitch", 1.0D, 0.5D, 2.0D),
            feedbackName(eventPath, defaultPath, "particle", ""),
            feedbackInt(eventPath, defaultPath, "particle-count", 12, 0, 80),
            feedbackDouble(eventPath, defaultPath, "particle-spread", 0.45D, 0.0D, 3.0D),
            feedbackDouble(eventPath, defaultPath, "particle-y-offset", 1.2D, -1.0D, 4.0D),
            feedbackString(eventPath, defaultPath, "actionbar", ""),
            feedbackString(eventPath, defaultPath, "title", ""),
            feedbackString(eventPath, defaultPath, "subtitle", ""),
            feedbackInt(eventPath, defaultPath, "title-fade-in-ticks", 10, 0, 200),
            feedbackInt(eventPath, defaultPath, "title-stay-ticks", 40, 0, 400),
            feedbackInt(eventPath, defaultPath, "title-fade-out-ticks", 10, 0, 200),
            feedbackString(eventPath, defaultPath, "bossbar", ""),
            feedbackName(eventPath, defaultPath, "bossbar-color", "YELLOW"),
            feedbackName(eventPath, defaultPath, "bossbar-overlay", "PROGRESS"),
            (float) feedbackDouble(eventPath, defaultPath, "bossbar-progress", 1.0D, 0.0D, 1.0D),
            feedbackInt(eventPath, defaultPath, "bossbar-duration-ticks", 30, 1, 400)
        );
    }

    public String nationResourceMigrationItemName() {
        return config().getString("nation.resources.migration-item.name", "资源区块迁移核心");
    }

    public List<String> nationResourceMigrationItemLore() {
        List<String> lore = config().getStringList("nation.resources.migration-item.lore").stream()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .toList();
        return lore.isEmpty()
            ? List.of("右键一个本国领地区块", "设置资源区块迁移目标")
            : lore;
    }

    public boolean claimPricingDistanceEnabled() {
        return config().getBoolean("nation.claims.pricing.distance.enabled", true);
    }

    public double claimPricingWorldCenterX() {
        return config().getDouble("nation.claims.pricing.distance.world-center-x", 0.0D);
    }

    public double claimPricingWorldCenterZ() {
        return config().getDouble("nation.claims.pricing.distance.world-center-z", 0.0D);
    }

    public int claimPricingDistanceStepBlocks() {
        return Math.max(1, config().getInt("nation.claims.pricing.distance.step-blocks", 1000));
    }

    public double claimPricingDistanceStepMultiplier() {
        return Math.clamp(config().getDouble("nation.claims.pricing.distance.step-multiplier", 0.05D), 0.0D, 10.0D);
    }

    public double claimPricingDistanceMaxMultiplier() {
        return Math.clamp(config().getDouble("nation.claims.pricing.distance.max-multiplier", 3.0D), 1.0D, 100.0D);
    }

    public boolean claimPricingBiomeEnabled() {
        return config().getBoolean("nation.claims.pricing.biome.enabled", true);
    }

    public double claimPricingBiomeMinMultiplier() {
        return Math.clamp(config().getDouble("nation.claims.pricing.biome.min-multiplier", 0.50D), 0.01D, 100.0D);
    }

    public double claimPricingBiomeMaxMultiplier() {
        return Math.clamp(config().getDouble("nation.claims.pricing.biome.max-multiplier", 1.80D), 0.01D, 100.0D);
    }

    public double claimPricingUnknownBiomeRichness() {
        return Math.clamp(config().getDouble("nation.claims.pricing.biome.unknown-richness", 1.0D), 0.01D, 100.0D);
    }

    public double claimPricingBiomeRichness(String biomeName, double fallback) {
        if (biomeName == null || biomeName.isBlank()) {
            return fallback;
        }
        return Math.clamp(config().getDouble("nation.claims.pricing.biome.richness-overrides." + biomeName, fallback), 0.01D, 100.0D);
    }

    public int claimPricingDetailLimit() {
        return Math.clamp(config().getInt("nation.claims.pricing.detail-limit", 32), 0, 256);
    }

    public boolean claimProtectionEnabled() {
        return config().getBoolean("nation.claims.protection.enabled", true);
    }

    public boolean claimProtectionAllowNationMembers() {
        return config().getBoolean("nation.claims.protection.allow-nation-members", true);
    }

    public boolean claimProtectionProtectBuild() {
        return config().getBoolean("nation.claims.protection.protect-build", true);
    }

    public boolean claimProtectionProtectInteractions() {
        return config().getBoolean("nation.claims.protection.protect-interactions", true);
    }

    public boolean claimProtectionProtectBuckets() {
        return config().getBoolean("nation.claims.protection.protect-buckets", true);
    }

    public boolean claimProtectionProtectEntities() {
        return config().getBoolean("nation.claims.protection.protect-entities", true);
    }

    public boolean claimProtectionProtectExplosions() {
        return config().getBoolean("nation.claims.protection.protect-explosions", true);
    }

    public boolean claimProtectionProtectPistons() {
        return config().getBoolean("nation.claims.protection.protect-pistons", true);
    }

    public boolean claimProtectionProtectEntityGrief() {
        return config().getBoolean("nation.claims.protection.protect-entity-grief", true);
    }

    public boolean claimProtectionProtectLiquidFlow() {
        return config().getBoolean("nation.claims.protection.protect-liquid-flow", true);
    }

    public boolean claimProtectionProtectFireSpread() {
        return config().getBoolean("nation.claims.protection.protect-fire-spread", true);
    }

    public int claimProtectionMessageCooldownSeconds() {
        return Math.clamp(config().getInt("nation.claims.protection.message-cooldown-seconds", 2), 0, 60);
    }

    public boolean integrationProtectorApiEnabled() {
        return config().getBoolean("integrations.protectorapi.enabled", true);
    }

    public boolean integrationProtectorApiBlockClaimsInProtectedAreas() {
        return config().getBoolean("integrations.protectorapi.block-claims-in-protected-areas", true);
    }

    public boolean integrationProtectorApiSampleSurfaceHeight() {
        return config().getBoolean("integrations.protectorapi.sample-surface-height", true);
    }

    public List<Integer> integrationProtectorApiSampleYLevels() {
        List<Integer> levels = config().getIntegerList("integrations.protectorapi.sample-y-levels").stream()
            .filter(level -> level != null)
            .distinct()
            .toList();
        return levels.isEmpty() ? List.of(64, 96, 160) : levels;
    }

    public int integrationProtectorApiEdgeInsetBlocks() {
        return Math.clamp(config().getInt("integrations.protectorapi.edge-inset-blocks", 1), 0, 7);
    }

    // ==================== Redis Configuration ====================

    public boolean redisEnabled() {
        return config().getBoolean("redis.enabled", false);
    }

    public String redisHost() {
        String raw = config().getString("redis.host", "127.0.0.1");
        return raw == null || raw.isBlank() ? "127.0.0.1" : raw.trim();
    }

    public int redisPort() {
        return Math.clamp(config().getInt("redis.port", 6379), 1, 65535);
    }

    public String redisPassword() {
        String raw = config().getString("redis.password", "");
        return raw == null ? "" : raw;
    }

    public int redisDatabase() {
        return Math.clamp(config().getInt("redis.database", 0), 0, 15);
    }

    public boolean claimToolEnabled() {
        return config().getBoolean("nation.claims.tool.enabled", true);
    }

    public Material claimToolMaterial() {
        String raw = config().getString("nation.claims.tool.material", "GOLDEN_SHOVEL");
        Material material = raw == null ? null : Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() ? Material.GOLDEN_SHOVEL : material;
    }

    public String claimToolName() {
        return config().getString("nation.claims.tool.name", "领地权杖");
    }

    public List<String> claimToolLore() {
        List<String> lore = config().getStringList("nation.claims.tool.lore").stream()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .toList();
        return lore.isEmpty()
            ? List.of("左键选择第一个角点", "右键选择第二个角点", "输入 /sc n ok 确认圈地")
            : lore;
    }

    public boolean moduleEnabled(String moduleName) {
        return config().getBoolean("modules." + moduleName, false);
    }

    /**
     * 读取模块开关，当配置缺失时返回指定默认值。
     * 用于新增功能模块：已部署服务器的旧 config.yml 没有对应键时，默认开启。
     */
    public boolean moduleEnabled(String moduleName, boolean defaultValue) {
        return config().getBoolean("modules." + moduleName, defaultValue);
    }

    public boolean databaseEnabled() {
        return config().getBoolean("database.enabled", true);
    }

    public boolean databaseFailFast() {
        return config().getBoolean("database.fail-fast", false);
    }

    public String databaseType() {
        String raw = config().getString("database.type", "sqlite");
        if (raw == null || raw.isBlank()) {
            return "sqlite";
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "mysql", "mariadb" -> "mysql";
            default -> "sqlite";
        };
    }

    public String databaseSqliteFile() {
        String raw = config().getString("database.sqlite.file", "starcore.db");
        return raw == null || raw.isBlank() ? "starcore.db" : raw.trim();
    }

    public String databaseMysqlHost() {
        String raw = config().getString("database.mysql.host", "127.0.0.1");
        return raw == null || raw.isBlank() ? "127.0.0.1" : raw.trim();
    }

    public int databaseMysqlPort() {
        return Math.clamp(config().getInt("database.mysql.port", 3306), 1, 65_535);
    }

    public String databaseMysqlDatabase() {
        String raw = config().getString("database.mysql.database", "starcore");
        return raw == null || raw.isBlank() ? "starcore" : raw.trim();
    }

    public String databaseMysqlUsername() {
        String raw = config().getString("database.mysql.username", "starcore");
        return raw == null ? "" : raw.trim();
    }

    public String databaseMysqlPassword() {
        String raw = config().getString("database.mysql.password", "");
        return raw == null ? "" : raw;
    }

    public String databaseMysqlParameters() {
        String raw = config().getString("database.mysql.parameters", "useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC");
        return raw == null ? "" : raw.trim();
    }

    public int databasePoolMaximumPoolSize() {
        return Math.clamp(config().getInt("database.pool.maximum-pool-size", 8), 1, 64);
    }

    public int databasePoolMinimumIdle() {
        return Math.clamp(config().getInt("database.pool.minimum-idle", 1), 0, databasePoolMaximumPoolSize());
    }

    public long databasePoolConnectionTimeoutMs() {
        return Math.clamp(config().getLong("database.pool.connection-timeout-ms", 30_000L), 250L, 300_000L);
    }

    public long databasePoolIdleTimeoutMs() {
        return Math.clamp(config().getLong("database.pool.idle-timeout-ms", 600_000L), 10_000L, 86_400_000L);
    }

    public long databasePoolMaxLifetimeMs() {
        return Math.clamp(config().getLong("database.pool.max-lifetime-ms", 1_800_000L), 30_000L, 86_400_000L);
    }

    public long databasePoolKeepaliveTimeMs() {
        return Math.clamp(config().getLong("database.pool.keepalive-time-ms", 0L), 0L, 86_400_000L);
    }

    public long databasePoolValidationTimeoutMs() {
        return Math.clamp(config().getLong("database.pool.validation-timeout-ms", 5_000L), 250L, 60_000L);
    }

    public long databasePoolLeakDetectionThresholdMs() {
        return Math.clamp(config().getLong("database.pool.leak-detection-threshold-ms", 0L), 0L, 86_400_000L);
    }

    public boolean mapWebEnabled() {
        return config().getBoolean("map.web.enabled", true);
    }

    public String mapWebHost() {
        return config().getString("map.web.host", "127.0.0.1");
    }

    public int mapWebPort() {
        return Math.max(1, config().getInt("map.web.port", 8716));
    }

    public String mapWebPublicUrl() {
        return config().getString("map.web.public-url", "");
    }

    public List<String> mapWebCorsAllowedOrigins() {
        return config().getStringList("map.web.cors-allowed-origins").stream()
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList();
    }

    public List<String> mapWebTrustedProxies() {
        return config().getStringList("map.web.trusted-proxies").stream()
            .map(String::trim)
            .filter(proxy -> !proxy.isBlank())
            .toList();
    }

    public boolean mapSseEnabled() {
        return config().getBoolean("map.web.sse-enabled", true);
    }

    public int mapSseIntervalTicks() {
        return Math.max(20, config().getInt("map.web.sse-interval-ticks", 40));
    }

    public boolean mapTerrainEnabled() {
        return config().getBoolean("map.web.terrain-enabled", true);
    }

    public boolean mapTerrainLoadGeneratedChunks() {
        return config().getBoolean("map.web.terrain-load-generated-chunks", true);
    }

    public int mapTerrainTileCacheSeconds() {
        return Math.max(10, config().getInt("map.web.terrain-tile-cache-seconds", 300));
    }

    public int mapTerrainTileCacheMaxEntries() {
        return Math.max(32, config().getInt("map.web.terrain-tile-cache-max-entries", 512));
    }

    public int mapTerrainTilePixels() {
        return Math.clamp(config().getInt("map.web.terrain-tile-pixels", DEFAULT_MAP_TERRAIN_TILE_PIXELS), 64, 512);
    }

    public int mapTerrainTileMaxConcurrentRenders() {
        return Math.clamp(config().getInt("map.web.terrain-tile-max-concurrent-renders", 2), 1, 8);
    }

    public boolean mapTerrainTileDiskCacheEnabled() {
        return config().getBoolean("map.web.terrain-tile-disk-cache-enabled", true);
    }

    public int mapTerrainTileDiskCacheHours() {
        return Math.max(1, config().getInt("map.web.terrain-tile-disk-cache-hours", 24));
    }

    public List<Integer> mapTerrainDirtyTileSizes() {
        return configuredPositiveIntegerList(
            "map.web.terrain-dirty-tile-sizes",
            DEFAULT_MAP_TERRAIN_DIRTY_TILE_SIZES,
            4096
        );
    }

    public int mapTerrainDirtyMaxEntries() {
        return Math.clamp(config().getInt("map.web.terrain-dirty-max-entries", 8192), 256, 65536);
    }

    public boolean mapTerrainPrewarmEnabled() {
        return config().getBoolean("map.web.terrain-prewarm-enabled", true);
    }

    public boolean mapWebClaimSelectionEnabled() {
        return config().getBoolean("map.web.claim-selection-enabled", true);
    }

    public boolean mapWebResourceDistrictManagementEnabled() {
        return config().getBoolean("map.web.resource-district-management-enabled", true);
    }

    public boolean mapWebFinanceExportCsvBomEnabled() {
        return config().getBoolean("map.web.finance-export.csv-bom-enabled", true);
    }

    public List<String> ledgerCategoryEventTypes(String category) {
        String key = normalizeLedgerCategoryKey(category);
        List<String> configured = config().getStringList("ledger.categories." + key + ".event-types").stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        return configured.isEmpty() ? DEFAULT_LEDGER_CATEGORY_EVENT_TYPES.getOrDefault(key, List.of()) : configured;
    }

    public List<String> ledgerCategoryPrefixes(String category) {
        String key = normalizeLedgerCategoryKey(category);
        List<String> configured = config().getStringList("ledger.categories." + key + ".prefixes").stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        return configured.isEmpty() ? DEFAULT_LEDGER_CATEGORY_PREFIXES.getOrDefault(key, List.of()) : configured;
    }

    public List<String> ledgerCategoryKeys() {
        Set<String> keys = new LinkedHashSet<>(DEFAULT_LEDGER_CATEGORY_KEYS);
        ConfigurationSection section = config().getConfigurationSection("ledger.categories");
        if (section != null) {
            section.getKeys(false).stream()
                .map(this::normalizeLedgerCategoryKey)
                .filter(key -> !key.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(keys::add);
        }
        return new ArrayList<>(keys);
    }

    public int mapWebClaimMaxChunks() {
        return Math.clamp(config().getInt("map.web.claim-max-chunks", 64), 1, 4096);
    }

    public int mapWebClaimPendingMinutes() {
        return Math.clamp(config().getInt("map.web.claim-pending-minutes", 5), 1, 60);
    }

    public int mapWebClaimCooldownSeconds() {
        return Math.clamp(config().getInt("map.web.claim-cooldown-seconds", 10), 0, 3600);
    }

    public int mapTerrainPrewarmRadiusBlocks() {
        return Math.clamp(config().getInt("map.web.terrain-prewarm-radius-blocks", 256), 0, 2048);
    }

    public int mapTerrainPrewarmMaxTiles() {
        return Math.clamp(config().getInt("map.web.terrain-prewarm-max-tiles", 96), 0, 512);
    }

    public int mapTerrainPrewarmIntervalTicks() {
        return Math.max(1, config().getInt("map.web.terrain-prewarm-interval-ticks", 4));
    }

    public List<Integer> mapTerrainPrewarmTileSizes() {
        return configuredPositiveIntegerList(
            "map.web.terrain-prewarm-tile-sizes",
            DEFAULT_MAP_TERRAIN_PREWARM_TILE_SIZES,
            4096
        );
    }

    public boolean mapAvatarCacheEnabled() {
        return config().getBoolean("map.web.avatar-cache-enabled", true);
    }

    public int mapAvatarCacheTtlMinutes() {
        return Math.max(10, config().getInt("map.web.avatar-cache-ttl-minutes", 360));
    }

    public int mapAvatarCleanupIntervalTicks() {
        return Math.max(20 * 60, config().getInt("map.web.avatar-cleanup-interval-ticks", 72000));
    }

    public List<String> mapAvatarUpstreams() {
        List<String> upstreams = config().getStringList("map.web.avatar-upstreams").stream()
            .map(String::trim)
            .filter(upstream -> !upstream.isBlank())
            .toList();
        return upstreams.isEmpty() ? DEFAULT_MAP_AVATAR_UPSTREAMS : upstreams;
    }

    public String mapWebAccessSecret() {
        return config().getString("map.web.access-secret", DEFAULT_MAP_WEB_ACCESS_SECRET);
    }

    public boolean mapWebAccessSecretConfigured() {
        String secret = mapWebAccessSecret();
        return secret != null
            && !secret.isBlank()
            && !DEFAULT_SECRET_VALUES.contains(secret);
    }

    public boolean ensureMapWebAccessSecretConfigured() {
        if (mapWebAccessSecretConfigured()) {
            return false;
        }
        String newSecret = generateSecret();
        config().set("map.web.access-secret", newSecret);
        plugin.saveConfig();
        plugin.getLogger().warning("⚠️ 地图 Web 服务访问密钥已自动生成！请在 config.yml 中查看并妥善保管。");
        return true;
    }

    public int mapWebAccessTtlMinutes() {
        return Math.max(5, config().getInt("map.web.access-ttl-minutes", 120));
    }

    public int mapWebIpAccessTtlMinutes() {
        return Math.max(5, config().getInt("map.web.ip-access-ttl-minutes", mapWebAccessTtlMinutes()));
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeLedgerCategoryKey(String category) {
        String key = category == null ? "" : category.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (key) {
            case "resource", "resource-income", "resourceincome", "resources-income" -> "resource-income";
            case "city", "city-state" -> "city-state";
            default -> key;
        };
    }

    private String normalizeFeedbackKey(String eventKey) {
        String key = eventKey == null ? "" : eventKey.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return key.isBlank() ? "default" : key;
    }

    private String normalizeOfficerRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private List<String> normalizedOfficerRoles(String path, List<String> defaults) {
        List<String> roles = config().getStringList(path).stream()
            .map(this::normalizeOfficerRole)
            .filter(role -> !role.isBlank())
            .distinct()
            .toList();
        return roles.isEmpty() ? defaults : roles;
    }

    private String feedbackName(String eventPath, String defaultPath, String option, String fallback) {
        String value = feedbackString(eventPath, defaultPath, option, fallback);
        return value.isBlank() ? "" : value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String feedbackString(String eventPath, String defaultPath, String option, String fallback) {
        String eventOptionPath = eventPath + "." + option;
        String defaultOptionPath = defaultPath + "." + option;
        String raw = config().contains(eventOptionPath)
            ? config().getString(eventOptionPath, fallback)
            : config().getString(defaultOptionPath, fallback);
        return raw == null ? fallback : raw.trim();
    }

    private int feedbackInt(String eventPath, String defaultPath, String option, int fallback, int min, int max) {
        String eventOptionPath = eventPath + "." + option;
        String defaultOptionPath = defaultPath + "." + option;
        int value = config().contains(eventOptionPath)
            ? config().getInt(eventOptionPath, fallback)
            : config().getInt(defaultOptionPath, fallback);
        return Math.clamp(value, min, max);
    }

    private double feedbackDouble(String eventPath, String defaultPath, String option, double fallback, double min, double max) {
        String eventOptionPath = eventPath + "." + option;
        String defaultOptionPath = defaultPath + "." + option;
        double value = config().contains(eventOptionPath)
            ? config().getDouble(eventOptionPath, fallback)
            : config().getDouble(defaultOptionPath, fallback);
        return Math.clamp(value, min, max);
    }

    private List<Integer> configuredPositiveIntegerList(String path, List<Integer> defaults, int maxValue) {
        List<Integer> configured = config().getIntegerList(path).stream()
            .filter(size -> size != null && size > 0 && size <= maxValue)
            .distinct()
            .toList();
        return configured.isEmpty() ? defaults : configured;
    }

    private BigDecimal money(String path, String fallback) {
        String raw = config().getString(path, fallback);
        try {
            BigDecimal value = new BigDecimal(raw == null ? fallback : raw.trim());
            return value.signum() < 0 ? BigDecimal.ZERO : value.setScale(2, RoundingMode.DOWN);
        } catch (RuntimeException ignored) {
            return new BigDecimal(fallback).setScale(2, RoundingMode.DOWN);
        }
    }

    private String normalizeTimeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "days";
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "s", "sec", "second", "seconds", "秒" -> "seconds";
            case "m", "min", "minute", "minutes", "分钟", "分" -> "minutes";
            case "h", "hour", "hours", "小时", "时" -> "hours";
            case "d", "day", "days", "天", "日" -> "days";
            default -> "days";
        };
    }

    // ==================== REST API Configuration ====================

    public boolean restApiEnabled() {
        return config().getBoolean("rest-api.enabled", false);
    }

    public String restApiHost() {
        return config().getString("rest-api.host", "127.0.0.1");
    }

    public int restApiPort() {
        return Math.max(1, config().getInt("rest-api.port", 8717));
    }

    public String restApiSigningSecret() {
        String raw = config().getString("rest-api.signing-secret", DEFAULT_REST_API_SIGNING_SECRET);
        return raw == null || raw.isBlank() ? DEFAULT_REST_API_SIGNING_SECRET : raw;
    }

    public boolean restApiSigningSecretConfigured() {
        String secret = restApiSigningSecret();
        return secret != null
            && !secret.isBlank()
            && !DEFAULT_SECRET_VALUES.contains(secret);
    }

    public boolean ensureRestApiSigningSecretConfigured() {
        if (restApiSigningSecretConfigured()) {
            return false;
        }
        String newSecret = generateSecret();
        config().set("rest-api.signing-secret", newSecret);
        plugin.saveConfig();
        plugin.getLogger().warning("⚠️ REST API 签名密钥已自动生成！请在 config.yml 中查看并妥善保管。");
        return true;
    }

    public int restApiKeyTtlHours() {
        return Math.max(1, config().getInt("rest-api.api-key-ttl-hours", 24));
    }

    public boolean restApiCorsEnabled() {
        return config().getBoolean("rest-api.cors.enabled", true);
    }

    public List<String> restApiCorsAllowedOrigins() {
        return config().getStringList("rest-api.cors.allowed-origins").stream()
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .toList();
    }

    public boolean restApiRateLimitEnabled() {
        return config().getBoolean("rest-api.rate-limit.enabled", true);
    }

    public int restApiRateLimitRequestsPerMinute() {
        return Math.max(1, config().getInt("rest-api.rate-limit.requests-per-minute", 60));
    }

    public int restApiRateLimitBurstSize() {
        return Math.max(1, config().getInt("rest-api.rate-limit.burst-size", 10));
    }

    public int restApiRateLimitBlockDurationSeconds() {
        return Math.max(1, config().getInt("rest-api.rate-limit.block-duration-seconds", 60));
    }

    public boolean restApiEndpointNationsEnabled() {
        return config().getBoolean("rest-api.endpoints.nations.enabled", true);
    }

    public boolean restApiEndpointNationsRequiresAuth() {
        return config().getBoolean("rest-api.endpoints.nations.requires-auth", false);
    }

    public boolean restApiEndpointTerritoriesEnabled() {
        return config().getBoolean("rest-api.endpoints.territories.enabled", true);
    }

    public boolean restApiEndpointTerritoriesRequiresAuth() {
        return config().getBoolean("rest-api.endpoints.territories.requires-auth", false);
    }

    public boolean restApiEndpointStatsEnabled() {
        return config().getBoolean("rest-api.endpoints.stats.enabled", true);
    }

    public boolean restApiEndpointStatsRequiresAuth() {
        return config().getBoolean("rest-api.endpoints.stats.requires-auth", false);
    }

    public boolean restApiEndpointFinanceEnabled() {
        return config().getBoolean("rest-api.endpoints.finance.enabled", true);
    }

    public boolean restApiEndpointFinanceRequiresAuth() {
        return config().getBoolean("rest-api.endpoints.finance.requires-auth", true);
    }

    public boolean restApiEndpointWebSocketEnabled() {
        return config().getBoolean("rest-api.endpoints.websocket.enabled", true);
    }

    public boolean restApiEndpointWebSocketRequiresAuth() {
        return config().getBoolean("rest-api.endpoints.websocket.requires-auth", false);
    }

    public int restApiWebSocketMaxConnections() {
        return Math.max(1, config().getInt("rest-api.endpoints.websocket.max-connections", 1000));
    }

    public int restApiWebSocketPingIntervalSeconds() {
        return Math.max(5, config().getInt("rest-api.endpoints.websocket.ping-interval-seconds", 30));
    }

    public int restApiWebSocketPingTimeoutSeconds() {
        return Math.max(10, config().getInt("rest-api.endpoints.websocket.ping-timeout-seconds", 60));
    }

    private static final String CURRENT_CONFIG_VERSION = "1.0";

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    /**
     * Get the underlying FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config();
    }

    /**
     * 校验配置文件版本
     * 如果版本不匹配，输出警告日志提示管理员
     * @return true 如果版本匹配或无版本字段（新配置），false 如果版本不匹配
     */
    public boolean validateConfigVersion() {
        String version = config().getString("config-version");
        if (version == null || version.isBlank()) {
            plugin.getLogger().warning("⚠️ config.yml 缺少 config-version 字段，建议添加以支持未来配置迁移");
            return true; // 新配置，无版本字段视为兼容
        }
        if (!version.equals(CURRENT_CONFIG_VERSION)) {
            plugin.getLogger().warning("⚠️ config.yml 版本不匹配: 期望 " + CURRENT_CONFIG_VERSION + "，实际 " + version);
            plugin.getLogger().warning("⚠️ 请检查配置迁移文档或重新生成默认配置");
            return false;
        }
        return true;
    }

}
