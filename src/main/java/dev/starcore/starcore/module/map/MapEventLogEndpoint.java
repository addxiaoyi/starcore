package dev.starcore.starcore.module.map;

import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.event.LedgerCategoryService;
import dev.starcore.starcore.module.event.NationEventRecord;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

final class MapEventLogEndpoint {
    private static final List<String> EVENT_CATEGORIES = List.of(
        "resource",
        "finance",
        "officer",
        "diplomacy",
        "war",
        "strategy",
        "territory",
        "nation",
        "other"
    );

    private final Supplier<EventService> eventService;
    private final Supplier<NationService> nationService;
    private final Supplier<LedgerCategoryService> ledgerCategories;
    private final BiPredicate<MapViewerAccess, NationId> nationVisible;
    private final Function<NationEventRecord, Optional<String>> resourceIdResolver;
    private final BooleanSupplier csvBomEnabled;
    private final MessageLookup messages;

    MapEventLogEndpoint(
        Supplier<EventService> eventService,
        Supplier<NationService> nationService,
        Supplier<LedgerCategoryService> ledgerCategories,
        BiPredicate<MapViewerAccess, NationId> nationVisible,
        Function<NationEventRecord, Optional<String>> resourceIdResolver,
        BooleanSupplier csvBomEnabled,
        MessageLookup messages
    ) {
        this.eventService = eventService;
        this.nationService = nationService;
        this.ledgerCategories = ledgerCategories;
        this.nationVisible = nationVisible;
        this.resourceIdResolver = resourceIdResolver;
        this.csvBomEnabled = csvBomEnabled;
        this.messages = messages;
    }

    Response buildResponse(MapViewerAccess access, Map<String, String> params) {
        if (access == null || access.isPublic()) {
            return new Response(403, errorJson("login-required", msg("command.map.login-required")));
        }
        EventService events = eventService();
        if (events == null) {
            return new Response(404, errorJson("disabled", "event service unavailable"));
        }
        Nation nation = resolveNation(params == null ? Map.of() : params);
        if (!nationVisible.test(access, nation.id())) {
            return new Response(403, errorJson("access-denied", "nation is not visible to this map viewer"));
        }
        int page = boundedPage(params == null ? null : params.get("page"));
        int size = boundedPageSize(params == null ? null : params.get("size"));
        String filter = normalizeEventFilter(params == null ? null : params.get("filter"));
        String type = normalizeEventType(params == null ? null : params.get("type"));
        String resourceId = normalizeResourceFilter(firstNonBlank(
            params == null ? null : params.get("resourceId"),
            params == null ? null : params.get("resource"),
            params == null ? null : params.get("districtId")
        ));
        String query = normalizeEventSearch(firstNonBlank(
            params == null ? null : params.get("query"),
            params == null ? null : params.get("q"),
            params == null ? null : params.get("search")
        ));
        String actor = normalizeEventSearch(params == null ? null : params.get("actor"));
        String reason = normalizeEventSearch(params == null ? null : params.get("reason"));
        EventTimeWindow timeWindow = eventTimeWindow(params == null ? Map.of() : params);
        List<NationEventRecord> filteredEvents = events.eventsOf(nation.id()).stream()
            .filter(event -> matchesEventFilter(event, filter))
            .filter(event -> matchesEventType(event, type))
            .filter(event -> matchesResourceId(event, resourceId))
            .filter(event -> matchesEventQuery(event, query))
            .filter(event -> matchesEventDetail(event, actor, "actor", "operator", "player", "viewer", "member", "target", "targetName"))
            .filter(event -> matchesEventDetail(event, reason, "reason", "cause", "operation", "action", "policy", "technology", "relation", "warId"))
            .filter(event -> matchesTimeWindow(event, timeWindow))
            .toList();
        String format = normalizeEventExportFormat(params == null ? null : params.get("format"));
        if ("csv".equals(format)) {
            return new Response(
                200,
                eventLogCsv(nation, filteredEvents, filter, type, resourceId, query, actor, reason, timeWindow),
                "text/csv; charset=utf-8",
                eventExportFilename(nation, filter, type, resourceId, query, actor, reason, timeWindow, "csv")
            );
        }
        if ("json".equals(format)) {
            return new Response(
                200,
                eventLogExportJson(nation, filteredEvents, filter, type, resourceId, query, actor, reason, timeWindow),
                "application/json; charset=utf-8",
                eventExportFilename(nation, filter, type, resourceId, query, actor, reason, timeWindow, "json")
            );
        }
        return new Response(200, eventLogJson(nation, filteredEvents, page, size, filter, type, resourceId, query, actor, reason, timeWindow));
    }

