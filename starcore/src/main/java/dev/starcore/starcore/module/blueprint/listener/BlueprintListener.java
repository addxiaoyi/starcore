package dev.starcore.starcore.module.blueprint.listener;
import java.util.Optional;

import dev.starcore.starcore.module.blueprint.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蓝图事件监听器
 */
public class BlueprintListener implements Listener {
    private static final String SELECTION_TOOL_MATERIAL = "WOODEN_HOE"; // 选区工具
    private static final String BLUEPRINT_TOOL_MATERIAL = "GOLDEN_HOE"; // 蓝图工具

    private final JavaPlugin plugin;
    private final BlueprintServiceImpl service;

    // 玩家选区状态
    private final Map<UUID, SelectionState> playerSelections = new ConcurrentHashMap<>();
    // 玩家编辑模式
    private final Set<UUID> editModePlayers = new HashSet<>();
    // 玩家搜索 GUI 映射（用于处理聊天搜索输入）
    private final Map<UUID, BlueprintGui> searchingPlayers = new ConcurrentHashMap<>();

    // 最大选区大小限制
    private static final int MAX_SELECTION_SIZE = 1000000; // 100万方块

    public BlueprintListener(JavaPlugin plugin, BlueprintServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    /**
     * 玩家选择状态
     */
    private static class SelectionState {
        BlockVector3 pos1;
        BlockVector3 pos2;
        String worldName;

        boolean hasCompleteSelection() {
            return pos1 != null && pos2 != null && worldName != null;
        }

        Optional<RegionSelection> toRegion() {
            if (!hasCompleteSelection()) return Optional.empty();
            return Optional.of(new CuboidRegion(worldName, pos1, pos2));
        }

        void clear() {
            pos1 = null;
            pos2 = null;
            worldName = null;
        }
    }

    /**
     * 处理玩家退出
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 清理玩家状态
        playerSelections.remove(playerId);
        editModePlayers.remove(playerId);
        searchingPlayers.remove(playerId);
    }

    /**
     * 处理聊天输入（用于搜索）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 检查是否在等待搜索输入
        BlueprintGui gui = searchingPlayers.get(playerId);
        if (gui != null && gui.isAwaitingSearchInput()) {
            event.setCancelled(true);
            String message = event.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> {
                gui.handleSearchInput(message);
                searchingPlayers.remove(playerId);
            });
        }
    }

    /**
     * 注册正在等待搜索输入的玩家 GUI
     */
    public void registerSearchInput(UUID playerId, BlueprintGui gui) {
        searchingPlayers.put(playerId, gui);
    }

    /**
     * 注销搜索输入
     */
    public void unregisterSearchInput(UUID playerId) {
        searchingPlayers.remove(playerId);
    }

    /**
     * 处理玩家交互（用于选区工具）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 只处理主手
        if (event.getHand() != EquipmentSlot.HAND) return;

        // 检查是否使用选区工具
        if (!isSelectionTool(player.getInventory().getItemInMainHand())) {
            return;
        }

        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            // 设置选区点1
            Location loc = event.getClickedBlock().getLocation();
            SelectionState state = getOrCreateSelection(playerId, loc.getWorld().getName());
            state.pos1 = BlockVector3.fromLocation(loc);
            event.setCancelled(true);

            player.sendActionBar(Component.text("Position 1 set: " + state.pos1, NamedTextColor.GREEN));

        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // 设置选区点2
            Location loc = event.getClickedBlock().getLocation();
            SelectionState state = getOrCreateSelection(playerId, loc.getWorld().getName());
            state.pos2 = BlockVector3.fromLocation(loc);
            event.setCancelled(true);

            player.sendActionBar(Component.text("Position 2 set: " + state.pos2, NamedTextColor.GREEN));

            // 如果选区完整，显示信息
            if (state.hasCompleteSelection()) {
                showSelectionInfo(player, state);
            }
        }
    }

    /**
     * 处理方块破坏（显示选区边框）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查是否是选区边框
        SelectionState state = playerSelections.get(playerId);
        if (state == null || !state.hasCompleteSelection()) {
            return;
        }

        Location loc = event.getBlock().getLocation();
        BlockVector3 pos = BlockVector3.fromLocation(loc);

        // 检查是否是选区点
        if (pos.equals(state.pos1) || pos.equals(state.pos2)) {
            // 清除被破坏的点
            if (pos.equals(state.pos1)) {
                state.pos1 = null;
            }
            if (pos.equals(state.pos2)) {
                state.pos2 = null;
            }
        }
    }

    /**
     * 显示选区信息
     */
    private void showSelectionInfo(Player player, SelectionState state) {
        Optional<RegionSelection> optRegion = state.toRegion();
        if (optRegion.isEmpty()) return;
        RegionSelection region = optRegion.get();

        int blocks = region.getBlockCount();

        if (blocks > MAX_SELECTION_SIZE) {
            player.sendActionBar(Component.text("Selection too large! Max: " + MAX_SELECTION_SIZE + " blocks",
                NamedTextColor.RED));
            return;
        }

        String info = String.format("%dx%dx%d = %,d blocks",
            region.getWidth(), region.getHeight(), region.getDepth(), blocks);

        player.sendActionBar(Component.text(info, NamedTextColor.AQUA));

        // 发送聊天消息
        player.sendMessage(Component.text()
            .append(Component.text("Selection: ", NamedTextColor.GOLD))
            .append(Component.text(region.getType(), NamedTextColor.AQUA))
            .append(Component.text(" " + info, NamedTextColor.GRAY)));
    }

    /**
     * 获取或创建选区状态
     */
    private SelectionState getOrCreateSelection(UUID playerId, String worldName) {
        SelectionState state = playerSelections.get(playerId);
        if (state == null) {
            state = new SelectionState();
            state.worldName = worldName;
            playerSelections.put(playerId, state);
        } else if (!worldName.equals(state.worldName)) {
            // 世界改变，清空选区
            state.clear();
            state.worldName = worldName;
        }
        return state;
    }

    /**
     * 检查是否是选区工具
     */
    private boolean isSelectionTool(org.bukkit.inventory.ItemStack item) {
        if (item == null) return false;
        return item.getType() == org.bukkit.Material.valueOf(SELECTION_TOOL_MATERIAL) ||
               item.getType() == org.bukkit.Material.GOLDEN_HOE ||
               item.getType() == org.bukkit.Material.WOODEN_HOE;
    }

    /**
     * 检查是否是蓝图工具
     */
    private boolean isBlueprintTool(org.bukkit.inventory.ItemStack item) {
        if (item == null) return false;
        return item.getType() == org.bukkit.Material.valueOf(BLUEPRINT_TOOL_MATERIAL);
    }

    /**
     * 获取玩家的选区
     */
    public Optional<RegionSelection> getPlayerSelection(UUID playerId) {
        SelectionState state = playerSelections.get(playerId);
        if (state == null || !state.hasCompleteSelection()) {
            return Optional.empty();
        }
        return state.toRegion();
    }

    /**
     * 清除玩家的选区
     */
    public void clearPlayerSelection(UUID playerId) {
        SelectionState state = playerSelections.get(playerId);
        if (state != null) {
            state.clear();
        }
    }

    /**
     * 检查玩家是否在编辑模式
     */
    public boolean isEditMode(UUID playerId) {
        return editModePlayers.contains(playerId);
    }

    /**
     * 设置玩家编辑模式
     */
    public void setEditMode(UUID playerId, boolean enabled) {
        if (enabled) {
            editModePlayers.add(playerId);
        } else {
            editModePlayers.remove(playerId);
        }
    }

    /**
     * 注销监听器
     */
    public void unregister() {
        playerSelections.clear();
        editModePlayers.clear();
        searchingPlayers.clear();
    }
}
