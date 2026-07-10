package dev.starcore.starcore.module.technology.listener;

import dev.starcore.starcore.foundation.util.RandomProvider;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.technology.TechnologyModule;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event listener that applies technology bonuses to game events.
 * Handles: XP gains, resource yields, crafting bonuses, growth bonuses, fishing bonuses,
 * combat bonuses (damage dealt and damage taken), and harvest bonuses.
 */
public final class TechnologyEventListener implements Listener {
    private final TechnologyModule technologyModule;
    private final NationService nationService;

    // Cache for growth bonus ticks per location (to avoid excessive checks)
    private final Map<String, Long> growthBonusCooldowns = new ConcurrentHashMap<>();
    private static final long GROWTH_CHECK_COOLDOWN_MS = 1000; // 1 second cooldown per location

    // Random for probabilistic bonuses

    public TechnologyEventListener(TechnologyModule technologyModule, NationService nationService) {
        this.technologyModule = technologyModule;
        this.nationService = nationService;
    }

    /**
     * Applies XP bonus from technology effects.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            return;
        }

        double xpModifier = technologyModule.getTotalModifier(nationIdOpt.get(), "xp_gain");
        if (xpModifier > 0) {
            int originalAmount = event.getAmount();
            int newAmount = (int) Math.ceil(originalAmount * (1.0 + xpModifier));
            event.setAmount(newAmount);
        }
    }

    /**
     * Applies resource yield bonus from technology effects.
     * Mining speed is handled via attribute modifiers in TechnologyEffectRegistry.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            return;
        }

        double resourceYield = technologyModule.getTotalModifier(nationIdOpt.get(), "resource_yield");
        if (resourceYield > 0 && event.isDropItems()) {
            applyResourceYieldBonus(event.getBlock().getType(), event.getBlock().getLocation(), player, resourceYield);
        }

        // Apply mining XP bonus
        double miningXpBonus = technologyModule.getTotalModifier(nationIdOpt.get(), "mining_xp");
        if (miningXpBonus > 0 && isOreBlock(event.getBlock().getType())) {
            applyMiningXpBonus(player, event.getBlock(), miningXpBonus);
        }
    }

    /**
     * Checks if a material is an ore block that yields mining XP.
     */
    private boolean isOreBlock(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 NETHER_GOLD_ORE, NETHER_QUARTZ_ORE,
                 ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    /**
     * Applies bonus mining XP to the player based on technology bonus.
     */
    private void applyMiningXpBonus(Player player, Block block, double miningXpBonus) {
        // Calculate bonus XP based on block type and bonus value
        int baseXp = getBlockBaseXp(block.getType());
        if (baseXp <= 0) return;

        // Apply mining XP bonus multiplier
        int bonusXp = (int) Math.ceil(baseXp * miningXpBonus);
        if (bonusXp > 0) {
            player.giveExp(bonusXp);
        }
    }

    /**
     * Gets the base XP value for breaking a specific block type.
     */
    private int getBlockBaseXp(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 2;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 3;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 4;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 5;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 4;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> 5;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 7;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 8;
            case NETHER_GOLD_ORE -> 4;
            case NETHER_QUARTZ_ORE -> 5;
            case ANCIENT_DEBRIS -> 15;
            default -> 0;
        };
    }

