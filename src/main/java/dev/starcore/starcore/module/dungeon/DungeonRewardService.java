package dev.starcore.starcore.module.dungeon;

import dev.starcore.starcore.foundation.util.RandomProvider;
import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.math.BigDecimal;

/**
 * 副本奖励服务
 * 负责分发副本奖励
 */
public class DungeonRewardService {
    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final Logger logger;

    // 奖励配置
    private final Map<String, RewardPool> rewardPools = new ConcurrentHashMap<>();

    public DungeonRewardService(JavaPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.logger = plugin.getLogger();
        loadRewardPools();
    }

    /**
     * 加载奖励池配置
     */
    private void loadRewardPools() {
        // 加载配置文件中的奖励池
        var config = plugin.getConfig().getConfigurationSection("dungeon.reward-pools");
        if (config == null) {
            // 使用默认奖励池
            createDefaultRewardPools();
            return;
        }

        for (String poolId : config.getKeys(false)) {
            var section = config.getConfigurationSection(poolId);
            if (section == null) continue;

            int weight = section.getInt("weight", 10);
            List<?> itemList = section.getList("items", List.of());
            List<RewardItem> items = new ArrayList<>();

            for (Object item : itemList) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> itemMap = (Map<String, Object>) item;
                    String material = (String) itemMap.get("material");
                    if (material == null || material.isBlank()) continue;
                    try {
                        int amount = 1;
                        Object amountObj = itemMap.getOrDefault("amount", 1);
                        if (amountObj instanceof Number) {
                            amount = ((Number) amountObj).intValue();
                        }
                        Material mat = Material.valueOf(material.toUpperCase(Locale.ROOT));
                        items.add(new RewardItem(mat, Math.max(1, amount), 1.0));
                    } catch (IllegalArgumentException e) {
                        logger.warning("无效的物品材质: " + material + ", 跳过该奖励");
                    }
                }
            }

