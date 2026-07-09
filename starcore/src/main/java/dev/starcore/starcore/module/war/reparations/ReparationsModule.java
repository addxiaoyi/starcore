package dev.starcore.starcore.module.war.reparations;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.reparations.command.ReparationsCommand;
import dev.starcore.starcore.module.war.reparations.listener.ReparationsEventListener;
import dev.starcore.starcore.war.WarReparation;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 战争赔款模块
 * 管理战争赔款的创建、支付、查询等操作
 */
public final class ReparationsModule implements StarCoreModule, ReparationsService {
    private static final String PERSISTENCE_NAMESPACE = "reparations";
    private static final String STATE_FILE = "reparations.dat";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "reparations",
        "战争赔款",
        ModuleLayer.MODULE,
        List.of("nation", "treasury", "war"),
        List.of(ReparationsService.class),
        "Manages war reparations between nations after conflicts."
    );

    private org.bukkit.plugin.java.JavaPlugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private MessageService messages;
    private DatabaseService databaseService;
    private PersistenceService persistenceService;
    private StarCoreEventBus eventBus;

    // 内存中的赔款数据
    private final ConcurrentHashMap<UUID, WarReparation> reparations = new ConcurrentHashMap<>();
    // 国家的赔款索引（作为支付方）
    private final ConcurrentHashMap<UUID, List<UUID>> payerIndex = new ConcurrentHashMap<>();
    // 国家的赔款索引（作为接收方）
    private final ConcurrentHashMap<UUID, List<UUID>> receiverIndex = new ConcurrentHashMap<>();

    private ReparationsConfig config;
    private ReparationsEventListener eventListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());

        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().require(TreasuryService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.databaseService = context.databaseService();
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);
        this.eventBus = context.eventBus();

        // 加载配置
        this.config = ReparationsConfig.fromConfig(context.plugin().getConfig().getConfigurationSection("reparations"));

        // 加载持久化数据
        loadReparations();

        // 注册命令
        registerCommands(context);

        // 注册事件监听器
        registerEventListener(context);

        // 启动定时任务
        startPeriodicTasks();

        plugin.getLogger().info("Reparations module enabled: " + reparations.size() + " reparations loaded");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有赔款数据
        saveAllReparations();
        plugin.getLogger().info("Reparations module disabled");
    }

    // ==================== ReparationsService 实现 ====================

    @Override
    public WarReparation createReparation(UUID treatyId, NationId payerId, NationId receiverId,
                                         BigDecimal totalAmount, int installments) {
        Objects.requireNonNull(treatyId, "treatyId");
        Objects.requireNonNull(payerId, "payerId");
        Objects.requireNonNull(receiverId, "receiverId");
        Objects.requireNonNull(totalAmount, "totalAmount");

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be positive");
        }

        if (installments < 1) {
            installments = 1;
        }

        // 检查最低赔款金额
        if (totalAmount.compareTo(config.minReparationAmount()) < 0) {
            throw new IllegalArgumentException(
                "Reparation amount must be at least " + config.minReparationAmount());
        }

        // 检查最高赔款金额
        if (totalAmount.compareTo(config.maxReparationAmount()) > 0) {
            throw new IllegalArgumentException(
                "Reparation amount cannot exceed " + config.maxReparationAmount());
        }

        // 创建赔款记录
        WarReparation reparation = new WarReparation(
            UUID.randomUUID(),
            treatyId,
            payerId.value(),
            receiverId.value(),
            totalAmount,
            installments,
            Instant.now()
        );

        // 存储
        reparations.put(reparation.id(), reparation);
        updatePayerIndex(payerId.value(), reparation.id());
        updateReceiverIndex(receiverId.value(), reparation.id());

        // 持久化
        persistReparation(reparation);

        // 发布事件
        publishEvent(new dev.starcore.starcore.module.war.reparations.event.ReparationCreatedEvent(reparation));

        plugin.getLogger().info(String.format(
            "Reparation created: ID=%s, Payer=%s, Receiver=%s, Amount=%s, Installments=%d",
            reparation.id(), payerId, receiverId, totalAmount, installments));

        return reparation;
    }

    @Override
    public boolean payReparation(UUID reparationId, BigDecimal amount) {
        WarReparation reparation = reparations.get(reparationId);
        if (reparation == null) {
            plugin.getLogger().warning("Reparation not found: " + reparationId);
            return false;
        }

        if (reparation.status() != WarReparation.ReparationStatus.ACTIVE) {
            plugin.getLogger().warning("Reparation is not active: " + reparationId);
            return false;
        }

        // 检查金额
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal remaining = reparation.remainingAmount();
        if (amount.compareTo(remaining) > 0) {
            amount = remaining; // 自动调整为剩余金额
        }

        // 从国库扣除
        NationId payerId = NationId.of(reparation.payerId());
        NationId receiverId = NationId.of(reparation.receiverId());

        if (!treasuryService.withdraw(payerId, amount)) {
            plugin.getLogger().warning("Insufficient treasury balance for reparation payment");
            return false;
        }

        // 存入接收方国库
        treasuryService.deposit(receiverId, amount);

        // 记录支付
        reparation.recordPayment(amount);

        // 持久化更新
        persistReparation(reparation);

        // 发布事件
        publishEvent(new dev.starcore.starcore.module.war.reparations.event.ReparationPaymentEvent(
            reparation, amount, reparation.paidAmount(), reparation.remainingAmount()));

        // 检查是否完成
        if (reparation.isCompleted()) {
            publishEvent(new dev.starcore.starcore.module.war.reparations.event.ReparationCompletedEvent(reparation));
        }

        plugin.getLogger().info(String.format(
            "Reparation paid: ID=%s, Amount=%s, Progress=%.1f%%",
            reparationId, amount, reparation.progressPercentage()));

        return true;
    }

    @Override
    public boolean payNextInstallment(UUID reparationId) {
        WarReparation reparation = reparations.get(reparationId);
        if (reparation == null) {
            return false;
        }

        if (reparation.status() != WarReparation.ReparationStatus.ACTIVE) {
            return false;
        }

        BigDecimal installmentAmount = reparation.installmentAmount();
        return payReparation(reparationId, installmentAmount);
    }

    @Override
    public Optional<WarReparation> getReparation(UUID reparationId) {
        return Optional.ofNullable(reparations.get(reparationId));
    }

    @Override
    public List<WarReparation> getReparationsForTreaty(UUID treatyId) {
        return reparations.values().stream()
            .filter(r -> r.treatyId().equals(treatyId))
            .collect(Collectors.toList());
    }

    @Override
    public List<WarReparation> getReparationsAsPayer(NationId nationId) {
        List<UUID> reparationIds = payerIndex.getOrDefault(nationId.value(), List.of());
        return reparationIds.stream()
            .map(reparations::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public List<WarReparation> getReparationsAsReceiver(NationId nationId) {
        List<UUID> reparationIds = receiverIndex.getOrDefault(nationId.value(), List.of());
        return reparationIds.stream()
            .map(reparations::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<WarReparation> getActiveReparations() {
        return reparations.values().stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<WarReparation> getAllReparations() {
        return List.copyOf(reparations.values());
    }

    @Override
    public boolean forgiveReparation(UUID reparationId) {
        WarReparation reparation = reparations.get(reparationId);
        if (reparation == null) {
            return false;
        }

        reparation.forgive();
        persistReparation(reparation);

        // 发布事件
        publishEvent(new dev.starcore.starcore.module.war.reparations.event.ReparationForgivenEvent(reparation));

        plugin.getLogger().info("Reparation forgiven: " + reparationId);
        return true;
    }

    @Override
    public boolean markDefault(UUID reparationId) {
        WarReparation reparation = reparations.get(reparationId);
        if (reparation == null) {
            return false;
        }

        reparation.markDefault();
        persistReparation(reparation);

        // 发布事件
        publishEvent(new dev.starcore.starcore.module.war.reparations.event.ReparationDefaultedEvent(reparation));

        plugin.getLogger().warning("Reparation defaulted: " + reparationId);
        return true;
    }

    @Override
    public boolean hasActiveReparation(NationId nationId) {
        return reparations.values().stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .anyMatch(r -> r.payerId().equals(nationId.value()));
    }

    @Override
    public boolean hasOverdueReparation(NationId nationId) {
        Instant now = Instant.now();
        return reparations.values().stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .filter(r -> r.payerId().equals(nationId.value()))
            .anyMatch(r -> r.isOverdue(now));
    }

    @Override
    public BigDecimal calculateTotalReparationDebt(NationId nationId) {
        return reparations.values().stream()
            .filter(r -> r.payerId().equals(nationId.value()))
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .map(WarReparation::remainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal calculateTotalReparationPaid(NationId nationId) {
        return reparations.values().stream()
            .filter(r -> r.payerId().equals(nationId.value()))
            .map(WarReparation::paidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void processOverdueReparations() {
        Instant now = Instant.now();
        int processedCount = 0;

        for (WarReparation reparation : reparations.values()) {
            if (reparation.status() != WarReparation.ReparationStatus.ACTIVE) {
                continue;
            }

            if (reparation.isOverdue(now)) {
                markDefault(reparation.id());
                processedCount++;
            }
        }

        if (processedCount > 0) {
            plugin.getLogger().info("Processed " + processedCount + " overdue reparations");
        }
    }

    @Override
    public String summary() {
        long active = reparations.values().stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.ACTIVE)
            .count();
        long completed = reparations.values().stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.COMPLETED)
            .count();
        long defaulted = reparations.values().stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.DEFAULTED)
            .count();
        long forgiven = reparations.values().stream()
            .filter(r -> r.status() == WarReparation.ReparationStatus.FORGIVEN)
            .count();

        return String.format("Reparations: %d active, %d completed, %d defaulted, %d forgiven (Total: %d)",
            active, completed, defaulted, forgiven, reparations.size());
    }

    // ==================== 内部方法 ====================

    private void registerCommands(StarCoreContext context) {
        ReparationsCommand command = new ReparationsCommand(this, nationService, treasuryService, messages);
        var cmd = plugin.getCommand("reparations");
        if (cmd != null) {
            cmd.setExecutor(command);
            if (command instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        } else {
            // 如果命令不存在，尝试通过命令别名注册
            plugin.getLogger().warning("Command 'reparations' not found in plugin.yml");
        }
    }

    private void registerEventListener(StarCoreContext context) {
        eventListener = new ReparationsEventListener(this, nationService, messages);
        plugin.getServer().getPluginManager().registerEvents(eventListener, plugin);
    }

    private void startPeriodicTasks() {
        // 每天检查一次逾期赔款
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            processOverdueReparations();
        }, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);

        // 每小时保存一次数据
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAllReparations();
        }, 20L * 60 * 60, 20L * 60 * 60);
    }

    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    // ==================== 索引管理 ====================

    private void updatePayerIndex(UUID payerId, UUID reparationId) {
        payerIndex.computeIfAbsent(payerId, k -> new java.util.ArrayList<>()).add(reparationId);
    }

    private void updateReceiverIndex(UUID receiverId, UUID reparationId) {
        receiverIndex.computeIfAbsent(receiverId, k -> new java.util.ArrayList<>()).add(reparationId);
    }

    // ==================== 持久化 ====================

    private void loadReparations() {
        if (persistenceService == null) {
            plugin.getLogger().warning("PersistenceService not available, reparations will not persist");
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                String json = props.getProperty(key);
                try {
                    WarReparation reparation = ReparationsStateCodec.decode(json);
                    reparations.put(reparation.id(), reparation);
                    updatePayerIndex(reparation.payerId(), reparation.id());
                    updateReceiverIndex(reparation.receiverId(), reparation.id());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load reparation " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + reparations.size() + " reparations from storage");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load reparations: " + e.getMessage());
        }
    }

    private void saveAllReparations() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new java.util.Properties();
            for (WarReparation reparation : reparations.values()) {
                String key = reparation.id().toString();
                String json = ReparationsStateCodec.encode(reparation);
                props.setProperty(key, json);
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save reparations: " + e.getMessage());
        }
    }

    private void persistReparation(WarReparation reparation) {
        if (persistenceService == null) {
            return;
        }

        try {
            String key = reparation.id().toString();
            String json = ReparationsStateCodec.encode(reparation);

            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, STATE_FILE);
            props.setProperty(key, json);
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist reparation " + reparation.id() + ": " + e.getMessage());
        }
    }

    // ==================== 配置 ====================

    /**
     * 赔款配置
     */
    public record ReparationsConfig(
        BigDecimal minReparationAmount,
        BigDecimal maxReparationAmount,
        int defaultInstallments,
        int maxInstallments,
        int overdueDays,
        boolean autoChargeEnabled,
        BigDecimal autoChargeAmount
    ) {
        public static ReparationsConfig defaults() {
            return new ReparationsConfig(
                new BigDecimal("1000"),
                new BigDecimal("1000000"),
                12,
                60,
                30,
                false,
                new BigDecimal("10000")
            );
        }

        public static ReparationsConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new ReparationsConfig(
                new BigDecimal(section.getString("min-reparation-amount", "1000")),
                new BigDecimal(section.getString("max-reparation-amount", "1000000")),
                section.getInt("default-installments", 12),
                section.getInt("max-installments", 60),
                section.getInt("overdue-days", 30),
                section.getBoolean("auto-charge-enabled", false),
                new BigDecimal(section.getString("auto-charge-amount", "10000"))
            );
        }
    }
}
