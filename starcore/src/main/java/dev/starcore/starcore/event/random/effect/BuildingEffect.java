package dev.starcore.starcore.event.random.effect;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.event.random.EventEffect;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 建筑效果
 * 影响建筑物和结构
 */
public class BuildingEffect implements EventEffect {

    private static final Logger LOGGER = Logger.getLogger(BuildingEffect.class.getName());
    private final EffectType effectType;
    private final double value;
    private final int radius;
    private final int duration;
    private final Material targetMaterial;

    public BuildingEffect(EffectType effectType, double value, int radius,
                         int duration, Material targetMaterial) {
        this.effectType = effectType;
        this.value = value;
        this.radius = radius;
        this.duration = duration;
        this.targetMaterial = targetMaterial;
    }

    @Override
    public boolean apply(Player player, Location location) {
        Location centerLocation = location != null ? location :
                                 (player != null ? player.getLocation() : null);

        if (centerLocation == null || centerLocation.getWorld() == null) {
            return false;
        }

        try {
            List<Block> affectedBlocks = findBlocksInRadius(centerLocation, radius);

            if (affectedBlocks.isEmpty()) {
                return false;
            }

            for (Block block : affectedBlocks) {
                applyBuildingEffect(block);
            }

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply building effect", e);
            return false;
        }
    }

    /**
     * 查找半径内的所有方块
     *
     * @param center 中心位置
     * @param radius 半径
     * @return 方块列表
     */
    private List<Block> findBlocksInRadius(Location center, int radius) {
        List<Block> blocks = new ArrayList<>();

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    Block block = center.getWorld().getBlockAt(x, y, z);
                    if (shouldAffect(block)) {
                        blocks.add(block);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * 检查方块是否应该被影响
     *
     * @param block 方块
     * @return 如果应该影响返回true
     */
    private boolean shouldAffect(Block block) {
        if (targetMaterial != null) {
            return block.getType() == targetMaterial;
        }

        // 如果没有指定材料，影响所有建筑材料
        Material type = block.getType();
        return type == Material.STONE ||
               type == Material.COBBLESTONE ||
               type == Material.STONE_BRICKS ||
               type == Material.OAK_PLANKS ||
               type == Material.SPRUCE_PLANKS ||
               type == Material.BIRCH_PLANKS ||
               type == Material.JUNGLE_PLANKS ||
               type == Material.ACACIA_PLANKS ||
               type == Material.DARK_OAK_PLANKS ||
               type == Material.BRICKS ||
               type == Material.GLASS ||
               type == Material.OAK_LOG ||
               type.name().contains("PLANKS") ||
               type.name().contains("LOG");
    }

    /**
     * 应用建筑效果
     *
     * @param block 方块
     */
    private void applyBuildingEffect(Block block) {
        switch (effectType) {
            case DAMAGE:
                // 损坏建筑（概率性破坏）
                if (ThreadLocalRandom.current().nextDouble() <= value) {
                    // 可以改变为损坏的变体或直接破坏
                    Material damaged = getDamagedVariant(block.getType());
                    if (damaged != null) {
                        block.setType(damaged);
                    }
                }
                break;

            case DESTROY:
                // 摧毁建筑
                if (ThreadLocalRandom.current().nextDouble() <= value) {
                    block.setType(Material.AIR);
                }
                break;

            case REPAIR:
                // 修复建筑
                Material repaired = getRepairedVariant(block.getType());
                if (repaired != null) {
                    block.setType(repaired);
                }
                break;

            case TRANSFORM:
                // 转换材料
                if (targetMaterial != null && ThreadLocalRandom.current().nextDouble() <= value) {
                    block.setType(targetMaterial);
                }
                break;

            case FIRE:
                // 点燃可燃方块
                if (block.getType().isBurnable() && ThreadLocalRandom.current().nextDouble() <= value) {
                    Block above = block.getRelative(0, 1, 0);
                    if (above.getType() == Material.AIR) {
                        above.setType(Material.FIRE);
                    }
                }
                break;

            default:
                break;
        }
    }

    /**
     * 获取损坏变体
     *
     * @param material 原材料
     * @return 损坏后的材料，如果没有返回null
     */
    private Material getDamagedVariant(Material material) {
        switch (material) {
            case STONE_BRICKS:
                return Material.CRACKED_STONE_BRICKS;
            case POLISHED_BLACKSTONE_BRICKS:
                return Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
            case NETHER_BRICKS:
                return Material.CRACKED_NETHER_BRICKS;
            default:
                return Material.COBBLESTONE;
        }
    }

    /**
     * 获取修复变体
     *
     * @param material 原材料
     * @return 修复后的材料，如果没有返回null
     */
    private Material getRepairedVariant(Material material) {
        switch (material) {
            case CRACKED_STONE_BRICKS:
                return Material.STONE_BRICKS;
            case CRACKED_POLISHED_BLACKSTONE_BRICKS:
                return Material.POLISHED_BLACKSTONE_BRICKS;
            case CRACKED_NETHER_BRICKS:
                return Material.NETHER_BRICKS;
            case COBBLESTONE:
                return Material.STONE;
            default:
                return null;
        }
    }

    @Override
    public String getType() {
        return "BUILDING";
    }

    @Override
    public String getDescription() {
        return String.format("建筑效果 [类型=%s, 值=%.2f, 半径=%d]",
                           effectType, value, radius);
    }

    public enum EffectType {
        DAMAGE,     // 损坏
        DESTROY,    // 摧毁
        REPAIR,     // 修复
        TRANSFORM,  // 转换
        FIRE        // 火灾
    }
}
