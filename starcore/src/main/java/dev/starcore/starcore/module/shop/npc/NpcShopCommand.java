package dev.starcore.starcore.module.shop.npc;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.starcore.starcore.module.shop.npc.NpcShopStorage.NpcTradeRecord;
import net.citizensnpcs.api.CitizensAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * NPC商店命令处理器
 * /npcshop <子命令>
 *
 * 子命令:
 * - create <名称> - 创建商店
 * - delete <商店ID> - 删除商店
 * - add <商店ID> <物品> <买价> [卖价] [库存] - 添加物品
 * - remove <商店ID> <物品ID> - 移除物品
 * - list [玩家] - 列出商店
 * - info <商店ID> - 查看商店信息
 * - open <商店ID|NPCID> - 打开商店GUI
 * - bind <商店ID> <NPCID> - 绑定到NPC
 * - unbind <商店ID> - 解除绑定
 * - infinite <商店ID> <true|false> - 设置无限库存
 * - edit <商店ID> <属性> <值> - 编辑商店属性
 * - stats <商店ID> - 查看商店统计
 * - history <商店ID|玩家> [页码] - 查看交易记录
 * - reload - 重载商店数据
 */
public class NpcShopCommand implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = Logger.getLogger(NpcShopCommand.class.getName());

    private final Plugin plugin;
    private final NpcShopService npcShopService;

    public NpcShopCommand(Plugin plugin, NpcShopService npcShopService) {
        this.plugin = plugin;
        this.npcShopService = npcShopService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家使用", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "create", "c" -> handleCreate(player, args);
                case "delete", "del", "d" -> handleDelete(player, args);
                case "add", "a" -> handleAdd(player, args);
                case "remove", "rm", "r" -> handleRemove(player, args);
                case "list", "ls", "l" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "open", "o" -> handleOpen(player, args);
                case "bind", "b" -> handleBind(player, args);
                case "unbind", "ub" -> handleUnbind(player, args);
                case "infinite", "inf" -> handleInfinite(player, args);
                case "edit", "e" -> handleEdit(player, args);
                case "stats", "s" -> handleStats(player, args);
                case "history", "hist", "h" -> handleHistory(player, args);
                case "reload" -> handleReload(player);
                case "help", "?" -> showHelp(player);
                default -> {
                    player.sendMessage(Component.text("未知命令: " + subCommand, NamedTextColor.RED));
                    showHelp(player);
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("执行命令时出错: " + e.getMessage(), NamedTextColor.RED));
            LOGGER.log(Level.SEVERE, "Error in npcshop command", e);
        }

        return true;
    }

    // ==================== 命令处理 ====================

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /npcshop create <名称> [描述]", NamedTextColor.YELLOW));
            return;
        }

        String name = args[1];
        String description = args.length > 2 ? args[2] : null;

        NpcShopData shop = npcShopService.createNpcShop(name, player.getLocation(), player, description);

        player.sendMessage(Component.text()
            .append(Component.text("商店已创建: ", NamedTextColor.GREEN))
            .append(Component.text(name, NamedTextColor.GOLD))
            .append(Component.text(" (ID: " + shortId(shop.id()) + ")", NamedTextColor.GRAY)));
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /npcshop delete <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId()) && !player.hasPermission("npcshop.admin")) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        if (npcShopService.deleteNpcShop(shopId)) {
            player.sendMessage(Component.text("商店已删除: " + shop.name(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("删除失败", NamedTextColor.RED));
        }
    }

    private void handleAdd(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("用法: /npcshop add <商店ID> <物品> <买价> [卖价] [库存]", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId()) && !player.hasPermission("npcshop.admin")) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        // 解析物品
        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的物品: " + args[2], NamedTextColor.RED));
            return;
        }

        // 解析价格
        BigDecimal buyPrice;
        BigDecimal sellPrice = BigDecimal.ZERO;
        int stock = 64;

        try {
            buyPrice = new BigDecimal(args[3]);
            if (args.length > 4) {
                sellPrice = new BigDecimal(args[4]);
            }
            if (args.length > 5) {
                stock = Integer.parseInt(args[5]);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的数字", NamedTextColor.RED));
            return;
        }

        NpcShopItemData item = npcShopService.addItem(shopId, material, buyPrice, sellPrice, stock);

        player.sendMessage(Component.text()
            .append(Component.text("物品已添加: ", NamedTextColor.GREEN))
            .append(Component.text(material.name(), NamedTextColor.GOLD))
            .append(Component.text(" - 买价: " + buyPrice + " 卖价: " + sellPrice, NamedTextColor.GRAY)));
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /npcshop remove <商店ID> <物品ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        Optional<UUID> itemIdOpt = parseItemId(args[2]);

        if (shopId == null || itemIdOpt.isEmpty()) {
            player.sendMessage(Component.text("无效的ID", NamedTextColor.RED));
            return;
        }
        UUID itemId = itemIdOpt.get();

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId()) && !player.hasPermission("npcshop.admin")) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        if (npcShopService.removeItem(shopId, itemId)) {
            player.sendMessage(Component.text("物品已移除", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("物品不存在或移除失败", NamedTextColor.RED));
        }
    }

    private void handleList(Player player, String[] args) {
        List<NpcShopData> shops;

        if (args.length > 1) {
            // 列出其他玩家的商店
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                shops = npcShopService.getPlayerShops(target.getUniqueId());
            } else {
                shops = npcShopService.getPlayerShops(player.getUniqueId());
            }
        } else {
            shops = npcShopService.getPlayerShops(player.getUniqueId());
        }

        if (shops.isEmpty()) {
            player.sendMessage(Component.text("你没有任何商店", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 我的NPC商店 ===", NamedTextColor.GOLD));
        for (NpcShopData shop : shops) {
            int itemCount = npcShopService.getShopItems(shop.id()).size();
            String npcStatus = shop.hasNpc() ? " [NPC#" + shop.npcId() + "]" : "";
            player.sendMessage(Component.text()
                .append(Component.text("[" + shortId(shop.id()) + "] ", NamedTextColor.GRAY))
                .append(Component.text(shop.name(), NamedTextColor.AQUA))
                .append(Component.text(" - " + itemCount + " 件物品" + npcStatus, NamedTextColor.GRAY)));
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /npcshop info <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        List<NpcShopItemData> items = npcShopService.getShopItems(shopId);

        player.sendMessage(Component.text("=== " + shop.name() + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("ID: " + shop.id(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("拥有者: " + shop.ownerName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("物品数: " + items.size(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("无限库存: " + (shop.infiniteStock() ? "是" : "否"), NamedTextColor.GRAY));
        player.sendMessage(Component.text("允许购买: " + (shop.buyEnabled() ? "是" : "否"), NamedTextColor.GRAY));
        player.sendMessage(Component.text("允许出售: " + (shop.sellEnabled() ? "是" : "否"), NamedTextColor.GRAY));

        if (shop.hasNpc()) {
            player.sendMessage(Component.text("绑定NPC: #" + shop.npcId(), NamedTextColor.GREEN));
        }

        if (shop.description() != null) {
            player.sendMessage(Component.text("描述: " + shop.description(), NamedTextColor.GRAY));
        }

        // 显示物品列表
        if (!items.isEmpty()) {
            player.sendMessage(Component.text("--- 物品列表 ---", NamedTextColor.YELLOW));
            for (NpcShopItemData item : items.stream().limit(5).toList()) {
                player.sendMessage(Component.text()
                    .append(Component.text("[" + shortId(item.id()) + "] ", NamedTextColor.GRAY))
                    .append(Component.text(item.material(), NamedTextColor.WHITE))
                    .append(Component.text(" 买:" + item.buyPrice() + " 卖:" + item.sellPrice(), NamedTextColor.GRAY)));
            }
            if (items.size() > 5) {
                player.sendMessage(Component.text("... 还有 " + (items.size() - 5) + " 件物品", NamedTextColor.GRAY));
            }
        }
    }

    private void handleOpen(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /npcshop open <商店ID|NPCID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            // 尝试作为NPC ID处理
            try {
                int npcId = Integer.parseInt(args[1]);
                if (!isCitizensAvailable()) {
                    player.sendMessage(Component.text("Citizens插件未安装", NamedTextColor.RED));
                    return;
                }
                if (!npcShopService.hasShop(npcId)) {
                    player.sendMessage(Component.text("NPC没有绑定商店", NamedTextColor.RED));
                    return;
                }
                npcShopService.openNpcShopGui(player, npcId);
                return;
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("无效的ID", NamedTextColor.RED));
                return;
            }
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        npcShopService.openShopGui(player, shopId);
    }

    private void handleBind(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /npcshop bind <商店ID> <NPCID>", NamedTextColor.YELLOW));
            return;
        }

        if (!isCitizensAvailable()) {
            player.sendMessage(Component.text("Citizens插件未安装", NamedTextColor.RED));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        int npcId;
        try {
            npcId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的NPC ID", NamedTextColor.RED));
            return;
        }

        // 检查NPC是否存在
        if (CitizensAPI.getNPCRegistry().getById(npcId) == null) {
            player.sendMessage(Component.text("NPC不存在: #" + npcId, NamedTextColor.RED));
            return;
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId()) && !player.hasPermission("npcshop.admin")) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        if (npcShopService.bindToNpc(shopId, npcId)) {
            player.sendMessage(Component.text("商店已绑定到NPC #" + npcId, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("绑定失败，NPC可能已被占用", NamedTextColor.RED));
        }
    }

    private void handleUnbind(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /npcshop unbind <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId()) && !player.hasPermission("npcshop.admin")) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        if (!shop.hasNpc()) {
            player.sendMessage(Component.text("此商店没有绑定NPC", NamedTextColor.YELLOW));
            return;
        }

        if (npcShopService.unbindFromNpc(shopId)) {
            player.sendMessage(Component.text("已解除NPC绑定", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("解除绑定失败", NamedTextColor.RED));
        }
    }

    private void handleInfinite(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /npcshop infinite <商店ID> <true|false>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        boolean infinite = Boolean.parseBoolean(args[2]);

        Optional<NpcShopData> result = npcShopService.setInfiniteStock(shopId, infinite);
        if (result.isPresent()) {
            player.sendMessage(Component.text("无限库存已" + (infinite ? "启用" : "禁用"), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("设置失败", NamedTextColor.RED));
        }
    }

    private void handleEdit(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("用法: /npcshop edit <商店ID> <属性> <值>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("可编辑属性: name(名称), description(描述), infinite(无限库存), buy(允许购买), sell(允许出售)", NamedTextColor.GRAY));
            player.sendMessage(Component.text("示例: /npcshop edit abc123 name 我的商店", NamedTextColor.GRAY));
            player.sendMessage(Component.text("示例: /npcshop edit abc123 infinite true", NamedTextColor.GRAY));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        NpcShopData shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId()) && !player.hasPermission("npcshop.admin")) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        String property = args[2].toLowerCase();
        String value = args[3];

        // 验证布尔值属性
        if (property.equals("infinite") || property.equals("buy") || property.equals("sellenabled") || property.equals("buyenabled")) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                player.sendMessage(Component.text("布尔值必须是 true 或 false", NamedTextColor.RED));
                return;
            }
        }

        Optional<NpcShopData> result = npcShopService.updateShop(shopId, property, value);
        if (result.isPresent()) {
            NpcShopData updated = result.get();
            String propertyName = switch (property) {
                case "name" -> "名称";
                case "description", "desc" -> "描述";
                case "infinite" -> "无限库存";
                case "buy", "buyenabled" -> "允许购买";
                case "sell", "sellenabled" -> "允许出售";
                default -> property;
            };
            player.sendMessage(Component.text()
                .append(Component.text("商店属性已更新: ", NamedTextColor.GREEN))
                .append(Component.text(propertyName, NamedTextColor.GOLD))
                .append(Component.text(" -> " + value, NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("无效的属性: " + property, NamedTextColor.RED));
            player.sendMessage(Component.text("可编辑属性: name, description, infinite, buy, sell", NamedTextColor.GRAY));
        }
    }

    private void handleStats(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /npcshop stats <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        Optional<NpcShopData> shopOpt = npcShopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        BigDecimal revenue = npcShopService.getShopRevenue(shopId);
        int transactionCount = npcShopService.getShopTransactionCount(shopId);

        player.sendMessage(Component.text("=== 商店统计 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("总收入: " + revenue + " 金币", NamedTextColor.GREEN));
        player.sendMessage(Component.text("交易次数: " + transactionCount, NamedTextColor.AQUA));
    }

    private void handleHistory(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /npcshop history <商店ID|player> [页码]", NamedTextColor.YELLOW));
            return;
        }

        int page = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        int pageSize = 10;
        int offset = (page - 1) * pageSize;

        List<NpcTradeRecord> records;
        UUID shopId = parseShopId(args[1]);

        if (shopId != null) {
            records = npcShopService.getTradeHistory(shopId);
        } else {
            records = npcShopService.getPlayerTradeHistory(player.getUniqueId(), 100);
        }

        if (records.isEmpty()) {
            player.sendMessage(Component.text("没有交易记录", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 交易记录 (第" + page + "页) ===", NamedTextColor.GOLD));

        int start = Math.min(offset, records.size());
        int end = Math.min(offset + pageSize, records.size());

        for (int i = start; i < end; i++) {
            NpcTradeRecord record = records.get(i);
            String action = record.type() == NpcTradeRecord.TradeType.BUY ? "购买" : "出售";
            NamedTextColor color = record.type() == NpcTradeRecord.TradeType.BUY ? NamedTextColor.RED : NamedTextColor.GREEN;

            player.sendMessage(Component.text()
                .append(Component.text(action + " ", color))
                .append(Component.text("物品 x" + record.quantity(), NamedTextColor.WHITE))
                .append(Component.text(" - " + record.totalPrice() + " 金币", NamedTextColor.GOLD)));
        }

        int totalPages = (records.size() + pageSize - 1) / pageSize;
        if (totalPages > 1) {
            player.sendMessage(Component.text("第 " + page + "/" + totalPages + " 页", NamedTextColor.GRAY));
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("npcshop.admin")) {
            player.sendMessage(Component.text("你没有权限使用此命令", NamedTextColor.RED));
            return;
        }

        // 重载NPC商店配置
        if (npcShopService != null) {
            npcShopService.reload();
            player.sendMessage(Component.text("NPC商店配置已重载", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("NPC商店服务不可用", NamedTextColor.RED));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== NPC商店帮助 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/npcshop create <名称> [描述] - 创建商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop delete <ID> - 删除商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop add <ID> <物品> <买价> [卖价] [库存] - 添加物品", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop remove <ID> <物品ID> - 移除物品", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop list - 列出我的商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop info <ID> - 查看商店信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop open <ID|NPC> - 打开商店GUI", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop bind <ID> <NPC> - 绑定到NPC", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop unbind <ID> - 解除绑定", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop infinite <ID> <true|false> - 设置无限库存", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop edit <ID> <属性> <值> - 编辑商店属性", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop stats <ID> - 商店统计", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/npcshop history <ID|player> [页码] - 交易记录", NamedTextColor.GRAY));
    }

    // ==================== 工具方法 ====================

    private UUID parseShopId(String id) {
        try {
            if (id.length() == 8) {
                // 查找匹配的商店 - 先检查玩家是否在线
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    // 玩家不在线，尝试作为UUID前缀匹配所有商店
                    return npcShopService.getAllShops().stream()
                        .filter(s -> s.id().toString().startsWith(id))
                        .map(NpcShopData::id)
                        .findFirst()
                        .orElse(null);
                }
                return npcShopService.getPlayerShops(player.getUniqueId()).stream()
                    .filter(s -> s.id().toString().startsWith(id))
                    .map(NpcShopData::id)
                    .findFirst()
                    .orElse(null);
            }
            return UUID.fromString(id);
        } catch (Exception e) {
            return null;
        }
    }

    private Optional<UUID> parseItemId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private boolean isCitizensAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Citizens") != null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "delete", "add", "remove", "list", "info", "open", "bind", "unbind", "infinite", "edit", "stats", "history", "help");
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("info") ||
                args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("unbind") ||
                args[0].equalsIgnoreCase("infinite") || args[0].equalsIgnoreCase("stats") ||
                args[0].equalsIgnoreCase("edit")) {
                return npcShopService.getPlayerShops(((Player) sender).getUniqueId()).stream()
                    .map(s -> s.id().toString().substring(0, 8))
                    .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("bind")) {
                return npcShopService.getPlayerShops(((Player) sender).getUniqueId()).stream()
                    .filter(s -> !s.hasNpc())
                    .map(s -> s.id().toString().substring(0, 8))
                    .collect(Collectors.toList());
            }
        }

        // edit 命令的属性补全
        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            return List.of("name", "description", "infinite", "buy", "sell");
        }

        // edit 命令的布尔值补全
        if (args.length == 4 && args[0].equalsIgnoreCase("edit")) {
            String property = args[2].toLowerCase();
            if (property.equals("infinite") || property.equals("buy") || property.equals("sell")) {
                return List.of("true", "false");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            // 物品名自动补全 - 返回所有可用的 Material
            String input = args[2].toUpperCase();
            if (input.isEmpty()) {
                // 无输入时返回常用物品列表
                return List.of("DIAMOND", "GOLD_INGOT", "IRON_INGOT", "COAL", "EMERALD", "APPLE", "BREAD", "BOOK", "ENCHANTED_GOLDEN_APPLE", "NETHERITE_INGOT", "ANCIENT_DEBRIS");
            }
            // 有输入时过滤匹配的 Material
            return Arrays.stream(Material.values())
                .filter(m -> m.name().startsWith(input))
                .map(Material::name)
                .collect(Collectors.toList());
        }

        return List.of();
    }
}
