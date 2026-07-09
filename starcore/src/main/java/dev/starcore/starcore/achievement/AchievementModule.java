package dev.starcore.starcore.achievement;
import java.util.Optional;

import dev.starcore.starcore.achievement.gui.AchievementGui;
import dev.starcore.starcore.achievement.gui.AchievementGuiListener;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.service.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就模块
 * 管理所有成就相关功能
 */
public final class AchievementModule extends AbstractAchievementService implements StarCoreModule, Listener {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "achievement",
        "成就系统",
        ModuleLayer.MODULE,
        List.of(),
        List.of(AchievementService.class),
        "Player achievement and progression tracking system"
    );

    private final Plugin plugin;
    private final Map<UUID, AchievementProgress> playerProgress = new ConcurrentHashMap<>();
    private AchievementStorage storage;
    private AchievementGui gui;
    private java.util.logging.Logger logger;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    public AchievementModule(Plugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.logger = context.plugin().getLogger();

        // 初始化存储
        this.storage = new AchievementStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            logger
        );

        // 注册服务
        context.serviceRegistry().register(AchievementService.class, this);

        // D-081: 注入内部经济服务，使成就的"金币奖励"类型真正落账
        setEconomyService(context.economyService());

        // 注册默认成就
        DefaultAchievements.registerAll(plugin, this);

        // 注册 GUI
        this.gui = new AchievementGui(plugin, this);

        // 注册事件监听器
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());

        // 注册 PlaceholderAPI 扩展
        registerPlaceholderAPI(context);

        logger.info("成就模块已启用，共注册 " + achievements.size() + " 个成就");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有玩家进度
        for (Map.Entry<UUID, AchievementProgress> entry : playerProgress.entrySet()) {
            storage.saveProgress(entry.getValue());
        }
        logger.info("成就模块已禁用");
    }

    /**
     * 注册 PlaceholderAPI 占位符
     */
    private void registerPlaceholderAPI(StarCoreContext context) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    new AchievementPlaceholder(plugin, this).register();
                    logger.info("成就系统 PlaceholderAPI 扩展已注册");
                } catch (Exception e) {
                    logger.warning("注册成就 PlaceholderAPI 扩展失败: " + e.getMessage());
                }
            });
        }
    }

    // ==================== AchievementService 实现 ====================

    @Override
    public void registerAchievement(Achievement achievement) {
        achievements.put(achievement.getKey(), achievement);
    }

    @Override
    public void registerAchievements(Achievement... achievements) {
        for (Achievement achievement : achievements) {
            registerAchievement(achievement);
        }
    }

    @Override
    public Optional<Achievement> getAchievement(NamespacedKey key) {
        return Optional.ofNullable(achievements.get(key));
    }

    @Override
    public Collection<Achievement> getAllAchievements() {
        return new ArrayList<>(achievements.values());
    }

    @Override
    public Collection<Achievement> getAchievementsByCategory(AchievementCategory category) {
        return achievements.values().stream()
            .filter(a -> getAchievementCategory(a.getKey()) == category)
            .toList();
    }

    @Override
    public boolean hasAchievement(UUID playerId, NamespacedKey key) {
        AchievementProgress progress = getOrCreateProgress(playerId);
        return progress.isCompleted(key);
    }

    @Override
    public boolean grantAchievement(Player player, NamespacedKey key) {
        Achievement achievement = achievements.get(key);
        if (achievement == null) {
            return false;
        }

        AchievementProgress progress = getOrCreateProgress(player.getUniqueId());

        // 检查是否已完成
        if (progress.isCompleted(key)) {
            return false;
        }

        // 检查父成就
        if (achievement.hasParent() && !progress.isParentCompleted(achievement.getParent())) {
            return false;
        }

        // 标记完成
        progress.markCompleted(key);
        storage.recordAchievementCompletion(player.getUniqueId(), key);

        // 显示成就通知
        displayAchievement(player, achievement);

        // 给予奖励
        giveRewards(player, achievement);

        return true;
    }

    @Override
    public Set<NamespacedKey> getPlayerAchievements(UUID playerId) {
        return getOrCreateProgress(playerId).getCompletedAchievements();
    }

    @Override
    public int getPlayerProgress(UUID playerId) {
        return getOrCreateProgress(playerId).getCompletedAchievements().size();
    }

    @Override
    public int getTotalAchievements() {
        return achievements.size();
    }

    @Override
    public void loadPlayerData(UUID playerId, Set<NamespacedKey> achievements) {
        AchievementProgress progress = storage.loadProgress(playerId);
        playerProgress.put(playerId, progress);
    }

    @Override
    public Set<NamespacedKey> savePlayerData(UUID playerId) {
        AchievementProgress progress = playerProgress.get(playerId);
        if (progress != null) {
            storage.saveProgress(progress);
        }
        return progress != null ? progress.getCompletedAchievements() : Set.of();
    }

    @Override
    public AchievementProgress getOrCreateProgress(UUID playerId) {
        return playerProgress.computeIfAbsent(playerId,
            k -> storage.loadProgress(k));
    }

    @Override
    public void incrementTrigger(UUID playerId, AchievementTrigger.TriggerType type, int amount) {
        AchievementProgress progress = getOrCreateProgress(playerId);
        String triggerKey = type.name();
        int newCount = progress.incrementTriggerCount(triggerKey, amount);

        // 检查并触发相关成就
        checkTriggerAchievements(playerId, type, newCount);

        // 保存进度
        storage.incrementAndSaveTriggerCount(playerId, triggerKey, amount);
    }

    /**
     * 检查基于触发器的成就
     */
    private void checkTriggerAchievements(UUID playerId, AchievementTrigger.TriggerType type, int currentCount) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        for (Achievement achievement : achievements.values()) {
            AchievementTrigger trigger = achievement.getTrigger();
            if (trigger == null || trigger.getType() != type) {
                continue;
            }

            // 检查是否满足条件
            Map<String, Object> conditions = trigger.getConditions();
            int required = (int) conditions.getOrDefault("kill_count", 1);

            if (currentCount >= required && !hasAchievement(playerId, achievement.getKey())) {
                grantAchievement(player, achievement.getKey());
            }
        }
    }

    /**
     * 打开成就 GUI
     */
    public void openGui(Player player) {
        if (gui != null) {
            gui.openMainMenu(player);
        }
    }

    /**
     * 打开分类成就 GUI
     */
    public void openCategoryGui(Player player, AchievementCategory category) {
        if (gui != null) {
            gui.openCategoryMenu(player, category);
        }
    }

    /**
     * 获取成就分类
     */
    public AchievementCategory getAchievementCategory(NamespacedKey key) {
        String keyStr = key.getKey().toLowerCase();
        if (keyStr.contains("combat") || keyStr.contains("kill") || keyStr.contains("pvp") || keyStr.contains("duel")) {
            return AchievementCategory.COMBAT;
        }
        if (keyStr.contains("gather") || keyStr.contains("mine") || keyStr.contains("wood") || keyStr.contains("stone")) {
            return AchievementCategory.GATHERING;
        }
        if (keyStr.contains("farm") || keyStr.contains("breed") || keyStr.contains("plant")) {
            return AchievementCategory.FARMING;
        }
        if (keyStr.contains("social") || keyStr.contains("friend") || keyStr.contains("guild")) {
            return AchievementCategory.SOCIAL;
        }
        if (keyStr.contains("nation") || keyStr.contains("city")) {
            return AchievementCategory.NATION;
        }
        if (keyStr.contains("tech") || keyStr.contains("craft") || keyStr.contains("recipe")) {
            return AchievementCategory.TECH;
        }
        if (keyStr.contains("nether") || keyStr.contains("end") || keyStr.contains("explore")) {
            return AchievementCategory.EXPLORATION;
        }
        if (keyStr.contains("money") || keyStr.contains("trade") || keyStr.contains("economy")) {
            return AchievementCategory.ECONOMY;
        }
        return AchievementCategory.ADVENTURE;
    }

    // ==================== 事件监听 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // 加载玩家进度
        getOrCreateProgress(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String blockKey = event.getBlock().getType().name();

        // 触发挖掘相关成就
        incrementTrigger(playerId, AchievementTrigger.TriggerType.BREAK_BLOCK, 1);

        // 检查物品相关成就
        if (isOre(event.getBlock().getType())) {
            incrementTrigger(playerId, AchievementTrigger.TriggerType.INVENTORY_CHANGED, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        incrementTrigger(playerId, AchievementTrigger.TriggerType.PLACED_BLOCK, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            UUID playerId = killer.getUniqueId();

            // 检查是否是玩家击杀
            if (event.getEntity() instanceof org.bukkit.entity.Player) {
                incrementTrigger(playerId, AchievementTrigger.TriggerType.CUSTOM_KILL_STREAK, 1);
            }

            // 怪物击杀
            incrementTrigger(playerId, AchievementTrigger.TriggerType.PLAYER_KILLED_ENTITY, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            UUID playerId = event.getEntity().getKiller().getUniqueId();
            incrementTrigger(playerId, AchievementTrigger.TriggerType.PLAYER_KILLED_ENTITY, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            ItemStack result = event.getInventory().getResult();
            if (result != null) {
                incrementTrigger(playerId, AchievementTrigger.TriggerType.RECIPE_CRAFTED, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        incrementTrigger(playerId, AchievementTrigger.TriggerType.CONSUME_ITEM, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().isRightClick() && event.hasBlock()) {
            UUID playerId = event.getPlayer().getUniqueId();
            incrementTrigger(playerId, AchievementTrigger.TriggerType.ITEM_USED_ON_BLOCK, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            incrementTrigger(playerId, AchievementTrigger.TriggerType.PLAYER_HURT_ENTITY, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String worldName = player.getWorld().getName();

        incrementTrigger(playerId, AchievementTrigger.TriggerType.CHANGED_DIMENSION, 1);

        if (worldName.contains("nether")) {
            incrementTrigger(playerId, AchievementTrigger.TriggerType.NETHER_TRAVEL, 1);
        }
    }

    private boolean isOre(org.bukkit.Material material) {
        return material == org.bukkit.Material.COAL_ORE ||
               material == org.bukkit.Material.IRON_ORE ||
               material == org.bukkit.Material.COPPER_ORE ||
               material == org.bukkit.Material.GOLD_ORE ||
               material == org.bukkit.Material.DIAMOND_ORE ||
               material == org.bukkit.Material.EMERALD_ORE ||
               material == org.bukkit.Material.NETHER_GOLD_ORE ||
               material == org.bukkit.Material.NETHER_QUARTZ_ORE ||
               material == org.bukkit.Material.DEEPSLATE_COAL_ORE ||
               material == org.bukkit.Material.DEEPSLATE_IRON_ORE ||
               material == org.bukkit.Material.DEEPSLATE_COPPER_ORE ||
               material == org.bukkit.Material.DEEPSLATE_GOLD_ORE ||
               material == org.bukkit.Material.DEEPSLATE_DIAMOND_ORE ||
               material == org.bukkit.Material.DEEPSLATE_EMERALD_ORE;
    }

    // PlaceholderAPI 支持
    public static class AchievementPlaceholder extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
        private final Plugin plugin;
        private final AchievementModule module;

        public AchievementPlaceholder(Plugin plugin, AchievementModule module) {
            this.plugin = plugin;
            this.module = module;
        }

        @Override
        public String getIdentifier() {
            return "starcore_achievement";
        }

        @Override
        public String getAuthor() {
            return String.join(", ", plugin.getPluginMeta().getAuthors());
        }

        @Override
        public String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String params) {
            if (player == null) {
                return "";
            }

            return switch (params) {
                case "completed" -> String.valueOf(module.getPlayerProgress(player.getUniqueId()));
                case "total" -> String.valueOf(module.getTotalAchievements());
                case "percentage" -> {
                    int completed = module.getPlayerProgress(player.getUniqueId());
                    int total = module.getTotalAchievements();
                    yield total > 0 ? String.format("%.1f", (completed * 100.0) / total) : "0";
                }
                default -> null;
            };
        }
    }
}
