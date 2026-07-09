package dev.starcore.starcore.module.resource.command;
import java.util.Optional;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.*;
import dev.starcore.starcore.module.resource.model.ResourcePrice;
import dev.starcore.starcore.module.resource.model.TradeOrder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 资源管理命令
 * 命令：/resource <子命令>
 */
public class ResourceCommand implements CommandExecutor, TabCompleter {

    private final ResourceService resourceService;
    private final ResourcePriceService priceService;
    private final ResourceTradeService tradeService;
    private final ResourceReserveService reserveService;
    private final ProcessingService processingService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    // 市场交易功能（可选）
    private MarketOrderBookService marketOrderBookService;
    private TradeHistoryService tradeHistoryService;
    private PlayerTradeService playerTradeService;
    private TradeTaxService tradeTaxService;

    public ResourceCommand(
            ResourceService resourceService,
            ResourcePriceService priceService,
            ResourceTradeService tradeService,
            ResourceReserveService reserveService,
            ProcessingService processingService,
            NationService nationService,
            OnlinePlayerDirectory onlinePlayerDirectory
    ) {
        this.resourceService = resourceService;
        this.priceService = priceService;
        this.tradeService = tradeService;
        this.reserveService = reserveService;
        this.processingService = processingService;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
        // 市场交易服务初始化为null，通过setter注入
        this.marketOrderBookService = null;
        this.tradeHistoryService = null;
        this.playerTradeService = null;
        this.tradeTaxService = null;
    }

    /**
     * 设置市场订单簿服务
     */
    public void setMarketOrderBookService(MarketOrderBookService service) {
        this.marketOrderBookService = service;
    }

    /**
     * 设置交易历史服务
     */
    public void setTradeHistoryService(TradeHistoryService service) {
        this.tradeHistoryService = service;
    }

    /**
     * 设置玩家交易服务
     */
    public void setPlayerTradeService(PlayerTradeService service) {
        this.playerTradeService = service;
    }

    /**
     * 设置交易税收服务
     */
    public void setTradeTaxService(TradeTaxService service) {
        this.tradeTaxService = service;
    }

    /**
     * 检查市场功能是否可用
     */
    private boolean hasMarketFeature() {
        return marketOrderBookService != null;
    }