    private EventService eventService() {
        return eventService == null ? null : eventService.get();
    }

    private NationService nationService() {
        return nationService == null ? null : nationService.get();
    }

    private LedgerCategoryService ledgerCategories() {
        return ledgerCategories == null ? null : ledgerCategories.get();
    }

    private Nation resolveNation(Map<String, String> params) {
        NationService service = nationService();
        if (service == null) {
            throw new IllegalArgumentException("Nation service unavailable");
        }
        String rawNation = firstNonBlank(
            params.get("nation"),
            params.get("nationId")
        );
        if (rawNation.isBlank()) {
            throw new IllegalArgumentException("Missing nation");
        }
        try {
            NationId nationId = new NationId(UUID.fromString(rawNation));
            return service.nationById(nationId)
                .orElseThrow(() -> new IllegalArgumentException("Nation not found"));
        } catch (IllegalArgumentException ignored) {
            return service.nationByName(rawNation)
                .orElseThrow(() -> new IllegalArgumentException("Nation not found"));
        }
    }

    private int boundedPage(String rawPage) {
        if (rawPage == null || rawPage.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(rawPage.trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid page");
        }
    }

    private int boundedPageSize(String rawSize) {
        if (rawSize == null || rawSize.isBlank()) {
            return 25;
        }
        try {
            int parsed = Integer.parseInt(rawSize.trim());
            return Math.max(1, Math.min(100, parsed));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid size");
        }
    }

    private String normalizeEventFilter(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.isBlank()) {
            return "all";
        }
        return switch (value) {
            case "all", "全部", "所有" -> "all";
            case "resource", "resources", "资源", "矿", "rsc" -> "resource";
            case "finance", "financial", "economy", "eco", "money", "treasury", "财政", "财务", "经济", "金库", "账本" -> "finance";
            case "officer", "off", "官员", "官" -> "officer";
            case "diplomacy", "dip", "外交", "交" -> "diplomacy";
            case "war", "w", "战争", "战" -> "war";
            case "strategy", "policy", "technology", "government", "策", "国策", "科技", "政体" -> "strategy";
            case "territory", "claim", "领地", "圈地", "领" -> "territory";
            case "nation", "city", "resolution", "国家", "城邦", "决议", "国" -> "nation";
            case "other", "其他" -> "other";
            case "resource-income", "resourceincome", "income", "reward", "tax", "deposit", "withdraw" -> value;
            default -> {
                LedgerCategoryService service = ledgerCategories();
                String normalized = service == null ? value : service.normalizeEventFilter(raw);
                yield normalized == null || normalized.isBlank() ? value : normalized.replace('_', '-');
            }
        };
    }

    private String normalizeEventType(String raw) {
        LedgerCategoryService service = ledgerCategories();
        if (service == null) {
            return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        }
        return service.normalizeEventType(raw);
    }

