package dev.starcore.starcore.module.policy;

import dev.starcore.starcore.module.nation.model.ClaimPriceBreakdown;
import dev.starcore.starcore.module.nation.model.NationKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * DomainModelTest 跨模块模型测试
 *
 * 验证 Nation 模块中与 Policy 模块交互的模型类。
 * 这些类定义在 nation 模块但被 policy 模块引用。
 */
@DisplayName("DomainModelTest - 跨模块模型测试")
class DomainModelTest {

    // ========== ClaimPriceBreakdown 测试 ==========

    @Nested
    @DisplayName("ClaimPriceBreakdown - 领地价格明细")
    class ClaimPriceBreakdownTests {

        @Test
        @DisplayName("有效参数创建实例")
        void validInput_createsInstance() {
            var breakdown = new ClaimPriceBreakdown(
                new BigDecimal("100"),
                new BigDecimal("500"),
                5,
                List.of()
            );

            assertThat(breakdown.baseChunkPrice()).isEqualByComparingTo("100");
            assertThat(breakdown.totalPrice()).isEqualByComparingTo("500");
            assertThat(breakdown.chunkCount()).isEqualTo(5);
            assertThat(breakdown.chunks()).isEmpty();
        }

        @Test
        @DisplayName("null baseChunkPrice 被转换为零")
        void nullBaseChunkPrice_convertedToZero() {
            var breakdown = new ClaimPriceBreakdown(
                null,
                new BigDecimal("500"),
                5,
                List.of()
            );

            assertThat(breakdown.baseChunkPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("null totalPrice 被转换为零")
        void nullTotalPrice_convertedToZero() {
            var breakdown = new ClaimPriceBreakdown(
                new BigDecimal("100"),
                null,
                5,
                List.of()
            );

            assertThat(breakdown.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("null chunks 被转换为空列表")
        void nullChunks_convertedToEmptyList() {
            var breakdown = new ClaimPriceBreakdown(
                new BigDecimal("100"),
                new BigDecimal("500"),
                5,
                null
            );

            assertThat(breakdown.chunks()).isEmpty();
        }

        @Test
        @DisplayName("empty 工厂方法创建空实例")
        void emptyFactory_createsEmptyInstance() {
            var breakdown = ClaimPriceBreakdown.empty(
                new BigDecimal("100"),
                new BigDecimal("500"),
                5
            );

            assertThat(breakdown.baseChunkPrice()).isEqualByComparingTo("100");
            assertThat(breakdown.totalPrice()).isEqualByComparingTo("500");
            assertThat(breakdown.chunkCount()).isEqualTo(5);
            assertThat(breakdown.chunks()).isEmpty();
        }

        @Test
        @DisplayName("chunks 列表不可变")
        void chunksList_isUnmodifiable() {
            var breakdown = new ClaimPriceBreakdown(
                new BigDecimal("100"),
                new BigDecimal("500"),
                5,
                List.of()
            );

            assertThatThrownBy(() ->
                breakdown.chunks().add(null)
            ).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ========== NationKind 测试 ==========

    @Nested
    @DisplayName("NationKind - 国家类型枚举")
    class NationKindTests {

        @Test
        @DisplayName("NATION 枚举值存在")
        void nationEnumValue_exists() {
            assertThat(NationKind.NATION).isNotNull();
            assertThat(NationKind.NATION.name()).isEqualTo("NATION");
        }

        @Test
        @DisplayName("CITY_STATE 枚举值存在")
        void cityStateEnumValue_exists() {
            assertThat(NationKind.CITY_STATE).isNotNull();
            assertThat(NationKind.CITY_STATE.name()).isEqualTo("CITY_STATE");
        }

        @Test
        @DisplayName("NATION 有正确的中文显示名")
        void nation_hasCorrectDisplayName() {
            assertThat(NationKind.NATION.displayName()).isEqualTo("国家");
        }

        @Test
        @DisplayName("CITY_STATE 有正确的中文显示名")
        void cityState_hasCorrectDisplayName() {
            assertThat(NationKind.CITY_STATE.displayName()).isEqualTo("城邦");
        }

        @Test
        @DisplayName("枚举值数量正确")
        void enumValuesCount_isCorrect() {
            assertThat(NationKind.values()).hasSize(2);
        }

        @Test
        @DisplayName("valueOf 方法工作正常")
        void valueOf_worksCorrectly() {
            assertThat(NationKind.valueOf("NATION")).isEqualTo(NationKind.NATION);
            assertThat(NationKind.valueOf("CITY_STATE")).isEqualTo(NationKind.CITY_STATE);
        }

        @Test
        @DisplayName("枚举值的 displayName 不为空")
        void allDisplayNames_areNonEmpty() {
            for (NationKind kind : NationKind.values()) {
                assertThat(kind.displayName()).isNotBlank();
            }
        }
    }
}
