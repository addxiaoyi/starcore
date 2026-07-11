package dev.starcore.starcore.foundation.economy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 经济服务测试
 * 确保经济操作的安全性和正确性
 */
class EconomyServiceTest {

    private static final UUID PLAYER_1 = UUID.randomUUID();
    private static final UUID PLAYER_2 = UUID.randomUUID();
    private TestableEconomyService economyService;

    @BeforeEach
    void setUp() {
        economyService = new TestableEconomyService();
    }

    @Nested
    @DisplayName("账户操作测试")
    class AccountTests {

        @Test
        @DisplayName("新账户初始余额应为0")
        void newAccountShouldHaveZeroBalance() {
            economyService.createAccount(PLAYER_1);
            assertEquals(BigDecimal.ZERO, economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("不存在的账户hasAccount应返回false")
        void nonExistentAccountShouldReturnFalse() {
            assertFalse(economyService.hasAccount(PLAYER_1));
        }

        @Test
        @DisplayName("创建后hasAccount应返回true")
        void afterCreateAccountShouldReturnTrue() {
            economyService.createAccount(PLAYER_1);
            assertTrue(economyService.hasAccount(PLAYER_1));
        }

        @Test
        @DisplayName("重复创建账户应安全处理")
        void duplicateAccountCreationShouldBeSafe() {
            economyService.createAccount(PLAYER_1);
            economyService.createAccount(PLAYER_1); // 不应抛出异常
            assertTrue(economyService.hasAccount(PLAYER_1));
        }
    }

    @Nested
    @DisplayName("余额操作测试")
    class BalanceTests {

        @BeforeEach
        void createAccounts() {
            economyService.createAccount(PLAYER_1);
            economyService.createAccount(PLAYER_2);
        }

        @Test
        @DisplayName("存款后余额应正确增加")
        void depositShouldIncreaseBalance() {
            economyService.deposit(PLAYER_1, new BigDecimal("100.00"));
            assertEquals(new BigDecimal("100.00"), economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("多次存款应累加")
        void multipleDepositsShouldAccumulate() {
            economyService.deposit(PLAYER_1, new BigDecimal("50.00"));
            economyService.deposit(PLAYER_1, new BigDecimal("30.00"));
            assertEquals(new BigDecimal("80.00"), economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("取款后余额应正确减少")
        void withdrawShouldDecreaseBalance() {
            economyService.deposit(PLAYER_1, new BigDecimal("100.00"));
            economyService.withdraw(PLAYER_1, new BigDecimal("30.00"));
            assertEquals(new BigDecimal("70.00"), economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("余额不足时取款应失败")
        void withdrawInsufficientBalanceShouldFail() {
            economyService.deposit(PLAYER_1, new BigDecimal("50.00"));
            boolean result = economyService.withdraw(PLAYER_1, new BigDecimal("100.00"));
            assertFalse(result);
            assertEquals(new BigDecimal("50.00"), economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("余额不足时has应返回false")
        void hasShouldReturnFalseWhenInsufficientBalance() {
            economyService.deposit(PLAYER_1, new BigDecimal("50.00"));
            assertFalse(economyService.has(PLAYER_1, new BigDecimal("100.00")));
        }

        @Test
        @DisplayName("余额充足时has应返回true")
        void hasShouldReturnTrueWhenSufficientBalance() {
            economyService.deposit(PLAYER_1, new BigDecimal("100.00"));
            assertTrue(economyService.has(PLAYER_1, new BigDecimal("100.00")));
        }

        @Test
        @DisplayName("设置余额应正确覆盖")
        void setBalanceShouldOverwrite() {
            economyService.deposit(PLAYER_1, new BigDecimal("100.00"));
            economyService.setBalance(PLAYER_1, new BigDecimal("500.00"));
            assertEquals(new BigDecimal("500.00"), economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("不能设置负数余额")
        void cannotSetNegativeBalance() {
            boolean result = economyService.setBalance(PLAYER_1, new BigDecimal("-100.00"));
            assertFalse(result);
            assertEquals(BigDecimal.ZERO, economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("不能存款负数")
        void cannotDepositNegative() {
            boolean result = economyService.deposit(PLAYER_1, new BigDecimal("-100.00"));
            assertFalse(result);
        }

        @Test
        @DisplayName("不能取款负数")
        void cannotWithdrawNegative() {
            boolean result = economyService.withdraw(PLAYER_1, new BigDecimal("-100.00"));
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("转账测试")
    class TransferTests {

        @BeforeEach
        void setupBalances() {
            economyService.createAccount(PLAYER_1);
            economyService.createAccount(PLAYER_2);
            economyService.deposit(PLAYER_1, new BigDecimal("1000.00"));
        }

        @Test
        @DisplayName("转账应正确转移资金")
        void transferShouldMoveMoney() {
            boolean result = economyService.transfer(PLAYER_1, PLAYER_2, new BigDecimal("300.00"));
            assertTrue(result);
            assertEquals(new BigDecimal("700.00"), economyService.getBalance(PLAYER_1));
            assertEquals(new BigDecimal("300.00"), economyService.getBalance(PLAYER_2));
        }

        @Test
        @DisplayName("余额不足转账应失败")
        void transferInsufficientShouldFail() {
            boolean result = economyService.transfer(PLAYER_1, PLAYER_2, new BigDecimal("2000.00"));
            assertFalse(result);
            assertEquals(new BigDecimal("1000.00"), economyService.getBalance(PLAYER_1));
            assertEquals(BigDecimal.ZERO, economyService.getBalance(PLAYER_2));
        }

        @Test
        @DisplayName("不能给自己转账")
        void cannotTransferToSelf() {
            boolean result = economyService.transfer(PLAYER_1, PLAYER_1, new BigDecimal("100.00"));
            assertFalse(result);
        }

        @Test
        @DisplayName("转账负数应失败")
        void transferNegativeShouldFail() {
            boolean result = economyService.transfer(PLAYER_1, PLAYER_2, new BigDecimal("-100.00"));
            assertFalse(result);
        }

        @Test
        @DisplayName("转账0应失败")
        void transferZeroShouldFail() {
            boolean result = economyService.transfer(PLAYER_1, PLAYER_2, BigDecimal.ZERO);
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("精度测试")
    class PrecisionTests {

        @Test
        @DisplayName("应支持小数操作")
        void shouldSupportDecimalOperations() {
            economyService.createAccount(PLAYER_1);
            economyService.deposit(PLAYER_1, new BigDecimal("100.99"));
            economyService.deposit(PLAYER_1, new BigDecimal("0.01"));
            assertEquals(new BigDecimal("101.00"), economyService.getBalance(PLAYER_1));
        }

        @Test
        @DisplayName("大额操作应正确")
        void shouldHandleLargeAmounts() {
            economyService.createAccount(PLAYER_1);
            economyService.deposit(PLAYER_1, new BigDecimal("999999999.99"));
            assertEquals(new BigDecimal("999999999.99"), economyService.getBalance(PLAYER_1));
        }
    }

    @Nested
    @DisplayName("安全性测试")
    class SecurityTests {

        @Test
        @DisplayName("操作不存在账户应返回失败")
        void operationsOnNonExistentAccountShouldFail() {
            assertFalse(economyService.withdraw(PLAYER_1, new BigDecimal("100.00")));
            assertFalse(economyService.deposit(PLAYER_1, new BigDecimal("100.00")));
        }

        @Test
        @DisplayName("getAllBalances不应返回null")
        void getAllBalancesShouldNotReturnNull() {
            economyService.createAccount(PLAYER_1);
            Map<UUID, BigDecimal> balances = economyService.getAllBalances();
            assertNotNull(balances);
        }

        @Test
        @DisplayName("转账后双方余额总和应不变")
        void totalBalanceShouldRemainSameAfterTransfer() {
            economyService.createAccount(PLAYER_1);
            economyService.createAccount(PLAYER_2);
            economyService.deposit(PLAYER_1, new BigDecimal("1000.00"));
            economyService.deposit(PLAYER_2, new BigDecimal("500.00"));

            BigDecimal totalBefore = economyService.getBalance(PLAYER_1).add(economyService.getBalance(PLAYER_2));
            economyService.transfer(PLAYER_1, PLAYER_2, new BigDecimal("200.00"));
            BigDecimal totalAfter = economyService.getBalance(PLAYER_1).add(economyService.getBalance(PLAYER_2));

            assertEquals(totalBefore, totalAfter);
        }
    }

    /**
     * 可测试的经济服务实现
     */
    static class TestableEconomyService implements EconomyService {
        private final java.util.Map<UUID, BigDecimal> balances = new java.util.HashMap<>();

        @Override
        public BigDecimal getBalance(UUID playerId) {
            return balances.getOrDefault(playerId, BigDecimal.ZERO);
        }

        @Override
        public boolean has(UUID playerId, BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
            return getBalance(playerId).compareTo(amount) >= 0;
        }

        @Override
        public boolean deposit(UUID playerId, BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return false;
            if (!balances.containsKey(playerId)) return false;
            balances.put(playerId, getBalance(playerId).add(amount));
            return true;
        }

        @Override
        public boolean withdraw(UUID playerId, BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return false;
            if (!has(playerId, amount)) return false;
            balances.put(playerId, getBalance(playerId).subtract(amount));
            return true;
        }

        @Override
        public boolean setBalance(UUID playerId, BigDecimal amount) {
            if (amount.compareTo(BigDecimal.ZERO) < 0) return false;
            if (!balances.containsKey(playerId)) return false;
            balances.put(playerId, amount);
            return true;
        }

        @Override
        public boolean transfer(UUID from, UUID to, BigDecimal amount) {
            if (from.equals(to)) return false;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return false;
            if (!has(from, amount)) return false;
            if (!balances.containsKey(to)) return false;
            balances.put(from, getBalance(from).subtract(amount));
            balances.put(to, getBalance(to).add(amount));
            return true;
        }

        @Override
        public Map<UUID, BigDecimal> getAllBalances() {
            return Map.copyOf(balances);
        }

        @Override
        public boolean hasAccount(UUID playerId) {
            return balances.containsKey(playerId);
        }

        @Override
        public void createAccount(UUID playerId) {
            balances.putIfAbsent(playerId, BigDecimal.ZERO);
        }
    }
}
