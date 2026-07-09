package dev.starcore.starcore.storage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 仓库GUI界面
 * 提供可视化的仓库管理界面
 */
public class WarehouseGUI {
    private final StorageService storageService;
    private final Warehouse warehouse;
    private final Player viewer;
    private final boolean isRemoteAccess;
    private Inventory inventory;
    private GUIMode mode;
    private PendingPermissionAdd pendingPlayerInput;

    /**
     * GUI模式
     */
    public enum GUIMode {
        NORMAL,      // 正常模式（存取物品）
        UPGRADE,     // 升级模式
        PERMISSIONS, // 权限管理模式
        LOGS         // 日志查看模式
    }

    /**
     * 构造函数
     */
    public WarehouseGUI(StorageService storageService, Warehouse warehouse, Player viewer, boolean isRemoteAccess) {
        this.storageService = storageService;
        this.warehouse = warehouse;
        this.viewer = viewer;
        this.isRemoteAccess = isRemoteAccess;
        this.mode = GUIMode.NORMAL;
    }

    /**
     * 打开GUI
     */
    public void open() {
        switch (mode) {
            case NORMAL -> openNormalMode();
            case UPGRADE -> openUpgradeMode();
            case PERMISSIONS -> openPermissionsMode();
            case LOGS -> openLogsMode();
        }
    }

    /**
     * 打开正常模式（存取物品）
     */
    private void openNormalMode() {
        int rows = warehouse.getCapacity() / 9;
        // 添加底部功能栏（1行用于按钮）
        int totalSlots = Math.max((rows + 1) * 9, 27);
        String title = warehouse.getName() + (isRemoteAccess ? " §7[远程]" : "");

        inventory = Bukkit.createInventory(null, totalSlots, title);

        // 加载仓库物品
        Map<Integer, StorageItem> items = warehouse.getItems();
        for (Map.Entry<Integer, StorageItem> entry : items.entrySet()) {
            int slot = entry.getKey();
            StorageItem item = entry.getValue();
            if (slot < inventory.getSize() - 9) { // 保留底部按钮区域
                if (item != null) {
                    ItemStack itemStack = item.getItemStack();
                    if (itemStack != null) {
                        inventory.setItem(slot, itemStack);
                    }
                }
            }
        }

        // 添加底部功能按钮
        addModeButtons();

        viewer.openInventory(inventory);

        // 记录日志
        if (storageService.getConfig().isLogsEnabled()) {
            StorageLog log = StorageLog.createOpenLog(
                    warehouse.getWarehouseId(),
                    viewer.getUniqueId(),
                    viewer.getName(),
                    isRemoteAccess
            );
            storageService.getLogService().addLog(log);
        }

        warehouse.updateAccessTime();
    }

    /**
     * 添加模式切换按钮
     */
    private void addModeButtons() {
        int startSlot = inventory.getSize() - 9;
        RemoteAccessPermission viewerPerm = storageService.getPermission(
                warehouse.getWarehouseId(), viewer.getUniqueId());

        // 升级按钮 (槽位0)
        ItemStack upgradeBtn = createItem(Material.EMERALD,
                "§a升级仓库",
                "§7点击查看升级选项",
                "§7当前等级: §f" + warehouse.getLevel()
        );
        inventory.setItem(startSlot, upgradeBtn);

        // 权限管理按钮 (槽位1) - 仅管理员可见
        if (viewerPerm.isAtLeast(RemoteAccessPermission.ADMIN)) {
            ItemStack permBtn = createItem(Material.BOOK,
                    "§6权限管理",
                    "§7点击管理访问权限",
                    "§7权限数: §f" + storageService.getWarehousePermissions(warehouse.getWarehouseId()).size()
            );
            inventory.setItem(startSlot + 1, permBtn);
        }

        // 日志按钮 (槽位2)
        ItemStack logBtn = createItem(Material.PAPER,
                "§e操作日志",
                "§7点击查看操作记录"
        );
        inventory.setItem(startSlot + 2, logBtn);

        // 锁定/解锁按钮 (槽位3) - 仅所有者可见
        if (viewerPerm.isOwner()) {
            if (warehouse.isLocked()) {
                ItemStack unlockBtn = createItem(Material.OAK_DOOR,
                        "§a解锁仓库",
                        "§7点击解锁仓库"
                );
                inventory.setItem(startSlot + 3, unlockBtn);
            } else {
                ItemStack lockBtn = createItem(Material.IRON_DOOR,
                        "§c锁定仓库",
                        "§7点击锁定仓库"
                );
                inventory.setItem(startSlot + 3, lockBtn);
            }
        }

        // 保存按钮 (槽位7) - 手动保存
        ItemStack saveBtn = createItem(Material.COMPASS,
                "§b保存数据",
                "§7手动保存仓库数据"
        );
        inventory.setItem(startSlot + 7, saveBtn);

        // 关闭按钮 (槽位8)
        ItemStack closeBtn = createItem(Material.BARRIER,
                "§c关闭",
                "§7点击关闭界面"
        );
        inventory.setItem(startSlot + 8, closeBtn);
    }

