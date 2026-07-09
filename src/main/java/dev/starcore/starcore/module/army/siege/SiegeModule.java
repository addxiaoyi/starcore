package dev.starcore.starcore.module.army.siege;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.siege.command.SiegeCommand;
import dev.starcore.starcore.module.army.siege.listener.SiegeListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;

/**
 * 攻城器械模块
 * 提供攻城器械的创建、管理、部署、攻击城墙等功能
 */
public final class SiegeModule implements StarCoreModule, SiegeService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "siege",
        "攻城器械系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(SiegeService.class),
        "Provides siege weapons creation, deployment, and wall assault capabilities."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private SiegeServiceImpl siegeServiceImpl;
    private SiegeCommand siegeCommand;
    private SiegeListener siegeListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().require(TreasuryService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 从配置读取攻城器械配置
        ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("siege");
        SiegeConfig siegeConfig = SiegeConfig.fromConfig(config);

        // 读取各类型配置
        Map<dev.starcore.starcore.module.army.siege.model.SiegeType, SiegeTypeConfig> typeConfigs = new EnumMap<>(dev.starcore.starcore.module.army.siege.model.SiegeType.class);
        if (config != null) {
            ConfigurationSection typeSection = config.getConfigurationSection("max-per-type");
            ConfigurationSection rangeSection = config.getConfigurationSection("effective-range");
            ConfigurationSection damageSection = config.getConfigurationSection("base-damage");
            ConfigurationSection siegeDmgSection = config.getConfigurationSection("siege-damage-multiplier");
            ConfigurationSection costSection = config.getConfigurationSection("construction-cost");
            ConfigurationSection maintSection = config.getConfigurationSection("maintenance-cost");

            for (dev.starcore.starcore.module.army.siege.model.SiegeType type : dev.starcore.starcore.module.army.siege.model.SiegeType.values()) {
                SiegeTypeConfig typeConfig = new SiegeTypeConfig(
                    typeSection != null ? typeSection.getInt(type.key(), 3) : 3,
                    rangeSection != null ? rangeSection.getInt(type.key(), type.effectiveRange()) : type.effectiveRange(),
                    damageSection != null ? damageSection.getDouble(type.key(), type.baseDamage()) : type.baseDamage(),
                    siegeDmgSection != null ? siegeDmgSection.getDouble(type.key(), type.siegeDamageMultiplier()) : type.siegeDamageMultiplier(),
                    costSection != null ? costSection.getInt(type.key(), type.constructionCost()) : type.constructionCost(),
                    maintSection != null ? maintSection.getInt(type.key(), type.maintenanceCostPerHour()) : type.maintenanceCostPerHour()
                );
                typeConfigs.put(type, typeConfig);
            }
        } else {
            // 默认配置
            for (dev.starcore.starcore.module.army.siege.model.SiegeType type : dev.starcore.starcore.module.army.siege.model.SiegeType.values()) {
                typeConfigs.put(type, SiegeTypeConfig.defaults());
            }
        }

        // 初始化攻城器械服务
        siegeServiceImpl = new SiegeServiceImpl(
            plugin,
            nationService,
            treasuryService,
            siegeConfig,
            typeConfigs,
            messages,
            persistenceService
        );

        // 注册命令
        siegeCommand = new SiegeCommand(siegeServiceImpl, nationService, messages);
        var siegeCmd = plugin.getServer().getPluginCommand("siege");
        if (siegeCmd != null) {
            siegeCmd.setExecutor(siegeCommand);
            siegeCmd.setTabCompleter(siegeCommand);
        }

        // 注册监听器
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        siegeListener = new SiegeListener(siegeServiceImpl, nationService, warService, messages);
        plugin.getServer().getPluginManager().registerEvents(siegeListener, plugin);

        plugin.getLogger().info("Siege module enabled: " + METADATA.displayName());
    }

    @Override
    public void disable(StarCoreContext context) {
        if (siegeServiceImpl != null) {
            siegeServiceImpl.shutdown();
            siegeServiceImpl = null;
        }

        siegeCommand = null;
        siegeListener = null;

        context.plugin().getLogger().info("Siege module disabled");
    }

    // ==================== SiegeService Implementation ====================

    @Override
    public SiegeConfig getConfig() {
        return siegeServiceImpl != null ? siegeServiceImpl.getConfig() : SiegeConfig.defaults();
    }

    @Override
    public SiegeTypeConfig getTypeConfig(dev.starcore.starcore.module.army.siege.model.SiegeType type) {
        return siegeServiceImpl != null ? siegeServiceImpl.getTypeConfig(type) : SiegeTypeConfig.defaults();
    }

    @Override
    public dev.starcore.starcore.module.army.siege.model.SiegeUnit createSiege(UUID nationId, dev.starcore.starcore.module.army.siege.model.SiegeType type, int crewSize, org.bukkit.Location location) {
        return siegeServiceImpl.createSiege(nationId, type, crewSize, location);
    }

    @Override
    public void deploySiege(UUID siegeId, org.bukkit.Location targetLocation) {
        siegeServiceImpl.deploySiege(siegeId, targetLocation);
    }

    @Override
    public dev.starcore.starcore.module.army.siege.model.SiegeResult startSiege(UUID siegeId, UUID targetWallId) {
        return siegeServiceImpl.startSiege(siegeId, targetWallId);
    }

    @Override
    public double fireSiege(UUID siegeId, org.bukkit.Location targetLocation) {
        return siegeServiceImpl.fireSiege(siegeId, targetLocation);
    }

    @Override
    public void repairSiege(UUID siegeId, double repairAmount) {
        siegeServiceImpl.repairSiege(siegeId, repairAmount);
    }

    @Override
    public void disbandSiege(UUID siegeId) {
        siegeServiceImpl.disbandSiege(siegeId);
    }

    @Override
    public Optional<dev.starcore.starcore.module.army.siege.model.SiegeUnit> getSiege(UUID siegeId) {
        return siegeServiceImpl.getSiege(siegeId);
    }

    @Override
    public List<dev.starcore.starcore.module.army.siege.model.SiegeUnit> getNationSieges(UUID nationId) {
        return siegeServiceImpl.getNationSieges(nationId);
    }

    @Override
    public List<dev.starcore.starcore.module.army.siege.model.SiegeUnit> getSiegesNear(org.bukkit.Location location, double radius) {
        return siegeServiceImpl.getSiegesNear(location, radius);
    }

    @Override
    public dev.starcore.starcore.module.army.siege.model.WallData createWall(org.bukkit.Location location, UUID nationId, dev.starcore.starcore.module.army.siege.model.WallType type) {
        return siegeServiceImpl.createWall(location, nationId, type);
    }

    @Override
    public Optional<dev.starcore.starcore.module.army.siege.model.WallData> getWall(UUID wallId) {
        return siegeServiceImpl.getWall(wallId);
    }

    @Override
    public Optional<dev.starcore.starcore.module.army.siege.model.WallData> getNearestWall(org.bukkit.Location location, double maxDistance) {
        return siegeServiceImpl.getNearestWall(location, maxDistance);
    }

    @Override
    public void repairWall(UUID wallId, double repairAmount) {
        siegeServiceImpl.repairWall(wallId, repairAmount);
    }

    @Override
    public boolean canDeploy(UUID nationId) {
        return siegeServiceImpl.canDeploy(nationId);
    }

    @Override
    public long getDeploymentCooldownRemaining(UUID nationId) {
        return siegeServiceImpl.getDeploymentCooldownRemaining(nationId);
    }

    @Override
    public void reloadSiege(UUID siegeId, int amount) {
        siegeServiceImpl.reloadSiege(siegeId, amount);
    }

    @Override
    public void moveSiege(UUID siegeId, org.bukkit.Location targetLocation) {
        siegeServiceImpl.moveSiege(siegeId, targetLocation);
    }

    @Override
    public void retreatSiege(UUID siegeId) {
        siegeServiceImpl.retreatSiege(siegeId);
    }

    @Override
    public void saveAll() {
        if (siegeServiceImpl != null) {
            siegeServiceImpl.saveAll();
        }
    }

    @Override
    public void shutdown() {
        if (siegeServiceImpl != null) {
            siegeServiceImpl.shutdown();
        }
    }

    /**
     * 获取攻城器械服务实现
     */
    public SiegeServiceImpl getSiegeServiceImpl() {
        return siegeServiceImpl;
    }
}