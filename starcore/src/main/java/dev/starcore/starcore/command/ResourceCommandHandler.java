package dev.starcore.starcore.command;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.foundation.epoch.EpochService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.NationOperationalOverview;
import dev.starcore.starcore.module.nation.NationOperationalSupport;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictMigrationResult;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictCommandSupport;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictOperationalOverview;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictOperationalSupport;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictService;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictSnapshot;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictViewSupport;
import dev.starcore.starcore.module.resource.ResourceService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class ResourceCommandHandler {
    private static final List<String> SUBCOMMAND_SUGGESTIONS = List.of(
        "s", "d", "i", "m", "g", "c", "status", "districts", "inspect", "migrate", "grant", "consume",
        "状", "状态", "区", "区块", "资", "资源区块", "查", "查看", "迁", "迁移", "增", "增加", "消", "消耗"
    );
    private static final List<String> AMOUNT_SUGGESTIONS = List.of("16", "64", "256", "1000", "10000");
    private static final DateTimeFormatter COMMAND_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StarCoreContext context;
    private final MessageService messages;

    ResourceCommandHandler(StarCoreContext context, MessageService messages) {
        this.context = context;
        this.messages = messages;
    }

    void handle(CommandSender sender, String label, String[] args) {
        ResourceService resourceService = context.serviceRegistry().find(ResourceService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (resourceService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.resource.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("command.resource.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (StarCoreCommandAliases.normalizeResourceSubcommand(args[1])) {
            case "status" -> resourceStatus(sender, args, nationService, resourceService);
            case "districts" -> resourceDistricts(sender, args, nationService);
            case "inspect" -> resourceInspect(sender, args, nationService);
            case "migrate" -> resourceMigrate(sender, args, nationService);
            case "grant" -> resourceGrant(sender, args, nationService, resourceService);
            case "consume" -> resourceConsume(sender, args, nationService, resourceService);
            default -> sender.sendMessage(Component.text(msg("command.resource.usage", label), NamedTextColor.YELLOW));
        }
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return prefixMatches(SUBCOMMAND_SUGGESTIONS, args[1]);
        }
        if (args.length == 3) {
            String subcommand = StarCoreCommandAliases.normalizeResourceSubcommand(args[1]);
            NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
            if (nationService != null && (subcommand.equals("status") || subcommand.equals("districts") || subcommand.equals("grant") || subcommand.equals("consume"))) {
                return prefixMatches(nationNameSuggestions(nationService), args[2]);
            }
            NationResourceDistrictService districtService = context.serviceRegistry().find(NationResourceDistrictService.class).orElse(null);
            if (districtService != null && (subcommand.equals("inspect") || subcommand.equals("migrate"))) {
                return prefixMatches(resourceDistrictCoordinateSuggestions(sender, nationService, districtService), args[2]);
            }
        }
        if (args.length == 4 && isStockpileMutation(args[1])) {
            ResourceService resourceService = context.serviceRegistry().find(ResourceService.class).orElse(null);
            if (resourceService != null) {
                return prefixMatches(resourceService.availableResourceTypes().stream().toList(), args[3]);
            }
        }
        if (args.length == 5 && isStockpileMutation(args[1])) {
            return prefixMatches(AMOUNT_SUGGESTIONS, args[4]);
        }
        return List.of();
    }

    private void resourceStatus(CommandSender sender, String[] args, NationService nationService, ResourceService resourceService) {
        Nation nation = resolveResourceNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.resource.status-usage"), NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text(msg("command.resource.stock-title"), NamedTextColor.GOLD)
            .append(Component.text(nation.name(), NamedTextColor.WHITE)));
        resourceService.stockpile(nation.id()).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(entry.getKey(), NamedTextColor.WHITE))
                .append(Component.text(msg("command.resource.stock-line", entry.getValue()), NamedTextColor.DARK_GRAY))));
        NationResourceDistrictService districtService = context.serviceRegistry().find(NationResourceDistrictService.class).orElse(null);
        if (districtService != null) {
            Collection<NationResourceDistrictSnapshot> districts = districtService.districtsOf(nation.id());
            sender.sendMessage(Component.text(msg("command.resource.district-count"), NamedTextColor.GRAY)
                .append(Component.text(msg("command.resource.district-count-value", districts.size(), districtService.districtLimitFor(nation)), NamedTextColor.WHITE)));
        }
    }

    private void resourceDistricts(CommandSender sender, String[] args, NationService nationService) {
        NationResourceDistrictService districtService = context.serviceRegistry().find(NationResourceDistrictService.class).orElse(null);
        if (districtService == null) {
            sender.sendMessage(Component.text(msg("command.resource.district-service-disabled"), NamedTextColor.RED));
            return;
        }
        Nation nation = resolveResourceNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.resource.districts-usage"), NamedTextColor.YELLOW));
            return;
        }
        Collection<NationResourceDistrictSnapshot> districts = districtService.districtsOf(nation.id());
        sender.sendMessage(Component.text(msg("command.resource.districts-title", nation.name()), NamedTextColor.GOLD)
            .append(Component.text(msg("command.resource.district-count-value", districts.size(), districtService.districtLimitFor(nation)), NamedTextColor.WHITE)));
        if (districts.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.resource.districts-empty"), NamedTextColor.GRAY));
            return;
        }
        districts.stream()
            .sorted(Comparator.comparing(snapshot -> snapshot.coordinate().toString()))
            .forEach(district -> {
                NationResourceDistrictOperationalOverview overview = operationalOverview(district);
                sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                    .append(Component.text(district.coordinate().toString(), NamedTextColor.WHITE))
                    .append(Component.text(msg(
                        "command.resource.district-line",
                        district.remainingResources(),
                        district.totalExperience(),
                        district.biomeName(),
                        localizedResourceMigrationState(district)
                    ), NamedTextColor.DARK_GRAY)));
                sender.sendMessage(Component.text(msg(
                    "command.resource.district-detail-line",
                    formatChunkCoordinate(district.pendingTarget()),
                    formatMoment(district.nextRefreshAtMillis()),
                    formatMoment(district.forceMigrationAtMillis())
                ), NamedTextColor.GRAY));
                sender.sendMessage(Component.text(msg(
                    "command.resource.district-cycle-line",
                    overview.expectedResourceYield(),
                    overview.expectedExperienceYield(),
                    formatDurationMinutes(overview.refreshCooldownMinutes())
                ), NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text(msg(
                    "command.resource.district-hourly-line",
                    formatHourlyRate(overview.expectedResourceYieldPerHour()),
                    formatHourlyRate(overview.expectedExperienceYieldPerHour())
                ), NamedTextColor.DARK_GRAY));
                sender.sendMessage(Component.text(msg(
                    "command.resource.district-forecast-line",
                    overview.forecastResourceYieldNext3Cycles(),
                    overview.forecastExperienceYieldNext3Cycles(),
                    formatDurationMinutes(overview.forecastWindowMinutesNext3Cycles())
                ), NamedTextColor.DARK_GRAY));
            });
    }

    private void resourceInspect(CommandSender sender, String[] args, NationService nationService) {
        NationResourceDistrictService districtService = context.serviceRegistry().find(NationResourceDistrictService.class).orElse(null);
        if (districtService == null) {
            sender.sendMessage(Component.text(msg("command.resource.district-service-disabled"), NamedTextColor.RED));
            return;
        }
        Optional<ChunkCoordinate> coordinate = resolveResourceDistrictCoordinate(sender, args, 2, "command.resource.inspect-usage");
        if (coordinate.isEmpty()) {
            return;
        }
        NationResourceDistrictSnapshot district = districtService.districtAt(coordinate.get()).orElse(null);
        if (district == null) {
            sender.sendMessage(Component.text(msg("command.resource.district-not-found", coordinate.get()), NamedTextColor.RED));
            return;
        }
        Nation nation = nationService.nationById(district.nationId()).orElse(null);
        String nationName = nation == null ? msg("resource.district.unknown-nation") : nation.name();
        NationOperationalOverview nationOverview = nation == null ? null : nationOperationalOverview(nationService, districtService, nation);
        NationResourceDistrictOperationalOverview overview = operationalOverview(district);
        sender.sendMessage(Component.text(msg("command.resource.inspect-title", district.coordinate()), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(msg("command.resource.inspect-nation", nationName), NamedTextColor.WHITE));
        if (nation != null && nationOverview != null) {
            sender.sendMessage(Component.text(msg("command.resource.inspect-founder", nationOverview.founderName()), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(msg("command.resource.inspect-government", nation.governmentType().displayName()), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(msg("command.resource.inspect-members", nationOverview.memberCount()), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(msg("command.resource.inspect-nation-level", nationOverview.level(), nationOverview.experience()), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(msg("command.resource.inspect-nation-level-progress", formatNationLevelProgress(nationOverview)), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(msg("command.resource.inspect-claims", nationOverview.claimCount(), nationOverview.claimLimit()), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(msg("command.resource.inspect-city-states", nationOverview.cityStateCount(), nationOverview.cityStateLimit()), NamedTextColor.WHITE));
            sender.sendMessage(Component.text(msg("command.resource.inspect-district-capacity", nationOverview.resourceDistrictCount(), nationOverview.resourceDistrictLimit()), NamedTextColor.WHITE));
        }
        sender.sendMessage(Component.text(msg("command.resource.inspect-biome", district.biomeName(), formatRichness(district.biomeRichness())), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-resources", district.remainingResources(), district.totalExperience()), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-cycle-resources", overview.expectedResourceYield()), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-cycle-experience", overview.expectedExperienceYield()), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-refresh-cycle", formatDurationMinutes(overview.refreshCooldownMinutes())), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-hourly-resources", formatHourlyRate(overview.expectedResourceYieldPerHour())), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-hourly-experience", formatHourlyRate(overview.expectedExperienceYieldPerHour())), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg(
            "command.resource.inspect-forecast-next-three",
            overview.forecastResourceYieldNext3Cycles(),
            overview.forecastExperienceYieldNext3Cycles()
        ), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-forecast-window", formatDurationMinutes(overview.forecastWindowMinutesNext3Cycles())), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-beacon", district.beaconX(), district.beaconY(), district.beaconZ()), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-migration", localizedResourceMigrationState(district)), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-pending-target", formatChunkCoordinate(district.pendingTarget())), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-next-refresh", formatMoment(district.nextRefreshAtMillis())), NamedTextColor.WHITE));
        sender.sendMessage(Component.text(msg("command.resource.inspect-force-migration", formatMoment(district.forceMigrationAtMillis())), NamedTextColor.WHITE));
    }

    private void resourceMigrate(CommandSender sender, String[] args, NationService nationService) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(msg("command.player-only"), NamedTextColor.RED));
            return;
        }
        NationResourceDistrictService districtService = context.serviceRegistry().find(NationResourceDistrictService.class).orElse(null);
        if (districtService == null) {
            sender.sendMessage(Component.text(msg("command.resource.district-service-disabled"), NamedTextColor.RED));
            return;
        }
        Optional<ChunkCoordinate> coordinate = resolveResourceDistrictCoordinate(sender, args, 2, "command.resource.migrate-usage");
        if (coordinate.isEmpty()) {
            return;
        }
        NationResourceDistrictSnapshot district = districtService.districtAt(coordinate.get()).orElse(null);
        if (district == null) {
            sender.sendMessage(Component.text(msg("command.resource.district-not-found", coordinate.get()), NamedTextColor.RED));
            return;
        }
        NationResourceDistrictMigrationResult result = districtService.beginMigration(player, district.id());
        sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        NationResourceDistrictSnapshot snapshot = result.snapshot() == null ? district : result.snapshot();
        if (!result.success()) {
            sendMigrationExplanation(sender, result, snapshot, nationService);
        }
        sender.sendMessage(Component.text(msg("command.resource.migrate-followup", snapshot.coordinate(), localizedResourceMigrationState(snapshot)), NamedTextColor.GRAY));
    }

    private void resourceGrant(CommandSender sender, String[] args, NationService nationService, ResourceService resourceService) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.resource.no-admin"), NamedTextColor.RED));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text(msg("command.resource.grant-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.resource.nation-not-found"), NamedTextColor.RED));
            return;
        }
        Optional<Long> amount = parseLongAmount(args[4]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.resource.amount-positive-integer"), NamedTextColor.RED));
            return;
        }
        if (!resourceService.grant(nation.id(), args[3], amount.get())) {
            sender.sendMessage(Component.text(msg("command.resource.unknown-type", String.join(", ", resourceService.availableResourceTypes())), NamedTextColor.RED));
            return;
        }
        String resourceType = args[3].toLowerCase(Locale.ROOT);
        String reason = commandReason(args, 5);
        recordNationEvent(
            nation,
            "resource.granted",
            msg("command.event.message.resource-granted", sender.getName(), amount.get(), resourceType, displayReason(reason)),
            ledgerContext(sender.getName(), String.valueOf(amount.get()), resourceType, reason)
        );
        sender.sendMessage(Component.text(msg("command.resource.granted", nation.name(), amount.get(), resourceType), NamedTextColor.GREEN));
    }

    private void resourceConsume(CommandSender sender, String[] args, NationService nationService, ResourceService resourceService) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.resource.no-admin"), NamedTextColor.RED));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text(msg("command.resource.consume-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.resource.nation-not-found"), NamedTextColor.RED));
            return;
        }
        Optional<Long> amount = parseLongAmount(args[4]);
        if (amount.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.resource.amount-positive-integer"), NamedTextColor.RED));
            return;
        }
        if (!resourceService.consume(nation.id(), args[3], amount.get())) {
            sender.sendMessage(Component.text(msg("command.resource.consume-failed"), NamedTextColor.RED));
            return;
        }
        String resourceType = args[3].toLowerCase(Locale.ROOT);
        String reason = commandReason(args, 5);
        recordNationEvent(
            nation,
            "resource.consumed",
            msg("command.event.message.resource-consumed", sender.getName(), amount.get(), resourceType, displayReason(reason)),
            ledgerContext(sender.getName(), String.valueOf(amount.get()), resourceType, reason)
        );
        sender.sendMessage(Component.text(msg("command.resource.consumed", nation.name(), amount.get(), resourceType), NamedTextColor.GREEN));
    }

    private Nation resolveResourceNation(CommandSender sender, String[] args, NationService nationService) {
        if (args.length >= 3) {
            return nationService.nationByName(args[2]).orElse(null);
        }
        if (sender instanceof Player player) {
            return nationService.nationOf(player.getUniqueId()).orElse(null);
        }
        return null;
    }

    private Optional<ChunkCoordinate> resolveResourceDistrictCoordinate(CommandSender sender, String[] args, int argumentIndex, String usageKey) {
        if (args.length > argumentIndex) {
            Optional<ChunkCoordinate> coordinate = parseChunkCoordinate(args[argumentIndex]);
            if (coordinate.isPresent()) {
                return coordinate;
            }
            sender.sendMessage(Component.text(msg("command.resource.invalid-coordinate"), NamedTextColor.RED));
            return Optional.empty();
        }
        if (sender instanceof Player player) {
            return Optional.of(new ChunkCoordinate(
                player.getWorld().getName(),
                player.getLocation().getChunk().getX(),
                player.getLocation().getChunk().getZ()
            ));
        }
        sender.sendMessage(Component.text(msg(usageKey), NamedTextColor.YELLOW));
        return Optional.empty();
    }

    private Optional<ChunkCoordinate> parseChunkCoordinate(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.trim().split(":");
        if (parts.length != 3 || parts[0].isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ChunkCoordinate(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private List<String> resourceDistrictCoordinateSuggestions(CommandSender sender, NationService nationService, NationResourceDistrictService districtService) {
        Collection<NationResourceDistrictSnapshot> snapshots = districtService.districts();
        if (sender instanceof Player player && nationService != null) {
            Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
            if (nation != null) {
                snapshots = districtService.districtsOf(nation.id());
            }
        }
        return snapshots.stream()
            .map(snapshot -> snapshot.coordinate().toString())
            .sorted()
            .toList();
    }

    private List<String> nationNameSuggestions(NationService nationService) {
        return nationService.nations().stream()
            .map(Nation::name)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private boolean isStockpileMutation(String value) {
        String subcommand = StarCoreCommandAliases.normalizeResourceSubcommand(value);
        return subcommand.equals("grant") || subcommand.equals("consume");
    }

    private Optional<Long> parseLongAmount(String raw) {
        try {
            long amount = Long.parseLong(raw);
            return amount > 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private void recordNationEvent(Nation nation, String type, String message, String eventContext) {
        EventService eventService = context.serviceRegistry().find(EventService.class).orElse(null);
        if (eventService == null || nation == null) {
            return;
        }
        try {
            eventService.record(nation.id(), type, message, eventContext);
        } catch (RuntimeException ignored) {
        }
    }

    private String commandReason(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length)).trim();
    }

    private String displayReason(String reason) {
        return reason == null || reason.isBlank() ? msg("command.event.reason-unspecified") : reason.trim();
    }

    private String ledgerContext(String actor, String amount, String resourceType, String reason) {
        List<String> entries = new ArrayList<>();
        addContextEntry(entries, "actor", actor);
        addContextEntry(entries, "amount", amount);
        addContextEntry(entries, "resource", resourceType);
        addContextEntry(entries, "reason", reason);
        return String.join(";", entries);
    }

    private void addContextEntry(List<String> entries, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        entries.add(key + "=" + sanitizeContextValue(value));
    }

    private String sanitizeContextValue(String value) {
        return value == null ? "" : value.replace(';', ',').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String localizedResourceMigrationState(NationResourceDistrictSnapshot district) {
        return NationResourceDistrictViewSupport.localizedMigrationLabel(messages, district);
    }

    private NationResourceDistrictOperationalOverview operationalOverview(NationResourceDistrictSnapshot district) {
        return NationResourceDistrictOperationalSupport.overview(context == null ? null : context.configuration(), district);
    }

    private void sendMigrationExplanation(
        CommandSender sender,
        NationResourceDistrictMigrationResult result,
        NationResourceDistrictSnapshot snapshot,
        NationService nationService
    ) {
        ClaimSelectionExplanation explanation = result == null ? null : result.explanation();
        if (sender instanceof Player player && context != null && context.economyService() != null) {
            OnlinePlayerDirectory onlinePlayerDirectory = context.serviceRegistry() == null
                ? null
                : context.serviceRegistry().find(OnlinePlayerDirectory.class).orElse(null);
            NationResourceDistrictCommandSupport.CommandState state = NationResourceDistrictCommandSupport.resolve(
                context.configuration(),
                context.economyService(),
                nationService,
                onlinePlayerDirectory,
                player.getUniqueId(),
                snapshot,
                NationResourceDistrictCommandSupport.actionStateForResultCode(result == null ? null : result.code())
            );
            NationResourceDistrictCommandSupport.CommandPresentation presentation =
                NationResourceDistrictCommandSupport.presentation(messages, state);
            explanation = NationResourceDistrictCommandSupport.explanation(state, presentation);
        }
        if (explanation == null) {
            return;
        }
        explanation.reasons().stream()
            .map(reason -> reason.message())
            .filter(message -> message != null && !message.isBlank())
            .limit(3)
            .forEach(message -> sender.sendMessage(Component.text("- " + message, NamedTextColor.GRAY)));
    }

    private NationOperationalOverview nationOperationalOverview(
        NationService nationService,
        NationResourceDistrictService districtService,
        Nation nation
    ) {
        return NationOperationalSupport.overview(
            context == null ? null : context.configuration(),
            nationService,
            districtService,
            nation
        );
    }

    private String formatNationLevelProgress(NationOperationalOverview overview) {
        if (overview == null) {
            return msg("command.resource.none");
        }
        if (overview.maxLevelReached()) {
            return msg("command.nation.level-progress-max");
        }
        return msg(
            "command.nation.level-progress-detail",
            overview.currentLevelProgress(),
            overview.nextLevelExperienceRequired(),
            overview.remainingExperienceToNextLevel()
        );
    }

    private String formatChunkCoordinate(ChunkCoordinate coordinate) {
        return coordinate == null ? msg("command.resource.none") : coordinate.toString();
    }

    private String formatDurationMinutes(long minutes) {
        return EpochService.humanDuration(Duration.ofMinutes(Math.max(0L, minutes)));
    }

    private String formatMoment(long epochMillis) {
        if (epochMillis <= 0L) {
            return msg("command.resource.none");
        }
        Instant instant = Instant.ofEpochMilli(epochMillis);
        String formatted = instant.atZone(ZoneId.systemDefault()).format(COMMAND_TIME_FORMATTER);
        long remainingMillis = epochMillis - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return msg("command.resource.time-ready", formatted);
        }
        return msg("command.resource.time-with-remaining", formatted, EpochService.humanDuration(Duration.ofMillis(remainingMillis)));
    }

    private String formatRichness(double richness) {
        return String.format(Locale.ROOT, "%.2f", richness);
    }

    private String formatHourlyRate(double rate) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0D, rate));
    }

    private List<String> prefixMatches(List<String> candidates, String prefix) {
        String normalized = StarCoreCommandAliases.normalizeToken(prefix);
        List<String> suggestions = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                suggestions.add(candidate);
            }
        }
        return suggestions;
    }

    private String msg(String key, Object... args) {
        return messages.format(key, args);
    }
}