    /**
     * 检查交易历史功能是否可用
     */
    private boolean hasTradeHistoryFeature() {
        return tradeHistoryService != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleInfo(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> {
                return handleInfo(player);
            }
            case "stockpile", "stock" -> {
                return handleStockpile(player);
            }
            case "market" -> {
                return handleMarket(player);
            }
            case "reserve" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /resource reserve <add|remove|info>");
                    return true;
                }
                return handleReserve(player, args);
            }
            case "trade" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /resource trade <routes|agreements|embargo>");
                    return true;
                }
                return handleTrade(player, args);
            }
            case "factory" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /resource factory <list|process|recipes>");
                    return true;
                }
                return handleFactory(player, args);
            }
            case "order" -> {
                if (!hasMarketFeature()) {
                    player.sendMessage("§c市场订单功能暂不可用");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /resource order <buy|sell|list|cancel>");
                    return true;
                }
                return handleOrder(player, args);
            }
            case "history" -> {
                if (!hasTradeHistoryFeature()) {
                    player.sendMessage("§c交易历史功能暂不可用");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /resource history [页码]");
                    return true;
                }
                return handleHistory(player, args);
            }
            case "give" -> {
                if (!sender.hasPermission("starcore.admin")) {
                    player.sendMessage("§c你没有权限使用此命令");
                    return true;
                }
                if (args.length < 4) {
                    player.sendMessage("§c用法: /resource give <Nation名称> <资源类型> <数量>");
                    return true;
                }
                return handleGive(sender, args);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                sendHelp(player);
                return true;
            }
        }
    }

    private boolean handleInfo(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();
        Map<String, Long> stockpile = resourceService.stockpile(nationId);

        player.sendMessage("§6§l==== " + nation.name() + " 资源信息 ====");
        // 分隔

        // 显示资源储量
        player.sendMessage("§e资源储量:");
        for (Map.Entry<String, Long> entry : stockpile.entrySet()) {
            String icon = getResourceIcon(entry.getKey());
            player.sendMessage(String.format("  %s §f%s: §a%d", icon, formatResourceName(entry.getKey()), entry.getValue()));
        }

        // 显示市场价格
        // 分隔
        player.sendMessage("§e市场价格:");
        for (String resourceType : resourceService.availableResourceTypes()) {
            double price = priceService.getCurrentPrice(resourceType);
            var state = priceService.getMarketState(resourceType);
            String stateColor = getStateColor(state);
            player.sendMessage(String.format("  §f%s: §6%.2f %s(%s)",
                formatResourceName(resourceType),
                price,
                stateColor,
                state.displayName()
            ));
        }

        return true;
    }

    private boolean handleStockpile(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();
        Map<String, Long> stockpile = resourceService.stockpile(nationId);

        player.sendMessage("§6§l==== 资源储量 ====");
        for (Map.Entry<String, Long> entry : stockpile.entrySet()) {
            String icon = getResourceIcon(entry.getKey());
            player.sendMessage(String.format("  %s §f%s: §a%d", icon, formatResourceName(entry.getKey()), entry.getValue()));
        }

        return true;
    }

    private boolean handleMarket(Player player) {
        player.sendMessage("§6§l==== 资源市场 ====");
        // 分隔

        for (String resourceType : resourceService.availableResourceTypes()) {
            var priceOpt = priceService.getPrice(resourceType);
            if (priceOpt.isEmpty()) {
                continue;
            }

            var price = priceOpt.get();
            var state = priceService.getMarketState(resourceType);
            String stateColor = getStateColor(state);
            double trend = priceService.getPriceTrend(resourceType);
            String trendStr = trend >= 0 ? "§a+" + String.format("%.1f", trend) + "%" : "§c" + String.format("%.1f", trend) + "%";

            player.sendMessage(String.format("§f%s:", formatResourceName(resourceType)));
            player.sendMessage(String.format("  §7价格: §6%.2f §7趋势: %s", price.currentPrice(), trendStr));
            player.sendMessage(String.format("  §7市场: %s", stateColor + state.displayName()));
            player.sendMessage(String.format("  §7供需比: §f%.2f:%.2f", price.supply(), price.demand()));
            // 分隔
        }

        return true;
    }

    private boolean handleReserve(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        String action = args[1].toLowerCase();

        switch (action) {
            case "info" -> {
                return handleReserveInfo(player, nationId);
            }
            case "add" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /resource reserve add <资源类型> <数量>");
                    return true;
                }
                String resourceType = args[2];
                try {
                    long amount = Long.parseLong(args[3]);
                    if (reserveService.transferToReserve(nationId, resourceType, amount)) {
                        player.sendMessage("§a已成功转移 " + amount + " " + formatResourceName(resourceType) + " 到储备");
                    } else {
                        player.sendMessage("§c转移失败，请检查资源是否充足");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的数量: " + args[3]);
                }
                return true;
            }
            case "release" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /resource reserve release <资源类型> <数量>");
                    return true;
                }
                String resourceType = args[2];
                try {
                    long amount = Long.parseLong(args[3]);
                    if (reserveService.emergencyRelease(nationId, resourceType, amount)) {
                        player.sendMessage("§a已成功从储备释放 " + amount + " " + formatResourceName(resourceType));
                    } else {
                        player.sendMessage("§c释放失败，储备可能不足");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的数量: " + args[3]);
                }
                return true;
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                player.sendMessage("§e可用操作: info, add, release");
                return true;
            }
        }
    }

    private boolean handleReserveInfo(Player player, NationId nationId) {
        Map<String, Long> reserves = reserveService.getAllReserves(nationId);
        Map<String, Long> goals = reserveService.getAllGoals(nationId);

        player.sendMessage("§6§l==== 战略储备 ====");

        if (reserves.isEmpty()) {
            player.sendMessage("§7暂无储备信息");
            return true;
        }

        double overallProgress = reserveService.getOverallProgress(nationId);
        player.sendMessage(String.format("§7总体完成度: §e%.1f%%", overallProgress * 100));

        for (String resourceId : reserves.keySet()) {
            long reserve = reserves.getOrDefault(resourceId, 0L);
            long goal = goals.getOrDefault(resourceId, 0L);
            double progress = goal > 0 ? (double) reserve / goal : 0;
            String progressBar = createProgressBar(progress);

            player.sendMessage(String.format("  %s: %d / %d %s",
                formatResourceName(resourceId),
                reserve,
                goal,
                progressBar
            ));
        }

        return true;
    }

    private boolean handleTrade(Player player, String[] args) {
        String action = args[1].toLowerCase();

        switch (action) {
            case "routes" -> {
                return handleTradeRoutes(player);
            }
            case "agreements" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /resource trade agreements <list|create|edit|cancel>");
                    return true;
                }
                return handleAgreements(player, args);
            }
            case "embargo" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /resource trade embargo <list|create|lift|edit>");
                    return true;
                }
                return handleEmbargo(player, args);
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                return true;
            }
        }
    }

    private boolean handleTradeRoutes(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }

        Nation nation = nationOpt.get();
        var routes = tradeService.getTradeRoutes(nation.id());

        player.sendMessage("§6§l==== 贸易路线 ====");

        if (routes.isEmpty()) {
            player.sendMessage("§7暂无贸易路线");
            return true;
        }

        for (var route : routes) {
            String status = route.isActive() ? "§a活跃" : "§c停用";
            player.sendMessage(String.format("  §e%s: §f%d%% §7- 状态: %s",
                route.routeName(),
                (int)(route.efficiency() * 100),
                status
            ));
        }

        return true;
    }

    private boolean handleAgreements(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /resource trade agreements <list|create|edit|cancel>");
            return true;
        }

        String action = args[2].toLowerCase();
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }

        Nation nation = nationOpt.get();

        switch (action) {
            case "list" -> {
                return handleAgreementsList(player, nation);
            }
            case "create" -> {
                if (args.length < 7) {
                    player.sendMessage("§c用法: /resource trade agreements create <对方国家> <资源类型> <数量> <单价> [有效期(天)]");
                    return true;
                }
                return handleAgreementCreate(player, nation, args);
            }
            case "edit" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /resource trade agreements edit <协定ID> <数量|单价|有效期>");
                    return true;
                }
                return handleAgreementEdit(player, nation, args);
            }
            case "cancel" -> {
                if (args.length < 4) {
                    player.sendMessage("§c用法: /resource trade agreements cancel <协定ID>");
                    return true;
                }
                return handleAgreementCancel(player, nation, args[3]);
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                player.sendMessage("§e可用操作: list, create, edit, cancel");
                return true;
            }
        }
    }

    private boolean handleAgreementsList(Player player, Nation nation) {
        Collection<dev.starcore.starcore.module.resource.model.TradeAgreement> agreements =
            tradeService.getTradeAgreements(nation.id());

        player.sendMessage("§6§l==== 贸易协定 ====");

        if (agreements.isEmpty()) {
            player.sendMessage("§7暂无贸易协定");
            player.sendMessage("§e使用 /resource trade agreements create 创建新协定");
            return true;
        }

        for (dev.starcore.starcore.module.resource.model.TradeAgreement agreement : agreements) {
            String status = agreement.isActive() ? "§a生效中" : "§c已过期";
            String counterparty = agreement.exporterNationId().equals(nation.id())
                ? getNationDisplayName(agreement.importerNationId())
                : getNationDisplayName(agreement.exporterNationId());
            String role = agreement.exporterNationId().equals(nation.id()) ? "§6出口" : "§9进口";

            player.sendMessage(String.format("  %s §7[§f%s§7]",
                formatResourceName(agreement.resourceId()),
                agreement.agreementId().toString().substring(0, 8)
            ));
            player.sendMessage(String.format("    §7对象: %s §7角色: %s", counterparty, role));
            player.sendMessage(String.format("    §7数量: §e%d §7单价: §6%.2f §7总计: §a%.2f",
                agreement.amount(),
                agreement.pricePerUnit(),
                agreement.amount() * agreement.pricePerUnit()
            ));
            player.sendMessage(String.format("    §7有效期: §f%s %s",
                formatExpiryTime(agreement),
                status));
        }

        return true;
    }

    private String formatExpiryTime(dev.starcore.starcore.module.resource.model.TradeAgreement agreement) {
        if (agreement.expiryTime() == null) {
            return "永久";
        }
        long remainingSeconds = agreement.expiryTime().getEpochSecond() - System.currentTimeMillis() / 1000;
        if (remainingSeconds <= 0) {
            return "已过期";
        }
        long days = remainingSeconds / 86400;
        long hours = (remainingSeconds % 86400) / 3600;
        if (days > 0) {
            return days + "天" + hours + "小时";
        } else if (hours > 0) {
            return hours + "小时";
        } else {
            return remainingSeconds / 60 + "分钟";
        }
    }

    private boolean handleAgreementCreate(Player player, Nation nation, String[] args) {
        String targetNationName = args[3];
        String resourceId = args[4];
        long amount;
        double pricePerUnit;
        long durationDays = 30; // 默认30天

        try {
            amount = Long.parseLong(args[5]);
            pricePerUnit = Double.parseDouble(args[6]);
            if (args.length > 7) {
                durationDays = Long.parseLong(args[7]);
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的数字格式");
            return true;
        }

        // 查找目标国家
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c未找到国家: " + targetNationName);
            return true;
        }

        Nation targetNation = targetNationOpt.get();
        NationId exporterId = nation.id();
        NationId importerId = targetNation.id();

        try {
            dev.starcore.starcore.module.resource.model.TradeAgreement agreement =
                tradeService.createTradeAgreement(exporterId, importerId, resourceId, amount, pricePerUnit, durationDays * 86400);

            player.sendMessage("§a贸易协定创建成功！");
            player.sendMessage(String.format("  §e资源: §f%s", formatResourceName(resourceId)));
            player.sendMessage(String.format("  §e交易对象: §f%s", targetNation.name()));
            player.sendMessage(String.format("  §e数量: §f%d §e单价: §6%.2f §e总计: §a%.2f",
                amount, pricePerUnit, amount * pricePerUnit));
            player.sendMessage(String.format("  §e有效期: §f%d天", durationDays));
            player.sendMessage(String.format("  §7协定ID: §f%s", agreement.agreementId().toString().substring(0, 8)));
        } catch (Exception e) {
            player.sendMessage("§c贸易协定创建失败: " + e.getMessage());
        }

        return true;
    }

    private boolean handleAgreementEdit(Player player, Nation nation, String[] args) {
        try {
            UUID agreementId = UUID.fromString(args[3]);
            var agreementOpt = tradeService.getTradeAgreement(agreementId);

            if (agreementOpt.isEmpty()) {
                player.sendMessage("§c未找到协定: " + args[3]);
                return true;
            }

            var agreement = agreementOpt.get();
            // 只能编辑自己参与的协定
            if (!agreement.exporterNationId().equals(nation.id()) && !agreement.importerNationId().equals(nation.id())) {
                player.sendMessage("§c你无权编辑此协定");
                return true;
            }

            String field = args[4].toLowerCase();
            switch (field) {
                case "数量", "amount" -> {
                    if (args.length < 6) {
                        player.sendMessage("§c用法: /resource trade agreements edit <协定ID> amount <新数量>");
                        return true;
                    }
                    long newAmount = Long.parseLong(args[5]);
                    // 需要重新创建协定（当前模型不可变）
                    player.sendMessage("§e注意: 当前协定数量不可修改，请取消后重新创建");
                    player.sendMessage(String.format("  §7原协定ID: §f%s", agreementId.toString().substring(0, 8)));
                    return true;
                }
                case "单价", "price" -> {
                    if (args.length < 6) {
                        player.sendMessage("§c用法: /resource trade agreements edit <协定ID> price <新单价>");
                        return true;
                    }
                    double newPrice = Double.parseDouble(args[5]);
                    player.sendMessage("§e注意: 当前协定单价不可修改，请取消后重新创建");
                    player.sendMessage(String.format("  §7原协定ID: §f%s", agreementId.toString().substring(0, 8)));
                    return true;
                }
                case "有效期", "duration" -> {
                    if (args.length < 6) {
                        player.sendMessage("§c用法: /resource trade agreements edit <协定ID> duration <天数>");
                        return true;
                    }
                    player.sendMessage("§e注意: 当前协定有效期不可修改，请取消后重新创建");
                    return true;
                }
                default -> {
                    player.sendMessage("§c未知字段: " + field);
                    player.sendMessage("§e可用字段: amount, price, duration");
                    return true;
                }
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的协定ID格式");
        }
        return true;
    }

    private boolean handleAgreementCancel(Player player, Nation nation, String agreementIdStr) {
        try {
            UUID agreementId = UUID.fromString(agreementIdStr);
            var agreementOpt = tradeService.getTradeAgreement(agreementId);

            if (agreementOpt.isEmpty()) {
                player.sendMessage("§c未找到协定: " + agreementIdStr);
                return true;
            }

            var agreement = agreementOpt.get();
            // 只能取消自己参与的协定
            if (!agreement.exporterNationId().equals(nation.id()) && !agreement.importerNationId().equals(nation.id())) {
                player.sendMessage("§c你无权取消此协定");
                return true;
            }

            if (tradeService.cancelTradeAgreement(agreementId)) {
                player.sendMessage("§a协定已取消！");
            } else {
                player.sendMessage("§c协定取消失败");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的协定ID格式");
        }
        return true;
    }

    private boolean handleEmbargo(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /resource trade embargo <list|create|lift|edit>");
            return true;
        }

        String action = args[2].toLowerCase();
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }

        Nation nation = nationOpt.get();

        switch (action) {
            case "list" -> {
                return handleEmbargoList(player, nation);
            }
            case "create" -> {
                if (args.length < 6) {
                    player.sendMessage("§c用法: /resource trade embargo create <目标国家> <资源类型> [有效期(天)] [原因]");
                    return true;
                }
                return handleEmbargoCreate(player, nation, args);
            }
            case "lift" -> {
                if (args.length < 4) {
                    player.sendMessage("§c用法: /resource trade embargo lift <禁运ID>");
                    return true;
                }
                return handleEmbargoLift(player, nation, args[3]);
            }
            case "edit" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /resource trade embargo edit <禁运ID> [duration <天数>] [reason <原因>]");
                    return true;
                }
                return handleEmbargoEdit(player, nation, args);
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                player.sendMessage("§e可用操作: list, create, lift, edit");
                return true;
            }
        }
    }

    private boolean handleEmbargoList(Player player, Nation nation) {
        Collection<dev.starcore.starcore.module.resource.model.ResourceEmbargo> initiated =
            tradeService.getEmbargoesBy(nation.id());
        Collection<dev.starcore.starcore.module.resource.model.ResourceEmbargo> against =
            tradeService.getEmbargoesAgainst(nation.id());

        player.sendMessage("§6§l==== 禁运管理 ====");

        if (initiated.isEmpty() && against.isEmpty()) {
            player.sendMessage("§7暂无禁运记录");
            return true;
        }

        if (!initiated.isEmpty()) {
            player.sendMessage("§e--- 我方发起的禁运 ---");
            for (dev.starcore.starcore.module.resource.model.ResourceEmbargo embargo : initiated) {
                String targets = embargo.targetNationIds().stream()
                    .map(this::getNationDisplayName)
                    .collect(Collectors.joining(", "));
                String status = embargo.isActive() ? "§a生效中" : "§c已过期";
                player.sendMessage(String.format("  §c%s §7-> §f%s: §e%s %s",
                    embargo.resourceId(),
                    targets,
                    embargo.reason() != null ? embargo.reason() : "无原因",
                    status));
            }
        }

        if (!against.isEmpty()) {
            player.sendMessage("§e--- 针对我方的禁运 ---");
            for (dev.starcore.starcore.module.resource.model.ResourceEmbargo embargo : against) {
                String initiator = getNationDisplayName(embargo.initiatorNationId());
                String status = embargo.isActive() ? "§c生效中" : "§a已解除";
                player.sendMessage(String.format("  §c%s §7来自 §f%s: §e%s %s",
                    embargo.resourceId(),
                    initiator,
                    embargo.reason() != null ? embargo.reason() : "无原因",
                    status));
            }
        }

        return true;
    }

    private boolean handleEmbargoCreate(Player player, Nation nation, String[] args) {
        String targetNationName = args[3];
        String resourceId = args[4];
        Long duration = null; // null 表示无限期
        String reason = null;

        // 解析可选参数
        for (int i = 5; i < args.length; i++) {
            String arg = args[i];
            try {
                // 尝试作为天数解析
                if (Character.isDigit(arg.charAt(0))) {
                    duration = Long.parseLong(arg) * 86400; // 转换为秒
                } else {
                    // 作为原因解析
                    reason = arg;
                }
            } catch (NumberFormatException e) {
                // 如果不是数字，则作为原因
                if (reason == null) {
                    reason = arg;
                } else {
                    reason = reason + " " + arg;
                }
            }
        }

        // 查找目标国家
        Optional<Nation> targetNationOpt = nationService.nationByName(targetNationName);
        if (targetNationOpt.isEmpty()) {
            player.sendMessage("§c未找到国家: " + targetNationName);
            return true;
        }

        NationId targetNationId = targetNationOpt.get().id();
        Set<NationId> targetIds = new HashSet<>();
        targetIds.add(targetNationId);

        try {
            dev.starcore.starcore.module.resource.model.ResourceEmbargo embargo =
                tradeService.createEmbargo(nation.id(), targetIds, resourceId, duration, reason);
            player.sendMessage("§a禁运创建成功！");
            player.sendMessage(String.format("  §e资源: §f%s", formatResourceName(resourceId)));
            player.sendMessage(String.format("  §e目标: §f%s", targetNationOpt.get().name()));
            if (duration != null) {
                player.sendMessage(String.format("  §e有效期: §f%d天", duration / 86400));
            } else {
                player.sendMessage("  §e有效期: §f永久");
            }
            if (reason != null) {
                player.sendMessage(String.format("  §e原因: §f%s", reason));
            }
            player.sendMessage(String.format("  §7禁运ID: §f%s", embargo.embargoId().toString().substring(0, 8)));
        } catch (Exception e) {
            player.sendMessage("§c禁运创建失败: " + e.getMessage());
        }

        return true;
    }

    private boolean handleEmbargoLift(Player player, Nation nation, String embargoIdStr) {
        try {
            UUID embargoId = UUID.fromString(embargoIdStr);
            var embargoOpt = tradeService.getEmbargo(embargoId);
            if (embargoOpt.isEmpty()) {
                player.sendMessage("§c未找到禁运: " + embargoIdStr);
                return true;
            }

            var embargo = embargoOpt.get();
            // 只能解除自己发起的禁运
            if (!embargo.initiatorNationId().equals(nation.id())) {
                player.sendMessage("§c你无权解除此禁运");
                return true;
            }

            if (tradeService.liftEmbargo(embargoId)) {
                player.sendMessage("§a禁运已解除！");
            } else {
                player.sendMessage("§c禁运解除失败");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的禁运ID格式");
        }
        return true;
    }

    private boolean handleEmbargoEdit(Player player, Nation nation, String[] args) {
        try {
            UUID embargoId = UUID.fromString(args[3]);
            var embargoOpt = tradeService.getEmbargo(embargoId);
            if (embargoOpt.isEmpty()) {
                player.sendMessage("§c未找到禁运: " + args[3]);
                return true;
            }

            var embargo = embargoOpt.get();
            // 只能编辑自己发起的禁运
            if (!embargo.initiatorNationId().equals(nation.id())) {
                player.sendMessage("§c你无权编辑此禁运");
                return true;
            }

            // 解析编辑命令
            for (int i = 4; i < args.length; i++) {
                String subCmd = args[i].toLowerCase();
                if (subCmd.equals("duration") && i + 1 < args.length) {
                    try {
                        long days = Long.parseLong(args[++i]);
                        player.sendMessage("§e注意: 当前禁运有效期不可动态修改，解除后重新创建");
                        player.sendMessage(String.format("  §7禁运ID: §f%s", embargoId.toString().substring(0, 8)));
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c无效的天数: " + args[i]);
                    }
                } else if (subCmd.equals("reason") && i + 1 < args.length) {
                    player.sendMessage("§e注意: 当前禁运原因不可动态修改，解除后重新创建");
                    player.sendMessage(String.format("  §7禁运ID: §f%s", embargoId.toString().substring(0, 8)));
                } else {
                    player.sendMessage("§c未知编辑选项: " + subCmd);
                    player.sendMessage("§e可用选项: duration <天数>, reason <原因>");
                    break;
                }
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的禁运ID格式");
        }
        return true;
    }

    private String getNationDisplayName(NationId nationId) {
        return nationService.nationById(nationId)
            .map(Nation::name)
            .orElse(nationId.toString().substring(0, 8));
    }

    private boolean handleFactory(Player player, String[] args) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你不在任何Nation中");
            return true;
        }

        Nation nation = nationOpt.get();
        String action = args[1].toLowerCase();

        switch (action) {
            case "list" -> {
                var factories = processingService.getFactories(nation.id());
                player.sendMessage("§6§l==== 工厂列表 ====");

                if (factories.isEmpty()) {
                    player.sendMessage("§7暂无工厂");
                    return true;
                }

                for (var factory : factories) {
                    String status = factory.isOperational() ? (factory.isProcessing() ? "§e加工中" : "§a运营中") : "§c停用";
                    player.sendMessage(String.format("  §e%s: §f等级%d %s",
                        factory.factoryName(),
                        factory.level(),
                        status
                    ));
                }
                return true;
            }
            case "recipes" -> {
                var recipes = processingService.getAllRecipes();
                player.sendMessage("§6§l==== 加工配方 ====");

                for (var recipe : recipes) {
                    String inputs = recipe.inputs().entrySet().stream()
                        .map(e -> e.getValue() + formatResourceName(e.getKey()))
                        .collect(Collectors.joining("+"));
                    String outputs = recipe.outputs().entrySet().stream()
                        .map(e -> e.getValue() + formatResourceName(e.getKey()))
                        .collect(Collectors.joining("+"));

                    player.sendMessage(String.format("  §e%s:", recipe.recipeName()));
                    player.sendMessage(String.format("    §7输入: §f%s", inputs));
                    player.sendMessage(String.format("    §7输出: §a%s", outputs));
                }
                return true;
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                return true;
            }
        }
    }

    private boolean handleOrder(Player player, String[] args) {
        String action = args[1].toLowerCase();
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty() && (action.equals("buy") || action.equals("sell"))) {
            player.sendMessage("§c你不在任何Nation中，无法交易");
            return true;
        }
        NationId nationId = nationOpt.map(Nation::id).orElse(null);

        switch (action) {
            case "list" -> {
                var orders = marketOrderBookService.getPlayerOrders(player.getUniqueId());
                player.sendMessage("§6§l==== 我的订单 ====");

                if (orders.isEmpty()) {
                    player.sendMessage("§7暂无订单");
                    return true;
                }

                for (var order : orders) {
                    String type = order.isBuyOrder() ? "§a买入" : "§c卖出";
                    String status = getOrderStatusText(order.status());
                    player.sendMessage(String.format("  %s §7%s §f%d @ %.2f %s",
                        type,
                        formatResourceName(order.resourceId()),
                        order.remainingAmount(),
                        order.pricePerUnit(),
                        status
                    ));
                }
                return true;
            }
            case "buy" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /resource order buy <资源> <数量> <价格>");
                    return true;
                }
                return handleOrderBuy(player, nationId, args);
            }
            case "sell" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /resource order sell <资源> <数量> <价格>");
                    return true;
                }
                return handleOrderSell(player, nationId, args);
            }
            case "cancel" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /resource order cancel <订单ID>");
                    return true;
                }
                return handleOrderCancel(player, args[2]);
            }
            case "book" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /resource order book <资源>");
                    return true;
                }
                return handleOrderBook(player, args[2]);
            }
            default -> {
                player.sendMessage("§c未知操作: " + action);
                player.sendMessage("§e可用操作: list, buy, sell, cancel, book");
                return true;
            }
        }
    }

    private boolean handleOrderBuy(Player player, NationId nationId, String[] args) {
        try {
            String resourceId = args[2].toLowerCase();
            long amount = Long.parseLong(args[3]);
            double price = Double.parseDouble(args[4]);

            var result = marketOrderBookService.submitBuyOrder(
                player.getUniqueId(), nationId, resourceId, amount, price,
                java.time.Instant.now().plus(java.time.Duration.ofHours(24))
            );

            if (result.isEmpty()) {
                player.sendMessage("§a买单已提交到订单簿");
                player.sendMessage(String.format("  §7资源: %s §7数量: %d §7价格: %.2f",
                    formatResourceName(resourceId), amount, price));
            } else {
                player.sendMessage("§c买单提交失败");
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的数字格式");
        }
        return true;
    }

    private boolean handleOrderSell(Player player, NationId nationId, String[] args) {
        try {
            String resourceId = args[2].toLowerCase();
            long amount = Long.parseLong(args[3]);
            double price = Double.parseDouble(args[4]);

            var result = marketOrderBookService.submitSellOrder(
                player.getUniqueId(), nationId, resourceId, amount, price,
                java.time.Instant.now().plus(java.time.Duration.ofHours(24))
            );

            if (result.isEmpty()) {
                player.sendMessage("§a卖单已提交到订单簿");
                player.sendMessage(String.format("  §7资源: %s §7数量: %d §7价格: %.2f",
                    formatResourceName(resourceId), amount, price));
            } else {
                player.sendMessage("§c卖单提交失败");
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的数字格式");
        }
        return true;
    }

    private boolean handleOrderCancel(Player player, String orderIdStr) {
        try {
            UUID orderId = UUID.fromString(orderIdStr);
            if (marketOrderBookService.cancelOrder(orderId)) {
                player.sendMessage("§a订单已取消");
            } else {
                player.sendMessage("§c订单取消失败（可能已成交或不存在）");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的订单ID格式");
        }
        return true;
    }

    private boolean handleOrderBook(Player player, String resourceId) {
        var snapshot = marketOrderBookService.getOrderBookSnapshot(resourceId);
        player.sendMessage("§6§l==== " + formatResourceName(resourceId) + " 订单簿 ====");
        player.sendMessage(String.format("  §7最佳买价: §a%.2f §7最佳卖价: §c%.2f",
            snapshot.bestBid(), snapshot.bestAsk()));
        player.sendMessage(String.format("  §7中间价: §f%.2f §7价差: §7%.2f",
            snapshot.midPrice(), snapshot.spread()));
        player.sendMessage(String.format("  §7买单: §a%d §7卖单: §c%d",
            snapshot.buyOrderCount(), snapshot.sellOrderCount()));
        player.sendMessage(String.format("  §7总买入量: §f%s §7总卖出量: §f%s",
            formatNumber(snapshot.totalBuyVolume()), formatNumber(snapshot.totalSellVolume())));
        return true;
    }

    private boolean handleHistory(Player player, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c无效的页码");
                return true;
            }
        }

        int limit = 10;
        int offset = (page - 1) * limit;

        var records = tradeHistoryService.getPlayerRecords(player.getUniqueId(), offset, limit);

        player.sendMessage("§6§l==== 交易历史 (第" + page + "页) ====");

        if (records.isEmpty()) {
            player.sendMessage("§7暂无交易记录");
            return true;
        }

        for (var record : records) {
            String type = record.orderType() == TradeOrder.OrderType.BUY ? "§a买入" : "§c卖出";
            String time = record.executedAt().toString().substring(0, 16);
            player.sendMessage(String.format("  %s §f%s §7x%d @ %.2f §7= %.2f",
                type,
                formatResourceName(record.resourceId()),
                record.amount(),
                record.pricePerUnit(),
                record.totalValue()
            ));
            player.sendMessage(String.format("    §7时间: §f%s §7税收: §c%.2f",
                time, record.taxAmount()));
        }

        // 分隔
        player.sendMessage("§7使用 /resource history " + (page + 1) + " 查看更多");
        return true;
    }

    private String getOrderStatusText(TradeOrder.OrderStatus status) {
        return switch (status) {
            case PENDING -> "§e待成交";
            case PARTIAL -> "§6部分成交";
            case FILLED -> "§a已完成";
            case CANCELLED -> "§7已取消";
            case EXPIRED -> "§c已过期";
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        String nationName = args[1];
        String resourceType = args[2];
        long amount;

        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c无效的数量: " + args[3]);
            return true;
        }

        Optional<Nation> nationOpt = nationService.nationByName(nationName);
        if (nationOpt.isEmpty()) {
            sender.sendMessage("§c找不到Nation: " + nationName);
            return true;
        }

        NationId nationId = nationOpt.get().id();

        if (resourceService.grant(nationId, resourceType, amount)) {
            sender.sendMessage("§a已给予 " + nationOpt.get().name() + " " + amount + " " + formatResourceName(resourceType));
        } else {
            sender.sendMessage("§c资源类型无效: " + resourceType);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l==== 资源命令帮助 ====");
        player.sendMessage("  §e/resource info §7- 查看资源总览");
        player.sendMessage("  §e/resource stockpile §7- 查看资源储量");
        player.sendMessage("  §e/resource market §7- 查看市场价格");
        player.sendMessage("  §e/resource reserve <add|release|info> §7- 管理战略储备");
        player.sendMessage("  §e/resource trade <routes|agreements> §7- 贸易管理");
        player.sendMessage("  §e/resource factory <list|recipes> §7- 工厂管理");
    }

    // ==================== 辅助方法 ====================

    private String getResourceIcon(String resourceId) {
        return switch (resourceId.toLowerCase()) {
            case "food" -> "🌾";
            case "timber" -> "🪵";
            case "ore" -> "⛏️";
            case "oil" -> "🛢️";
            case "rare_metal" -> "💎";
            default -> "📦";
        };
    }

    private String formatResourceName(String resourceId) {
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
        bar.append(progress >= 1.0 ? "§a完成" : String.format("§e%.0f%%", progress * 100));
        return bar.toString();
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "stockpile", "market", "reserve", "trade", "factory")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "reserve" -> {
                    return Arrays.asList("info", "add", "release")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "trade" -> {
                    return Arrays.asList("routes", "agreements", "embargo")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "factory" -> {
                    return Arrays.asList("list", "recipes")
                        .stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("reserve") &&
                (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("release"))) {
                return resourceService.availableResourceTypes().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
            // /resource trade agreements <action>
            if (args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("agreements")) {
                return Arrays.asList("list", "create", "edit", "cancel")
                    .stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
            // /resource trade embargo <action>
            if (args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("embargo")) {
                return Arrays.asList("list", "create", "lift", "edit")
                    .stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        // /resource trade agreements create <nation> <resource> <amount> <price>
        if (args.length == 4 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("agreements")
            && args[2].equalsIgnoreCase("create")) {
            // 返回所有国家名称
            return nationService.nations().stream()
                .map(Nation::name)
                .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                .collect(Collectors.toList());
        }

        // /resource trade agreements create <nation> <resource>
        if (args.length == 5 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("agreements")
            && args[2].equalsIgnoreCase("create")) {
            return resourceService.availableResourceTypes().stream()
                .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                .collect(Collectors.toList());
        }

        // /resource trade embargo create <nation> <resource>
        if (args.length == 5 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("embargo")
            && args[2].equalsIgnoreCase("create")) {
            return resourceService.availableResourceTypes().stream()
                .filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase()))
                .collect(Collectors.toList());
        }

        // /resource trade embargo create <nation>
        if (args.length == 4 && args[0].equalsIgnoreCase("trade") && args[1].equalsIgnoreCase("embargo")
            && args[2].equalsIgnoreCase("create")) {
            return nationService.nations().stream()
                .map(Nation::name)
                .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
