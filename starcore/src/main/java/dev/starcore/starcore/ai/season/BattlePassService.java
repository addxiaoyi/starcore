package dev.starcore.starcore.ai.season;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 赛季通行证服务
 */
public final class BattlePassService {
    // 颜色代码常量
    private static final String C_GOLD = "§6";
    private static final String C_BOLD = "§l";
    private static final String C_YELLOW = "§e";
    private static final String C_GREEN = "§a";
    private static final String C_AQUA = "§b";
    private static final String C_WHITE = "§f";

    private final Plugin plugin;
    // 当前赛季
    private Season currentSeason;

    // 玩家通行证进度 UUID -> BattlePassProgress
    private final ConcurrentHashMap<UUID, BattlePassProgress> playerProgress =
        new ConcurrentHashMap<>();

    public BattlePassService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 开始新赛季
     */
    public void startNewSeason(String seasonName, LocalDate startDate, LocalDate endDate) {
        this.currentSeason = new Season(
            UUID.randomUUID(),
            seasonName,
            startDate,
            endDate
        );

        // 清空旧赛季数据
        playerProgress.clear();
    }

    /**
     * 获取或创建玩家进度
     */
    public BattlePassProgress getOrCreateProgress(UUID playerId) {
        return playerProgress.computeIfAbsent(playerId, id ->
            new BattlePassProgress(id, currentSeason.seasonId())
        );
    }

    /**
     * 增加经验
     */
    public void addExperience(UUID playerId, int exp, String source) {
        BattlePassProgress progress = getOrCreateProgress(playerId);
        progress.addExperience(exp);

        // 检查升级
        while (progress.canLevelUp()) {
            int oldLevel = progress.getLevel();
            progress.levelUp();
            int newLevel = progress.getLevel();

            // 发放奖励
            giveRewardsForLevel(playerId, newLevel, progress.isPremium());
        }
    }

    /**
     * 购买付费通行证
     */
    public boolean purchasePremiumPass(UUID playerId) {
        BattlePassProgress progress = getOrCreateProgress(playerId);

        if (progress.isPremium()) {
            return false;
        }

        progress.setPremium(true);

        // 发放已解锁等级的付费奖励
        for (int level = 1; level <= progress.getLevel(); level++) {
            List<BattlePassReward> premiumRewards = getRewardsForLevel(level, true)
                .stream()
                .filter(BattlePassReward::premiumOnly)
                .toList();

            for (BattlePassReward reward : premiumRewards) {
                giveReward(playerId, reward);
            }
        }

        return true;
    }

    /**
     * 获取等级奖励
     */
    public List<BattlePassReward> getRewardsForLevel(int level, boolean premium) {
        List<BattlePassReward> rewards = new ArrayList<>();

        // 免费奖励
        rewards.add(new BattlePassReward(
            "coins",
            level * 100,
            false
        ));

        // 付费奖励
        if (premium && level % 5 == 0) {
            rewards.add(new BattlePassReward(
                "special_kit",
                1,
                true
            ));
        }

        return rewards;
    }

    /**
     * 检查赛季是否结束
     */
    public boolean isSeasonEnded() {
        if (currentSeason == null) {
            return true;
        }
        return LocalDate.now().isAfter(currentSeason.endDate());
    }

    /**
     * 获取当前赛季
     */
    public Season getCurrentSeason() {
        return currentSeason;
    }

    /**
     * 发放等级奖励
     */
    private void giveRewardsForLevel(UUID playerId, int level, boolean premium) {
        List<BattlePassReward> rewards = getRewardsForLevel(level, premium);

        for (BattlePassReward reward : rewards) {
            // 如果是付费专属奖励，但玩家没有付费通行证，跳过
            if (reward.premiumOnly() && !premium) {
                continue;
            }

            giveReward(playerId, reward);
        }

        // 通知玩家 - 需要在主线程执行
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(C_GOLD + C_BOLD + "★ 战斗通行证升级！");
                player.sendMessage(C_YELLOW + "等级: " + C_GREEN + level);
                player.sendMessage(C_YELLOW + "奖励已发放");
            }
        });
    }

    /**
     * 给予单个奖励 - 需要在主线程执行所有玩家相关操作
     */
    private void giveReward(UUID playerId, BattlePassReward reward) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        // 所有玩家操作都在主线程执行
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            switch (reward.type().toLowerCase()) {
                case "coins", "money" -> {
                    giveCoins(player, reward.amount());
                }
                case "special_kit" -> {
                    // 给予特殊套装
                    player.sendMessage(C_GREEN + "获得特殊套装！");
                    // 这里可以调用套装系统
                }
                case "item" -> {
                    // 给予物品
                    player.sendMessage(C_GREEN + "获得物品奖励！");
                }
                case "exp", "experience" -> {
                    player.giveExp(reward.amount());
                    player.sendMessage(C_AQUA + "+ " + reward.amount() + " 经验");
                }
                default -> {
                    Bukkit.getLogger().warning("Unknown reward type: " + reward.type());
                }
            }
        });
    }

    /**
     * 给予金币
     */
    private void giveCoins(Player player, int amount) {
        // 尝试使用 Vault
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            try {
                net.milkbowl.vault.economy.Economy economy = getVaultEconomy();
                if (economy != null) {
                    economy.depositPlayer(player, amount);
                    player.sendMessage(C_GOLD + "+ " + amount + " 金币");
                    return;
                }
            } catch (Exception e) {
                // Vault 未正确配置
            }
        }

        // 使用内部经济系统或直接提示
        player.sendMessage(C_GOLD + "+ " + amount + " 金币");
    }

    /**
     * 获取 Vault 经济系统
     */
    private net.milkbowl.vault.economy.Economy getVaultEconomy() {
        try {
            org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (rsp != null) {
                return rsp.getProvider();
            }
        } catch (Exception e) {
            // Vault 未安装
        }
        return null;
    }

    /**
     * 赛季
     */
    public record Season(
        UUID seasonId,
        String name,
        LocalDate startDate,
        LocalDate endDate
    ) {
        public int getDurationDays() {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        }

        public int getRemainingDays() {
            return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        }
    }

    /**
     * 通行证进度
     */
    public static class BattlePassProgress {
        private final UUID playerId;
        private final UUID seasonId;
        private int level;
        private int experience;
        private boolean premium;

        private static final int EXP_PER_LEVEL = 1000;

        public BattlePassProgress(UUID playerId, UUID seasonId) {
            this.playerId = playerId;
            this.seasonId = seasonId;
            this.level = 1;
            this.experience = 0;
            this.premium = false;
        }

        public void addExperience(int exp) {
            this.experience += exp;
        }

        public boolean canLevelUp() {
            return experience >= EXP_PER_LEVEL;
        }

        public void levelUp() {
            if (canLevelUp()) {
                level++;
                experience -= EXP_PER_LEVEL;
            }
        }

        public int getExpForNextLevel() {
            return EXP_PER_LEVEL - experience;
        }

        public double getProgressPercent() {
            return (double) experience / EXP_PER_LEVEL * 100;
        }

        // Getters and Setters
        public UUID getPlayerId() { return playerId; }
        public UUID getSeasonId() { return seasonId; }
        public int getLevel() { return level; }
        public int getExperience() { return experience; }
        public boolean isPremium() { return premium; }
        public void setPremium(boolean premium) { this.premium = premium; }
    }

    /**
     * 通行证奖励
     */
    public record BattlePassReward(
        String type,
        int amount,
        boolean premiumOnly
    ) {}
}
