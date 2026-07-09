package dev.starcore.starcore.module.policy.effect;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.PolicyInteractionMatrix;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.policy.model.PolicyEffectType;

import java.util.*;

/**
 * 国策效果应用器 v2
 * 提供统一的方法在其他服务中应用国策效果
 */
public class PolicyEffectApplier {

    private final PolicyService policyService;

    public PolicyEffectApplier(PolicyService policyService) {
        this.policyService = policyService;
    }

    /**
     * 获取针对特定效果键和范围的政策加成值
     */
    public double getModifier(NationId nationId, String effectKey, PolicyEffectScope scope) {
        return policyService.activePolicyModifier(nationId, effectKey, scope);
    }

    /**
     * 检查国家是否拥有特定效果
     */
    public boolean hasEffect(NationId nationId, String effectKey, PolicyEffectScope scope) {
        return policyService.activePolicyEffects(nationId, scope).stream()
            .anyMatch(e -> e.key().equalsIgnoreCase(effectKey));
    }

    /**
     * 获取所有活动效果
     */
    public Iterable<PolicyEffect> getActiveEffects(NationId nationId) {
        return policyService.activePolicyEffects(nationId);
    }

    /**
     * 应用生产加成到基础值
     */
    public double applyProductionBonus(NationId nationId, double baseValue) {
        double modifier = getModifier(nationId, "production_bonus", PolicyEffectScope.PRODUCTION);
        return baseValue * (1.0 + modifier);
    }

    /**
     * 应用交易收入加成
     */
    public double applyTradeIncomeBonus(NationId nationId, double baseValue) {
        double modifier = getModifier(nationId, "trade_income_modifier", PolicyEffectScope.TRADE);
        return baseValue * (1.0 + modifier);
    }

    /**
     * 应用战斗准备度加成
     */
    public double applyCombatReadiness(NationId nationId, double baseValue) {
        double modifier = getModifier(nationId, "military", PolicyEffectScope.MILITARY);
        return baseValue * (1.0 + modifier);
    }

    /**
     * 应用领土防御加成
     */
    public double applyClaimDefenseBonus(NationId nationId, double baseValue) {
        double modifier = getModifier(nationId, "defense_bonus", PolicyEffectScope.DEFENSE);
        return baseValue * (1.0 + modifier);
    }

    /**
     * 应用外交点数加成
     */
    public double applyDiplomacyPointBonus(NationId nationId, double baseValue) {
        double modifier = getModifier(nationId, "diplomatic_reputation", PolicyEffectScope.DIPLOMACY);
        return baseValue * (1.0 + modifier);
    }

    /**
     * 应用资源产出加成
     * audit B-160: 仅当存在 per-resource 加成时不再叠加通用加成，避免重复施加
     */
    public long applyResourceBonus(NationId nationId, String resourceType, long baseAmount) {
        String effectKey = resourceType.toLowerCase() + "_production_bonus";
        double specific = getModifier(nationId, effectKey, PolicyEffectScope.PRODUCTION);
        double modifier;
        if (specific != 0.0) {
            // 已有专属资源加成，不与通用加成重复叠加
            modifier = specific;
        } else {
            modifier = getModifier(nationId, "production_bonus", PolicyEffectScope.PRODUCTION);
        }
        return Math.round(baseAmount * (1.0 + modifier));
    }

    /**
     * 检查国家当前激活的国策
     */
    public Optional<String> getActivePolicy(NationId nationId) {
        return policyService.activePolicy(nationId);
    }

    /**
     * 获取当前生效的国策定义
     */
    public Optional<PolicyDefinition> getActivePolicyDefinition(NationId nationId) {
        return policyService.activePolicyDefinition(nationId);
    }

    // ==================== v2 新增方法 ====================

    /**
     * 获取国家稳定性评分 (-1.0 到 1.0)
     */
    public double getStabilityScore(NationId nationId) {
        Collection<PolicyEffect> effects = policyService.activePolicyEffects(nationId);
        if (effects.isEmpty()) {
            return 0.0;
        }

        List<PolicyDefinition> policies = new ArrayList<>();
        policyService.activePolicyDefinition(nationId)
            .ifPresent(p -> policies.add(p));

        return PolicyEffectCalculator.calculateStabilityImpact(policies);
    }

    /**
     * 获取国家支持率评分 (-1.0 到 1.0)
     */
    public double getApprovalScore(NationId nationId) {
        Collection<PolicyEffect> effects = policyService.activePolicyEffects(nationId);
        if (effects.isEmpty()) {
            return 0.0;
        }

        List<PolicyDefinition> policies = new ArrayList<>();
        policyService.activePolicyDefinition(nationId)
            .ifPresent(p -> policies.add(p));

        return PolicyEffectCalculator.calculateApprovalImpact(policies);
    }

