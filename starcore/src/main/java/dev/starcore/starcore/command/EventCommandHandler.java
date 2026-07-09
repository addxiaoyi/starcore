package dev.starcore.starcore.command;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.foundation.epoch.EpochService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.event.LedgerCategoryService;
import dev.starcore.starcore.module.event.NationEventRecord;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class EventCommandHandler {
    private static final List<String> SUBCOMMAND_SUGGESTIONS = List.of("ls", "a", "ex", "r", "x", "list", "audit", "export", "record", "clear", "列", "列表", "审", "审计", "账", "账本", "导", "导出", "记", "记录", "清", "清除");
    private static final List<String> FILTER_SUGGESTIONS = List.of("all", "finance", "resource-income", "income", "reward", "tax", "deposit", "withdraw", "treasury", "resource", "nation", "territory", "city_state", "policy", "technology", "diplomacy", "war", "officer", "resolution",
        "全部", "财政", "账本", "资源产出", "日常收入", "任务奖励", "税收", "存入", "支出", "金库", "资源", "国家", "领地", "城邦", "国策", "科技", "外交", "战争", "官员", "提案", "决议");
    private static final List<String> PAGE_SUGGESTIONS = List.of("1", "2", "3", "24h", "7d", "30d", "1天", "7天");
    private static final List<String> PAGE_SIZE_SUGGESTIONS = List.of("10", "25", "50");
    private static final List<String> EVENT_TYPE_SUGGESTIONS = List.of(
        "nation.created",
        "territory.claimed",
        "territory.unclaimed",
        "resolution.proposed",
        "resolution.signed",
        "treasury.deposit",
        "treasury.withdraw",
        "treasury.income",
        "treasury.reward",
        "treasury.tax",
        "resource.granted",
        "resource.consumed",
        "resource.migration.requested",
        "resource.migration.completed",
        "policy.set",
        "technology.unlocked",
        "diplomacy.updated",
        "war.declared",
        "officer.appointed"
    );
    private static final DateTimeFormatter COMMAND_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StarCoreContext context;
    private final MessageService messages;
    private final LedgerCategoryService ledgerCategories;

    EventCommandHandler(StarCoreContext context, MessageService messages) {
        this.context = context;
        this.messages = messages;
        this.ledgerCategories = new LedgerCategoryService(context.configuration());
    }

    void handle(CommandSender sender, String label, String[] args) {
        EventService eventService = context.serviceRegistry().find(EventService.class).orElse(null);
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (eventService == null || nationService == null) {
            sender.sendMessage(Component.text(msg("command.event.disabled-service"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(msg("command.event.usage", label), NamedTextColor.YELLOW));
            return;
        }
        switch (normalizeEventSubcommand(args[1])) {
            case "list" -> eventList(sender, args, nationService, eventService);
            case "audit" -> eventAudit(sender, args, nationService, eventService);
            case "export" -> eventExport(sender, args, nationService, eventService);
            case "record" -> eventRecord(sender, args, nationService, eventService);
            case "clear" -> eventClear(sender, args, nationService, eventService);
            default -> sender.sendMessage(Component.text(msg("command.event.usage", label), NamedTextColor.YELLOW));
        }
    }

    List<String> complete(String[] args) {
        if (args.length == 2) {
            return prefixMatches(SUBCOMMAND_SUGGESTIONS, args[1]);
        }
        if (args.length == 3) {
            return prefixMatches(nationNameSuggestions(), args[2]);
        }
        if (args.length == 4) {
            String subcommand = normalizeEventSubcommand(args[1]);
            if (subcommand.equals("list") || subcommand.equals("audit") || subcommand.equals("export")) {
                return prefixMatches(ledgerCategories.suggestions(FILTER_SUGGESTIONS), args[3]);
            }
            if (subcommand.equals("record")) {
                return prefixMatches(EVENT_TYPE_SUGGESTIONS, args[3]);
            }
        }
        if (args.length == 5 && isListLikeSubcommand(args[1])) {
            String subcommand = normalizeEventSubcommand(args[1]);
            List<String> suggestions = subcommand.equals("export") ? merged(PAGE_SUGGESTIONS, List.of("csv", "json")) : PAGE_SUGGESTIONS;
            return prefixMatches(suggestions, args[4]);
        }
        if (args.length == 6 && isListLikeSubcommand(args[1])) {
            String subcommand = normalizeEventSubcommand(args[1]);
            List<String> suggestions = subcommand.equals("export") ? List.of("csv", "json") : PAGE_SIZE_SUGGESTIONS;
            return prefixMatches(suggestions, args[5]);
        }
        return List.of();
    }

    private void eventList(CommandSender sender, String[] args, NationService nationService, EventService eventService) {
        Nation nation = resolveEventNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.event.list-usage"), NamedTextColor.YELLOW));
            return;
        }
        EventFilterSelection selection = selectEventFilter(args, "all");
        String filter = selection.filter();
        EventQueryOptions options = parseEventQueryOptions(args, selection.queryStartIndex());
        if (!options.valid()) {
            sender.sendMessage(Component.text(msg("command.event.invalid-page"), NamedTextColor.RED));
            return;
        }
        List<NationEventRecord> events = filteredEvents(eventService, nation, filter, options);
        sender.sendMessage(Component.text(msg("command.event.list-title"), NamedTextColor.GOLD)
            .append(Component.text(nation.name(), NamedTextColor.WHITE)));
        if (!filter.equals("all") || selection.filterExplicit()) {
            sender.sendMessage(Component.text(msg("command.event.filter-label", displayEventFilter(filter)), NamedTextColor.GRAY));
        }
        sendEventQueryOverview(sender, events, options);
        if (events.isEmpty()) {
            sender.sendMessage(Component.text(selection.filterExplicit() ? msg("command.event.filter-empty") : msg("command.event.empty"), NamedTextColor.GRAY));
            return;
        }
        List<NationEventRecord> page = eventPage(events, options);
        if (page.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.event.page-empty"), NamedTextColor.GRAY));
            return;
        }
        sendEventRows(sender, page);
    }

    private void eventAudit(CommandSender sender, String[] args, NationService nationService, EventService eventService) {
        Nation nation = resolveEventNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.event.audit-usage"), NamedTextColor.YELLOW));
            return;
        }
        EventFilterSelection selection = selectEventFilter(args, "finance");
        String filter = selection.filter();
        EventQueryOptions options = parseEventQueryOptions(args, selection.queryStartIndex());
        if (!options.valid()) {
            sender.sendMessage(Component.text(msg("command.event.invalid-page"), NamedTextColor.RED));
            return;
        }
        List<NationEventRecord> events = filteredEvents(eventService, nation, filter, options);
        sender.sendMessage(Component.text(msg("command.event.audit-title"), NamedTextColor.GOLD)
            .append(Component.text(nation.name(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text(msg("command.event.filter-label", displayEventFilter(filter)), NamedTextColor.GRAY));
        sendEventQueryOverview(sender, events, options);
        if (events.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.event.filter-empty"), NamedTextColor.GRAY));
            return;
        }
        List<NationEventRecord> page = eventPage(events, options);
        if (page.isEmpty()) {
            sender.sendMessage(Component.text(msg("command.event.page-empty"), NamedTextColor.GRAY));
            return;
        }
        sendEventRows(sender, page);
    }

    private void eventExport(CommandSender sender, String[] args, NationService nationService, EventService eventService) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.event.no-export-permission"), NamedTextColor.RED));
            return;
        }
        Nation nation = resolveEventNation(sender, args, nationService);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.event.export-usage"), NamedTextColor.YELLOW));
            return;
        }
        EventExportRequest request = parseEventExportRequest(args);
        if (!request.valid()) {
            sender.sendMessage(Component.text(msg("command.event.invalid-export"), NamedTextColor.RED));
            return;
        }
        List<NationEventRecord> events = filteredEvents(eventService, nation, request.filter(), request.options());
        try {
            Path output = writeEventExport(nation, request.filter(), request.format(), request.options(), events);
            sender.sendMessage(Component.text(msg("command.event.export-success", events.size(), request.format(), output.toAbsolutePath()), NamedTextColor.GREEN));
        } catch (IOException exception) {
            sender.sendMessage(Component.text(msg("command.event.export-failed", exception.getMessage()), NamedTextColor.RED));
        }
    }

    private void eventRecord(CommandSender sender, String[] args, NationService nationService, EventService eventService) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.event.no-record-permission"), NamedTextColor.RED));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text(msg("command.event.record-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.event.nation-not-found"), NamedTextColor.RED));
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        try {
            NationEventRecord record = eventService.record(nation.id(), args[3], message);
            sender.sendMessage(Component.text(msg("command.event.recorded-success"), NamedTextColor.GREEN)
                .append(Component.text(record.id().toString(), NamedTextColor.WHITE)));
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text(msg("command.event.record-failed", exception.getMessage()), NamedTextColor.RED));
        }
    }

    private void eventClear(CommandSender sender, String[] args, NationService nationService, EventService eventService) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage(Component.text(msg("command.event.no-clear-permission"), NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(msg("command.event.clear-usage"), NamedTextColor.YELLOW));
            return;
        }
        Nation nation = nationService.nationByName(args[2]).orElse(null);
        if (nation == null) {
            sender.sendMessage(Component.text(msg("command.event.nation-not-found"), NamedTextColor.RED));
            return;
        }
        eventService.clear(nation.id());
        sender.sendMessage(Component.text(msg("command.event.cleared-success"), NamedTextColor.GREEN)
            .append(Component.text(nation.name(), NamedTextColor.WHITE)));
    }

    private Nation resolveEventNation(CommandSender sender, String[] args, NationService nationService) {
        if (args.length >= 3) {
            return nationService.nationByName(args[2]).orElse(null);
        }
        if (sender instanceof Player player) {
            return nationService.nationOf(player.getUniqueId()).orElse(null);
        }
        return null;
    }

    private List<NationEventRecord> filteredEvents(EventService eventService, Nation nation, String filter, EventQueryOptions options) {
        return eventService.eventsOf(nation.id()).stream()
            .filter(record -> ledgerCategories.matchesEventFilter(record.type(), filter))
            .filter(record -> options.since() == null || !record.occurredAt().isBefore(options.since()))
            .toList();
    }

    private void sendEventQueryOverview(CommandSender sender, List<NationEventRecord> events, EventQueryOptions options) {
        int totalPages = Math.max(1, (int) Math.ceil(events.size() / (double) options.pageSize()));
        sender.sendMessage(Component.text(msg("command.event.page-label", options.page(), totalPages, options.pageSize(), events.size()), NamedTextColor.GRAY));
        if (options.window() != null) {
            sender.sendMessage(Component.text(msg("command.event.time-window-label", EpochService.humanDuration(options.window())), NamedTextColor.GRAY));
        }
    }

    private List<NationEventRecord> eventPage(List<NationEventRecord> events, EventQueryOptions options) {
        int fromIndex = (options.page() - 1) * options.pageSize();
        if (fromIndex < 0 || fromIndex >= events.size()) {
            return List.of();
        }
        int toIndex = Math.min(events.size(), fromIndex + options.pageSize());
        return events.subList(fromIndex, toIndex);
    }

    private void sendEventRows(CommandSender sender, List<NationEventRecord> events) {
        events.forEach(record -> {
            sender.sendMessage(Component.text("- ", NamedTextColor.GRAY)
                .append(Component.text(formatCommandTime(record.occurredAt()), NamedTextColor.DARK_GRAY))
                .append(Component.text(" [" + displayEventType(record.type()) + "] ", NamedTextColor.YELLOW))
                .append(Component.text(record.message(), NamedTextColor.WHITE)));
            String context = formatEventContext(record.context());
            if (!context.isBlank()) {
                sender.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                    .append(Component.text(msg("command.event.context-label", context), NamedTextColor.DARK_GRAY)));
            }
        });
    }

    private EventExportRequest parseEventExportRequest(String[] args) {
        String format = "csv";
        int effectiveLength = args.length;
        if (args.length >= 4) {
            String maybeFormat = normalizeToken(args[args.length - 1]);
            if (maybeFormat.equals("csv") || maybeFormat.equals("json")) {
                format = maybeFormat;
                effectiveLength--;
            }
        }
        String[] effectiveArgs = Arrays.copyOf(args, effectiveLength);
        EventFilterSelection selection = selectEventFilter(effectiveArgs, "finance");
        EventQueryOptions options = parseEventQueryOptions(effectiveArgs, selection.queryStartIndex());
        return new EventExportRequest(selection.filter(), format, options, options.valid());
    }

    private Path writeEventExport(Nation nation, String filter, String format, EventQueryOptions options, List<NationEventRecord> events) throws IOException {
        Path directory = eventExportDirectory();
        Files.createDirectories(directory);
        String fileName = eventExportFileName(nation, filter, options, format);
        Path output = directory.resolve(fileName);
        String content = format.equals("json") ? renderEventExportJson(nation, filter, options, events) : renderEventExportCsv(nation, filter, options, events);
        Files.writeString(output, content, StandardCharsets.UTF_8);
        return output;
    }

    private Path eventExportDirectory() {
        if (context.plugin() != null) {
            return context.plugin().getDataFolder().toPath().resolve("exports").resolve("events");
        }
        return Path.of("target", "starcore-event-exports-test");
    }

    private String renderEventExportCsv(Nation nation, String filter, EventQueryOptions options, List<NationEventRecord> events) {
        StringBuilder builder = new StringBuilder();
        if (eventExportCsvBomEnabled()) {
            builder.append('\ufeff');
        }
        builder.append("nation_id,nation_name,filter,range,from,to,event_id,occurred_at,type,localized_type,message,amount,actor,reason,balance,context").append(System.lineSeparator());
        for (NationEventRecord event : events) {
            Map<String, String> contextEntries = parseEventContextEntries(event.context());
            builder.append(csv(nation.id().toString())).append(',')
                .append(csv(nation.name())).append(',')
                .append(csv(normalizeEventFilter(filter))).append(',')
                .append(csv(eventExportRangeLabel(options))).append(',')
                .append(csv(options.since() == null ? "" : options.since().toString())).append(',')
                .append(csv("")).append(',')
                .append(csv(event.id().toString())).append(',')
                .append(csv(event.occurredAt().toString())).append(',')
                .append(csv(event.type())).append(',')
                .append(csv(displayEventType(event.type()))).append(',')
                .append(csv(event.message())).append(',')
                .append(csv(contextEntries.getOrDefault("amount", ""))).append(',')
                .append(csv(contextEntries.getOrDefault("actor", ""))).append(',')
                .append(csv(contextEntries.getOrDefault("reason", ""))).append(',')
                .append(csv(contextEntries.getOrDefault("balance", ""))).append(',')
                .append(csv(event.context()))
                .append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String eventExportFileName(Nation nation, String filter, EventQueryOptions options, String format) {
        String nationId = nation.id().toString().replace("-", "");
        if (nationId.length() > 8) {
            nationId = nationId.substring(0, 8);
        }
        return "starcore-event-"
            + safeExportFileSegment(nation.name())
            + "-"
            + nationId
            + "-"
            + safeExportFileSegment(normalizeEventFilter(filter))
            + "-"
            + safeExportFileSegment(eventExportRangeLabel(options))
            + "-"
            + Instant.now().toEpochMilli()
            + "."
            + format;
    }

    private String eventExportRangeLabel(EventQueryOptions options) {
        if (options == null || options.window() == null) {
            return "all";
        }
        Duration duration = options.window();
        if (duration.toDaysPart() == 0 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return "all";
        }
        if (duration.toDays() >= 2 && duration.toHoursPart() == 0 && duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toDays() + "d";
        }
        if (duration.toMinutesPart() == 0 && duration.toSecondsPart() == 0) {
            return duration.toHours() + "h";
        }
        if (duration.toSecondsPart() == 0) {
            return duration.toMinutes() + "m";
        }
        return duration.toSeconds() + "s";
    }

    private boolean eventExportCsvBomEnabled() {
        return context.configuration() == null || context.configuration().mapWebFinanceExportCsvBomEnabled();
    }

    private Map<String, String> parseEventContextEntries(String context) {
        if (context == null || context.isBlank()) {
            return Map.of();
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (String entry : context.trim().split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            String key = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!key.isBlank() && !value.isBlank()) {
                entries.put(key, value);
            }
        }
        return entries;
    }

    private String safeExportFileSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "events";
        }
        StringBuilder builder = new StringBuilder(raw.length());
        boolean lastUnderscore = false;
        for (int offset = 0; offset < raw.length(); ) {
            int codePoint = raw.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '.' || codePoint == '_' || codePoint == '-') {
                builder.appendCodePoint(codePoint);
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                builder.append('_');
                lastUnderscore = true;
            }
        }
        String value = builder.toString().replaceAll("^_+|_+$", "");
        return value.isBlank() ? "events" : value;
    }

    private String renderEventExportJson(Nation nation, String filter, EventQueryOptions options, List<NationEventRecord> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n")
            .append("  \"nation\": \"").append(json(nation.name())).append("\",\n")
            .append("  \"nationId\": \"").append(json(nation.id().toString())).append("\",\n")
            .append("  \"filter\": \"").append(json(normalizeEventFilter(filter))).append("\",\n")
            .append("  \"range\": \"").append(json(eventExportRangeLabel(options))).append("\",\n")
            .append("  \"from\": \"").append(json(options == null || options.since() == null ? "" : options.since().toString())).append("\",\n")
            .append("  \"to\": \"\",\n")
            .append("  \"total\": ").append(events.size()).append(",\n")
            .append("  \"exportedAt\": \"").append(json(Instant.now().toString())).append("\",\n")
            .append("  \"events\": [\n");
        for (int index = 0; index < events.size(); index++) {
            NationEventRecord event = events.get(index);
            builder.append("    {")
                .append("\"id\":\"").append(json(event.id().toString())).append("\",")
                .append("\"nationId\":\"").append(json(event.nationId().value().toString())).append("\",")
                .append("\"occurredAt\":\"").append(json(event.occurredAt().toString())).append("\",")
                .append("\"type\":\"").append(json(event.type())).append("\",")
                .append("\"localizedType\":\"").append(json(displayEventType(event.type()))).append("\",")
                .append("\"message\":\"").append(json(event.message())).append("\",")
                .append("\"context\":\"").append(json(event.context())).append("\"")
                .append("}");
            if (index + 1 < events.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n}");
        return builder.toString();
    }

    private EventFilterSelection selectEventFilter(String[] args, String defaultFilter) {
        if (args.length < 4) {
            return new EventFilterSelection(defaultFilter, 3, false);
        }
        String candidate = normalizeToken(args[3]);
        if (isEventQueryToken(candidate)) {
            return new EventFilterSelection(defaultFilter, 3, false);
        }
        return new EventFilterSelection(args[3], 4, true);
    }

    private boolean isEventQueryToken(String token) {
        return parseEventWindow(token).isPresent() || parsePositiveInt(token).isPresent();
    }

    private EventQueryOptions parseEventQueryOptions(String[] args, int startIndex) {
        int page = 1;
        int pageSize = 10;
        Duration window = null;
        int numericSeen = 0;
        boolean valid = true;
        for (int index = startIndex; index < args.length; index++) {
            String token = normalizeToken(args[index]);
            if (token.isBlank()) {
                continue;
            }
            Optional<Duration> parsedWindow = parseEventWindow(token);
            if (parsedWindow.isPresent()) {
                window = parsedWindow.get();
                continue;
            }
            Optional<Integer> number = parsePositiveInt(token);
            if (number.isPresent()) {
                if (numericSeen == 0) {
                    page = number.get();
                } else if (numericSeen == 1) {
                    pageSize = Math.min(50, number.get());
                } else {
                    valid = false;
                }
                numericSeen++;
                continue;
            }
            valid = false;
        }
        Instant since = window == null ? null : Instant.now().minus(window);
        return new EventQueryOptions(page, pageSize, window, since, valid && page > 0 && pageSize > 0);
    }

    private Optional<Duration> parseEventWindow(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("分钟")) {
            return parseLongAmount(normalized.substring(0, normalized.length() - 2)).map(Duration::ofMinutes);
        }
        if (normalized.endsWith("小时")) {
            return parseLongAmount(normalized.substring(0, normalized.length() - 2)).map(Duration::ofHours);
        }
        if (normalized.endsWith("天")) {
            return parseLongAmount(normalized.substring(0, normalized.length() - 1)).map(Duration::ofDays);
        }
        if (normalized.length() < 2) {
            return Optional.empty();
        }
        char suffix = normalized.charAt(normalized.length() - 1);
        String number = normalized.substring(0, normalized.length() - 1);
        return switch (suffix) {
            case 'm' -> parseLongAmount(number).map(Duration::ofMinutes);
            case 'h' -> parseLongAmount(number).map(Duration::ofHours);
            case 'd' -> parseLongAmount(number).map(Duration::ofDays);
            default -> Optional.empty();
        };
    }

    private Optional<Long> parseLongAmount(String raw) {
        try {
            long amount = Long.parseLong(raw);
            return amount > 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<Integer> parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private boolean isListLikeSubcommand(String value) {
        String subcommand = normalizeEventSubcommand(value);
        return subcommand.equals("list") || subcommand.equals("audit") || subcommand.equals("export");
    }

    private String normalizeEventFilter(String filter) {
        return ledgerCategories.normalizeEventFilter(filter);
    }

    private String displayEventFilter(String filter) {
        String suffix = ledgerCategories.eventFilterMessageKeySuffix(filter);
        return suffix.isBlank() ? normalizeEventFilter(filter) : msg(eventFilterMessageKey(suffix));
    }

    private String displayEventType(String type) {
        String localized = localizedEventType(type);
        String normalized = normalizeToken(type);
        if (localized.equals(normalized)) {
            return normalized;
        }
        return localized + " / " + normalized;
    }

    private String localizedEventType(String type) {
        return switch (normalizeToken(type)) {
            case "nation.created" -> msg("command.event.type.nation-created");
            case "city_state.created" -> msg("command.event.type.city-state-created");
            case "territory.claimed" -> msg("command.event.type.territory-claimed");
            case "territory.unclaimed" -> msg("command.event.type.territory-unclaimed");
            case "resolution.proposed" -> msg("command.event.type.resolution-proposed");
            case "resolution.signed" -> msg("command.event.type.resolution-signed");
            case "diplomacy.updated" -> msg("command.event.type.diplomacy-updated");
            case "diplomacy.proposed" -> msg("command.event.type.diplomacy-proposed");
            case "treasury.deposit" -> msg("command.event.type.treasury-deposit");
            case "treasury.withdraw" -> msg("command.event.type.treasury-withdraw");
            case "treasury.income" -> msg("command.event.type.treasury-income");
            case "treasury.reward" -> msg("command.event.type.treasury-reward");
            case "treasury.tax" -> msg("command.event.type.treasury-tax");
            case "policy.set" -> msg("command.event.type.policy-set");
            case "policy.cleared" -> msg("command.event.type.policy-cleared");
            case "resource.granted" -> msg("command.event.type.resource-granted");
            case "resource.consumed" -> msg("command.event.type.resource-consumed");
            case "resource.migration.requested" -> msg("command.event.type.resource-migration-requested");
            case "resource.migration.target-selected" -> msg("command.event.type.resource-migration-target-selected");
            case "resource.migration.completed" -> msg("command.event.type.resource-migration-completed");
            case "resource.migration.completed-forced" -> msg("command.event.type.resource-migration-completed-forced");
            case "technology.unlocked" -> msg("command.event.type.technology-unlocked");
            case "technology.revoked" -> msg("command.event.type.technology-revoked");
            case "war.declared" -> msg("command.event.type.war-declared");
            case "war.ended" -> msg("command.event.type.war-ended");
            case "officer.appointed" -> msg("command.event.type.officer-appointed");
            case "officer.removed" -> msg("command.event.type.officer-removed");
            default -> normalizeToken(type);
        };
    }

    private String formatEventContext(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        String trimmed = context.trim();
        if (!trimmed.contains("=")) {
            return trimmed;
        }
        List<String> parts = new ArrayList<>();
        for (String entry : trimmed.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                continue;
            }
            String key = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!value.isBlank()) {
                parts.add(msg(eventContextMessageKey(contextKeyLabel(key)), value));
            }
        }
        return parts.isEmpty() ? trimmed : String.join(" | ", parts);
    }

    private String eventFilterMessageKey(String suffix) {
        return "command.event.filter." + suffix;
    }

    private String eventContextMessageKey(String suffix) {
        return "command.event.context." + suffix;
    }

    private String contextKeyLabel(String key) {
        return switch (normalizeToken(key)) {
            case "actor" -> "actor";
            case "amount" -> "amount";
            case "operation" -> "operation";
            case "target" -> "target";
            case "targetid" -> "targetId";
            case "resolutionid" -> "resolutionId";
            case "kind" -> "kind";
            case "state" -> "state";
            case "summary" -> "summary";
            case "resource" -> "resource";
            case "balance" -> "balance";
            case "members" -> "members";
            case "claims" -> "claims";
            case "relation" -> "relation";
            case "relationdisplay" -> "relationDisplay";
            case "pairid" -> "pairId";
            case "role" -> "role";
            case "member" -> "member";
            case "policy" -> "policy";
            case "technology" -> "technology";
            case "warid" -> "warId";
            case "applicant" -> "applicant";
            case "applicantid" -> "applicantId";
            case "government" -> "government";
            case "from" -> "from";
            case "to" -> "to";
            case "reason" -> "reason";
            default -> "other";
        };
    }

    private String formatCommandTime(Instant instant) {
        return COMMAND_TIME_FORMATTER.withZone(ZoneId.systemDefault()).format(instant);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String json(String value) {
        String safe = value == null ? "" : value;
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < safe.length(); index++) {
            char character = safe.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }

    private List<String> nationNameSuggestions() {
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (nationService == null) {
            return List.of();
        }
        return nationService.nations().stream()
            .map(Nation::name)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    private List<String> prefixMatches(List<String> candidates, String prefix) {
        String normalized = normalizeToken(prefix);
        List<String> suggestions = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                suggestions.add(candidate);
            }
        }
        return suggestions;
    }

    private List<String> merged(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first.size() + second.size());
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

    private String normalizeEventSubcommand(String value) {
        return switch (normalizeToken(value)) {
            case "列", "列表", "list", "ls" -> "list";
            case "审", "审计", "账", "账本", "audit", "log", "a" -> "audit";
            case "导", "导出", "export", "ex" -> "export";
            case "记", "记录", "record", "r" -> "record";
            case "清", "清除", "clear", "x" -> "clear";
            default -> normalizeToken(value);
        };
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String msg(String key, Object... args) {
        return messages.format(key, args);
    }

    private record EventFilterSelection(String filter, int queryStartIndex, boolean filterExplicit) {
    }

    private record EventExportRequest(String filter, String format, EventQueryOptions options, boolean valid) {
    }

    private record EventQueryOptions(int page, int pageSize, Duration window, Instant since, boolean valid) {
    }
}
