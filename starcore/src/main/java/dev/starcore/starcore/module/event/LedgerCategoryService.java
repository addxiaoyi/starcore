package dev.starcore.starcore.module.event;

import dev.starcore.starcore.core.config.ConfigurationService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LedgerCategoryService {
    private static final List<String> DEFAULT_CATEGORY_KEYS = List.of(
        "all", "finance", "resource-income", "income", "reward", "tax", "deposit", "withdraw",
        "treasury", "resource", "nation", "territory", "city-state", "policy", "technology", "diplomacy", "war", "officer"
    );
    private static final Map<String, List<String>> DEFAULT_EVENT_TYPES = Map.ofEntries(
        Map.entry("resource-income", List.of("treasury.resource-income")),
        Map.entry("income", List.of("treasury.income")),
        Map.entry("reward", List.of("treasury.reward")),
        Map.entry("tax", List.of("treasury.tax")),
        Map.entry("deposit", List.of("treasury.deposit")),
        Map.entry("withdraw", List.of("treasury.withdraw"))
    );
    private static final Map<String, List<String>> DEFAULT_PREFIXES = Map.ofEntries(
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

    private final ConfigurationService configuration;

    public LedgerCategoryService(ConfigurationService configuration) {
        this.configuration = configuration;
    }

    public String normalizeEventFilter(String raw) {
        return switch (normalizeToken(raw)) {
            case "", "*", "all", "全部" -> "all";
            case "finance", "financial", "economy", "eco", "money", "财政", "财务", "经济", "钱", "账", "账本", "审", "审计" -> "finance";
            case "resource_income", "resource-income", "resourceincome", "resources-income", "资源产出", "资源收入", "矿产收入" -> "resource_income";
            case "income", "daily", "daily-income", "日常", "日常收入" -> "income";
            case "reward", "rewards", "rpg", "奖励", "任务奖励", "副本奖励" -> "reward";
            case "tax", "taxes", "税", "税收", "玩家税收" -> "tax";
            case "deposit", "deposits", "存入", "管理员存入" -> "deposit";
            case "withdraw", "withdrawal", "withdraws", "spending", "expense", "支出", "金库支出" -> "withdraw";
            case "国", "国家", "nation" -> "nation";
            case "领", "领地", "圈地", "territory", "claim" -> "territory";
            case "城", "城邦", "city", "city_state", "city-state" -> "city_state";
            case "库", "金库", "treasury", "tr" -> "treasury";
            case "资", "矿", "资源", "resource", "rsc" -> "resource";
            case "策", "国策", "policy", "po" -> "policy";
            case "技", "科技", "technology", "tech" -> "technology";
            case "交", "外交", "diplomacy", "dip" -> "diplomacy";
            case "战", "战争", "war", "w" -> "war";
            case "官", "官员", "officer", "off" -> "officer";
            default -> normalizeToken(raw);
        };
    }

    public String normalizeFinanceFilter(String raw) {
        String value = normalizeToken(raw).replace('_', '-');
        String normalized = switch (value) {
            case "", "all", "finance", "treasury", "全部", "所有", "财政", "金库" -> "all";
            case "income", "daily", "日常", "日常收入" -> "income";
            case "resource", "resource-income", "resources", "资源", "资源产出" -> "resource";
            case "reward", "rewards", "rpg", "奖励", "任务奖励" -> "reward";
            case "tax", "taxes", "税", "税收", "玩家税收" -> "tax";
            case "deposit", "admin", "存入", "管理员存入" -> "deposit";
            case "withdraw", "spending", "expense", "支出", "金库支出" -> "withdraw";
            default -> "";
        };
        if (!normalized.isBlank()) {
            return normalized;
        }
        String categoryKey = categoryKey(value);
        return categoryKeys().contains(categoryKey) ? categoryKey : "all";
    }

    public String categoryKey(String normalizedFilter) {
        String key = normalizedFilter == null ? "" : normalizedFilter.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (key) {
            case "resource", "resource-income", "resourceincome", "resources-income" -> "resource-income";
            case "city", "city-state" -> "city-state";
            default -> key;
        };
    }

    public boolean matchesEventFilter(String eventType, String filter) {
        String type = normalizeEventType(eventType);
        String normalized = normalizeEventFilter(filter);
        if (type.isBlank()) {
            return false;
        }
        if ("all".equals(normalized)) {
            return true;
        }
        String categoryKey = categoryKey(normalized);
        if (eventTypes(categoryKey).stream().anyMatch(type::equals)) {
            return true;
        }
        List<String> prefixes = prefixes(categoryKey);
        if (prefixes.isEmpty() && !knownCategory(categoryKey)) {
            prefixes = List.of(normalized);
        }
        return prefixes.stream().anyMatch(type::startsWith);
    }

    public boolean matchesFinanceFilter(String eventType, String filter) {
        String type = normalizeEventType(eventType);
        String normalized = filter == null ? "all" : filter;
        if (type.isBlank()) {
            return false;
        }
        if ("all".equals(normalized)) {
            return true;
        }
        String categoryKey = categoryKey(normalized);
        if (eventTypes(categoryKey).stream().anyMatch(type::equals)) {
            return true;
        }
        return prefixes(categoryKey).stream().anyMatch(type::startsWith);
    }

    public String eventFilterMessageKeySuffix(String filter) {
        return switch (normalizeEventFilter(filter)) {
            case "all" -> "all";
            case "finance" -> "finance";
            case "resource_income" -> "resource-income";
            case "income" -> "income";
            case "reward" -> "reward";
            case "tax" -> "tax";
            case "deposit" -> "deposit";
            case "withdraw" -> "withdraw";
            case "nation" -> "nation";
            case "territory" -> "territory";
            case "city_state" -> "city-state";
            case "treasury" -> "treasury";
            case "resource" -> "resource";
            case "policy" -> "policy";
            case "technology" -> "technology";
            case "diplomacy" -> "diplomacy";
            case "war" -> "war";
            case "officer" -> "officer";
            default -> "";
        };
    }

    public List<String> categoryKeys() {
        if (configuration == null) {
            return DEFAULT_CATEGORY_KEYS;
        }
        return configuration.ledgerCategoryKeys();
    }

    public List<String> suggestions(List<String> baseSuggestions) {
        Set<String> suggestions = new LinkedHashSet<>(baseSuggestions);
        suggestions.addAll(categoryKeys());
        return new ArrayList<>(suggestions);
    }

    public String normalizeEventType(String eventType) {
        return eventType == null ? "" : eventType.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> eventTypes(String categoryKey) {
        if (configuration == null) {
            return DEFAULT_EVENT_TYPES.getOrDefault(categoryKey, List.of());
        }
        return configuration.ledgerCategoryEventTypes(categoryKey);
    }

    private List<String> prefixes(String categoryKey) {
        if (configuration == null) {
            return DEFAULT_PREFIXES.getOrDefault(categoryKey, List.of());
        }
        return configuration.ledgerCategoryPrefixes(categoryKey);
    }

    private boolean knownCategory(String categoryKey) {
        return categoryKeys().contains(categoryKey);
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
