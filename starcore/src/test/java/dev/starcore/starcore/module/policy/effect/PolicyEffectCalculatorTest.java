package dev.starcore.starcore.module.policy.effect;

import dev.starcore.starcore.module.policy.PolicyInteractionMatrix;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.policy.model.PolicyCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * PolicyEffectCalculator 效果计算器测试
 *
 * 验证稳定度、支持率、经济影响、军事影响、冲突检测、综合评估。
 */
@DisplayName("PolicyEffectCalculator - 效果计算器")
class PolicyEffectCalculatorTest {

    // ========== 测试数据工厂 ==========

    private PolicyDefinition freeTradePolicy() {
        return new PolicyDefinition(
            "free_trade", "自由贸易", PolicyCategory.ECONOMY,
            Set.of(), new BigDecimal("100"), 86400L, 3600L,
            Set.of("protectionism", "autarky"),
            List.of(
                new PolicyEffect("ECONOMIC_GROWTH", PolicyEffectScope.GLOBAL, 0.10, "经济增长+10%"),
                new PolicyEffect("TRADE_INCOME", PolicyEffectScope.GLOBAL, 0.05, "贸易收入+5%")
            )
        );
    }

    private PolicyDefinition healthcarePolicy() {
        return new PolicyDefinition(
            "universal_healthcare", "全民医疗", PolicyCategory.SOCIAL,
            Set.of(), new BigDecimal("200"), -1L, 7200L,
            Set.of(),
            List.of(
                new PolicyEffect("HAPPINESS_MODIFIER", PolicyEffectScope.GLOBAL, 0.15, "幸福度+15%"),
                new PolicyEffect("STABILITY", PolicyEffectScope.GLOBAL, 0.10, "稳定性+10%")
            )
        );
    }

    private PolicyDefinition militaryPolicy() {
        return new PolicyDefinition(
            "strong_military", "强军政策", PolicyCategory.MILITARY,
            Set.of(), new BigDecimal("300"), 86400L, 3600L,
            Set.of(),
            List.of(
                new PolicyEffect("MILITARY_STRENGTH", PolicyEffectScope.MILITARY, 0.20, "军事力量+20%")
            )
        );
    }

    // ========== 基础效果计算 ==========

    @Nested
    @DisplayName("基础效果计算 (calculateEffect)")
    class CalculateEffect {

        @Test
        @DisplayName("GLOBAL scope 返回所有效果之和")
        void globalScope_returnsSumOfEffects() {
            double total = PolicyEffectCalculator.calculateEffect(
                freeTradePolicy(), PolicyEffectScope.GLOBAL
            );
            assertThat(total).isCloseTo(0.15, within(0.001));
        }

        @Test
        @DisplayName("无匹配 scope 返回 0")
        void noMatchingScope_returnsZero() {
            double total = PolicyEffectCalculator.calculateEffect(
                healthcarePolicy(), PolicyEffectScope.ECONOMY
            );
            assertThat(total).isEqualTo(0.0);
        }
    }

    // ========== 协同加成计算 ==========

    @Nested
    @DisplayName("协同加成计算")
    class SynergyCalculation {

        @Test
        @DisplayName("有协同的两个政策返回正确加成")
        void withSynergy_returnsCorrectBonus() {
            double bonus = PolicyEffectCalculator.calculateSynergyBonus(
                healthcarePolicy(), freeTradePolicy()
            );
            // 无直接协同
            assertThat(bonus).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("医疗+教育有协同加成")
        void healthcareAndEducation_haveSynergy() {
            var education = new PolicyDefinition(
                "education_reform", "教育改革", PolicyCategory.SOCIAL,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            );
            double bonus = PolicyEffectCalculator.calculateSynergyBonus(
                healthcarePolicy(), education
            );
            assertThat(bonus).isEqualTo(0.05);
        }

        @Test
        @DisplayName("总协同加成累加")
        void totalSynergy_sumsAll() {
            var active = List.of(
                healthcarePolicy(),
                new PolicyDefinition(
                    "social_housing", "社会住房", PolicyCategory.SOCIAL,
                    Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
                )
            );

            var education = new PolicyDefinition(
                "education_reform", "教育改革", PolicyCategory.SOCIAL,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            );

            double total = PolicyEffectCalculator.calculateTotalSynergyBonus(
                List.of("universal_healthcare", "social_housing"),
                "education_reform"
            );
            // education + healthcare = 0.05, education + social_housing = 0.04
            assertThat(total).isEqualTo(0.09);
        }

        @Test
        @DisplayName("获取所有协同政策")
        void getAllSynergyPolicies() {
            Map<String, PolicyInteractionMatrix.SynergyBonus> synergies =
                PolicyEffectCalculator.getSynergyWithActive(
                    List.of("universal_healthcare", "social_housing"),
                    "education_reform"
                );

            assertThat(synergies).containsKey("universal_healthcare");
            assertThat(synergies.get("universal_healthcare").bonus()).isEqualTo(0.05);
        }
    }

    // ========== 稳定度计算 ==========

