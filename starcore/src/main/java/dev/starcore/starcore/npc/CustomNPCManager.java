package dev.starcore.starcore.npc;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义 NPC 管理器
 * 轻量级的内置 NPC 系统
 */
public final class CustomNPCManager implements Listener {
    private final Plugin plugin;

    // 所有 NPC（ID -> NPC）
    private final Map<UUID, CustomNPC> npcs = new ConcurrentHashMap<>();

    // 实体 -> NPC 映射
    private final Map<UUID, UUID> entityToNPC = new ConcurrentHashMap<>();

    // 命令白名单 - 防止命令注入攻击
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        "tp", "teleport", "give", "effect", "particle", "playsound",
        "title", "tellraw", "execute", "summon", "clear", "gamemode",
        "advancement", "experience", "xp", "weather", "time"
    );

    public CustomNPCManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化
     */
    public void init() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("✅ 自定义 NPC 系统已启用");
    }

    /**
     * 创建 NPC
     */
    public CustomNPC createNPC(String name, EntityType type, Location location) {
        UUID id = UUID.randomUUID();
        CustomNPC npc = new CustomNPC(id, name, type, location);
        npcs.put(id, npc);
        return npc;
    }

    /**
     * 生成 NPC
     */
    public boolean spawnNPC(UUID npcId) {
        CustomNPC npc = npcs.get(npcId);
        if (npc == null || npc.isSpawned()) {
            return false;
        }

        try {
            Location loc = npc.getLocation();
            Entity entity = loc.getWorld().spawnEntity(loc, npc.getEntityType());

            if (entity instanceof LivingEntity living) {
                // 设置基本属性
                living.setCustomName(npc.getDisplayName());
                living.setCustomNameVisible(true);
                living.setAI(false);
                living.setCollidable(false);
                living.setInvulnerable(npc.isInvulnerable());
                living.setSilent(true);

                // 设置朝向
                Location entityLoc = entity.getLocation();
                entityLoc.setYaw(npc.getLookYaw());
                entityLoc.setPitch(npc.getLookPitch());
                entity.teleport(entityLoc);

                // 保存实体引用
                npc.setEntity(entity);
                npc.setSpawned(true);
                entityToNPC.put(entity.getUniqueId(), npcId);

                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("生成 NPC 失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 取消生成 NPC
     */
    public boolean despawnNPC(UUID npcId) {
        CustomNPC npc = npcs.get(npcId);
        if (npc == null || !npc.isSpawned()) {
            return false;
        }

        try {
            Entity entity = npc.getEntity();
            if (entity != null && !entity.isDead()) {
                entityToNPC.remove(entity.getUniqueId());
                entity.remove();
            }

            npc.setEntity(null);
            npc.setSpawned(false);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("取消生成 NPC 失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除 NPC
     */
    public boolean removeNPC(UUID npcId) {
        CustomNPC npc = npcs.get(npcId);
        if (npc == null) {
            return false;
        }

        // 先取消生成
        if (npc.isSpawned()) {
            despawnNPC(npcId);
        }

        // 删除记录
        npcs.remove(npcId);
        return true;
    }

    /**
     * 获取 NPC
     */
    public Optional<CustomNPC> getNPC(UUID npcId) {
        return Optional.ofNullable(npcs.get(npcId));
    }

    /**
     * 通过实体获取 NPC
     */
    public Optional<CustomNPC> getNPCByEntity(Entity entity) {
        UUID npcId = entityToNPC.get(entity.getUniqueId());
        if (npcId != null) {
            return getNPC(npcId);
        }
        return Optional.empty();
    }

    /**
     * 检查是否为 NPC
     */
    public boolean isNPC(Entity entity) {
        return entityToNPC.containsKey(entity.getUniqueId());
    }

    /**
     * 让 NPC 看向玩家
     */
    public void lookAtPlayer(CustomNPC npc, Player player) {
        if (!npc.isSpawned() || !npc.isLookAtPlayer()) {
            return;
        }

        Entity entity = npc.getEntity();
        if (entity == null || entity.isDead()) {
            return;
        }

        try {
            Location entityLoc = entity.getLocation();
            Location playerLoc = player.getEyeLocation();

            float yaw = npc.calculateYawTowards(playerLoc);
            float pitch = npc.calculatePitchTowards(playerLoc);

            entityLoc.setYaw(yaw);
            entityLoc.setPitch(pitch);
            entity.teleport(entityLoc);

        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * 传送 NPC
     */
    public boolean teleportNPC(UUID npcId, Location location) {
        CustomNPC npc = npcs.get(npcId);
        if (npc == null) {
            return false;
        }

        npc.setLocation(location);

        if (npc.isSpawned()) {
            Entity entity = npc.getEntity();
            if (entity != null && !entity.isDead()) {
                return entity.teleport(location);
            }
        }

        return true;
    }

    /**
     * 获取所有 NPC
     */
    public Collection<CustomNPC> getAllNPCs() {
        return new ArrayList<>(npcs.values());
    }

    /**
     * 获取 NPC 数量
     */
    public int getNPCCount() {
        return npcs.size();
    }

    /**
     * Tick 更新（让 NPC 看向附近的玩家）
     */
    public void tick() {
        for (CustomNPC npc : npcs.values()) {
            if (!npc.isSpawned() || !npc.isLookAtPlayer()) {
                continue;
            }

            Entity entity = npc.getEntity();
            if (entity == null || entity.isDead()) {
                continue;
            }

            // 查找附近的玩家
            Location npcLoc = npc.getLocation();
            Player nearestPlayer = null;
            double nearestDistance = npc.getInteractionRange();

            for (Player player : npcLoc.getWorld().getPlayers()) {
                double distance = player.getLocation().distance(npcLoc);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }

            // 让 NPC 看向最近的玩家
            if (nearestPlayer != null) {
                lookAtPlayer(npc, nearestPlayer);
            }
        }
    }

    /**
     * 处理玩家交互
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        Optional<CustomNPC> npcOpt = getNPCByEntity(entity);
        if (npcOpt.isEmpty()) {
            return;
        }

        CustomNPC npc = npcOpt.get();
        event.setCancelled(true);

        // 检查权限
        if (npc.getPermission() != null && !player.hasPermission(npc.getPermission())) {
            player.sendMessage("§c你没有权限与这个 NPC 交互");
            return;
        }

        // 检查距离
        if (!npc.isInRange(player.getLocation())) {
            return;
        }

        // 显示对话
        List<String> dialogues = npc.getDialogues();
        if (!dialogues.isEmpty()) {
            for (String dialogue : dialogues) {
                player.sendMessage(dialogue
                    .replace("{player}", player.getName())
                    .replace("{npc}", npc.getDisplayName())
                );
            }
        }

        // 执行命令
        List<String> commands = npc.getCommands();
        if (!commands.isEmpty()) {
            for (String command : commands) {
                executeSecureCommand(player, npc, command);
            }
        }
    }

    /**
     * 安全执行命令 - 防止命令注入攻击
     *
     * @param player 玩家
     * @param npc NPC对象
     * @param command 原始命令
     */
    private void executeSecureCommand(Player player, CustomNPC npc, String command) {
        // 替换占位符
        String cmd = command
            .replace("{player}", player.getName())
            .replace("{uuid}", player.getUniqueId().toString())
            .replace("{npc}", npc.getName());

        // 确定执行类型
        boolean asConsole = true;
        if (cmd.startsWith("[console]")) {
            cmd = cmd.substring(9).trim();
        } else if (cmd.startsWith("[player]")) {
            cmd = cmd.substring(8).trim();
            asConsole = false;
        }

        // 提取命令基础名（第一个单词）
        String baseCommand = cmd.split("\\s+")[0].toLowerCase();

        // 移除可能的 minecraft: 前缀
        if (baseCommand.startsWith("minecraft:")) {
            baseCommand = baseCommand.substring(10);
        }

        // 检查命令白名单
        if (!ALLOWED_COMMANDS.contains(baseCommand)) {
            plugin.getLogger().warning(
                "§c[安全] 拦截非法NPC命令: '" + baseCommand + "' " +
                "(NPC: " + npc.getName() + ", 玩家: " + player.getName() + ")"
            );
            player.sendMessage("§c该NPC尝试执行非授权命令，已被系统拦截");
            return;
        }

        // 执行命令
        try {
            if (asConsole) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else {
                player.performCommand(cmd);
            }
        } catch (Exception e) {
            plugin.getLogger().warning(
                "NPC命令执行失败: " + cmd + " - " + e.getMessage()
            );
        }
    }

    /**
     * 防止 NPC 受伤
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();

        if (isNPC(entity)) {
            Optional<CustomNPC> npcOpt = getNPCByEntity(entity);
            if (npcOpt.isPresent() && npcOpt.get().isInvulnerable()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 关闭并清理
     */
    public void shutdown() {
        // 移除所有 NPC
        for (CustomNPC npc : new ArrayList<>(npcs.values())) {
            if (npc.isSpawned()) {
                despawnNPC(npc.getId());
            }
        }

        npcs.clear();
        entityToNPC.clear();

        plugin.getLogger().info("自定义 NPC 系统已关闭");
    }
}
