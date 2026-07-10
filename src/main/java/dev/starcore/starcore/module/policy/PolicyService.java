package dev.starcore.starcore.module.policy;

import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.model.PolicyActivationResult;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface PolicyService {
    Collection<String> availablePolicies();

    Collection<PolicyDefinition> policyDefinitions();

    Optional<PolicyDefinition> policyDefinition(String policyKey);

    Optional<String> activePolicy(NationId nationId);

    Optional<PolicyDefinition> activePolicyDefinition(NationId nationId);

    Optional<PolicyRuntimeState> activePolicyState(NationId nationId);

    Collection<String> unlockedPolicies(NationId nationId);

    boolean hasUnlockedPolicy(NationId nationId, String policyKey);

    Collection<PolicyEffect> activePolicyEffects(NationId nationId);

    Collection<PolicyEffect> activePolicyEffects(NationId nationId, PolicyEffectScope scope);

    double activePolicyModifier(NationId nationId, String effectKey, PolicyEffectScope scope);

    PolicyActivationResult activatePolicy(NationId nationId, String policyKey, TreasuryService treasuryService);

    PolicyActivationResult activatePolicy(NationId nationId, String policyKey, TreasuryService treasuryService, Instant now);

    boolean expirePoliciesAt(Instant now);

    boolean setActivePolicy(NationId nationId, String policyKey);

    boolean clearActivePolicy(NationId nationId);

    /**
     * 获取某国家某政策的冷却剩余秒数
     * @param nationId 国家ID
     * @param policyKey 政策键
     * @param now 当前时间
     * @return 冷却剩余秒数，如果不在冷却中则返回0
     */
    long cooldownRemaining(NationId nationId, String policyKey, Instant now);

    String summary();
}
