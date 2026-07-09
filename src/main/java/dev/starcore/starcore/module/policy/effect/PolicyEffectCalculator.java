package dev.starcore.starcore.module.policy.effect;

import dev.starcore.starcore.module.policy.PolicyInteractionMatrix;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.policy.model.PolicyEffectType;

import java.util.*;

/**
 * 国策效果计算器 v2
 *
 * 功能：
 * - 复合效果计算
 * - 协同加成计算
 * - 稳定度/支持率计算
 * - 政策冲突检测
 */
public final class PolicyEffectCalculator {

    private PolicyEffectCalculator() {}

    // ==================== 基础效果计算 ====================

    /**
     * 计算基础效果加成
     */
    public static double calculateEffect(PolicyDefinition policy, PolicyEffectScope scope) {
        return policy.effects().stream()
            .filter(e -> e.scope() == scope)
            .mapToDouble(PolicyEffect::modifier)
            .sum();
    }

    /**
     * 计算特定类型的效果
     */
    public static double calculateEffectByType(PolicyDefinition policy, PolicyEffectType type) {
        return policy.effects().stream()
            .filter(e -> getEffectType(e.key()) == type)
            .mapToDouble(PolicyEffect::modifier)
            .sum();
    }

    /**
     * 获取效果类型（通过 key 映射）
     */
    private static PolicyEffectType getEffectType(String effectKey) {
        try {
            return PolicyEffectType.valueOf(effectKey.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== 协同加成计算 ====================

    /**
     * 计算协同加成
     */
    public static double calculateSynergyBonus(PolicyDefinition active, PolicyDefinition candidate) {
        return PolicyInteractionMatrix.calculateSynergyBonus(
            active.key(), candidate.key()
        );
    }

    /**
     * 计算多个政策的总协同加成
     */
    public static double calculateTotalSynergyBonus(Collection<String> activePolicies, String candidatePolicy) {
        double totalBonus = 0.0;
        for (String active : activePolicies) {
            totalBonus += PolicyInteractionMatrix.calculateSynergyBonus(active, candidatePolicy);
        }
        return totalBonus;
    }

    /**
     * 获取所有协同政策及其加成
     */
    public static Map<String, PolicyInteractionMatrix.SynergyBonus> getSynergyWithActive(
            Collection<String> activePolicies, String candidatePolicy) {
        Map<String, PolicyInteractionMatrix.SynergyBonus> synergies = new LinkedHashMap<>();

        for (String active : activePolicies) {
            Optional<PolicyInteractionMatrix.SynergyBonus> synergy =
                PolicyInteractionMatrix.getSynergy(active, candidatePolicy);
            synergy.ifPresent(bonus -> synergies.put(active, bonus));
        }

        return synergies;
    }

    // ==================== 稳定度计算 ====================

    /**
     * 计算政策对稳定度的影响
     *
     * 稳定度 = 基础值 + 经济加成 + 社会加成 + 军事加成 + 惩罚
     */
    public static double calculateStabilityImpact(Collection<PolicyDefinition> activePolicies) {
        double stability = 0.0;

        for (PolicyDefinition policy : activePolicies) {
            for (PolicyEffect effect : policy.effects()) {
                stability += calculateStabilityEffect(effect);
            }
        }

        return Math.max(-1.0, Math.min(1.0, stability)); // 限制在 [-1, 1]
    }

    /**
     * 计算单个效果对稳定度的影响
     * audit B-161: 仅当 type 枚举不存在该 key 时才走 calculateStabilityByKey；
     * 已有 type 的话绝不再走 key 子串回退，因此不会双重计算。下方 if(type==null) 即此保证。
     */
    private static double calculateStabilityEffect(PolicyEffect effect) {
        PolicyEffectType type = getEffectType(effect.key());
        double modifier = effect.modifier();

        if (type == null) {
            // 通过 key 子串匹配作为 type 缺失时的回退；type 命中时必走 switch，不再调用此方法
            return calculateStabilityByKey(effect.key(), modifier);
        }

        return switch (type) {
            // 正向影响稳定
            case STABILITY -> modifier * 0.5;
            case HAPPINESS_MODIFIER -> modifier * 0.3;
            case ECONOMIC_GROWTH -> modifier * 0.2;
            case GOVERNMENT_EFFICIENCY -> modifier * 0.2;
            case DIPLOMATIC_REPUTATION -> modifier * 0.1;

            // 负向影响稳定
            case REVOLUTION_RISK -> -modifier * 0.3;
            case CORRUPTION -> -modifier * 0.3;
            case WAR_SUPPORT -> -modifier * 0.2;

            default -> 0.0;
        };
    }

    /**
     * 通过 key 计算稳定度影响
     */
    private static double calculateStabilityByKey(String key, double modifier) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);

        if (normalized.contains("stability") || normalized.contains("stable")) {
            return modifier * 0.5;
        }
        if (normalized.contains("revolution") || normalized.contains("rebel")) {
            return -modifier * 0.3;
        }
        if (normalized.contains("corrupt")) {
            return -modifier * 0.3;
        }

        return 0.0;
    }

    // ==================== 支持率计算 ====================

    /**
     * 计算政策对支持率的影响
     */
    public static double calculateApprovalImpact(Collection<PolicyDefinition> activePolicies) {
        double approval = 0.0;

        for (PolicyDefinition policy : activePolicies) {
            for (PolicyEffect effect : policy.effects()) {
                approval += calculateApprovalEffect(effect);
            }
        }

        return Math.max(-1.0, Math.min(1.0, approval));
    }

    /**
     * 计算单个效果对支持率的影响
     */
    private static double calculateApprovalEffect(PolicyEffect effect) {
        PolicyEffectType type = getEffectType(effect.key());
        double modifier = effect.modifier();

        if (type == null) {
            return calculateApprovalByKey(effect.key(), modifier);
        }

        return switch (type) {
            // 正向影响支持率
            case APPROVAL_RATING -> modifier * 0.6;
            case HAPPINESS_MODIFIER -> modifier * 0.3;
            case STABILITY -> modifier * 0.2;
            case CULTURE_SPREAD -> modifier * 0.1;

            // 负向影响支持率
            case DISSIDENTS_SUPPRESSION -> -modifier * 0.2;
            case FREEDOM_OF_SPEECH -> modifier * 0.2; // 言论自由通常增加支持

            default -> 0.0;
        };
    }

    /**
     * 通过 key 计算支持率影响
     */
    private static double calculateApprovalByKey(String key, double modifier) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);