    /**
     * 获取当前打开的Inventory
     */
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 打开升级模式
     */
    private void openUpgradeMode() {
        inventory = Bukkit.createInventory(null, 27, "§6升级仓库 - " + warehouse.getName());

        // 当前等级信息
        ItemStack currentInfo = createItem(Material.CHEST,
                "§a当前等级: §e" + warehouse.getLevel(),
                "§7容量: §f" + warehouse.getCapacity() + " 格",
                "§7已使用: §f" + warehouse.getUsedCapacity() + " 格",
                "§7使用率: §f" + String.format("%.1f%%", warehouse.getUsagePercentage() * 100)
        );
        inventory.setItem(11, currentInfo);

        // 升级信息
        if (warehouse.canUpgrade()) {
            WarehouseLevel nextLevel = warehouse.getNextLevelConfig();
            UpgradeRecipe recipe = UpgradeRecipe.fromLevels(
                    warehouse.getCurrentLevelConfig(),
                    nextLevel
            );

            List<String> lore = new ArrayList<>();
            lore.add("§a下一等级: §e" + nextLevel.getLevel());
            lore.add("§7容量: §f" + nextLevel.getCapacity() + " 格");
            lore.add("");
            lore.add("§6升级需求:");
            lore.add("§7金币: §f" + recipe.getMoneyCost());

            if (recipe.hasMaterialRequirements()) {
                lore.add("§7材料:");
                for (Map.Entry<String, Integer> entry : recipe.getMaterialRequirements().entrySet()) {
                    lore.add("  §7- §f" + entry.getKey() + " x" + entry.getValue());
                }
            }

            if (recipe.hasUpgradeTime()) {
                lore.add("§7时间: §f" + recipe.getFormattedUpgradeTime());
            }

            lore.add("");
            lore.add("§e点击升级");

            ItemStack upgradeButton = createItem(Material.EMERALD,
                    "§a升级到等级 " + nextLevel.getLevel(),
                    lore.toArray(new String[0])
            );
            inventory.setItem(15, upgradeButton);
        } else {
            ItemStack maxLevel = createItem(Material.BARRIER,
                    "§c已达最大等级",
                    "§7当前等级: §f" + warehouse.getLevel(),
                    "§7最大等级: §f" + warehouse.getType().getMaxLevel()
            );
            inventory.setItem(15, maxLevel);
        }

        // 返回按钮
        ItemStack backButton = createItem(Material.ARROW, "§f返回");
        inventory.setItem(22, backButton);

        viewer.openInventory(inventory);
    }

