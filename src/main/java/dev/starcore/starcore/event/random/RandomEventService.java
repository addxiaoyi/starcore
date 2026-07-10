package dev.starcore.starcore.event.random;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.event.random.effect.*;
import dev.starcore.starcore.event.random.trigger.*;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.util.RandomProvider;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.util.ColorCodes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 随机事件服务
 * 管理所有随机事件的加载、触发和执行
 */
public class RandomEventService {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final EconomyService economyService;
    private final Map<String, RandomEvent> events;
    private final EventChain eventChain;
    private final Map<String, Long> lastTriggerTimes;
    private final Map<UUID, PersistentEffect> persistentEffects;
    private final Map<String, Long> pendingChainTriggers;
    private RandomEventResponseMenuListener menuListener;
    private File configFile;
    private FileConfiguration pluginConfig;
    private boolean initialized = false;

    // 服务引用（延迟解析）
    private NationService nationService;
    private ResourceService resourceService;
    private TreasuryService treasuryService;
    private TechnologyService technologyService;
    private DiplomacyService diplomacyService;
    private ServiceRegistry serviceRegistry;

    public RandomEventService(JavaPlugin plugin, EconomyService economyService) {
        this(plugin, economyService, null, null, null, null, null);
    }

    public RandomEventService(JavaPlugin plugin, EconomyService economyService,
                            NationService nationService, ResourceService resourceService,
                            TreasuryService treasuryService) {
        this(plugin, economyService, nationService, resourceService, treasuryService, null, null);
    }

    public RandomEventService(JavaPlugin plugin, EconomyService economyService,
                            NationService nationService, ResourceService resourceService,
                            TreasuryService treasuryService, TechnologyService technologyService,
                            DiplomacyService diplomacyService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.economyService = economyService;
        this.nationService = nationService;
        this.resourceService = resourceService;
        this.treasuryService = treasuryService;
        this.technologyService = technologyService;
        this.diplomacyService = diplomacyService;
        this.events = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
        this.eventChain = new EventChain(this);
        this.eventChain.setPlugin(plugin);  // 设置插件引用用于调度
        this.lastTriggerTimes = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
        this.persistentEffects = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
        this.pendingChainTriggers = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
        this.pluginConfig = plugin.getConfig();
    }

    /**
     * 设置额外服务（用于延迟注入）
     */
    public void setServices(NationService nationService, ResourceService resourceService,
                           TreasuryService treasuryService, TechnologyService technologyService,
                           DiplomacyService diplomacyService) {
        this.nationService = nationService;
        this.resourceService = resourceService;
        this.treasuryService = treasuryService;
        this.technologyService = technologyService;
        this.diplomacyService = diplomacyService;
    }

    /**
     * 设置服务注册表
     */
    public void setServiceRegistry(ServiceRegistry registry) {
        this.serviceRegistry = registry;
    }

