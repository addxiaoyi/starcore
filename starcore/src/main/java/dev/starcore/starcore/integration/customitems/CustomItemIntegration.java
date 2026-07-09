package dev.starcore.starcore.integration.customitems;

// import dev.starcore.starcore.integration.itemadder.ItemsAdderIntegration; // Disabled - missing dependency
import dev.starcore.starcore.integration.oraxen.OraxenIntegration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * 自定义物品集成管理器
 * 统一管理 ItemsAdder、Oraxen 等自定义物品插件
 */
public final class CustomItemIntegration {
    private final Plugin plugin;

    // private ItemsAdderIntegration itemsAdder; // Disabled - missing dependency
    private OraxenIntegration oraxen;

    private CustomItemProvider activeProvider;

    public CustomItemIntegration(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化集成
     * 自动检测并选择可用的插件
     */
    public boolean init() {
        // ItemsAdder disabled - missing dependency
        /*
        itemsAdder = new ItemsAdderIntegration(plugin);
        if (itemsAdder.init()) {
            activeProvider = CustomItemProvider.ITEMS_ADDER;
            plugin.getLogger().info("✅ 自定义物品集成: 使用 ItemsAdder");
            return true;
        }
        */

        // 初始化 Oraxen
        oraxen = new OraxenIntegration(plugin);
        if (oraxen.init()) {
            activeProvider = CustomItemProvider.ORAXEN;
            plugin.getLogger().info("✅ 自定义物品集成: 使用 Oraxen");
            return true;
        }

        plugin.getLogger().info("⚠️ 未检测到自定义物品插件（ItemsAdder 或 Oraxen）");
        activeProvider = CustomItemProvider.NONE;
        return false;
    }

    /**
     * 检查是否为自定义物品
     */
    public boolean isCustomItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        return switch (activeProvider) {
            // case ITEMS_ADDER -> itemsAdder.isCustomItem(item); // Disabled
            case ORAXEN -> oraxen.isCustomItem(item);
            case NONE -> false;
            default -> false;
        };
    }

    /**
     * 获取自定义物品ID
     */
    public Optional<String> getCustomItemId(ItemStack item) {
        if (item == null) {
            return Optional.empty();
        }

        return switch (activeProvider) {
            // case ITEMS_ADDER -> itemsAdder.getCustomItemId(item); // Disabled
            case ORAXEN -> oraxen.getCustomItemId(item);
            case NONE -> Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * 通过ID创建自定义物品
     */
    public Optional<ItemStack> getCustomItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Optional.empty();
        }

        return switch (activeProvider) {
            // case ITEMS_ADDER -> itemsAdder.getCustomItem(itemId); // Disabled
            case ORAXEN -> oraxen.getCustomItem(itemId);
            case NONE -> Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * 通过ID创建自定义物品（指定数量）
     */
    public Optional<ItemStack> getCustomItem(String itemId, int amount) {
        return switch (activeProvider) {
            // case ITEMS_ADDER -> itemsAdder.getCustomItem(itemId, amount); // Disabled
            case ORAXEN -> oraxen.getCustomItem(itemId, amount);
            case NONE -> Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * 给予玩家自定义物品
     */
    public boolean giveCustomItem(Player player, String itemId, int amount) {
        if (player == null) {
            return false;
        }

        return switch (activeProvider) {
            // case ITEMS_ADDER -> itemsAdder.giveCustomItem(player, itemId, amount); // Disabled
            case ORAXEN -> oraxen.giveCustomItem(player, itemId, amount);
            case NONE -> false;
            default -> false;
        };
    }

    /**
     * 检查玩家是否拥有自定义物品
     */
    public boolean hasCustomItem(Player player, String itemId) {
        if (player == null) {
            return false;
        }

        return switch (activeProvider) {
            // case ITEMS_ADDER -> itemsAdder.hasCustomItem(player, itemId); // Disabled
            case ORAXEN -> oraxen.hasCustomItem(player, itemId);
            case NONE -> false;
            default -> false;
        };
    }

    /**
     * 检查自定义物品是否存在
     */
    public boolean customItemExists(String itemId) {
        return switch (activeProvider) {
            // case ITEMS_ADDER -> itemsAdder.customItemExists(itemId); // Disabled
            case ORAXEN -> oraxen.customItemExists(itemId);
            case NONE -> false;
            default -> false;
        };
    }

    /**
     * 是否已启用
     */
    public boolean isEnabled() {
        return activeProvider != CustomItemProvider.NONE;
    }

    /**
     * 获取当前提供者
     */
    public CustomItemProvider getProvider() {
        return activeProvider;
    }

    /**
     * 获取提供者名称
     */
    public String getProviderName() {
        return switch (activeProvider) {
            case ITEMS_ADDER -> "ItemsAdder";
            case ORAXEN -> "Oraxen";
            case NONE -> "None";
        };
    }

    /**
     * 自定义物品提供者
     */
    public enum CustomItemProvider {
        ITEMS_ADDER,
        ORAXEN,
        NONE
    }
}
