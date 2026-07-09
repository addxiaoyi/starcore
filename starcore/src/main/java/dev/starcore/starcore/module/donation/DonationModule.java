package dev.starcore.starcore.module.donation;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.donation.command.DonationCommand;
import dev.starcore.starcore.module.donation.listener.DonationListener;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 献金模块
 * 玩家可以向国家献金，支持多种献金等级和奖励
 */
public final class DonationModule implements StarCoreModule, DonationService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "donation",
        "献金系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),  // 依赖国家模块和金库模块
        List.of(DonationService.class),
        "Allows players to donate to nations with tier rewards."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private InternalEconomyService economyService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private DonationServiceImpl donationService;
    private DonationListener donationListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().require(TreasuryService.class);
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 从配置读取献金配置
        ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("donation");
        DonationConfig donationConfig = DonationConfig.fromConfig(config);

        // 获取事件服务（可选）
        EventService eventService = context.serviceRegistry().find(EventService.class).orElse(null);

        // 初始化献金服务
        donationService = new DonationServiceImpl(
            plugin,
            nationService,
            treasuryService,
            economyService,
            messages,
            persistenceService,
            context.databaseService(),
            eventService,
            donationConfig
        );

        // 注册服务
        context.serviceRegistry().register(DonationService.class, donationService);

        // 注册命令
        DonationCommand command = new DonationCommand(this, nationService, messages);
        var donateCmd = plugin.getServer().getPluginCommand("donate");
        if (donateCmd != null) {
            donateCmd.setExecutor(command);
            donateCmd.setTabCompleter(command);
        }

        // 注册监听器
        Optional<dev.starcore.starcore.module.nation.NationService> nationSvc =
            context.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class);
        donationListener = new DonationListener(
            donationService,
            nationSvc.orElse(null),
            messages,
            donationConfig
        );
        plugin.getServer().getPluginManager().registerEvents(donationListener, plugin);

        plugin.getLogger().info("STARCORE donation module enabled");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存数据
        if (donationService != null) {
            donationService.shutdown();
        }

        // 清理引用
        donationService = null;
        donationListener = null;

        plugin.getLogger().info("STARCORE donation module disabled");
    }

    // ==================== 代理 DonationService 接口方法 ====================

    @Override
    public DonationResult donate(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId, java.math.BigDecimal amount) {
        return donationService.donate(playerId, nationId, amount);
    }

    @Override
    public DonationResult donate(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId, java.math.BigDecimal amount, String message) {
        return donationService.donate(playerId, nationId, amount, message);
    }

    @Override
    public List<DonationRecord> getPlayerDonations(java.util.UUID playerId) {
        return donationService.getPlayerDonations(playerId);
    }

    @Override
    public List<DonationRecord> getPlayerDonations(java.util.UUID playerId, int limit, int offset) {
        return donationService.getPlayerDonations(playerId, limit, offset);
    }

    @Override
    public List<DonationRecord> getNationDonations(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return donationService.getNationDonations(nationId);
    }

    @Override
    public List<DonationRecord> getNationDonations(dev.starcore.starcore.module.nation.model.NationId nationId, int limit, int offset) {
        return donationService.getNationDonations(nationId, limit, offset);
    }

    @Override
    public java.math.BigDecimal getTotalDonations(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId) {
        return donationService.getTotalDonations(playerId, nationId);
    }

    @Override
    public java.math.BigDecimal getTotalDonations(java.util.UUID playerId) {
        return donationService.getTotalDonations(playerId);
    }

    @Override
    public java.math.BigDecimal getTotalDonations(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return donationService.getTotalDonations(nationId);
    }

    @Override
    public List<DonationRankingEntry> getDonationRanking(dev.starcore.starcore.module.nation.model.NationId nationId, int limit) {
        return donationService.getDonationRanking(nationId, limit);
    }

    @Override
    public java.util.Optional<java.lang.Integer> getPlayerRanking(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId) {
        return donationService.getPlayerRanking(playerId, nationId);
    }

    @Override
    public DonationTier getPlayerTier(java.util.UUID playerId) {
        return donationService.getPlayerTier(playerId);
    }

    @Override
    public DonationTier getPlayerTier(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId) {
        return donationService.getPlayerTier(playerId, nationId);
    }

    @Override
    public java.util.Map<java.lang.String, DonationTier> getAllTiers() {
        return donationService.getAllTiers();
    }

    @Override
    public java.util.Optional<DonationTier> getTier(java.lang.String tierId) {
        return donationService.getTier(tierId);
    }

    @Override
    public DonationTier getTierForAmount(java.math.BigDecimal amount) {
        return donationService.getTierForAmount(amount);
    }

    @Override
    public java.math.BigDecimal getAmountNeededForTier(java.util.UUID playerId, java.lang.String tierId) {
        return donationService.getAmountNeededForTier(playerId, tierId);
    }

    @Override
    public List<DonationReward> getAvailableRewards(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId) {
        return donationService.getAvailableRewards(playerId, nationId);
    }

    @Override
    public boolean claimReward(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId, java.lang.String rewardId) {
        return donationService.claimReward(playerId, nationId, rewardId);
    }

    @Override
    public boolean isRewardClaimable(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId, java.lang.String rewardId) {
        return donationService.isRewardClaimable(playerId, nationId, rewardId);
    }

    @Override
    public List<ClaimedReward> getClaimedRewards(java.util.UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId) {
        return donationService.getClaimedRewards(playerId, nationId);
    }

    @Override
    public boolean deleteDonation(java.util.UUID donationId) {
        return donationService.deleteDonation(donationId);
    }

    @Override
    public java.lang.String summary() {
        return donationService.summary();
    }

    public DonationService getDonationService() {
        return donationService;
    }
}
