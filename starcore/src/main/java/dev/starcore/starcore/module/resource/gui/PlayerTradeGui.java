package dev.starcore.starcore.module.resource.gui;

import dev.starcore.starcore.foundation.gui.BaseGui;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.resource.PlayerTradeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 玩家交易 GUI - 基于 BaseGui
 * 提供可视化的玩家间交易界面
 */
public class PlayerTradeGui extends BaseGui {

    // 菜单ID
    private static final String MENU_ID = "player_trade";

    // 标题
    private static final Component TITLE_MAIN = Component.text("§6§l玩家交易");
    private static final Component TITLE_RECEIVED = Component.text("§c§l收到的请求");
    private static final Component TITLE_SENT = Component.text("§9§l发出的请求");
    private static final Component TITLE_CREATE = Component.text("§a§l创建交易");

    // 菜单状态
    private enum TradeState {
        MAIN,
        RECEIVED,
        SENT,
        CREATE
    }

    private final PlayerTradeService tradeService;
    private final NationService nationService;
    private final Consumer<UUID> onAccept;
    private final Consumer<UUID> onReject;

    private TradeState state = TradeState.MAIN;
    private UUID selectedOfferId;
    private List<PlayerTradeService.PlayerTradeOffer> displayedOffers = new ArrayList<>();

    public PlayerTradeGui(Player player, Plugin plugin,
                          PlayerTradeService tradeService,
                          NationService nationService,
                          Consumer<UUID> onAccept,
                          Consumer<UUID> onReject) {
        super(player, plugin, MENU_ID, TITLE_MAIN);
        this.tradeService = tradeService;
        this.nationService = nationService;
        this.onAccept = onAccept;
        this.onReject = onReject;
    }

    // ==================== BaseGui 实现 ====================

    @Override
    protected int getSize() {
        return switch (state) {
            case MAIN, CREATE -> 36;
            case RECEIVED, SENT -> 54;
        };
    }

    @Override
    protected void buildContent() {
        displayedOffers.clear();

        switch (state) {
            case MAIN -> buildMainMenu();
            case RECEIVED -> buildReceivedMenu();
            case SENT -> buildSentMenu();
            case CREATE -> buildCreateMenu();
        }
    }

    // ==================== 菜单构建 ====================

    private void buildMainMenu() {
        // 更新标题
        title = TITLE_MAIN;

        // 获取统计数据
        List<PlayerTradeService.PlayerTradeOffer> received = getPendingReceived();
        List<PlayerTradeService.PlayerTradeOffer> sent = getPendingSent();

        // 收到的请求
        setClickableButton(11, Material.REDSTONE_BLOCK,
            Component.text("§c收到的交易请求"),
            Arrays.asList(
                Component.text("§7待处理请求: §f" + received.size()),
                Component.text(" "),
                Component.text("§a点击查看")
            ),
            p -> {
                state = TradeState.RECEIVED;
                refresh();
            });

        // 发出的请求
        setClickableButton(15, Material.LAPIS_BLOCK,
            Component.text("§9发出的交易请求"),
            Arrays.asList(
                Component.text("§7待处理请求: §f" + sent.size()),
                Component.text(" "),
                Component.text("§a点击查看")
            ),
            p -> {
                state = TradeState.SENT;
                refresh();
            });

        // 创建新交易
        setClickableButton(13, Material.EMERALD,
            Component.text("§a发起新交易"),
            Arrays.asList(
                Component.text("§7向其他玩家发起交易请求"),
                Component.text(" "),
                Component.text("§a点击查看命令")
            ),
            p -> {
                state = TradeState.CREATE;
                refresh();
            });

        // 帮助信息
        setButton(22, Material.BOOK,
            Component.text("§e使用说明"),
            Arrays.asList(
                Component.text("§7交易流程:"),
                Component.text("§71. 发起交易请求"),
                Component.text("§72. 对方接受后自动执行"),
                Component.text("§73. 资源和金币自动转移"),
                Component.text(" "),
                Component.text("§7命令: §f/trade offer <玩家> <资源> <数量> <单价>")
            ));
    }

