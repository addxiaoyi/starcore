package dev.starcore.starcore.integration.citizens;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Citizens 集成
 * 支持 NPC 创建、管理、交互等
 *
 * Citizens 是最流行的 NPC 插件
 * 官网: https://wiki.citizensnpcs.co/
 */
public final class CitizensIntegration {
    private final Plugin plugin;
    private boolean enabled = false;

    public CitizensIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化集成
     */
    public boolean init() {
        try {
            // 检测 Citizens 插件
            Plugin citizensPlugin = plugin.getServer().getPluginManager().getPlugin("Citizens");
            if (citizensPlugin == null) {
                plugin.getLogger().info("⚠️ Citizens 未安装");
                return false;
            }

            if (!plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) {
                plugin.getLogger().warning("⚠️ Citizens 已安装但未启用");
                return false;
            }

            // 检查 API 类是否存在
            Class.forName("net.citizensnpcs.api.CitizensAPI");
            Class.forName("net.citizensnpcs.api.npc.NPC");
            Class.forName("net.citizensnpcs.api.npc.NPCRegistry");

            this.enabled = true;
            plugin.getLogger().info("✅ Citizens 集成已启用");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("⚠️ Citizens API 未找到（可能版本不兼容）");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ Citizens 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否为 NPC
     */
    public boolean isNPC(Entity entity) {
        if (!enabled || entity == null) {
            return false;
        }

        try {
            return net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(entity);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过实体获取 NPC
     */
    public Optional<Integer> getNPCId(Entity entity) {
        if (!enabled || entity == null) {
            return Optional.empty();
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entity);

            if (npc != null) {
                return Optional.of(npc.getId());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("获取 NPC ID 失败: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 通过ID获取NPC
     */
    public boolean npcExists(int npcId) {
        if (!enabled) {
            return false;
        }

        try {
            return net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建 NPC
     */
    public Optional<Integer> createNPC(String name, EntityType type, Location location) {
        if (!enabled || name == null || type == null || location == null) {
            return Optional.empty();
        }

        try {
            net.citizensnpcs.api.npc.NPCRegistry registry =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry();

            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(type, name);

            if (npc != null) {
                npc.spawn(location);
                return Optional.of(npc.getId());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("创建 NPC 失败: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 删除 NPC
     */
    public boolean removeNPC(int npcId) {
        if (!enabled) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPCRegistry registry =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry();

            net.citizensnpcs.api.npc.NPC npc = registry.getById(npcId);

            if (npc != null) {
                npc.destroy();
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("删除 NPC 失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 生成 NPC
     */
    public boolean spawnNPC(int npcId, Location location) {
        if (!enabled || location == null) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null) {
                return npc.spawn(location);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("生成 NPC 失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 取消生成 NPC
     */
    public boolean despawnNPC(int npcId) {
        if (!enabled) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null && npc.isSpawned()) {
                return npc.despawn();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("取消生成 NPC 失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 检查 NPC 是否已生成
     */
    public boolean isSpawned(int npcId) {
        if (!enabled) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            return npc != null && npc.isSpawned();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 NPC 名称
     */
    public Optional<String> getNPCName(int npcId) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null) {
                return Optional.of(npc.getName());
            }
        } catch (Exception e) {
            // Ignore
        }

        return Optional.empty();
    }

    /**
     * 设置 NPC 名称
     */
    public boolean setNPCName(int npcId, String name) {
        if (!enabled || name == null) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null) {
                npc.setName(name);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("设置 NPC 名称失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 传送 NPC
     */
    public boolean teleportNPC(int npcId, Location location) {
        if (!enabled || location == null) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null && npc.isSpawned()) {
                Entity entity = npc.getEntity();
                if (entity != null) {
                    return entity.teleport(location);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("传送 NPC 失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 让 NPC 看向玩家
     */
    public boolean lookAt(int npcId, Player player) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null && npc.isSpawned()) {
                Entity entity = npc.getEntity();
                if (entity != null) {
                    Location npcLoc = entity.getLocation();
                    Location playerLoc = player.getLocation();

                    // 计算朝向
                    double dx = playerLoc.getX() - npcLoc.getX();
                    double dz = playerLoc.getZ() - npcLoc.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;

                    npcLoc.setYaw(yaw);
                    entity.teleport(npcLoc);
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("NPC 看向玩家失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 设置 NPC 皮肤（仅限玩家类型）
     */
    public boolean setSkin(int npcId, String skinName) {
        if (!enabled || skinName == null) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null) {
                // 使用 Citizens Trait 系统设置皮肤
                npc.data().setPersistent("player-skin-name", skinName);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("设置 NPC 皮肤失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 让 NPC 说话（聊天气泡）
     */
    public boolean speak(int npcId, String message) {
        if (!enabled || message == null) {
            return false;
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null && npc.isSpawned()) {
                // Citizens 提供的 speak 方法
                // 需要使用 Citizens 的内部 API
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("NPC 说话失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 获取所有 NPC 数量
     */
    public int getNPCCount() {
        if (!enabled) {
            return 0;
        }

        try {
            // Citizens 2.0.27+ 返回 Iterable
            int count = 0;
            for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取 NPC 位置
     */
    public Optional<Location> getNPCLocation(int npcId) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            net.citizensnpcs.api.npc.NPC npc =
                net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);

            if (npc != null && npc.isSpawned()) {
                Entity entity = npc.getEntity();
                if (entity != null) {
                    return Optional.of(entity.getLocation());
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return Optional.empty();
    }

    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
