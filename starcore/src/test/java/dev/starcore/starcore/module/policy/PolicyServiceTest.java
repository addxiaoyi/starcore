package dev.starcore.starcore.module.policy;

import dev.starcore.starcore.module.policy.effect.PolicyEffectCalculator;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyCategory;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * PolicyService 策略服务集成测试
 *
 * 测试策略的激活、冲突检测、协同计算等完整流程。
 * 使用模拟数据，不依赖数据库。
 */
@DisplayName("PolicyService - 策略服务集成测试")
class PolicyServiceTest {

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

    private PolicyDefinition protectionismPolicy() {
        return new PolicyDefinition(
            "protectionism", "保护主义", PolicyCategory.ECONOMY,
            Set.of(), new BigDecimal("100"), 86400L, 3600L,
            Set.of("free_trade", "autarky"),
            List.of(
                new PolicyEffect("DOMESTIC_INDUSTRY", PolicyEffectScope.ECONOMY, 0.08, "国内工业+8%")
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

    private PolicyDefinition educationPolicy() {
        return new PolicyDefinition(
            "education_reform", "教育改革", PolicyCategory.SOCIAL,
            Set.of(), new BigDecimal("150"), 86400L, 3600L,
            Set.of(),
            List.of(
                new PolicyEffect("APPROVAL_RATING", PolicyEffectScope.GLOBAL, 0.10, "支持率+10%"),
                new PolicyEffect("ECONOMIC_GROWTH", PolicyEffectScope.GLOBAL, 0.05, "经济增长+5%")
            )
        );
    }

    // ========== 策略激活验证 ==========

    @Nested
    @DisplayName("策略激活验证")
    class ActivationValidation {

        @Test
        @DisplayName("当激活自由贸易时，若保护主义已激活则应检测到冲突")
        void freeTrade_shouldConflictWithProtectionism() {
            var result = PolicyEffectCalculator.checkConflict(
                "free_trade",
                List.of("protectionism")
            );

            assertThat(result.hasConflict()).isTrue();
            assertThat(result.exclusivePolicies()).contains("protectionism");
        }

        @Test
        @DisplayName("当激活教育改革时，若全民医疗已激活则应检测到协同")
        void educationReform_shouldSynergizeWithHealthcare() {
            var result = PolicyEffectCalculator.checkConflict(
                "education_reform",
                List.of("universal_healthcare")
            );

            assertThat(result.hasConflict()).isFalse();
            assertThat(result.hasSynergy()).isTrue();
            assertThat(result.totalSynergyBonus()).isEqualTo(0.05); // 健康教育双重提升
        }

        @Test
        @DisplayName("当无冲突政策时，激活检查应返回空列表")
        void noConflictPolicies_shouldPass() {
            var result = PolicyEffectCalculator.checkConflict(
                "military_alliance",
                List.of("universal_healthcare")
            );

            assertThat(result.hasConflict()).isFalse();
        }
    }

    // ========== 协同效果计算 ==========

    @Nested
    @DisplayName("协同效果计算")
    class SynergyCalculation {

        @Test
        @DisplayName("全民医疗+教育改革应有5%协同加成")
        void healthcarePlusEducation_hasFivePercentSynergy() {
            double bonus = PolicyEffectCalculator.calculateSynergyBonus(
                healthcarePolicy(), educationPolicy()
            );
            assertThat(bonus).isEqualTo(0.05);
        }

        @Test
        @DisplayName("多个已激活政策的协同加成应累加")
        void multipleActivePolicies_sumsSynergies() {
            double totalSynergy = PolicyEffectCalculator.calculateTotalSynergyBonus(
                List.of("universal_healthcare", "social_housing"),
                "education_reform"
            );

            // healthcare + education_reform = 0.05, social_housing + education_reform = 0.04
            assertThat(totalSynergy).isEqualTo(0.09);
        }
    }

    // ========== 综合效果评估 ==========

    @Nested
    @DisplayName("综合效果评估")
    class ComprehensiveAssessment {

        @Test
        @DisplayName("激活多个政策后应能正确计算综合影响")
        void multiplePolicies_calculatesCorrectImpact() {
            var assessment = PolicyEffectCalculator.assess(
                List.of(freeTradePolicy(), healthcarePolicy())
            );

            assertThat(assessment.activeCount()).isEqualTo(2);
            assertThat(assessment.stability()).isGreaterThan(0); // healthcare 贡献稳定性
            assertThat(assessment.approval()).isGreaterThan(0); // healthcare + education 贡献支持率
        }

        @Test
        @DisplayName("空政策列表应返回零值评估")
        void emptyPolicies_returnsZeroAssessment() {
            var assessment = PolicyEffectCalculator.assess(List.of());

            assertThat(assessment.stability()).isEqualTo(0.0);
            assertThat(assessment.approval()).isEqualTo(0.0);
            assertThat(assessment.activeCount()).isEqualTo(0);
            assertThat(assessment.isStable()).isFalse();
            assertThat(assessment.isPopular()).isFalse();
        }

        @Test
        @DisplayName("经济政策应影响经济增长")
        void economicPolicy_affectsGrowth() {
            var impact = PolicyEffectCalculator.calculateEconomicImpact(
                List.of(freeTradePolicy())
            );

            assertThat(impact.growth()).isGreaterThan(0);
        }

        @Test
        @DisplayName("社会政策应影响稳定性")
        void socialPolicy_affectsStability() {
            double stability = PolicyEffectCalculator.calculateStabilityImpact(
                List.of(healthcarePolicy())
            );

            assertThat(stability).isGreaterThan(0);
        }
    }

    // ========== 策略冲突检测 ==========

    @Nested
    @DisplayName("策略冲突检测")
    class ConflictDetection {

        @Test
        @DisplayName("冲突键应被正确识别")
        void conflictKeys_areIdentified() {
            assertThat(freeTradePolicy().conflictsWith("protectionism")).isTrue();
            assertThat(freeTradePolicy().conflictsWith("autarky")).isTrue();
        }

        @Test
        @DisplayName("非冲突键不应被识别")
        void nonConflictKeys_areNotIdentified() {
            assertThat(freeTradePolicy().conflictsWith("military_alliance")).isFalse();
            assertThat(freeTradePolicy().conflictsWith("education_reform")).isFalse();
        }

        @Test
        @DisplayName("相互冲突的关系应双向成立")
        void mutualConflicts_areBidirectional() {
            assertThat(freeTradePolicy().conflictsWith("protectionism")).isTrue();
            assertThat(protectionismPolicy().conflictsWith("free_trade")).isTrue();
        }
    }

    // ========== 策略分类验证 ==========

    @Nested
    @DisplayName("策略分类验证")
    class CategoryValidation {

        @Test
        @DisplayName("经济政策应属于 ECONOMY 分类")
        void freeTrade_belongsToEconomy() {
            assertThat(freeTradePolicy().category()).isEqualTo(PolicyCategory.ECONOMY);
        }

        @Test
        @DisplayName("社会政策应属于 SOCIAL 分类")
        void healthcare_belongsToSocial() {
            assertThat(healthcarePolicy().category()).isEqualTo(PolicyCategory.SOCIAL);
        }

        @Test
        @DisplayName("军事政策应属于 MILITARY 分类")
        void military_belongsToMilitary() {
            var policy = new PolicyDefinition(
                "defense", "国防", PolicyCategory.MILITARY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            );
            assertThat(policy.category()).isEqualTo(PolicyCategory.MILITARY);
        }
    }

    // ========== 策略成本验证 ==========

    @Nested
    @DisplayName("策略成本验证")
    class CostValidation {

        @Test
        @DisplayName("策略成本应为非负数")
        void treasuryCost_mustBeNonNegative() {
            assertThat(freeTradePolicy().treasuryCost().signum()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("零成本政策应被接受")
        void zeroCost_isAccepted() {
            var freePolicy = new PolicyDefinition(
                "freebie", "免费政策", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 100L, 0L, Set.of(), List.of()
            );
            assertThat(freePolicy.treasuryCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("负成本政策应被拒绝")
        void negativeCost_isRejected() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "test", "测试", PolicyCategory.ECONOMY,
                Set.of(), new BigDecimal("-1"), 100L, 0L, Set.of(), List.of()
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
