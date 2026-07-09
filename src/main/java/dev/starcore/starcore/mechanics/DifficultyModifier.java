package dev.starcore.starcore.mechanics;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 难度调整器
 * 根据玩家实力动态调整游戏难度
 */
public class DifficultyModifier {

    private final double difficultyMultiplier;
    private final String difficultyLevel;

    public DifficultyModifier(double multiplier) {
        this.difficultyMultiplier = multiplier;
        this.difficultyLevel = calculateDifficultyLevel(multiplier);
    }

    /**
     * 计算难度等级描述
     */
    private String calculateDifficultyLevel(double multiplier) {
        if (multiplier >= 2.0) return "噩梦";
        if (multiplier >= 1.5) return "困难";
        if (multiplier >= 1.2) return "挑战";
        if (multiplier >= 1.0) return "标准";
        if (multiplier >= 0.8) return "简单";
        return "新手";
    }

    /**
     * 应用难度调整到怪物
     */
    public void applyToMob(LivingEntity mob) {
        if (mob == null || mob instanceof Player) return;

        // 调整生命值
        var maxHealthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double maxHealth = maxHealthAttr.getBaseValue();
            double newMaxHealth = maxHealth * difficultyMultiplier;
            maxHealthAttr.setBaseValue(newMaxHealth);
            mob.setHealth(newMaxHealth);
        }

        // 调整攻击力
        var attackDamageAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            double damage = attackDamageAttr.getBaseValue();
            attackDamageAttr.setBaseValue(damage * difficultyMultiplier);
        }

        // 调整移动速度
        var movementSpeedAttr = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementSpeedAttr != null) {
            double speed = movementSpeedAttr.getBaseValue();
            double speedMultiplier = 1.0 + (difficultyMultiplier - 1.0) * 0.3; // 速度增幅较小
            movementSpeedAttr.setBaseValue(speed * speedMultiplier);
        }

        // 高难度时给怪物添加增益效果
        if (difficultyMultiplier >= 1.5) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
        if (difficultyMultiplier >= 2.0) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));
            mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));
        }
    }

    /**
     * 调整掉落物数量
     */
    public int modifyDropAmount(int originalAmount) {
        // 高难度给予更多奖励
        if (difficultyMultiplier >= 1.5) {
            return (int) Math.ceil(originalAmount * 1.5);
        } else if (difficultyMultiplier >= 1.2) {
            return (int) Math.ceil(originalAmount * 1.2);
        }
        return originalAmount;
    }

    /**
     * 调整经验值
     */
    public int modifyExperience(int originalExp) {
        return (int) Math.ceil(originalExp * difficultyMultiplier);
    }

    /**
     * 调整伤害（对玩家造成的伤害）
     */
    public double modifyDamageToPlayer(double originalDamage) {
        return originalDamage * difficultyMultiplier;
    }

    /**
     * 调整伤害（玩家对怪物造成的伤害）
     */
    public double modifyDamageFromPlayer(double originalDamage) {
        // 高难度时玩家伤害略微降低
        if (difficultyMultiplier > 1.0) {
            double reduction = 1.0 - (difficultyMultiplier - 1.0) * 0.2;
            return originalDamage * Math.max(reduction, 0.7); // 最多降低30%
        }
        return originalDamage;
    }

    /**
     * 获取怪物生成数量倍率
     */
    public double getSpawnRateMultiplier() {
        if (difficultyMultiplier >= 2.0) return 1.5;
        if (difficultyMultiplier >= 1.5) return 1.3;
        if (difficultyMultiplier >= 1.2) return 1.1;
        if (difficultyMultiplier <= 0.8) return 0.7;
        return 1.0;
    }

    public double getDifficultyMultiplier() {
        return difficultyMultiplier;
    }

    public String getDifficultyLevel() {
        return difficultyLevel;
    }
}