            rewardPools.put(poolId, new RewardPool(poolId, weight, items));
        }
    }

    /**
     * 创建默认奖励池
     */
    private void createDefaultRewardPools() {
        // 通用奖励
        List<RewardItem> commonItems = List.of(
            new RewardItem(Material.IRON_INGOT, 10, 1.0),
            new RewardItem(Material.GOLD_INGOT, 5, 0.8),
            new RewardItem(Material.COAL, 20, 0.9)
        );
        rewardPools.put("common", new RewardPool("common", 50, commonItems));

        // 稀有奖励
        List<RewardItem> uncommonItems = List.of(
            new RewardItem(Material.DIAMOND, 3, 0.6),
            new RewardItem(Material.EMERALD, 10, 0.7),
            new RewardItem(Material.EXPERIENCE_BOTTLE, 16, 1.0)
        );
        rewardPools.put("uncommon", new RewardPool("uncommon", 30, uncommonItems));

        // 稀有奖励
        List<RewardItem> rareItems = List.of(
            new RewardItem(Material.NETHERITE_INGOT, 1, 0.3),
            new RewardItem(Material.ENCHANTED_GOLDEN_APPLE, 2, 0.4)
        );
        rewardPools.put("rare", new RewardPool("rare", 15, rareItems));

        // 传说奖励
        List<RewardItem> legendaryItems = List.of(
            new RewardItem(Material.NETHER_STAR, 1, 0.1),
            new RewardItem(Material.ELYTRA, 1, 0.2)
        );
        rewardPools.put("legendary", new RewardPool("legendary", 5, legendaryItems));
    }

    /**
     * 分发奖励
     */
    public void distributeRewards(Player player, DungeonDefinition definition, int deaths) {
        DungeonRewards rewards = definition.rewards();
        if (rewards == null) return;

        double multiplier = rewards.completionBonusMultiplier();
        double deathPenalty = 1.0 - (deaths * 0.1); // 每次死亡减少10%
        deathPenalty = Math.max(0.5, deathPenalty); // 最低50%

        // 计算最终倍数
        double finalMultiplier = multiplier * deathPenalty;

        // 分发经验
        int experience = (int) (rewards.experience() * finalMultiplier);
        player.giveExp(experience);

        // 分发金币
        BigDecimal goldAmount = BigDecimal.valueOf((int) (rewards.gold() * finalMultiplier));
        economyService.deposit(player.getUniqueId(), goldAmount);

        // 分发物品奖励
        for (DungeonRewards.RewardItem item : rewards.items()) {
            int amount = (int) (item.amount() * finalMultiplier);
            try {
                Material material = Material.valueOf(item.material().toUpperCase(Locale.ROOT));
                ItemStack itemStack = new ItemStack(material, amount);

                // 检查玩家背包空间
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(itemStack);
                if (!overflow.isEmpty()) {
                    // 背包满了，掉落在地上
                    player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                }
            } catch (Exception e) {
                logger.warning("无法创建物品: " + item.material());
            }
        }

        // 随机抽取额外奖励
        drawRandomRewards(player, definition.difficulty());

        // 发送消息
        int goldInt = goldAmount.intValue();
        sendRewardMessage(player, definition.name(), experience, goldInt);
    }

    /**
     * 抽取随机奖励
     */
    private void drawRandomRewards(Player player, DungeonDifficulty difficulty) {
        // 根据难度增加额外奖励概率
        int bonusRolls = switch (difficulty) {
            case EASY -> 0;
            case NORMAL -> 1;
            case HARD -> 2;
            case NIGHTMARE -> 3;
        };

        for (int i = 0; i < bonusRolls; i++) {
            // 从奖励池抽取（根据难度选择，永不返回 null）
            RewardPool pool = selectRandomPool(difficulty);
            RewardItem item = pool.selectRandomItem();
            if (item == null) continue;

            try {
                Material material = item.material();
                ItemStack itemStack = new ItemStack(material, item.amount());

                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(itemStack);
                if (!overflow.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                }

                // 通知玩家获得额外奖励
                player.sendMessage("§6额外奖励: §e" + itemStack.getAmount() + "x " + formatMaterialName(material));
            } catch (Exception e) {
                // 忽略
            }
        }
    }

    /**
     * 选择随机奖励池（根据难度）
     * 永远不会返回 null
     */
    private RewardPool selectRandomPool(DungeonDifficulty difficulty) {
        // 确保奖励池已初始化
        if (rewardPools.isEmpty()) {
            createDefaultRewardPools();
        }

        // 如果只有一个池，直接返回
        if (rewardPools.size() == 1) {
            return rewardPools.values().iterator().next();
        }

        // 根据难度选择首选奖励池
        String preferredPoolId = switch (difficulty) {
            case EASY -> "common";
            case NORMAL -> "uncommon";
            case HARD -> "rare";
            case NIGHTMARE -> "legendary";
        };

        // 70% 概率选择符合难度的池，30% 概率随机
        if (rewardPools.containsKey(preferredPoolId) && ThreadLocalRandom.current().nextDouble() < 0.7) {
            return rewardPools.get(preferredPoolId);
        }

        // 加权随机选择
        int totalWeight = rewardPools.values().stream()
            .mapToInt(RewardPool::weight)
            .sum();

        if (totalWeight <= 0) {
            return rewardPools.values().iterator().next();
        }

        int random = RandomProvider.nextInt(totalWeight);
        int cumulative = 0;

        for (RewardPool pool : rewardPools.values()) {
            cumulative += pool.weight();
            if (random < cumulative) {
                return pool;
            }
        }

        // 兜底：返回第一个池（永不返回 null）
        return rewardPools.values().iterator().next();
    }

    /**
     * 发送奖励消息
     */
    private void sendRewardMessage(Player player, String dungeonName, int experience, int gold) {
        // 分隔
        player.sendMessage("§6========== 副本奖励 ==========");
        player.sendMessage("§a副本: §e" + dungeonName);
        player.sendMessage("§a经验: §e+" + experience);
        player.sendMessage("§a金币: §e+" + gold);
        player.sendMessage("§6==============================");
        // 分隔
    }

    /**
     * 格式化物品名称
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        for (String word : name.split(" ")) {
            result.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1))
                .append(" ");
        }
        return result.toString().trim();
    }

    /**
     * 奖励物品
     */
    public record RewardItem(Material material, int amount, double dropChance) {
        public static RewardItem fromConfig(String material, int amount) {
            if (material == null || material.isBlank()) return null;
            try {
                return new RewardItem(Material.valueOf(material.toUpperCase(Locale.ROOT)), Math.max(1, amount), 1.0);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /**
         * 安全创建 RewardItem，如果材质无效则返回 null
         */
        public static RewardItem safeCreate(String material, int amount, double chance) {
            if (material == null || material.isBlank()) return null;
            try {
                Material mat = Material.valueOf(material.toUpperCase(Locale.ROOT));
                return new RewardItem(mat, Math.max(1, amount), Math.max(0.0, Math.min(1.0, chance)));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * 奖励池
     */
    public record RewardPool(String id, int weight, List<RewardItem> items) {
        public RewardItem selectRandomItem() {
            // 安全检查：处理 null 和空列表
            if (items == null || items.isEmpty()) return null;
            // 过滤掉 null 元素
            List<RewardItem> validItems = items.stream()
                .filter(Objects::nonNull)
                .toList();
            if (validItems.isEmpty()) return null;
            return validItems.get(RandomProvider.nextInt(validItems.size()));
        }
    }
}
