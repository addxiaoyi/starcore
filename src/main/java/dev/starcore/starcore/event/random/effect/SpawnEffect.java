package dev.starcore.starcore.event.random.effect;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.event.random.EventEffect;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.attribute.Attribute;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 生成效果
 * 生成生物或实体
 */
public class SpawnEffect implements EventEffect {

    private static final Logger LOGGER = Logger.getLogger(SpawnEffect.class.getName());
    private final EntityType entityType;
    private final int amount;
    private final int radius;
    private final boolean hostile;
    private final double healthMultiplier;
    private final int duration;

    public SpawnEffect(EntityType entityType, int amount, int radius,
                      boolean hostile, double healthMultiplier, int duration) {
        this.entityType = entityType;
        this.amount = amount;
        this.radius = radius;
        this.hostile = hostile;
        this.healthMultiplier = healthMultiplier;
        this.duration = duration;
    }

    @Override
    public boolean apply(Player player, Location location) {
        Location spawnLocation = location != null ? location :
                                (player != null ? player.getLocation() : null);

        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            return false;
        }

        try {
            int spawned = 0;

            for (int i = 0; i < amount; i++) {
                // 在半径内随机位置生成
                Location randomLoc = getRandomLocation(spawnLocation, radius);

                // 确保生成位置安全
                if (!isSafeSpawnLocation(randomLoc)) {
                    continue;
                }

                // 生成实体
                var world = randomLoc.getWorld();
                if (world == null) {
                    continue;
                }
                LivingEntity entity = (LivingEntity) world.spawnEntity(randomLoc, entityType);

                if (entity != null) {
                    // 设置生物属性
                    configureEntity(entity, player);
                    spawned++;
                }
            }

            return spawned > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply spawn effect", e);
            return false;
        }
    }

    /**
     * 获取半径内的随机位置
     *
     * @param center 中心位置
     * @param radius 半径
     * @return 随机位置
     */
    private Location getRandomLocation(Location center, int radius) {
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = ThreadLocalRandom.current().nextDouble() * radius;

        double x = center.getX() + distance * Math.cos(angle);
        double z = center.getZ() + distance * Math.sin(angle);
        double y = center.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;

        return new Location(center.getWorld(), x, y, z);
    }

    /**
     * 检查位置是否安全生成
     *
     * @param location 位置
     * @return 如果安全返回true
     */
    private boolean isSafeSpawnLocation(Location location) {
        if (location.getWorld() == null) {
            return false;
        }

        // 检查是否在空气中且下方有固体方块
        return location.getBlock().isEmpty() &&
               location.clone().add(0, -1, 0).getBlock().isSolid();
    }

    /**
     * 配置生成的实体
     *
     * @param entity 实体
     * @param player 相关玩家
     */
    private void configureEntity(LivingEntity entity, Player player) {
        // 设置生命值倍数
        if (healthMultiplier != 1.0) {
            var maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double maxHealth = maxHealthAttr.getValue();
                double newMaxHealth = maxHealth * healthMultiplier;
                maxHealthAttr.setBaseValue(newMaxHealth);
                entity.setHealth(newMaxHealth);
            }
        }

        // 设置敌对状态
        if (hostile && player != null && entity instanceof Mob mob) {
            mob.setTarget(player);
        }

        // 设置持久性（不会自然消失）
        entity.setRemoveWhenFarAway(false);

        // 可以添加自定义名称
        entity.setCustomName(String.format("§c事件生物 [%s]", entityType.name()));
        entity.setCustomNameVisible(true);
    }

    @Override
    public String getType() {
        return "SPAWN";
    }

    @Override
    public String getDescription() {
        return String.format("生成效果 [实体=%s, 数量=%d, 半径=%d, 敌对=%s]",
                           entityType, amount, radius, hostile);
    }
}
