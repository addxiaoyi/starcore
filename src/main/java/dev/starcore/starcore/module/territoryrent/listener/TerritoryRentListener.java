package dev.starcore.starcore.module.territoryrent.listener;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.territoryrent.TerritoryRentService;
import dev.starcore.starcore.module.territoryrent.model.PermissionLevel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;

/**
 * 领土租借事件监听器
 * 处理租借区块的权限检查和交互限制
 */
public final class TerritoryRentListener implements Listener {

    private static final Set<Material> INTERACTIVE_BLOCKS = Set.of(
        // 容器类
        Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
        Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
        Material.HOPPER, Material.DISPENSER, Material.DROPPER, Material.BARREL,
        Material.SHULKER_BOX,
        // 特殊容器
        Material.CRAFTING_TABLE, Material.ENCHANTING_TABLE, Material.ANVIL,
        Material.GRINDSTONE, Material.CARTOGRAPHY_TABLE, Material.STONECUTTER,
        Material.SMITHING_TABLE, Material.LOOM, Material.BREWING_STAND,
        Material.BEACON, Material.JUKEBOX, Material.NOTE_BLOCK,
        // 红石
        Material.REPEATER, Material.COMPARATOR
    );

    private static final Set<Material> BUILD_BLOCKS = Set.of(
        Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
        Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR,
        Material.BAMBOO_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR,
        Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
        Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
        Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
        Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR,
        Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
        Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
        Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
        Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE,
        Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON,
        Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON,
        Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON, Material.BAMBOO_BUTTON,
        Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
        Material.LEVER, Material.COMPARATOR, Material.REPEATER,
        Material.TORCH, Material.SOUL_TORCH, Material.REDSTONE_TORCH
    );

    private final TerritoryRentService service;
    private final NationService nationService;

    public TerritoryRentListener(TerritoryRentService service, NationService nationService) {
        this.service = service;
        this.nationService = nationService;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();
        ChunkCoordinate toCoord = new ChunkCoordinate(
            event.getTo().getWorld().getName(),
            event.getTo().getChunk().getX(),
            event.getTo().getChunk().getZ()
        );

        if (service.isChunkRented(toCoord)) {
            int permission = service.getRentalPermissionLevel(toCoord, player.getUniqueId());

            if (permission == 0) {
                player.sendMessage(Component.text("⚠️ 你正在进入他人租借的区域", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ChunkCoordinate coord = new ChunkCoordinate(
            block.getWorld().getName(),
            block.getChunk().getX(),
            block.getChunk().getZ()
        );

        if (!service.isChunkRented(coord)) {
            return;
        }

        int permission = service.getRentalPermissionLevel(coord, player.getUniqueId());
        if (permission < PermissionLevel.BUILD.level()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🚫 你没有权限破坏此方块（需要租借管理权限）", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ChunkCoordinate coord = new ChunkCoordinate(
            block.getWorld().getName(),
            block.getChunk().getX(),
            block.getChunk().getZ()
        );

        if (!service.isChunkRented(coord)) {
            return;
        }

        int permission = service.getRentalPermissionLevel(coord, player.getUniqueId());
        if (permission < PermissionLevel.BUILD.level()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🚫 你没有权限放置方块（需要租借管理权限）", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Material type = block.getType();

        if (!isInteractiveBlock(type)) {
            return;
        }

        ChunkCoordinate coord = new ChunkCoordinate(
            block.getWorld().getName(),
            block.getChunk().getX(),
            block.getChunk().getZ()
        );

        if (!service.isChunkRented(coord)) {
            return;
        }

        int permission = service.getRentalPermissionLevel(coord, player.getUniqueId());
        if (permission < PermissionLevel.USE.level()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🚫 你没有权限交互此方块", NamedTextColor.RED));
        }
    }

    private boolean isInteractiveBlock(Material type) {
        return INTERACTIVE_BLOCKS.contains(type) || BUILD_BLOCKS.contains(type);
    }
}