    /**
     * Applies production bonus to crafting.
     * Combines production_multiplier and production_speed effects.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            return;
        }

        // Apply production multiplier (e.g., 1.5 = 50% bonus)
        double productionMultiplier = technologyModule.getTotalModifier(nationIdOpt.get(), "production_multiplier");
        // Apply production speed bonus (e.g., 0.3 = 30% bonus as additive)
        double productionSpeed = technologyModule.getTotalModifier(nationIdOpt.get(), "production_speed");

        // Combine both bonuses: total bonus = (multiplier - 1) + speed
        double totalBonus = (productionMultiplier > 0 ? productionMultiplier - 1.0 : 0) + productionSpeed;

        if (totalBonus > 0) {
            ItemStack result = event.getRecipe().getResult().clone();
            int bonusAmount = (int) Math.ceil(result.getAmount() * totalBonus);
            if (bonusAmount > 0) {
                result.setAmount(result.getAmount() + bonusAmount);
                event.setCurrentItem(result);
            }
        }
    }

    /**
     * Applies farming/growth bonuses to crop growth.
     * When a crop grows, there's a chance to spawn a bonus crop nearby.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        String key = location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();

        // Check cooldown to avoid spam
        long now = System.currentTimeMillis();
        if (growthBonusCooldowns.containsKey(key) && now - growthBonusCooldowns.get(key) < GROWTH_CHECK_COOLDOWN_MS) {
            return;
        }
        growthBonusCooldowns.put(key, now);

        // Find a farmer player nearby (within 16 blocks)
        Player farmer = null;
        for (Player player : block.getWorld().getPlayers()) {
            if (player.getWorld().equals(block.getWorld()) &&
                player.getLocation().distance(location) <= 16) {
                Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
                if (nationIdOpt.isPresent()) {
                    double growthBonus = technologyModule.getTotalModifier(nationIdOpt.get(), "growth_bonus");
                    if (growthBonus > 0) {
                        farmer = player;
                        // Apply growth bonus: spawn extra crop
                        applyGrowthBonus(block, farmer, growthBonus);
                    }
                    double cropGrowth = technologyModule.getTotalModifier(nationIdOpt.get(), "crop_growth");
                    if (cropGrowth > 0) {
                        // crop_growth affects growth speed (handled by chance-based growth acceleration)
                        applyCropGrowthSpeedup(block, cropGrowth);
                    }
                }
            }
        }
    }

    /**
     * Applies growth bonus by spawning bonus crops adjacent to the grown crop.
     */
    private void applyGrowthBonus(Block grownBlock, Player farmer, double growthBonus) {
        if (grownBlock == null || farmer == null) return;

        Material type = grownBlock.getType();
        if (!isCropBlock(type)) return;

        // 10% chance per 0.1 bonus to spawn a bonus crop (max 50%)
        double chance = Math.min(growthBonus * 0.1, 0.5);
        if (RandomProvider.nextDouble() > chance) return;

        // Find an adjacent air block
        Block[] adjacent = {
            grownBlock.getRelative(1, 0, 0),
            grownBlock.getRelative(-1, 0, 0),
            grownBlock.getRelative(0, 0, 1),
            grownBlock.getRelative(0, 0, -1)
        };

        for (Block adj : adjacent) {
            if (adj.getType() == Material.AIR) {
                Material seedMaterial = getSeedForCrop(type);
                if (seedMaterial != null) {
                    adj.setType(type);
                    adj.getWorld().playEffect(adj.getLocation(), org.bukkit.Effect.BONE_MEAL_USE, 1);
                    return;
                }
            }
        }
    }

    /**
     * Applies crop growth speedup by using bone meal effect.
     */
    private void applyCropGrowthSpeedup(Block crop, double cropGrowthBonus) {
        if (crop == null) return;

        // 5% chance per 0.1 bonus to trigger instant growth (max 25%)
        double chance = Math.min(cropGrowthBonus * 0.05, 0.25);
        if (RandomProvider.nextDouble() < chance) {
            // Trigger bone meal effect for faster growth
            crop.getWorld().playEffect(crop.getLocation(), org.bukkit.Effect.BONE_MEAL_USE, 1);
        }
    }