    /**
     * 获取服务注册表
     */
    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 初始化服务
     */
    public synchronized void initialize() {
        if (initialized) {
            logger.warning("RandomEventService 已初始化，跳过");
            return;
        }

        // 如果没有传入服务，从 ServiceRegistry 获取
        if (serviceRegistry != null) {
            if (nationService == null) {
                nationService = serviceRegistry.find(NationService.class).orElse(null);
            }
            if (resourceService == null) {
                resourceService = serviceRegistry.find(ResourceService.class).orElse(null);
            }
            if (treasuryService == null) {
                treasuryService = serviceRegistry.find(TreasuryService.class).orElse(null);
            }
            if (technologyService == null) {
                technologyService = serviceRegistry.find(TechnologyService.class).orElse(null);
            }
            if (diplomacyService == null) {
                diplomacyService = serviceRegistry.find(DiplomacyService.class).orElse(null);
            }
        }

        // 如果还是 null，尝试从 Bukkit 服务管理器获取
        if (nationService == null) {
            nationService = plugin.getServer().getServicesManager().load(NationService.class);
        }
        if (resourceService == null) {
            resourceService = plugin.getServer().getServicesManager().load(ResourceService.class);
        }
        if (treasuryService == null) {
            treasuryService = plugin.getServer().getServicesManager().load(TreasuryService.class);
        }
        if (technologyService == null) {
            technologyService = plugin.getServer().getServicesManager().load(TechnologyService.class);
        }
        if (diplomacyService == null) {
            diplomacyService = plugin.getServer().getServicesManager().load(DiplomacyService.class);
        }

        // 设置经济效果的服务引用
        EconomyEffect.setEconomyService(economyService);
        if (economyService instanceof InternalEconomyService) {
            EconomyEffect.setInternalEconomyService((InternalEconomyService) economyService);
        }
        EconomyEffect.setEventService(this);
        EconomyEffect.setNationService(nationService);
        EconomyEffect.setResourceService(resourceService);
        EconomyEffect.setTreasuryService(treasuryService);
        EconomyEffect.setTechnologyService(technologyService);
        EconomyEffect.setDiplomacyService(diplomacyService);
        EconomyEffect.setServiceRegistry(serviceRegistry);

        // 设置国家状态触发器的服务引用
        NationStateTrigger.injectServices(
            nationService, resourceService, technologyService, diplomacyService, plugin
        );
        if (serviceRegistry != null) {
            NationStateTrigger.setServiceRegistry(serviceRegistry);
        }

        // 设置 EventResponse 的静态服务引用
        if (economyService instanceof InternalEconomyService) {
            RandomEvent.EventResponse.setEconomyService((InternalEconomyService) economyService);
        }
        RandomEvent.EventResponse.setServiceRegistry(serviceRegistry);
        RandomEvent.EventResponse.setPlugin(plugin);
        RandomEvent.EventResponse.injectServices(
            economyService instanceof InternalEconomyService ? economyService : null,
            serviceRegistry, plugin
        );

        loadConfiguration();
        loadPersistentEffects();
        startEventChecker();
        // 初始化菜单监听器
        initializeMenuListener();

        initialized = true;
        logger.info("随机事件服务已启动，加载了 " + events.size() + " 个事件");
    }

    /**
     * 初始化菜单监听器
     */
    public void initializeMenuListener() {
        if (menuListener == null) {
            menuListener = new RandomEventResponseMenuListener(this);
            plugin.getServer().getPluginManager().registerEvents(menuListener, plugin);
            logger.info("随机事件响应菜单监听器已注册");
        }
    }

    /**
     * 获取菜单监听器
     */
    public RandomEventResponseMenuListener getMenuListener() {
        return menuListener;
    }

