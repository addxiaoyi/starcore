package dev.starcore.starcore.pvp.duel;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

/**
 * Duel Kit 装备配置
 * 用于定义决斗套装的装备和效果
 */
public record DuelKit(
    String id,
    String displayName,
    Map<Integer, ItemStack> armor,
    List<ItemStack> weapons,
    List<PotionEffectConfig> effects
) {}