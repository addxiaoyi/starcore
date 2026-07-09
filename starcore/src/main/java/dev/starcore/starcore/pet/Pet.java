package dev.starcore.starcore.pet;

import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 宠物数据模型
 */
public class Pet {
    private UUID petId;
    private final UUID ownerId;
    private PetType petType;
    private PetRarity rarity;
    private String name;
    private int level;
    private long experience;
    private double health;
    private double maxHealth;
    private double damage;
    private double defense;
    private double speed;
    private boolean summoned;
    private UUID entityUuid;
    private long createdAt;
    private long lastFed;
    private boolean namingMode;

    // 属性加成
    private final Map<String, Double> attributeBonuses;

    public Pet(UUID ownerId, PetType petType, PetRarity rarity) {
        this.petId = UUID.randomUUID();
        this.ownerId = ownerId;
        this.petType = petType;
        this.rarity = rarity;
        this.name = rarity.getDisplayName() + petType.getDisplayName();
        this.level = 1;
        this.experience = 0;
        this.summoned = false;
        this.createdAt = System.currentTimeMillis();
        this.lastFed = System.currentTimeMillis();
        this.namingMode = false;
        this.attributeBonuses = new HashMap<>();

        // 根据稀有度初始化基础属性
        initBaseAttributes();
    }

    /**
     * 根据宠物类型和稀有度初始化基础属性
     */
    private void initBaseAttributes() {
        double multiplier = rarity.getAttributeMultiplier();

        switch (petType.getCategory()) {
            case COMPANION -> {
                this.maxHealth = 20 * multiplier;
                this.damage = 3 * multiplier;
                this.defense = 2 * multiplier;
                this.speed = 0.25 * multiplier;
            }
            case MOUNT -> {
                this.maxHealth = 30 * multiplier;
                this.damage = 2 * multiplier;
                this.defense = 5 * multiplier;
                this.speed = 0.35 * multiplier;
            }
            case FLYING -> {
                this.maxHealth = 15 * multiplier;
                this.damage = 5 * multiplier;
                this.defense = 1 * multiplier;
                this.speed = 0.4 * multiplier;
            }
            case AQUATIC -> {
                this.maxHealth = 25 * multiplier;
                this.damage = 2 * multiplier;
                this.defense = 3 * multiplier;
                this.speed = 0.3 * multiplier;
            }
            case SPECIAL -> {
                this.maxHealth = 40 * multiplier;
                this.damage = 8 * multiplier;
                this.defense = 8 * multiplier;
                this.speed = 0.2 * multiplier;
            }
        }

        this.health = this.maxHealth;
    }

    /**
     * 增加经验值，检查升级
     */
    public boolean addExperience(long exp) {
        this.experience += exp;

        // 升级公式：每级需要 100 * level 经验
        long expNeeded = getExpForNextLevel();

        while (this.experience >= expNeeded && this.level < getMaxLevel()) {
            this.experience -= expNeeded;
            levelUp();
            expNeeded = getExpForNextLevel();
        }

        return this.experience >= expNeeded;
    }

    /**
     * 升级
     */
    public void levelUp() {
        this.level++;
        double levelBonus = 1 + (level * 0.05);

        this.maxHealth *= levelBonus;
        this.damage *= levelBonus;
        this.defense *= levelBonus;
        this.speed *= 1 + (level * 0.02);

        this.health = Math.min(this.health, this.maxHealth);

        // 更新稀有度（特定等级触发）
        checkRarityEvolution();
    }

    /**
     * 检查稀有度进化
     */
    private void checkRarityEvolution() {
        if (level >= 25 && rarity == PetRarity.COMMON) {
            this.rarity = PetRarity.UNCOMMON;
            applyRarityBonus();
        } else if (level >= 50 && rarity == PetRarity.UNCOMMON) {
            this.rarity = PetRarity.RARE;
            applyRarityBonus();
        } else if (level >= 75 && rarity == PetRarity.RARE) {
            this.rarity = PetRarity.EPIC;
            applyRarityBonus();
        } else if (level >= 100 && rarity == PetRarity.EPIC) {
            this.rarity = PetRarity.LEGENDARY;
            applyRarityBonus();
        }
    }

    /**
     * 应用稀有度加成
     */
    private void applyRarityBonus() {
        double bonus = rarity.getAttributeMultiplier();
        this.maxHealth *= bonus;
        this.damage *= bonus;
        this.defense *= bonus;
        this.health = Math.min(this.health, this.maxHealth);
    }

    /**
     * 获取升级所需经验
     */
    public long getExpForNextLevel() {
        return 100L * level * level;
    }

    /**
     * 获取最大等级
     */
    public int getMaxLevel() {
        return switch (rarity) {
            case COMMON -> 50;
            case UNCOMMON -> 75;
            case RARE -> 100;
            case EPIC -> 125;
            case LEGENDARY, MYTHIC -> 150;
        };
    }

