package dev.starcore.starcore.essentials.home;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 家园系统服务
 * 支持多家园设置
 */
public final class HomeService {
    private final int maxHomes;

    // 玩家家园数据 UUID -> (家园名称 -> 位置)
    private final ConcurrentHashMap<UUID, Map<String, Location>> playerHomes = new ConcurrentHashMap<>();

    public HomeService(int maxHomes) {
        this.maxHomes = maxHomes;
    }

    /**
     * 设置家园
     */
    public boolean setHome(Player player, String name) {
        UUID playerId = player.getUniqueId();

        Map<String, Location> homes = playerHomes.computeIfAbsent(
            playerId,
            k -> new ConcurrentHashMap<>()
        );

        // 检查数量限制
        if (!homes.containsKey(name) && homes.size() >= maxHomes) {
            player.sendMessage("§c你已达到家园数量上限（" + maxHomes + "个）");
            return false;
        }

        // 保存家园
        homes.put(name, player.getLocation().clone());
        player.sendMessage("§a已设置家园: " + name);

        return true;
    }

    /**
     * 删除家园
     */
    public boolean deleteHome(Player player, String name) {
        UUID playerId = player.getUniqueId();
        Map<String, Location> homes = playerHomes.get(playerId);

        if (homes == null || !homes.containsKey(name)) {
            player.sendMessage("§c家园不存在: " + name);
            return false;
        }

        homes.remove(name);
        player.sendMessage("§a已删除家园: " + name);

        return true;
    }

    /**
     * 获取家园位置
     */
    public Optional<Location> getHome(UUID playerId, String name) {
        Map<String, Location> homes = playerHomes.get(playerId);

        if (homes == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(homes.get(name));
    }

    /**
     * 获取所有家园
     */
    public List<String> getHomeNames(UUID playerId) {
        Map<String, Location> homes = playerHomes.get(playerId);

        if (homes == null || homes.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(homes.keySet());
    }

    /**
     * 传送到家园
     */
    public boolean teleportToHome(Player player, String name) {
        Optional<Location> homeOpt = getHome(player.getUniqueId(), name);

        if (homeOpt.isEmpty()) {
            player.sendMessage("§c家园不存在: " + name);
            return false;
        }

        player.teleport(homeOpt.get());
        player.sendMessage("§a已传送到家园: " + name);

        return true;
    }

    /**
     * 列出所有家园
     */
    public void listHomes(Player player) {
        List<String> homes = getHomeNames(player.getUniqueId());

        if (homes.isEmpty()) {
            player.sendMessage("§c你还没有设置任何家园");
            player.sendMessage("§7使用 /sethome <名称> 来设置家园");
            return;
        }

        player.sendMessage("§a你的家园列表:");
        for (String name : homes) {
            player.sendMessage("§7 - " + name);
        }
        player.sendMessage("§7使用 /home <名称> 来传送");
    }

    /**
     * 加载玩家数据
     */
    public void loadPlayerData(UUID playerId, Map<String, Location> homes) {
        playerHomes.put(playerId, new ConcurrentHashMap<>(homes));
    }

    /**
     * 保存玩家数据
     */
    public Map<String, Location> getPlayerData(UUID playerId) {
        Map<String, Location> homes = playerHomes.get(playerId);
        return homes != null ? new HashMap<>(homes) : new HashMap<>();
    }

    /**
     * 清理玩家数据
     */
    public void cleanup(UUID playerId) {
        playerHomes.remove(playerId);
    }
}
