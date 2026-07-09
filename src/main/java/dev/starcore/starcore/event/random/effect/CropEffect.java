package dev.starcore.starcore.event.random.effect;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.event.random.EventEffect;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 作物效果
 * 影响农作物的生长和收成
 */
public class CropEffect implements EventEffect {

    private static final Logger LOGGER = Logger.getLogger(CropEffect.class.getName());
    private final EffectType effectType;
    private final double value;
    private final int radius;
    private final int duration;

    public CropEffect(EffectType effectType, double value, int radius, int duration) {
        this.effectType = effectType;
        this.value = value;
        this.radius = radius;
        this.duration = duration;
    }

    @Override
    public boolean apply(Player player, Location location) {
        Location centerLocation = location != null ? location :
                                 (player != null ? player.getLocation() : null);

        if (centerLocation == null || centerLocation.getWorld() == null) {
            return false;
        }

        try {
            List<Block> affectedCrops = findCropsInRadius(centerLocation, radius);

            if (affectedCrops.isEmpty()) {
                return false;
            }

            for (Block crop : affectedCrops) {
                applyCropEffect(crop);
            }

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply crop effect", e);
            return false;
        }
    }

    /**
     * 查找半径内的所有作物
     *
     * @param center 中心位置
     * @param radius 半径
     * @return 作物方块列表
     */
    private List<Block> findCropsInRadius(Location center, int radius) {
        List<Block> crops = new ArrayList<>();

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    if (isCrop(block)) {
                        crops.add(block);
                    }
                }
            }
        }

        return crops;
    }

    /**
     * 检查方块是否为作物
     *
     * @param block 方块
     * @return 如果是作物返回true
     */
    private boolean isCrop(Block block) {
        Material type = block.getType();
        return type == Material.WHEAT ||
               type == Material.CARROTS ||
               type == Material.POTATOES ||
               type == Material.BEETROOTS ||
               type == Material.NETHER_WART ||
               type == Material.COCOA ||
               type == Material.SWEET_BERRY_BUSH ||
               type == Material.MELON_STEM ||
               type == Material.PUMPKIN_STEM;
    }

    /**
     * 应用作物效果
     *
     * @param crop 作物方块
     */
    private void applyCropEffect(Block crop) {
        BlockState state = crop.getState();

        if (!(state.getBlockData() instanceof Ageable)) {
            return;
        }

        Ageable ageable = (Ageable) state.getBlockData();

        switch (effectType) {
            case BOOST_GROWTH:
                // 促进生长
                int currentAge = ageable.getAge();
                int maxAge = ageable.getMaximumAge();
                int newAge = Math.min(maxAge, currentAge + (int) value);
                ageable.setAge(newAge);
                state.setBlockData(ageable);
                state.update();
                break;

            case REDUCE_GROWTH:
                // 减缓生长
                currentAge = ageable.getAge();
                newAge = Math.max(0, currentAge - (int) value);
                ageable.setAge(newAge);
                state.setBlockData(ageable);
                state.update();
                break;

            case DESTROY:
                // 摧毁作物
                if (ThreadLocalRandom.current().nextDouble() <= value) {
                    crop.setType(Material.AIR);
                }
                break;

            case INSTANT_GROW:
                // 立即成熟
                ageable.setAge(ageable.getMaximumAge());
                state.setBlockData(ageable);
                state.update();
                break;

            default:
                break;
        }
    }

    @Override
    public String getType() {
        return "CROP";
    }

    @Override
    public String getDescription() {
        return String.format("作物效果 [类型=%s, 值=%.2f, 半径=%d]",
                           effectType, value, radius);
    }

    public enum EffectType {
        BOOST_GROWTH,   // 促进生长
        REDUCE_GROWTH,  // 减缓生长
        DESTROY,        // 摧毁作物
        INSTANT_GROW    // 立即成熟
    }
}
