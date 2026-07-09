package dev.starcore.starcore.module.policy.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * PolicyActivationResult 记录类测试
 *
 * 验证工厂方法、Optional 字段解包、失败原因。
 */
@DisplayName("PolicyActivationResult - 策略激活结果模型")
class PolicyActivationResultTest {

    private PolicyDefinition samplePolicy() {
        return new PolicyDefinition(
            "free_trade", "自由贸易", PolicyCategory.ECONOMY,
            Set.of(), new BigDecimal("100"), 86400L, 3600L,
            Set.of("protectionism"), List.of()
        );
    }

    // ========== 工厂方法 ==========

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("success() 创建成功的激活结果")
        void success_createsSuccessfulResult() {
            var policy = samplePolicy();
            var result = PolicyActivationResult.success(policy);

            assertThat(result.successful()).isTrue();
            assertThat(result.failure()).isNull();
            assertThat(result.policyDefinition()).isEqualTo(policy);
            assertThat(result.message()).isEqualTo("Policy activated");
        }

        @Test
        @DisplayName("failure() 创建失败的激活结果")
        void failure_createsFailedResult() {
            var policy = samplePolicy();
            var result = PolicyActivationResult.failure(
                PolicyActivationFailure.INSUFFICIENT_TREASURY,
                policy,
                "Not enough treasury"
            );

            assertThat(result.successful()).isFalse();
            assertThat(result.failure()).isEqualTo(PolicyActivationFailure.INSUFFICIENT_TREASURY);
            assertThat(result.policyDefinition()).isEqualTo(policy);
            assertThat(result.message()).isEqualTo("Not enough treasury");
        }
    }

    // ========== Optional 解包 ==========

    @Nested
    @DisplayName("Optional 解包方法")
    class OptionalMethods {

        @Test
        @DisplayName("failureReason() 成功时返回 empty Optional")
        void failureReason_success_returnsEmpty() {
            var result = PolicyActivationResult.success(samplePolicy());
            Optional<PolicyActivationFailure> reason = result.failureReason();

            assertThat(reason).isEmpty();
        }

        @Test
        @DisplayName("failureReason() 失败时返回 present Optional")
        void failureReason_failure_returnsPresent() {
            var result = PolicyActivationResult.failure(
                PolicyActivationFailure.COOLDOWN_NOT_EXPIRED,
                samplePolicy(),
                "Still cooling down"
            );
            Optional<PolicyActivationFailure> reason = result.failureReason();

            assertThat(reason).isPresent();
            assertThat(reason.get()).isEqualTo(PolicyActivationFailure.COOLDOWN_NOT_EXPIRED);
        }

        @Test
        @DisplayName("definition() 总是返回 present Optional")
        void definition_alwaysReturnsPresent() {
            var policy = samplePolicy();
            var successResult = PolicyActivationResult.success(policy);
            var failResult = PolicyActivationResult.failure(
                PolicyActivationFailure.INSUFFICIENT_TREASURY,
                policy,
                "Failed"
            );

            assertThat(successResult.definition()).isPresent();
            assertThat(successResult.definition().get()).isEqualTo(policy);
            assertThat(failResult.definition()).isPresent();
            assertThat(failResult.definition().get()).isEqualTo(policy);
        }
    }

    // ========== PolicyActivationFailure 枚举 ==========

    @Nested
    @DisplayName("PolicyActivationFailure 枚举")
    class FailureTypeTests {

        @Test
        @DisplayName("INSUFFICIENT_TREASURY 枚举值存在")
        void insufficientTreasury_enumExists() {
            var result = PolicyActivationResult.failure(
                PolicyActivationFailure.INSUFFICIENT_TREASURY,
                samplePolicy(),
                "Failed"
            );
            assertThat(result.failure()).isEqualTo(PolicyActivationFailure.INSUFFICIENT_TREASURY);
        }

        @Test
        @DisplayName("COOLDOWN_NOT_EXPIRED 枚举值存在")
        void cooldownNotExpired_enumExists() {
            var result = PolicyActivationResult.failure(
                PolicyActivationFailure.COOLDOWN_NOT_EXPIRED,
                samplePolicy(),
                "Failed"
            );
            assertThat(result.failure()).isEqualTo(PolicyActivationFailure.COOLDOWN_NOT_EXPIRED);
        }

        @Test
        @DisplayName("PREREQUISITES_NOT_MET 枚举值存在")
        void prerequisitesNotMet_enumExists() {
            var result = PolicyActivationResult.failure(
                PolicyActivationFailure.PREREQUISITES_NOT_MET,
                samplePolicy(),
                "Failed"
            );
            assertThat(result.failure()).isEqualTo(PolicyActivationFailure.PREREQUISITES_NOT_MET);
        }

        @Test
        @DisplayName("MUTUALLY_EXCLUSIVE 枚举值存在")
        void mutuallyExclusive_enumExists() {
            var result = PolicyActivationResult.failure(
                PolicyActivationFailure.MUTUALLY_EXCLUSIVE,
                samplePolicy(),
                "Failed"
            );
            assertThat(result.failure()).isEqualTo(PolicyActivationFailure.MUTUALLY_EXCLUSIVE);
        }

        @Test
        @DisplayName("NOT_LEADER 枚举值存在")
        void notLeader_enumExists() {
            var result = PolicyActivationResult.failure(
                PolicyActivationFailure.NOT_LEADER,
                samplePolicy(),
                "Failed"
            );
            assertThat(result.failure()).isEqualTo(PolicyActivationFailure.NOT_LEADER);
        }

        @Test
        @DisplayName("枚举值数量合理")
        void enumSize_isReasonable() {
            int count = PolicyActivationFailure.values().length;
            assertThat(count).isGreaterThanOrEqualTo(5);
        }
    }

    // ========== 记录相等性 ==========

    @Nested
    @DisplayName("记录相等性")
    class RecordEquality {

        @Test
        @DisplayName("相同参数的两次 success 调用结果相等")
        void sameParams_successResultsAreEqual() {
            var policy = samplePolicy();
            var result1 = PolicyActivationResult.success(policy);
            var result2 = PolicyActivationResult.success(policy);

            assertThat(result1).isEqualTo(result2);
        }

        @Test
        @DisplayName("相同参数的两次 failure 调用结果相等")
        void sameParams_failureResultsAreEqual() {
            var policy = samplePolicy();
            var result1 = PolicyActivationResult.failure(
                PolicyActivationFailure.INSUFFICIENT_TREASURY,
                policy,
                "Not enough"
            );
            var result2 = PolicyActivationResult.failure(
                PolicyActivationFailure.INSUFFICIENT_TREASURY,
                policy,
                "Not enough"
            );

            assertThat(result1).isEqualTo(result2);
        }
    }
}
