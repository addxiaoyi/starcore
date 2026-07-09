package dev.starcore.starcore.mechanics;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气效果
 * 定义不同天气对玩家和游戏的影响
 */
public class WeatherEffect {

    private final WeatherType weatherType;
    private final List<Modifier> modifiers;

    public WeatherEffect(WeatherType weatherType) {
        this.weatherType = weatherType;
        this.modifiers = new ArrayList<>();
        initializeEffects();
    }

    /**
     * 初始化天气效果
     */
    private void initializeEffects() {
        switch (weatherType) {
            case CLEAR:
                // 晴天：心情+10%，工作效率+5%
                modifiers.add(new Modifier("mood", 1.1));
                modifiers.add(new Modifier("work_efficiency", 1.05));
                modifiers.add(new Modifier("visibility", 1.0));
                break;

            case RAIN:
                // 雨天：作物生长+20%，视野-30%
                modifiers.add(new Modifier("crop_growth", 1.2));
                modifiers.add(new Modifier("visibility", 0.7));
                modifiers.add(new Modifier("fire_damage", 0.5));
                break;

            case THUNDERSTORM:
                // 雷暴：户外危险，挖矿速度-20%
                modifiers.add(new Modifier("outdoor_danger", 1.5));
                modifiers.add(new Modifier("mining_speed", 0.8));
                modifiers.add(new Modifier("visibility", 0.5));
                modifiers.add(new Modifier("lightning_risk", 2.0));
                break;

            case SNOW:
                // 雪天：移动速度-15%，视野-20%
                modifiers.add(new Modifier("movement_speed", 0.85));
                modifiers.add(new Modifier("visibility", 0.8));
                modifiers.add(new Modifier("freeze_risk", 1.3));
                break;
        }
    }

    /**
     * 应用天气效果到玩家
     */
    public void applyToPlayer(Player player) {
        switch (weatherType) {
            case CLEAR:
                // 晴天给予轻微速度加成
                if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 600, 0, true, false));
                }
                break;

            case RAIN:
                // 雨天无特殊药水效果
                break;

            case THUNDERSTORM:
                // 雷暴天气给予虚弱效果
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS, 600, 0, true, false));
                break;

            case SNOW:
                // 雪天给予缓慢效果
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 600, 0, true, false));

                // 户外玩家可能受到寒冷伤害
                if (!player.isInsideVehicle() && isOutdoors(player)) {
                    // 每30秒可能受到1点伤害
                    if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                        player.damage(1.0);
                        player.sendMessage("§b你感到寒冷刺骨...");
                    }
                }
                break;
        }
    }

    /**
     * 移除天气效果
     */
    public void removeFromPlayer(Player player) {
        // 清除天气相关的药水效果
        // 注意：不要清除其他系统的效果
    }

    /**
     * 检查玩家是否在户外
     */
    private boolean isOutdoors(Player player) {
        int highestBlock = player.getWorld()
            .getHighestBlockYAt(player.getLocation());
        return player.getLocation().getBlockY() >= highestBlock;
    }

    /**
     * 获取指定类型的效果倍率
     */
    public double getModifier(String type) {
        return modifiers.stream()
            .filter(m -> m.getType().equals(type))
            .findFirst()
            .map(Modifier::getMultiplier)
            .orElse(1.0);
    }

    /**
     * 检查是否有指定效果
     */
    public boolean hasModifier(String type) {
        return modifiers.stream().anyMatch(m -> m.getType().equals(type));
    }

    /**
     * 获取天气描述
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(weatherType.getColorCode())
          .append(weatherType.getDisplayName())
          .append(" §7效果:\n");

        for (Modifier modifier : modifiers) {
            String effect = modifier.getDisplayName();
            double value = modifier.getMultiplier();
            String change = value > 1.0 ?
                "+" + String.format("%.0f", (value - 1.0) * 100) + "%" :
                "-" + String.format("%.0f", (1.0 - value) * 100) + "%";

            sb.append("  §7- §f").append(effect).append(": ")
              .append(value > 1.0 ? "§a" : "§c")
              .append(change).append("\n");
        }

        return sb.toString();
    }

    public WeatherType getWeatherType() {
        return weatherType;
    }

    /**
     * 天气类型枚举
     */
    public enum WeatherType {
        CLEAR("晴天", "§e"),
        RAIN("雨天", "§9"),
        THUNDERSTORM("雷暴", "§5"),
        SNOW("雪天", "§f");

        private final String displayName;
        private final String colorCode;

        WeatherType(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }

        /**
         * 从Bukkit天气状态转换
         */
        public static WeatherType fromBukkit(org.bukkit.World world) {
            if (world.isThundering()) {
                return THUNDERSTORM;
            } else if (world.hasStorm()) {
                // 检查生物群系判断是雨还是雪
                return RAIN; // 简化处理
            }
            return CLEAR;
        }
    }

    /**
     * 修饰符内部类
     */
    public static class Modifier {
        private final String type;
        private final double multiplier;

        public Modifier(String type, double multiplier) {
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
                case "mood": return "心情";
                case "work_efficiency": return "工作效率";
                case "visibility": return "能见度";
                case "crop_growth": return "作物生长";
                case "fire_damage": return "火焰伤害";
                case "outdoor_danger": return "户外危险";
                case "mining_speed": return "挖掘速度";
                case "lightning_risk": return "雷击风险";
                case "movement_speed": return "移动速度";
                case "freeze_risk": return "冻伤风险";
                default: return type;
            }
        }
    }
}
