package dev.starcore.starcore.protection.integration;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 粘液科技方块保护
 *
 * 提供对粘液科技方块的细粒度保护
 * - 机器类型识别
 * - 权限分级
 * - 特殊方块保护
 */
public class SlimefunBlockProtection {

    private final Plugin plugin;
    private final Logger logger;
    private final SlimefunIntegration integration;

    // 机器类型分类 - 使用不可变 Set
    private static final Set<String> ENERGY_MACHINES = Set.of(
        "SOLAR_GENERATOR", "BIO_REACTOR", "NUCLEAR_REACTOR", "COAL_GENERATOR", "LAVA_GENERATOR"
    );
    private static final Set<String> CARGO_MACHINES = Set.of(
        "CARGO_MANAGER", "CARGO_NODE", "CARGO_INPUT_NODE", "CARGO_OUTPUT_NODE"
    );
    private static final Set<String> FARM_MACHINES = Set.of(
        "AUTO_BREEDER", "CROP_GROWTH_ACCELERATOR", "TREE_GROWTH_ACCELERATOR"
    );
    private static final Set<String> MINING_MACHINES = Set.of(
        "AUTO_ANVIL", "AUTO_DRIER", "GEO_MINER"
    );

    public SlimefunBlockProtection(Plugin plugin, SlimefunIntegration integration) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.integration = integration;
    }

    /**
     * 检查玩家是否可以破坏粘液科技方块
     */
    public boolean canBreak(Player player, Block block) {
        if (!integration.isEnabled()) {
            return true;
        }

        String blockId = integration.getCustomBlockId(block);
        if (blockId == null) {
            return true;
        }

        // 检查机器类型特定权限
        if (isEnergyMachine(blockId)) {
            if (!player.hasPermission("starcore.protection.slimefun.break.energy")) {
                logger.fine(String.format("玩家 %s 无权破坏能量机器 %s", player.getName(), blockId));
                return false;
            }
        }

        if (isCargoMachine(blockId)) {
            if (!player.hasPermission("starcore.protection.slimefun.break.cargo")) {
                logger.fine(String.format("玩家 %s 无权破坏货运机器 %s", player.getName(), blockId));
                return false;
            }
        }

        if (isFarmMachine(blockId)) {
            if (!player.hasPermission("starcore.protection.slimefun.break.farm")) {
                logger.fine(String.format("玩家 %s 无权破坏农业机器 %s", player.getName(), blockId));
                return false;
            }
        }

        if (isMiningMachine(blockId)) {
            if (!player.hasPermission("starcore.protection.slimefun.break.mining")) {
                logger.fine(String.format("玩家 %s 无权破坏采矿机器 %s", player.getName(), blockId));
                return false;
            }
        }

        return integration.canBreak(player, block);
    }

    /**
     * 检查玩家是否可以与粘液科技方块交互
     */
    public boolean canInteract(Player player, Block block) {
        if (!integration.isEnabled()) {
            return true;
        }

        String blockId = integration.getCustomBlockId(block);
        if (blockId == null) {
            return true;
        }

        // 检查机器类型特定权限
        if (isEnergyMachine(blockId)) {
            if (!player.hasPermission("starcore.protection.slimefun.interact.energy")) {
                logger.fine(String.format("玩家 %s 无权与能量机器 %s 交互", player.getName(), blockId));
                return false;
            }
        }

        if (isCargoMachine(blockId)) {
            if (!player.hasPermission("starcore.protection.slimefun.interact.cargo")) {
                logger.fine(String.format("玩家 %s 无权与货运机器 %s 交互", player.getName(), blockId));
                return false;
            }
        }

        return integration.canInteract(player, block);
    }

    /**
     * 检查玩家是否可以使用粘液胶
     */
    public boolean canUseTape(Player player, ItemStack item) {
        if (!integration.isEnabled()) {
            return true;
        }

        // 检查是否为粘液胶
        String itemId = integration.getCustomItemId(item);
        if (itemId != null && itemId.contains("DUCT_TAPE")) {
            if (!player.hasPermission("starcore.protection.slimefun.use.tape")) {
                logger.fine(String.format("玩家 %s 无权使用粘液胶", player.getName()));
                return false;
            }
        }

        return true;
    }

    // ==================== 机器类型检查 ====================

    /**
     * 检查是否为能量机器
     */
    private boolean isEnergyMachine(String blockId) {
        if (blockId == null) return false;
        String upperBlockId = blockId.toUpperCase();
        return ENERGY_MACHINES.contains(upperBlockId) ||
               upperBlockId.contains("GENERATOR") ||
               upperBlockId.contains("REACTOR") ||
               upperBlockId.contains("CAPACITOR");
    }

    /**
     * 检查是否为货运机器
     */
    private boolean isCargoMachine(String blockId) {
        if (blockId == null) return false;
        String upperBlockId = blockId.toUpperCase();
        return CARGO_MACHINES.contains(upperBlockId) ||
               upperBlockId.contains("CARGO");
    }

    /**
     * 检查是否为农业机器
     */
    private boolean isFarmMachine(String blockId) {
        if (blockId == null) return false;
        String upperBlockId = blockId.toUpperCase();
        return FARM_MACHINES.contains(upperBlockId) ||
               upperBlockId.contains("FARM") ||
               upperBlockId.contains("CROP") ||
               upperBlockId.contains("BREEDER");
    }

    /**
     * 检查是否为采矿机器
     */
    private boolean isMiningMachine(String blockId) {
        if (blockId == null) return false;
        String upperBlockId = blockId.toUpperCase();
        return MINING_MACHINES.contains(upperBlockId) ||
               upperBlockId.contains("MINER") ||
               upperBlockId.contains("QUARRY");
    }

    /**
     * 检查是否为工业机器
     */
    public boolean isIndustrialMachine(String blockId) {
        if (blockId == null) return false;
        return isEnergyMachine(blockId) ||
               isCargoMachine(blockId) ||
               isFarmMachine(blockId) ||
               isMiningMachine(blockId);
    }
}
