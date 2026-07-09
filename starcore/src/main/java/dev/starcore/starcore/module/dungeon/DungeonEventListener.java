package dev.starcore.starcore.module.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 副本事件监听器
 * 处理副本内的所有事件
 */
public class DungeonEventListener implements Listener {
    private final DungeonServiceImpl service;
    private final DungeonConfig config;

    // 玩家最后位置（用于防作弊）
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    public DungeonEventListener(DungeonServiceImpl service, DungeonConfig config) {
        this.service = service;
        this.config = config;
    }

    /**
     * 玩家死亡事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID playerId = event.getEntity().getUniqueId();
        Player player = event.getEntity();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isEmpty()) {
            return;
        }

        // 处理副本死亡
        service.handlePlayerDeath(player);

        // 清空掉落物
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /**
     * 玩家复活事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isEmpty()) {
            return;
        }

        // 副本复活
        service.respawnPlayer(event.getPlayer());
    }

    /**
     * 玩家退出事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isPresent()) {
            // 自动离开副本
            service.leaveDungeon(player);
        }

        // 清理位置缓存
        lastLocations.remove(playerId);
    }

    /**
     * 实体死亡事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        // 检查是否在副本世界中
        World world = event.getEntity().getWorld();
        String worldName = world.getName();

        if (!worldName.startsWith(config.getWorldPrefix())) {
            return;
        }

        // 清空掉落物（奖励由服务处理）
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /**
     * 玩家移动事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().equals(event.getTo())) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isEmpty()) {
            return;
        }

        // 记录最后位置
        lastLocations.put(playerId, event.getFrom());

        // 检查是否离开副本世界边界
        if (config.getWorldPrefix().equals("dungeon_")) {
            Location to = event.getTo();
            // 如果玩家试图离开副本边界，阻止移动
            if (Math.abs(to.getX()) > 500 || Math.abs(to.getZ()) > 500) {
                event.setTo(event.getFrom());
                event.getPlayer().sendMessage("§c你不能离开副本区域!");
            }
        }
    }

    /**
     * 玩家交互事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isEmpty()) {
            return;
        }

        // 检查是否允许使用桶
        if (!config.isBlockInteractionDisabled()) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        // 阻止使用桶
        if (item.getType().name().contains("BUCKET") || item.getType().name().contains("CAULDRON")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c副本中不能使用桶!");
        }
    }

    /**
     * 物品丢弃事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isEmpty()) {
            return;
        }

        // 阻止玩家丢弃物品，防止物品丢失
        event.setCancelled(true);
        event.getPlayer().sendMessage("§c副本中不能丢弃物品!");
    }

    /**
     * 方块破坏事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isEmpty()) {
            return;
        }

        // 检查是否允许破坏方块
        if (config.isBlockInteractionDisabled()) {
            event.setCancelled(true);
        }
    }

    /**
     * 方块放置事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 检查玩家是否在副本中
        if (service.getInstanceByPlayer(playerId).isEmpty()) {
            return;
        }

        // 检查是否允许放置方块
        if (config.isBlockInteractionDisabled()) {
            event.setCancelled(true);
        }
    }

    /**
     * 实体伤害事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        // 检查是否在副本世界中
        World world = event.getEntity().getWorld();
        String worldName = world.getName();

        if (!worldName.startsWith(config.getWorldPrefix())) {
            return;
        }

        // 检查PvP是否禁用 - 禁用时取消玩家被玩家攻击的伤害
        if (config.isPvpDisabled()) {
            if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 玩家伤害玩家事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // 检查是否在副本世界中
        String worldName = victim.getWorld().getName();
        if (!worldName.startsWith(config.getWorldPrefix())) {
            return;
        }

        // 检查PvP设置
        if (config.isPvpDisabled()) {
            event.setCancelled(true);
        }
    }

    /**
     * 获取玩家最后位置
     */
    public Location getLastLocation(UUID playerId) {
        return lastLocations.get(playerId);
    }
}