    private void buildReceivedMenu() {
        title = TITLE_RECEIVED;
        setTotalPages(1);

        List<PlayerTradeService.PlayerTradeOffer> received = getPendingReceived();

        if (received.isEmpty()) {
            setButton(22, Material.BARRIER,
                Component.text("§c暂无收到的请求"),
                Arrays.asList(Component.text("§7你没有收到的交易请求")));
            return;
        }

        displayedOffers = received;

        // 填充请求列表
        int slot = 10;
        int count = 0;
        for (PlayerTradeService.PlayerTradeOffer offer : received) {
            if (slot > 43 || count >= 35) break;

            Player initiator = Bukkit.getPlayer(offer.initiatorId());
            String initiatorName = initiator != null ? initiator.getName()
                : "§7" + offer.initiatorId().toString().substring(0, 8);

            long remainingSeconds = Math.max(0,
                offer.expiresAt().getEpochSecond() - System.currentTimeMillis() / 1000);
            String timeStr = remainingSeconds > 60
                ? (remainingSeconds / 60) + "分钟"
                : remainingSeconds + "秒";

            setClickableButton(slot, Material.PAPER,
                Component.text("§e" + initiatorName + " §f的请求"),
                Arrays.asList(
                    Component.text("§7资源: §f" + offer.resourceId() + " x" + offer.amount()),
                    Component.text("§7单价: §6" + formatNumber(offer.pricePerUnit())),
                    Component.text("§7总价: §e" + formatNumber(offer.totalValue())),
                    Component.text("§7剩余时间: §f" + timeStr),
                    Component.text(" "),
                    Component.text("§a点击接受 §c右键拒绝")
                ),
                p -> acceptTrade(offer),
                p -> offer.isPending());

            count++;
            slot++;
            if (slot % 9 == 8) slot += 2; // 跳过边框
        }
    }

    private void buildSentMenu() {
        title = TITLE_SENT;
        setTotalPages(1);

        List<PlayerTradeService.PlayerTradeOffer> sent = getPendingSent();

        if (sent.isEmpty()) {
            setButton(22, Material.BARRIER,
                Component.text("§c暂无发出的请求"),
                Arrays.asList(Component.text("§7你没有发出的交易请求")));
            return;
        }

        displayedOffers = sent;

        // 填充请求列表
        int slot = 10;
        int count = 0;
        for (PlayerTradeService.PlayerTradeOffer offer : sent) {
            if (slot > 43 || count >= 35) break;

            Player target = Bukkit.getPlayer(offer.targetId());
            String targetName = target != null ? target.getName()
                : "§7" + offer.targetId().toString().substring(0, 8);

            long remainingSeconds = Math.max(0,
                offer.expiresAt().getEpochSecond() - System.currentTimeMillis() / 1000);
            String timeStr = remainingSeconds > 60
                ? (remainingSeconds / 60) + "分钟"
                : remainingSeconds + "秒";

            setClickableButton(slot, Material.PAPER,
                Component.text("§9发给 §f" + targetName + " §9的请求"),
                Arrays.asList(
                    Component.text("§7资源: §f" + offer.resourceId() + " x" + offer.amount()),
                    Component.text("§7单价: §6" + formatNumber(offer.pricePerUnit())),
                    Component.text("§7总价: §e" + formatNumber(offer.totalValue())),
                    Component.text("§7剩余时间: §f" + timeStr),
                    Component.text(" "),
                    Component.text("§c点击取消请求")
                ),
                p -> cancelTrade(offer),
                p -> offer.isPending());

            count++;
            slot++;
            if (slot % 9 == 8) slot += 2;
        }
    }