        if (normalized.contains("approval") || normalized.contains("support")) {
            return modifier * 0.5;
        }
        if (normalized.contains("welfare") || normalized.contains("healthcare")) {
            return modifier * 0.3;
        }

        return 0.0;
    }

    // ==================== 经济影响计算 ====================

    /**
     * 计算政策对经济的影响
     */
    public static EconomicImpact calculateEconomicImpact(Collection<PolicyDefinition> activePolicies) {
        double growth = 0.0;
        double inflation = 0.0;
        double employment = 0.0;

        for (PolicyDefinition policy : activePolicies) {
            for (PolicyEffect effect : policy.effects()) {
                EconomicImpactType impactType = getEconomicImpactType(effect.key());
                double modifier = effect.modifier();

                switch (impactType) {
                    case GROWTH -> growth += modifier * 0.3;
                    case INFLATION -> inflation += modifier * 0.2;
                    case EMPLOYMENT -> employment += modifier * 0.2;
                }
            }
        }

        return new EconomicImpact(growth, inflation, employment);
    }

    /**
     * 获取经济影响类型
     */
    private static EconomicImpactType getEconomicImpactType(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);

        if (normalized.contains("growth") || normalized.contains("economic")) {
            return EconomicImpactType.GROWTH;
        }
        if (normalized.contains("inflation")) {
            return EconomicImpactType.INFLATION;
        }
        if (normalized.contains("employment") || normalized.contains("job")) {
            return EconomicImpactType.EMPLOYMENT;
        }
        if (normalized.contains("trade_income")) {
            return EconomicImpactType.GROWTH;
        }
        if (normalized.contains("production")) {
            return EconomicImpactType.GROWTH;
        }

        return EconomicImpactType.NONE;
    }

    /**
     * 经济影响类型
     */
    private enum EconomicImpactType {
        NONE, GROWTH, INFLATION, EMPLOYMENT
    }

    /**
     * 经济影响记录
     */
    public record EconomicImpact(double growth, double inflation, double employment) {
        public double netEffect() {
            return growth - inflation;
        }
    }

    // ==================== 军事影响计算 ====================

    /**
     * 计算政策对军事的影响
     */
    public static MilitaryImpact calculateMilitaryImpact(Collection<PolicyDefinition> activePolicies) {
        double defense = 0.0;
        double offense = 0.0;
        double morale = 0.0;

        for (PolicyDefinition policy : activePolicies) {
            for (PolicyEffect effect : policy.effects()) {
                MilitaryImpactType impactType = getMilitaryImpactType(effect.key());
                double modifier = effect.modifier();

                switch (impactType) {
                    case DEFENSE -> defense += modifier * 0.3;
                    case OFFENSE -> offense += modifier * 0.3;
                    case MORALE -> morale += modifier * 0.2;
                }
            }
        }

        return new MilitaryImpact(defense, offense, morale);
    }

    /**
     * 获取军事影响类型
     */
    private static MilitaryImpactType getMilitaryImpactType(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);

        if (normalized.contains("defense") || normalized.contains("defensive")) {
            return MilitaryImpactType.DEFENSE;
        }
        if (normalized.contains("attack") || normalized.contains("offense") || normalized.contains("offensive")) {
            return MilitaryImpactType.OFFENSE;
        }
        if (normalized.contains("morale") || normalized.contains("military")) {
            return MilitaryImpactType.MORALE;
        }
        if (normalized.contains("combat") || normalized.contains("ready")) {
            return MilitaryImpactType.MORALE;
        }

        return MilitaryImpactType.NONE;
    }

    /**
     * 军事影响类型
     */
    private enum MilitaryImpactType {
        NONE, DEFENSE, OFFENSE, MORALE
    }

    /**
     * 军事影响记录
     */
    public record MilitaryImpact(double defense, double offense, double morale) {
        public double combatPower() {
            return (defense + offense) * (1 + morale);
        }
    }

    // ==================== 冲突检测 ====================

    /**
     * 检测政策冲突
     */
    public static ConflictResult checkConflict(String newPolicy, Collection<String> activePolicies) {
        List<String> exclusive = new ArrayList<>();
        List<SynergyInfo> synergies = new ArrayList<>();

        for (String active : activePolicies) {
            if (PolicyInteractionMatrix.isMutuallyExclusive(newPolicy, active)) {
                exclusive.add(active);
            } else {
                Optional<PolicyInteractionMatrix.SynergyBonus> synergy =
                    PolicyInteractionMatrix.getSynergy(newPolicy, active);
                synergy.ifPresent(bonus ->
                    synergies.add(new SynergyInfo(active, bonus.bonus(), bonus.description()))
                );
            }
        }

        return new ConflictResult(!exclusive.isEmpty(), exclusive, synergies);
    }

    /**
     * 冲突检测结果
     */
    public record ConflictResult(
        boolean hasConflict,
        List<String> exclusivePolicies,
        List<SynergyInfo> synergies
    ) {
        public boolean hasSynergy() {
            return !synergies.isEmpty();
        }

        public double totalSynergyBonus() {
            return synergies.stream().mapToDouble(SynergyInfo::bonus).sum();
        }
    }

    /**
     * 协同信息
     */
    public record SynergyInfo(String policy, double bonus, String description) {
        public double bonusPercentage() {
            return bonus * 100;
        }
    }

    // ==================== 综合评估 ====================

    /**
     * 综合政策效果评估
     */
    public static PolicyAssessment assess(Collection<PolicyDefinition> activePolicies) {
        return new PolicyAssessment(
            calculateStabilityImpact(activePolicies),
            calculateApprovalImpact(activePolicies),
            calculateEconomicImpact(activePolicies),
            calculateMilitaryImpact(activePolicies),
            activePolicies.size()
        );
    }

    /**
     * 政策评估记录
     */
    public record PolicyAssessment(
        double stability,
        double approval,
        EconomicImpact economic,
        MilitaryImpact military,
        int activeCount
    ) {
        public String overallScore() {
            double score = (stability + approval + economic.netEffect() + military.combatPower()) / 4;
            if (score > 0.3) return "优秀";
            if (score > 0.1) return "良好";
            if (score > -0.1) return "一般";
            if (score > -0.3) return "较差";
            return "危险";
        }

        public boolean isStable() {
            return stability > 0;
        }

        public boolean isPopular() {
            return approval > 0;
        }
    }
}
