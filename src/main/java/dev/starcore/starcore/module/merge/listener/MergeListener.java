package dev.starcore.starcore.module.merge.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.merge.MergeReferendumService;
import dev.starcore.starcore.module.merge.model.MergeReferendum;
import dev.starcore.starcore.module.merge.event.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * 合并公投事件监听器
 * 处理公投相关事件并广播消息
 */
public final class MergeListener implements Listener {
    private final MergeReferendumService mergeService;
    private final NationService nationService;
    private final MessageService messages;

    public MergeListener(MergeReferendumService mergeService, NationService nationService, MessageService messages) {
        this.mergeService = mergeService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 处理公投发起事件
     */
    @EventHandler
    public void onReferendumProposed(MergeReferendumProposedEvent event) {
        MergeReferendum referendum = event.getReferendum();

        // 通知公投涉及的两个国家成员
        notifyNationMembers(referendum.proposerNationId().value(), referendum);
        notifyNationMembers(referendum.targetNationId().value(), referendum);
    }

    /**
     * 处理投票事件
     */
    @EventHandler
    public void onReferendumVoted(MergeReferendumVotedEvent event) {
        // 可选：通知其他成员有人投票了
    }

    /**
     * 处理公投通过事件
     */
    @EventHandler
    public void onReferendumApproved(MergeReferendumApprovedEvent event) {
        MergeReferendum referendum = event.getReferendum();

        String message = messages.format(
            "merge.approved.broadcast",
            referendum.proposerNationId().value().toString().substring(0, 8),
            referendum.targetNationId().value().toString().substring(0, 8),
            referendum.newNationName()
        );

        broadcastToInvolvedNations(referendum, message);
    }

    /**
     * 处理公投被拒事件
     */
    @EventHandler
    public void onReferendumRejected(MergeReferendumRejectedEvent event) {
        MergeReferendum referendum = event.getReferendum();

        String message = messages.format(
            "merge.rejected.broadcast",
            referendum.proposerNationId().value().toString().substring(0, 8),
            referendum.targetNationId().value().toString().substring(0, 8)
        );

        broadcastToInvolvedNations(referendum, message);
    }

    /**
     * 处理合并完成事件
     */
    @EventHandler
    public void onMergeCompleted(MergeCompletedEvent event) {
        String message = messages.format(
            "merge.completed.broadcast",
            event.getResultNationId().value().toString().substring(0, 8)
        );

        // 广播给所有在线玩家
        org.bukkit.Bukkit.broadcast(Component.text(message, NamedTextColor.GOLD));
    }

    private void notifyNationMembers(UUID nationId, MergeReferendum referendum) {
        Nation nation = nationService.nationById(dev.starcore.starcore.module.nation.model.NationId.of(nationId)).orElse(null);
        if (nation == null) {
            return;
        }

        for (var member : nation.members()) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text(
                    messages.format("merge.notify.proposed", referendum.proposerName(), referendum.newNationName()),
                    NamedTextColor.YELLOW
                ));
            }
        }
    }

    private void broadcastToInvolvedNations(MergeReferendum referendum, String message) {
        notifyNationMembersAsEntity(referendum.proposerNationId(), message);
        notifyNationMembersAsEntity(referendum.targetNationId(), message);
    }

    private void notifyNationMembersAsEntity(dev.starcore.starcore.module.nation.model.NationId nationId, String message) {
        Nation nation = nationService.nationById(nationId).orElse(null);
        if (nation == null) {
            return;
        }

        for (var member : nation.members()) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(member.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text(message, NamedTextColor.GOLD));
            }
        }
    }
}