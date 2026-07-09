package dev.starcore.starcore.module.army.espionage.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.espionage.EspionageService;
import dev.starcore.starcore.module.army.espionage.event.*;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 间谍事件监听器
 * 处理间谍相关事件的通知和后续处理
 */
public final class EspionageListener implements Listener {
    private final EspionageService espionageService;
    private final NationService nationService;
    private final MessageService messages;

    public EspionageListener(
            EspionageService espionageService,
            NationService nationService,
            MessageService messages
    ) {
        this.espionageService = espionageService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 处理间谍被训练事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpyRecruited(SpyRecruitedEvent event) {
        // 可以在这里添加日志、成就等后续处理
        // 实际消息已在命令中发送
    }

    /**
     * 处理行动开始事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOperationStarted(EspionageOperationStartedEvent event) {
        // 记录日志
        // 实际消息已在命令中发送
    }

    /**
     * 处理行动完成事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOperationCompleted(EspionageOperationCompletedEvent event) {
        // 发送结果给发起国玩家
        notifyOperationResult(event);

        // 如果被发现，通知目标国
        if (event.wasDetected()) {
            notifyDetectionAlert(event);
        }
    }

    /**
     * 处理间谍被发现事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpyDetected(SpyDetectedEvent event) {
        // 通知目标国领导层
        notifySpyDetectedToTarget(event);
    }

    /**
     * 处理间谍死亡事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpyLost(SpyLostEvent event) {
        // 根据原因发送不同消息
        switch (event.getCause()) {
            case DETECTED_AND_EXECUTED -> {
                // 间谍被处决，通知国家
                notifySpyExecuted(event);
            }
            case MORALE_DEPLETED -> {
                // 士气耗尽，通知国家
                notifySpyMoraleDepleted(event);
            }
            case DISMISSED -> {
                // 主动解雇，只记录
            }
            default -> {
                // 其他原因
            }
        }
    }

    /**
     * 通知行动结果给发起国
     */
    private void notifyOperationResult(EspionageOperationCompletedEvent event) {
        var operation = event.getOperation();
        var nation = nationService.nationById(new dev.starcore.starcore.module.nation.model.NationId(operation.sourceNationId()));

        if (nation.isEmpty()) {
            return;
        }

        String prefix = event.isSuccess() ? "espionage.notification.success." : "espionage.notification.failure.";
        String operationType = operation.type().key();

        // 获取国家所有在线玩家并发送消息
        for (var member : nation.get().members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                if (event.isSuccess()) {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            messages.format(prefix + operationType, operation.targetNationName())
                    ).color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
                } else {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            messages.format(prefix + operationType, operation.targetNationName())
                    ).color(net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
        }
    }

    /**
     * 通知检测警报
     */
    private void notifyDetectionAlert(EspionageOperationCompletedEvent event) {
        var operation = event.getOperation();
        var targetNation = nationService.nationById(new dev.starcore.starcore.module.nation.model.NationId(operation.targetNationId()));

        if (targetNation.isEmpty()) {
            return;
        }

        // 通知目标国领导层有间谍被检测到
        for (var member : targetNation.get().members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                // 只有高级成员能看到警报
                if ("leader".equals(member.rank()) || "officer".equals(member.rank())) {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            messages.format("espionage.alert.detected", operation.sourceNationName())
                    ).color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                }
            }
        }
    }

    /**
     * 通知目标国有间谍被发现
     */
    private void notifySpyDetectedToTarget(SpyDetectedEvent event) {
        var targetNation = nationService.nationById(new dev.starcore.starcore.module.nation.model.NationId(event.getTargetNationId()));

        if (targetNation.isEmpty()) {
            return;
        }

        for (var member : targetNation.get().members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                // 只通知领导层
                if ("leader".equals(member.rank()) || "officer".equals(member.rank())) {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            messages.format("espionage.ci.spy-detected",
                                    event.getSourceNationName(),
                                    event.getSpy().type().key())
                    ).color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
                }
            }
        }
    }

    /**
     * 通知间谍被处决
     */
    private void notifySpyExecuted(SpyLostEvent event) {
        var nation = nationService.nationById(new dev.starcore.starcore.module.nation.model.NationId(event.getNationId()));

        if (nation.isEmpty()) {
            return;
        }

        for (var member : nation.get().members()) {
            Player player = Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                if ("leader".equals(member.rank()) || "officer".equals(member.rank())) {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            messages.format("espionage.notification.spy-executed",
                                    event.getSpy().type().key())
                    ).color(net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
        }
    }

    /**
     * 通知间谍士气耗尽
     */
    private void notifySpyMoraleDepleted(SpyLostEvent event) {
        // 间谍士气耗尽不需要特别通知，因为它是一个逐渐发生的过程
    }
}
