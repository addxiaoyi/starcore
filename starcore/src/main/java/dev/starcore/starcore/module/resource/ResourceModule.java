package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.resource.command.ResourceCommand;
import dev.starcore.starcore.module.resource.gui.ResourceMenu;
import dev.starcore.starcore.module.resource.gui.ResourceMenuListener;
import dev.starcore.starcore.module.resource.gui.ResourceModuleGui;
import dev.starcore.starcore.module.resource.listener.ResourceListener;
import org.bukkit.plugin.java.JavaPlugin;

import dev.starcore.starcore.module.resource.model.ResourceType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ResourceModule implements StarCoreModule, ResourceService {
    private static final String FILE_NAME = "resources.properties";

    // 资源类型定义 - 与 ResourceType 枚举对齐
    // 使用小写下划线格式以保持向后兼容
    private static final Set<String> RESOURCE_TYPES = Set.of(
        ResourceType.MINERAL.name().toLowerCase(),      // mineral - 矿产
        ResourceType.AGRICULTURAL.name().toLowerCase(), // agricultural - 农业
        ResourceType.ENERGY.name().toLowerCase(),       // energy - 能源
        ResourceType.LUXURY.name().toLowerCase(),       // luxury - 奢侈品
        ResourceType.INDUSTRIAL.name().toLowerCase(),   // industrial - 工业原料
        ResourceType.CHEMICAL.name().toLowerCase(),     // chemical - 化工产品
        ResourceType.STRATEGIC.name().toLowerCase()     // strategic - 战略物资
    );

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "resource",
        "资源核心",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(ResourceService.class),
        "Owns national strategic resource stockpiles for industry, war, and technology."
    );

    // 核心储量数据
    private final ConcurrentMap<NationId, ConcurrentMap<String, Long>> stockpiles = new ConcurrentHashMap<>();
    private ResourceStateStorage stateStorage;
    private PolicyService policyService;
    private NationService nationService;
    private OnlinePlayerDirectory onlinePlayerDirectory;

    // 子服务实例
    private SimpleResourcePriceService priceService;
    private SimpleResourceMonopolyService monopolyService;
    private SimpleResourceTradeService tradeService;
    private SimpleResourceReserveService reserveService;
    private SimpleProcessingService processingService;
    // 市场交易服务
    private SimpleMarketOrderBookService marketOrderBookService;
    private SimpleTradeHistoryService tradeHistoryService;
    private SimplePlayerTradeService playerTradeService;
    private SimpleTradeTaxService tradeTaxService;

    // GUI 和监听器
    private ResourceMenu resourceMenu;
    private ResourceMenuListener menuListener;
    private ResourceModuleGui moduleGui;
    private ResourceListener resourceListener;

    // 动画服务
    private GuiAnimationManager animationManager;
    private SoundFeedbackManager soundManager;

    // 语言消息服务
    private MessageService messages;

    // 上下文
    private StarCoreContext context;
    private StarCoreScheduler scheduler;
    private StarCoreEventBus eventBus;
    private JavaPlugin plugin;
    private ServiceRegistry serviceRegistry;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        this.plugin = context.plugin();
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();
        this.serviceRegistry = context.serviceRegistry();

        // 初始化动画服务
        this.animationManager = new GuiAnimationManager(plugin);
        this.soundManager = new SoundFeedbackManager(plugin);

        // 初始化消息服务
        this.messages = context.serviceRegistry().require(MessageService.class);

        context.persistenceService().ensureNamespace(metadata().id());
        this.stateStorage = new DatabaseAwareResourceStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );

        // 获取依赖服务
        this.policyService = context.serviceRegistry().find(PolicyService.class).orElse(null);
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        this.onlinePlayerDirectory = context.serviceRegistry().find(OnlinePlayerDirectory.class).orElse(null);

        // 初始化子服务
        initializeServices();

        // 加载状态
        loadState();

        // 注册命令
        registerCommands();

        // 注册监听器
        registerListeners();

        // 启动定时任务
        startScheduledTasks();

        // 注册到服务注册表
        registerToServiceRegistry();

        plugin.getLogger().info("ResourceModule enabled successfully");
    }

    /**
     * 初始化所有子服务
     */
    private void initializeServices() {
        // 税收服务（最先初始化，其他服务依赖它）
        this.tradeTaxService = new SimpleTradeTaxService();

        // 价格服务
        this.priceService = new SimpleResourcePriceService();
        this.priceService.initializeDefaultPrices();
        this.priceService.setScheduler(scheduler);

        // 垄断服务
        this.monopolyService = new SimpleResourceMonopolyService(this);
        for (String resourceType : RESOURCE_TYPES) {
            this.monopolyService.registerResourceType(resourceType);
        }
        this.monopolyService.setScheduler(scheduler);

        // 贸易服务
        this.tradeService = new SimpleResourceTradeService(this);
        this.tradeService.setEventBus(eventBus);
        this.tradeService.setScheduler(scheduler);

        // 储备服务
        this.reserveService = new SimpleResourceReserveService(this);

        // 加工服务
        this.processingService = new SimpleProcessingService(this, this.priceService);
        this.processingService.initializeDefaultRecipes();
        this.processingService.setScheduler(scheduler);

        // 市场订单簿服务
        this.marketOrderBookService = new SimpleMarketOrderBookService(
            this,
            serviceRegistry.find(dev.starcore.starcore.foundation.economy.EconomyService.class).orElse(null),
            this.tradeTaxService
        );
        this.marketOrderBookService.setScheduler(scheduler);
        this.marketOrderBookService.setEventBus(eventBus);
        this.marketOrderBookService.setPriceService(this.priceService);

        // 交易历史服务
        this.tradeHistoryService = new SimpleTradeHistoryService(
            serviceRegistry.find(dev.starcore.starcore.core.database.DatabaseService.class).orElse(null),
            scheduler
        );

        // 玩家交易服务
        this.playerTradeService = new SimplePlayerTradeService(
            this,
            serviceRegistry.find(dev.starcore.starcore.foundation.economy.EconomyService.class).orElse(null),
            this.tradeTaxService,
            this.marketOrderBookService,
            plugin
        );
        this.playerTradeService.setScheduler(scheduler);
        this.playerTradeService.setEventBus(eventBus);

        // GUI
        this.resourceMenu = new ResourceMenu(
            this, priceService, reserveService, marketOrderBookService,
            tradeHistoryService, playerTradeService,
            animationManager, soundManager, messages
        );

        // GUI 整合类
        this.moduleGui = new ResourceModuleGui(
            this, this, priceService, reserveService,
            marketOrderBookService, tradeHistoryService, playerTradeService,
            nationService, animationManager, soundManager,
            messages
        );

        // 监听器
        this.resourceListener = new ResourceListener(
            this, priceService, tradeService, reserveService,
            processingService, resourceMenu, eventBus, nationService
        );

        // GUI 监听器
        this.menuListener = new ResourceMenuListener(
            this, resourceMenu, this, priceService, reserveService,
            marketOrderBookService, tradeHistoryService, playerTradeService,
            nationService, soundManager, messages
        );
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        if (plugin == null) {
            return;
        }

        var command = plugin.getCommand("resource");
        if (command != null) {
            var resourceCommand = new ResourceCommand(
                this,
                priceService,
                tradeService,
                reserveService,
                processingService,
                nationService,
                onlinePlayerDirectory
            );
            // 设置市场交易服务
            resourceCommand.setMarketOrderBookService(marketOrderBookService);
            resourceCommand.setTradeHistoryService(tradeHistoryService);
            resourceCommand.setPlayerTradeService(playerTradeService);
            resourceCommand.setTradeTaxService(tradeTaxService);
            command.setExecutor(resourceCommand);
            command.setTabCompleter(resourceCommand);
            plugin.getLogger().info("Resource command registered.");
        } else {
            plugin.getLogger().warning("Command 'resource' not found in plugin.yml");
        }

        // 注册打开 GUI 的命令
        var guiCommand = plugin.getCommand("resourcegui");
        if (guiCommand != null) {
            guiCommand.setExecutor((sender, cmd, label, args) -> {
                if (sender instanceof org.bukkit.entity.Player player) {
                    resourceMenu.openMainMenu(player);
                } else {
                    sender.sendMessage("§c只有玩家可以使用此命令");
                }
                return true;
            });
            plugin.getLogger().info("ResourceGUI command registered.");
        }

        // 注册交易命令
        var tradeCommand = plugin.getCommand("trade");
        if (tradeCommand != null) {
            var tradeCmd = new dev.starcore.starcore.module.resource.command.TradeCommand();
            tradeCmd.setPlayerTradeService(playerTradeService);
            tradeCmd.setNationService(nationService);
            tradeCmd.setEconomyService(serviceRegistry.find(dev.starcore.starcore.foundation.economy.EconomyService.class).orElse(null));
            tradeCmd.setTradeTaxService(tradeTaxService);
            tradeCommand.setExecutor(tradeCmd);
            tradeCommand.setTabCompleter(tradeCmd);
            plugin.getLogger().info("Trade command registered.");
        } else {
            plugin.getLogger().warning("Command 'trade' not found in plugin.yml");
        }
    }

    /**
     * 注册事件监听器
     */
    private void registerListeners() {
        if (plugin == null) {
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(resourceListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(menuListener, plugin);
        plugin.getLogger().info("Resource listeners registered.");

        // 订阅贸易事件
        resourceListener.subscribeToTradeEvents();
    }

    /**
     * 启动定时任务
     */
    private void startScheduledTasks() {
        if (scheduler == null) {
            return;
        }

        // 每5分钟刷新所有价格
        scheduler.runSyncTimer(() -> {
            priceService.refreshAllPrices();
        }, 5 * 60 * 20L, 5 * 60 * 20L);

        // 每10分钟刷新垄断数据
        scheduler.runSyncTimer(() -> {
            monopolyService.refreshMonopolies();
        }, 10 * 60 * 20L, 10 * 60 * 20L);

        // 每分钟刷新工厂
        scheduler.runSyncTimer(() -> {
            processingService.refreshFactories();
        }, 60 * 20L, 60 * 20L);

        // 每小时重置过期配额
        scheduler.runSyncTimer(() -> {
            tradeService.resetExpiredQuotas();
        }, 60 * 60 * 20L, 60 * 60 * 20L);

        // 每30秒匹配市场订单
        scheduler.runSyncTimer(() -> {
            marketOrderBookService.matchAllOrders();
        }, 30 * 20L, 30 * 20L);

        // 每分钟清理过期订单
        scheduler.runSyncTimer(() -> {
            marketOrderBookService.cleanExpiredOrders();
        }, 60 * 20L, 60 * 20L);

        plugin.getLogger().info("Resource scheduled tasks started.");
    }

    /**
     * 注册到服务注册表
     */
    private void registerToServiceRegistry() {
        if (serviceRegistry == null) {
            return;
        }

        // 注册 ResourceService 主服务
        serviceRegistry.register(ResourceService.class, this);

        // 注册子服务
        serviceRegistry.register(ResourcePriceService.class, priceService);
        serviceRegistry.register(ResourceMonopolyService.class, monopolyService);
        serviceRegistry.register(ResourceTradeService.class, tradeService);
        serviceRegistry.register(ResourceReserveService.class, reserveService);
        serviceRegistry.register(ProcessingService.class, processingService);
        serviceRegistry.register(MarketOrderBookService.class, marketOrderBookService);
        serviceRegistry.register(TradeHistoryService.class, tradeHistoryService);
        serviceRegistry.register(PlayerTradeService.class, playerTradeService);
        serviceRegistry.register(TradeTaxService.class, tradeTaxService);

        plugin.getLogger().info("Resource services registered to ServiceRegistry.");
    }

    @Override
    public void disable(StarCoreContext context) {
        flushState();
        plugin.getLogger().info("ResourceModule disabled.");
    }

    // ==================== ResourceService 接口实现 ====================

    @Override
    public Collection<String> availableResourceTypes() {
        return RESOURCE_TYPES.stream().sorted().toList();
    }

    @Override
    public Map<String, Long> stockpile(NationId nationId) {
        Map<String, Long> values = stockpiles.getOrDefault(nationId, new ConcurrentHashMap<>());
        Map<String, Long> snapshot = new LinkedHashMap<>();
        for (String type : availableResourceTypes()) {
            snapshot.put(type, Math.max(0L, values.getOrDefault(type, 0L)));
        }
        return Map.copyOf(snapshot);
    }

    @Override
    public long amount(NationId nationId, String resourceType) {
        String type = normalizeResource(resourceType);
        if (!RESOURCE_TYPES.contains(type)) {
            return 0L;
        }
        return stockpiles.getOrDefault(nationId, new ConcurrentHashMap<>()).getOrDefault(type, 0L);
    }

    @Override
    public boolean grant(NationId nationId, String resourceType, long amount) {
        String type = normalizeResource(resourceType);
        if (!RESOURCE_TYPES.contains(type) || amount <= 0) {
            return false;
        }
        // 应用国策效果加成
        long finalAmount = applyPolicyBonus(nationId, type, amount);
        // audit B-090: finalAmount 因 applyPolicyBonus 乘法可能远超 Long.MAX_VALUE 截断。
        //   safeAdd 截断到 Long.MAX_VALUE 后超出部分丢失但这里能至少不回卷。
        //   为保守起见若 baseAmount 已接近上限则拒绝 grant 以保护玩家产出
        if (amount > Long.MAX_VALUE - (Long.MAX_VALUE >>> 1)) {
            if (plugin != null) plugin.getLogger().warning(
                "ResourceModule.grant rejected: baseAmount too large to apply policy bonus safely (nation=" + nationId + ", type=" + type + ", amount=" + amount + ")");
            return false;
        }
        stockpiles.computeIfAbsent(nationId, ignored -> new ConcurrentHashMap<>())
            .merge(type, finalAmount, ResourceModule::safeAdd);

        // 更新价格供应
        if (priceService != null) {
            priceService.addSupply(type, finalAmount);
        }

        saveState();
        return true;
    }

    /**
     * 应用国策效果加成到资源产出
     */
    private long applyPolicyBonus(NationId nationId, String resourceType, long baseAmount) {
        if (policyService == null) {
            return baseAmount;
        }
        // 获取生产加成（通用 production_bonus）
        double modifier = policyService.activePolicyModifier(nationId, "production_bonus", PolicyEffectScope.GLOBAL);
        if (modifier <= 0) {
            // audit B-091: modifier<=0 (含 admin 误配负值) 时直接返回原值，避免 baseAmount*(1+负mod) 回卷
            //   修饰加成语义应是非负放大；若需减产应使用其他字段。
            return baseAmount;
        }
        // audit B-091: 防止 baseAmount*(1+mod) 溢出。乘法前先对 modifier 设上限，并对大数用 BigDecimal 计算
        if (modifier > 10.0) modifier = 10.0;
        try {
            java.math.BigDecimal product = java.math.BigDecimal.valueOf(baseAmount)
                .multiply(java.math.BigDecimal.valueOf(1.0 + modifier));
            if (product.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                return Long.MAX_VALUE;
            }
            return product.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            // 理论不会发生
            return baseAmount;
        }
    }

    @Override
    public boolean consume(NationId nationId, String resourceType, long amount) {
        String type = normalizeResource(resourceType);
        // audit B-092: 区分"资源不存在"与"数量不足"两种 false 原因，便于调用方诊断
        if (!RESOURCE_TYPES.contains(type) || amount <= 0) {
            if (plugin != null) plugin.getLogger().fine(
                "ResourceModule.consume rejected invalid input (nation=" + nationId + ", type=" + resourceType + ", amount=" + amount + ")");
            return false;
        }
        boolean[] consumed = new boolean[] { false };
        boolean[] exists = new boolean[] { false };
        stockpiles.computeIfAbsent(nationId, ignored -> new ConcurrentHashMap<>())
            .compute(type, (ignored, current) -> {
                long value = current == null ? 0L : current;
                if (value > 0) exists[0] = true;
                if (value < amount) {
                    return value;
                }
                consumed[0] = true;
                return value - amount;
            });
        if (!consumed[0] && plugin != null) {
            plugin.getLogger().fine(
                "ResourceModule.consume insufficient stock (nation=" + nationId + ", type=" + type + ", amount=" + amount + ", exists=" + exists[0] + ")");
        }
        if (consumed[0]) {
            // 更新价格需求
            if (priceService != null) {
                priceService.addDemand(type, amount);
            }
            // audit B-093: consume 已扣玩家/国家资源，必须同步落盘；saveState() 的 saveAsync
            //   失败 / 崩溃时磁盘回档会导致钱已扣资源再出现。改用同步 flushState。
            flushState();
        }
        return consumed[0];
    }

    @Override
    public String summary() {
        long total = stockpiles.values().stream()
            .flatMap(map -> map.values().stream())
            .mapToLong(Long::longValue)
            .sum();
        return stockpiles.size() + " nation resource stockpile(s), total units " + total;
    }

    // ==================== 持久化方法 ====================

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(ResourceStateCodec.toProperties(snapshotStockpiles()));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(ResourceStateCodec.toProperties(snapshotStockpiles()));
    }

    private void loadState() {
        stockpiles.clear();
        ResourceStateCodec.fromProperties(
            stateStorage == null ? new java.util.Properties() : stateStorage.load(),
            RESOURCE_TYPES
        ).forEach((nationId, values) -> {
            ConcurrentMap<String, Long> mutable = new ConcurrentHashMap<>();
            values.forEach((type, amount) -> {
                if (RESOURCE_TYPES.contains(type) && amount > 0L) {
                    mutable.put(type, amount);
                }
            });
            if (!mutable.isEmpty()) {
                stockpiles.put(nationId, mutable);
            }
        });
    }

    private Map<NationId, Map<String, Long>> snapshotStockpiles() {
        Map<NationId, Map<String, Long>> snapshot = new LinkedHashMap<>();
        stockpiles.entrySet().stream()
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .forEach(entry -> {
                Map<String, Long> values = new LinkedHashMap<>();
                availableResourceTypes().forEach(type -> {
                    long amount = entry.getValue().getOrDefault(type, 0L);
                    if (amount > 0L) {
                        values.put(type, amount);
                    }
                });
                if (!values.isEmpty()) {
                    snapshot.put(entry.getKey(), Map.copyOf(values));
                }
            });
        return snapshot;
    }

    private static String normalizeResource(String resourceType) {
        return resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT);
    }

    private static long safeAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    // ==================== 公开的访问器方法 ====================

    /**
     * 获取价格服务
     */
    public ResourcePriceService getPriceService() {
        return priceService;
    }

    /**
     * 获取垄断服务
     */
    public ResourceMonopolyService getMonopolyService() {
        return monopolyService;
    }

    /**
     * 获取贸易服务
     */
    public ResourceTradeService getTradeService() {
        return tradeService;
    }

    /**
     * 获取储备服务
     */
    public ResourceReserveService getReserveService() {
        return reserveService;
    }

    /**
     * 获取加工服务
     */
    public ProcessingService getProcessingService() {
        return processingService;
    }

    /**
     * 获取市场订单簿服务
     */
    public MarketOrderBookService getMarketOrderBookService() {
        return marketOrderBookService;
    }

    /**
     * 获取交易历史服务
     */
    public TradeHistoryService getTradeHistoryService() {
        return tradeHistoryService;
    }

    /**
     * 获取玩家交易服务
     */
    public PlayerTradeService getPlayerTradeService() {
        return playerTradeService;
    }

    /**
     * 获取交易税收服务
     */
    public TradeTaxService getTradeTaxService() {
        return tradeTaxService;
    }

    /**
     * 获取 GUI
     */
    public ResourceMenu getResourceMenu() {
        return resourceMenu;
    }

    /**
     * 获取插件实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * 获取 GUI 整合类
     */
    public ResourceModuleGui getModuleGui() {
        return moduleGui;
    }

    /**
     * 获取 GUI 监听器
     */
    public ResourceMenuListener getMenuListener() {
        return menuListener;
    }
}
