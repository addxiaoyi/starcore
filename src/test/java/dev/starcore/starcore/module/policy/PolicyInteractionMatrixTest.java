package dev.starcore.starcore.module.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * PolicyInteractionMatrix 策略交互矩阵测试
 *
 * 验证互斥关系、协同加成、冲突检测等核心逻辑。
 */
@DisplayName("PolicyInteractionMatrix - 策略交互矩阵")
class PolicyInteractionMatrixTest {

    // ========== 互斥关系测试 ==========

    @Nested
    @DisplayName("互斥关系 (isMutuallyExclusive)")
    class MutualExclusivity {

        @Test
        @DisplayName("自由贸易与保护主义互斥")
        void freeTrade_conflictsWithProtectionism() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("free_trade", "protectionism")).isTrue();
        }

        @Test
        @DisplayName("自由贸易与闭关锁国互斥")
        void freeTrade_conflictsWithAutarky() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("free_trade", "autarky")).isTrue();
        }

        @Test
        @DisplayName("孤立主义与全球主义互斥")
        void isolationism_conflictsWithGlobalism() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("isolationism", "globalism")).isTrue();
        }

        @Test
        @DisplayName("常备军与义务兵役互斥")
        void professionalArmy_conflictsWithConscription() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("professional_army", "conscription")).isTrue();
        }

        @Test
        @DisplayName("军事同盟与不结盟互斥")
        void militaryAlliance_conflictsWithNonAlignment() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("military_alliance", "non_alignment")).isTrue();
        }

        @Test
        @DisplayName("威权主义与民主主义互斥")
        void authoritarianism_conflictsWithDemocracy() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("authoritarianism", "democracy")).isTrue();
        }

        @Test
        @DisplayName("渐进税与统一税率互斥")
        void progressiveTax_conflictsWithFlatTax() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("progressive_tax", "flat_tax")).isTrue();
        }

        @Test
        @DisplayName("核武器扩散与核裁军互斥")
        void nuclearProliferation_conflictsWithNuclearDisarmament() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("nuclear_proliferation", "nuclear_disarmament")).isTrue();
        }

        @Test
        @DisplayName("非互斥政策返回 false")
        void nonExclusivePolicies_returnFalse() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("free_trade", "universal_healthcare")).isFalse();
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("military_alliance", "free_trade")).isFalse();
        }

        @Test
        @DisplayName("互斥关系是双向的")
        void mutualExclusivity_isBidirectional() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("protectionism", "free_trade")).isTrue();
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("autarky", "free_trade")).isTrue();
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("FREE_TRADE", "protectionism")).isTrue();
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("free_trade", "PROTECTIONISM")).isTrue();
        }

        @Test
        @DisplayName("空白被正确处理")
        void whitespace_handled() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("  free_trade  ", "protectionism")).isTrue();
        }

        @Test
        @DisplayName("null 输入返回 false")
        void nullInput_returnsFalse() {
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive(null, "protectionism")).isFalse();
            assertThat(PolicyInteractionMatrix.isMutuallyExclusive("free_trade", null)).isFalse();
        }
    }

    // ========== 获取互斥政策列表 ==========

    @Nested
    @DisplayName("获取互斥政策 (getExclusivePolicies)")
    class GetExclusivePolicies {

        @Test
        @DisplayName("自由贸易有三个互斥政策")
        void freeTrade_hasThreeExclusivePolicies() {
            Set<String> exclusive = PolicyInteractionMatrix.getExclusivePolicies("free_trade");
            assertThat(exclusive).containsExactlyInAnyOrder("protectionism", "autarky");
        }

        @Test
        @DisplayName("孤立主义有多个互斥政策")
        void isolationism_hasMultipleExclusivePolicies() {
            Set<String> exclusive = PolicyInteractionMatrix.getExclusivePolicies("isolationism");
            assertThat(exclusive).contains("globalism", "expansionism", "imperialism");
        }

        @Test
        @DisplayName("未知政策返回空集合")
        void unknownPolicy_returnsEmptySet() {
            Set<String> exclusive = PolicyInteractionMatrix.getExclusivePolicies("nonexistent_policy");
            assertThat(exclusive).isEmpty();
        }

        @Test
        @DisplayName("返回不可变集合")
        void returnsUnmodifiableSet() {
            Set<String> exclusive = PolicyInteractionMatrix.getExclusivePolicies("free_trade");
            assertThatThrownBy(() -> exclusive.add("new_conflict"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ========== 协同效果测试 ==========

    @Nested
    @DisplayName("协同效果 (getSynergy)")
    class SynergyTests {

        @Test
        @DisplayName("全民医疗+教育改革有协同效果")
        void healthcareAndEducation_haveSynergy() {
            Optional<PolicyInteractionMatrix.SynergyBonus> synergy =
                PolicyInteractionMatrix.getSynergy("universal_healthcare", "education_reform");

            assertThat(synergy).isPresent();
            assertThat(synergy.get().bonus()).isEqualTo(0.05);
            assertThat(synergy.get().description()).isEqualTo("健康教育双重提升");
        }

        @Test
        @DisplayName("基建投资+产业政策有协同效果")
        void infrastructureAndIndustry_haveSynergy() {
            Optional<PolicyInteractionMatrix.SynergyBonus> synergy =
                PolicyInteractionMatrix.getSynergy("infrastructure_investment", "industrial_policy");

            assertThat(synergy).isPresent();
            assertThat(synergy.get().bonus()).isEqualTo(0.06);
        }

        @Test
        @DisplayName("军民融合有最高协同加成 (10%)")
        void militaryResearchAndTech_hasHighestSynergy() {
            Optional<PolicyInteractionMatrix.SynergyBonus> synergy =
                PolicyInteractionMatrix.getSynergy("military_research", "tech_investment");

            assertThat(synergy).isPresent();
            assertThat(synergy.get().bonus()).isEqualTo(0.10);
            assertThat(synergy.get().bonusPercentage()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("无协同效果返回 empty")
        void noSynergy_returnsEmpty() {
            Optional<PolicyInteractionMatrix.SynergyBonus> synergy =
                PolicyInteractionMatrix.getSynergy("free_trade", "protectionism");

            assertThat(synergy).isEmpty();
        }

        @Test
        @DisplayName("协同效果是双向的")
        void synergy_isBidirectional() {
            var s1 = PolicyInteractionMatrix.getSynergy("universal_healthcare", "education_reform");
            var s2 = PolicyInteractionMatrix.getSynergy("education_reform", "universal_healthcare");

            assertThat(s1).isPresent();
            assertThat(s2).isPresent();
            assertThat(s1.get().bonus()).isEqualTo(s2.get().bonus());
        }

        @Test
        @DisplayName("大小写不敏感")
        void caseInsensitive() {
            var s1 = PolicyInteractionMatrix.getSynergy("UNIVERSAL_HEALTHCARE", "education_reform");
            var s2 = PolicyInteractionMatrix.getSynergy("universal_healthcare", "EDUCATION_REFORM");

            assertThat(s1).isPresent();
            assertThat(s2).isPresent();
        }
    }

    // ========== 计算协同加成 ==========

    @Nested
    @DisplayName("计算协同加成 (calculateSynergyBonus)")
    class CalculateSynergyBonus {

        @Test
        @DisplayName("有协同时返回正确加成值")
        void withSynergy_returnsCorrectBonus() {
            double bonus = PolicyInteractionMatrix.calculateSynergyBonus(
                "universal_healthcare", "education_reform"
            );
            assertThat(bonus).isEqualTo(0.05);
        }

        @Test
        @DisplayName("无协同时返回 0")
        void withoutSynergy_returnsZero() {
            double bonus = PolicyInteractionMatrix.calculateSynergyBonus(
                "free_trade", "protectionism"
            );
            assertThat(bonus).isEqualTo(0.0);
        }
    }

    // ========== 获取协同政策列表 ==========

    @Nested
    @DisplayName("获取协同政策 (getSynergyPolicies)")
    class GetSynergyPolicies {

        @Test
        @DisplayName("返回所有协同政策")
        void returnsAllSynergyPolicies() {
            Map<String, PolicyInteractionMatrix.SynergyBonus> synergies =
                PolicyInteractionMatrix.getSynergyPolicies("tech_investment");

            assertThat(synergies).containsKey("universal_education");
            assertThat(synergies).containsKey("education_reform");
            assertThat(synergies).containsKey("research_grants");
        }

        @Test
        @DisplayName("未知政策返回空映射")
        void unknownPolicy_returnsEmptyMap() {
            Map<String, PolicyInteractionMatrix.SynergyBonus> synergies =
                PolicyInteractionMatrix.getSynergyPolicies("nonexistent");

            assertThat(synergies).isEmpty();
        }

        @Test
        @DisplayName("返回不可变映射")
        void returnsUnmodifiableMap() {
            Map<String, PolicyInteractionMatrix.SynergyBonus> synergies =
                PolicyInteractionMatrix.getSynergyPolicies("tech_investment");

            assertThatThrownBy(() -> synergies.put("new", null))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ========== 激活冲突检测 ==========

    @Nested
    @DisplayName("激活冲突检测 (checkActivation)")
    class ActivationCheck {

        @Test
        @DisplayName("激活新政策时检测到冲突")
        void detectsConflict() {
            var blockers = PolicyInteractionMatrix.checkActivation(
                "free_trade",
                java.util.List.of("protectionism", "universal_healthcare")
            );

            assertThat(blockers).contains("protectionism");
            assertThat(blockers).doesNotContain("universal_healthcare");
        }

        @Test
        @DisplayName("无冲突时返回空列表")
        void noConflict_returnsEmptyList() {
            var blockers = PolicyInteractionMatrix.checkActivation(
                "military_alliance",
                java.util.List.of("universal_healthcare", "education_reform")
            );

            assertThat(blockers).isEmpty();
        }

        @Test
        @DisplayName("空已激活列表返回空列表")
        void emptyActiveList_returnsEmptyList() {
            var blockers = PolicyInteractionMatrix.checkActivation(
                "free_trade",
                java.util.List.of()
            );

            assertThat(blockers).isEmpty();
        }

        @Test
        @DisplayName("检测多个冲突")
        void detectsMultipleConflicts() {
            var blockers = PolicyInteractionMatrix.checkActivation(
                "isolationism",
                java.util.List.of("globalism", "expansionism")
            );

            assertThat(blockers).contains("globalism", "expansionism");
        }
    }

    // ========== SynergyBonus 记录测试 ==========

    @Nested
    @DisplayName("SynergyBonus 记录")
    class SynergyBonusRecord {

        @Test
        @DisplayName("bonusPercentage() 正确计算百分比")
        void bonusPercentage_calculatesCorrectly() {
            var bonus = new PolicyInteractionMatrix.SynergyBonus(0.05, "测试");
            assertThat(bonus.bonusPercentage()).isEqualTo(5.0);

            var bonus2 = new PolicyInteractionMatrix.SynergyBonus(0.10, "测试");
            assertThat(bonus2.bonusPercentage()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("记录相等性正确")
        void recordEquality_works() {
            var bonus1 = new PolicyInteractionMatrix.SynergyBonus(0.05, "测试");
            var bonus2 = new PolicyInteractionMatrix.SynergyBonus(0.05, "测试");
            assertThat(bonus1).isEqualTo(bonus2);
        }
    }

    // ========== 全局协同映射 ==========

    @Nested
    @DisplayName("全局协同映射 (getAllSynergies)")
    class GetAllSynergies {

        @Test
        @DisplayName("返回非空映射")
        void returnsNonEmptyMap() {
            Map<String, Map<String, PolicyInteractionMatrix.SynergyBonus>> all =
                PolicyInteractionMatrix.getAllSynergies();

            assertThat(all).isNotEmpty();
            assertThat(all).containsKey("universal_healthcare");
            assertThat(all).containsKey("tech_investment");
        }

        @Test
        @DisplayName("协同映射不可变")
        void synergiesMap_isUnmodifiable() {
            Map<String, Map<String, PolicyInteractionMatrix.SynergyBonus>> all =
                PolicyInteractionMatrix.getAllSynergies();

            assertThatThrownBy(() -> all.put("new_policy", Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
