package dev.starcore.starcore.module.resource.gui;

import java.util.concurrent.ConcurrentHashMap;
import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.*;
import dev.starcore.starcore.module.resource.model.ResourcePrice;
import dev.starcore.starcore.module.resource.model.TradeOrder;
import dev.starcore.starcore.module.resource.model.TradeRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 资源系统 GUI 菜单
 * 提供交互式的资源管理界面
 */
public class ResourceMenu {

    public static final String MENU_TITLE = "§6§l资源管理系统";
    public static final String STOCKPILE_MENU_TITLE = "§6§l资源储量";
    public static final String MARKET_MENU_TITLE = "§6§l资源市场";
    public static final String RESERVE_MENU_TITLE = "§6§l战略储备";
    public static final String ORDER_BOOK_MENU_TITLE = "§6§l市场订单簿";
    public static final String TRADE_HISTORY_MENU_TITLE = "§6§l交易历史";
    public static final String PLAYER_TRADE_MENU_TITLE = "§6§l玩家交易";

    private final ResourceService resourceService;
    private final ResourcePriceService priceService;
    private final ResourceReserveService reserveService;
    private final MarketOrderBookService orderBookService;
    private final TradeHistoryService tradeHistoryService;
    private final PlayerTradeService playerTradeService;
    private final NationService nationService;

    // 动画管理器
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;
    private final MessageService messages;
    private final Map<UUID, String> selectedResource = new ConcurrentHashMap<>();

    // 菜单标题（支持语言键）
    private static final String MENU_TITLE_KEY = "resource.menu.title";
    private static final String STOCKPILE_TITLE_KEY = "resource.menu.stockpile.title";
    private static final String MARKET_TITLE_KEY = "resource.menu.market.title";
    private static final String RESERVE_TITLE_KEY = "resource.menu.reserve.title";
    private static final String ORDER_BOOK_TITLE_KEY = "resource.menu.orderbook.title";
    private static final String TRADE_HISTORY_TITLE_KEY = "resource.menu.tradehistory.title";
    private static final String PLAYER_TRADE_TITLE_KEY = "resource.menu.playertrade.title";

    public ResourceMenu(
            ResourceService resourceService,
            ResourcePriceService priceService,
            ResourceReserveService reserveService,
            NationService nationService,
            GuiAnimationManager animationManager,
            SoundFeedbackManager soundManager,
            MessageService messages
    ) {
        this.resourceService = resourceService;
        this.priceService = priceService;
        this.reserveService = reserveService;
        this.orderBookService = null;
        this.tradeHistoryService = null;
        this.playerTradeService = null;
        this.nationService = nationService;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
        this.messages = messages;
    }

    public ResourceMenu(
            ResourceService resourceService,
            ResourcePriceService priceService,
            ResourceReserveService reserveService,
            MarketOrderBookService orderBookService,
            TradeHistoryService tradeHistoryService,
            PlayerTradeService playerTradeService,
            GuiAnimationManager animationManager,
            SoundFeedbackManager soundManager,
            MessageService messages
    ) {
        this.resourceService = resourceService;
        this.priceService = priceService;
        this.reserveService = reserveService;
        this.orderBookService = orderBookService;
        this.tradeHistoryService = tradeHistoryService;
        this.playerTradeService = playerTradeService;
        this.nationService = null;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
        this.messages = messages;
    }

