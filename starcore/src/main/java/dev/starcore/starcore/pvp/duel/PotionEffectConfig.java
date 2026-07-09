package dev.starcore.starcore.pvp.duel;

import org.bukkit.potion.PotionEffectType;

/**
 * 药水效果配置
 */
public record PotionEffectConfig(
    PotionEffectType type,
    int duration,
    int level
) {}