    /**
     * 喂食宠物
     */
    public boolean feed() {
        long now = System.currentTimeMillis();
        long timeSinceLastFeed = now - lastFed;

        // 饱食度恢复间隔：1小时
        if (timeSinceLastFeed < 3600000) {
            return false;
        }

        // 恢复生命值
        this.health = Math.min(this.maxHealth, this.health + (this.maxHealth * 0.3));
        this.lastFed = now;
        return true;
    }

    /**
     * 受伤处理
     */
    public void takeDamage(double damage) {
        // 伤害减免计算
        double actualDamage = damage * (1 - (defense / (defense + 20)));
        this.health -= actualDamage;

        if (this.health <= 0) {
            this.health = 0;
        }
    }

    /**
     * 治愈
     */
    public void heal(double amount) {
        this.health = Math.min(this.maxHealth, this.health + amount);
    }

    /**
     * 重命名宠物
     */
    public void rename(String newName) {
        this.name = newName != null && !newName.isEmpty() ? newName : this.name;
        this.namingMode = false;
    }

    // Getters and Setters
    public UUID getPetId() {
        return petId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public PetType getPetType() {
        return petType;
    }

    public void setPetType(PetType petType) {
        this.petType = petType;
    }

    public PetRarity getRarity() {
        return rarity;
    }

    public void setRarity(PetRarity rarity) {
        this.rarity = rarity;
        initBaseAttributes();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getExperience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = experience;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = Math.min(this.maxHealth, Math.max(0, health));
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public double getDefense() {
        return defense;
    }

    public void setDefense(double defense) {
        this.defense = defense;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public boolean isSummoned() {
        return summoned;
    }

    public void setSummoned(boolean summoned) {
        this.summoned = summoned;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastFed() {
        return lastFed;
    }

    public boolean isNamingMode() {
        return namingMode;
    }

    public void setNamingMode(boolean namingMode) {
        this.namingMode = namingMode;
    }

    public Map<String, Double> getAttributeBonuses() {
        return attributeBonuses;
    }

    /**
     * 添加属性加成
     */
    public void addAttributeBonus(String attribute, double value) {
        attributeBonuses.merge(attribute, value, Double::sum);
    }

    /**
     * 获取经验条百分比
     */
    public double getExpProgress() {
        long expNeeded = getExpForNextLevel();
        return expNeeded > 0 ? (double) experience / expNeeded : 1.0;
    }

    /**
     * 获取生命值百分比
     */
    public double getHealthPercent() {
        return maxHealth > 0 ? health / maxHealth : 0;
    }

    /**
     * 获取总战斗力（用于比较）
     */
    public double getTotalPower() {
        return (damage * 2) + (defense * 1.5) + (maxHealth / 5) + (speed * 100);
    }

    /**
     * 转为可存储的Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("petId", petId.toString());
        data.put("ownerId", ownerId.toString());
        data.put("petType", petType.name());
        data.put("rarity", rarity.name());
        data.put("name", name);
        data.put("level", level);
        data.put("experience", experience);
        data.put("health", health);
        data.put("maxHealth", maxHealth);
        data.put("damage", damage);
        data.put("defense", defense);
        data.put("speed", speed);
        data.put("summoned", summoned);
        data.put("entityUuid", entityUuid != null ? entityUuid.toString() : null);
        data.put("createdAt", createdAt);
        data.put("lastFed", lastFed);
        data.put("attributeBonuses", attributeBonuses);
        return data;
    }

    /**
     * 从Map加载数据
     */
    public static Pet fromMap(Map<String, Object> data) {
        Pet pet = new Pet(
            UUID.fromString((String) data.get("ownerId")),
            PetType.fromString((String) data.get("petType")),
            PetRarity.fromString((String) data.get("rarity"))
        );

        pet.petId = UUID.fromString((String) data.get("petId"));
        pet.name = (String) data.get("name");
        pet.level = ((Number) data.get("level")).intValue();
        pet.experience = ((Number) data.get("experience")).longValue();
        pet.health = ((Number) data.get("health")).doubleValue();
        pet.maxHealth = ((Number) data.get("maxHealth")).doubleValue();
        pet.damage = ((Number) data.get("damage")).doubleValue();
        pet.defense = ((Number) data.get("defense")).doubleValue();
        pet.speed = ((Number) data.get("speed")).doubleValue();
        pet.summoned = (Boolean) data.getOrDefault("summoned", false);
        pet.createdAt = ((Number) data.get("createdAt")).longValue();
        pet.lastFed = ((Number) data.get("lastFed")).longValue();

        String entityUuidStr = (String) data.get("entityUuid");
        if (entityUuidStr != null && !entityUuidStr.isEmpty()) {
            pet.entityUuid = UUID.fromString(entityUuidStr);
        }

        @SuppressWarnings("unchecked")
        Map<String, Double> bonuses = (Map<String, Double>) data.get("attributeBonuses");
        if (bonuses != null) {
            pet.attributeBonuses.putAll(bonuses);
        }

        return pet;
    }
}