    /**
     * Bug修复 #4: 检查玩家是否有资源管理权限
     * 注意: 对于市场交易等个人操作，不需要权限检查
     * 但对于国家资源储备、战略储备等操作，需要权限
     * @return true if player has permission, false otherwise
     */
    private boolean checkResourceManagePermission(Player player, Inventory inv, NationId nationId) {
        if (nationId == null) {
            ItemStack noNation = createItem(Material.BARRIER, "§c你没有所属国家", List.of(
                "§7无法管理国家资源"
            ));
            inv.setItem(13, noNation);
            ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
            inv.setItem(22, back);
            player.openInventory(inv);
            return false;
        }

        if (nationService != null) {
            Nation nation = nationService.nationById(nationId).orElse(null);
            if (nation != null && !nation.hasPermission(player.getUniqueId(), "resource.manage")) {
                ItemStack noPermission = createItem(Material.BARRIER, "§c权限不足", List.of(
                    "§7只有国家创始人和资源管理员",
                    "§7才能管理国家资源储备"
                ));
                inv.setItem(13, noPermission);
                ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
                inv.setItem(22, back);
                player.openInventory(inv);
                return false;
            }
        }

        return true;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        // 尝试获取国家信息
        NationId nationId = nationService != null
            ? nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null)
            : null;
        openMainMenu(player, nationId);
    }

    /**
     * 打开主菜单（带国家信息）
     * @param nationId 玩家所在国家的ID，可能为null
     */
    public void openMainMenu(Player player, NationId nationId) {
        // 播放菜单打开动画
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, MENU_TITLE.replace("§6§l", ""));
        }

        Inventory inv = Bukkit.createInventory(null, 36, MENU_TITLE);

        // 设置装饰物品
        setBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // 获取储量统计信息
        Map<String, Long> stockpileData = new HashMap<>();
        int totalResources = 0;
        if (nationId != null) {
            stockpileData = resourceService.stockpile(nationId);
            totalResources = stockpileData.values().stream().mapToInt(Long::intValue).sum();
        }

        // 资源储量 - 显示储量摘要
        String stockpileInfo = nationId != null
            ? "§7储量总计: §f" + formatNumber(totalResources) + " §7单位"
            : "§7加入国家后查看储量";
        String stockpileStatus = nationId != null ? "§a已加入国家" : "§c未加入国家";

        ItemStack stockpile = createItem(Material.CHEST, "§e资源储量", List.of(
            stockpileInfo,
            "§7状态: " + stockpileStatus,
            "",
            "§a点击打开"
        ));
        inv.setItem(11, stockpile);

        // 资源市场
        ItemStack market = createItem(Material.BEACON, "§e资源市场", List.of(
            "§7查看市场价格波动",
            "§7买卖资源赚取差价",
            "",
            "§a点击打开"
        ));
        inv.setItem(13, market);

        // 战略储备 - 显示储备状态
        String reserveInfo = nationId != null
            ? "§7查看和管理战略储备"
            : "§7加入国家后使用";
        ItemStack reserve = createItem(Material.ENDER_CHEST, "§e战略储备", List.of(
            reserveInfo,
            "",
            "§a点击打开"
        ));
        inv.setItem(15, reserve);

        // 市场订单簿（新增）
        if (orderBookService != null) {
            ItemStack orderBook = createItem(Material.BOOK, "§e市场订单簿", List.of(
                "§7查看和管理市场订单",
                "§7查看实时买卖盘",
                "",
                "§a点击打开"
            ));
            inv.setItem(20, orderBook);
        }

        // 交易历史（新增）
        if (tradeHistoryService != null) {
            ItemStack history = createItem(Material.BOOKSHELF, "§e交易历史", List.of(
                "§7查看您的交易记录",
                "§7分析交易盈亏",
                "",
                "§a点击打开"
            ));
            inv.setItem(22, history);
        }

        // 玩家交易（新增）
        if (playerTradeService != null) {
            ItemStack trade = createItem(Material.EMERALD, "§e玩家交易", List.of(
                "§7与其他玩家交易资源",
                "§7直接P2P交易",
                "",
                "§a点击打开"
            ));
            inv.setItem(24, trade);
        }

        player.openInventory(inv);
    }

    /**
     * 打开储量菜单
     * @param nationId 玩家所在国家的ID
     */
    public void openStockpileMenu(Player player, NationId nationId) {
        Inventory inv = Bukkit.createInventory(null, 27, STOCKPILE_MENU_TITLE);
        setBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Bug修复 #4: 权限检查 - 查看储量需要权限
        if (!checkResourceManagePermission(player, inv, nationId)) {
            return;
        }

        // 获取真实的储量数据
        Map<String, Long> stockpileData = nationId != null
            ? resourceService.stockpile(nationId)
            : new HashMap<>();

        int slot = 10;
        for (String resourceId : resourceService.availableResourceTypes()) {
            // 使用真实储量数据，默认为0
            long amount = stockpileData.getOrDefault(resourceId, 0L);
            Material material = getResourceMaterial(resourceId);
            String name = getResourceDisplayName(resourceId);

            // 颜色根据储量多少变化
            String amountColor = amount > 10000 ? "§a" : amount > 1000 ? "§e" : "§f";
            String progressColor = amount > 0 ? "§a" : "§7";

            ItemStack item = createItem(material, "§e" + name, List.of(
                "§7当前储量: " + amountColor + formatNumber(amount),
                "",
                "§7点击查看详情"
            ));
            inv.setItem(slot, item);

            slot++;
            if (slot == 17) slot = 19;
        }

        // 返回按钮
        ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
        inv.setItem(22, back);

        player.openInventory(inv);
    }

    /**
     * 打开市场菜单
     */
    public void openMarketMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, MARKET_MENU_TITLE);
        setBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        int slot = 10;
        for (String resourceId : resourceService.availableResourceTypes()) {
            var priceOpt = priceService.getPrice(resourceId);
            Material material = getResourceMaterial(resourceId);
            String name = getResourceDisplayName(resourceId);

            if (priceOpt.isPresent()) {
                var price = priceOpt.get();
                var state = priceService.getMarketState(resourceId);
                double trend = price.priceChangePercentage();

                String trendStr = trend >= 0 ? "§a+" + String.format("%.1f", trend) + "%" : "§c" + String.format("%.1f", trend) + "%";
                String stateColor = getStateColor(state);

                ItemStack item = createItem(material, "§e" + name, List.of(
                    "§7当前价格: §6" + String.format("%.2f", price.currentPrice()),
                    "§7基准价格: §f" + String.format("%.2f", price.basePrice()),
                    "§7价格趋势: " + trendStr,
                    "",
                    "§7市场状态: " + stateColor + state.displayName(),
                    "§7供应量: §f" + String.format("%.1f", price.supply()),
                    "§7需求量: §f" + String.format("%.1f", price.demand()),
                    "",
                    "§7点击查看详细分析"
                ));
                inv.setItem(slot, item);
            } else {
                ItemStack item = createItem(material, "§e" + name, List.of(
                    "§7当前价格: §c无数据",
                    "",
                    "§7点击查看详细分析"
                ));
                inv.setItem(slot, item);
            }

            slot++;
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
        }

        // 返回按钮
        ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
        inv.setItem(40, back);

        player.openInventory(inv);
    }

    /**
     * 打开订单簿菜单
     */
    public void openOrderBookMenu(Player player, String resourceId) {
        if (orderBookService == null) {
            player.sendMessage("§c市场订单簿功能暂不可用");
            return;
        }

        selectedResource.put(player.getUniqueId(), resourceId);

        var snapshot = orderBookService.getOrderBookSnapshot(resourceId);

        Inventory inv = Bukkit.createInventory(null, 54, ORDER_BOOK_MENU_TITLE + " - " + getResourceDisplayName(resourceId));
        setBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // 市场概览
        ItemStack overview = createItem(Material.PAPER, "§e市场概览", List.of(
            "§7最佳买价: §a" + String.format("%.2f", snapshot.bestBid()),
            "§7最佳卖价: §c" + String.format("%.2f", snapshot.bestAsk()),
            "§7中间价: §f" + String.format("%.2f", snapshot.midPrice()),
            "§7买卖价差: §f" + String.format("%.2f", snapshot.spread()),
            "",
            "§7买单数量: §a" + snapshot.buyOrderCount(),
            "§7卖单数量: §c" + snapshot.sellOrderCount(),
            "§7总买入量: §f" + formatNumber(snapshot.totalBuyVolume()),
            "§7总卖出量: §f" + formatNumber(snapshot.totalSellVolume())
        ));
        inv.setItem(4, overview);

        // 买单列表标题
        ItemStack buyTitle = createItem(Material.GREEN_STAINED_GLASS_PANE, "§a§l买单列表", List.of());
        inv.setItem(19, buyTitle);

        // 卖单列表标题
        ItemStack sellTitle = createItem(Material.RED_STAINED_GLASS_PANE, "§c§l卖单列表", List.of());
        inv.setItem(28, sellTitle);

        // 显示买单（按价格排序）
        var book = orderBookService.getOrderBook(resourceId);
        int buySlot = 20;
        for (TradeOrder order : book.getBuyOrders()) {
            if (buySlot >= 28 || buySlot == 27) break;
            if (buySlot == 27) buySlot = 28;

            ItemStack item = createItem(Material.GREEN_WOOL, "§a买单 #" + order.orderId().toString().substring(0, 8), List.of(
                "§7价格: §a" + String.format("%.2f", order.pricePerUnit()),
                "§7数量: " + formatNumber(order.remainingAmount()),
                "§7状态: " + getOrderStatusColor(order.status()) + order.status().displayName()
            ));
            inv.setItem(buySlot++, item);
        }

        // 显示卖单
        int sellSlot = 29;
        for (TradeOrder order : book.getSellOrders()) {
            if (sellSlot >= 37 || sellSlot == 36) break;

            ItemStack item = createItem(Material.RED_WOOL, "§c卖单 #" + order.orderId().toString().substring(0, 8), List.of(
                "§7价格: §c" + String.format("%.2f", order.pricePerUnit()),
                "§7数量: " + formatNumber(order.remainingAmount()),
                "§7状态: " + getOrderStatusColor(order.status()) + order.status().displayName()
            ));
            inv.setItem(sellSlot++, item);
        }

        // 玩家订单
        ItemStack myOrders = createItem(Material.NAME_TAG, "§e我的订单", List.of(
            "§7查看您在该市场的订单",
            "",
            "§a点击查看"
        ));
        inv.setItem(49, myOrders);

        // 返回按钮
        ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
        inv.setItem(53, back);

        player.openInventory(inv);
    }

    /**
     * 打开交易历史菜单
     */
    public void openTradeHistoryMenu(Player player) {
        if (tradeHistoryService == null) {
            player.sendMessage("§c交易历史功能暂不可用");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, TRADE_HISTORY_MENU_TITLE);
        setBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // 获取玩家最近的交易记录
        List<TradeRecord> records = tradeHistoryService.getPlayerRecords(player.getUniqueId(), 0, 28);

        int slot = 0;
        for (TradeRecord record : records) {
            if (slot >= 45) break;

            Material material = getResourceMaterial(record.resourceId());
            boolean isBuy = record.orderType() == TradeOrder.OrderType.BUY;

            ItemStack item = createItem(material,
                isBuy ? "§a购买 " + getResourceDisplayName(record.resourceId()) : "§c出售 " + getResourceDisplayName(record.resourceId()),
                List.of(
                    "§7数量: " + formatNumber(record.amount()),
                    "§7单价: " + String.format("%.2f", record.pricePerUnit()),
                    "§7总额: §6" + String.format("%.2f", record.totalValue()),
                    "§7税收: §c" + String.format("%.2f", record.taxAmount()),
                    "§7时间: §f" + formatTime(record.executedAt()),
                    "",
                    isBuy ? "§a从市场买入" : "§c向市场卖出"
                )
            );
            inv.setItem(slot++, item);
        }

        if (records.isEmpty()) {
            ItemStack empty = createItem(Material.BARRIER, "§c暂无交易记录", List.of(
                "§7您还没有任何交易记录"
            ));
            inv.setItem(22, empty);
        }

        // 返回按钮
        ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
        inv.setItem(53, back);

        player.openInventory(inv);
    }

    /**
     * 打开玩家交易菜单
     */
    public void openPlayerTradeMenu(Player player) {
        if (playerTradeService == null) {
            player.sendMessage("§c玩家交易功能暂不可用");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 36, PLAYER_TRADE_MENU_TITLE);
        setBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // 待处理请求数量
        int pendingCount = playerTradeService.getPendingOfferCount(player.getUniqueId());

        // 收到的交易请求
        List<PlayerTradeService.PlayerTradeOffer> received = playerTradeService.getReceivedOffers(player.getUniqueId())
            .stream().filter(PlayerTradeService.PlayerTradeOffer::isPending).toList();

        // 发出的交易请求
        List<PlayerTradeService.PlayerTradeOffer> sent = playerTradeService.getSentOffers(player.getUniqueId())
            .stream().filter(PlayerTradeService.PlayerTradeOffer::isPending).toList();

        ItemStack incoming = createItem(Material.REDSTONE_BLOCK, "§c收到的交易请求", List.of(
            "§7待处理请求: §f" + pendingCount,
            "",
            "§7点击查看"
        ));
        inv.setItem(11, incoming);

        ItemStack outgoing = createItem(Material.LAPIS_BLOCK, "§9发出的交易请求", List.of(
            "§7待处理请求: §f" + sent.size(),
            "",
            "§7点击查看"
        ));
        inv.setItem(13, outgoing);

        ItemStack newTrade = createItem(Material.EMERALD, "§a发起新交易", List.of(
            "§7向其他玩家发起交易请求",
            "",
            "§7使用方法: /trade <玩家> <资源> <数量> <价格>"
        ));
        inv.setItem(15, newTrade);

        // 快速交易选项
        ItemStack quickBuy = createItem(Material.GOLD_INGOT, "§e快速买入", List.of(
            "§7以市场价格快速买入资源",
            "",
            "§7使用方法: /trade buy <资源> <数量>"
        ));
        inv.setItem(20, quickBuy);

        ItemStack quickSell = createItem(Material.IRON_INGOT, "§7快速卖出", List.of(
            "§7以市场价格快速卖出资源",
            "",
            "§7使用方法: /trade sell <资源> <数量>"
        ));
        inv.setItem(24, quickSell);

        // 返回按钮
        ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
        inv.setItem(31, back);

        player.openInventory(inv);
    }

    /**
     * 打开储备菜单
     * @param nationId 玩家所在国家的ID
     */
    public void openReserveMenu(Player player, NationId nationId) {
        Inventory inv = Bukkit.createInventory(null, 36, RESERVE_MENU_TITLE);
        setBorder(inv, Material.BLACK_STAINED_GLASS_PANE);

        // Bug修复 #4: 权限检查 - 战略储备需要权限
        if (!checkResourceManagePermission(player, inv, nationId)) {
            return;
        }

        // 获取真实的储备数据
        double overallProgress = 0.0;
        int unmetGoals = 0;
        if (nationId != null && reserveService != null) {
            overallProgress = reserveService.getOverallProgress(nationId);
            unmetGoals = reserveService.getUnmetGoalsCount(nationId);
        }

        // 总体进度
        ItemStack progress = createItem(Material.NETHER_STAR, "§e总体进度", List.of(
            createProgressBar(overallProgress),
            "",
            "§7未完成目标: §f" + unmetGoals
        ));
        inv.setItem(4, progress);

        // 资源储备项
        int slot = 10;
        for (String resourceId : resourceService.availableResourceTypes()) {
            // 使用真实储备数据
            long reserve = 0L;
            long goal = 0L;
            double progressVal = 0.0;

            if (nationId != null && reserveService != null) {
                reserve = reserveService.getReserveAmount(nationId, resourceId);
                goal = reserveService.getReserveGoal(nationId, resourceId);
                progressVal = goal > 0 ? (double) reserve / goal : 0;
            }

            Material material = getResourceMaterial(resourceId);
            String name = getResourceDisplayName(resourceId);

            // 颜色根据完成度变化
            String progressColor = progressVal >= 1.0 ? "§a" : progressVal > 0.5 ? "§e" : progressVal > 0 ? "§c" : "§7";
            String statusIcon = progressVal >= 1.0 ? " §a✓" : progressVal > 0 ? " §e⏳" : " §c✗";

            List<String> lore = new ArrayList<>();
            lore.add("§7当前储备: §f" + formatNumber(reserve));
            lore.add("§7目标储备: §f" + formatNumber(goal));
            lore.add("");
            lore.add(progressColor + createProgressBar(progressVal));
            lore.add("");
            lore.add("§7状态: " + progressColor + (progressVal >= 1.0 ? "已完成" : progressVal > 0 ? "进行中" : "未启动") + statusIcon);
            lore.add("");
            lore.add("§7左键: 存入储备");
            lore.add("§7右键: 从储备取出");

            ItemStack item = createItem(material, "§e" + name, lore);
            inv.setItem(slot, item);

            slot++;
            if (slot == 17) slot = 19;
        }

        // 存入/取出说明
        ItemStack help = createItem(Material.BOOK, "§e操作说明", List.of(
            "§7存入储备: §f/reserve deposit <资源> <数量>",
            "§7取出储备: §f/reserve withdraw <资源> <数量>",
            "",
            "§7战略储备用于紧急情况下的资源调用"
        ));
        inv.setItem(22, help);

        // 返回按钮
        ItemStack back = createItem(Material.ARROW, "§c返回", List.of("§7返回上一级菜单"));
        inv.setItem(31, back);

        player.openInventory(inv);
    }

    // ==================== 辅助方法 ====================

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setBorder(Inventory inv, Material material) {
        ItemStack border = new ItemStack(material);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            border.setItemMeta(meta);
        }

        // 顶部和底部边框
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(inv.getSize() - 9 + i, border);
        }

        // 左右边框
        for (int i = 1; i < (inv.getSize() / 9) - 1; i++) {
            inv.setItem(i * 9, border);
            inv.setItem(i * 9 + 8, border);
        }
    }

    private String createProgressBar(double progress) {
        int filled = (int) (progress * 10);
        StringBuilder bar = new StringBuilder("§7[");
        for (int i = 0; i < 10; i++) {
            if (i < filled) {
                bar.append(progress >= 1.0 ? "§a█" : "§e█");
            } else {
                bar.append("§8░");
            }
        }
        bar.append("§7] ");
        bar.append(String.format("§f%.0f%%", progress * 100));
        if (progress >= 1.0) {
            bar.append(" §a✓");
        }
        return bar.toString();
    }

    private Material getResourceMaterial(String resourceId) {
        return switch (resourceId.toLowerCase()) {
            case "food" -> Material.WHEAT;
            case "timber" -> Material.OAK_LOG;
            case "ore" -> Material.IRON_INGOT;
            case "oil" -> Material.LAVA_BUCKET;
            case "rare_metal" -> Material.DIAMOND;
            default -> Material.EMERALD;
        };
    }

    private String getResourceDisplayName(String resourceId) {
        return switch (resourceId.toLowerCase()) {
            case "food" -> "食物";
            case "timber" -> "木材";
            case "ore" -> "矿石";
            case "oil" -> "石油";
            case "rare_metal" -> "稀有金属";
            default -> resourceId;
        };
    }

    private String getStateColor(ResourcePrice.MarketState state) {
        return switch (state) {
            case SHORTAGE -> "§c";
            case HIGH_DEMAND -> "§e";
            case BALANCED -> "§a";
            case LOW_DEMAND -> "§7";
            case OVERSUPPLY -> "§9";
        };
    }

    private String getOrderStatusColor(TradeOrder.OrderStatus status) {
        return switch (status) {
            case PENDING -> "§e";
            case PARTIAL -> "§6";
            case FILLED -> "§a";
            case CANCELLED -> "§7";
            case EXPIRED -> "§c";
        };
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    private String formatTime(Instant instant) {
        if (instant == null) return "未知";
        var formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }
}
