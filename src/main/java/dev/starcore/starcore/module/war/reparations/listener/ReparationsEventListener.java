package dev.starcore.starcore.module.war.reparations.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.war.reparations.ReparationsService;
import dev.starcore.starcore.module.war.reparations.event.*;
import dev.starcore.starcore.war.WarReparation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 战争赔款事件监听器
 * 处理赔款相关事件并发送通知
 */
public final class ReparationsEventListener implements Listener {

    private final ReparationsService reparationsService;
    private final NationService nationService;
    private final MessageService messages;

    public ReparationsEventListener(
        ReparationsService reparationsService,
        NationService nationService,
        MessageService messages
    ) {
        this.reparationsService = reparationsService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 处理赔款创建事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onReparationCreated(@NotNull ReparationCreatedEvent event) {
        WarReparation reparation = event.getReparation();

        String payerName = getNationName(reparation.payerId());
        String receiverName = getNationName(reparation.receiverId());

        // 广播消息给所有在线玩家
        String title = messages.format("reparations.event.created.title", payerName, receiverName);
        String subtitle = messages.format("reparations.event.created.subtitle",
            reparation.totalAmount().toPlainString(), reparation.totalInstallments());

        Component message = Component.text()
            .append(Component.text("[!] ", NamedTextColor.WHITE))
            .append(Component.text(title, NamedTextColor.GOLD))
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(Component.text(subtitle, NamedTextColor.GRAY))
            .build();

        broadcast(message);

        // 通知支付方成员
        notifyNationMembers(reparation.payerId(),
            messages.format("reparations.event.your-nation.created", receiverName, reparation.totalAmount().toPlainString()),
            NamedTextColor.RED);

        // 通知接收方成员
        notifyNationMembers(reparation.receiverId(),
            messages.format("reparations.event.enemy.created", payerName, reparation.totalAmount().toPlainString()),
            NamedTextColor.GREEN);
    }

    /**
     * 处理赔款支付事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onReparationPayment(@NotNull ReparationPaymentEvent event) {
        WarReparation reparation = event.getReparation();

        String payerName = getNationName(reparation.payerId());
        String receiverName = getNationName(reparation.receiverId());

        // 通知支付方成员
        notifyNationMembers(reparation.payerId(),
            messages.format("reparations.event.your-nation.paid",
                receiverName,
                event.getAmountPaid().toPlainString(),
                String.format("%.1f%%", reparation.progressPercentage())),
            NamedTextColor.YELLOW);

        // 通知接收方成员
        notifyNationMembers(reparation.receiverId(),
            messages.format("reparations.event.enemy.paid",
                payerName,
                event.getAmountPaid().toPlainString()),
            NamedTextColor.GREEN);
    }

    /**
     * 处理赔款完成事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onReparationCompleted(@NotNull ReparationCompletedEvent event) {
        WarReparation reparation = event.getReparation();

        String payerName = getNationName(reparation.payerId());
        String receiverName = getNationName(reparation.receiverId());

        // 广播完成消息
        String title = messages.format("reparations.event.completed.title", payerName, receiverName);
        String subtitle = messages.format("reparations.event.completed.subtitle", reparation.totalAmount().toPlainString());

        Component message = Component.text()
            .append(Component.text("[*] ", NamedTextColor.WHITE))
            .append(Component.text(title, NamedTextColor.GREEN))
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(Component.text(subtitle, NamedTextColor.GRAY))
            .build();

        broadcast(message);

        // 通知支付方成员
        notifyNationMembers(reparation.payerId(),
            messages.format("reparations.event.your-nation.completed", receiverName),
            NamedTextColor.GREEN);

        // 通知接收方成员
        notifyNationMembers(reparation.receiverId(),
            messages.format("reparations.event.enemy.completed", payerName, reparation.totalAmount().toPlainString()),
            NamedTextColor.GOLD);
    }

    /**
     * 处理赔款违约事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onReparationDefaulted(@NotNull ReparationDefaultedEvent event) {
        WarReparation reparation = event.getReparation();

        String payerName = getNationName(reparation.payerId());
        String receiverName = getNationName(reparation.receiverId());

        // 广播违约消息
        String title = messages.format("reparations.event.defaulted.title", payerName);
        String subtitle = messages.format("reparations.event.defaulted.subtitle",
            receiverName, reparation.remainingAmount().toPlainString());

        Component message = Component.text()
            .append(Component.text("[!] ", NamedTextColor.WHITE))
            .append(Component.text(title, NamedTextColor.DARK_RED))
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(Component.text(subtitle, NamedTextColor.RED))
            .build();

        broadcast(message);

        // 通知支付方成员
        notifyNationMembers(reparation.payerId(),
            messages.format("reparations.event.your-nation.defaulted", receiverName, reparation.remainingAmount().toPlainString()),
            NamedTextColor.DARK_RED);

        // 通知接收方成员
        notifyNationMembers(reparation.receiverId(),
            messages.format("reparations.event.enemy.defaulted", payerName),
            NamedTextColor.YELLOW);
    }

    /**
     * 处理赔款免除事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onReparationForgiven(@NotNull ReparationForgivenEvent event) {
        WarReparation reparation = event.getReparation();

        String payerName = getNationName(reparation.payerId());
        String receiverName = getNationName(reparation.receiverId());

        // 广播免除消息
        Component message = Component.text()
            .append(Component.text("[*] ", NamedTextColor.WHITE))
            .append(Component.text(payerName, NamedTextColor.GRAY))
            .append(Component.text(" 的战争赔款已被免除 ", NamedTextColor.GREEN))
            .append(Component.text("（原金额: " + reparation.totalAmount().toPlainString() + "）", NamedTextColor.GRAY))
            .build();

        broadcast(message);

        // 通知支付方成员
        notifyNationMembers(reparation.payerId(),
            messages.format("reparations.event.your-nation.forgiven", receiverName),
            NamedTextColor.GREEN);

        // 通知接收方成员
        notifyNationMembers(reparation.receiverId(),
            messages.format("reparations.event.enemy.forgiven", payerName),
            NamedTextColor.GRAY);
    }

    /**
     * 广播消息给所有在线玩家
     */
    private void broadcast(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /**
     * 通知国家所有在线成员
     */
    private void notifyNationMembers(UUID nationId, String message, NamedTextColor color) {
        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        for (NationMember member : nation.members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text(message, color));
            }
        }
    }

    /**
     * 获取国家名称
     */
    private String getNationName(UUID nationId) {
        return nationService.nationById(NationId.of(nationId))
            .map(Nation::name)
            .orElse("Unknown Nation");
    }
}