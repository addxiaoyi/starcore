package dev.starcore.starcore.storage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 远程访问GUI
 * 提供远程访问仓库的选择界面
 */
public class RemoteAccessGUI {
    private final StorageService storageService;
    private final Player player;
    private Inventory inventory;

    /**
     * 构造函数
     */
    public RemoteAccessGUI(StorageService storageService, Player player) {
        this.storageService = storageService;
        this.player = player;
    }

    /**
     * 打开仓库选择界面
     */
    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§6远程访问 - 选择仓库");

        // 获取玩家可访问的所有仓库
        List<Warehouse> warehouses = storageService.getAccessibleWarehouses(player.getUniqueId());

        int slot = 0;
        for (Warehouse warehouse : warehouses) {
            if (slot >= 45) break;

            RemoteAccessPermission permission = storageService.getPermission(
                    warehouse.getWarehouseId(),
                    player.getUniqueId()
            );

            ItemStack item = createWarehouseItem(warehouse, permission);
            inventory.setItem(slot++, item);
        }

        // 信息按钮
        ItemStack infoButton = createItem(Material.BOOK,
                "§6远程访问说明",
                "§7远程访问允许您在任何地方",
                "§7访问您的仓库",
                "",
                "§7费用: §f" + storageService.getConfig().getRemoteAccessCost() + " 金币/次",
                "§7距离限制: §f" + getDistanceInfo()
        );
        inventory.setItem(49, infoButton);

        // 刷新按钮
        ItemStack refreshButton = createItem(Material.LIME_DYE, "§a刷新列表");
        inventory.setItem(50, refreshButton);

        // 关闭按钮
        ItemStack closeButton = createItem(Material.BARRIER, "§c关闭");
        inventory.setItem(53, closeButton);

        player.openInventory(inventory);
    }

    /**
     * 创建仓库物品
     */
    private ItemStack createWarehouseItem(Warehouse warehouse, RemoteAccessPermission permission) {
        Material material = getWarehouseIcon(warehouse.getType());

        List<String> lore = new ArrayList<>();
        lore.add("§7类型: §f" + warehouse.getType().getDisplayName());
        lore.add("§7等级: §f" + warehouse.getLevel());
        lore.add("§7容量: §f" + warehouse.getUsedCapacity() + "/" + warehouse.getCapacity());
        lore.add("§7使用率: §f" + String.format("%.1f%%", warehouse.getUsagePercentage() * 100));
        lore.add("");
        lore.add("§7您的权限: §f" + permission.getDisplayName());

        if (warehouse.isLocked()) {
            lore.add("§c已锁定");
        }

        // 检查远程访问
        RemoteAccessService.AccessCheckResult checkResult =
                storageService.getRemoteAccessService().canAccess(player, warehouse.getWarehouseId());

        if (checkResult.isSuccess()) {
            lore.add("");
            if (checkResult.getCost().compareTo(java.math.BigDecimal.ZERO) > 0) {
                lore.add("§7费用: §f" + checkResult.getCost() + " 金币");
            }
            lore.add("§e点击访问");
        } else {
            lore.add("");
            lore.add("§c无法访问: " + checkResult.getFailureReason());
        }

        return createItem(material, "§6" + warehouse.getName(), lore.toArray(new String[0]));
    }

    /**
     * 获取仓库图标
     */
    private Material getWarehouseIcon(WarehouseType type) {
        return switch (type) {
            case PERSONAL -> Material.CHEST;
            case NATION -> Material.ENDER_CHEST;
            case SHARED -> Material.BARREL;
            case PREMIUM -> Material.SHULKER_BOX;
        };
    }

    /**
     * 获取距离信息
     */
    private String getDistanceInfo() {
        if (!storageService.getConfig().hasDistanceLimit()) {
            return "无限制";
        }
        return storageService.getConfig().getMaxRemoteDistance() + " 格";
    }

    /**
     * 处理仓库点击
     */
    public void handleWarehouseClick(int slot) {
        List<Warehouse> warehouses = storageService.getAccessibleWarehouses(player.getUniqueId());
        if (slot < 0 || slot >= warehouses.size()) {
            return;
        }

        Warehouse warehouse = warehouses.get(slot);

        // 执行远程访问
        if (storageService.getRemoteAccessService().executeRemoteAccess(player, warehouse.getWarehouseId())) {
            player.closeInventory();
            // 打开仓库GUI
            WarehouseGUI warehouseGUI = new WarehouseGUI(storageService, warehouse, player, true);
            warehouseGUI.open();
        }
    }

    /**
     * 创建物品
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(List.of(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 获取玩家
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * 获取库存
     */
    public Inventory getInventory() {
        return inventory;
    }
}
