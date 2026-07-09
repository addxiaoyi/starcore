package dev.starcore.starcore.mechanics;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 季节效果
 * 定义每个季节对玩家和环境的影响
 */
public class SeasonEffect {

    private final Season season;
    private final List<EffectModifier> modifiers;

    public SeasonEffect(Season season) {
        this.season = season;
        this.modifiers = new ArrayList<>();
        initializeEffects();
    }

    /**
     * 初始化季节效果
     */
    private void initializeEffects() {
        switch (season) {
            case SPRING:
                // 春季：作物生长+50%，再生效果
                modifiers.add(new EffectModifier("crop_growth", 1.5));
                modifiers.add(new EffectModifier("health_regen", 1.2));
                modifiers.add(new EffectModifier("animal_spawn", 1.3));
                break;

            case SUMMER:
                // 夏季：挖矿效率+30%，速度+10%，饥饿+20%
                modifiers.add(new EffectModifier("mining_speed", 1.3));
                modifiers.add(new EffectModifier("movement_speed", 1.1));
                modifiers.add(new EffectModifier("hunger_rate", 1.2));
                modifiers.add(new EffectModifier("fire_resistance", 1.0));
                break;

            case AUTUMN:
                // 秋季：收获产量+40%，经验+20%
                modifiers.add(new EffectModifier("harvest_yield", 1.4));
                modifiers.add(new EffectModifier("experience_gain", 1.2));
                modifiers.add(new EffectModifier("luck", 1.15));
                break;

            case WINTER:
                // 冬季：移动速度-10%，饥饿+20%，伤害抗性+10%
                modifiers.add(new EffectModifier("movement_speed", 0.9));
                modifiers.add(new EffectModifier("hunger_rate", 1.2));
                modifiers.add(new EffectModifier("damage_resistance", 1.1));
                modifiers.add(new EffectModifier("crop_growth", 0.5));
                break;
        }
    }

    /**
     * 应用季节效果到玩家
     */
    public void applyToPlayer(Player player) {
        switch (season) {
            case SPRING:
                // 轻微再生效果
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION, 200, 0, true, false));
                break;

            case SUMMER:
                // 急迫效果（加快挖掘）
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.HASTE, 200, 0, true, false));
                break;

            case AUTUMN:
                // 幸运效果
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.LUCK, 200, 0, true, false));
                break;

            case WINTER:
                // 缓慢效果
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 200, 0, true, false));
                break;
        }
    }

    /**
     * 移除季节效果
     */
    public void removeFromPlayer(Player player) {
        // 清除所有季节相关的药水效果
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.LUCK);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    /**
     * 获取指定类型的效果倍率
     */
    public double getModifier(String type) {
        return modifiers.stream()
            .filter(m -> m.getType().equals(type))
            .findFirst()
            .map(EffectModifier::getMultiplier)
            .orElse(1.0);
    }

    /**
     * 检查是否有指定效果
     */
    public boolean hasModifier(String type) {
        return modifiers.stream().anyMatch(m -> m.getType().equals(type));
    }

    /**
     * 获取所有效果修饰符
     */
    public List<EffectModifier> getModifiers() {
        return new ArrayList<>(modifiers);
    }

    /**
     * 获取季节描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(season.getColorCode()).append(season.getDisplayName()).append(" 效果:\n");

        for (EffectModifier modifier : modifiers) {
            String effect = modifier.getDisplayName();
            double value = modifier.getMultiplier();
            String change = value > 1.0 ? "+" + String.format("%.0f", (value - 1.0) * 100) + "%" :
                                         "-" + String.format("%.0f", (1.0 - value) * 100) + "%";

            sb.append("  §7- §f").append(effect).append(": ")
              .append(value > 1.0 ? "§a" : "§c").append(change).append("\n");
        }

        return sb.toString();
    }

    public Season getSeason() {
        return season;
    }

    /**
     * 效果修饰符内部类
     */
    public static class EffectModifier {
        private final String type;
        private final double multiplier;

        public EffectModifier(String type, double multiplier) {
            this.type = type;
            this.multiplier = multiplier;
        }

        public String getType() {
            return type;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public String getDisplayName() {
            switch (type) {
                case "crop_growth": return "作物生长速度";
                case "health_regen": return "生命恢复";
                case "animal_spawn": return "动物生成率";
                case "mining_speed": return "挖掘速度";
                case "movement_speed": return "移动速度";
                case "hunger_rate": return "饥饿速度";
                case "fire_resistance": return "火焰抗性";
                case "harvest_yield": return "收获产量";
                case "experience_gain": return "经验获取";
                case "luck": return "幸运值";
                case "damage_resistance": return "伤害抗性";
                default: return type;
            }
        }
    }
}
