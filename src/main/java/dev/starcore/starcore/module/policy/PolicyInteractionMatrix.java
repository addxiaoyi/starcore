package dev.starcore.starcore.module.policy;
import java.util.Optional;

import java.util.*;

/**
 * 国策交互矩阵
 *
 * 定义国策之间的互斥和协同关系
 * - 互斥(Mutually Exclusive): 两个政策不能同时激活
 * - 协同(Synergy): 两个政策同时激活时获得额外加成
 *
 * audit B-163 (partial): 此处的 MUTUALLY_EXCLUSIVE 表与 PolicyDefinition.conflictKeys
 * 是两套冲突来源，存在不一致（matrix 中有的 key 默认定义里没有；默认定义里有的 matrix 没收录）。
 * 长期应统一冲突源为 PolicyDefinition.conflictKeys，本表降级为可选协同表；
 * 短期统一开销大暂保持现状，等政策定义配置体系重构时一并修。
 */
public final class PolicyInteractionMatrix {

    private PolicyInteractionMatrix() {}

    // ==================== 互斥政策映射 ====================
    // Key: 政策键 -> Value: 与之互斥的政策键集合

    private static final Map<String, Set<String>> MUTUALLY_EXCLUSIVE;

    static {
        Map<String, Set<String>> exclusive = new HashMap<>();

        // 孤立 vs 扩张
        addMutualExclusive(exclusive, "isolationism", "globalism", "expansionism", "imperialism");

        // 贸易政策
        addMutualExclusive(exclusive, "free_trade", "protectionism", "autarky");

        // 外交立场
        addMutualExclusive(exclusive, "multilateralism", "unilateralism");

        // 经济模式
        addMutualExclusive(exclusive, "welfare_state", "laissez_faire", "state_capitalism");

        // 军事制度
        addMutualExclusive(exclusive, "mandatory_service", "professional_army");
        addMutualExclusive(exclusive, "conscription", "volunteer_military");
        addMutualExclusive(exclusive, "professional_army", "conscription");

        // 宗教政策
        addMutualExclusive(exclusive, "state_religion", "secularism", "freedom_of_religion");

        // 政治制度
        addMutualExclusive(exclusive, "authoritarianism", "democracy", "libertarianism");

        // 移民政策
        addMutualExclusive(exclusive, "open_immigration", "closed_borders", "restrictive_immigration");

        // 环境 vs 经济
        addMutualExclusive(exclusive, "environmental_regulation", "industrial_boom", "economic_deregulation");

        // 货币政策
        addMutualExclusive(exclusive, "tight_money", "quantitative_easing");
        addMutualExclusive(exclusive, "fixed_exchange_rate", "floating_exchange_rate");

        // 税收政策
        addMutualExclusive(exclusive, "progressive_tax", "flat_tax", "regressive_tax");

        // 媒体政策
        addMutualExclusive(exclusive, "free_press", "state_media", "censorship");

        // 核政策
        addMutualExclusive(exclusive, "nuclear_proliferation", "nuclear_disarmament");

        // 联盟政策
        addMutualExclusive(exclusive, "military_alliance", "non_alignment");

        MUTUALLY_EXCLUSIVE = Collections.unmodifiableMap(exclusive);
    }

    /**
     * 添加互斥关系（双向）
     */
    private static void addMutualExclusive(Map<String, Set<String>> map, String... policies) {
        for (int i = 0; i < policies.length; i++) {
            for (int j = 0; j < policies.length; j++) {
                if (i != j) {
                    map.computeIfAbsent(policies[i], k -> new HashSet<>()).add(policies[j]);
                }
            }
        }
    }

    // ==================== 协同政策映射 ====================
    // 同时激活时获得额外加成的政策组合

    private static final Map<String, Map<String, SynergyBonus>> SYNERGIES;

