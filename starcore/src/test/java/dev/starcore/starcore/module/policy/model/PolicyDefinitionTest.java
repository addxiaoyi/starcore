package dev.starcore.starcore.module.policy.model;

import dev.starcore.starcore.module.policy.PolicyInteractionMatrix;
import dev.starcore.starcore.module.policy.effect.PolicyEffectCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * PolicyDefinition 记录类测试
 *
 * 验证构造器行为、字段标准化、不变式保护。
 */
@DisplayName("PolicyDefinition - 策略定义模型")
class PolicyDefinitionTest {

    // ========== 正常构造 ==========

    @Test
    @DisplayName("有效参数创建实例")
    void validInput_createsInstance() {
        var policy = new PolicyDefinition(
            "FREE_TRADE",
            "自由贸易",
            PolicyCategory.ECONOMY,
            Set.of(),
            new BigDecimal("100"),
            86400L,
            3600L,
            Set.of("protectionism"),
            List.of(
                new PolicyEffect("ECONOMIC_GROWTH", PolicyEffectScope.GLOBAL, 0.10, "经济增长+10%")
            )
        );

        assertThat(policy.key()).isEqualTo("free_trade");
        assertThat(policy.displayName()).isEqualTo("自由贸易");
        assertThat(policy.category()).isEqualTo(PolicyCategory.ECONOMY);
        assertThat(policy.treasuryCost()).isEqualByComparingTo("100");
        assertThat(policy.durationSeconds()).isEqualTo(86400L);
        assertThat(policy.cooldownSeconds()).isEqualTo(3600L);
        assertThat(policy.conflictKeys()).contains("protectionism");
        assertThat(policy.effects()).hasSize(1);
    }

