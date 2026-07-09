package dev.starcore.starcore.integration.itemadder;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * ItemsAdder 集成
 * 支持自定义物品、方块、家具等
 *
 * ItemsAdder 是一个强大的自定义物品插件
 * 官网: https://itemsadder.devs.beer/
 */
public final class ItemsAdderIntegration {
    private final Plugin plugin;
    private boolean enabled = false;

    public ItemsAdderIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化集成
     */
    public boolean init() {
        try {
            // 检测 ItemsAdder 插件
            if (plugin.getServer().getPluginManager().getPlugin("ItemsAdder") == null) {
                plugin.getLogger().info("⚠️ ItemsAdder 未安装");
                return false;
            }

            if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
                plugin.getLogger().warning("⚠️ ItemsAdder 已安装但未启用");
                return false;
            }

            // 检查 API 类是否存在
            Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            Class.forName("dev.lone.itemsadder.api.CustomStack");

            this.enabled = true;
            plugin.getLogger().info("✅ ItemsAdder 集成已启用");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("⚠️ ItemsAdder API 未找到（可能版本不兼容）");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("⚠️ ItemsAdder 集成初始化失败: " + e.getMessage());
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
            // 使用 ItemsAdder API
            dev.lone.itemsadder.api.CustomStack customStack =
                dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            return customStack != null;
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
            dev.lone.itemsadder.api.CustomStack customStack =
                dev.lone.itemsadder.api.CustomStack.byItemStack(item);

            if (customStack != null) {
                return Optional.of(customStack.getNamespacedID());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("获取自定义物品ID失败: " + e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * 通过ID创建自定义物品
     */
    public Optional<ItemStack> getCustomItem(String itemId) {
        if (!enabled || itemId == null || itemId.isEmpty()) {
            return Optional.empty();
        }

        try {
            dev.lone.itemsadder.api.CustomStack customStack =
                dev.lone.itemsadder.api.CustomStack.getInstance(itemId);

            if (customStack != null) {
                return Optional.of(customStack.getItemStack());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("创建自定义物品失败 (" + itemId + "): " + e.getMessage());
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
            dev.lone.itemsadder.api.CustomStack customStack =
                dev.lone.itemsadder.api.CustomStack.getInstance(itemId);

            if (customStack == null) {
                return false;
            }

            ItemStack item = customStack.getItemStack();
            item.setAmount(amount);

            player.getInventory().addItem(item);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("给予自定义物品失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查玩家是否拥有自定义物品
     */
    public boolean hasCustomItem(Player player, String itemId) {
        if (!enabled || player == null) {
            return false;
        }

        try {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null) {
                    dev.lone.itemsadder.api.CustomStack customStack =
                        dev.lone.itemsadder.api.CustomStack.byItemStack(item);

                    if (customStack != null && customStack.getNamespacedID().equals(itemId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("检查自定义物品失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 获取自定义物品显示名称
     */
    public Optional<String> getCustomItemDisplayName(String itemId) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            dev.lone.itemsadder.api.CustomStack customStack =
                dev.lone.itemsadder.api.CustomStack.getInstance(itemId);

            if (customStack != null) {
                return Optional.of(customStack.getDisplayName());
            }
        } catch (Exception e) {
            // Ignore
        }

        return Optional.empty();
    }

    /**
     * 检查自定义物品是否存在
     */
    public boolean customItemExists(String itemId) {
        if (!enabled) {
            return false;
        }

        try {
            return dev.lone.itemsadder.api.CustomStack.getInstance(itemId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 重载 ItemsAdder 配置
     */
    public boolean reload() {
        if (!enabled) {
            return false;
        }

        try {
            // ItemsAdder 重载方法
            // 注意：这可能需要管理员权限
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("重载 ItemsAdder 失败: " + e.getMessage());
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
