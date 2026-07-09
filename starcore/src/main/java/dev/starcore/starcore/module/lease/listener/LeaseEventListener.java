package dev.starcore.starcore.module.lease.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.lease.LeaseService;
import dev.starcore.starcore.module.lease.event.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * 租约事件监听器
 * 处理租约相关事件的通知和业务逻辑
 */
public final class LeaseEventListener implements Listener {

    private final LeaseService leaseService;
    private final NationService nationService;
    private final MessageService messages;
    private final Logger logger;

    public LeaseEventListener(LeaseService leaseService, NationService nationService, MessageService messages) {
        this.leaseService = leaseService;
        this.nationService = nationService;
        this.messages = messages;
        this.logger = Bukkit.getLogger();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeaseCreated(LeaseCreatedEvent event) {
        logger.info("[Lease] New lease contract created: " + event.getContract().id());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeaseSigned(LeaseSignedEvent event) {
        String party = event.isLessor() ? "出租方" : "承租方";
        Player signer = Bukkit.getPlayer(event.getSignerId());
        if (signer != null) {
            signer.sendMessage(net.kyori.adventure.text.Component.text(
                "您已签署租约 " + event.getContract().id().toString().substring(0, 8),
                net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
        }
        logger.info("[Lease] Lease " + event.getContract().id() + " signed by " + party);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeaseActivated(LeaseActivatedEvent event) {
        var contract = event.getContract();

        // 通知出租方
        Player lessor = Bukkit.getPlayer(contract.lessorPlayerId());
        if (lessor != null) {
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "[租约] 您的租约已生效！",
                net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "类型: " + contract.type().getZhName() + " | 区域: " + contract.regionId(),
                net.kyori.adventure.text.format.NamedTextColor.GRAY
            ));
        }

        // 通知承租方
        if (contract.tenantPlayerId() != null) {
            Player tenant = Bukkit.getPlayer(contract.tenantPlayerId());
            if (tenant != null) {
                tenant.sendMessage(net.kyori.adventure.text.Component.text(
                    "[租约] 您签署的租约已生效！",
                    net.kyori.adventure.text.format.NamedTextColor.GREEN
                ));
                tenant.sendMessage(net.kyori.adventure.text.Component.text(
                    "类型: " + contract.type().getZhName() + " | 区域: " + contract.regionId(),
                    net.kyori.adventure.text.format.NamedTextColor.GRAY
                ));
            }
        }

        // 通知出租方国家成员
        if (contract.lessorNationId() != null) {
            Optional<Nation> nationOpt = nationService.nationById(contract.lessorNationId());
            if (nationOpt.isPresent()) {
                Nation nation = nationOpt.get();
                for (var member : nation.members()) {
                    Player p = Bukkit.getPlayer(member.playerId());
                    if (p != null && !p.equals(lessor)) {
                        p.sendMessage(net.kyori.adventure.text.Component.text(
                            "[国家] 租约已生效: " + contract.type().getZhName() + " - " + contract.regionId(),
                            net.kyori.adventure.text.format.NamedTextColor.AQUA
                        ));
                    }
                }
            }
        }

        logger.info("[Lease] Lease " + contract.id() + " activated");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeaseExpired(LeaseExpiredEvent event) {
        var contract = event.getContract();

        // 通知出租方
        Player lessor = Bukkit.getPlayer(contract.lessorPlayerId());
        if (lessor != null) {
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "[租约] 您的租约已过期！",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "类型: " + contract.type().getZhName() + " | 区域: " + contract.regionId(),
                net.kyori.adventure.text.format.NamedTextColor.GRAY
            ));
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "如需续租，请使用 /lease renew",
                net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
            ));
        }

        // 通知承租方
        if (contract.tenantPlayerId() != null) {
            Player tenant = Bukkit.getPlayer(contract.tenantPlayerId());
            if (tenant != null) {
                tenant.sendMessage(net.kyori.adventure.text.Component.text(
                    "[租约] 您租赁的区域已到期！",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW
                ));
                tenant.sendMessage(net.kyori.adventure.text.Component.text(
                    "请在 " + contract.regionId() + " 区域的租约到期前完成续租",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
                ));
            }
        }

        logger.info("[Lease] Lease " + contract.id() + " expired");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeaseTerminated(LeaseTerminatedEvent event) {
        var contract = event.getContract();

        // 通知相关方
        Player lessor = Bukkit.getPlayer(contract.lessorPlayerId());
        if (lessor != null) {
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "[租约] 租约已被终止",
                net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "原因: " + event.getReason(),
                net.kyori.adventure.text.format.NamedTextColor.GRAY
            ));
        }

        if (contract.tenantPlayerId() != null) {
            Player tenant = Bukkit.getPlayer(contract.tenantPlayerId());
            if (tenant != null) {
                tenant.sendMessage(net.kyori.adventure.text.Component.text(
                    "[租约] 租约已被终止",
                    net.kyori.adventure.text.format.NamedTextColor.RED
                ));
                tenant.sendMessage(net.kyori.adventure.text.Component.text(
                    "原因: " + event.getReason(),
                    net.kyori.adventure.text.format.NamedTextColor.GRAY
                ));
            }
        }

        logger.info("[Lease] Lease " + contract.id() + " terminated: " + event.getReason());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeasePayment(LeasePaymentEvent event) {
        var contract = event.getContract();

        // 通知承租方
        Player tenant = Bukkit.getPlayer(event.getPayerId());
        if (tenant != null) {
            tenant.sendMessage(net.kyori.adventure.text.Component.text(
                "[租约] 租金支付成功: " + event.getAmount() + " (" + event.getMonthsPaid() + "个月)",
                net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
        }

        // 通知出租方
        Player lessor = Bukkit.getPlayer(contract.lessorPlayerId());
        if (lessor != null) {
            lessor.sendMessage(net.kyori.adventure.text.Component.text(
                "[租约] 收到租金: " + event.getAmount() + " (" + event.getMonthsPaid() + "个月)",
                net.kyori.adventure.text.format.NamedTextColor.GREEN
            ));
        }

        logger.info("[Lease] Lease " + contract.id() + " payment: " + event.getAmount());
    }
}