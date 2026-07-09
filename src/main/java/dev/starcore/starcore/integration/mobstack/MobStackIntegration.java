package dev.starcore.starcore.integration.mobstack;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Optional;

/**
 * 生物堆叠集成
 * 支持 StackMob、MobStacker、RoseStacker
 *
 * 目标：减少实体数量，提升服务器性能
 */
public final class MobStackIntegration {
    private final Plugin plugin;

    private boolean stackMobAvailable = false;
    private boolean mobStackerAvailable = false;
    private boolean roseStackerAvailable = false;

    private MobStackProvider provider;

    public MobStackIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化并检测可用的堆叠插件
     */
    public boolean init() {
        PluginManager pm = plugin.getServer().getPluginManager();

        // 检测 StackMob（优先）
        if (pm.getPlugin("StackMob") != null && pm.isPluginEnabled("StackMob")) {
            stackMobAvailable = true;
            provider = new StackMobProvider();
            plugin.getLogger().info("✅ 生物堆叠: StackMob 集成已启用");
            return true;
        }

        // 检测 MobStacker
        if (pm.getPlugin("MobStacker") != null && pm.isPluginEnabled("MobStacker")) {
            mobStackerAvailable = true;
            provider = new MobStackerProvider();
            plugin.getLogger().info("✅ 生物堆叠: MobStacker 集成已启用");
            return true;
        }

        // 检测 RoseStacker
        if (pm.getPlugin("RoseStacker") != null && pm.isPluginEnabled("RoseStacker")) {
            roseStackerAvailable = true;
            provider = new RoseStackerProvider();
            plugin.getLogger().info("✅ 生物堆叠: RoseStacker 集成已启用");
            return true;
        }

        plugin.getLogger().info("⚠️ 未检测到生物堆叠插件（推荐安装 StackMob）");
        return false;
    }

    /**
     * 检查实体是否被堆叠
     */
    public boolean isStacked(Entity entity) {
        if (provider == null) {
            return false;
        }
        return provider.isStacked(entity);
    }

    /**
     * 获取堆叠数量
     */
    public int getStackSize(Entity entity) {
        if (provider == null) {
            return 1;
        }
        return provider.getStackSize(entity);
    }

    /**
     * 设置堆叠数量
     */
    public boolean setStackSize(Entity entity, int size) {
        if (provider == null) {
            return false;
        }
        return provider.setStackSize(entity, size);
    }

    /**
     * 分离一个实体
     */
    public Optional<Entity> splitStack(Entity entity, int amount) {
        if (provider == null) {
            return Optional.empty();
        }
        return provider.splitStack(entity, amount);
    }

    /**
     * 是否启用了生物堆叠
     */
    public boolean isEnabled() {
        return provider != null;
    }

    /**
     * 获取当前使用的插件名称
     */
    public String getProviderName() {
        if (stackMobAvailable) return "StackMob";
        if (mobStackerAvailable) return "MobStacker";
        if (roseStackerAvailable) return "RoseStacker";
        return "None";
    }

    /**
     * 生物堆叠提供者接口
     */
    interface MobStackProvider {
        boolean isStacked(Entity entity);
        int getStackSize(Entity entity);
        boolean setStackSize(Entity entity, int size);
        Optional<Entity> splitStack(Entity entity, int amount);
    }

    /**
     * StackMob 实现
     */
    static class StackMobProvider implements MobStackProvider {
        @Override
        public boolean isStacked(Entity entity) {
            // 通过 NBT 或 API 检查
            return entity.hasMetadata("StackMob") || entity.hasMetadata("stackSize");
        }