    @Test
    @DisplayName("永久政策 (duration = -1) 被接受")
    void permanentPolicy_acceptsDurationNegativeOne() {
        var policy = new PolicyDefinition(
            "constitution", "宪法", PolicyCategory.POLITICAL,
            Set.of(), BigDecimal.ZERO, -1L, 0L, Set.of(), List.of()
        );
        assertThat(policy.durationSeconds()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("零成本政策被接受")
    void zeroCostPolicy_accepted() {
        var policy = new PolicyDefinition(
            "basic_rights", "基本权利", PolicyCategory.SOCIAL,
            Set.of(), BigDecimal.ZERO, -1L, 0L, Set.of(), List.of()
        );
        assertThat(policy.treasuryCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========== key 标准化 ==========

    @Nested
    @DisplayName("key 标准化")
    class KeyNormalization {

        @Test
        @DisplayName("key 转为小写")
        void key_convertedToLowerCase() {
            var policy = new PolicyDefinition(
                "UPPER_CASE_KEY", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            );
            assertThat(policy.key()).isEqualTo("upper_case_key");
        }

        @Test
        @DisplayName("key 去除首尾空白")
        void key_trimmed() {
            var policy = new PolicyDefinition(
                "  spaced_key  ", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            );
            assertThat(policy.key()).isEqualTo("spaced_key");
        }

        @Test
        @DisplayName("prerequisiteKeys 标准化")
        void prerequisiteKeys_normalized() {
            var policy = new PolicyDefinition(
                "advanced", "进阶政策", PolicyCategory.ECONOMY,
                Set.of("FIRST_STEP", "  second_step  "), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            );
            assertThat(policy.prerequisiteKeys()).containsExactlyInAnyOrder("first_step", "second_step");
        }

        @Test
        @DisplayName("conflictKeys 标准化")
        void conflictKeys_normalized() {
            var policy = new PolicyDefinition(
                "trade", "贸易", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L,
                Set.of("PROTECTIONISM", "  autarky  "), List.of()
            );
            assertThat(policy.conflictKeys()).containsExactlyInAnyOrder("protectionism", "autarky");
        }
    }

    // ========== 冲突检测 ==========

    @Nested
    @DisplayName("conflictsWith 方法")
    class ConflictsWith {

        @Test
        @DisplayName("与定义的冲突键返回 true")
        void definedConflictKey_returnsTrue() {
            var policy = new PolicyDefinition(
                "free_trade", "自由贸易", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L,
                Set.of("protectionism", "autarky"), List.of()
            );
            assertThat(policy.conflictsWith("protectionism")).isTrue();
            assertThat(policy.conflictsWith("AUTARKY")).isTrue(); // 大小写不敏感
        }

        @Test
        @DisplayName("未定义的冲突键返回 false")
        void undefinedConflictKey_returnsFalse() {
            var policy = new PolicyDefinition(
                "free_trade", "自由贸易", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L,
                Set.of("protectionism"), List.of()
            );
            assertThat(policy.conflictsWith("military_alliance")).isFalse();
        }

        @Test
        @DisplayName("与自身比较返回 false")
        void selfComparison_returnsFalse() {
            var policy = new PolicyDefinition(
                "free_trade", "自由贸易", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L,
                Set.of("protectionism"), List.of()
            );
            assertThat(policy.conflictsWith("free_trade")).isFalse();
        }
    }

    // ========== 不变式验证 ==========

    @Nested
    @DisplayName("不变式保护")
    class InvariantValidation {

        @Test
        @DisplayName("null key 抛出 NullPointerException")
        void nullKey_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                null, "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("空 key 抛出 IllegalArgumentException")
        void emptyKey_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "   ", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("key cannot be empty");
        }

        @Test
        @DisplayName("null displayName 抛出 NullPointerException")
        void nullDisplayName_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", null, PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("空 displayName 抛出 IllegalArgumentException")
        void emptyDisplayName_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "   ", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("displayName cannot be empty");
        }

        @Test
        @DisplayName("null category 抛出 NullPointerException")
        void nullCategory_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", null,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null treasuryCost 抛出 NullPointerException")
        void nullTreasuryCost_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), null, 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("负 treasuryCost 抛出 IllegalArgumentException")
        void negativeTreasuryCost_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), new BigDecimal("-1"), 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("treasuryCost cannot be negative");
        }

        @Test
        @DisplayName("负 durationSeconds (除 -1) 抛出 IllegalArgumentException")
        void negativeDuration_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, -5L, 0L, Set.of(), List.of()
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("durationSeconds cannot be negative");
        }

        @Test
        @DisplayName("负 cooldownSeconds 抛出 IllegalArgumentException")
        void negativeCooldown_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, -1L, Set.of(), List.of()
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("cooldownSeconds cannot be negative");
        }

        @Test
        @DisplayName("null prerequisiteKeys 抛出 NullPointerException")
        void nullPrerequisiteKeys_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                null, BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null conflictKeys 抛出 NullPointerException")
        void nullConflictKeys_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, null, List.of()
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null effects 抛出 NullPointerException")
        void nullEffects_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), null
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("空 effects 列表被接受")
        void emptyEffects_accepted() {
            var policy = new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(), List.of()
            );
            assertThat(policy.effects()).isEmpty();
        }
    }

    // ========== 不可变性 ==========

    @Nested
    @DisplayName("不可变性")
    class Immutability {

        @Test
        @DisplayName("conflictKeys 返回不可变集合")
        void conflictKeys_isUnmodifiable() {
            var policy = new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of("conflict"), List.of()
            );
            assertThatThrownBy(() ->
                ((java.util.Set<String>) policy.conflictKeys()).add("new_conflict")
            ).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("effects 返回不可变列表")
        void effects_isUnmodifiable() {
            var policy = new PolicyDefinition(
                "key", "显示名", PolicyCategory.ECONOMY,
                Set.of(), BigDecimal.ZERO, 0L, 0L, Set.of(),
                List.of(new PolicyEffect("test", PolicyEffectScope.GLOBAL, 0.1, "测试"))
            );
            assertThatThrownBy(() ->
                policy.effects().add(new PolicyEffect("new", PolicyEffectScope.GLOBAL, 0.1, "新"))
            ).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