    @Nested
    @DisplayName("稳定度计算 (calculateStabilityImpact)")
    class StabilityCalculation {

        @Test
        @DisplayName("医疗政策增加稳定度")
        void healthcarePolicy_increasesStability() {
            double stability = PolicyEffectCalculator.calculateStabilityImpact(
                List.of(healthcarePolicy())
            );
            assertThat(stability).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("自由贸易政策影响稳定度")
        void freeTradePolicy_affectsStability() {
            double stability = PolicyEffectCalculator.calculateStabilityImpact(
                List.of(freeTradePolicy())
            );
            // ECONOMIC_GROWTH contributes 0.10 * 0.2 = 0.02
            assertThat(stability).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("空列表返回 0")
        void emptyList_returnsZero() {
            double stability = PolicyEffectCalculator.calculateStabilityImpact(List.of());
            assertThat(stability).isEqualTo(0.0);
        }

        @Test
        @DisplayName("多个政策稳定度可累加")
        void multiplePolicies_addsUp() {
            double single = PolicyEffectCalculator.calculateStabilityImpact(
                List.of(healthcarePolicy())
            );
            double combined = PolicyEffectCalculator.calculateStabilityImpact(
                List.of(healthcarePolicy(), freeTradePolicy())
            );
            assertThat(combined).isGreaterThanOrEqualTo(single);
        }
    }

    // ========== 支持率计算 ==========

    @Nested
    @DisplayName("支持率计算 (calculateApprovalImpact)")
    class ApprovalCalculation {

        @Test
        @DisplayName("医疗政策增加支持率")
        void healthcarePolicy_increasesApproval() {
            double approval = PolicyEffectCalculator.calculateApprovalImpact(
                List.of(healthcarePolicy())
            );
            // HAPPINESS 0.15 * 0.3 = 0.045 + STABILITY 0.10 * 0.2 = 0.02
            assertThat(approval).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("空列表返回 0")
        void emptyList_returnsZero() {
            double approval = PolicyEffectCalculator.calculateApprovalImpact(List.of());
            assertThat(approval).isEqualTo(0.0);
        }
    }

    // ========== 经济影响计算 ==========

    @Nested
    @DisplayName("经济影响计算 (calculateEconomicImpact)")
    class EconomicImpactCalculation {

        @Test
        @DisplayName("自由贸易增加经济增长")
        void freeTrade_increasesGrowth() {
            var impact = PolicyEffectCalculator.calculateEconomicImpact(
                List.of(freeTradePolicy())
            );
            assertThat(impact.growth()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("netEffect 计算正确")
        void netEffect_calculatedCorrectly() {
            var impact = new PolicyEffectCalculator.EconomicImpact(0.10, 0.03, 0.05);
            assertThat(impact.netEffect()).isCloseTo(0.07, within(0.001));
        }

        @Test
        @DisplayName("空列表返回零影响")
        void emptyList_returnsZeroImpact() {
            var impact = PolicyEffectCalculator.calculateEconomicImpact(List.of());
            assertThat(impact.growth()).isEqualTo(0.0);
            assertThat(impact.inflation()).isEqualTo(0.0);
            assertThat(impact.employment()).isEqualTo(0.0);
        }
    }

    // ========== 军事影响计算 ==========

    @Nested
    @DisplayName("军事影响计算 (calculateMilitaryImpact)")
    class MilitaryImpactCalculation {

        @Test
        @DisplayName("军事政策影响军事指标")
        void militaryPolicy_affectsMetrics() {
            var impact = PolicyEffectCalculator.calculateMilitaryImpact(
                List.of(militaryPolicy())
            );
            // MILITARY_STRENGTH key 包含 "military"，匹配 MORALE
            assertThat(impact.morale()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("combatPower 计算正确")
        void combatPower_calculatedCorrectly() {
            var impact = new PolicyEffectCalculator.MilitaryImpact(0.5, 0.3, 0.2);
            // (0.5 + 0.3) * (1 + 0.2) = 0.8 * 1.2 = 0.96
            assertThat(impact.combatPower()).isCloseTo(0.96, within(0.001));
        }

        @Test
        @DisplayName("空列表返回零影响")
        void emptyList_returnsZeroImpact() {
            var impact = PolicyEffectCalculator.calculateMilitaryImpact(List.of());
            assertThat(impact.defense()).isEqualTo(0.0);
            assertThat(impact.offense()).isEqualTo(0.0);
            assertThat(impact.morale()).isEqualTo(0.0);
        }
    }

    // ========== 冲突检测 ==========

    @Nested
    @DisplayName("冲突检测 (checkConflict)")
    class ConflictDetection {

        @Test
        @DisplayName("检测到互斥政策")
        void detectsMutuallyExclusive() {
            var result = PolicyEffectCalculator.checkConflict(
                "free_trade",
                List.of("protectionism", "universal_healthcare")
            );

            assertThat(result.hasConflict()).isTrue();
            assertThat(result.exclusivePolicies()).contains("protectionism");
            assertThat(result.exclusivePolicies()).doesNotContain("universal_healthcare");
        }

        @Test
        @DisplayName("无冲突时 hasConflict 为 false")
        void noConflict_hasConflictIsFalse() {
            var result = PolicyEffectCalculator.checkConflict(
                "military_alliance",
                List.of("universal_healthcare", "education_reform")
            );

            assertThat(result.hasConflict()).isFalse();
            assertThat(result.exclusivePolicies()).isEmpty();
        }

        @Test
        @DisplayName("检测到协同效果")
        void detectsSynergy() {
            var result = PolicyEffectCalculator.checkConflict(
                "education_reform",
                List.of("universal_healthcare")
            );

            assertThat(result.hasSynergy()).isTrue();
            assertThat(result.totalSynergyBonus()).isEqualTo(0.05);
        }

        @Test
        @DisplayName("无协同时 hasSynergy 为 false")
        void noSynergy_hasSynergyIsFalse() {
            var result = PolicyEffectCalculator.checkConflict(
                "free_trade",
                List.of("protectionism")
            );

            assertThat(result.hasSynergy()).isFalse();
            assertThat(result.totalSynergyBonus()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("多个协同累加")
        void multipleSynergies_sumUp() {
            var result = PolicyEffectCalculator.checkConflict(
                "education_reform",
                List.of("universal_healthcare", "social_housing")
            );

            assertThat(result.hasSynergy()).isTrue();
            // 0.05 + 0.04 = 0.09
            assertThat(result.totalSynergyBonus()).isEqualTo(0.09);
        }
    }

    // ========== 综合评估 ==========

    @Nested
    @DisplayName("综合评估 (assess)")
    class ComprehensiveAssessment {

        @Test
        @DisplayName("空列表返回零值评估")
        void emptyList_returnsZeroAssessment() {
            var assessment = PolicyEffectCalculator.assess(List.of());

            assertThat(assessment.stability()).isEqualTo(0.0);
            assertThat(assessment.approval()).isEqualTo(0.0);
            assertThat(assessment.activeCount()).isEqualTo(0);
            assertThat(assessment.isStable()).isFalse();
            assertThat(assessment.isPopular()).isFalse();
        }

        @Test
        @DisplayName("医疗政策评估为稳定")
        void healthcarePolicy_assessesAsStable() {
            var assessment = PolicyEffectCalculator.assess(List.of(healthcarePolicy()));

            assertThat(assessment.stability()).isGreaterThan(0);
            assertThat(assessment.isStable()).isTrue();
        }

        @Test
        @DisplayName("activeCount 正确计数")
        void activeCount_countsCorrectly() {
            var assessment = PolicyEffectCalculator.assess(
                List.of(healthcarePolicy(), freeTradePolicy(), militaryPolicy())
            );
            assertThat(assessment.activeCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("overallScore 返回有效评级")
        void overallScore_returnsValidRating() {
            var assessment = PolicyEffectCalculator.assess(List.of(healthcarePolicy()));

            assertThat(assessment.overallScore())
                .isIn("优秀", "良好", "一般", "较差", "危险");
        }

        @Test
        @DisplayName("isPopular 判断支持率")
        void isPopular_checksApproval() {
            var assessment = PolicyEffectCalculator.assess(List.of(healthcarePolicy()));
            assertThat(assessment.isPopular()).isTrue();
        }
    }

    // ========== 内部记录类测试 ==========

    @Nested
    @DisplayName("内部记录类")
    class InternalRecords {

        @Test
        @DisplayName("ConflictResult.hasSynergy() 正确")
        void conflictResult_hasSynergy() {
            var withSynergy = new PolicyEffectCalculator.ConflictResult(
                false, List.of(), List.of(
                    new PolicyEffectCalculator.SynergyInfo("policy1", 0.05, "协同")
                )
            );
            assertThat(withSynergy.hasSynergy()).isTrue();

            var withoutSynergy = new PolicyEffectCalculator.ConflictResult(
                false, List.of(), List.of()
            );
            assertThat(withoutSynergy.hasSynergy()).isFalse();
        }

        @Test
        @DisplayName("SynergyInfo.bonusPercentage() 正确")
        void synergyInfo_bonusPercentage() {
            var info = new PolicyEffectCalculator.SynergyInfo("policy", 0.05, "描述");
            assertThat(info.bonusPercentage()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("EconomicImpact.netEffect() 正确")
        void economicImpact_netEffect() {
            var impact = new PolicyEffectCalculator.EconomicImpact(0.15, 0.05, 0.10);
            assertThat(impact.netEffect()).isCloseTo(0.10, within(0.001));
        }

        @Test
        @DisplayName("MilitaryImpact.combatPower() 正确")
        void militaryImpact_combatPower() {
            var impact = new PolicyEffectCalculator.MilitaryImpact(0.6, 0.4, 0.1);
            // (0.6 + 0.4) * (1 + 0.1) = 1.0 * 1.1 = 1.1
            assertThat(impact.combatPower()).isCloseTo(1.1, within(0.001));
        }
    }
}
