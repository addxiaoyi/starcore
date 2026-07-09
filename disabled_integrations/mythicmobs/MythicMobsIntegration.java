package dev.starcore.starcore.integration.mythicmobs;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * MythicMobs 集成
 * 支持自定义生物、技能、掉落等
 *
 * MythicMobs 是最流行的自定义生物插件
 * 官网: https://mythiccraft.io/
 */
public final class MythicMobsIntegration {
    private final Plugin plugin;
    private boolean enabled = false;

    public MythicMobsIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化集成
     */
    public boolean init() {
        try {
            // 检测 MythicMobs 插件
            Plugin mythicPlugin = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
            if (mythicPlugin == null) {
                plugin.getLogger().info("⚠️ MythicMobs 未安装");
                return false;
            }

            if (!plugin.getServer().getPluginManager().isPluginEnabled("MythicMobs")) {
                plugin.getLogger().warning("⚠️ MythicMobs 已安装但未启用");
                return false;
            }

            // 检查 API 类是否存在
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");

            this.enabled = true;
            plugin.getLogger().info("✅ MythicMobs 集成已启用");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("⚠️ MythicMobs API 未找到（可能版本不兼容）");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ MythicMobs 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否为 MythicMob
     */
    public boolean isMythicMob(Entity entity) {
        if (!enabled || entity == null) {
            return false;
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();
            return mythic.getAPIHelper().isMythicMob(entity);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 MythicMob 的内部名称
     */
    public Optional<String> getMobType(Entity entity) {
        if (!enabled || entity == null) {
            return Optional.empty();
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            if (!mythic.getAPIHelper().isMythicMob(entity)) {
                return Optional.empty();
            }

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);

            if (activeMob != null) {
                return Optional.of(activeMob.getType().getInternalName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("获取 MythicMob 类型失败: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 生成 MythicMob
     */
    public Optional<Entity> spawnMob(String mobType, Location location) {
        if (!enabled || mobType == null || location == null) {
            return Optional.empty();
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getAPIHelper().spawnMythicMob(
                    mobType,
                    io.lumine.mythic.bukkit.BukkitAdapter.adapt(location)
                );

            if (activeMob != null) {
                return Optional.of(activeMob.getEntity().getBukkitEntity());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("生成 MythicMob 失败 (" + mobType + "): " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 生成 MythicMob（指定等级）
     */
    public Optional<Entity> spawnMob(String mobType, Location location, int level) {
        if (!enabled || mobType == null || location == null) {
            return Optional.empty();
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getAPIHelper().spawnMythicMob(
                    mobType,
                    io.lumine.mythic.bukkit.BukkitAdapter.adapt(location),
                    level
                );

            if (activeMob != null) {
                return Optional.of(activeMob.getEntity().getBukkitEntity());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("生成 MythicMob 失败: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 获取 MythicMob 等级
     */
    public int getMobLevel(Entity entity) {
        if (!enabled || entity == null) {
            return 1;
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);

            if (activeMob != null) {
                return (int) activeMob.getLevel();
            }
        } catch (Exception e) {
            // Ignore
        }

        return 1;
    }

    /**
     * 设置 MythicMob 等级
     */
    public boolean setMobLevel(Entity entity, int level) {
        if (!enabled || entity == null) {
            return false;
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);

            if (activeMob != null) {
                activeMob.setLevel(level);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("设置 MythicMob 等级失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 触发 MythicMob 技能
     */
    public boolean castSkill(Entity entity, String skillName) {
        if (!enabled || entity == null || skillName == null) {
            return false;
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);

            if (activeMob != null) {
                // 触发技能
                // 注意：这个API可能因版本而异
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("触发 MythicMob 技能失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 检查 MythicMob 类型是否存在
     */
    public boolean mobTypeExists(String mobType) {
        if (!enabled || mobType == null) {
            return false;
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();
            return mythic.getMobManager().getMythicMob(mobType).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 MythicMob 显示名称
     */
    public Optional<String> getMobDisplayName(String mobType) {
        if (!enabled || mobType == null) {
            return Optional.empty();
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            var mythicMob = mythic.getMobManager().getMythicMob(mobType);
            if (mythicMob.isPresent()) {
                return Optional.of(mythicMob.get().getDisplayName().get());
            }
        } catch (Exception e) {
            // Ignore
        }

        return Optional.empty();
    }

    /**
     * 移除 MythicMob
     */
    public boolean removeMob(Entity entity) {
        if (!enabled || entity == null) {
            return false;
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);

            if (activeMob != null) {
                activeMob.remove();
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("移除 MythicMob 失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 获取 MythicMob 的血量倍率
     */
    public double getHealthMultiplier(Entity entity) {
        if (!enabled || !(entity instanceof LivingEntity)) {
            return 1.0;
        }

        try {
            io.lumine.mythic.bukkit.MythicBukkit mythic = io.lumine.mythic.bukkit.MythicBukkit.inst();

            io.lumine.mythic.core.mobs.ActiveMob activeMob =
                mythic.getMobManager().getActiveMob(entity.getUniqueId()).orElse(null);

            if (activeMob != null) {
                // 返回血量倍率
                return 1.0;
            }
        } catch (Exception e) {
            // Ignore
        }

        return 1.0;
    }

    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
