package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.upgrade.gui.TerritoryUpgradeGui;
import dev.starcore.starcore.module.territory.upgrade.gui.TerritoryUpgradeGuiListener;
import dev.starcore.starcore.module.territory.upgrade.model.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Territory Upgrade Module - Main implementation.
 * 领地升级模块 - 主要实现
 */
public final class TerritoryUpgradeModule implements StarCoreModule, TerritoryUpgradeService, Listener {

    private static final String FILE_NAME = "territory_upgrades.yml";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "territory_upgrade",
        "领地升级系统",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(TerritoryUpgradeService.class),
        "Provides territory upgrade system with experience-based progression."
    );

    private final Map<NationId, UpgradeProgressData> nationProgress;
    private final Map<String, Integer> expSources;
    // 审计 A-074: 追踪每日奖励时间
    private final Map<NationId, Long> lastDailyReward = new ConcurrentHashMap<>();

    private StarCoreContext currentContext;
    private UpgradeDefinitionLoader definitionLoader;
    private UpgradeValidator validator;
    private UpgradeScheduler scheduler;
    private UpgradeStateStorage stateStorage;
    private NationService nationService;

    public TerritoryUpgradeModule() {
        this.nationProgress = new ConcurrentHashMap<>();
        this.expSources = new ConcurrentHashMap<>();
    }

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.currentContext = context;
        context.persistenceService().ensureNamespace(metadata().id());

        // 初始化组件
        this.definitionLoader = new UpgradeDefinitionLoader(context.plugin());
        this.validator = new UpgradeValidator(this, context.serviceRegistry(), definitionLoader);
        this.scheduler = new UpgradeScheduler(
            context.plugin(),
            context.scheduler(),
            this::onUpgradeComplete
        );

        // 初始化存储
        this.stateStorage = new DatabaseAwareUpgradeStateStorage(
            context.plugin(),
            metadata().id(),
            context.databaseService(),
            context.persistenceService()
        );

        // 获取国家服务
        this.nationService = context.serviceRegistry().find(NationService.class).orElse(null);

        // 加载经验来源配置
        loadExpSourcesConfig();

        // 注册事件监听器
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());

        // 注册GUI监听器
        context.plugin().getServer().getPluginManager().registerEvents(
            new TerritoryUpgradeGuiListener(this, nationService, context.plugin()),
            context.plugin()
        );

        // 注册命令
        registerCommands();

        // 加载数据
        loadState();

        currentContext.plugin().getLogger().info("TerritoryUpgradeModule enabled.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有数据
        saveState();

        // 关闭调度器
        if (scheduler != null) {
            scheduler.shutdown();
        }

        // 清除玩家效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 清理玩家特定的升级效果
        }

        context.plugin().getLogger().info("TerritoryUpgradeModule disabled.");
        currentContext = null;
    }

    // ========== TerritoryUpgradeService Implementation ==========

    @Override
    public Collection<String> getAvailablePaths() {
        return definitionLoader.getAllPaths().keySet();
    }

    @Override
    public Optional<UpgradeTierDefinition> getPathDefinition(String pathId) {
        return Optional.ofNullable(definitionLoader.getPath(pathId));
    }

    @Override
    public Optional<TerritoryUpgradeLevel> getLevelDefinition(String pathId, int level) {
        UpgradeTierDefinition path = definitionLoader.getPath(pathId);
        if (path == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(path.getLevel(level));
    }

    @Override
    public int getCurrentLevel(NationId nationId, String pathId) {
        return getOrCreateProgress(nationId).getPathLevel(pathId);
    }

    @Override
    public int getTotalExp(NationId nationId) {
        return getOrCreateProgress(nationId).totalExp();
    }

    @Override
    public int getExpSpent(NationId nationId) {
        return getOrCreateProgress(nationId).totalExpSpent();
    }

    @Override
    public UpgradeCheckResult canUpgrade(NationId nationId, String pathId) {
        return validator.validate(nationId, pathId);
    }

    @Override
    public UpgradeCheckResult canUpgradeTo(NationId nationId, String pathId, int targetLevel) {
        return validator.validateLevel(nationId, pathId, targetLevel);
    }

    @Override
    public UpgradeCheckResult validateRequirements(NationId nationId, String pathId) {
        return validator.validate(nationId, pathId);
    }

    @Override
    public List<String> getMissingPrerequisites(NationId nationId, String pathId) {
        return validator.getMissingPrerequisites(nationId, pathId);
    }

    @Override
    public UpgradeCheckResult startUpgrade(NationId nationId, String pathId) {
        // 验证
        UpgradeCheckResult result = validator.validate(nationId, pathId);
        if (!result.isSuccess()) {
            return result;
        }

        // 获取当前进度
        UpgradeProgressData progress = getOrCreateProgress(nationId);
        int currentLevel = progress.getPathLevel(pathId);

        // 获取目标等级
        Optional<TerritoryUpgradeLevel> nextLevelOpt = getNextLevel(nationId, pathId);
        if (nextLevelOpt.isEmpty()) {
            return UpgradeCheckResult.failure("无法获取下一等级", UpgradeCheckError.INVALID_LEVEL);
        }

        TerritoryUpgradeLevel nextLevel = nextLevelOpt.get();
        int requiredExp = nextLevel.expRequired();

        // 计算可用经验
        int availableExp = progress.totalExp() - progress.totalExpSpent();
        if (availableExp < requiredExp) {
            return UpgradeCheckResult.failure(
                String.format("经验不足: 需要 %d, 可用 %d", requiredExp, availableExp),
                UpgradeCheckError.NOT_ENOUGH_EXP
            );
        }

        // 消耗经验
        progress.spendExp(requiredExp);

        // 设置路径等级
        progress.setPathLevel(pathId, currentLevel + 1);

        // 更新活跃升级
        UpgradeProcess newProcess = new UpgradeProcess(
            pathId,
            currentLevel + 1,
            requiredExp,
            requiredExp,
            java.time.Instant.now(),
            java.time.Instant.now(),
            true
        );
        progress.addActiveUpgrade(pathId, newProcess);

        // 通知完成
        onUpgradeComplete(nationId, pathId);

        // 保存状态
        saveStateAsync();

        return UpgradeCheckResult.success();
    }

    @Override
    public boolean cancelUpgrade(NationId nationId, String pathId) {
        UpgradeProgressData progress = nationProgress.get(nationId);
        if (progress == null) {
            return false;
        }

        progress.removeActiveUpgrade(pathId);
        scheduler.cancelUpgrade(nationId, pathId);
        saveStateAsync();
        return true;
    }

    @Override
    public boolean forceCompleteUpgrade(NationId nationId, String pathId) {
        UpgradeProgressData progress = nationProgress.get(nationId);
        if (progress == null) {
            return false;
        }

        // 强制设置等级
        UpgradeTierDefinition path = definitionLoader.getPath(pathId);
        if (path != null) {
            int maxLevel = path.maxLevel();
            progress.setPathLevel(pathId, maxLevel);
        }

        progress.clearAllActiveUpgrades();
        scheduler.cancelUpgrade(nationId, pathId);
        saveStateAsync();

        onUpgradeComplete(nationId, pathId);
        return true;
    }

    @Override
    public void addExperience(NationId nationId, int exp, String source) {
        addExperience(nationId, exp, source, null);
    }

    @Override
    public void addExperience(NationId nationId, int exp, String source, Consumer<Integer> callback) {
        if (exp <= 0 || nationId == null) {
            return;
        }

        // 应用来源加成
        int finalExp = exp;
        Integer sourceMultiplier = expSources.get(source.toLowerCase());
        if (sourceMultiplier != null) {
            finalExp = (int) (exp * sourceMultiplier);
        }

        UpgradeProgressData progress = getOrCreateProgress(nationId);
        progress.addExp(finalExp);

        if (callback != null) {
            callback.accept(finalExp);
        }

        saveStateAsync();

        // 设计决策：仅升级最高优先级一条路径，避免无限递归
        // 收集所有可升级路径后再批量处理是后续优化工作
        String bestPath = null;
        UpgradeCheckResult bestResult = null;
        for (String pathId : getAvailablePaths()) {
            UpgradeCheckResult result = validator.validate(nationId, pathId);
            if (result.isSuccess() && bestResult == null) {
                bestResult = result;
                bestPath = pathId;
            }
        }
        if (bestPath != null) {
            startUpgrade(nationId, bestPath);
        }
    }

    @Override
    public Optional<UpgradeProcess> getActiveUpgrade(NationId nationId, String pathId) {
        UpgradeProgressData progress = nationProgress.get(nationId);
        if (progress == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(progress.getActiveUpgrade(pathId));
    }

    @Override
    public Map<String, UpgradeProcess> getActiveUpgrades(NationId nationId) {
        UpgradeProgressData progress = nationProgress.get(nationId);
        if (progress == null) {
            return Map.of();
        }
        return progress.activeUpgrades();
    }

    @Override
    public boolean isUpgrading(NationId nationId, String pathId) {
        UpgradeProgressData progress = nationProgress.get(nationId);
        if (progress == null) {
            return false;
        }
        return progress.isUpgrading(pathId);
    }

    @Override
    public int getUpgradeProgress(NationId nationId, String pathId) {
        UpgradeProcess process = getActiveUpgrade(nationId, pathId).orElse(null);
        if (process == null) {
            return 0;
        }
        return process.getProgressPercent();
    }

    @Override
    public UpgradeBenefit getCumulativeBenefits(NationId nationId, String pathId) {
        UpgradeTierDefinition path = definitionLoader.getPath(pathId);
        if (path == null) {
            return new UpgradeBenefit(pathId, 0, Map.of());
        }

        int currentLevel = getCurrentLevel(nationId, pathId);
        Map<String, Object> cumulativeBenefits = new HashMap<>();

        // 累积所有等级的利益
        for (int i = 1; i <= currentLevel; i++) {
            TerritoryUpgradeLevel levelDef = path.getLevel(i);
            if (levelDef != null) {
                for (Map.Entry<String, Object> entry : levelDef.benefits().entrySet()) {
                    // 对于乘法收益，累积相乘
                    if (entry.getKey().endsWith("_bonus") || entry.getKey().endsWith("_rate") ||
                        entry.getKey().endsWith("_modifier")) {
                        double current = 0.0;
                        if (cumulativeBenefits.containsKey(entry.getKey())) {
                            Object obj = cumulativeBenefits.get(entry.getKey());
                            if (obj instanceof Number) {
                                current = ((Number) obj).doubleValue();
                            }
                        }
                        double addValue = 0.0;
                        if (entry.getValue() instanceof Number) {
                            addValue = ((Number) entry.getValue()).doubleValue();
                            // 如果当前值为1.0（默认值），则替换；否则相乘
                            if (current == 0.0 || current == 1.0) {
                                cumulativeBenefits.put(entry.getKey(), addValue);
                            } else {
                                cumulativeBenefits.put(entry.getKey(), current * addValue);
                            }
                        }
                    } else {
                        // 对于加法收益，累积相加
                        double current = 0.0;
                        if (cumulativeBenefits.containsKey(entry.getKey())) {
                            Object obj = cumulativeBenefits.get(entry.getKey());
                            if (obj instanceof Number) {
                                current = ((Number) obj).doubleValue();
                            }
                        }
                        if (entry.getValue() instanceof Number) {
                            double addValue = ((Number) entry.getValue()).doubleValue();
                            cumulativeBenefits.put(entry.getKey(), current + addValue);
                        }
                    }
                }
            }
        }

        return new UpgradeBenefit(pathId, currentLevel, cumulativeBenefits);
    }

    @Override
    public double getBenefitModifier(NationId nationId, String benefitType) {
        UpgradeBenefit benefit = getCumulativeBenefits(nationId, "basic");
        if (benefit.has(benefitType)) {
            return benefit.getDouble(benefitType, 1.0);
        }
        return 1.0;
    }

    @Override
    public int getClaimLimitBonus(NationId nationId) {
        UpgradeBenefit benefit = getCumulativeBenefits(nationId, "basic");
        return benefit.getClaimLimitBonus();
    }

    @Override
    public double getTaxRateModifier(NationId nationId) {
        UpgradeBenefit benefit = getCumulativeBenefits(nationId, "basic");
        return benefit.getTaxRateModifier();
    }

    @Override
    public double getResourceBonus(NationId nationId) {
        UpgradeBenefit benefit = getCumulativeBenefits(nationId, "basic");
        return benefit.getResourceBonus();
    }

    @Override
    public Optional<TerritoryUpgradeLevel> getNextLevel(NationId nationId, String pathId) {
        UpgradeTierDefinition path = definitionLoader.getPath(pathId);
        if (path == null) {
            return Optional.empty();
        }

        int currentLevel = getCurrentLevel(nationId, pathId);
        return Optional.ofNullable(path.getNextLevel(currentLevel));
    }

    @Override
    public int getExpRequiredForNextLevel(NationId nationId, String pathId) {
        return getNextLevel(nationId, pathId)
            .map(TerritoryUpgradeLevel::expRequired)
            .orElse(0);
    }

    @Override
    public int getProgressToNextLevel(NationId nationId, String pathId) {
        UpgradeProgressData progress = nationProgress.get(nationId);
        if (progress == null) {
            return 0;
        }

        int availableExp = progress.totalExp() - progress.totalExpSpent();
        int requiredExp = getExpRequiredForNextLevel(nationId, pathId);

        if (requiredExp <= 0) {
            return 100;
        }

        return Math.min(100, (availableExp * 100) / requiredExp);
    }

    @Override
    public boolean isPathMaxed(NationId nationId, String pathId) {
        UpgradeTierDefinition path = definitionLoader.getPath(pathId);
        if (path == null) {
            return true;
        }
        return getCurrentLevel(nationId, pathId) >= path.maxLevel();
    }

    @Override
    public String getSummary() {
        return nationProgress.size() + " nations with upgrade data, " +
               scheduler.getActiveCount() + " active upgrades";
    }

    @Override
    public void resetProgress(NationId nationId) {
        nationProgress.remove(nationId);
        scheduler.clearNationUpgrades(nationId);
        saveStateAsync();
    }

    @Override
    public Collection<NationId> getAllUpgradedNations() {
        return Set.copyOf(nationProgress.keySet());
    }

    @Override
    public void reloadDefinitions() {
        definitionLoader.reload();
    }

    @Override
    public int getExpFromSource(String source, int baseAmount) {
        Integer multiplier = expSources.get(source.toLowerCase());
        if (multiplier == null) {
            return baseAmount;
        }
        return (int) (baseAmount * multiplier);
    }

    // ========== Internal Methods ==========

    private UpgradeProgressData getOrCreateProgress(NationId nationId) {
        return nationProgress.computeIfAbsent(nationId, UpgradeProgressData::new);
    }

    private void onUpgradeComplete(NationId nationId, String pathId) {
        if (currentContext == null) {
            return;
        }

        int newLevel = getCurrentLevel(nationId, pathId);

        // 获取国家服务并通知成员
        if (nationService != null) {
            nationService.nationById(nationId).ifPresent(nation -> {
                String pathName = definitionLoader.getPath(pathId).pathName();
                String message = String.format("§a[升级] §f%s 路径升级至 Lv.%d!", pathName, newLevel);

                for (var member : nation.members()) {
                    Player player = Bukkit.getPlayer(member.playerId());
                    if (player != null) {
                        player.sendMessage(message);
                    }
                }
            });
        }

        // 触发升级完成事件
        currentContext.eventBus().publish(new TerritoryUpgradedEvent(nationId, pathId, newLevel));
    }

    private void registerCommands() {
        // 注册领地升级命令
        JavaPlugin plugin = currentContext.plugin();
        org.bukkit.command.PluginCommand command = plugin.getCommand("upgrade");
        if (command != null) {
            command.setExecutor(new TerritoryUpgradeCommand(this, nationService));
            command.setTabCompleter(new TerritoryUpgradeCommand(this, nationService));
        }
    }

    private void loadExpSourcesConfig() {
        // 从配置加载经验来源加成
        expSources.clear();
        // 默认加成
        expSources.put("territory_claim", 1);
        expSources.put("resource_gathering", 1);
        expSources.put("combat", 1);
        expSources.put("economy", 1);
        expSources.put("technology", 1);
        expSources.put("daily_reward", 1);
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        Map<String, String> serialized = new HashMap<>();
        for (Map.Entry<NationId, UpgradeProgressData> entry : nationProgress.entrySet()) {
            Map<String, String> nationData = UpgradeStateCodec.encodeNation(entry.getValue());
            serialized.putAll(nationData);
        }
        stateStorage.save(serialized);
    }

    private void saveStateAsync() {
        if (stateStorage == null) {
            return;
        }
        Map<String, String> serialized = new HashMap<>();
        for (Map.Entry<NationId, UpgradeProgressData> entry : nationProgress.entrySet()) {
            Map<String, String> nationData = UpgradeStateCodec.encodeNation(entry.getValue());
            serialized.putAll(nationData);
        }
        stateStorage.saveAsync(serialized);
    }

    private void loadState() {
        nationProgress.clear();
        if (stateStorage == null) {
            return;
        }

        Map<String, String> loaded = stateStorage.load();
        Set<String> validPaths = Set.copyOf(getAvailablePaths());
        Map<NationId, UpgradeProgressData> loadedData = UpgradeStateCodec.fromProperties(loaded, validPaths);
        nationProgress.putAll(loadedData);
    }

    // ========== Event Handlers ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (nationService == null) {
            return;
        }

        nationService.nationOf(player.getUniqueId()).ifPresent(nation -> {
            // 审计 A-074: 检查每日奖励冷却，仅在跨天时给予奖励
            long now = System.currentTimeMillis();
            long lastReward = lastDailyReward.getOrDefault(nation.id(), 0L);
            long dayInMillis = 24 * 60 * 60 * 1000L;
            if (now - lastReward >= dayInMillis) {
                addExperience(nation.id(), 100, "daily_reward");
                lastDailyReward.put(nation.id(), now);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (nationService == null) {
            return;
        }

        nationService.nationOf(player.getUniqueId()).ifPresent(nation -> {
            // 根据资源稀有度给予经验
            int exp = getExpForBlock(event.getBlock().getType().getKey().getKey());
            if (exp > 0) {
                addExperience(nation.id(), exp, "resource_gathering");
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // 可选：建造也给予少量经验
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || nationService == null) {
            return;
        }

        nationService.nationOf(killer.getUniqueId()).ifPresent(nation -> {
            // PvP 击杀给予经验
            if (event.getEntity() instanceof Player) {
                addExperience(nation.id(), 50, "combat");
            }
        });
    }

    private int getExpForBlock(String blockKey) {
        // 根据资源稀有度返回经验值
        if (blockKey.contains("diamond") || blockKey.contains("emerald") ||
            blockKey.contains("gold") || blockKey.contains("iron")) {
            return 10;
        } else if (blockKey.contains("coal") || blockKey.contains("copper") ||
                   blockKey.contains("lapis") || blockKey.contains("redstone")) {
            return 5;
        } else if (blockKey.contains("stone")) {
            return 1;
        }
        return 0;
    }
}
