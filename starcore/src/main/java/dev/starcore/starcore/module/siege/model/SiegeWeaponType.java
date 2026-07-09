package dev.starcore.starcore.module.siege.model;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 攻城器械类型
 */
public enum SiegeWeaponType {
    /**
     * 投石机 - 高伤害攻城武器
     * 基础伤害: 500, 射程: 100格, 弹药消耗: 10, 冷却: 3秒
     */
    BALLISTA("ballista", "Ballista", 500, 100, 10, 3, true, 80),

    /**
     * 投石车 - 超远距离攻城武器
     * 基础伤害: 800, 射程: 200格, 弹药消耗: 20, 冷却: 5秒
     */
    TREBUCHET("trebuchet", "Trebuchet", 800, 200, 20, 5, false, 120),

    /**
     * 攻城锤 - 近距离破门武器
     * 基础伤害: 1000, 射程: 5格, 弹药消耗: 0, 冷却: 2秒
     */
    RAM("ram", "Ram", 1000, 5, 0, 2, true, 60),

    /**
     * 巨型弩炮 - 远程穿透武器
     * 基础伤害: 600, 射程: 150格, 弹药消耗: 8, 冷却: 2秒
     */
    SCORPION("scorpion", "Scorpion", 600, 150, 8, 2, true, 70),

    /**
     * 火炮 - 爆炸范围伤害
     * 基础伤害: 700, 射程: 80格, 弹药消耗: 15, 冷却: 4秒, 范围伤害
     */
    CANNON("cannon", "Cannon", 700, 80, 15, 4, false, 100);

    private final String key;
    private final String displayName;
    private final int baseDamage;
    private final int range;
    private final int ammoCost;
    private final int cooldownTicks;
    private final boolean movable;
    private final int defaultAmmunition;

    SiegeWeaponType(String key, String displayName, int baseDamage, int range,
                    int ammoCost, int cooldownTicks, boolean movable, int defaultAmmunition) {
        this.key = key;
        this.displayName = displayName;
        this.baseDamage = baseDamage;
        this.range = range;
        this.ammoCost = ammoCost;
        this.cooldownTicks = cooldownTicks;
        this.movable = movable;
        this.defaultAmmunition = defaultAmmunition;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int baseDamage() {
        return baseDamage;
    }

    public int range() {
        return range;
    }

    public int ammoCost() {
        return ammoCost;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public boolean movable() {
        return movable;
    }

    public int defaultAmmunition() {
        return defaultAmmunition;
    }

    /**
     * 计算实际伤害（根据距离衰减）
     */
    public int calculateDamage(Location target, Location source) {
        double distance = target.distance(source);
        if (distance > range) {
            return 0; // 超出射程
        }

        // 距离衰减：每超出10格伤害减少10%
        double falloff = 1.0 - (distance / range) * 0.5;
        return (int) (baseDamage * Math.max(0.3, falloff));
    }

    /**
     * 计算建造成本（根据类型）
     */
    public int buildCost() {
        return switch (this) {
            case RAM -> 500;
            case SCORPION -> 600;
            case BALLISTA -> 800;
            case CANNON -> 1000;
            case TREBUCHET -> 1500;
        };
    }

    /**
     * 计算维护成本（每小时）
     */
    public int maintenanceCost() {
        return switch (this) {
            case RAM -> 20;
            case SCORPION -> 25;
            case BALLISTA -> 30;
            case CANNON -> 40;
            case TREBUCHET -> 50;
        };
    }

    /**
     * 获取范围伤害半径（仅用于火炮等范围武器）
     */
    public double areaOfEffectRadius() {
        return switch (this) {
            case CANNON -> 5.0;
            case TREBUCHET -> 8.0;
            default -> 0.0;
        };
    }

    /**
     * 是否为范围伤害武器
     */
    public boolean isAreaOfEffect() {
        return areaOfEffectRadius() > 0;
    }

    // 不可变 Map - 使用静态初始化块创建
    private static final Map<String, SiegeWeaponType> BY_KEY;
    static {
        var map = new java.util.HashMap<String, SiegeWeaponType>();
        for (SiegeWeaponType type : values()) {
            map.put(type.key, type);
            map.put(type.name(), type);
        }
        BY_KEY = Collections.unmodifiableMap(map);
    }

    /**
     * 从字符串解析
     */
    public static SiegeWeaponType fromString(String str) {
        SiegeWeaponType type = BY_KEY.get(str.toLowerCase());
        if (type == null) {
            throw new IllegalArgumentException("Unknown siege weapon type: " + str);
        }
        return type;
    }

    /**
     * 检查字符串是否有效
     */
    public static boolean isValid(String str) {
        return BY_KEY.containsKey(str.toLowerCase());
    }
}