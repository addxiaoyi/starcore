package dev.starcore.starcore.module.policy;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

record PolicyStateSnapshot(
    Map<NationId, PolicyRuntimeState> activePolicyStates,
    Map<NationId, Map<String, Instant>> cooldowns,
    Map<NationId, Set<String>> unlockedPolicies
) {
    PolicyStateSnapshot {
        activePolicyStates = Map.copyOf(Objects.requireNonNull(activePolicyStates, "activePolicyStates"));
        cooldowns = Map.copyOf(Objects.requireNonNull(cooldowns, "cooldowns"));
        unlockedPolicies = Map.copyOf(Objects.requireNonNull(unlockedPolicies, "unlockedPolicies"));
    }
}