    /**
     * 打开权限管理模式
     */
    private void openPermissionsMode() {
        inventory = Bukkit.createInventory(null, 54, "§6权限管理 - " + warehouse.getName());

        // 检查查看者是否有管理权限
        RemoteAccessPermission viewerPerm = storageService.getPermission(
                warehouse.getWarehouseId(), viewer.getUniqueId());
        boolean canManage = viewerPerm.isAtLeast(RemoteAccessPermission.ADMIN);

        // 获取所有权限列表
        Map<UUID, RemoteAccessPermission> allPermissions = storageService.getWarehousePermissions(
                warehouse.getWarehouseId());

        // 显示已授权的玩家列表（最多显示45个）
        int slot = 0;
        for (Map.Entry<UUID, RemoteAccessPermission> entry : allPermissions.entrySet()) {
            if (slot >= 45) break;

            UUID playerId = entry.getKey();
            RemoteAccessPermission perm = entry.getValue();

            // 跳过所有者（不显示在列表中）
            if (perm == RemoteAccessPermission.OWNER) continue;

            String playerName = Bukkit.getOfflinePlayer(playerId).getName();
            if (playerName == null) {
                playerName = playerId.toString().substring(0, 8);
            }

            Material icon = getPermissionIcon(perm);
            List<String> lore = new ArrayList<>();
            lore.add("§7权限等级: §f" + perm.getDisplayName());
            lore.add("§7权限值: §f" + perm.getLevel());
            lore.add("");
            if (canManage && !perm.isAtLeast(RemoteAccessPermission.OWNER)) {
                lore.add("§c右键移除权限");
            }

            ItemStack permItem = createItem(icon,
                    "§e" + playerName,
                    lore.toArray(new String[0])
            );
            inventory.setItem(slot++, permItem);
        }

        // 统计信息
        ItemStack statsButton = createItem(Material.BOOK,
                "§6权限统计",
                "§7总权限数: §f" + allPermissions.size()
        );
        inventory.setItem(49, statsButton);

        // 添加权限按钮（仅管理者可见）
        if (canManage) {
            ItemStack addButton = createItem(Material.LIME_DYE, "§a添加权限",
                    "§7点击添加新的访问权限",
                    "§7请在聊天框输入玩家名称"
            );
            inventory.setItem(50, addButton);
        }

        // 返回按钮
        ItemStack backButton = createItem(Material.ARROW, "§f返回");
        inventory.setItem(53, backButton);

        viewer.openInventory(inventory);
    }

    /**
     * 打开日志查看模式
     */
    private void openLogsMode() {
        inventory = Bukkit.createInventory(null, 54, "§6操作日志 - " + warehouse.getName());

        // 获取最近的日志
        List<StorageLog> logs = storageService.getLogService()
                .getRecentLogs(warehouse.getWarehouseId(), 45);

        int slot = 0;
        for (StorageLog log : logs) {
            if (slot >= 45) break;

            Material material = getLogIcon(log.getAction());
            List<String> lore = new ArrayList<>();
            lore.add("§7操作者: §f" + log.getPlayerName());
            lore.add("§7时间: §f" + log.getTimestamp());

            if (log.getItemInfo() != null) {
                lore.add("§7物品: §f" + log.getItemInfo());
                if (log.getAmount() > 0) {
                    lore.add("§7数量: §f" + log.getAmount());
                }
            }

            if (log.isRemoteAccess()) {
                lore.add("§e远程访问");
            }

            ItemStack logItem = createItem(material,
                    "§6" + log.getAction().getDisplayName(),
                    lore.toArray(new String[0])
            );
            inventory.setItem(slot++, logItem);
        }

        // 统计信息
        ItemStack statsButton = createItem(Material.BOOK,
                "§6统计信息",
                "§7点击查看详细统计"
        );
        inventory.setItem(49, statsButton);

        // 返回按钮
        ItemStack backButton = createItem(Material.ARROW, "§f返回");
        inventory.setItem(53, backButton);

        viewer.openInventory(inventory);
    }

