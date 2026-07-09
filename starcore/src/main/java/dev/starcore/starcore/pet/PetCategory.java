package dev.starcore.starcore.pet;

/**
 * 宠物分类枚举
 */
public enum PetCategory {
    COMPANION("伴侣型"),
    MOUNT("骑乘型"),
    FLYING("飞行型"),
    AQUATIC("水生型"),
    SPECIAL("特殊型");

    private final String displayName;

    PetCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