    static {
        Map<String, Map<String, SynergyBonus>> synergies = new HashMap<>();

        // 社会政策协同
        addSynergy(synergies, "universal_healthcare", "education_reform", 0.05, "健康教育双重提升");
        addSynergy(synergies, "universal_healthcare", "social_housing", 0.03, "社会福利完善");
        addSynergy(synergies, "social_housing", "education_reform", 0.04, "教育住房配套");
        addSynergy(synergies, "universal_education", "tech_investment", 0.08, "人才与科技双发展");
        addSynergy(synergies, "universal_education", "immigration_open", 0.04, "吸引国际人才");
        addSynergy(synergies, "social_welfare", "labor_protection", 0.04, "劳工权益保障");

        // 经济政策协同
        addSynergy(synergies, "infrastructure_investment", "industrial_policy", 0.06, "基建带动产业");
        addSynergy(synergies, "infrastructure_investment", "trade_infrastructure", 0.05, "物流优化");
        addSynergy(synergies, "free_trade", "diplomatic_openness", 0.04, "贸易外交双促进");
        addSynergy(synergies, "free_trade", "immigration_open", 0.03, "开放包容");
        addSynergy(synergies, "low_interest_rates", "infrastructure_investment", 0.04, "宽松信贷支持基建");
        addSynergy(synergies, "tax_cut", "deregulation", 0.08, "自由经济刺激");

        // 军事政策协同
        addSynergy(synergies, "nationalism", "strong_military", 0.06, "民族主义军事强化");
        addSynergy(synergies, "nationalism", "cultural_heritage", 0.04, "文化认同");
        addSynergy(synergies, "strong_military", "industrial_policy", 0.05, "军事工业结合");
        addSynergy(synergies, "professional_army", "tech_investment", 0.06, "科技强军");
        addSynergy(synergies, "military_research", "tech_investment", 0.10, "军民融合");
        addSynergy(synergies, "conscription", "nationalism", 0.05, "爱国动员");

        // 外交政策协同
        addSynergy(synergies, "cultural_diplomacy", "trade_agreements", 0.05, "文化贸易双拓展");
        addSynergy(synergies, "bilateralism", "free_trade", 0.04, "双边自贸");
        addSynergy(synergies, "multilateralism", "diplomatic_openness", 0.06, "多边合作");
        addSynergy(synergies, "foreign_aid", "cultural_diplomacy", 0.04, "援助外交");

        // 科技政策协同
        addSynergy(synergies, "tech_investment", "education_reform", 0.08, "科教兴国");
        addSynergy(synergies, "tech_investment", "research_grants", 0.10, "研发激励");
        addSynergy(synergies, "tech_investment", "immigration_open", 0.05, "吸引科技人才");
        addSynergy(synergies, "green_energy", "tech_investment", 0.06, "绿色科技");

        // 资源政策协同
        addSynergy(synergies, "strategic_reserves", "trade_infrastructure", 0.03, "储备物流配套");
        addSynergy(synergies, "resource_nationalization", "industrial_policy", 0.05, "资源工业一体化");

        // 内政政策协同
        addSynergy(synergies, "corruption_crackdown", "government_reform", 0.08, "反腐改革双管齐下");
        addSynergy(synergies, "decentralization", "education_reform", 0.04, "地方自主教育");
        addSynergy(synergies, "bureaucracy_reform", "tech_investment", 0.05, "简政放权科技");

        // 文化政策协同
        addSynergy(synergies, "cultural_heritage", "tourism", 0.05, "文化旅游");
        addSynergy(synergies, "arts_funding", "education_reform", 0.04, "艺术教育");
        addSynergy(synergies, "nationalism", "propaganda", 0.06, "宣传动员");

        SYNERGIES = Collections.unmodifiableMap(synergies);
    }

    /**
     * 添加协同效果（双向）
     */
    private static void addSynergy(Map<String, Map<String, SynergyBonus>> synergies,
                                   String policy1, String policy2,
                                   double bonus, String description) {
        synergies.computeIfAbsent(policy1, k -> new HashMap<>())
                 .put(policy2, new SynergyBonus(bonus, description));
        synergies.computeIfAbsent(policy2, k -> new HashMap<>())
                 .put(policy1, new SynergyBonus(bonus, description));
    }

    // ==================== 公共 API ====================

    /**
     * 检查两个政策是否互斥
     */
    public static boolean isMutuallyExclusive(String policy1, String policy2) {
        String p1 = normalize(policy1);
        String p2 = normalize(policy2);

        Set<String> exclusive = MUTUALLY_EXCLUSIVE.get(p1);
        return exclusive != null && exclusive.contains(p2);
    }

    /**
     * 获取与指定政策互斥的所有政策
     */
    public static Set<String> getExclusivePolicies(String policy) {
        Set<String> exclusive = MUTUALLY_EXCLUSIVE.get(normalize(policy));
        return exclusive == null ? Set.of() : Collections.unmodifiableSet(exclusive);
    }

    /**
     * 检查两个政策是否有协同效果
     * audit B-162: addSynergy 已双向写入，第二个 if 永不命中，删除以避免歧义
     */
    public static Optional<SynergyBonus> getSynergy(String policy1, String policy2) {
        String p1 = normalize(policy1);
        String p2 = normalize(policy2);

        Map<String, SynergyBonus> p1Synergies = SYNERGIES.get(p1);
        if (p1Synergies != null) {
            SynergyBonus bonus = p1Synergies.get(p2);
            if (bonus != null) {
                return Optional.of(bonus);
            }
        }
        return Optional.empty();
    }

    /**
     * 获取与指定政策有协同效果的所有政策
     */
    public static Map<String, SynergyBonus> getSynergyPolicies(String policy) {
        String normalized = normalize(policy);

        Map<String, SynergyBonus> result = new HashMap<>();

        Map<String, SynergyBonus> p1Synergies = SYNERGIES.get(normalized);
        if (p1Synergies != null) {
            result.putAll(p1Synergies);
        }

        // 双向检查
        for (Map.Entry<String, Map<String, SynergyBonus>> entry : SYNERGIES.entrySet()) {
            if (entry.getValue().containsKey(normalized)) {
                result.put(entry.getKey(), entry.getValue().get(normalized));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取所有协同政策
     */
    public static Map<String, Map<String, SynergyBonus>> getAllSynergies() {
        return SYNERGIES;
    }

    /**
     * 计算两个政策同时激活时的总加成
     */
    public static double calculateSynergyBonus(String policy1, String policy2) {
        return getSynergy(policy1, policy2)
            .map(SynergyBonus::bonus)
            .orElse(0.0);
    }

    /**
     * 检查政策是否可激活（考虑互斥关系）
     */
    public static List<String> checkActivation(String newPolicy, Collection<String> activePolicies) {
        List<String> blockers = new ArrayList<>();
        String normalized = normalize(newPolicy);

        for (String active : activePolicies) {
            if (isMutuallyExclusive(normalized, active)) {
                blockers.add(active);
            }
        }

        return blockers;
    }

    /**
     * 标准化政策键
     */
    private static String normalize(String policy) {
        return policy == null ? "" : policy.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // ==================== 内部类 ====================

    /**
     * 协同加成记录
     */
    public record SynergyBonus(double bonus, String description) {
        public double bonusPercentage() {
            return bonus * 100;
        }
    }
}