    private void buildCreateMenu() {
        title = TITLE_CREATE;

        // 说明
        setButton(13, Material.BOOK,
            Component.text("§e创建交易请求"),
            Arrays.asList(
                Component.text("§7请使用命令发起交易:"),
                Component.text(" "),
                Component.text("§a/trade offer <玩家> <资源> <数量> <单价>"),
                Component.text(" "),
                Component.text("§7示例:"),
                Component.text("§f/trade offer Steve iron 100 5.5"),
                Component.text(" "),
                Component.text("§7这将向 Steve 发起一个交易请求，"),
                Component.text("§7购买 100 单位 iron，单价 5.5 金币")
            ));

        // 快速交易
        setButton(11, Material.COMPASS,
            Component.text("§e快速交易选项"),
            Arrays.asList(
                Component.text("§7/buy <资源> <数量> §f- 快速买入"),
                Component.text("§7/sell <资源> <数量> §f- 快速卖出"),
                Component.text(" "),
                Component.text("§7快速交易直接以市场价格执行")
            ));

        // 资源列表
        setButton(15, Material.CHEST,
            Component.text("§e可用资源类型"),
            Arrays.asList(
                Component.text("§7iron, gold, coal, diamond,"),
                Component.text("§7emerald, stone, wood, food,"),
                Component.text("§7energy, luxury, industrial,"),
                Component.text("§7chemical, strategic"),
                Component.text(" "),
                Component.text("§7使用 /trade offer 时指定资源名称")
            ));

        // 返回按钮
        setClickableButton(31, Material.ARROW,
            Component.text("§c返回主菜单"),
            Arrays.asList(Component.text("§7返回交易主界面")),
            p -> {
                state = TradeState.MAIN;
                refresh();
            });
    }

    // ==================== 交易操作 ====================

    private void acceptTrade(PlayerTradeService.PlayerTradeOffer offer) {
        Optional<?> result = tradeService.acceptTrade(offer.offerId());
        if (result.isPresent()) {
            playSuccess("交易成功完成！");

            Player initiator = Bukkit.getPlayer(offer.initiatorId());
            if (initiator != null) {
                initiator.sendMessage(Component.text("§e你的交易请求已被 " + player.getName() + " 接受！")
                    .color(NamedTextColor.YELLOW));
            }

            if (onAccept != null) {
                onAccept.accept(offer.offerId());
            }

            close();
        } else {
            playFailure("交易执行失败，可能是资源不足");
        }
    }

    private void rejectTrade(PlayerTradeService.PlayerTradeOffer offer) {
        if (tradeService.rejectTrade(offer.offerId())) {
            playSuccess("已拒绝交易请求");

            Player initiator = Bukkit.getPlayer(offer.initiatorId());
            if (initiator != null) {
                initiator.sendMessage(Component.text("§e你的交易请求被 " + player.getName() + " 拒绝了。")
                    .color(NamedTextColor.RED));
            }

            if (onReject != null) {
                onReject.accept(offer.offerId());
            }

            refresh();
        }
    }

    private void cancelTrade(PlayerTradeService.PlayerTradeOffer offer) {
        if (tradeService.cancelOffer(offer.offerId())) {
            playSuccess("交易请求已取消");

            Player target = Bukkit.getPlayer(offer.targetId());
            if (target != null) {
                target.sendMessage(Component.text("§e" + player.getName() + " 取消了向你发起的交易请求。")
                    .color(NamedTextColor.YELLOW));
            }

            refresh();
        }
    }

    // ==================== 导航覆盖 ====================

    @Override
    protected void buildNavigation() {
        // 自定义导航按钮
        setClickableButton(49, Material.ARROW,
            Component.text("§c返回"),
            Arrays.asList(Component.text("§7返回主菜单")),
            p -> {
                state = TradeState.MAIN;
                refresh();
            });
    }

    // ==================== 工具方法 ====================

    private List<PlayerTradeService.PlayerTradeOffer> getPendingReceived() {
        return tradeService.getReceivedOffers(player.getUniqueId())
            .stream()
            .filter(PlayerTradeService.PlayerTradeOffer::isPending)
            .collect(Collectors.toList());
    }

    private List<PlayerTradeService.PlayerTradeOffer> getPendingSent() {
        return tradeService.getSentOffers(player.getUniqueId())
            .stream()
            .filter(PlayerTradeService.PlayerTradeOffer::isPending)
            .collect(Collectors.toList());
    }

    private String formatTime(Instant instant) {
        if (instant == null) return "N/A";
        LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        return ldt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
