package dev.starcore.starcore.module.army.espionage.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 间谍类型枚举
 */
public enum SpyType {
    /**
     * 普通间谍 - 基础隐蔽能力
     */
    BASIC("basic", 0.3, 1000.0, 50.0),

    /**
     * 专业间谍 - 中等隐蔽和效率
     */
    PROFESSIONAL("professional", 0.5, 5000.0, 100.0),

    /**
     * 精英间谍 - 高级隐蔽和效率
     */
    ELITE("elite", 0.75, 20000.0, 200.0),

    /**
     * 大师间谍 - 最高隐蔽和效率
     */
    MASTER("master", 0.9, 100000.0, 500.0);

    private final String key;
    private final double stealthBonus;
    private final double trainingCost;
    private final double maintenanceCost;

    SpyType(String key, double stealthBonus, double trainingCost, double maintenanceCost) {
        this.key = key;
        this.stealthBonus = stealthBonus;
        this.trainingCost = trainingCost;
        this.maintenanceCost = maintenanceCost;
    }

    public String key() {
        return key;
    }

    /**
     * 隐蔽能力加成
     */
    public double stealthBonus() {
        return stealthBonus;
    }

    /**
     * 训练成本
     */
    public double trainingCost() {
        return trainingCost;
    }

    /**
     * 维护成本（每日）
     */
    public double maintenanceCost() {
        return maintenanceCost;
    }

    public static SpyType fromString(String str) {
        for (SpyType type : values()) {
            if (type.key.equalsIgnoreCase(str) || type.name().equalsIgnoreCase(str)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown spy type: " + str);
    }
}
