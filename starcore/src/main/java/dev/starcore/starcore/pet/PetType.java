package dev.starcore.starcore.pet;

import org.bukkit.entity.EntityType;

/**
 * 宠物类型枚举
 */
public enum PetType {
    // 基础宠物
    WOLF("狼", EntityType.WOLF, PetCategory.COMPANION),
    CAT("猫", EntityType.CAT, PetCategory.COMPANION),
    FOX("狐狸", EntityType.FOX, PetCategory.COMPANION),
    OCELOT("豹猫", EntityType.OCELOT, PetCategory.COMPANION),
    PARROT("鹦鹉", EntityType.PARROT, PetCategory.COMPANION),
    RABBIT("兔子", EntityType.RABBIT, PetCategory.COMPANION),

    // 农场动物
    HORSE("马", EntityType.HORSE, PetCategory.MOUNT),
    DONKEY("驴", EntityType.DONKEY, PetCategory.MOUNT),
    MULE("骡子", EntityType.MULE, PetCategory.MOUNT),
    LLAMA("羊驼", EntityType.LLAMA, PetCategory.MOUNT),

    // 骑乘生物
    PIG("猪", EntityType.PIG, PetCategory.MOUNT),
    STRIDER("炽足兽", EntityType.STRIDER, PetCategory.MOUNT),
    SKELETON_HORSE("骷髅马", EntityType.SKELETON_HORSE, PetCategory.MOUNT),
    ZOMBIE_HORSE("僵尸马", EntityType.ZOMBIE_HORSE, PetCategory.MOUNT),

    // 特殊宠物
    SNOW_GOLEM("雪傀儡", EntityType.SNOW_GOLEM, PetCategory.SPECIAL),
    IRON_GOLEM("铁傀儡", EntityType.IRON_GOLEM, PetCategory.SPECIAL),
    BLAZE("烈焰人", EntityType.BLAZE, PetCategory.SPECIAL),
    WITHER_SKELETON("凋零骷髅", EntityType.WITHER_SKELETON, PetCategory.SPECIAL),

    // 飞行宠物
    PHANTOM("幻翼", EntityType.PHANTOM, PetCategory.FLYING),

    // 水生宠物
    DOLPHIN("海豚", EntityType.DOLPHIN, PetCategory.AQUATIC),
    TURTLE("海龟", EntityType.TURTLE, PetCategory.AQUATIC),
    AXOLOTL("美西螈", EntityType.AXOLOTL, PetCategory.AQUATIC);

    private final String displayName;
    private final EntityType entityType;
    private final PetCategory category;

    PetType(String displayName, EntityType entityType, PetCategory category) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public PetCategory getCategory() {
        return category;
    }

    public boolean canRide() {
        return category == PetCategory.MOUNT || category == PetCategory.FLYING;
    }

    /**
     * 获取宠物类型
     */
    public static PetType fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