    /**
     * 加载配置文件
     */
    private void loadConfiguration() {
        configFile = new File(plugin.getDataFolder(), "events.yml");

        if (!configFile.exists()) {
            plugin.saveResource("events.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        loadEvents(config);
    }

    /**
     * 从配置加载事件
     *
     * @param config 配置对象
     */
    private void loadEvents(FileConfiguration config) {
        events.clear();

        ConfigurationSection eventsSection = config.getConfigurationSection("events");
        if (eventsSection == null) {
            logger.warning("配置文件中没有找到 'events' 节点");
            return;
        }

        for (String eventId : eventsSection.getKeys(false)) {
            ConfigurationSection eventSection = eventsSection.getConfigurationSection(eventId);
            if (eventSection == null) {
                continue;
            }

            try {
                RandomEvent event = parseEvent(eventId, eventSection);
                events.put(eventId, event);
                logger.info("加载事件: " + eventId + " - " + event.getName());
            } catch (Exception e) {
                logger.severe("加载事件失败: " + eventId);
                logger.log(Level.WARNING, "Failed to parse event: " + eventId, e);
            }
        }
    }

    /**
     * 解析事件配置
     *
     * @param eventId 事件ID
     * @param section 配置节点
     * @return 随机事件对象
     */
    private RandomEvent parseEvent(String eventId, ConfigurationSection section) {
        String name = section.getString("name", eventId);
        String description = section.getString("description", "");
        int cooldown = section.getInt("cooldown", 0);
        boolean global = section.getBoolean("global", false);
        int priority = section.getInt("priority", 0);

        // 解析触发器
        List<EventTrigger> triggers = parseTriggers(section.getConfigurationSection("triggers"));

        // 解析效果
        List<EventEffect> effects = parseEffects(section.getConfigurationSection("effects"));

        // 解析响应选项
        Map<String, RandomEvent.EventResponse> responses = parseResponses(
            section.getConfigurationSection("responses")
        );

        // 解析后续事件
        List<String> chainEvents = section.getStringList("chain_events");

        return new RandomEvent(eventId, name, description, triggers, effects,
                             responses, chainEvents, cooldown, global, priority);
    }

    /**
     * 解析触发器列表
     *
     * @param section 触发器配置节点
     * @return 触发器列表
     */
    private List<EventTrigger> parseTriggers(ConfigurationSection section) {
        List<EventTrigger> triggers = new ArrayList<>();

        if (section == null) {
            return triggers;
        }

        for (String triggerKey : section.getKeys(false)) {
            ConfigurationSection triggerSection = section.getConfigurationSection(triggerKey);
            if (triggerSection == null) {
                continue;
            }

            String type = triggerSection.getString("type");
            EventTrigger trigger = parseTrigger(type, triggerSection);

            if (trigger != null) {
                triggers.add(trigger);
            }
        }

        return triggers;
    }

    /**
     * 解析单个触发器
     *
     * @param type 触发器类型
     * @param section 配置节点
     * @return 触发器对象
     */
    private EventTrigger parseTrigger(String type, ConfigurationSection section) {
        switch (type.toLowerCase()) {
            case "time":
                TimeTrigger.TimeType timeType = TimeTrigger.TimeType.valueOf(
                    section.getString("time_type", "GAME_TIME")
                );
                int startTime = section.getInt("start", 0);
                int endTime = section.getInt("end", 24000);
                return new TimeTrigger(timeType, startTime, endTime);

            case "weather":
                WeatherTrigger.WeatherType weatherType = WeatherTrigger.WeatherType.valueOf(
                    section.getString("weather_type", "CLEAR")
                );
                return new WeatherTrigger(weatherType);

            case "location":
                LocationTrigger.LocationType locType = LocationTrigger.LocationType.valueOf(
                    section.getString("location_type", "WORLD")
                );
                Set<String> values = new HashSet<>(section.getStringList("values"));
                double radius = section.getDouble("radius", 10.0);
                return new LocationTrigger(locType, values, radius);

            case "probability":
                double probability = section.getDouble("probability", 0.5);
                return new ProbabilityTrigger(probability);

            case "player_count":
                int minPlayers = section.getInt("min", 0);
                int maxPlayers = section.getInt("max", Integer.MAX_VALUE);
                PlayerCountTrigger.CountType countType = PlayerCountTrigger.CountType.valueOf(
                    section.getString("count_type", "GLOBAL")
                );
                String worldName = section.getString("world");
                double countRadius = section.getDouble("radius", 50.0);
                return new PlayerCountTrigger(minPlayers, maxPlayers, countType, worldName, countRadius);

            case "nation_state":
                NationStateTrigger.StateType stateType = NationStateTrigger.StateType.valueOf(
                    section.getString("state_type", "POPULATION")
                );
                String stateValue = section.getString("state_value", "");
                NationStateTrigger.ComparisonType comparison = NationStateTrigger.ComparisonType.valueOf(
                    section.getString("comparison", "GREATER_THAN")
                );
                double threshold = section.getDouble("threshold", 0.0);
                return new NationStateTrigger(stateType, stateValue, comparison, threshold);

            default:
                logger.warning("未知的触发器类型: " + type);
                return null;
        }
    }

    /**
     * 检查事件系统是否允许生成生物
     * @return true 表示允许，false 表示禁用
     */
    private boolean isEventEntitySpawnEnabled() {
        return pluginConfig.getBoolean("module.event-entity-spawn", true);
    }

    /**
     * 解析效果列表
     *
     * @param section 效果配置节点
     * @return 效果列表
     */
    private List<EventEffect> parseEffects(ConfigurationSection section) {
        List<EventEffect> effects = new ArrayList<>();

        if (section == null) {
            return effects;
        }

        for (String effectKey : section.getKeys(false)) {
            ConfigurationSection effectSection = section.getConfigurationSection(effectKey);
            if (effectSection == null) {
                continue;
            }

            String type = effectSection.getString("type");
            EventEffect effect = parseEffect(type, effectSection);

            if (effect != null) {
                effects.add(effect);
            }
        }

        return effects;
    }

    /**
     * 解析单个效果
     *
     * @param type 效果类型
     * @param section 配置节点
     * @return 效果对象
     */
    private EventEffect parseEffect(String type, ConfigurationSection section) {
        switch (type.toLowerCase()) {
            case "player":
                PlayerEffect.EffectType playerEffectType = PlayerEffect.EffectType.valueOf(
                    section.getString("effect_type", "DAMAGE")
                );
                double value = section.getDouble("value", 0.0);
                int duration = section.getInt("duration", 0);
                String message = section.getString("message", "");
                return new PlayerEffect(playerEffectType, value, duration, message);

            case "crop":
                CropEffect.EffectType cropEffectType = CropEffect.EffectType.valueOf(
                    section.getString("effect_type", "BOOST_GROWTH")
                );
                value = section.getDouble("value", 1.0);
                int radius = section.getInt("radius", 10);
                duration = section.getInt("duration", 0);
                return new CropEffect(cropEffectType, value, radius, duration);

            case "building":
                BuildingEffect.EffectType buildingEffectType = BuildingEffect.EffectType.valueOf(
                    section.getString("effect_type", "DAMAGE")
                );
                value = section.getDouble("value", 0.5);
                radius = section.getInt("radius", 10);
                duration = section.getInt("duration", 0);
                String materialName = section.getString("material");
                Material material = materialName != null ? Material.valueOf(materialName) : null;
                return new BuildingEffect(buildingEffectType, value, radius, duration, material);

            case "economy":
                EconomyEffect.EffectType economyEffectType = EconomyEffect.EffectType.valueOf(
                    section.getString("effect_type", "ADD_MONEY")
                );
                value = section.getDouble("value", 0.0);
                String resourceType = section.getString("resource_type", "");
                duration = section.getInt("duration", 0);
                return new EconomyEffect(economyService, economyEffectType, value, resourceType, duration);

            case "spawn":
                // 检查配置：是否允许事件生成生物
                if (!isEventEntitySpawnEnabled()) {
                    logger.info("事件生成生物已禁用，跳过: " + section.getString("entity_type", "UNKNOWN"));
                    return null; // 返回null，效果列表会自动跳过
                }
                EntityType entityType = EntityType.valueOf(
                    section.getString("entity_type", "ZOMBIE")
                );
                int amount = section.getInt("amount", 1);
                int spawnRadius = section.getInt("radius", 10);
                boolean hostile = section.getBoolean("hostile", true);
                double healthMultiplier = section.getDouble("health_multiplier", 1.0);
                duration = section.getInt("duration", -1);
                return new SpawnEffect(entityType, amount, spawnRadius, hostile, healthMultiplier, duration);

            case "nation":
                NationEffect.EffectType nationEffectType = NationEffect.EffectType.valueOf(
                    section.getString("effect_type", "MORALE_CHANGE")
                );
                value = section.getDouble("value", 0.0);
                duration = section.getInt("duration", 0);
                String nationMessage = section.getString("message");
                NationEffect nationEffect = new NationEffect(plugin, nationService,
                    nationEffectType, value, duration, nationMessage);
                // 设置 TreasuryService（如果有）
                if (treasuryService != null) {
                    nationEffect.setTreasuryService(treasuryService);
                }
                return nationEffect;

            case "broadcast":
                String broadcastMsg = section.getString("message", "");
                return new BroadcastEffect(broadcastMsg);

            case "siege":
                SiegeEffect.Action siegeAction = SiegeEffect.Action.valueOf(
                    section.getString("action", "START").toUpperCase()
                );
                double siegeDamage = section.getDouble("damage_per_minute", 1.0);
                double siegeMoraleDrain = section.getDouble("morale_drain", 2.0);
                int siegeDuration = section.getInt("duration", 1800);
                return new SiegeEffect(plugin, nationService, siegeAction,
                    siegeDamage, siegeMoraleDrain, siegeDuration);

            default:
                logger.warning("未知的效果类型: " + type);
                return null;
        }
    }

    /**
     * 解析响应选项
     *
     * @param section 响应配置节点
     * @return 响应映射
     */
    private Map<String, RandomEvent.EventResponse> parseResponses(ConfigurationSection section) {
        Map<String, RandomEvent.EventResponse> responses = new HashMap<>();

        if (section == null) {
            return responses;
        }

        for (String responseId : section.getKeys(false)) {
            ConfigurationSection responseSection = section.getConfigurationSection(responseId);
            if (responseSection == null) {
                continue;
            }

            String name = responseSection.getString("name", responseId);
            String description = responseSection.getString("description", "");
            List<EventEffect> effects = parseEffects(responseSection.getConfigurationSection("effects"));
            List<String> chainEvents = responseSection.getStringList("chain_events");
            Map<String, Object> requirements = new HashMap<>();

            ConfigurationSection reqSection = responseSection.getConfigurationSection("requirements");
            if (reqSection != null) {
                for (String key : reqSection.getKeys(false)) {
                    requirements.put(key, reqSection.get(key));
                }
            }

            RandomEvent.EventResponse response = new RandomEvent.EventResponse(
                responseId, name, description, effects, chainEvents, requirements
            );
            responses.put(responseId, response);
        }

        return responses;
    }

    /**
     * 启动事件检查器
     * 修复: 降低检查频率从每秒改为每60秒，减少服务器压力
     * 原: 20L, 20L (每秒)
     * 新: 1200L, 1200L (每60秒)
     */
    private void startEventChecker() {
        // 每60秒检查一次随机事件（原为每秒，减少服务器压力）
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndTriggerEvents();
            checkPendingChainTriggers();
            eventChain.cleanupExpiredChains(3600); // 清理1小时前的事件链
            cleanupExpiredEffects(); // 清理过期的持续效果
        }, 1200L, 1200L);  // 60秒 = 1200 ticks
    }

