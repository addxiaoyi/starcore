package dev.starcore.starcore.module.policy;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class PolicyStateCodec {
    private PolicyStateCodec() {
    }

    static Properties toProperties(
        Map<NationId, PolicyRuntimeState> activePolicyStates,
        Map<NationId, ? extends Map<String, Instant>> cooldowns,
        Map<NationId, ? extends Set<String>> unlockedPolicies
    ) {
        Properties properties = new Properties();
        Set<NationId> nationIds = new LinkedHashSet<>();
        nationIds.addAll(activePolicyStates.keySet());
        nationIds.addAll(cooldowns.keySet());
        nationIds.addAll(unlockedPolicies.keySet());
        List<NationId> snapshot = nationIds.stream()
            .sorted((left, right) -> left.toString().compareTo(right.toString()))
            .toList();
        properties.setProperty("count", String.valueOf(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            NationId nationId = snapshot.get(index);
            String prefix = "policy." + index + '.';
            properties.setProperty(prefix + "nationId", nationId.toString());

            PolicyRuntimeState state = activePolicyStates.get(nationId);
            if (state != null) {
                properties.setProperty(prefix + "key", normalizePolicy(state.policyKey()));
                properties.setProperty(prefix + "activatedAt", state.activatedAt().toString());
                properties.setProperty(prefix + "expiresAt", state.expiresAt().toString());
                properties.setProperty(prefix + "cooldownEndsAt", state.cooldownEndsAt().toString());
            }

            Set<String> unlockedValues = unlockedPolicies.get(nationId);
            List<String> unlockedSnapshot = (unlockedValues == null ? Set.<String>of() : unlockedValues).stream()
                .map(PolicyStateCodec::normalizePolicy)
                .filter(value -> !value.isBlank())
                .sorted()
                .toList();
            properties.setProperty(prefix + "unlockedCount", String.valueOf(unlockedSnapshot.size()));
            for (int unlockedIndex = 0; unlockedIndex < unlockedSnapshot.size(); unlockedIndex++) {
                properties.setProperty(prefix + "unlocked." + unlockedIndex, unlockedSnapshot.get(unlockedIndex));
            }

            Map<String, Instant> cooldownValues = cooldowns.get(nationId);
            List<Map.Entry<String, Instant>> cooldownSnapshot = (cooldownValues == null ? Map.<String, Instant>of() : cooldownValues).entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> Map.entry(normalizePolicy(entry.getKey()), entry.getValue()))
                .filter(entry -> !entry.getKey().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .toList();
            properties.setProperty(prefix + "cooldownCount", String.valueOf(cooldownSnapshot.size()));
            for (int cooldownIndex = 0; cooldownIndex < cooldownSnapshot.size(); cooldownIndex++) {
                Map.Entry<String, Instant> cooldown = cooldownSnapshot.get(cooldownIndex);
                String cooldownPrefix = prefix + "cooldown." + cooldownIndex + '.';
                properties.setProperty(cooldownPrefix + "key", cooldown.getKey());
                properties.setProperty(cooldownPrefix + "endsAt", cooldown.getValue().toString());
            }
        }
        return properties;
    }

    static PolicyStateSnapshot fromProperties(Properties properties, Set<String> allowedPolicies) {
        Set<String> allowed = allowedPolicies == null ? Set.of() : allowedPolicies.stream()
            .map(PolicyStateCodec::normalizePolicy)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
        Map<NationId, PolicyRuntimeState> activePolicyStates = new LinkedHashMap<>();
        Map<NationId, Map<String, Instant>> cooldowns = new LinkedHashMap<>();
        Map<NationId, Set<String>> unlockedPolicies = new LinkedHashMap<>();
        int count = parseInt(properties.getProperty("count"), 0);
        for (int index = 0; index < count; index++) {
            String prefix = "policy." + index + '.';
            NationId nationId = parseNationId(properties.getProperty(prefix + "nationId"));
            if (nationId == null) {
                continue;
            }

            String activePolicy = normalizePolicy(properties.getProperty(prefix + "key", ""));
            if (allowed.contains(activePolicy)) {
                PolicyRuntimeState state = loadRuntimeState(properties, prefix, activePolicy);
                if (state != null) {
                    activePolicyStates.put(nationId, state);
                    cooldowns.computeIfAbsent(nationId, ignored -> new LinkedHashMap<>())
                        .put(activePolicy, state.cooldownEndsAt());
                }
                addUnlocked(unlockedPolicies, nationId, activePolicy);
            }

            int unlockedCount = parseInt(properties.getProperty(prefix + "unlockedCount"), 0);
            for (int unlockedIndex = 0; unlockedIndex < unlockedCount; unlockedIndex++) {
                String unlocked = normalizePolicy(properties.getProperty(prefix + "unlocked." + unlockedIndex, ""));
                if (allowed.contains(unlocked)) {
                    addUnlocked(unlockedPolicies, nationId, unlocked);
                }
            }

            int cooldownCount = parseInt(properties.getProperty(prefix + "cooldownCount"), 0);
            for (int cooldownIndex = 0; cooldownIndex < cooldownCount; cooldownIndex++) {
                String cooldownPrefix = prefix + "cooldown." + cooldownIndex + '.';
                String cooldownPolicy = normalizePolicy(properties.getProperty(cooldownPrefix + "key", ""));
                Instant endsAt = parseInstant(properties.getProperty(cooldownPrefix + "endsAt"), null);
                if (allowed.contains(cooldownPolicy) && endsAt != null) {
                    cooldowns.computeIfAbsent(nationId, ignored -> new LinkedHashMap<>()).put(cooldownPolicy, endsAt);
                }
            }
        }
        Map<NationId, Map<String, Instant>> immutableCooldowns = new LinkedHashMap<>();
        cooldowns.forEach((nationId, values) -> immutableCooldowns.put(nationId, Map.copyOf(values)));
        Map<NationId, Set<String>> immutableUnlocked = new LinkedHashMap<>();
        unlockedPolicies.forEach((nationId, values) -> immutableUnlocked.put(nationId, Set.copyOf(values)));
        return new PolicyStateSnapshot(
            Map.copyOf(activePolicyStates),
            Map.copyOf(immutableCooldowns),
            Map.copyOf(immutableUnlocked)
        );
    }

    private static void addUnlocked(Map<NationId, Set<String>> unlockedPolicies, NationId nationId, String policyKey) {
        unlockedPolicies.computeIfAbsent(nationId, ignored -> new LinkedHashSet<>()).add(policyKey);
    }

    private static PolicyRuntimeState loadRuntimeState(Properties properties, String prefix, String policyKey) {
        Instant activatedAt = parseInstant(properties.getProperty(prefix + "activatedAt"), Instant.EPOCH);
        Instant expiresAt = parseInstant(properties.getProperty(prefix + "expiresAt"), Instant.MAX);
        Instant cooldownEndsAt = parseInstant(properties.getProperty(prefix + "cooldownEndsAt"), Instant.EPOCH);
        return new PolicyRuntimeState(policyKey, activatedAt, expiresAt, cooldownEndsAt);
    }

    private static String normalizePolicy(String policyKey) {
        return policyKey == null ? "" : policyKey.trim().toLowerCase(Locale.ROOT);
    }

    private static NationId parseNationId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new NationId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }
}
