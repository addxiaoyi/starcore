package dev.starcore.starcore.module.map;

import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.event.LedgerCategoryService;
import dev.starcore.starcore.module.event.NationEventRecord;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class MapFinanceEndpoint {
    private final Supplier<EventService> eventService;
    private final Supplier<NationService> nationService;
    private final Supplier<TreasuryService> treasuryService;
    private final Supplier<LedgerCategoryService> ledgerCategories;
    private final BiPredicate<MapViewerAccess, NationId> nationVisible;
    private final BooleanSupplier csvBomEnabled;
    private final MessageLookup messages;

    MapFinanceEndpoint(
        Supplier<EventService> eventService,
        Supplier<NationService> nationService,
        Supplier<TreasuryService> treasuryService,
        Supplier<LedgerCategoryService> ledgerCategories,
        BiPredicate<MapViewerAccess, NationId> nationVisible,
        BooleanSupplier csvBomEnabled,
        MessageLookup messages
    ) {
        this.eventService = eventService;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.ledgerCategories = ledgerCategories;
        this.nationVisible = nationVisible;
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
        Nation nation = resolveFinanceNation(params == null ? Map.of() : params);
        if (!nationVisible.test(access, nation.id())) {
            return new Response(403, errorJson("access-denied", "nation is not visible to this map viewer"));
        }
        int page = boundedPage(params == null ? null : params.get("page"));
        int size = boundedPageSize(params == null ? null : params.get("size"));
        String filter = normalizeFinanceFilter(params == null ? null : params.get("filter"));
        FinanceEventTimeWindow timeWindow = financeEventTimeWindow(params == null ? Map.of() : params);
        List<NationEventRecord> financeEvents = events.eventsOf(nation.id()).stream()
            .filter(event -> event.type() != null && event.type().startsWith("treasury."))
            .filter(event -> financeEventMatchesFilter(event, filter))
            .filter(event -> financeEventMatchesTimeWindow(event, timeWindow))
            .toList();
        String format = normalizeFinanceExportFormat(params == null ? null : params.get("format"));
        if ("csv".equals(format)) {
            return new Response(
                200,
                financeEventsCsv(nation, financeEvents, filter, timeWindow),
                "text/csv; charset=utf-8",
                financeExportFilename(nation, filter, timeWindow, "csv")
            );
        }
        if ("json".equals(format)) {
            return new Response(
                200,
                financeEventsExportJson(nation, financeEvents, filter, timeWindow),
                "application/json; charset=utf-8",
                financeExportFilename(nation, filter, timeWindow, "json")
            );
        }
        return new Response(200, financeEventsJson(nation, financeEvents, page, size, filter, timeWindow));
    }

    private EventService eventService() {
        return eventService == null ? null : eventService.get();
    }

    private NationService nationService() {
        return nationService == null ? null : nationService.get();
    }

    private TreasuryService treasuryService() {
        return treasuryService == null ? null : treasuryService.get();
    }

    private LedgerCategoryService ledgerCategories() {
        return ledgerCategories == null ? null : ledgerCategories.get();
    }

    private String normalizeFinanceExportFormat(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "csv" -> "csv";
            case "json", "export-json" -> "json";
            default -> "";
        };
    }

    private String normalizeFinanceFilter(String raw) {
        LedgerCategoryService service = ledgerCategories();
        return service == null ? "all" : service.normalizeFinanceFilter(raw);
    }

    private boolean financeEventMatchesFilter(NationEventRecord event, String filter) {
        LedgerCategoryService service = ledgerCategories();
        return event != null && service != null && service.matchesFinanceFilter(event.type(), filter);
    }

    private FinanceEventTimeWindow financeEventTimeWindow(Map<String, String> params) {
        String range = normalizeFinanceRange(params.get("range"));
        Instant from = parseFinanceInstant(params.get("from"), "from");
        Instant to = parseFinanceInstant(params.get("to"), "to");
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
            throw new IllegalArgumentException("Invalid finance time range");
        }
        return new FinanceEventTimeWindow(range, from, to);
    }

    private String normalizeFinanceRange(String raw) {
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

    private Instant parseFinanceInstant(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid finance " + field + " time");
        }
    }

    private boolean financeEventMatchesTimeWindow(NationEventRecord event, FinanceEventTimeWindow timeWindow) {
        if (event == null || event.occurredAt() == null || timeWindow == null) {
            return false;
        }
        Instant occurredAt = event.occurredAt();
        if (timeWindow.from() != null && occurredAt.isBefore(timeWindow.from())) {
            return false;
        }
        return timeWindow.to() == null || !occurredAt.isAfter(timeWindow.to());
    }

    private Nation resolveFinanceNation(Map<String, String> params) {
        NationService service = nationService();
        if (service == null) {
            throw new IllegalArgumentException("Nation service unavailable");
        }
        String rawNation = params.getOrDefault("nation", "").trim();
        if (rawNation.isBlank()) {
            rawNation = params.getOrDefault("nationId", "").trim();
        }
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

    private String financeEventsJson(Nation nation, List<NationEventRecord> financeEvents, int page, int size, String filter, FinanceEventTimeWindow timeWindow) {
        List<NationEventRecord> source = financeEvents == null ? List.of() : financeEvents;
        int total = source.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        int fromIndex = Math.min(total, (page - 1) * size);
        int toIndex = Math.min(total, fromIndex + size);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal reward = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal deposit = BigDecimal.ZERO;
        BigDecimal withdraw = BigDecimal.ZERO;
        BigDecimal resourceIncome = BigDecimal.ZERO;
        for (NationEventRecord event : source) {
            BigDecimal amount = ledgerAmount(event.context());
            switch (event.type()) {
                case "treasury.income" -> income = income.add(amount);
                case "treasury.reward" -> reward = reward.add(amount);
                case "treasury.tax" -> tax = tax.add(amount);
                case "treasury.deposit" -> deposit = deposit.add(amount);
                case "treasury.withdraw" -> withdraw = withdraw.add(amount);
                case "treasury.resource-income" -> resourceIncome = resourceIncome.add(amount);
                default -> {
                }
            }
        }
        TreasuryService treasury = treasuryService();
        StringBuilder builder = new StringBuilder(1024 + Math.min(size, total) * 256);
        builder.append('{');
        appendBooleanField(builder, "ok", true);
        builder.append(',');
        appendField(builder, "nationId", nation.id().toString());
        builder.append(',');
        appendField(builder, "nationName", nation.name());
        builder.append(',');
        appendField(builder, "nationKind", nation.kind().name().toLowerCase(Locale.ROOT));
        builder.append(',');
        appendField(builder, "filter", filter == null || filter.isBlank() ? "all" : filter);
        builder.append(',');
        appendField(builder, "range", timeWindow == null ? "all" : timeWindow.range());
        builder.append(',');
        appendField(builder, "from", timeWindow == null || timeWindow.from() == null ? "" : timeWindow.from().toString());
        builder.append(',');
        appendField(builder, "to", timeWindow == null || timeWindow.to() == null ? "" : timeWindow.to().toString());
        builder.append(',');
        appendMoneyField(builder, "treasuryBalance", treasury == null ? BigDecimal.ZERO : treasury.balance(nation.id()));
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
        appendMoneyField(builder, "income", normalizeMoney(income));
        builder.append(',');
        appendMoneyField(builder, "reward", normalizeMoney(reward));
        builder.append(',');
        appendMoneyField(builder, "tax", normalizeMoney(tax));
        builder.append(',');
        appendMoneyField(builder, "deposit", normalizeMoney(deposit));
        builder.append(',');
        appendMoneyField(builder, "withdraw", normalizeMoney(withdraw));
        builder.append(',');
        appendMoneyField(builder, "resourceIncome", normalizeMoney(resourceIncome));
        builder.append(',');
        appendMoneyField(builder, "net", normalizeMoney(income.add(reward).add(tax).add(deposit).add(resourceIncome).subtract(withdraw)));
        builder.append('}');
        builder.append(',');
        builder.append("\"events\":[");
        int eventIndex = 0;
        for (NationEventRecord event : source.subList(fromIndex, toIndex)) {
            if (eventIndex++ > 0) {
                builder.append(',');
            }
            appendFinanceEventJson(builder, event);
        }
        builder.append(']');
        builder.append('}');
        return builder.toString();
    }

    private String financeEventsExportJson(Nation nation, List<NationEventRecord> financeEvents, String filter, FinanceEventTimeWindow timeWindow) {
        List<NationEventRecord> source = financeEvents == null ? List.of() : financeEvents;
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
        appendField(builder, "range", timeWindow == null ? "all" : timeWindow.range());
        builder.append(',');
        appendField(builder, "from", timeWindow == null || timeWindow.from() == null ? "" : timeWindow.from().toString());
        builder.append(',');
        appendField(builder, "to", timeWindow == null || timeWindow.to() == null ? "" : timeWindow.to().toString());
        builder.append(',');
        appendNumberField(builder, "total", source.size());
        builder.append(',');
        builder.append("\"events\":[");
        int index = 0;
        for (NationEventRecord event : source) {
            if (index++ > 0) {
                builder.append(',');
            }
            appendFinanceEventJson(builder, event);
        }
        builder.append(']');
        builder.append('}');
        return builder.toString();
    }

    private String financeEventsCsv(Nation nation, List<NationEventRecord> financeEvents, String filter, FinanceEventTimeWindow timeWindow) {
        List<NationEventRecord> source = financeEvents == null ? List.of() : financeEvents;
        StringBuilder builder = new StringBuilder(512 + source.size() * 160);
        if (csvBomEnabled.getAsBoolean()) {
            builder.append('\ufeff');
        }
        builder.append("nation_id,nation_name,filter,range,from,to,event_id,occurred_at,type,message,amount,actor,reason,balance,context\n");
        for (NationEventRecord event : source) {
            Map<String, String> contextEntries = ledgerContextEntries(event.context());
            appendCsvField(builder, nation.id().toString());
            appendCsvField(builder, nation.name());
            appendCsvField(builder, filter == null || filter.isBlank() ? "all" : filter);
            appendCsvField(builder, timeWindow == null ? "all" : timeWindow.range());
            appendCsvField(builder, timeWindow == null || timeWindow.from() == null ? "" : timeWindow.from().toString());
            appendCsvField(builder, timeWindow == null || timeWindow.to() == null ? "" : timeWindow.to().toString());
            appendCsvField(builder, event.id().toString());
            appendCsvField(builder, event.occurredAt().toString());
            appendCsvField(builder, event.type());
            appendCsvField(builder, event.message());
            appendCsvField(builder, normalizeMoney(ledgerAmount(event.context())).toPlainString());
            appendCsvField(builder, contextEntries.getOrDefault("actor", ""));
            appendCsvField(builder, contextEntries.getOrDefault("reason", ""));
            appendCsvField(builder, contextEntries.getOrDefault("balance", ""));
            appendCsvField(builder, event.context() == null ? "" : event.context());
            builder.setCharAt(builder.length() - 1, '\n');
        }
        return builder.toString();
    }

    private String financeExportFilename(Nation nation, String filter, FinanceEventTimeWindow timeWindow, String extension) {
        String safeNation = safeFinanceFilenameSegment(nation.name());
        String shortNationId = nation.id().toString().replace("-", "");
        if (shortNationId.length() > 8) {
            shortNationId = shortNationId.substring(0, 8);
        }
        String range = timeWindow == null ? "all" : timeWindow.range();
        String safeFilter = filter == null || filter.isBlank() ? "all" : filter;
        return "starcore-finance-" + safeNation + "-" + shortNationId + "-" + safeFilter + "-" + range + "." + extension;
    }

    private String safeFinanceFilenameSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "nation";
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
        return value.isBlank() ? "nation" : value;
    }

    private void appendFinanceEventJson(StringBuilder builder, NationEventRecord event) {
        BigDecimal amount = ledgerAmount(event.context());
        Map<String, String> contextEntries = ledgerContextEntries(event.context());
        builder.append('{');
        appendField(builder, "id", event.id().toString());
        builder.append(',');
        appendField(builder, "type", event.type());
        builder.append(',');
        appendField(builder, "message", event.message());
        builder.append(',');
        appendField(builder, "occurredAt", event.occurredAt().toString());
        builder.append(',');
        appendMoneyField(builder, "amount", amount);
        builder.append(',');
        appendField(builder, "actor", contextEntries.getOrDefault("actor", ""));
        builder.append(',');
        appendField(builder, "reason", contextEntries.getOrDefault("reason", ""));
        builder.append(',');
        appendField(builder, "balance", contextEntries.getOrDefault("balance", ""));
        builder.append(',');
        appendField(builder, "context", event.context() == null ? "" : event.context());
        builder.append(',');
        builder.append("\"details\":{");
        int index = 0;
        for (Map.Entry<String, String> entry : contextEntries.entrySet()) {
            if (index++ > 0) {
                builder.append(',');
            }
            appendField(builder, entry.getKey(), entry.getValue());
        }
        builder.append('}');
        builder.append('}');
    }

    private BigDecimal ledgerAmount(String context) {
        String value = ledgerContextValue(context, "amount");
        if (value.isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }
        try {
            return normalizeMoney(new BigDecimal(value.trim()));
        } catch (RuntimeException ignored) {
            return BigDecimal.ZERO.setScale(2);
        }
    }

    private String ledgerContextValue(String context, String key) {
        if (context == null || context.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        for (String entry : context.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            if (key.equals(entry.substring(0, separator).trim())) {
                return entry.substring(separator + 1).trim();
            }
        }
        return "";
    }

    private Map<String, String> ledgerContextEntries(String context) {
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

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.DOWN);
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

    private String errorJson(String code, String message) {
        StringBuilder builder = new StringBuilder(128);
        builder.append('{');
        appendBooleanField(builder, "ok", false);
        builder.append(',');
        appendField(builder, "code", code == null ? "error" : code);
        builder.append(',');
        appendField(builder, "message", message == null || message.isBlank() ? msg("command.map.request-failed") : message);
        builder.append(',');
        appendBooleanField(builder, "canSubmit", false);
        builder.append('}');
        return builder.toString();
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

    private void appendMoneyField(StringBuilder builder, String name, BigDecimal value) {
        builder.append('"').append(escape(name)).append("\":");
        if (value == null) {
            builder.append("\"0.00\"");
        } else {
            builder.append('"').append(escape(value.toPlainString())).append('"');
        }
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

    private record FinanceEventTimeWindow(String range, Instant from, Instant to) {
    }

    @FunctionalInterface
    interface MessageLookup {
        String format(String key, Object... args);
    }
}
