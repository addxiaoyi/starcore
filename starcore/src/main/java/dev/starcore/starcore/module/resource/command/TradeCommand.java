package dev.starcore.starcore.module.resource.command;

import java.util.concurrent.ConcurrentHashMap;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.PlayerTradeService;
import dev.starcore.starcore.module.resource.TradeTaxService;
import dev.starcore.starcore.module.resource.gui.PlayerTradeGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 玩家交易命令处理器
 * /trade <player> <资源> <数量> <价格> - 向玩家发起交易请求
 * /trade accept [offerId] - 接受交易请求
 * /trade reject [offerId] - 拒绝交易请求
 * /trade cancel [offerId] - 取消发出的交易请求
 * /trade list - 查看交易请求列表
 * /trade gui - 打开交易GUI
 * /trade help - 显示帮助
 */
public class TradeCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_USE = "starcore.trade.use";
    private static final String PERMISSION_ADMIN = "starcore.trade.admin";

    private PlayerTradeService playerTradeService;
    private NationService nationService;
    private EconomyService economyService;
    private TradeTaxService tradeTaxService;
    private JavaPlugin plugin;

    // 临时存储，用于 GUI 创建交易时的上下文
    private final Map<UUID, TradeContext> pendingTradeContext = new ConcurrentHashMap<>();

    public TradeCommand() {
    }

    public void setPlayerTradeService(PlayerTradeService service) {
        this.playerTradeService = service;
    }

    public void setNationService(NationService service) {
        this.nationService = service;
    }

    public void setEconomyService(EconomyService service) {
        this.economyService = service;
    }

    public void setTradeTaxService(TradeTaxService service) {
        this.tradeTaxService = service;
    }

    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家使用。");
            return true;
        }

        if (!player.hasPermission(PERMISSION_USE)) {
            player.sendMessage("§c你没有权限使用交易命令。");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "offer", "send", "create" -> handleOffer(player, args);
                case "accept", "yes" -> handleAccept(player, args);
                case "reject", "no", "decline" -> handleReject(player, args);
                case "cancel" -> handleCancel(player, args);
                case "list", "pending" -> handleList(player);
                case "received", "inbox" -> handleReceived(player);
                case "sent", "outbox" -> handleSent(player);
                case "gui" -> handleGui(player);
                case "help", "?" -> showHelp(player);
                default -> {
                    player.sendMessage("§c未知命令。使用 /trade help 查看帮助。");
                }
            }
        } catch (Exception e) {
            player.sendMessage("§c执行命令时出错: " + e.getMessage());
            player.sendMessage("§c详细信息: " + e.getClass().getSimpleName());
        }

        return true;
    }

    /**
     * 发起交易请求
     * /trade offer <玩家> <资源> <数量> <单价> [有效期(秒)]
     */
    private void handleOffer(Player player, String[] args) {
        if (playerTradeService == null) {
            player.sendMessage("§c交易服务暂不可用。");
            return;
        }

        if (args.length < 5) {
            player.sendMessage("§c用法: /trade offer <玩家> <资源> <数量> <单价> [有效期(秒)]");
            player.sendMessage("§7示例: /trade offer Steve iron 100 5.5 300");
            return;
        }

        // 解析参数
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§c玩家不在线: " + args[1]);
            return;
        }

        if (target.equals(player)) {
            player.sendMessage("§c不能与自己交易。");
            return;
        }

        String resourceId = args[2].toLowerCase();
        long amount;
        double pricePerUnit;

        try {
            amount = Long.parseLong(args[3]);
            pricePerUnit = Double.parseDouble(args[4]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的数字格式。");
            return;
        }

        if (amount <= 0 || pricePerUnit <= 0) {
            player.sendMessage("§c数量和价格必须大于0。");
            return;
        }

        long expirySeconds = args.length > 5 ? Long.parseLong(args[5]) : 300; // 默认5分钟

        // 获取国家ID
        NationId initiatorNationId = getPlayerNationId(player);
        NationId targetNationId = getPlayerNationId(target);

        // 创建交易请求
        Optional<PlayerTradeService.PlayerTradeOffer> offerOpt = playerTradeService.createTradeOffer(
            player.getUniqueId(),
            initiatorNationId,
            target.getUniqueId(),
            targetNationId,
            resourceId,
            amount,
            pricePerUnit,
            expirySeconds
        );

        if (offerOpt.isPresent()) {
            PlayerTradeService.PlayerTradeOffer offer = offerOpt.get();
            player.sendMessage("§a交易请求已发送给 " + target.getName());
            player.sendMessage("§7资源: " + resourceId + " x" + amount);
            player.sendMessage("§7总价: " + String.format("%.2f", offer.totalValue()));
            player.sendMessage("§7交易ID: " + offer.offerId().toString().substring(0, 8));

            // 通知目标玩家
            target.sendMessage("§6===========================================");
            target.sendMessage("§e你收到了来自 " + player.getName() + " 的交易请求！");
            target.sendMessage("§7资源: " + resourceId + " x" + amount);
            target.sendMessage("§7单价: " + String.format("%.2f", pricePerUnit));
            target.sendMessage("§7总价: " + String.format("%.2f", offer.totalValue()));
            target.sendMessage("§7有效期: " + (expirySeconds / 60) + " 分钟");
            // 分隔
            target.sendMessage("§a/trade accept " + offer.offerId().toString().substring(0, 8) + " §7- 接受");
            target.sendMessage("§c/trade reject " + offer.offerId().toString().substring(0, 8) + " §7- 拒绝");
            target.sendMessage("§6===========================================");
        } else {
            player.sendMessage("§c创建交易请求失败。可能是:");
            player.sendMessage("§7- 余额不足");
            player.sendMessage("§7- 目标玩家资源不足");
            player.sendMessage("§7- 资源类型无效");
        }
    }

    /**
     * 接受交易请求
     * /trade accept [offerId]
     */
    private void handleAccept(Player player, String[] args) {
        if (playerTradeService == null) {
            player.sendMessage("§c交易服务暂不可用。");
            return;
        }

        UUID resolvedOfferId = null;
        if (args.length > 1) {
            try {
                // 尝试解析部分UUID
                String partialId = args[1];
                resolvedOfferId = findOfferIdByPartial(player.getUniqueId(), partialId);
            } catch (Exception e) {
                player.sendMessage("§c无效的交易ID: " + args[1]);
                return;
            }
        }

        if (resolvedOfferId == null) {
            player.sendMessage("§c请指定要接受的交易ID。");
            player.sendMessage("§7使用 /trade received 查看收到的请求");
            return;
        }

        // 保存为 final 变量以供 lambda 使用
        final UUID finalResolvedOfferId = resolvedOfferId;

        // 查找并接受交易
        List<PlayerTradeService.PlayerTradeOffer> received = playerTradeService.getReceivedOffers(player.getUniqueId());
        PlayerTradeService.PlayerTradeOffer offer = received.stream()
            .filter(o -> o.offerId().equals(finalResolvedOfferId))
            .filter(PlayerTradeService.PlayerTradeOffer::isPending)
            .findFirst()
            .orElse(null);

        if (offer == null) {
            player.sendMessage("§c找不到待处理的交易请求: " + resolvedOfferId.toString().substring(0, 8));
            return;
        }

        Optional<?> recordOpt = playerTradeService.acceptTrade(resolvedOfferId);
        if (recordOpt.isPresent()) {
            player.sendMessage("§a交易成功完成！");

            // 通知发起者
            Player initiator = Bukkit.getPlayer(offer.initiatorId());
            if (initiator != null) {
                initiator.sendMessage("§e你的交易请求已被 " + player.getName() + " 接受！");
            }
        } else {
            player.sendMessage("§c交易执行失败。可能是资源不足。");
        }
    }

    /**
     * 拒绝交易请求
     * /trade reject [offerId]
     */
    private void handleReject(Player player, String[] args) {
        if (playerTradeService == null) {
            player.sendMessage("§c交易服务暂不可用。");
            return;
        }

        UUID resolvedOfferId = null;
        if (args.length > 1) {
            try {
                String partialId = args[1];
                resolvedOfferId = findOfferIdByPartial(player.getUniqueId(), partialId);
            } catch (Exception e) {
                player.sendMessage("§c无效的交易ID: " + args[1]);
                return;
            }
        }

        if (resolvedOfferId == null) {
            player.sendMessage("§c请指定要拒绝的交易ID。");
            return;
        }

        // 保存为 final 变量以供 lambda 使用
        final UUID finalResolvedOfferId = resolvedOfferId;

        List<PlayerTradeService.PlayerTradeOffer> received = playerTradeService.getReceivedOffers(player.getUniqueId());
        PlayerTradeService.PlayerTradeOffer offer = received.stream()
            .filter(o -> o.offerId().equals(finalResolvedOfferId))
            .filter(PlayerTradeService.PlayerTradeOffer::isPending)
            .findFirst()
            .orElse(null);

        if (offer == null) {
            player.sendMessage("§c找不到待处理的交易请求。");
            return;
        }

        if (playerTradeService.rejectTrade(resolvedOfferId)) {
            player.sendMessage("§e已拒绝交易请求。");

            Player initiator = Bukkit.getPlayer(offer.initiatorId());
            if (initiator != null) {
                initiator.sendMessage("§e你的交易请求被 " + player.getName() + " 拒绝了。");
            }
        }
    }

    /**
     * 取消发出的交易请求
     * /trade cancel [offerId]
     */
    private void handleCancel(Player player, String[] args) {
        if (playerTradeService == null) {
            player.sendMessage("§c交易服务暂不可用。");
            return;
        }

        UUID offerId = null;
        if (args.length > 1) {
            try {
                String partialId = args[1];
                offerId = findSentOfferIdByPartial(player.getUniqueId(), partialId);
            } catch (Exception e) {
                player.sendMessage("§c无效的交易ID: " + args[1]);
                return;
            }
        }

        if (offerId == null) {
            player.sendMessage("§c请指定要取消的交易ID。使用 /trade sent 查看发出的请求。");
            return;
        }

        if (playerTradeService.cancelOffer(offerId)) {
            player.sendMessage("§a交易请求已取消。");
        } else {
            player.sendMessage("§c取消交易请求失败。可能已被处理或不存在。");
        }
    }

    /**
     * 列出所有待处理的交易请求
     */
    private void handleList(Player player) {
        handleReceived(player);
    }

    /**
     * 查看收到的交易请求
     */
    private void handleReceived(Player player) {
        if (playerTradeService == null) {
            player.sendMessage("§c交易服务暂不可用。");
            return;
        }

        List<PlayerTradeService.PlayerTradeOffer> received = playerTradeService.getReceivedOffers(player.getUniqueId())
            .stream()
            .filter(PlayerTradeService.PlayerTradeOffer::isPending)
            .toList();

        if (received.isEmpty()) {
            player.sendMessage("§7你没有收到的交易请求。");
            return;
        }

        player.sendMessage("§6===========================================");
        player.sendMessage("§e收到的交易请求 (" + received.size() + "):");
        // 分隔

        for (PlayerTradeService.PlayerTradeOffer offer : received) {
            Player initiator = Bukkit.getPlayer(offer.initiatorId());
            String initiatorName = initiator != null ? initiator.getName() : offer.initiatorId().toString().substring(0, 8);

            long remainingSeconds = offer.expiresAt().getEpochSecond() - System.currentTimeMillis() / 1000;
            String timeStr = remainingSeconds > 60 ? (remainingSeconds / 60) + "分钟" : remainingSeconds + "秒";

            player.sendMessage("§a[ID: " + offer.offerId().toString().substring(0, 8) + "] §f" + initiatorName);
            player.sendMessage("§7  资源: " + offer.resourceId() + " x" + offer.amount());
            player.sendMessage("§7  总价: " + String.format("%.2f", offer.totalValue()) + " (含税)");
            player.sendMessage("§7  剩余: " + timeStr);
            // 分隔
        }

        player.sendMessage("§e使用 /trade accept <ID> 接受");
        player.sendMessage("§c使用 /trade reject <ID> 拒绝");
        player.sendMessage("§6===========================================");
    }

    /**
     * 查看发出的交易请求
     */
    private void handleSent(Player player) {
        if (playerTradeService == null) {
            player.sendMessage("§c交易服务暂不可用。");
            return;
        }

        List<PlayerTradeService.PlayerTradeOffer> sent = playerTradeService.getSentOffers(player.getUniqueId())
            .stream()
            .filter(PlayerTradeService.PlayerTradeOffer::isPending)
            .toList();

        if (sent.isEmpty()) {
            player.sendMessage("§7你没有发出的交易请求。");
            return;
        }

        player.sendMessage("§6===========================================");
        player.sendMessage("§9发出的交易请求 (" + sent.size() + "):");
        // 分隔

        for (PlayerTradeService.PlayerTradeOffer offer : sent) {
            Player target = Bukkit.getPlayer(offer.targetId());
            String targetName = target != null ? target.getName() : offer.targetId().toString().substring(0, 8);

            long remainingSeconds = offer.expiresAt().getEpochSecond() - System.currentTimeMillis() / 1000;
            String timeStr = remainingSeconds > 60 ? (remainingSeconds / 60) + "分钟" : remainingSeconds + "秒";

            player.sendMessage("§a[ID: " + offer.offerId().toString().substring(0, 8) + "] §f-> " + targetName);
            player.sendMessage("§7  资源: " + offer.resourceId() + " x" + offer.amount());
            player.sendMessage("§7  总价: " + String.format("%.2f", offer.totalValue()) + " (含税)");
            player.sendMessage("§7  剩余: " + timeStr);
            // 分隔
        }

        player.sendMessage("§c使用 /trade cancel <ID> 取消");
        player.sendMessage("§6===========================================");
    }

    /**
     * 打开交易GUI
     */
    private void handleGui(Player player) {
        if (playerTradeService == null) {
            player.sendMessage("§c交易服务暂不可用。");
            return;
        }

        PlayerTradeGui gui = new PlayerTradeGui(
            player,
            plugin,
            playerTradeService,
            nationService,
            this::onTradeAccepted,
            this::onTradeRejected
        );
        gui.open();
    }

    /**
     * 交易接受回调 - GUI中使用，通知交易发起者交易已被接受
     */
    private void onTradeAccepted(UUID offerId) {
        if (playerTradeService == null) {
            return;
        }

        // 使用 getOffer 获取交易详情
        PlayerTradeService.PlayerTradeOffer offer = playerTradeService.getOffer(offerId).orElse(null);

        if (offer == null) {
            return;
        }

        // 通知交易发起者
        Player initiator = Bukkit.getPlayer(offer.initiatorId());
        if (initiator != null) {
            Player target = Bukkit.getPlayer(offer.targetId());
            String targetName = target != null ? target.getName() : offer.targetId().toString().substring(0, 8);

            initiator.sendMessage("§a===========================================");
            initiator.sendMessage("§a[交易成功] 你的资源已售出！");
            initiator.sendMessage("§7买家: " + targetName);
            initiator.sendMessage("§7资源: " + offer.resourceId() + " x" + offer.amount());
            initiator.sendMessage("§7收入: " + String.format("%.2f", offer.totalValue()));
            initiator.sendMessage("§a===========================================");
        }
    }

    /**
     * 交易拒绝回调 - GUI中使用，通知交易发起者交易已被拒绝
     */
    private void onTradeRejected(UUID offerId) {
        if (playerTradeService == null) {
            return;
        }

        // 使用 getOffer 获取交易详情
        PlayerTradeService.PlayerTradeOffer offer = playerTradeService.getOffer(offerId).orElse(null);

        if (offer == null) {
            return;
        }

        // 通知交易发起者
        Player initiator = Bukkit.getPlayer(offer.initiatorId());
        if (initiator != null) {
            Player target = Bukkit.getPlayer(offer.targetId());
            String targetName = target != null ? target.getName() : offer.targetId().toString().substring(0, 8);

            initiator.sendMessage("§6===========================================");
            initiator.sendMessage("§e交易请求被拒绝。");
            initiator.sendMessage("§7买家: " + targetName);
            initiator.sendMessage("§7资源: " + offer.resourceId() + " x" + offer.amount());
            initiator.sendMessage("§7总价: " + String.format("%.2f", offer.totalValue()));
            initiator.sendMessage("§6===========================================");
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(Player player) {
        player.sendMessage("§6========== 玩家交易系统 ==========");
        // 分隔
        player.sendMessage("§e交易命令:");
        player.sendMessage("§a/trade offer <玩家> <资源> <数量> <单价> [有效期]");
        player.sendMessage("§7  向玩家发起交易请求");
        // 分隔
        player.sendMessage("§a/trade accept <ID> §7- 接受交易请求");
        player.sendMessage("§a/trade reject <ID> §7- 拒绝交易请求");
        player.sendMessage("§a/trade cancel <ID> §7- 取消发出的请求");
        // 分隔
        player.sendMessage("§a/trade received §7- 查看收到的请求");
        player.sendMessage("§a/trade sent §7- 查看发出的请求");
        player.sendMessage("§a/trade gui §7- 打开交易界面");
        // 分隔
        player.sendMessage("§e快捷命令:");
        player.sendMessage("§7/trade accept/reject/cancel [ID] 可简写为 /ta /tr /tc");
        player.sendMessage("§6====================================");
    }

    /**
     * 获取玩家的国家ID
     */
    private NationId getPlayerNationId(Player player) {
        if (nationService == null) {
            return null;
        }
        return nationService.getNationByMember(player.getUniqueId())
            .map(nation -> nation.id())
            .orElse(null);
    }

    /**
     * 根据部分ID查找收到的交易请求
     * @param playerId 玩家UUID
     * @param partialId 部分ID（前8位）
     * @return 匹配的交易请求UUID，若未找到则返回null
     */
    @Nullable
    private UUID findOfferIdByPartial(UUID playerId, String partialId) {
        List<PlayerTradeService.PlayerTradeOffer> received = playerTradeService.getReceivedOffers(playerId);
        String lowerPartial = partialId.toLowerCase();

        for (PlayerTradeService.PlayerTradeOffer offer : received) {
            if (offer.offerId().toString().toLowerCase().startsWith(lowerPartial)) {
                return offer.offerId();
            }
        }

        // 调试信息：当未找到时记录详细原因
        if (plugin != null && plugin.getConfig().getBoolean("debug.trade", false)) {
            plugin.getLogger().info("[TradeDebug] findOfferIdByPartial 未找到匹配项:");
            plugin.getLogger().info("  玩家: " + playerId);
            plugin.getLogger().info("  搜索的部分ID: " + partialId);
            plugin.getLogger().info("  收到的交易请求数量: " + received.size());
            for (PlayerTradeService.PlayerTradeOffer offer : received) {
                String offerIdStr = offer.offerId().toString();
                plugin.getLogger().info("    - " + offerIdStr.substring(0, 8)
                    + " (状态: " + (offer.isPending() ? "待处理" : "已处理") + ")");
            }
        }
        return null;
    }

    /**
     * 根据部分ID查找发出的交易请求
     * @param playerId 玩家UUID
     * @param partialId 部分ID（前8位）
     * @return 匹配的交易请求UUID，若未找到则返回null
     */
    @Nullable
    private UUID findSentOfferIdByPartial(UUID playerId, String partialId) {
        List<PlayerTradeService.PlayerTradeOffer> sent = playerTradeService.getSentOffers(playerId);
        String lowerPartial = partialId.toLowerCase();

        for (PlayerTradeService.PlayerTradeOffer offer : sent) {
            if (offer.offerId().toString().toLowerCase().startsWith(lowerPartial)) {
                return offer.offerId();
            }
        }

        // 调试信息：当未找到时记录详细原因
        if (plugin != null && plugin.getConfig().getBoolean("debug.trade", false)) {
            plugin.getLogger().info("[TradeDebug] findSentOfferIdByPartial 未找到匹配项:");
            plugin.getLogger().info("  玩家: " + playerId);
            plugin.getLogger().info("  搜索的部分ID: " + partialId);
            plugin.getLogger().info("  发出的交易请求数量: " + sent.size());
            for (PlayerTradeService.PlayerTradeOffer offer : sent) {
                String offerIdStr = offer.offerId().toString();
                plugin.getLogger().info("    - " + offerIdStr.substring(0, 8)
                    + " (状态: " + (offer.isPending() ? "待处理" : "已处理") + ")");
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterArgs(args[0], "offer", "accept", "reject", "cancel", "received", "sent", "gui", "help");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("offer")) {
                // 玩家名补全
                return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("accept") || subCommand.equals("reject") || subCommand.equals("cancel")) {
                // 交易ID补全
                List<PlayerTradeService.PlayerTradeOffer> offers = subCommand.equals("cancel")
                    ? playerTradeService.getSentOffers(player.getUniqueId())
                    : playerTradeService.getReceivedOffers(player.getUniqueId());

                return offers.stream()
                    .filter(PlayerTradeService.PlayerTradeOffer::isPending)
                    .map(o -> o.offerId().toString().substring(0, 8))
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("offer")) {
            // 资源类型补全
            return filterArgs(args[2], "iron", "gold", "coal", "diamond", "emerald", "stone", "wood", "food", "energy");
        } else if (args.length == 3) {
            // 其他子命令的第三参数启用默认Tab补全
            return null;
        }

        return Collections.emptyList();
    }

    private List<String> filterArgs(String input, String... options) {
        String lower = input.toLowerCase();
        return Arrays.stream(options)
            .filter(opt -> opt.toLowerCase().startsWith(lower))
            .collect(Collectors.toList());
    }

    /**
     * 交易上下文（用于GUI）
     */
    public record TradeContext(
        UUID initiatorId,
        UUID targetId,
        String resourceId,
        long amount,
        double pricePerUnit,
        long expirySeconds
    ) {}
}
