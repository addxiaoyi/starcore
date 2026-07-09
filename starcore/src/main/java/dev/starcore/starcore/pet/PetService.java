package dev.starcore.starcore.pet;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宠物服务 - 核心服务类
 */
public class PetService {
    private final Map<UUID, PlayerPets> playerPetsCache;
    private final Map<UUID, Set<UUID>> summonedPets; // 玩家已召唤的宠物
    // D-101: 跟踪跟随任务，despawn 时可主动 cancel，防止任务泄漏
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> petFollowTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private final PetDataLoader dataLoader;
    private final PetConfig config;
    private final EconomyService economyService;
    private final PlayerProfileService playerProfileService;
    private final org.bukkit.plugin.Plugin plugin;
    private final java.util.logging.Logger logger;

    private static final String PET_METADATA_KEY = "StarCorePetOwner";

    public PetService(org.bukkit.plugin.Plugin plugin, PetConfig config,
                     EconomyService economyService, PlayerProfileService playerProfileService) {
        this.plugin = plugin;
        this.config = config;
        this.economyService = economyService;
        this.playerProfileService = playerProfileService;
        this.logger = plugin.getLogger();
        this.dataLoader = new PetDataLoader(plugin);
        this.playerPetsCache = new ConcurrentHashMap<>();
        this.summonedPets = new ConcurrentHashMap<>();

        loadAllData();
    }

    /**
     * 加载所有玩家宠物数据
     */
    private void loadAllData() {
        Map<UUID, PlayerPets> allData = dataLoader.loadAll();
        playerPetsCache.putAll(allData);
        logger.info("Loaded pet data for " + allData.size() + " players");
    }

    /**
     * 保存所有数据
     */
    public void saveAll() {
        dataLoader.saveAll(playerPetsCache);
        logger.info("Saved pet data for " + playerPetsCache.size() + " players");
    }

    /**
     * 获取玩家宠物数据
     */
    public PlayerPets getPlayerPets(UUID playerId) {
        return playerPetsCache.computeIfAbsent(playerId, PlayerPets::new);
    }

    /**
     * 购买宠物
     */
    public boolean purchasePet(UUID playerId, PetType petType, PetRarity rarity) {
        PlayerPets playerPets = getPlayerPets(playerId);

        if (playerPets.getPetCount() >= playerPets.getMaxPets()) {
            return false;
        }

        // D-105: 先扣费，若 addPet 失败则回滚（金币先扣，确认添加成功才保序）
        if (economyService != null) {
            double price = getPetPrice(petType, rarity);
            if (!economyService.has(playerId, BigDecimal.valueOf(price))) {
                return false;
            }
            economyService.withdraw(playerId, BigDecimal.valueOf(price));
        }

        Pet pet = new Pet(playerId, petType, rarity);
        // addPet 失败时（重复 ID）需回滚扣费
        if (!playerPets.addPet(pet)) {
            if (economyService != null) {
                double price = getPetPrice(petType, rarity);
                economyService.deposit(playerId, BigDecimal.valueOf(price));
            }
            return false;
        }
        return true;
    }

    /**
     * 获取宠物价格
     */
    public double getPetPrice(PetType petType, PetRarity rarity) {
        double basePrice = rarity.getShopPrice();
        return basePrice * config.getPriceMultiplier(petType);
    }

    /**
     * 召唤宠物
     */
    public boolean summonPet(UUID playerId, UUID petId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }

        PlayerPets playerPets = getPlayerPets(playerId);
        Pet pet = playerPets.getPet(petId);

        if (pet == null) {
            return false;
        }

        // 如果已经有召唤的宠物，先移除
        if (pet.isSummoned()) {
            despawnPet(playerId, petId);
        }

        // 召唤宠物实体
        Entity entity = spawnPetEntity(player, pet);
        if (entity != null) {
            pet.setSummoned(true);
            pet.setEntityUuid(entity.getUniqueId());

            // 添加到已召唤列表
            summonedPets.computeIfAbsent(playerId, k -> new HashSet<>()).add(petId);

            // 更新实体属性
            applyPetAttributes(entity, pet);

            // D-104: 仅对 canRide 的类型执行骑乘，且玩家未在其他载具上时才能骑
            if (pet.getPetType().canRide() && player.getVehicle() == null) {
                entity.addPassenger(player);
            }

            return true;
        }

