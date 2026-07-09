package dev.starcore.starcore.module.resource.gui;

import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.*;
import dev.starcore.starcore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

/**
 * Resource Module GUI 整合类
 * 提供统一的资源系统 GUI 入口
 */
public class ResourceModuleGui {

    private final ResourceModule resourceModule;
    private final ResourceService resourceService;
    private final ResourcePriceService priceService;
    private final ResourceReserveService reserveService;
    private final MarketOrderBookService orderBookService;
    private final TradeHistoryService tradeHistoryService;
    private final PlayerTradeService playerTradeService;
    private final NationService nationService;
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;
    private final MessageService messages;
    private final JavaPlugin plugin;

    // GUI 实例
    private ResourceMenu resourceMenu;
    private ResourceMenuListener menuListener;
    private PlayerTradeGui playerTradeGui;

    public ResourceModuleGui(
            ResourceModule resourceModule,
            ResourceService resourceService,
            ResourcePriceService priceService,
            ResourceReserveService reserveService,
            MarketOrderBookService orderBookService,
            TradeHistoryService tradeHistoryService,
            PlayerTradeService playerTradeService,
            NationService nationService,
            GuiAnimationManager animationManager,
            SoundFeedbackManager soundManager,
            MessageService messages
    ) {
        this.resourceModule = resourceModule;
        this.resourceService = resourceService;
        this.priceService = priceService;
        this.reserveService = reserveService;
        this.orderBookService = orderBookService;
        this.tradeHistoryService = tradeHistoryService;
        this.playerTradeService = playerTradeService;
        this.nationService = nationService;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
        this.messages = messages;
        this.plugin = resourceModule.getPlugin();

        initializeGuis();
    }

    private void initializeGuis() {
        // 初始化主菜单
        this.resourceMenu = new ResourceMenu(
            resourceService,
            priceService,
            reserveService,
            orderBookService,
            tradeHistoryService,
            playerTradeService,
            animationManager,
            soundManager,
            messages
        );

        // 初始化监听器
        this.menuListener = new ResourceMenuListener(
            resourceModule,
            resourceMenu,
            resourceService,
            priceService,
            reserveService,
            orderBookService,
            tradeHistoryService,
            playerTradeService,
            nationService,
            soundManager,
            messages
        );
    }

    /**
     * 获取菜单监听器
     */
    public ResourceMenuListener getMenuListener() {
        return menuListener;
    }

    /**
     * 获取主菜单
     */
    public ResourceMenu getResourceMenu() {
        return resourceMenu;
    }