    /**
     * 处理点击事件
     */
    public void handleClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        switch (mode) {
            case NORMAL -> handleNormalModeClick(event, slot, clickedItem);
            case UPGRADE -> handleUpgradeModeClick(event, slot, clickedItem);
            case PERMISSIONS -> handlePermissionsModeClick(event, slot, clickedItem);
            case LOGS -> handleLogsModeClick(event, slot, clickedItem);
        }
    }

    /**
     * 处理正常模式的点击
     */
    private void handleNormalModeClick(InventoryClickEvent event, int slot, ItemStack clickedItem) {
        RemoteAccessPermission permission = storageService.getPermission(
                warehouse.getWarehouseId(),
                viewer.getUniqueId()
        );

        // 检查是否点击底部功能栏
        int topSize = inventory.getSize() - 9;
        if (slot >= topSize) {
            handleNormalModeButtonClick(slot, clickedItem);
            return;
        }

        // 检查权限
        if (event.isShiftClick()) {
            // 快速存取需要完全访问权限
            if (!permission.hasFullAccess()) {
                viewer.sendMessage("§c您没有权限进行此操作");
                return;
            }
        }

        // 顶部仓库区域（0-当前容量-1）
        if (slot >= 0 && slot < topSize - 9) {
            // 点击的是仓库区域
            if (slot < warehouse.getCapacity()) {
                // 从仓库取出物品到玩家背包
                handleWithdraw(slot, clickedItem, permission);
            }
        } else if (slot >= topSize - 9 && slot < topSize) {
            // 点击的是玩家背包区域
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                // 存入物品到仓库
                handleDeposit(slot, clickedItem, permission);
            }
        }
    }

    /**
     * 处理正常模式下的按钮点击
     */
    private void handleNormalModeButtonClick(int slot, ItemStack clickedItem) {
        int topSize = inventory.getSize() - 9;
        int buttonSlot = slot - topSize; // 按钮在功能栏中的位置 0-8

        switch (buttonSlot) {
            case 0 -> {
                // 升级按钮
                mode = GUIMode.UPGRADE;
                open();
            }
            case 1 -> {
                // 权限管理按钮
                mode = GUIMode.PERMISSIONS;
                open();
            }
            case 2 -> {
                // 日志按钮
                mode = GUIMode.LOGS;
                open();
            }
            case 3 -> {
                // 锁定/解锁按钮
                warehouse.setLocked(!warehouse.isLocked());
                viewer.sendMessage(warehouse.isLocked() ?
                        "§a仓库已锁定" : "§a仓库已解锁");
                open();
            }
            case 7 -> {
                // 保存按钮
                storageService.saveData();
                viewer.sendMessage("§a数据已保存");
            }
            case 8 -> {
                // 关闭按钮
                viewer.closeInventory();
            }
        }
    }

    /**
     * 处理从仓库取出物品
     */
    private void handleWithdraw(int slot, ItemStack warehouseItem, RemoteAccessPermission permission) {
        // 检查取出权限
        if (!permission.canWithdraw() && !permission.hasFullAccess()) {
            viewer.sendMessage("§c您没有取出权限");
            return;
        }

        // 获取仓库物品
        StorageItem storageItem = warehouse.getItem(slot);
        if (storageItem == null) {
            return;
        }

        ItemStack itemStack = storageItem.getItemStack();
        if (itemStack == null) {
            return;
        }
        itemStack = itemStack.clone();

        // 检查玩家背包是否有空间
        HashMap<Integer, ItemStack> overflow = viewer.getInventory().addItem(itemStack);
        if (!overflow.isEmpty()) {
            viewer.sendMessage("§c背包空间不足，无法取出全部物品");
            // 只取出能放入的部分
            return;
        }

        // 记录日志
        if (storageService.getConfig().isLogsEnabled()) {
            StorageLog log = StorageLog.createWithdrawLog(
                    warehouse.getWarehouseId(),
                    viewer.getUniqueId(),
                    viewer.getName(),
                    itemStack.getType().name(),
                    itemStack.getAmount(),
                    isRemoteAccess
            );
            storageService.getLogService().addLog(log);
        }

        // 从仓库移除物品
        warehouse.removeItem(slot);
        viewer.sendMessage("§a已取出 " + itemStack.getAmount() + "x " + formatItemName(itemStack));
    }

    /**
     * 处理存入物品到仓库
     */
    private void handleDeposit(int playerSlot, ItemStack playerItem, RemoteAccessPermission permission) {
        // 检查存入权限
        if (!permission.canDeposit() && !permission.hasFullAccess()) {
            viewer.sendMessage("§c您没有存入权限");
            return;
        }

        // 检查仓库是否已满
        if (warehouse.isFull()) {
            viewer.sendMessage("§c仓库已满，无法存入更多物品");
            return;
        }

        // 查找空槽位或叠加
        int targetSlot = warehouse.findEmptySlot();
        if (targetSlot == -1) {
            viewer.sendMessage("§c仓库已满");
            return;
        }

        // 存入物品
        ItemStack depositItem = playerItem.clone();
        StorageItem storageItem = new StorageItem(depositItem);

        if (warehouse.setItem(targetSlot, storageItem)) {
            // 从玩家背包移除
            playerItem.setAmount(0);

            // 记录日志
            if (storageService.getConfig().isLogsEnabled()) {
                StorageLog log = StorageLog.createDepositLog(
                        warehouse.getWarehouseId(),
                        viewer.getUniqueId(),
                        viewer.getName(),
                        depositItem.getType().name(),
                        depositItem.getAmount(),
                        isRemoteAccess
                );
                storageService.getLogService().addLog(log);
            }

            viewer.sendMessage("§a已存入 " + depositItem.getAmount() + "x " + formatItemName(depositItem));
        }
    }

    /**
     * 格式化物品名称
     */
    private String formatItemName(ItemStack item) {
        String name = item.getType().name().toLowerCase().replace("_", " ");
        // 首字母大写
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * 处理升级模式的点击
     */
    private void handleUpgradeModeClick(InventoryClickEvent event, int slot, ItemStack clickedItem) {
        if (slot == 15 && clickedItem.getType() == Material.EMERALD) {
            // 点击升级按钮
            attemptUpgrade();
        } else if (slot == 22 && clickedItem.getType() == Material.ARROW) {
            // 返回按钮
            mode = GUIMode.NORMAL;
            open();
        }
    }

    /**
     * 处理权限管理模式的点击
     */
    private void handlePermissionsModeClick(InventoryClickEvent event, int slot, ItemStack clickedItem) {
        // 返回按钮
        if (slot == 53 && clickedItem.getType() == Material.ARROW) {
            mode = GUIMode.NORMAL;
            open();
            return;
        }

        // 检查查看者是否有管理权限
        RemoteAccessPermission viewerPerm = storageService.getPermission(
                warehouse.getWarehouseId(), viewer.getUniqueId());
        if (!viewerPerm.isAtLeast(RemoteAccessPermission.ADMIN)) {
            viewer.sendMessage("§c您没有管理权限");
            return;
        }

        // 点击统计信息按钮
        if (slot == 49 && clickedItem.getType() == Material.BOOK) {
            Map<UUID, RemoteAccessPermission> allPerms = storageService.getWarehousePermissions(
                    warehouse.getWarehouseId());
            viewer.sendMessage("§6=== 权限统计 ===");
            viewer.sendMessage("§7总权限数: §f" + allPerms.size());
            viewer.sendMessage("§7所有者: §f" + Bukkit.getOfflinePlayer(warehouse.getOwnerId()).getName());
            return;
        }

        // 点击添加权限按钮
        if (slot == 50 && clickedItem.getType() == Material.LIME_DYE) {
            viewer.sendMessage("§a请在聊天框输入要添加权限的玩家名称");
            viewer.sendMessage("§7输入 §fcancel §7取消");
            // 标记等待玩家输入
            pendingPlayerInput = new PendingPermissionAdd(warehouse.getWarehouseId(), viewer.getUniqueId());
            viewer.closeInventory();
            return;
        }

        // 点击权限项目（槽位0-44）尝试移除权限
        if (slot >= 0 && slot <= 44 && clickedItem != null && clickedItem.getType() != Material.AIR) {
            // 获取槽位对应的玩家
            Map<UUID, RemoteAccessPermission> allPerms = storageService.getWarehousePermissions(
                    warehouse.getWarehouseId());
            int index = 0;
            for (Map.Entry<UUID, RemoteAccessPermission> entry : allPerms.entrySet()) {
                if (entry.getValue() == RemoteAccessPermission.OWNER) continue;
                if (index == slot) {
                    UUID targetId = entry.getKey();
                    RemoteAccessPermission targetPerm = entry.getValue();

                    // 右键点击移除权限
                    if (event.isRightClick()) {
                        removePermission(targetId, targetPerm);
                        return;
                    }

                    // 左键点击显示权限详情并提供选择
                    viewer.sendMessage("§6=== 权限详情 ===");
                    viewer.sendMessage("§7玩家: §f" + getPlayerName(targetId));
                    viewer.sendMessage("§7当前权限: §f" + targetPerm.getDisplayName());
                    viewer.sendMessage("§7权限等级: §f" + targetPerm.getLevel());
                    // 分隔
                    viewer.sendMessage("§a左键: 查看详情");
                    viewer.sendMessage("§c右键: 移除权限");
                    return;
                }
                index++;
            }
        }
    }

    /**
     * 获取玩家名称
     */
    private String getPlayerName(UUID playerId) {
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        return name != null ? name : playerId.toString().substring(0, 8);
    }

    /**
     * 移除权限
     */
    private void removePermission(UUID targetId, RemoteAccessPermission currentPerm) {
        // 移除权限
        storageService.removePermission(warehouse.getWarehouseId(), targetId);

        // 记录日志
        if (storageService.getConfig().isLogsEnabled()) {
            StorageLog log = StorageLog.createPermissionLog(
                    warehouse.getWarehouseId(),
                    viewer.getUniqueId(),
                    viewer.getName(),
                    false, // 移除权限
                    getPlayerName(targetId),
                    currentPerm.getDisplayName()
            );
            storageService.getLogService().addLog(log);
        }

        viewer.sendMessage("§a已移除 " + getPlayerName(targetId) + " 的 " +
                currentPerm.getDisplayName() + " 权限");

        // 刷新界面
        openPermissionsMode();
    }

    /**
     * 待处理的权限添加请求
     */
    private static class PendingPermissionAdd {
        final UUID warehouseId;
        final UUID requesterId;

        PendingPermissionAdd(UUID warehouseId, UUID requesterId) {
            this.warehouseId = warehouseId;
            this.requesterId = requesterId;
        }
    }

    /**
     * 处理聊天输入的玩家名称（用于权限添加）
     */
    public void handlePlayerNameInput(String playerName) {
        if (pendingPlayerInput == null) {
            return;
        }

        if (playerName.equalsIgnoreCase("cancel")) {
            viewer.sendMessage("§7已取消");
            pendingPlayerInput = null;
            return;
        }

        // 查找玩家
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            viewer.sendMessage("§c找不到玩家: " + playerName);
            pendingPlayerInput = null;
            return;
        }

        // 检查是否是仓库所有者
        if (target.getUniqueId().equals(warehouse.getOwnerId())) {
            viewer.sendMessage("§c无法修改所有者的权限");
            pendingPlayerInput = null;
            return;
        }

        // 设置默认权限（VIEW查看权限）
        storageService.setPermission(pendingPlayerInput.warehouseId, target.getUniqueId(),
                RemoteAccessPermission.VIEW);

        // 记录日志
        if (storageService.getConfig().isLogsEnabled()) {
            StorageLog log = StorageLog.createPermissionLog(
                    pendingPlayerInput.warehouseId,
                    viewer.getUniqueId(),
                    viewer.getName(),
                    true, // 授权
                    target.getName(),
                    RemoteAccessPermission.VIEW.getDisplayName()
            );
            storageService.getLogService().addLog(log);
        }

        viewer.sendMessage("§a已为 " + target.getName() + " 添加 §f" +
                RemoteAccessPermission.VIEW.getDisplayName() + " §a权限");
        pendingPlayerInput = null;

        // 刷新界面
        openPermissionsMode();
    }

    /**
     * 清除待处理的权限添加请求
     */
    public void clearPendingInput() {
        this.pendingPlayerInput = null;
    }

    /**
     * 检查是否有待处理的输入
     */
    public boolean hasPendingInput() {
        return pendingPlayerInput != null;
    }

    /**
     * 处理日志模式的点击
     */
    private void handleLogsModeClick(InventoryClickEvent event, int slot, ItemStack clickedItem) {
        if (slot == 53 && clickedItem.getType() == Material.ARROW) {
            // 返回按钮
            mode = GUIMode.NORMAL;
            open();
        } else if (slot == 49 && clickedItem.getType() == Material.BOOK) {
            // 显示统计信息
            String summary = storageService.getLogService()
                    .getLogSummary(warehouse.getWarehouseId(), 7);
            viewer.sendMessage(summary);
        }
    }

    /**
     * 尝试升级仓库
     */
    private void attemptUpgrade() {
        storageService.getUpgradeService()
                .startUpgrade(viewer, warehouse.getWarehouseId())
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        if (result.isAsync()) {
                            viewer.sendMessage("§a开始升级仓库，升级到等级 " + result.getToLevel());
                        } else {
                            viewer.sendMessage("§a仓库已升级到等级 " + result.getToLevel());
                        }
                        // 刷新界面
                        Bukkit.getScheduler().runTask(
                                Bukkit.getPluginManager().getPlugin("StarCore"),
                                this::openUpgradeMode
                        );
                    } else {
                        viewer.sendMessage("§c升级失败: " + result.getMessage());
                    }
                });
    }

    /**
     * 切换模式
     */
    public void switchMode(GUIMode newMode) {
        this.mode = newMode;
        open();
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
     * 获取日志操作对应的图标
     */
    private Material getLogIcon(StorageLog.LogAction action) {
        return switch (action) {
            case OPEN -> Material.ENDER_CHEST;
            case DEPOSIT -> Material.CHEST;
            case WITHDRAW -> Material.HOPPER;
            case REMOTE_ACCESS -> Material.ENDER_PEARL;
            case UPGRADE -> Material.ANVIL;
            case GRANT_PERMISSION -> Material.LIME_DYE;
            case REVOKE_PERMISSION -> Material.RED_DYE;
            case RENAME -> Material.NAME_TAG;
            case LOCK -> Material.IRON_DOOR;
            case UNLOCK -> Material.OAK_DOOR;
            case DELETE -> Material.BARRIER;
        };
    }

    /**
     * 获取权限等级对应的图标
     */
    private Material getPermissionIcon(RemoteAccessPermission permission) {
        return switch (permission) {
            case NONE -> Material.BARRIER;
            case VIEW -> Material.ENDER_EYE;
            case WITHDRAW -> Material.HOPPER;
            case DEPOSIT -> Material.CHEST;
            case FULL -> Material.CHEST_MINECART;
            case ADMIN -> Material.COMMAND_BLOCK;
            case OWNER -> Material.PLAYER_HEAD;
        };
    }

    /**
     * 关闭GUI
     */
    public void close() {
        if (inventory != null && viewer.getOpenInventory().getTopInventory() == inventory) {
            viewer.closeInventory();
        }
    }

    // ==================== Getters ====================

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Player getViewer() {
        return viewer;
    }

    public GUIMode getMode() {
        return mode;
    }

    public boolean isRemoteAccess() {
        return isRemoteAccess;
    }
}
