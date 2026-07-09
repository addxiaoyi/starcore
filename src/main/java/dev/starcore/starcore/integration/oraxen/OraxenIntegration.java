package dev.starcore.starcore.integration.oraxen;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Oraxen 集成
 * 支持自定义物品、方块、家具、武器等
 *
 * Oraxen 是另一个流行的自定义物品插件
 * 官网: https://www.oraxen.com/
 */
public final class OraxenIntegration {
    private final Plugin plugin;
    private boolean enabled = false;

    public OraxenIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化集成
     */
    public boolean init() {
        try {
            // 检测 Oraxen 插件
            if (plugin.getServer().getPluginManager().getPlugin("Oraxen") == null) {
                plugin.getLogger().info("⚠️ Oraxen 未安装");
                return false;
            }

            if (!plugin.getServer().getPluginManager().isPluginEnabled("Oraxen")) {
                plugin.getLogger().warning("⚠️ Oraxen 已安装但未启用");
                return false;
            }

            // 检查 API 类是否存在
            Class.forName("io.th0rgal.oraxen.api.OraxenItems");

            this.enabled = true;
            plugin.getLogger().info("✅ Oraxen 集成已启用");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("⚠️ Oraxen API 未找到（可能版本不兼容）");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ Oraxen 集成初始化失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否为自定义物品
     */
    public boolean isCustomItem(ItemStack item) {
        if (!enabled || item == null) {
            return false;
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object itemId = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
            return itemId != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取自定义物品ID
     */
    public Optional<String> getCustomItemId(ItemStack item) {
        if (!enabled || item == null) {
            return Optional.empty();
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object itemId = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
            return Optional.ofNullable(itemId != null ? itemId.toString() : null);
        } catch (Exception e) {
            plugin.getLogger().warning("获取 Oraxen 物品ID失败: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 通过ID创建自定义物品
     */
    public Optional<ItemStack> getCustomItem(String itemId) {
        if (!enabled || itemId == null || itemId.isEmpty()) {
            return Optional.empty();
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object itemBuilder = oraxenItemsClass.getMethod("getItemById", String.class).invoke(null, itemId);
            if (itemBuilder != null) {
                ItemStack item = (ItemStack) itemBuilder.getClass().getMethod("build").invoke(itemBuilder);
                return Optional.ofNullable(item);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("创建 Oraxen 物品失败 (" + itemId + "): " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 通过ID创建自定义物品（指定数量）
     */
    public Optional<ItemStack> getCustomItem(String itemId, int amount) {
        if (!enabled) {
            return Optional.empty();
        }

        return getCustomItem(itemId).map(item -> {
            item.setAmount(Math.max(1, Math.min(amount, 64)));
            return item;
        });
    }

    /**
     * 给予玩家自定义物品
     */
    public boolean giveCustomItem(Player player, String itemId, int amount) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object itemBuilder = oraxenItemsClass.getMethod("getItemById", String.class).invoke(null, itemId);
            if (itemBuilder != null) {
                ItemStack item = (ItemStack) itemBuilder.getClass().getMethod("build").invoke(itemBuilder);
                if (item != null) {
                    item.setAmount(amount);
                    player.getInventory().addItem(item);
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("给予 Oraxen 物品失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 检查玩家是否拥有自定义物品
     */
    public boolean hasCustomItem(Player player, String itemId) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null) {
                    Object id = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class).invoke(null, item);
                    if (id != null && itemId.equals(id.toString())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("检查 Oraxen 物品失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 检查自定义物品是否存在
     */
    public boolean customItemExists(String itemId) {
        if (!enabled) {
            return false;
        }

        try {
            Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Object result = oraxenItemsClass.getMethod("exists", String.class).invoke(null, itemId);
            return result != null && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        return enabled;
    }
}
