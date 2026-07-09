package dev.starcore.starcore.module.shop.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.shop.model.*;
import dev.starcore.starcore.module.shop.service.NpcShopService;
import dev.starcore.starcore.module.shop.service.ShopService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商店命令处理器
 * /shop <子命令>
 * /npcshop <子命令>
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final ShopService shopService;
    private final NpcShopService npcShopService;
    private final NationService nationService;
    private final MessageService messages;

    public ShopCommand(
        ShopService shopService,
        NpcShopService npcShopService,
        NationService nationService,
        MessageService messages
    ) {
        this.shopService = shopService;
        this.npcShopService = npcShopService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
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
                case "list", "ls", "l" -> handleList(player, args);
                case "info", "i" -> handleInfo(player, args);
                case "open", "o" -> handleOpen(player, args);
                case "close" -> handleClose(player, args);
                case "add", "a" -> handleAddItem(player, args);
                case "remove", "rm", "r" -> handleRemoveItem(player, args);
                case "edit", "e" -> handleEditItem(player, args);
                case "buy", "b" -> handleBuy(player, args);
                case "sell", "s" -> handleSell(player, args);
                case "search", "find", "f" -> handleSearch(player, args);
                case "public", "pub" -> handlePublic(player, args);
                case "private", "priv" -> handlePrivate(player, args);
                case "admin" -> handleAdmin(player, args);
                case "stats" -> handleStats(player, args);
                case "history", "hist" -> handleHistory(player, args);
                case "bind" -> handleBind(player, args);
                case "unbind" -> handleUnbind(player, args);
                case "help", "h", "?" -> showHelp(player);
                default -> {
                    player.sendMessage(Component.text(messages.format("shop.unknown-command", subCommand), NamedTextColor.RED));
                    showHelp(player);
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop create <名称>", NamedTextColor.YELLOW));
            return;
        }

        String name = args[1];
        Shop shop = shopService.createPlayerShop(player, name);

        player.sendMessage(Component.text()
            .append(Component.text("商店已创建: ", NamedTextColor.GREEN))
            .append(Component.text(name, NamedTextColor.GOLD))
            .append(Component.text(" (ID: " + shop.shopId().toString().substring(0, 8) + ")", NamedTextColor.GRAY)));
    }

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop delete <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        if (shopId == null) {
            player.sendMessage(Component.text("无效的商店ID", NamedTextColor.RED));
            return;
        }

        if (shopService.deleteShop(shopId, player.getUniqueId())) {
            player.sendMessage(Component.text("商店已删除", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("删除失败：你不是此商店的拥有者", NamedTextColor.RED));
        }
    }

    private void handleList(Player player, String[] args) {
        List<Shop> shops;

        if (args.length > 1 && args[1].equalsIgnoreCase("public")) {
            shops = shopService.getPublicShops();
        } else if (args.length > 1 && args[1].equalsIgnoreCase("nation")) {
            var nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isPresent()) {
                shops = shopService.getNationShops(nationOpt.get().id().value());
            } else {
                player.sendMessage(Component.text("你没有加入任何国家", NamedTextColor.RED));
                shops = Collections.emptyList();
            }
        } else {
            shops = shopService.getPlayerShops(player.getUniqueId());
        }

        if (shops.isEmpty()) {
            player.sendMessage(Component.text("没有找到商店", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 商店列表 ===", NamedTextColor.GOLD));
        for (Shop shop : shops) {
            player.sendMessage(Component.text()
                .append(Component.text("[" + shop.shopId().toString().substring(0, 8) + "] ", NamedTextColor.GRAY))
                .append(Component.text(shop.name(), NamedTextColor.AQUA))
                .append(Component.text(" - " + shop.items().size() + " 件物品", NamedTextColor.GRAY)));
        }
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop info <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        Optional<Shop> shopOpt = shopService.getShop(shopId);

        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();
        player.sendMessage(Component.text("=== " + shop.name() + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("类型: " + shop.shopType().displayName(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("物品数: " + shop.items().size(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("状态: " + (shop.isOpen() ? "营业中" : "已关闭"), NamedTextColor.GRAY));
        if (shop.description() != null) {
            player.sendMessage(Component.text("描述: " + shop.description(), NamedTextColor.GRAY));
        }
    }

    private void handleOpen(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop open <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        Optional<Shop> shopOpt = shopService.getShop(shopId);

        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();

        if (!shop.canAccess(player.getUniqueId())) {
            player.sendMessage(Component.text("你没有权限访问此商店", NamedTextColor.RED));
            return;
        }

        // 打开商店GUI（通过事件）
        org.bukkit.Bukkit.getServer().getPluginManager().callEvent(
            new dev.starcore.starcore.module.shop.event.ShopOpenEvent(player, shop)
        );
    }

    private void handleClose(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop close <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        Optional<Shop> shopOpt = shopService.getShop(shopId);

        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        shop.setOpen(false);
        shopService.updateShop(shop);
        player.sendMessage(Component.text("商店已关闭", NamedTextColor.GREEN));
    }

    private void handleAddItem(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(Component.text("用法: /shop add <商店ID> <物品> <数量> <购买价> [出售价]", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        String materialName = args[2].toUpperCase();
        int amount;
        BigDecimal buyPrice;
        BigDecimal sellPrice = BigDecimal.ZERO;

        try {
            amount = Integer.parseInt(args[3]);
            buyPrice = new BigDecimal(args[4]);
            if (args.length > 5) {
                sellPrice = new BigDecimal(args[5]);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的数字", NamedTextColor.RED));
            return;
        }

        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("无效的物品: " + materialName, NamedTextColor.RED));
            return;
        }

        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        ShopItem item = ShopItem.create(material, amount, buyPrice, sellPrice, 64);
        if (shopService.addItemToShop(shopId, item, player.getUniqueId())) {
            player.sendMessage(Component.text("物品已添加: " + materialName, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("添加失败", NamedTextColor.RED));
        }
    }

    private void handleRemoveItem(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /shop remove <商店ID> <物品ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        UUID itemId = parseItemId(args[2]);
        if (itemId == null) {
            player.sendMessage(Component.text("无效的物品ID", NamedTextColor.RED));
            return;
        }

        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        if (shopService.removeItemFromShop(shopId, itemId, player.getUniqueId())) {
            player.sendMessage(Component.text("物品已移除", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("移除失败", NamedTextColor.RED));
        }
    }

    private void handleEditItem(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(Component.text("用法: /shop edit <商店ID> <物品ID> <属性> <新值>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("属性: buy|sell|stock|maxstock|amount", NamedTextColor.GRAY));
            player.sendMessage(Component.text("示例: /shop edit abc12345 def67890 buy 100", NamedTextColor.GRAY));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        UUID itemId = parseItemId(args[2]);
        String property = args[3].toLowerCase();

        if (shopId == null || itemId == null) {
            player.sendMessage(Component.text("无效的商店ID或物品ID", NamedTextColor.RED));
            return;
        }

        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        // 查找物品
        ShopItem item = shop.items().stream()
            .filter(i -> i.itemId().equals(itemId))
            .findFirst()
            .orElse(null);

        if (item == null) {
            player.sendMessage(Component.text("商店中没有此物品", NamedTextColor.RED));
            return;
        }

        String newValue = args[4];
        boolean updated = false;

        try {
            switch (property) {
                case "buy" -> {
                    BigDecimal buyPrice = new BigDecimal(newValue);
                    item.setBuyPrice(buyPrice);
                    player.sendMessage(Component.text("购买价格已更新为: " + buyPrice, NamedTextColor.GREEN));
                    updated = true;
                }
                case "sell" -> {
                    BigDecimal sellPrice = new BigDecimal(newValue);
                    item.setSellPrice(sellPrice);
                    player.sendMessage(Component.text("出售价格已更新为: " + sellPrice, NamedTextColor.GREEN));
                    updated = true;
                }
                case "stock" -> {
                    int stock = Integer.parseInt(newValue);
                    item.setStock(stock);
                    player.sendMessage(Component.text("当前库存已更新为: " + stock, NamedTextColor.GREEN));
                    updated = true;
                }
                case "maxstock" -> {
                    int maxStock = Integer.parseInt(newValue);
                    item.setMaxStock(maxStock);
                    player.sendMessage(Component.text("最大库存已更新为: " + maxStock, NamedTextColor.GREEN));
                    updated = true;
                }
                case "amount" -> {
                    int amount = Integer.parseInt(newValue);
                    item.setAmount(amount);
                    player.sendMessage(Component.text("每组数量已更新为: " + amount, NamedTextColor.GREEN));
                    updated = true;
                }
                default -> {
                    player.sendMessage(Component.text("未知属性: " + property, NamedTextColor.RED));
                    player.sendMessage(Component.text("可用属性: buy|sell|stock|maxstock|amount", NamedTextColor.GRAY));
                    return;
                }
            }

            if (updated) {
                shop.updateItem(item);
                shopService.updateShop(shop);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的数字: " + newValue, NamedTextColor.RED));
        }
    }

    private void handleBuy(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("用法: /shop buy <商店ID> <物品ID> <数量>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        UUID itemId = parseItemId(args[2]);
        if (itemId == null) {
            player.sendMessage(Component.text("无效的物品ID", NamedTextColor.RED));
            return;
        }
        int quantity;

        try {
            quantity = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的数量", NamedTextColor.RED));
            return;
        }

        TransactionResult result = shopService.buyItem(shopId, itemId, quantity, player);

        if (result.isSuccess()) {
            player.sendMessage(Component.text()
                .append(Component.text("购买成功! ", NamedTextColor.GREEN))
                .append(Component.text(result.actualQuantity() + " 件物品", NamedTextColor.GOLD))
                .append(Component.text("，花费: " + result.amount(), NamedTextColor.GRAY)));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("用法: /shop sell <商店ID> <物品ID> <数量>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        UUID itemId = parseItemId(args[2]);
        if (itemId == null) {
            player.sendMessage(Component.text("无效的物品ID", NamedTextColor.RED));
            return;
        }
        int quantity;

        try {
            quantity = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的数量", NamedTextColor.RED));
            return;
        }

        // 获取商店物品信息用于验证
        Optional<Shop> shopOpt = shopService.getShop(shopId);
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();
        Optional<ShopItem> itemOpt = shop.items().stream()
            .filter(i -> i.itemId().equals(itemId))
            .findFirst();

        if (itemOpt.isEmpty()) {
            player.sendMessage(Component.text("商店中没有此物品", NamedTextColor.RED));
            return;
        }

        ShopItem shopItem = itemOpt.get();

        // 检查是否有出售价格
        if (shopItem.sellPrice().compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(Component.text("此商店不收购此物品", NamedTextColor.RED));
            return;
        }

        // 检查玩家背包是否有足够的物品
        org.bukkit.inventory.ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            player.sendMessage(Component.text("请手持要出售的物品", NamedTextColor.YELLOW));
            return;
        }

        // 检查物品类型是否匹配
        if (itemInHand.getType() != shopItem.material()) {
            player.sendMessage(Component.text("物品类型不匹配，手持物品: " + itemInHand.getType().name() + ", 商店需要: " + shopItem.material().name(), NamedTextColor.RED));
            return;
        }

        // 检查数量
        if (itemInHand.getAmount() < quantity) {
            player.sendMessage(Component.text("物品数量不足，你只有 " + itemInHand.getAmount() + " 个", NamedTextColor.RED));
            return;
        }

        TransactionResult result = shopService.sellItem(shopId, itemId, quantity, itemInHand, player);

        if (result.isSuccess()) {
            player.sendMessage(Component.text()
                .append(Component.text("出售成功! ", NamedTextColor.GREEN))
                .append(Component.text(result.actualQuantity() + " 件物品", NamedTextColor.GOLD))
                .append(Component.text("，获得: " + result.amount() + " 金币", NamedTextColor.YELLOW)));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        }
    }

    private void handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop search <关键词>", NamedTextColor.YELLOW));
            return;
        }

        String term = args[1];
        List<Shop> results = shopService.searchShops(term);

        if (results.isEmpty()) {
            player.sendMessage(Component.text("没有找到匹配的商店", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 搜索结果 ===", NamedTextColor.GOLD));
        for (Shop shop : results.stream().limit(10).toList()) {
            player.sendMessage(Component.text()
                .append(Component.text("[" + shop.shopId().toString().substring(0, 8) + "] ", NamedTextColor.GRAY))
                .append(Component.text(shop.name(), NamedTextColor.AQUA)));
        }
    }

    private void handlePublic(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop public <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        Optional<Shop> shopOpt = shopService.getShop(shopId);

        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        shop.setGlobalPublic(true);
        shopService.updateShop(shop);
        player.sendMessage(Component.text("商店已设为公开", NamedTextColor.GREEN));
    }

    private void handlePrivate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop private <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        Optional<Shop> shopOpt = shopService.getShop(shopId);

        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("商店不存在", NamedTextColor.RED));
            return;
        }

        Shop shop = shopOpt.get();
        if (!shop.ownerId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("你不是此商店的拥有者", NamedTextColor.RED));
            return;
        }

        shop.setGlobalPublic(false);
        shopService.updateShop(shop);
        player.sendMessage(Component.text("商店已设为私有", NamedTextColor.GREEN));
    }

    private void handleAdmin(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /shop admin <商店ID> <add|remove> <玩家>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        String action = args[2].toLowerCase();

        Player target = org.bukkit.Bukkit.getPlayer(args[3]);
        if (target == null) {
            player.sendMessage(Component.text("玩家不存在或离线", NamedTextColor.RED));
            return;
        }

        UUID targetId = target.getUniqueId();

        if ("add".equals(action)) {
            if (shopService.addShopAdmin(shopId, targetId)) {
                player.sendMessage(Component.text("已添加管理员: " + args[3], NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("添加失败", NamedTextColor.RED));
            }
        } else if ("remove".equals(action)) {
            if (shopService.removeShopAdmin(shopId, targetId)) {
                player.sendMessage(Component.text("已移除管理员: " + args[3], NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("移除失败", NamedTextColor.RED));
            }
        }
    }

    private void handleStats(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop stats <商店ID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        BigDecimal revenue = shopService.getShopRevenue(shopId);

        player.sendMessage(Component.text("=== 商店统计 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("总收入: " + revenue + " 金币", NamedTextColor.GREEN));
    }

    private void handleHistory(Player player, String[] args) {
        List<ShopTransaction> transactions = shopService.getPlayerTransactions(player.getUniqueId(), 10);

        if (transactions.isEmpty()) {
            player.sendMessage(Component.text("没有交易记录", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 交易记录 ===", NamedTextColor.GOLD));
        for (ShopTransaction tx : transactions) {
            player.sendMessage(Component.text()
                .append(Component.text(tx.getType().name() + " ", NamedTextColor.AQUA))
                .append(Component.text(tx.getAmount().toPlainString() + " 金币", NamedTextColor.GOLD)));
        }
    }

    private void handleBind(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("用法: /shop bind <商店ID> <NPCID>", NamedTextColor.YELLOW));
            return;
        }

        UUID shopId = parseShopId(args[1]);
        int npcId;

        try {
            npcId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的NPC ID", NamedTextColor.RED));
            return;
        }

        if (npcShopService.bindShopToNpc(shopId, npcId)) {
            player.sendMessage(Component.text("商店已绑定到NPC " + npcId, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("绑定失败", NamedTextColor.RED));
        }
    }

    private void handleUnbind(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /shop unbind <NPCID>", NamedTextColor.YELLOW));
            return;
        }

        int npcId;

        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("无效的NPC ID", NamedTextColor.RED));
            return;
        }

        if (npcShopService.unbindShopFromNpc(npcId)) {
            player.sendMessage(Component.text("已解除绑定", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("解除绑定失败", NamedTextColor.RED));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== 商店帮助 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/shop create <名称> - 创建商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop list [public|nation] - 列出商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop info <ID> - 查看商店信息", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop open <ID> - 打开商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop add <ID> <物品> <数量> <买价> [卖价] - 添加物品", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop remove <ID> <物品ID> - 移除物品", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop buy <ID> <物品ID> <数量> - 购买物品", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop search <关键词> - 搜索商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop public <ID> - 公开商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop private <ID> - 私有化商店", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop stats <ID> - 商店统计", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop history - 我的交易记录", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/shop bind <ID> <NPC> - 绑定到NPC", NamedTextColor.GRAY));
    }

    @Nullable
    private UUID parseShopId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            if (id.length() == 8) {
                // 查找匹配的商店 - 通过玩家名查找
                org.bukkit.entity.Player targetPlayer = org.bukkit.Bukkit.getPlayer(id);
                if (targetPlayer == null) {
                    return null;
                }
                return shopService.getPlayerShops(targetPlayer.getUniqueId()).stream()
                    .filter(s -> s.shopId().toString().startsWith(id))
                    .map(Shop::shopId)
                    .findFirst()
                    .orElse(null);
            }
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID parseItemId(String id) {
        try {
            return UUID.fromString(id);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("create", "c", "list", "ls", "info", "i", "open", "o", "add", "a", "remove", "rm", "r", "edit", "e", "buy", "b", "sell", "s", "search", "find", "f", "public", "pub", "private", "priv", "admin", "stats", "history", "hist", "bind", "unbind", "help", "h", "?");
        }

        // Tab补全需要玩家上下文，非玩家返回空列表
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "list", "ls" -> {
                    return List.of("public", "nation", "my");
                }
                case "open", "o", "info", "i", "delete", "del", "d", "close", "public", "pub", "private", "priv", "stats", "bind" -> {
                    return shopService.getPlayerShops(player.getUniqueId()).stream()
                        .map(s -> s.shopId().toString().substring(0, 8))
                        .collect(Collectors.toList());
                }
                case "admin" -> {
                    return List.of("<shopId>");
                }
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "add", "a" -> {
                    return List.of("DIAMOND", "GOLD_INGOT", "IRON_INGOT", "COAL", "EMERALD", "APPLE", "BREAD", "BOOK", "WOOD", "STONE", "GOLD_NUGGET", "EXPERIENCE_BOTTLE");
                }
                case "admin" -> {
                    return List.of("add", "remove");
                }
                case "unbind" -> {
                    return List.of("<npcId>");
                }
                case "search", "find", "f" -> {
                    return List.of("<关键词>");
                }
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            return List.of(); // 玩家名补全由Bukkit处理
        }

        return List.of();
    }
}