    /**
     * 检查并触发事件
     */
    private void checkAndTriggerEvents() {
        for (RandomEvent event : events.values()) {
            // 跳过冷却为0的事件（这些是链事件，由主事件触发，不应被随机检查）
            if (event.getCooldown() <= 0) {
                continue;
            }

            // 检查冷却时间
            if (!checkCooldownInternal(event)) {
                continue;
            }

            // 全局事件
            if (event.isGlobal()) {
                if (event.shouldTrigger(null, null)) {
                    // 立即设置冷却，防止同一周期内重复触发
                    lastTriggerTimes.put(event.getId(), System.currentTimeMillis());
                    triggerEvent(event, null, null);
                }
            } else {
                // 玩家事件 - 先检查是否有玩家需要触发
                boolean shouldTrigger = false;
                Player triggerPlayer = null;
                Location triggerLocation = null;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (event.shouldTrigger(player, player.getLocation())) {
                        shouldTrigger = true;
                        triggerPlayer = player;
                        triggerLocation = player.getLocation();
                        break; // 只取第一个触发玩家
                    }
                }

                if (shouldTrigger) {
                    // 立即设置冷却，防止同一周期内重复触发
                    lastTriggerTimes.put(event.getId(), System.currentTimeMillis());
                    triggerEvent(event, triggerPlayer, triggerLocation);
                }
            }
        }
    }

    /**
     * 触发事件
     *
     * @param event 事件
     * @param player 触发玩家
     * @param location 触发位置
     */
    public void triggerEvent(RandomEvent event, Player player, Location location) {
        triggerEvent(event, player, location, true);
    }

    /**
     * 触发事件
     *
     * @param event 事件
     * @param player 触发玩家
     * @param location 触发位置
     * @param setCooldown 是否设置冷却时间
     */
    public void triggerEvent(RandomEvent event, Player player, Location location, boolean setCooldown) {
        logger.info("触发事件: " + event.getName());

        // 应用效果
        event.applyEffects(player, location);

        // 更新冷却时间 - 链事件也要设置冷却，防止重复触发
        // 如果 setCooldown=true，或者事件还没有冷却记录，则设置冷却
        if (setCooldown || !lastTriggerTimes.containsKey(event.getId())) {
            lastTriggerTimes.put(event.getId(), System.currentTimeMillis());
        }

        // 通知玩家
        if (player != null) {
            player.sendMessage("§6§l[事件] §r" + event.getName());
            player.sendMessage("§7" + event.getDescription());
        } else {
            // 全局广播
            Bukkit.broadcast(Component.text("§6§l[全局事件] §r" + event.getName()));
            Bukkit.broadcast(Component.text("§7" + event.getDescription()));
        }

        // 处理后续事件链
        if (!event.getChainEvents().isEmpty()) {
            UUID chainId = eventChain.startChain(event.getId(), player, location);
            // 触发链中的第一个事件（延迟0），后续事件会在 executeChainEvent 中处理
            for (int i = 0; i < event.getChainEvents().size(); i++) {
                String nextEventId = event.getChainEvents().get(i);
                // 每个后续事件延迟 1 分钟 (20 ticks * 60 seconds)
                eventChain.continueChain(chainId, nextEventId, 20L * 60 * (i + 1));
            }
        }
    }

    /**
     * 内部使用：检查冷却时间
     * 注意：链事件配置 cooldown: 0，但仍有最小冷却防止重复触发
     */
    private boolean checkCooldownInternal(RandomEvent event) {
        // 链事件使用最小60秒冷却，防止重复触发
        int effectiveCooldown = Math.max(event.getCooldown(), 60);

        Long lastTime = lastTriggerTimes.get(event.getId());
        if (lastTime == null) {
            return true;
        }

        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        return elapsed >= effectiveCooldown;
    }

    /**
     * 检查冷却时间（公开方法，供外部使用）
     *
     * @param event 事件
     * @return 如果冷却完成返回true
     */
    public boolean checkCooldown(RandomEvent event) {
        return checkCooldown(event.getId(), event.getCooldown());
    }

    /**
     * 检查事件冷却时间
     *
     * @param eventId 事件ID
     * @param cooldownSeconds 冷却时间（秒）
     * @return 如果冷却完成返回true
     */
    public boolean checkCooldown(String eventId, int cooldownSeconds) {
        // 链事件使用最小60秒冷却，防止重复触发
        // 即使配置文件中 cooldown: 0，也要确保不会无限重复触发
        int effectiveCooldown = Math.max(cooldownSeconds, 60);

        Long lastTime = lastTriggerTimes.get(eventId);
        if (lastTime == null) {
            return true;
        }

        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        return elapsed >= effectiveCooldown;
    }

    /**
     * 设置事件冷却时间（用于链事件）
     *
     * @param eventId 事件ID
     */
    public void setCooldown(String eventId) {
        lastTriggerTimes.put(eventId, System.currentTimeMillis());
    }

    /**
     * 执行响应
     *
     * @param event 事件
     * @param responseId 响应ID
     * @param player 玩家
     * @param location 位置
     * @return 如果成功执行返回true
     */
    public boolean executeResponse(RandomEvent event, String responseId, Player player, Location location) {
        return event.executeResponse(responseId, player, location);
    }

    /**
     * 打开事件响应菜单
     *
     * @param event 事件
     * @param player 玩家
     * @param chainId 事件链ID
     */
    public void openResponseMenu(RandomEvent event, Player player, UUID chainId) {
        if (menuListener == null) {
            initializeMenuListener();
        }

        // Bug修复 #2: 验证事件链是否仍然有效
        if (chainId != null) {
            EventChain.ChainState chainState = eventChain.getChainState(chainId);
            if (chainState == null) {
                player.sendMessage(Component.text("§c此事件已过期！", NamedTextColor.RED));
                return;
            }
        }

        RandomEventResponseMenu menu = RandomEventResponseMenu.create(event, player, this, chainId);
        menu.buildMenu();
        menuListener.registerOpenMenu(menu);
        player.openInventory(menu.getInventory());
    }

    /**
     * 检查玩家是否有足够的金币
     *
     * @param player 玩家
     * @param amount 金额
     * @return 是否有足够的金币
     */
    public boolean hasPlayerMoney(Player player, double amount) {
        if (economyService instanceof InternalEconomyService) {
            return ((InternalEconomyService) economyService).has(
                player.getUniqueId(),
                java.math.BigDecimal.valueOf(amount)
            );
        }
        // 通用接口检查
        try {
            var balance = economyService.getBalance(player.getUniqueId());
            return balance.doubleValue() >= amount;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查玩家是否有指定物品
     *
     * @param player 玩家
     * @param itemConfig 物品配置
     * @return 是否有足够的物品
     */
    public boolean hasPlayerItems(Player player, Map<String, Object> itemConfig) {
        String materialName = (String) itemConfig.get("material");
        int amount = ((Number) itemConfig.getOrDefault("amount", 1)).intValue();

        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }
            return count >= amount;
        } catch (Exception e) {
            logger.warning("检查物品失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理事件链响应
     *
     * @param chainId 事件链ID
     * @param chainEvents 后续事件列表
     */
    public void processChainResponses(UUID chainId, List<String> chainEvents) {
        if (chainEvents == null || chainEvents.isEmpty()) {
            return;
        }

        for (int i = 0; i < chainEvents.size(); i++) {
            String nextEventId = chainEvents.get(i);
            int delayTicks = (i + 1) * 20 * 60; // 每个后续事件延迟1分钟

            final String eventId = nextEventId;
            pendingChainTriggers.put(eventId + "_" + chainId, System.currentTimeMillis() + (delayTicks * 50));

            // 延迟触发
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                EventChain.ChainState chainState = eventChain.getChainState(chainId);
                if (chainState != null) {
                    eventChain.continueChain(chainId, eventId);
                }
                pendingChainTriggers.remove(eventId + "_" + chainId);
            }, delayTicks);
        }
    }

    /**
     * 检查待触发的事件链
     * 注意: 链事件由 processChainResponses 中的 runTaskLater 处理
     * 此方法仅用于清理过期的条目
     */
    private void checkPendingChainTriggers() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : new HashMap<>(pendingChainTriggers).entrySet()) {
            // 清理超过5分钟的过期条目
            if (entry.getValue() + 300000 < now) {
                pendingChainTriggers.remove(entry.getKey());
            }
        }
    }

    /**
     * 添加持续性效果
     *
     * @param playerId 玩家ID
     * @param effectType 效果类型
     * @param effectId 效果ID
     * @param value 值
     * @param durationSeconds 持续时间（秒）
     */
    public void addPersistentEffect(UUID playerId, String effectType, String effectId,
                                   double value, int durationSeconds) {
        long expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        PersistentEffect effect = new PersistentEffect(playerId, effectType, effectId,
                                                       value, expiryTime);
        persistentEffects.put(playerId, effect);

        // 保存到配置文件
        savePersistentEffects();
    }

    /**
     * 获取玩家的持续性效果
     *
     * @param playerId 玩家ID
     * @return 持续性效果，如果不存在或已过期返回null
     */
    public PersistentEffect getPersistentEffect(UUID playerId) {
        PersistentEffect effect = persistentEffects.get(playerId);
        if (effect != null && !effect.isExpired()) {
            return effect;
        }
        if (effect != null) {
            persistentEffects.remove(playerId);
        }
        return null;
    }

    /**
     * 检查玩家是否有指定类型的持续效果
     *
     * @param playerId 玩家ID
     * @param effectType 效果类型
     * @return 是否有该效果
     */
    public boolean hasPersistentEffect(UUID playerId, String effectType) {
        PersistentEffect effect = getPersistentEffect(playerId);
        return effect != null && effect.getEffectType().equals(effectType);
    }

    /**
     * 移除玩家的持续性效果
     *
     * @param playerId 玩家ID
     */
    public void removePersistentEffect(UUID playerId) {
        persistentEffects.remove(playerId);
        savePersistentEffects();
    }

    /**
     * 保存持续性效果到文件
     */
    private void savePersistentEffects() {
        File dataFile = new File(plugin.getDataFolder(), "event_effects.yml");
        org.bukkit.configuration.file.YamlConfiguration config =
            new org.bukkit.configuration.file.YamlConfiguration();

        for (Map.Entry<UUID, PersistentEffect> entry : persistentEffects.entrySet()) {
            String path = "effects." + entry.getKey().toString();
            PersistentEffect effect = entry.getValue();
            config.set(path + ".type", effect.getEffectType());
            config.set(path + ".id", effect.getEffectId());
            config.set(path + ".value", effect.getValue());
            config.set(path + ".expiry", effect.getExpiryTime());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            logger.warning("保存持续性效果失败: " + e.getMessage());
        }
    }

    /**
     * 加载持续性效果
     */
    private void loadPersistentEffects() {
        File dataFile = new File(plugin.getDataFolder(), "event_effects.yml");
        if (!dataFile.exists()) {
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration config =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);

        for (String key : config.getKeys(false)) {
            if ("effects".equals(key)) {
                for (String playerIdStr : config.getConfigurationSection("effects").getKeys(false)) {
                    try {
                        UUID playerId = UUID.fromString(playerIdStr);
                        String type = config.getString("effects." + playerIdStr + ".type");
                        String effectId = config.getString("effects." + playerIdStr + ".id");
                        double value = config.getDouble("effects." + playerIdStr + ".value");
                        long expiry = config.getLong("effects." + playerIdStr + ".expiry");

                        PersistentEffect effect = new PersistentEffect(playerId, type, effectId,
                                                                       value, expiry);
                        if (!effect.isExpired()) {
                            persistentEffects.put(playerId, effect);
                        }
                    } catch (Exception e) {
                        logger.warning("加载持续性效果失败: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 清理过期的持续性效果
     */
    private void cleanupExpiredEffects() {
        long now = System.currentTimeMillis();
        persistentEffects.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                logger.info("清理过期效果: " + entry.getKey() + " - " + entry.getValue().getEffectType());
                return true;
            }
            return false;
        });
    }

    /**
     * 持续性效果类
     */
    public static class PersistentEffect {
        private final UUID playerId;
        private final String effectType;
        private final String effectId;
        private final double value;
        private final long expiryTime;

        public PersistentEffect(UUID playerId, String effectType, String effectId,
                               double value, long expiryTime) {
            this.playerId = playerId;
            this.effectType = effectType;
            this.effectId = effectId;
            this.value = value;
            this.expiryTime = expiryTime;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public String getEffectType() {
            return effectType;
        }

        public String getEffectId() {
            return effectId;
        }

        public double getValue() {
            return value;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }

        public long getRemainingSeconds() {
            long remaining = (expiryTime - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        }
    }

    /**
     * 获取事件
     *
     * @param eventId 事件ID
     * @return 事件对象，如果不存在返回null
     */
    public RandomEvent getEvent(String eventId) {
        return events.get(eventId);
    }

    /**
     * 获取所有事件
     *
     * @return 事件映射
     */
    public Map<String, RandomEvent> getAllEvents() {
        return new HashMap<>(events);
    }

    /**
     * 重载配置
     */
    public void reload() {
        plugin.reloadConfig();
        this.pluginConfig = plugin.getConfig();
        loadConfiguration();
        logger.info("随机事件配置已重载");
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        savePersistentEffects();
        events.clear();
        lastTriggerTimes.clear();
        persistentEffects.clear();
        pendingChainTriggers.clear();
        logger.info("随机事件服务已关闭");
    }
}
