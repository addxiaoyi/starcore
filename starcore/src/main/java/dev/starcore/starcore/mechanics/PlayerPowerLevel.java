package dev.starcore.starcore.mechanics;

import org.bukkit.entity.Player;

/**
 * 玩家实力评估
 * 综合装备、等级、技能、成就等因素评估玩家实力
 */
public class PlayerPowerLevel {

    private final Player player;
    private double equipmentScore;
    private double levelScore;
    private double achievementScore;
    private double wealthScore;

    public PlayerPowerLevel(Player player) {
        this.player = player;
        calculate();
    }

    /**
     * 计算玩家综合实力
     */
    public void calculate() {
        this.equipmentScore = calculateEquipmentScore();
        this.levelScore = calculateLevelScore();
        this.achievementScore = calculateAchievementScore();
        this.wealthScore = calculateWealthScore();
    }

    /**
     * 计算装备分数（0-100）
     */
    private double calculateEquipmentScore() {
        double score = 0;

        // 武器评分
        if (player.getInventory().getItemInMainHand() != null) {
            score += evaluateItem(player.getInventory().getItemInMainHand()) * 20;
        }

        // 护甲评分
        if (player.getInventory().getHelmet() != null) {
            score += evaluateItem(player.getInventory().getHelmet()) * 15;
        }
        if (player.getInventory().getChestplate() != null) {
            score += evaluateItem(player.getInventory().getChestplate()) * 25;
        }
        if (player.getInventory().getLeggings() != null) {
            score += evaluateItem(player.getInventory().getLeggings()) * 20;
        }
        if (player.getInventory().getBoots() != null) {
            score += evaluateItem(player.getInventory().getBoots()) * 10;
        }

        // 副手评分
        if (player.getInventory().getItemInOffHand() != null) {
            score += evaluateItem(player.getInventory().getItemInOffHand()) * 10;
        }

        return Math.min(score, 100);
    }

    /**
     * 评估物品质量（0-1）
     */
    private double evaluateItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        double quality = 0;

        // 基础材料分数
        String material = item.getType().name();
        if (material.contains("NETHERITE")) quality += 1.0;
        else if (material.contains("DIAMOND")) quality += 0.8;
        else if (material.contains("IRON")) quality += 0.5;
        else if (material.contains("GOLD")) quality += 0.4;
        else if (material.contains("STONE") || material.contains("CHAINMAIL")) quality += 0.3;
        else if (material.contains("LEATHER") || material.contains("WOOD")) quality += 0.1;

        // 附魔加成
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            int enchantCount = item.getEnchantments().size();
            int enchantLevel = item.getEnchantments().values().stream()
                .mapToInt(Integer::intValue).sum();
            quality += Math.min(enchantCount * 0.05 + enchantLevel * 0.02, 0.3);
        }

        return Math.min(quality, 1.0);
    }

    /**
     * 计算等级分数（0-100）
     */
    private double calculateLevelScore() {
        int level = player.getLevel();
        // 对数增长，防止高等级玩家过于强大
        return Math.min(Math.log(level + 1) * 20, 100);
    }

    /**
     * 计算成就分数（0-100）
     */
    private double calculateAchievementScore() {
        // 基于玩家的统计数据
        int mobKills = player.getStatistic(org.bukkit.Statistic.MOB_KILLS);
        int playerKills = player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(org.bukkit.Statistic.DEATHS);

        double kdr = deaths > 0 ? (double) (mobKills + playerKills * 5) / deaths : mobKills + playerKills * 5;
        return Math.min(Math.log(kdr + 1) * 15, 100);
    }

    /**
     * 计算财富分数（0-100）
     */
    private double calculateWealthScore() {
        // 这里需要与经济系统集成
        // 暂时基于背包中的贵重物品
        double wealth = 0;

        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            String material = item.getType().name();
            int amount = item.getAmount();

            if (material.contains("DIAMOND")) wealth += amount * 100;
            else if (material.contains("EMERALD")) wealth += amount * 150;
            else if (material.contains("NETHERITE")) wealth += amount * 200;
            else if (material.contains("GOLD")) wealth += amount * 50;
        }

        return Math.min(Math.log(wealth + 1) * 10, 100);
    }

    /**
     * 获取综合实力等级（0-100）
     */
    public double getTotalPowerLevel() {
        return (equipmentScore * 0.35 + levelScore * 0.25 +
                achievementScore * 0.25 + wealthScore * 0.15);
    }

    /**
     * 获取实力等级描述
     */
    public String getPowerTier() {
        double power = getTotalPowerLevel();

        if (power >= 90) return "传奇";
        if (power >= 75) return "精英";
        if (power >= 60) return "老练";
        if (power >= 40) return "熟练";
        if (power >= 20) return "普通";
        return "萌新";
    }

    public double getEquipmentScore() { return equipmentScore; }
    public double getLevelScore() { return levelScore; }
    public double getAchievementScore() { return achievementScore; }
    public double getWealthScore() { return wealthScore; }
}
