package dev.starcore.starcore.module.policy.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PolicyEffect 记录类测试
 *
 * 验证构造器行为、key 标准化、不变式保护。
 */
@DisplayName("PolicyEffect - 策略效果模型")
class PolicyEffectTest {

    // ========== 正常构造 ==========

    @Test
    @DisplayName("有效参数创建实例")
    void validInput_createsInstance() {
        var effect = new PolicyEffect(
            "STABILITY",
            PolicyEffectScope.GLOBAL,
            0.10,
            "稳定性提升10%"
        );

        assertThat(effect.key()).isEqualTo("stability");
        assertThat(effect.scope()).isEqualTo(PolicyEffectScope.GLOBAL);
        assertThat(effect.modifier()).isEqualTo(0.10);
        assertThat(effect.description()).isEqualTo("稳定性提升10%");
    }

    @Test
    @DisplayName("负 modifier 被接受")
    void negativeModifier_accepted() {
        var effect = new PolicyEffect(
            "REVOLUTION_RISK",
            PolicyEffectScope.GLOBAL,
            -0.5,
            "革命风险+50%"
        );
        assertThat(effect.modifier()).isEqualTo(-0.5);
    }

    @Test
    @DisplayName("零 modifier 被接受")
    void zeroModifier_accepted() {
        var effect = new PolicyEffect(
            "NEUTRAL",
            PolicyEffectScope.GLOBAL,
            0.0,
            "无效果"
        );
        assertThat(effect.modifier()).isEqualTo(0.0);
    }

    // ========== key 标准化 ==========

    @Nested
    @DisplayName("key 标准化")
    class KeyNormalization {

        @Test
        @DisplayName("key 转为小写")
        void key_convertedToLowerCase() {
            var effect = new PolicyEffect(
                "UPPER_CASE_KEY",
                PolicyEffectScope.GLOBAL,
                0.1,
                "描述"
            );
            assertThat(effect.key()).isEqualTo("upper_case_key");
        }

        @Test
        @DisplayName("key 去除首尾空白")
        void key_trimmed() {
            var effect = new PolicyEffect(
                "  spaced_key  ",
                PolicyEffectScope.GLOBAL,
                0.1,
                "描述"
            );
            assertThat(effect.key()).isEqualTo("spaced_key");
        }

        @Test
        @DisplayName("key 去除前后空白后转小写")
        void key_trimmedAndLowercased() {
            var effect = new PolicyEffect(
                "  Mixed_Case_Key  ",
                PolicyEffectScope.GLOBAL,
                0.1,
                "描述"
            );
            assertThat(effect.key()).isEqualTo("mixed_case_key");
        }
    }

    // ========== 不变式验证 ==========

    @Nested
    @DisplayName("不变式保护")
    class InvariantValidation {

        @Test
        @DisplayName("null key 抛出 NullPointerException")
        void nullKey_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyEffect(
                null, PolicyEffectScope.GLOBAL, 0.1, "描述"
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("key");
        }

        @Test
        @DisplayName("空 key 抛出 IllegalArgumentException")
        void emptyKey_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PolicyEffect(
                "   ", PolicyEffectScope.GLOBAL, 0.1, "描述"
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("key cannot be empty");
        }

        @Test
        @DisplayName("null scope 抛出 NullPointerException")
        void nullScope_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyEffect(
                "test", null, 0.1, "描述"
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("scope");
        }

        @Test
        @DisplayName("null description 抛出 NullPointerException")
        void nullDescription_throwsNullPointerException() {
            assertThatThrownBy(() -> new PolicyEffect(
                "test", PolicyEffectScope.GLOBAL, 0.1, null
            )).isInstanceOf(NullPointerException.class)
              .hasMessageContaining("description");
        }

        @Test
        @DisplayName("空 description 抛出 IllegalArgumentException")
        void emptyDescription_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> new PolicyEffect(
                "test", PolicyEffectScope.GLOBAL, 0.1, "   "
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("description cannot be empty");
        }

        @Test
        @DisplayName("description 首尾空白被 trim")
        void description_trimmed() {
            var effect = new PolicyEffect(
                "test", PolicyEffectScope.GLOBAL, 0.1, "  描述文本  "
            );
            assertThat(effect.description()).isEqualTo("描述文本");
        }
    }

    // ========== 效果范围测试 ==========

    @Nested
    @DisplayName("PolicyEffectScope 枚举")
    class EffectScopeTests {

        @Test
        @DisplayName("GLOBAL scope 有正确显示名")
        void globalScope_hasCorrectDisplayName() {
            assertThat(PolicyEffectScope.GLOBAL.displayName()).isEqualTo("全局");
        }

        @Test
        @DisplayName("ECONOMY scope 属于 ECONOMY 分组")
        void economyScope_belongsToEconomyGroup() {
            assertThat(PolicyEffectScope.ECONOMY.group())
                .isEqualTo(PolicyEffectScope.ScopeGroup.ECONOMY);
        }

        @Test
        @DisplayName("MILITARY scope 属于 MILITARY 分组")
        void militaryScope_belongsToMilitaryGroup() {
            assertThat(PolicyEffectScope.MILITARY.group())
                .isEqualTo(PolicyEffectScope.ScopeGroup.MILITARY);
        }

        @Test
        @DisplayName("所有 scope 都有非空描述")
        void allScopes_haveDescriptions() {
            for (PolicyEffectScope scope : PolicyEffectScope.values()) {
                assertThat(scope.description()).isNotBlank();
                assertThat(scope.displayName()).isNotBlank();
            }
        }

        @Test
        @DisplayName("ScopeGroup 枚举值数量正确")
        void scopeGroupEnum_hasCorrectSize() {
            assertThat(PolicyEffectScope.ScopeGroup.values()).hasSize(6);
        }
    }
}