    /**
     * Checks if a material is a crop block.
     */
    private boolean isCropBlock(Material material) {
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS,
                 PUMPKIN_STEM, MELON_STEM,
                 COCOA, NETHER_WART,
                 SWEET_BERRY_BUSH, CAVE_VINES,
                 GLOW_BERRIES, TORCHFLOWER_CROP,
                 PITCHER_CROP -> true;
            default -> false;
        };
    }

    /**
     * Gets the seed material for a given crop type.
     */
    private Material getSeedForCrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case PUMPKIN_STEM -> Material.PUMPKIN_SEEDS;
            case MELON_STEM -> Material.MELON_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    /**
     * Applies fishing bonuses to caught items.
     * Technology fishing_bonus improves loot quality.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            return;
        }

        double fishingBonus = technologyModule.getTotalModifier(nationIdOpt.get(), "fishing_bonus");
        if (fishingBonus <= 0 || event.getCaught() == null) {
            return;
        }

        // Apply fishing luck bonus via potion effect
        int luckAmplifier = (int) Math.min(fishingBonus * 2, 4); // Max level 4 (Luck V equivalent)
        if (luckAmplifier > 0) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.LUCK,
                100, // 5 seconds
                luckAmplifier - 1,
                false, false
            ));
        }

        // Bonus treasure drop chance
        double treasureChance = Math.min(fishingBonus * 0.05, 0.3); // Max 30% bonus
        if (treasureChance > 0 && RandomProvider.nextDouble() < treasureChance) {
            // Spawn a bonus treasure item
            Location catchLocation = event.getCaught().getLocation();
            ItemStack[] treasures = {
                new ItemStack(Material.NAUTILUS_SHELL, 1),
                new ItemStack(Material.HEART_OF_THE_SEA, 1),
                new ItemStack(Material.SADDLE, 1),
                new ItemStack(Material.NAME_TAG, 1),
                new ItemStack(Material.BOW, 1),
                new ItemStack(Material.FISHING_ROD, 1)
            };
            ItemStack bonus = treasures[RandomProvider.nextInt(treasures.length)].clone();
            catchLocation.getWorld().dropItemNaturally(catchLocation, bonus);
        }
    }

    /**
     * Applies attack damage bonuses from technology.
     * This supplements the attribute-based damage modifiers.
     * Handles: melee_damage, ranged_damage, bow_damage, attack_damage
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            return;
        }

        // Check if using a ranged weapon (bow/crossbow)
        boolean isRanged = player.getInventory().getItemInMainHand().getType().name().contains("BOW") ||
                          player.getInventory().getItemInOffHand().getType().name().contains("BOW");

        double totalBonus = 0;

        // Apply base attack damage bonus
        double attackDamage = technologyModule.getTotalModifier(nationIdOpt.get(), "attack_damage");
        totalBonus += attackDamage;

        // Apply melee-specific bonus
        if (!isRanged) {
            double meleeDamage = technologyModule.getTotalModifier(nationIdOpt.get(), "melee_damage");
            totalBonus += meleeDamage;
        }

        // Apply ranged-specific bonus
        if (isRanged) {
            double rangedDamage = technologyModule.getTotalModifier(nationIdOpt.get(), "ranged_damage");
            totalBonus += rangedDamage;
            double bowDamage = technologyModule.getTotalModifier(nationIdOpt.get(), "bow_damage");
            totalBonus += bowDamage;
        }

        // Apply team damage bonus (bonus when fighting with allies nearby)
        double teamBonus = technologyModule.getTotalModifier(nationIdOpt.get(), "team_damage_bonus");
        if (teamBonus > 0 && isFightingWithAllies(player, event.getEntity().getLocation())) {
            totalBonus += teamBonus;
        }

        if (totalBonus > 0) {
            double originalDamage = event.getFinalDamage();
            double newDamage = originalDamage * (1.0 + totalBonus);
            event.setDamage(newDamage);
        }
    }

    /**
     * Checks if the player is fighting with allied players nearby.
     */
    private boolean isFightingWithAllies(Player player, Location target) {
        if (target == null) return false;

        // Find allied players within 10 blocks
        Optional<NationId> playerNationOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (playerNationOpt.isEmpty()) return false;

        int allyCount = 0;
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player)) continue;
            if (nearby.getLocation().distance(target) > 10) continue;

            Optional<NationId> nearbyNationOpt = nationService.nationOf(nearby.getUniqueId()).map(n -> n.id());
            if (nearbyNationOpt.isPresent() && nearbyNationOpt.get().equals(playerNationOpt.get())) {
                allyCount++;
            }
        }

        // Require at least 1 ally for team bonus
        return allyCount > 0;
    }

    /**
     * Applies defense bonuses from technology.
     * Defense is calculated from defense_bonus effect type.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            return;
        }

        // Apply defense_bonus for damage reduction
        double defenseBonus = technologyModule.getTotalModifier(nationIdOpt.get(), "defense_bonus");
        if (defenseBonus > 0) {
            double originalDamage = event.getFinalDamage();
            // Cap reduction at 75% for balance
            double reduction = Math.min(defenseBonus, 0.75);
            double newDamage = originalDamage * (1.0 - reduction);
            event.setDamage(newDamage);
        }

        // Also apply food_efficiency for hunger-based damage reduction
        double foodEfficiency = technologyModule.getTotalModifier(nationIdOpt.get(), "food_efficiency");
        if (foodEfficiency > 0) {
            // Food efficiency reduces starvation damage
            if (event.getCause() == EntityDamageEvent.DamageCause.STARVATION) {
                double originalDamage = event.getFinalDamage();
                double reduction = Math.min(foodEfficiency, 0.5); // Cap at 50%
                double newDamage = originalDamage * (1.0 - reduction);
                event.setDamage(newDamage);
            }
        }
    }

    /**
     * Applies harvest bonuses when harvesting crops.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            return;
        }

        // Apply growth_bonus to harvest yield
        double growthBonus = technologyModule.getTotalModifier(nationIdOpt.get(), "growth_bonus");
        var harvested = event.getHarvestedBlock();
        if (growthBonus > 0 && harvested != null) {
            // 获取收获的物品
            var drops = harvested.getDrops(player.getInventory().getItemInMainHand());
            if (!drops.isEmpty()) {
                List<ItemStack> bonusDrops = new ArrayList<>();

                for (ItemStack drop : drops) {
                    int bonusAmount = (int) Math.ceil(drop.getAmount() * growthBonus * 0.1);
                    if (bonusAmount > 0) {
                        ItemStack bonus = drop.clone();
                        bonus.setAmount(bonusAmount);
                        bonusDrops.add(bonus);
                    }
                }

                // 添加奖励掉落
                for (ItemStack bonus : bonusDrops) {
                    player.getWorld().dropItemNaturally(harvested.getLocation(), bonus);
                }
            }
        }
    }

    /**
     * Applies resource yield bonus from technology effects.
     * Adds bonus item drops based on the resource yield modifier.
     *
     * @param material The type of block that was broken
     * @param location The location where the block was broken
     * @param player The player who broke the block
     * @param resourceYieldBonus The yield bonus multiplier (e.g., 0.1 = +10% bonus drops)
     */
    private void applyResourceYieldBonus(Material material, Location location, Player player, double resourceYieldBonus) {
        if (material == null || location == null || player == null || resourceYieldBonus <= 0) {
            return;
        }

        // Determine bonus drops based on material type
        ItemStack bonusItem = getBonusDropForMaterial(material);
        if (bonusItem == null) {
            return;
        }

        // Calculate bonus amount based on yield modifier
        int bonusAmount = (int) Math.max(1, bonusItem.getAmount() * resourceYieldBonus);
        if (bonusAmount <= 0) {
            return;
        }

        // Create and spawn the bonus item
        ItemStack bonus = bonusItem.clone();
        bonus.setAmount(bonusAmount);
        location.getWorld().dropItemNaturally(location, bonus);
    }

    /**
     * Gets the appropriate bonus item for a given material.
     * Returns null if the material doesn't have a meaningful bonus.
     */
    private ItemStack getBonusDropForMaterial(Material material) {
        if (material == null) {
            return null;
        }

        return switch (material) {
            // Ores yield their respective gems/ingots as bonus
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new ItemStack(Material.DIAMOND, 1);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new ItemStack(Material.EMERALD, 1);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT, 1);
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT, 1);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT, 1);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new ItemStack(Material.LAPIS_LAZULI, 1);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new ItemStack(Material.REDSTONE, 1);
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new ItemStack(Material.COAL, 1);
            case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ, 1);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP, 1);
            // Default: no bonus
            default -> null;
        };
    }
}