    private String normalizeEventExportFormat(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "csv" -> "csv";
            case "json", "export-json" -> "json";
            default -> "";
        };
    }

    private String normalizeEventSearch(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.trim().replace('\r', ' ').replace('\n', ' ');
        while (normalized.contains("  ")) {
            normalized = normalized.replace("  ", " ");
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private boolean matchesEventFilter(NationEventRecord event, String filter) {
        if (event == null) {
            return false;
        }
        String normalizedFilter = normalizeEventFilter(filter);
        if ("all".equals(normalizedFilter)) {
            return true;
        }
        if (EVENT_CATEGORIES.contains(normalizedFilter)) {
            return normalizedFilter.equals(NationMapMetadataSupport.recentEventCategory(event.type()));
        }
        LedgerCategoryService service = ledgerCategories();
        if (service != null) {
            return service.matchesEventFilter(event.type(), normalizedFilter);
        }
        String eventType = normalizeEventType(event.type());
        return normalizedFilter.equals(eventType) || eventType.startsWith(normalizedFilter + '.');
    }

    private boolean matchesEventType(NationEventRecord event, String type) {
        if (event == null) {
            return false;
        }
        String normalizedType = normalizeEventType(type);
        return normalizedType.isBlank() || normalizedType.equals(normalizeEventType(event.type()));
    }

    private EventTimeWindow eventTimeWindow(Map<String, String> params) {
        String range = normalizeEventRange(params.get("range"));
        Instant from = parseEventInstant(params.get("from"), "from");
        Instant to = parseEventInstant(params.get("to"), "to");
        Instant now = Instant.now();
        if ("1h".equals(range)) {
            from = now.minus(Duration.ofHours(1));
        } else if ("24h".equals(range)) {
            from = now.minus(Duration.ofHours(24));
        } else if ("7d".equals(range)) {
            from = now.minus(Duration.ofDays(7));
        } else if (!"custom".equals(range)) {
            range = (from == null && to == null) ? "all" : "custom";
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid event time range");
        }
        return new EventTimeWindow(range, from, to);
    }

    private String normalizeEventRange(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (value) {
            case "", "all", "全部", "所有" -> "all";
            case "1h", "hour", "last-hour", "最近1小时", "一小时" -> "1h";
            case "24h", "day", "today", "last-day", "最近24小时", "一天" -> "24h";
            case "7d", "week", "last-week", "最近7天", "七天" -> "7d";
            case "custom", "range", "自定义" -> "custom";
            default -> "all";
        };
    }

    private Instant parseEventInstant(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid event " + field + " time");
        }
    }

    private boolean matchesTimeWindow(NationEventRecord event, EventTimeWindow timeWindow) {
        if (event == null || event.occurredAt() == null || timeWindow == null) {
            return false;
        }
        Instant occurredAt = event.occurredAt();
        if (timeWindow.from() != null && occurredAt.isBefore(timeWindow.from())) {
            return false;
        }
        return timeWindow.to() == null || !occurredAt.isAfter(timeWindow.to());
    }

    private String normalizeResourceFilter(String raw) {
        String normalized = normalizeResourceToken(raw);
        return normalized == null ? "" : normalized;
    }

    private boolean matchesResourceId(NationEventRecord event, String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return true;
        }
        if (event == null) {
            return false;
        }
        Set<String> resourceTokens = eventResourceTokens(event);
        return resourceTokens.contains(resourceId);
    }

    private boolean matchesEventQuery(NationEventRecord event, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (event == null) {
            return false;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        if (containsIgnoreCase(event.type(), needle)
            || containsIgnoreCase(event.message(), needle)
            || containsIgnoreCase(event.context(), needle)
            || containsIgnoreCase(NationMapMetadataSupport.recentEventCategory(event.type()), needle)) {
            return true;
        }
        for (Map.Entry<String, String> entry : contextEntries(event.context()).entrySet()) {
            if (containsIgnoreCase(entry.getKey(), needle) || containsIgnoreCase(entry.getValue(), needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesEventDetail(NationEventRecord event, String query, String... keys) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (event == null) {
            return false;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        Map<String, String> entries = contextEntries(event.context());
        for (String key : keys == null ? new String[0] : keys) {
            if (contextValueContains(entries, key, needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean contextValueContains(Map<String, String> entries, String key, String lowercaseNeedle) {
        if (entries == null || entries.isEmpty() || key == null || key.isBlank()) {
            return false;
        }
        String value = entries.get(key);
        if (containsIgnoreCase(value, lowercaseNeedle)) {
            return true;
        }
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key) && containsIgnoreCase(entry.getValue(), lowercaseNeedle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String haystack, String lowercaseNeedle) {
        return haystack != null
            && lowercaseNeedle != null
            && !lowercaseNeedle.isBlank()
            && haystack.toLowerCase(Locale.ROOT).contains(lowercaseNeedle);
    }

    private Set<String> eventResourceTokens(NationEventRecord event) {
        if (event == null) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        addResourceToken(tokens, event.context());
        Map<String, String> contextEntries = contextEntries(event.context());
        addResourceToken(tokens, contextEntries.get("resourceId"));
        addResourceToken(tokens, contextEntries.get("resource_id"));
        addResourceToken(tokens, contextEntries.get("resource-district-id"));
        addResourceToken(tokens, contextEntries.get("resourceDistrictId"));
        addResourceToken(tokens, contextEntries.get("districtId"));
        addResourceToken(tokens, contextEntries.get("district_id"));
        addResourceToken(tokens, contextEntries.get("district"));
        if (resourceIdResolver != null) {
            try {
                resourceIdResolver.apply(event).ifPresent(value -> addResourceToken(tokens, value));
            } catch (RuntimeException ignored) {
                // Keep endpoint resilient when marker lookup cannot resolve a stale district.
            }
        }
        return Set.copyOf(tokens);
    }

    private void addResourceToken(Set<String> tokens, String raw) {
        String normalized = normalizeResourceToken(raw);
        if (!normalized.isBlank()) {
            tokens.add(normalized);
        }
    }

    private String normalizeResourceToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.indexOf(';') >= 0 || trimmed.indexOf('=') >= 0) {
            return "";
        }
        String value = trimmed;
        if (value.regionMatches(true, 0, "resource:", 0, "resource:".length())) {
            value = value.substring("resource:".length()).trim();
        }
        try {
            return "resource:" + UUID.fromString(value).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String eventLogJson(
        Nation nation,
        List<NationEventRecord> events,
        int page,
        int size,
        String filter,
        String type,
        String resourceId,
        String query,
        String actor,
        String reason,
        EventTimeWindow timeWindow
    ) {
        List<NationEventRecord> source = events == null ? List.of() : events;
        int total = source.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        int fromIndex = Math.min(total, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);
        Map<String, Integer> counts = new LinkedHashMap<>();
        EVENT_CATEGORIES.forEach(category -> counts.put(category, 0));
        for (NationEventRecord event : source) {
            String category = NationMapMetadataSupport.recentEventCategory(event.type());
            counts.computeIfPresent(category, (ignored, value) -> value + 1);
        }
        StringBuilder builder = new StringBuilder(1024 + Math.min(size, total) * 256);
        builder.append('{');
        appendBooleanField(builder, "ok", true);
        builder.append(',');
        appendField(builder, "nationId", nation.id().toString());
        builder.append(',');
        appendField(builder, "nationName", nation.name());
        builder.append(',');
        appendField(builder, "filter", filter == null || filter.isBlank() ? "all" : filter);
        builder.append(',');
        appendField(builder, "type", type == null ? "" : type);
        builder.append(',');
        appendField(builder, "resourceId", resourceId == null ? "" : resourceId);
        builder.append(',');
        appendField(builder, "query", query == null ? "" : query);
        builder.append(',');
        appendField(builder, "actor", actor == null ? "" : actor);
        builder.append(',');
        appendField(builder, "reason", reason == null ? "" : reason);
        builder.append(',');
        appendField(builder, "range", timeWindow == null ? "all" : timeWindow.range());
        builder.append(',');
        appendField(builder, "from", timeWindow == null || timeWindow.from() == null ? "" : timeWindow.from().toString());
        builder.append(',');
        appendField(builder, "to", timeWindow == null || timeWindow.to() == null ? "" : timeWindow.to().toString());
        builder.append(',');
        appendNumberField(builder, "page", page);
        builder.append(',');
        appendNumberField(builder, "size", size);
        builder.append(',');
        appendNumberField(builder, "total", total);
        builder.append(',');
        appendNumberField(builder, "totalPages", totalPages);
        builder.append(',');
        builder.append("\"summary\":{");
        int summaryIndex = 0;
        for (String category : EVENT_CATEGORIES) {
            if (summaryIndex++ > 0) {
                builder.append(',');
            }
            appendNumberField(builder, category, counts.getOrDefault(category, 0));
        }
        builder.append('}');
        builder.append(',');
        builder.append("\"events\":[");
        int eventIndex = 0;
        for (NationEventRecord event : source.subList(fromIndex, toIndex)) {
            if (eventIndex++ > 0) {
                builder.append(',');
            }
            appendEventJson(builder, event);
        }
        builder.append(']');
        builder.append('}');
        return builder.toString();
    }

    private String eventLogExportJson(
        Nation nation,
        List<NationEventRecord> events,
        String filter,
        String type,
        String resourceId,
        String query,
        String actor,
        String reason,
        EventTimeWindow timeWindow
    ) {
        List<NationEventRecord> source = events == null ? List.of() : events;
        StringBuilder builder = new StringBuilder(1024 + source.size() * 256);
        builder.append('{');
        appendBooleanField(builder, "ok", true);
        builder.append(',');
        appendField(builder, "nationId", nation.id().toString());
        builder.append(',');
        appendField(builder, "nationName", nation.name());
        builder.append(',');
        appendField(builder, "filter", filter == null || filter.isBlank() ? "all" : filter);
        builder.append(',');
        appendField(builder, "type", type == null ? "" : type);
        builder.append(',');
        appendField(builder, "resourceId", resourceId == null ? "" : resourceId);
        builder.append(',');
        appendField(builder, "query", query == null ? "" : query);
        builder.append(',');
        appendField(builder, "actor", actor == null ? "" : actor);
        builder.append(',');
        appendField(builder, "reason", reason == null ? "" : reason);
        builder.append(',');
        appendField(builder, "range", timeWindow == null ? "all" : timeWindow.range());
        builder.append(',');
        appendField(builder, "from", timeWindow == null || timeWindow.from() == null ? "" : timeWindow.from().toString());
        builder.append(',');
        appendField(builder, "to", timeWindow == null || timeWindow.to() == null ? "" : timeWindow.to().toString());
        builder.append(',');
        appendNumberField(builder, "total", source.size());
        builder.append(',');
        builder.append("\"summary\":{");
        Map<String, Integer> counts = eventCategoryCounts(source);
        int summaryIndex = 0;
        for (String category : EVENT_CATEGORIES) {
            if (summaryIndex++ > 0) {
                builder.append(',');
            }
            appendNumberField(builder, category, counts.getOrDefault(category, 0));
        }
        builder.append('}');
        builder.append(',');
        builder.append("\"events\":[");
        int index = 0;
        for (NationEventRecord event : source) {
            if (index++ > 0) {
                builder.append(',');
            }
            appendEventJson(builder, event);
        }
        builder.append(']');
        builder.append('}');
        return builder.toString();
    }

    private String eventLogCsv(
        Nation nation,
        List<NationEventRecord> events,
        String filter,
        String type,
        String resourceId,
        String query,
        String actor,
        String reason,
        EventTimeWindow timeWindow
    ) {
        List<NationEventRecord> source = events == null ? List.of() : events;
        StringBuilder builder = new StringBuilder(512 + source.size() * 180);
        if (csvBomEnabled()) {
            builder.append('\ufeff');
        }
        builder.append("nation_id,nation_name,filter,type,resource_id,query,actor,reason,range,from,to,event_id,occurred_at,event_type,category,message,event_resource_id,context\n");
        for (NationEventRecord event : source) {
            appendCsvField(builder, nation.id().toString());
            appendCsvField(builder, nation.name());
            appendCsvField(builder, filter == null || filter.isBlank() ? "all" : filter);
            appendCsvField(builder, type == null ? "" : type);
            appendCsvField(builder, resourceId == null ? "" : resourceId);
            appendCsvField(builder, query == null ? "" : query);
            appendCsvField(builder, actor == null ? "" : actor);
            appendCsvField(builder, reason == null ? "" : reason);
            appendCsvField(builder, timeWindow == null ? "all" : timeWindow.range());
            appendCsvField(builder, timeWindow == null || timeWindow.from() == null ? "" : timeWindow.from().toString());
            appendCsvField(builder, timeWindow == null || timeWindow.to() == null ? "" : timeWindow.to().toString());
            appendCsvField(builder, event.id() == null ? "" : event.id().toString());
            appendCsvField(builder, event.occurredAt() == null ? "" : event.occurredAt().toString());
            appendCsvField(builder, event.type());
            appendCsvField(builder, NationMapMetadataSupport.recentEventCategory(event.type()));
            appendCsvField(builder, event.message());
            appendCsvField(builder, eventResourceTokens(event).stream().findFirst().orElse(""));
            appendCsvField(builder, event.context() == null ? "" : event.context());
            builder.setCharAt(builder.length() - 1, '\n');
        }
        return builder.toString();
    }

    private Map<String, Integer> eventCategoryCounts(List<NationEventRecord> events) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        EVENT_CATEGORIES.forEach(category -> counts.put(category, 0));
        for (NationEventRecord event : events == null ? List.<NationEventRecord>of() : events) {
            String category = NationMapMetadataSupport.recentEventCategory(event.type());
            counts.computeIfPresent(category, (ignored, value) -> value + 1);
        }
        return counts;
    }

    private String eventExportFilename(
        Nation nation,
        String filter,
        String type,
        String resourceId,
        String query,
        String actor,
        String reason,
        EventTimeWindow timeWindow,
        String extension
    ) {
        String safeNation = safeEventFilenameSegment(nation.name());
        String shortNationId = nation.id().toString().replace("-", "");
        if (shortNationId.length() > 8) {
            shortNationId = shortNationId.substring(0, 8);
        }
        String safeFilter = safeEventFilenameSegment(filter == null || filter.isBlank() ? "all" : filter);
        String safeType = safeEventFilenameSegment(type == null || type.isBlank() ? "all-types" : type);
        String safeResource = eventExportResourceSegment(resourceId);
        String search = firstNonBlank(query, actor, reason);
        String searchSegment = search.isBlank() ? "" : "-" + safeEventFilenameSegment(search);
        String range = safeEventFilenameSegment(timeWindow == null ? "all" : timeWindow.range());
        return "starcore-events-" + safeNation + "-" + shortNationId + "-" + safeFilter + "-" + safeType + "-" + safeResource + searchSegment + "-" + range + "." + extension;
    }

    private String eventExportResourceSegment(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return "all-resources";
        }
        String value = resourceId.trim();
        if (value.regionMatches(true, 0, "resource:", 0, "resource:".length())) {
            value = value.substring("resource:".length()).trim();
        }
        try {
            String compact = UUID.fromString(value).toString().replace("-", "");
            return "resource-" + compact.substring(0, Math.min(8, compact.length()));
        } catch (IllegalArgumentException ignored) {
            return safeEventFilenameSegment(resourceId);
        }
    }

    private String safeEventFilenameSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "all";
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
        return value.isBlank() ? "all" : value;
    }

    private void appendEventJson(StringBuilder builder, NationEventRecord event) {
        Map<String, String> details = contextEntries(event == null ? "" : event.context());
        builder.append('{');
        appendField(builder, "id", event == null || event.id() == null ? "" : event.id().toString());
        builder.append(',');
        appendField(builder, "type", event == null ? "" : event.type());
        builder.append(',');
        appendField(builder, "category", event == null ? "" : NationMapMetadataSupport.recentEventCategory(event.type()));
        builder.append(',');
        appendField(builder, "message", event == null ? "" : event.message());
        builder.append(',');
        appendField(builder, "occurredAt", event == null || event.occurredAt() == null ? "" : event.occurredAt().toString());
        builder.append(',');
        appendField(builder, "resourceId", eventResourceTokens(event).stream().findFirst().orElse(""));
        builder.append(',');
        appendField(builder, "context", event == null || event.context() == null ? "" : event.context());
        builder.append(',');
        builder.append("\"details\":{");
        int detailIndex = 0;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            if (detailIndex++ > 0) {
                builder.append(',');
            }
            appendField(builder, entry.getKey(), entry.getValue());
        }
        builder.append('}');
        builder.append('}');
    }

    private Map<String, String> contextEntries(String context) {
        if (context == null || context.isBlank()) {
            return Map.of();
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (String entry : context.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!key.isBlank()) {
                entries.put(key, value);
            }
        }
        return entries;
    }

    private String errorJson(String code, String message) {
        StringBuilder builder = new StringBuilder(128);
        builder.append('{');
        appendBooleanField(builder, "ok", false);
        builder.append(',');
        appendField(builder, "code", code == null ? "error" : code);
        builder.append(',');
        appendField(builder, "message", message == null || message.isBlank() ? msg("command.map.request-failed") : message);
        builder.append('}');
        return builder.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void appendField(StringBuilder builder, String name, String value) {
        builder.append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"');
    }

    private void appendNumberField(StringBuilder builder, String name, int value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private void appendBooleanField(StringBuilder builder, String name, boolean value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private void appendCsvField(StringBuilder builder, String value) {
        String normalized = value == null ? "" : value;
        builder.append('"');
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '"') {
                builder.append("\"\"");
            } else {
                builder.append(ch);
            }
        }
        builder.append('"').append(',');
    }

    private boolean csvBomEnabled() {
        return csvBomEnabled == null || csvBomEnabled.getAsBoolean();
    }

    private String msg(String key, Object... args) {
        return messages == null ? key : messages.format(key, args);
    }

    private String escape(String input) {
        return (input == null ? "" : input)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    record Response(int status, String json, String contentType, String filename) {
        private Response(int status, String json) {
            this(status, json, "application/json; charset=utf-8", "");
        }
    }

    private record EventTimeWindow(String range, Instant from, Instant to) {
    }

    @FunctionalInterface
    interface MessageLookup {
        String format(String key, Object... args);
    }
}