    /**
     * 打开资源主菜单
     */
    public void openMainMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "资源系统");
        }
        resourceMenu.openMainMenu(player);
    }

    /**
     * 打开储量菜单
     * @param player 玩家
     * @param nationId 玩家所属国家ID
     */
    public void openStockpileMenu(Player player, NationId nationId) {
        resourceMenu.openStockpileMenu(player, nationId);
    }

    /**
     * 打开市场菜单
     */
    public void openMarketMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "资源市场");
        }
        resourceMenu.openMarketMenu(player);
    }

    /**
     * 打开订单簿菜单
     */
    public void openOrderBookMenu(Player player, String resourceId) {
        resourceMenu.openOrderBookMenu(player, resourceId);
    }

    /**
     * 打开交易历史菜单
     */
    public void openTradeHistoryMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "交易历史");
        }
        resourceMenu.openTradeHistoryMenu(player);
    }

    /**
     * 打开玩家交易菜单
     */
    public void openPlayerTradeMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "玩家交易");
        }
        resourceMenu.openPlayerTradeMenu(player);
    }

    /**
     * 打开玩家交易 GUI（基于 BaseGui）
     */
    public void openPlayerTradeGui(Player player) {
        if (playerTradeService != null) {
            this.playerTradeGui = new PlayerTradeGui(
                player,
                plugin,
                playerTradeService,
                nationService,
                null,
                null
            );
            playerTradeGui.open();
        } else {
            player.sendMessage(MessageUtil.colorize("&c玩家交易功能暂不可用"));
        }
    }

    /**
     * 打开储备菜单
     * @param player 玩家
     * @param nationId 玩家所属国家ID
     */
    public void openReserveMenu(Player player, NationId nationId) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "战略储备");
        }
        resourceMenu.openReserveMenu(player, nationId);
    }

    /**
     * 打开资源详情菜单
     */
    public void openResourceDetailMenu(Player player, String resourceId) {
        if (orderBookService != null) {
            resourceMenu.openOrderBookMenu(player, resourceId);
        }
    }

    // ==================== 市场操作 ====================

    /**
     * 执行市场买入
     */
    public boolean executeBuy(Player player, String resourceId, long amount) {
        if (orderBookService == null) {
            player.sendMessage(MessageUtil.colorize("&c市场功能暂不可用"));
            return false;
        }

        var priceOpt = priceService.getPrice(resourceId);
        if (priceOpt.isEmpty()) {
            player.sendMessage(MessageUtil.colorize("&c未找到资源: " + resourceId));
            return false;
        }

        // 计算总价 - currentPrice() 返回 double
        double currentPrice = priceOpt.get().currentPrice();
        double totalPrice = currentPrice * amount;

        player.sendMessage(MessageUtil.colorize("&6正在从市场买入 " + amount + " 单位 " + resourceId));
        player.sendMessage(MessageUtil.colorize("&7总价: &e" + String.format("%.2f", totalPrice) + " 金币"));

        return true;
    }

    /**
     * 执行市场卖出
     */
    public boolean executeSell(Player player, String resourceId, long amount) {
        if (orderBookService == null) {
            player.sendMessage(MessageUtil.colorize("&c市场功能暂不可用"));
            return false;
        }

        var priceOpt = priceService.getPrice(resourceId);
        if (priceOpt.isEmpty()) {
            player.sendMessage(MessageUtil.colorize("&c未找到资源: " + resourceId));
            return false;
        }

        // 计算总收入 - currentPrice() 返回 double
        double currentPrice = priceOpt.get().currentPrice();
        double totalValue = currentPrice * amount;

        player.sendMessage(MessageUtil.colorize("&6正在向市场卖出 " + amount + " 单位 " + resourceId));
        player.sendMessage(MessageUtil.colorize("&7总收入: &a" + String.format("%.2f", totalValue) + " 金币"));

        return true;
    }

    // ==================== 储备操作 ====================

    /**
     * 存入战略储备
     */
    public boolean depositToReserve(Player player, String resourceId, long amount) {
        if (reserveService == null) {
            player.sendMessage(MessageUtil.colorize("&c储备功能暂不可用"));
            return false;
        }

        var nationIdOpt = nationService.nationOf(player.getUniqueId());
        if (nationIdOpt.isEmpty()) {
            player.sendMessage(MessageUtil.colorize("&c你没有所属国家"));
            return false;
        }

        var nation = nationIdOpt.get();
        if (reserveService.transferToReserve(nation.getId(), resourceId, amount)) {
            player.sendMessage(MessageUtil.colorize("&a成功存入 " + amount + " 单位 " + resourceId + " 到战略储备"));
            return true;
        } else {
            player.sendMessage(MessageUtil.colorize("&c存入失败，可能储量不足"));
            return false;
        }
    }

    /**
     * 从战略储备取出
     */
    public boolean withdrawFromReserve(Player player, String resourceId, long amount) {
        if (reserveService == null) {
            player.sendMessage(MessageUtil.colorize("&c储备功能暂不可用"));
            return false;
        }

        var nationIdOpt = nationService.nationOf(player.getUniqueId());
        if (nationIdOpt.isEmpty()) {
            player.sendMessage(MessageUtil.colorize("&c你没有所属国家"));
            return false;
        }

        var nation = nationIdOpt.get();
        if (reserveService.emergencyRelease(nation.getId(), resourceId, amount)) {
            player.sendMessage(MessageUtil.colorize("&a成功从战略储备取出 " + amount + " 单位 " + resourceId));
            return true;
        } else {
            player.sendMessage(MessageUtil.colorize("&c取出失败，储备可能不足"));
            return false;
        }
    }

    /**
     * 获取总储备量
     */
    public long getTotalReserve(NationId nationId) {
        if (reserveService == null) {
            return 0;
        }
        return reserveService.getAllReserves(nationId).values().stream()
            .mapToLong(Long::longValue).sum();
    }

    // ==================== 玩家交易操作 ====================

    /**
     * 创建玩家交易请求
     */
    public boolean createTradeOffer(Player player, String targetPlayerName, String resourceId,
                                    long amount, BigDecimal pricePerUnit) {
        if (playerTradeService == null) {
            player.sendMessage(MessageUtil.colorize("&c玩家交易功能暂不可用"));
            return false;
        }

        // 获取目标玩家
        Player target = Bukkit.getPlayer(targetPlayerName);
        if (target == null) {
            player.sendMessage(MessageUtil.colorize("&c玩家不存在或不在线: " + targetPlayerName));
            return false;
        }

        var nationIdOpt = nationService.nationOf(player.getUniqueId());
        var targetNationIdOpt = nationService.nationOf(target.getUniqueId());

        if (nationIdOpt.isEmpty() || targetNationIdOpt.isEmpty()) {
            player.sendMessage(MessageUtil.colorize("&c你和目标玩家都必须在同一个国家才能交易"));
            return false;
        }

        var result = playerTradeService.createTradeOffer(
            player.getUniqueId(),
            nationIdOpt.get().getId(),
            target.getUniqueId(),
            targetNationIdOpt.get().getId(),
            resourceId,
            amount,
            pricePerUnit.doubleValue(),
            3600 // 1小时过期
        );

        if (result.isPresent()) {
            player.sendMessage(MessageUtil.colorize("&a交易请求已发送给 " + target.getName()));
            target.sendMessage(MessageUtil.colorize("&e" + player.getName() + " 向你发起了一个交易请求"));
            return true;
        } else {
            player.sendMessage(MessageUtil.colorize("&c创建交易请求失败"));
            return false;
        }
    }

    /**
     * 获取资源统计信息
     */
    public ResourceStats getResourceStats(Player player) {
        var nationIdOpt = nationService.nationOf(player.getUniqueId());

        if (nationIdOpt.isEmpty()) {
            return new ResourceStats(0, 0, BigDecimal.ZERO);
        }

        var nation = nationIdOpt.get();
        var stockpile = resourceService.stockpile(nation.getId());
        long totalResources = stockpile.values().stream().mapToLong(Long::longValue).sum();
        long totalReserve = getTotalReserve(nation.getId());

        // 估算总价值 - currentPrice() 返回 double
        double totalValue = 0.0;
        for (var entry : stockpile.entrySet()) {
            var priceOpt = priceService.getPrice(entry.getKey());
            if (priceOpt.isPresent()) {
                totalValue += priceOpt.get().currentPrice() * entry.getValue();
            }
        }

        return new ResourceStats(totalResources, totalReserve, BigDecimal.valueOf(totalValue));
    }

    /**
     * 资源统计记录
     */
    public record ResourceStats(long totalResources, long totalReserve, BigDecimal estimatedValue) {}
}