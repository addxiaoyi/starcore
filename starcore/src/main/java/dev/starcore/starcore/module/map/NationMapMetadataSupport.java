package dev.starcore.starcore.module.map;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.module.event.NationEventRecord;
import dev.starcore.starcore.module.nation.NationOperationalOverview;
import dev.starcore.starcore.module.nation.model.Nation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

final class NationMapMetadataSupport {
    private NationMapMetadataSupport() {
    }

    static Map<String, String> baseMetadata(
        Nation nation,
        NationOperationalOverview nationOverview,
        String displayColor,
        String relation
    ) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("nationId", nation.id().toString());
        metadata.put("nation", nation.name());
        metadata.put("nationKind", nation.kind().name().toLowerCase(Locale.ROOT));
        metadata.put("government", nation.governmentType().name());
        metadata.put("claims", String.valueOf(nationOverview.claimCount()));
        metadata.put("claimCount", String.valueOf(nationOverview.claimCount()));
        metadata.put("claimLimit", String.valueOf(nationOverview.claimLimit()));
        metadata.put("memberCount", String.valueOf(nationOverview.memberCount()));
        metadata.put("founderId", nation.founderId().toString());
        metadata.put("founderName", nationOverview.founderName());
        metadata.put("nationLevel", String.valueOf(nationOverview.level()));
        metadata.put("nationExperience", String.valueOf(nationOverview.experience()));
        metadata.put("nationExperienceProgress", String.valueOf(nationOverview.currentLevelProgress()));
        metadata.put("nationNextLevelExperience", String.valueOf(nationOverview.nextLevelExperienceRequired()));
        metadata.put("nationExperienceRemaining", String.valueOf(nationOverview.remainingExperienceToNextLevel()));
        metadata.put("nationMaxLevelReached", String.valueOf(nationOverview.maxLevelReached()));
        metadata.put("cityStateCount", String.valueOf(nationOverview.cityStateCount()));
        metadata.put("cityStateLimit", String.valueOf(nationOverview.cityStateLimit()));
        metadata.put("resourceDistrictCount", String.valueOf(nationOverview.resourceDistrictCount()));
        metadata.put("resourceDistrictLimit", String.valueOf(nationOverview.resourceDistrictLimit()));
        metadata.put("displayColor", displayColor);
        metadata.put("relation", relation);
        return metadata;
    }

    static void appendRecentEvents(
        Map<String, String> metadata,
        List<NationEventRecord> recentEvents,
        Function<NationEventRecord, Optional<String>> resourceIdResolver
    ) {
        if (metadata == null) {
            return;
        }
        List<NationEventRecord> events = recentEvents == null ? List.of() : recentEvents;
        metadata.put("recentEventCount", String.valueOf(events.size()));
        for (int index = 0; index < events.size(); index++) {
            NationEventRecord event = events.get(index);
            String eventKeyPrefix = "recentEvent" + index;
            metadata.put(eventKeyPrefix + "Type", event.type());
            metadata.put(eventKeyPrefix + "Category", recentEventCategory(event.type()));
            metadata.put(eventKeyPrefix + "Message", event.message());
            metadata.put(eventKeyPrefix + "At", event.occurredAt().toString());
            if (resourceIdResolver != null) {
                resourceIdResolver.apply(event).ifPresent(resourceId -> metadata.put(eventKeyPrefix + "ResourceId", resourceId));
            }
        }
    }

    static List<NationEventRecord> selectRecentEvents(List<NationEventRecord> recentEvents, int limit) {
        if (recentEvents == null || recentEvents.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<NationEventRecord> source = recentEvents.stream()
            .filter(Objects::nonNull)
            .toList();
        if (source.size() <= limit) {
            return source;
        }
        List<NationEventRecord> selected = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();
        Set<String> selectedCategories = new HashSet<>();
        for (NationEventRecord event : source) {
            if (selected.isEmpty()) {
                addSelectedRecentEvent(selected, selectedIds, selectedCategories, event);
                break;
            }
        }
        for (NationEventRecord event : source) {
            if (selected.size() >= limit) {
                break;
            }
            String category = recentEventCategory(event.type());
            if (!selectedCategories.contains(category)) {
                addSelectedRecentEvent(selected, selectedIds, selectedCategories, event);
            }
        }
        for (NationEventRecord event : source) {
            if (selected.size() >= limit) {
                break;
            }
            addSelectedRecentEvent(selected, selectedIds, selectedCategories, event);
        }
        return selected.stream()
            .sorted(Comparator.comparing(NationEventRecord::occurredAt).reversed())
            .toList();
    }

    private static void addSelectedRecentEvent(
        List<NationEventRecord> selected,
        Set<String> selectedIds,
        Set<String> selectedCategories,
        NationEventRecord event
    ) {
        if (event == null || event.id() == null || selectedIds.contains(event.id().toString())) {
            return;
        }
        selected.add(event);
        selectedIds.add(event.id().toString());
        selectedCategories.add(recentEventCategory(event.type()));
    }

    static String recentEventCategory(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("resource.")) {
            return "resource";
        }
        if (normalized.startsWith("treasury.")) {
            return "finance";
        }
        if (normalized.startsWith("officer.")) {
            return "officer";
        }
        if (normalized.startsWith("diplomacy.")) {
            return "diplomacy";
        }
        if (normalized.startsWith("war.")) {
            return "war";
        }
        if (normalized.startsWith("policy.") || normalized.startsWith("technology.") || normalized.startsWith("government.")) {
            return "strategy";
        }
        if (normalized.startsWith("territory.") || normalized.startsWith("claim.")) {
            return "territory";
        }
        if (normalized.startsWith("nation.") || normalized.startsWith("city.") || normalized.startsWith("resolution.")) {
            return "nation";
        }
        return "other";
    }

    static void appendFinanceSummary(
        Map<String, String> metadata,
        BigDecimal treasuryBalance,
        List<NationEventRecord> financeEvents
    ) {
        if (metadata == null) {
            return;
        }
        if (treasuryBalance != null) {
            metadata.put("treasuryBalance", treasuryBalance.toPlainString());
        }
        List<NationEventRecord> events = financeEvents == null ? List.of() : financeEvents;
        metadata.put("financeEventCount", String.valueOf(events.size()));
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal reward = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal deposit = BigDecimal.ZERO;
        BigDecimal withdraw = BigDecimal.ZERO;
        BigDecimal resourceIncome = BigDecimal.ZERO;
        int latestLimit = Math.min(3, events.size());
        for (int index = 0; index < events.size(); index++) {
            NationEventRecord event = events.get(index);
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
            if (index < latestLimit) {
                String prefix = "financeEvent" + index;
                metadata.put(prefix + "Type", event.type());
                metadata.put(prefix + "Message", event.message());
                metadata.put(prefix + "At", event.occurredAt().toString());
                metadata.put(prefix + "Amount", amount.toPlainString());
            }
        }
        metadata.put("financeIncomeTotal", normalizeMoney(income).toPlainString());
        metadata.put("financeRewardTotal", normalizeMoney(reward).toPlainString());
        metadata.put("financeTaxTotal", normalizeMoney(tax).toPlainString());
        metadata.put("financeDepositTotal", normalizeMoney(deposit).toPlainString());
        metadata.put("financeWithdrawTotal", normalizeMoney(withdraw).toPlainString());
        metadata.put("financeResourceIncomeTotal", normalizeMoney(resourceIncome).toPlainString());
        metadata.put("financeNetTotal", normalizeMoney(income.add(reward).add(tax).add(deposit).add(resourceIncome).subtract(withdraw)).toPlainString());
    }

    static void appendOfficerAuthorizationMetadata(Map<String, String> metadata, ConfigurationService configuration) {
        if (metadata == null || configuration == null) {
            return;
        }
        metadata.put("officerAuthorizationCount", "9");
        metadata.put("officerRoleResourceMigration", joinRoles(configuration.nationResourceMigrationOfficerRoles()));
        metadata.put("officerRoleTreasuryWithdraw", joinRoles(configuration.nationTreasuryWithdrawOfficerRoles()));
        metadata.put("officerRoleDiplomacySet", joinRoles(configuration.nationDiplomacySetOfficerRoles()));
        metadata.put("officerRoleWarDeclare", joinRoles(configuration.nationWarDeclareOfficerRoles()));
        metadata.put("officerRoleWarEnd", joinRoles(configuration.nationWarEndOfficerRoles()));
        metadata.put("officerRolePolicySet", joinRoles(configuration.nationPolicySetOfficerRoles()));
        metadata.put("officerRolePolicyClear", joinRoles(configuration.nationPolicyClearOfficerRoles()));
        metadata.put("officerRoleTechnologyUnlock", joinRoles(configuration.nationTechnologyUnlockOfficerRoles()));
        metadata.put("officerRoleTechnologyRevoke", joinRoles(configuration.nationTechnologyRevokeOfficerRoles()));
    }

    private static String joinRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        return String.join(", ", roles.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(role -> !role.isBlank())
            .distinct()
            .toList());
    }

    private static BigDecimal ledgerAmount(String context) {
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

    private static String ledgerContextValue(String context, String key) {
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

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.DOWN);
    }
}