    /**
     * 获取经济影响评估
     */
    public PolicyEffectCalculator.EconomicImpact getEconomicImpact(NationId nationId) {
        List<PolicyDefinition> policies = new ArrayList<>();
        policyService.activePolicyDefinition(nationId)
            .ifPresent(p -> policies.add(p));

        return PolicyEffectCalculator.calculateEconomicImpact(policies);
    }

    /**
     * 获取军事影响评估
     */
    public PolicyEffectCalculator.MilitaryImpact getMilitaryImpact(NationId nationId) {
        List<PolicyDefinition> policies = new ArrayList<>();
        policyService.activePolicyDefinition(nationId)
            .ifPresent(p -> policies.add(p));

        return PolicyEffectCalculator.calculateMilitaryImpact(policies);
    }

    /**
     * 检查新政策是否会与当前激活的政策冲突
     * audit B-159: 修复 hasConflict 原实现恒返回 false 的死代码；改为真正查询活跃政策定义
     */
    public boolean hasConflict(NationId nationId, String newPolicyKey) {
        if (nationId == null || newPolicyKey == null) {
            return false;
        }
        Optional<PolicyDefinition> currentOpt = policyService.activePolicyDefinition(nationId);
        if (currentOpt.isEmpty()) {
            return false;
        }
        PolicyDefinition current = currentOpt.get();
        return current.conflictsWith(newPolicyKey)
            || PolicyInteractionMatrix.isMutuallyExclusive(current.key(), newPolicyKey);
    }

    /**
     * @deprecated 使用 {@link #hasConflict(NationId, String)}，旧签名恒返回 false，将删除
     */
    @Deprecated
    public boolean hasConflict(String newPolicyKey) {
        return false;
    }

    /**
     * 检查政策互斥关系
     */
    public boolean isMutuallyExclusive(String policy1, String policy2) {
        return PolicyInteractionMatrix.isMutuallyExclusive(policy1, policy2);
    }

    /**
     * 获取与指定政策互斥的所有政策
     */
    public Set<String> getExclusivePolicies(String policy) {
        return PolicyInteractionMatrix.getExclusivePolicies(policy);
    }

    /**
     * 获取与指定政策的协同效果
     */
    public Optional<PolicyInteractionMatrix.SynergyBonus> getSynergy(String policy1, String policy2) {
        return PolicyInteractionMatrix.getSynergy(policy1, policy2);
    }

    /**
     * 获取综合政策评估
     */
    public PolicyEffectCalculator.PolicyAssessment assessPolicies(NationId nationId) {
        List<PolicyDefinition> policies = new ArrayList<>();
        policyService.activePolicyDefinition(nationId)
            .ifPresent(p -> policies.add(p));

        return PolicyEffectCalculator.assess(policies);
    }

    /**
     * 计算协同加成
     */
    public double calculateSynergyBonus(PolicyDefinition active, PolicyDefinition candidate) {
        return PolicyEffectCalculator.calculateSynergyBonus(active, candidate);
    }

    /**
     * 应用经济增长率加成
     */
    public double applyEconomicGrowthBonus(NationId nationId, double baseRate) {
        PolicyEffectCalculator.EconomicImpact impact = getEconomicImpact(nationId);
        return baseRate * (1.0 + impact.growth());
    }

    /**
     * 应用防御加成
     */
    public double applyDefenseBonus(NationId nationId, double baseDefense) {
        PolicyEffectCalculator.MilitaryImpact impact = getMilitaryImpact(nationId);
        return baseDefense * (1.0 + impact.defense());
    }

    /**
     * 应用攻击加成
     */
    public double applyAttackBonus(NationId nationId, double baseAttack) {
        PolicyEffectCalculator.MilitaryImpact impact = getMilitaryImpact(nationId);
        return baseAttack * (1.0 + impact.offense());
    }

    /**
     * 应用研究速度加成
     */
    public double applyResearchSpeedBonus(NationId nationId, double baseSpeed) {
        double modifier = getModifier(nationId, "research_speed", PolicyEffectScope.RESEARCH);
        if (modifier == 0) {
            modifier = getModifier(nationId, "research", PolicyEffectScope.TECHNOLOGY);
        }
        return baseSpeed * (1.0 + modifier);
    }

    /**
     * 应用幸福度加成
     */
    public double applyHappinessBonus(NationId nationId, double baseHappiness) {
        double modifier = getModifier(nationId, "happiness_modifier", PolicyEffectScope.HAPPINESS);
        return baseHappiness * (1.0 + modifier);
    }

    /**
     * 获取通胀控制效果
     */
    public double getInflationControl(NationId nationId) {
        PolicyEffectCalculator.EconomicImpact impact = getEconomicImpact(nationId);
        return impact.inflation();
    }

    /**
     * 获取就业率加成
     */
    public double getEmploymentBonus(NationId nationId) {
        PolicyEffectCalculator.EconomicImpact impact = getEconomicImpact(nationId);
        return impact.employment();
    }
}
