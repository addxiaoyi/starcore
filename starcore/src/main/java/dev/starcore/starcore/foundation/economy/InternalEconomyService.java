package dev.starcore.starcore.foundation.economy;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.foundation.economy.event.EconomyEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class InternalEconomyService implements EconomyService {
    private static final int SCALE = 2;
    private static final String NAMESPACE = "economy";
    private static final String FILE_NAME = "player-balances.properties";

    private final BalanceStorage storage;
    private final ConcurrentMap<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
    /** flushNow/flushAsync 加锁对象，避免并发写入互覆盖。审计 B-007。 */
    private final Object flushLock = new Object();
    /** 跨账户转账的稳定锁序：每个账户一把独立锁，避免死锁与竞态。审计 B-001/B-002。 */
    private final ConcurrentMap<UUID, Object> transferLocks = new ConcurrentHashMap<>();
    private final Logger logger;
    /** E-114 修复: 记录 UUID 解析/余额解析失败次数，供运维 metric 采集。 */
    private int parseFailureCount = 0;
    private StarCoreEventBus eventBus;
    private volatile boolean eventsEnabled = false;

    public InternalEconomyService(PersistenceService persistenceService) {
        this(new PersistenceBalanceStorage(persistenceService), null);
    }

    public InternalEconomyService(DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this(new DatabaseAwareBalanceStorage(databaseService, persistenceService, logger), logger);
    }

    InternalEconomyService(BalanceStorage storage) {
        this(storage, null);
    }

    InternalEconomyService(BalanceStorage storage, Logger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.logger = logger;
    }

    /**
     * 设置事件总线用于发布经济事件
     */
    public void setEventBus(StarCoreEventBus eventBus) {
        this.eventBus = eventBus;
        this.eventsEnabled = eventBus != null;
    }

    /**
     * 订阅经济事件
     * @param listener 事件监听器
     * @return 取消订阅的 Runnable
     */
    public Runnable subscribeToEvents(Consumer<EconomyEvent> listener) {
        if (eventBus != null) {
            return eventBus.subscribe(EconomyEvent.class, listener);
        }
        return () -> {};
    }

    /**
     * 发布经济事件
     */
    private void publishEvent(EconomyEvent event) {
        if (eventsEnabled && eventBus != null) {
            eventBus.publish(event);
        }
    }

    public void start() {
        Properties properties = storage.load();
        balances.clear();
        for (String key : properties.stringPropertyNames()) {
            try {
                UUID accountId = UUID.fromString(key);
                BigDecimal balance = normalize(new BigDecimal(properties.getProperty(key)));
                // audit B-005/B-006: 保留 0 余额账户，让 hasAccount 在重启后仍为 true；
                // balances contain zero entries as 存在性标记，余额查询时仍返回 0。
                balances.put(accountId, balance);
            } catch (RuntimeException e) {
                parseFailureCount++;
                logWarning("Failed to parse balance for account: " + key + " - " + e.getMessage());
            }
        }
    }

    public void stop() {
        flushNow();
        // E-026 修复: stop 时强制 await 最后一次 flush 完成，避免关服时余额未持久化。
        if (storage instanceof SqlBalanceStorage sbs) {
            sbs.shutdown();
        }
    }

    public BigDecimal balance(UUID accountId) {
        return balances.getOrDefault(accountId, BigDecimal.ZERO).setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public BigDecimal getBalance(UUID playerId) {
        return balance(playerId);
    }

    @Override
    public boolean has(UUID playerId, BigDecimal amount) {
        return balance(playerId).compareTo(amount) >= 0;
    }

    @Override
    public boolean deposit(UUID playerId, BigDecimal amount) {
        return deposit(playerId, amount, false);
    }

    /**
     * 存款 - 支持同步/异步持久化
     * @param playerId 玩家ID
     * @param amount 金额
     * @param syncWrite 是否同步写入磁盘（关键交易使用true）
     * @return 是否成功
     */
    public boolean deposit(UUID playerId, BigDecimal amount, boolean syncWrite) {
        return deposit(playerId, amount, syncWrite, true);
    }

    /**
     * 存款 - 完整参数版本
     * @param playerId 玩家ID
     * @param amount 金额
     * @param syncWrite 是否同步写入磁盘
     * @param publishEvent 是否发布事件
     * @return 是否成功
     */
    public boolean deposit(UUID playerId, BigDecimal amount, boolean syncWrite, boolean publishEvent) {
        try {
            requirePositive(amount);
            // audit B-004: 拒绝金额小于 0.01 的微额（避免 normalize 后被截断为 0 导致凭空吞钱）
            BigDecimal normalized = normalize(amount);
            if (normalized.signum() == 0) {
                logWarning("Deposit amount too small after scale (" + amount + "), rejected");
                return false;
            }
            boolean hadPreviousBalance = balances.containsKey(playerId);
            BigDecimal previousBalance = balances.getOrDefault(playerId, BigDecimal.ZERO);
            // E-116 修复: balances.merge 在 getOrDefault + merge 之间非原子，
            // 多线程同时 deposit 同一玩家时可能丢失更新。
            // 改用 compute 原子读-算-写三步，并捕获 compute 返回值作为 newBalance。
            BigDecimal[] computed = {null};
            balances.compute(playerId, (ignored, current) -> {
                BigDecimal balance = current == null ? BigDecimal.ZERO : current;
                BigDecimal next = balance.add(normalized);
                computed[0] = next;
                return next;
            });
            BigDecimal newBalance = computed[0];
            if (syncWrite) {
                try {
                    flushNow();
                } catch (RuntimeException e) {
                    // audit B-003: 同步落盘失败需回滚内存，避免内存有钱磁盘没钱导致重启后凭空消失
                    logWarning("Sync flush failed during deposit, rolling back: " + e.getMessage());
                    balances.compute(playerId, (ignored, current) -> hadPreviousBalance ? previousBalance : null);
                    return false;
                }
            } else {
                // audit B-003: 异步落盘无法保证持久化成功；关键交易调用方应显式传 syncWrite=true
                flushAsync();
            }

            // 发布存款事件
            if (publishEvent) {
                publishEvent(new EconomyEvent.Deposit(
                    playerId,
                    amount,
                    newBalance,
                    System.currentTimeMillis()
                ));
            }
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            // E-115 修复: 区分验证类异常（参数非法/业务校验失败）与 IO 类异常。
            // 验证失败不打印 SEVERE，只 WARNING；IO 失败打印 SEVERE 并提示持久化可能失败。
            logWarning("Deposit validation failed for " + playerId + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            // E-115 修复: IO/持久化异常（flushNow 失败等），此时内存已更新但磁盘未落盘。
            // 日志 SEVERE 级别，并注明"余额已入内存，重启前请手动同步"，提示运维。
            logWarning("Deposit I/O failed for " + playerId + ", memory updated but not persisted: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean setBalance(UUID playerId, BigDecimal amount) {
        return setBalance(playerId, amount, true);
    }

    /**
     * 设置余额
     * @param playerId 玩家ID
     * @param amount 金额
     * @param publishEvent 是否发布事件
     * @return 是否成功
     */
    public boolean setBalance(UUID playerId, BigDecimal amount, boolean publishEvent) {
        try {
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("amount must be zero or positive");
            }
            BigDecimal previousBalance = balance(playerId);
            BigDecimal normalized = normalize(amount);
            // audit B-005/B-006: 保留 0 余额条目以维持 hasAccount 语义；不再 remove
            balances.put(playerId, normalized);
            flushAsync();

            // 发布余额设置事件
            if (publishEvent) {
                publishEvent(new EconomyEvent.BalanceSet(
                    playerId,
                    previousBalance,
                    normalized,
                    System.currentTimeMillis()
                ));
            }
            return true;
        } catch (Exception e) {
            logWarning("Failed to set balance for " + playerId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean withdraw(UUID accountId, BigDecimal amount) {
        return withdraw(accountId, amount, false);
    }

    /**
     * 取款 - 支持同步/异步持久化
     * @param accountId 账户ID
     * @param amount 金额
     * @param syncWrite 是否同步写入磁盘（关键交易使用true）
     * @return 是否成功
     */
    public boolean withdraw(UUID accountId, BigDecimal amount, boolean syncWrite) {
        return withdraw(accountId, amount, syncWrite, true);
    }

    /**
     * 取款 - 完整参数版本
     * @param accountId 账户ID
     * @param amount 金额
     * @param syncWrite 是否同步写入磁盘
     * @param publishEvent 是否发布事件
     * @return 是否成功
     */
    public boolean withdraw(UUID accountId, BigDecimal amount, boolean syncWrite, boolean publishEvent) {
        requirePositive(amount);
        BigDecimal normalized = normalize(amount);
        AtomicBoolean withdrawn = new AtomicBoolean(false);
        BigDecimal[] balanceAfter = {BigDecimal.ZERO};
        balances.compute(accountId, (ignored, current) -> {
            BigDecimal balance = current == null ? BigDecimal.ZERO : current;
            if (balance.compareTo(normalized) < 0) {
                return balance;
            }
            withdrawn.set(true);
            balanceAfter[0] = balance.subtract(normalized);
            return balanceAfter[0];
        });
        if (withdrawn.get()) {
            if (syncWrite) {
                flushNow();
            } else {
                flushAsync();
            }

            // 发布取款事件
            if (publishEvent) {
                publishEvent(new EconomyEvent.Withdraw(
                    accountId,
                    amount,
                    balanceAfter[0],
                    System.currentTimeMillis()
                ));
            }
        }
        return withdrawn.get();
    }

    public Map<UUID, BigDecimal> snapshot() {
        return Map.copyOf(balances);
    }

    @Override
    public Map<UUID, BigDecimal> getAllBalances() {
        return snapshot();
    }

    @Override
    public boolean hasAccount(UUID playerId) {
        return balances.containsKey(playerId);
    }

    @Override
    public void createAccount(UUID playerId) {
        createAccount(playerId, true);
    }

    /**
     * 创建账户
     * @param playerId 玩家ID
     * @param publishEvent 是否发布事件
     */
    public void createAccount(UUID playerId, boolean publishEvent) {
        if (!hasAccount(playerId)) {
            balances.put(playerId, BigDecimal.ZERO);
            flushAsync();

            // 发布账户创建事件
            if (publishEvent) {
                publishEvent(new EconomyEvent.AccountCreated(
                    playerId,
                    BigDecimal.ZERO,
                    System.currentTimeMillis()
                ));
            }
        }
    }

    @Override
    public boolean transfer(UUID from, UUID to, BigDecimal amount) {
        return transfer(from, to, amount, true);
    }

    /**
     * 转账 - 支持同步/异步持久化
     * @param from 源账户
     * @param to 目标账户
     * @param amount 金额
     * @param syncWrite 是否同步写入磁盘（转账建议使用true防止数据丢失）
     * @return 是否成功
     */
    public boolean transfer(UUID from, UUID to, BigDecimal amount, boolean syncWrite) {
        return transfer(from, to, amount, syncWrite, true);
    }

    /**
     * 转账 - 完整参数版本
     * @param from 源账户
     * @param to 目标账户
     * @param amount 金额
     * @param syncWrite 是否同步写入磁盘
     * @param publishEvent 是否发布事件
     * @return 是否成功
     */
    public boolean transfer(UUID from, UUID to, BigDecimal amount, boolean syncWrite, boolean publishEvent) {
        // audit B-001/B-002: 原子化转账，避免先withdraw后deposit在中间崩溃造成凭空销毁玩家金钱；
        // 改为单次 compute 同时校验并扣减双方余额，保证 check-then-act 竞态不可达。
        // 注意：deposit 失败回滚由 compute 内部一致性保证；外部仅一次 flush。
        requirePositive(amount);
        if (from.equals(to)) {
            return false;
        }

        final BigDecimal normalized = normalize(amount);
        // 防止 from/to 在 compute 内部产生死帧：先锁 from 再锁 to 的稳定顺序。
        // 由于两个账号不同，按 hashCode 排序获取稳定锁序以避免死锁。
        final int fromHash = System.identityHashCode(from);
        final int toHash = System.identityHashCode(to);
        final UUID firstId;
        final UUID secondId;
        if (fromHash <= toHash) {
            firstId = from;
            secondId = to;
        } else {
            firstId = to;
            secondId = from;
        }

        AtomicBoolean success = new AtomicBoolean(false);
        synchronized (lockFor(firstId)) {
            synchronized (lockFor(secondId)) {
                BigDecimal fromBalance = balances.getOrDefault(from, BigDecimal.ZERO);
                BigDecimal toBalance = balances.getOrDefault(to, BigDecimal.ZERO);
                if (fromBalance.compareTo(normalized) < 0) {
                    return false;
                }
                balances.put(from, fromBalance.subtract(normalized));
                balances.put(to, toBalance.add(normalized));
                success.set(true);
            }
        }
        if (!success.get()) {
            return false;
        }

        // 同步落盘，关键交易丢失风险最小化（B-003/B-007：同步 flush 替代异步）
        try {
            flushNow();
        } catch (RuntimeException e) {
            // 落盘失败，回滚内存以保持内存与磁盘一致性（B-003：避免账已记上但没落盘）
            logWarning("Flush failed after transfer, rolling back in-memory: " + e.getMessage());
            synchronized (lockFor(firstId)) {
                synchronized (lockFor(secondId)) {
                    balances.compute(from, (ignored, current) -> current == null ? null : current.add(normalized));
                    balances.compute(to, (ignored, current) -> current == null ? null : current.subtract(normalized));
                }
            }
            return false;
        }

        // 发布转账事件
        if (publishEvent) {
            publishEvent(new EconomyEvent.Transfer(
                from,
                to,
                amount,
                balance(from),
                balance(to),
                System.currentTimeMillis()
            ));
        }
        return true;
    }

    /** 每个账户一把独立锁，避免对 Map.compute 产生竞态；只在 transfer 跨账户原子段使用。 */
    private Object lockFor(UUID id) {
        return transferLocks.computeIfAbsent(id, k -> new Object());
    }

    public void flushNow() {
        // audit B-007: 加同步锁避免并发 flushNow/flushAsync 写入旧快照覆盖新快照导致回档
        synchronized (flushLock) {
            storage.save(toProperties(snapshot()));
        }
    }

    private void flushAsync() {
        // audit B-007: 加同步锁避免与 flushNow 互相覆盖
        synchronized (flushLock) {
            storage.saveAsync(toProperties(snapshot()));
        }
    }

    private static Properties toProperties(Map<UUID, BigDecimal> balances) {
        Properties properties = new Properties();
        balances.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                BigDecimal value = normalize(entry.getValue());
                // audit B-005/B-006: 持久化 0 余额账户，保证 hasAccount 重启后仍可识别账户存在性
                properties.setProperty(entry.getKey().toString(), value.toPlainString());
            });
        return properties;
    }

    private static BigDecimal normalize(BigDecimal amount) {
        // audit B-004: 对 deposit 已在入口拒绝 <0.01 的微额；这里保持 DOWN 截断到 2 位小数，与原精度语义一致
        return amount.setScale(SCALE, RoundingMode.DOWN);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    // ==================== 日志辅助 ====================

    private void logWarning(String message) {
        if (logger != null) {
            logger.warning("[EconomyService] " + message);
        }
    }

    interface BalanceStorage {
        Properties load();

        void save(Properties properties);

        default void saveAsync(Properties properties) {
            save(properties);
        }
    }

    static final class PersistenceBalanceStorage implements BalanceStorage {
        private final PersistenceService persistenceService;

        PersistenceBalanceStorage(PersistenceService persistenceService) {
            this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        }

        @Override
        public Properties load() {
            return persistenceService.loadProperties(NAMESPACE, FILE_NAME);
        }

        @Override
        public void save(Properties properties) {
            persistenceService.saveProperties(NAMESPACE, FILE_NAME, properties);
        }

        @Override
        public void saveAsync(Properties properties) {
            persistenceService.savePropertiesAsync(NAMESPACE, FILE_NAME, properties);
        }
    }
}