        return false;
    }

    /**
     * 生成宠物实体
     */
    private Entity spawnPetEntity(Player player, Pet pet) {
        Location loc = player.getLocation().add(player.getLocation().getDirection().multiply(2));

        EntityType entityType = pet.getPetType().getEntityType();

        // 检查实体类型是否可用于生成
        if (entityType == null || !entityType.isSpawnable()) {
            // 对于不可直接生成的实体，使用对应的蛋来生成
            return spawnUsingEgg(player, loc, entityType, pet);
        }

        Entity entity = loc.getWorld().spawnEntity(loc, entityType);

        // 设置名称
        updatePetName(entity, pet);

        // 设置元数据
        entity.setMetadata(PET_METADATA_KEY, new FixedMetadataValue(plugin, pet.getOwnerId().toString()));

        // 如果是动物类型，设置驯服状态
        if (entity instanceof Tameable tameable) {
            tameable.setTamed(true);
            tameable.setOwner(player);
        }

        return entity;
    }

    /**
     * 使用蛋生成宠物
     * @deprecated 使用 spawnUsingBukkit() 直接生成实体
     */
    @Deprecated
    private Entity spawnUsingEgg(Player player, Location loc, EntityType type, Pet pet) {
        // 直接使用 Bukkit API 生成实体，不再依赖已弃用的 setSpawnedType
        return spawnUsingBukkit(loc, type, pet);
    }

    /**
     * 使用 Bukkit API 直接生成
     * D-103: 补全 Tameable 驯服状态，与 spawnPetEntity 行为一致
     */
    private Entity spawnUsingBukkit(Location loc, EntityType type, Pet pet) {
        try {
            Entity entity = loc.getWorld().spawn(loc, type.getEntityClass());
            if (entity != null) {
                updatePetName(entity, pet);
                entity.setMetadata(PET_METADATA_KEY, new FixedMetadataValue(plugin, pet.getOwnerId().toString()));
                // Tameable 驯服设置（与 spawnPetEntity 保持一致）
                if (entity instanceof Tameable tameable) {
                    Player owner = Bukkit.getPlayer(pet.getOwnerId());
                    if (owner != null) {
                        tameable.setTamed(true);
                        tameable.setOwner(owner);
                    }
                }
            }
            return entity;
        } catch (Exception e) {
            logger.warning("Failed to spawn pet entity: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取蛋的材质
     */
    private Material getEggMaterial(EntityType type) {
        return switch (type) {
            case WOLF -> Material.WOLF_SPAWN_EGG;
            case CAT -> Material.CAT_SPAWN_EGG;
            case FOX -> Material.FOX_SPAWN_EGG;
            case OCELOT -> Material.OCELOT_SPAWN_EGG;
            case PARROT -> Material.PARROT_SPAWN_EGG;
            case RABBIT -> Material.RABBIT_SPAWN_EGG;
            case HORSE -> Material.HORSE_SPAWN_EGG;
            case DONKEY -> Material.DONKEY_SPAWN_EGG;
            case MULE -> Material.MULE_SPAWN_EGG;
            case LLAMA -> Material.LLAMA_SPAWN_EGG;
            case PIG -> Material.PIG_SPAWN_EGG;
            case STRIDER -> Material.STRIDER_SPAWN_EGG;
            case SKELETON_HORSE -> Material.SKELETON_HORSE_SPAWN_EGG;
            case ZOMBIE_HORSE -> Material.ZOMBIE_HORSE_SPAWN_EGG;
            case PHANTOM -> Material.PHANTOM_SPAWN_EGG;
            case DOLPHIN -> Material.DOLPHIN_SPAWN_EGG;
            case TURTLE -> Material.TURTLE_SPAWN_EGG;
            case AXOLOTL -> Material.AXOLOTL_SPAWN_EGG;
            case SNOW_GOLEM -> Material.SNOW_GOLEM_SPAWN_EGG;
            case IRON_GOLEM -> Material.IRON_GOLEM_SPAWN_EGG;
            case BLAZE -> Material.BLAZE_SPAWN_EGG;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SPAWN_EGG;
            default -> null;
        };
    }

    /**
     * 取消召唤宠物
     */
    public boolean despawnPet(UUID playerId, UUID petId) {
        PlayerPets playerPets = getPlayerPets(playerId);
        Pet pet = playerPets.getPet(petId);

        if (pet == null || !pet.isSummoned()) {
            return false;
        }

        UUID entityUuid = pet.getEntityUuid();
        if (entityUuid != null) {
            Entity entity = Bukkit.getEntity(entityUuid);
            if (entity != null && entity.isValid()) {
                // 如果玩家正在骑乘，先下马
                if (entity.getPassengers().contains(Bukkit.getPlayer(playerId))) {
                    entity.removePassenger(Bukkit.getPlayer(playerId));
                }
                entity.remove();
            }
        }

        pet.setSummoned(false);
        pet.setEntityUuid(null);

        // D-101: 取消跟随任务，防止任务泄漏
        org.bukkit.scheduler.BukkitTask task = petFollowTasks.remove(petId);
        if (task != null) {
            task.cancel();
        }

        // 从已召唤列表移除
        Set<UUID> summoned = summonedPets.get(playerId);
        if (summoned != null) {
            summoned.remove(petId);
        }

        return true;
    }

    /**
     * 取消召唤所有宠物
     */
    public void despawnAllPets(UUID playerId) {
        PlayerPets playerPets = getPlayerPets(playerId);

        for (Pet pet : playerPets.getAllPets()) {
            if (pet.isSummoned()) {
                despawnPet(playerId, pet.getPetId());
            }
        }
        // D-101: 清理所有残留任务
        petFollowTasks.entrySet().removeIf(e -> {
            UUID ownerId = playerPets.getPet(e.getKey()) != null ? playerPets.getPet(e.getKey()).getOwnerId() : null;
            return ownerId != null && ownerId.equals(playerId);
        });
    }

    /**
     * 更新宠物名称
     */
    public void updatePetName(Entity entity, Pet pet) {
        if (entity instanceof LivingEntity living) {
            String rarityColor = "§" + pet.getRarity().getColorCode();
            String name = rarityColor + "[" + pet.getName() + " Lv." + pet.getLevel() + "]";

            // 添加生命值显示
            int healthBar = (int) (pet.getHealthPercent() * 10);
            String healthStr = "§a" + "█".repeat(Math.max(0, healthBar)) +
                              "§c" + "█".repeat(Math.max(0, 10 - healthBar));

            living.setCustomName(name + " " + healthStr);
            living.setCustomNameVisible(true);
        }
    }

    /**
     * 应用宠物属性到实体
     */
    private void applyPetAttributes(Entity entity, Pet pet) {
        if (entity instanceof LivingEntity living) {
            // 设置属性
            if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(pet.getMaxHealth());
                living.setHealth(pet.getHealth());
            }

            if (living.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                living.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(pet.getSpeed());
            }

            if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(pet.getDamage());
            }

            // D-100: 不再给宠物加 INVISIBILITY，宠物应正常可见；移除原 "永久隐身" bug
            // living.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

            // 设置跟随主人
            // D-101: 保存任务引用，despawn 时可取消
            org.bukkit.scheduler.BukkitTask task = startFollowingTask(living, pet);
            if (task != null) {
                petFollowTasks.put(pet.getPetId(), task);
            }
        }
    }

    /**
     * 宠物跟随主人任务
     * D-101: 返回 BukkitTask 引用，调用方保存并在 despawn 时 cancel，防止任务泄漏
     */
    private org.bukkit.scheduler.BukkitTask startFollowingTask(LivingEntity entity, Pet pet) {
        Player owner = Bukkit.getPlayer(pet.getOwnerId());
        if (owner == null) {
            return null;
        }

        return new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid() || !pet.isSummoned() || !owner.isOnline()) {
                    cancel();
                    petFollowTasks.remove(pet.getPetId());
                    return;
                }

                // D-102: 跨世界时跳过（distance 会对不同世界抛 IllegalArgumentException）
                if (!entity.getWorld().equals(owner.getWorld())) {
                    return;
                }

                // 跟随距离
                double distance = entity.getLocation().distance(owner.getLocation());

                if (distance > 10) {
                    // 远距离传送
                    Location targetLoc = owner.getLocation().add(
                        owner.getLocation().getDirection().multiply(2)
                    );
                    entity.teleport(targetLoc);
                } else if (distance > 3) {
                    // 跟随
                    Location targetLoc = owner.getLocation().subtract(0, 0, 0);
                    Vector direction = targetLoc.toVector().subtract(entity.getLocation().toVector()).normalize();
                    entity.setVelocity(direction.multiply(0.3));
                } else {
                    // 停止移动
                    entity.setVelocity(new Vector(0, entity.getVelocity().getY(), 0));
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    /**
     * 喂养宠物
     */
    public boolean feedPet(UUID playerId, UUID petId, ItemStack food) {
        PlayerPets playerPets = getPlayerPets(playerId);
        Pet pet = playerPets.getPet(petId);

        if (pet == null) {
            return false;
        }

        // 检查食物是否合适
        if (!isValidPetFood(food.getType())) {
            return false;
        }

        boolean fed = pet.feed();
        if (fed) {
            // 给予经验
            pet.addExperience(config.getFeedExp());

            // 更新已召唤宠物的显示
            if (pet.isSummoned() && pet.getEntityUuid() != null) {
                Entity entity = Bukkit.getEntity(pet.getEntityUuid());
                if (entity instanceof LivingEntity living) {
                    living.setHealth(pet.getHealth());
                    updatePetName(entity, pet);
                }
            }
        }

        return fed;
    }

    /**
     * 检查是否是有效的宠物食物
     */
    private boolean isValidPetFood(Material material) {
        return switch (material) {
            case PORKCHOP, BEEF, CHICKEN, MUTTON, RABBIT, COD, SALMON, TROPICAL_FISH,
                 APPLE, CARROT, GOLDEN_CARROT, GOLDEN_APPLE, BREAD, COOKIE,
                 BEETROOT, POTATO, BAKED_POTATO -> true;
            default -> false;
        };
    }

    /**
     * 重命名宠物
     */
    public void renamePet(UUID playerId, UUID petId, String newName) {
        PlayerPets playerPets = getPlayerPets(playerId);
        Pet pet = playerPets.getPet(petId);

        if (pet != null) {
            pet.rename(newName);

            // 更新已召唤宠物的显示
            if (pet.isSummoned() && pet.getEntityUuid() != null) {
                Entity entity = Bukkit.getEntity(pet.getEntityUuid());
                if (entity != null) {
                    updatePetName(entity, pet);
                }
            }
        }
    }

    /**
     * 升级宠物稀有度
     */
    public boolean upgradePetRarity(UUID playerId, UUID petId) {
        PlayerPets playerPets = getPlayerPets(playerId);
        Pet pet = playerPets.getPet(petId);

        if (pet == null) {
            return false;
        }

        PetRarity nextRarity = pet.getRarity().getNextRarity();
        if (nextRarity == pet.getRarity()) {
            return false; // 已经是最高稀有度
        }

        double upgradeCost = config.getRarityUpgradeCost(pet.getRarity());
        if (economyService != null) {
            if (!economyService.has(playerId, BigDecimal.valueOf(upgradeCost))) {
                return false;
            }
            economyService.withdraw(playerId, BigDecimal.valueOf(upgradeCost));
        }

        pet.setRarity(nextRarity);

        // 如果宠物已召唤，更新属性
        if (pet.isSummoned() && pet.getEntityUuid() != null) {
            Entity entity = Bukkit.getEntity(pet.getEntityUuid());
            if (entity != null) {
                applyPetAttributes(entity, pet);
            }
        }

        return true;
    }

    /**
     * 增加宠物经验
     */
    public boolean addPetExperience(UUID playerId, UUID petId, long exp) {
        PlayerPets playerPets = getPlayerPets(playerId);
        Pet pet = playerPets.getPet(petId);

        if (pet == null) {
            return false;
        }

        boolean leveledUp = pet.addExperience(exp);

        // 更新已召唤宠物的显示
        if (pet.isSummoned() && pet.getEntityUuid() != null) {
            Entity entity = Bukkit.getEntity(pet.getEntityUuid());
            if (entity instanceof LivingEntity living) {
                living.setHealth(pet.getHealth());
                updatePetName(entity, pet);
            }
        }

        return leveledUp;
    }

    /**
     * 获取所有可购买的宠物类型
     */
    public List<PetType> getAvailablePetTypes() {
        return Arrays.asList(PetType.values());
    }

    /**
     * 获取所有可购买的稀有度
     */
    public List<PetRarity> getAvailableRarities() {
        return Arrays.asList(PetRarity.values());
    }

    /**
     * 获取玩家的所有已召唤宠物
     */
    public Collection<UUID> getSummonedPetIds(UUID playerId) {
        return summonedPets.getOrDefault(playerId, Collections.emptySet());
    }

    /**
     * 检查宠物是否已召唤
     */
    public boolean isPetSummoned(UUID playerId, UUID petId) {
        Set<UUID> summoned = summonedPets.get(playerId);
        return summoned != null && summoned.contains(petId);
    }

    /**
     * 获取宠物的战斗加成
     */
    public Map<String, Double> getPetBonuses(UUID playerId) {
        Map<String, Double> bonuses = new HashMap<>();
        PlayerPets playerPets = getPlayerPets(playerId);

        for (Pet pet : playerPets.getAllPets()) {
            if (pet.isSummoned()) {
                bonuses.merge("damage", pet.getDamage(), Double::sum);
                bonuses.merge("defense", pet.getDefense(), Double::sum);
                bonuses.merge("health", pet.getMaxHealth() / 10, Double::sum);
            }
        }

        return bonuses;
    }

    /**
     * 处理玩家登出
     */
    public void handlePlayerQuit(UUID playerId) {
        despawnAllPets(playerId);
    }

    /**
     * 获取配置
     */
    public PetConfig getConfig() {
        return config;
    }
}