        @Override
        public int getStackSize(Entity entity) {
            // 优先检查 StackMob metadata
            if (entity.hasMetadata("stackSize")) {
                return entity.getMetadata("stackSize").get(0).asInt();
            }

            if (entity.hasMetadata("StackMob")) {
                return entity.getMetadata("StackMob").get(0).asInt();
            }

            // 尝试从 CustomName 读取（某些版本）
            String customName = entity.getCustomName();
            if (customName != null && customName.contains("x")) {
                try {
                    String[] parts = customName.split("x");
                    if (parts.length > 0) {
                        return Integer.parseInt(parts[0].trim());
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            return 1;
        }

        @Override
        public boolean setStackSize(Entity entity, int size) {
            if (size <= 0) return false;

            try {
                // 设置 metadata
                entity.setMetadata("stackSize",
                    new org.bukkit.metadata.FixedMetadataValue(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("STARCORE"),
                        size
                    )
                );

                // 更新 CustomName 显示
                if (size > 1) {
                    entity.setCustomName("§e" + size + "x §f" + entity.getType().name());
                    entity.setCustomNameVisible(true);
                } else {
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Optional<Entity> splitStack(Entity entity, int amount) {
            int currentSize = getStackSize(entity);

            if (amount <= 0 || amount >= currentSize) {
                return Optional.empty();
            }

            // 减少当前堆叠数量
            setStackSize(entity, currentSize - amount);

            // 生成新实体
            try {
                Entity newEntity = entity.getWorld().spawnEntity(
                    entity.getLocation().clone().add(1, 0, 1),
                    entity.getType()
                );

                setStackSize(newEntity, amount);
                return Optional.of(newEntity);
            } catch (Exception e) {
                // 恢复原始数量
                setStackSize(entity, currentSize);
                return Optional.empty();
            }
        }
    }

    /**
     * MobStacker 实现
     */
    static class MobStackerProvider implements MobStackProvider {
        @Override
        public boolean isStacked(Entity entity) {
            return entity.hasMetadata("MobStacker") ||
                   entity.hasMetadata("MobStacker_stackSize");
        }

        @Override
        public int getStackSize(Entity entity) {
            // 检查 MobStacker metadata
            if (entity.hasMetadata("MobStacker_stackSize")) {
                return entity.getMetadata("MobStacker_stackSize").get(0).asInt();
            }

            if (entity.hasMetadata("MobStacker")) {
                return entity.getMetadata("MobStacker").get(0).asInt();
            }

            // 尝试从 CustomName 解析
            String customName = entity.getCustomName();
            if (customName != null && customName.contains("x")) {
                try {
                    // MobStacker 格式: "5x Zombie"
                    String[] parts = customName.split("x");
                    if (parts.length > 0) {
                        String numStr = parts[0].replaceAll("[^0-9]", "");
                        return Integer.parseInt(numStr);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            return 1;
        }

        @Override
        public boolean setStackSize(Entity entity, int size) {
            if (size <= 0) return false;

            try {
                entity.setMetadata("MobStacker_stackSize",
                    new org.bukkit.metadata.FixedMetadataValue(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("STARCORE"),
                        size
                    )
                );

                // 更新显示名称
                if (size > 1) {
                    entity.setCustomName("§6" + size + "x §e" + entity.getType().name());
                    entity.setCustomNameVisible(true);
                } else {
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Optional<Entity> splitStack(Entity entity, int amount) {
            int currentSize = getStackSize(entity);

            if (amount <= 0 || amount >= currentSize) {
                return Optional.empty();
            }

            setStackSize(entity, currentSize - amount);

            try {
                Entity newEntity = entity.getWorld().spawnEntity(
                    entity.getLocation().clone().add(1, 0, 1),
                    entity.getType()
                );

                setStackSize(newEntity, amount);
                return Optional.of(newEntity);
            } catch (Exception e) {
                setStackSize(entity, currentSize);
                return Optional.empty();
            }
        }
    }

    /**
     * RoseStacker 实现
     */
    static class RoseStackerProvider implements MobStackProvider {
        @Override
        public boolean isStacked(Entity entity) {
            return entity.hasMetadata("RoseStacker") ||
                   entity.hasMetadata("RoseStacker_StackSize");
        }

        @Override
        public int getStackSize(Entity entity) {
            // RoseStacker 的标准 metadata
            if (entity.hasMetadata("RoseStacker_StackSize")) {
                return entity.getMetadata("RoseStacker_StackSize").get(0).asInt();
            }

            if (entity.hasMetadata("RoseStacker")) {
                return entity.getMetadata("RoseStacker").get(0).asInt();
            }

            // 尝试从 CustomName 解析
            String customName = entity.getCustomName();
            if (customName != null) {
                // RoseStacker 格式: "§6§lx5§r §eZombie"
                try {
                    if (customName.contains("x")) {
                        String[] parts = customName.split("x");
                        if (parts.length > 1) {
                            String numStr = parts[1].split("§")[0].replaceAll("[^0-9]", "");
                            if (!numStr.isEmpty()) {
                                return Integer.parseInt(numStr);
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            return 1;
        }

        @Override
        public boolean setStackSize(Entity entity, int size) {
            if (size <= 0) return false;

            try {
                entity.setMetadata("RoseStacker_StackSize",
                    new org.bukkit.metadata.FixedMetadataValue(
                        org.bukkit.Bukkit.getPluginManager().getPlugin("STARCORE"),
                        size
                    )
                );

                // 更新显示名称（RoseStacker 风格）
                if (size > 1) {
                    entity.setCustomName("§6§lx" + size + "§r §e" + entity.getType().name());
                    entity.setCustomNameVisible(true);
                } else {
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Optional<Entity> splitStack(Entity entity, int amount) {
            int currentSize = getStackSize(entity);

            if (amount <= 0 || amount >= currentSize) {
                return Optional.empty();
            }

            setStackSize(entity, currentSize - amount);

            try {
                Entity newEntity = entity.getWorld().spawnEntity(
                    entity.getLocation().clone().add(1, 0, 1),
                    entity.getType()
                );

                setStackSize(newEntity, amount);
                return Optional.of(newEntity);
            } catch (Exception e) {
                setStackSize(entity, currentSize);
                return Optional.empty();
            }
        }
    }
}
