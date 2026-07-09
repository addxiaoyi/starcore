package dev.starcore.starcore.event.random.effect;

import dev.starcore.starcore.event.random.EventEffect;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 玩家效果
 * 影响玩家的各种属性和状态
 */
public class PlayerEffect implements EventEffect {

    private static final Logger LOGGER = Logger.getLogger(PlayerEffect.class.getName());
    private final EffectType effectType;
    private final double value;
    private final int duration;
    private final String message;

    public PlayerEffect(EffectType effectType, double value, int duration, String message) {
        this.effectType = effectType;
        this.value = value;
        this.duration = duration;
        this.message = message;
    }

    @Override
    public boolean apply(Player player, Location location) {
        if (player == null) {
            return false;
        }

        try {
            switch (effectType) {
                case DAMAGE:
                    // 造成伤害
                    player.damage(value);
                    break;

                case HEAL:
                    // 治疗
                    double newHealth = Math.min(player.getHealth() + value, player.getMaxHealth());
                    player.setHealth(newHealth);
                    break;

                case HUNGER:
                    // 改变饥饿值
                    int newFoodLevel = Math.max(0, Math.min(20, player.getFoodLevel() + (int) value));
                    player.setFoodLevel(newFoodLevel);
                    break;

                case EXPERIENCE:
                    // 给予经验
                    player.giveExp((int) value);
                    break;

                case POTION_EFFECT:
                    // 应用药水效果（value表示效果强度）
                    applyPotionEffect(player, (int) value);
                    break;

                case TELEPORT:
                    // 传送（需要位置参数）
                    if (location != null) {
                        player.teleport(location);
                    }
                    break;

                case SET_FIRE:
                    // 点燃玩家
                    player.setFireTicks((int) value);
                    break;

                default:
                    return false;
            }

            // 发送消息
            if (message != null && !message.isEmpty()) {
                player.sendMessage(message);
            }

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply player effect", e);
            return false;
        }
    }

    /**
     * 应用药水效果
     *
     * @param player 玩家
     * @param amplifier 效果强度
     */
    private void applyPotionEffect(Player player, int amplifier) {
        // 这里可以根据配置应用不同的药水效果
        // 示例：应用速度效果
        PotionEffect effect = new PotionEffect(
            PotionEffectType.SPEED,
            duration * 20, // 转换为tick
            amplifier,
            false,
            true
        );
        player.addPotionEffect(effect);
    }

    @Override
    public String getType() {
        return "PLAYER";
    }

    @Override
    public String getDescription() {
        return String.format("玩家效果 [类型=%s, 值=%.2f, 持续=%d秒]",
                           effectType, value, duration);
    }

    public enum EffectType {
        DAMAGE,         // 伤害
        HEAL,           // 治疗
        HUNGER,         // 饥饿值
        EXPERIENCE,     // 经验
        POTION_EFFECT,  // 药水效果
        TELEPORT,       // 传送
        SET_FIRE        // 点燃
    }
}
