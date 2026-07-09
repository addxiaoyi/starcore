package dev.starcore.starcore.module.split.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.split.SplitService;
import dev.starcore.starcore.module.split.event.NationSplitEvent;
import dev.starcore.starcore.module.split.event.SplitRequestApprovedEvent;
import dev.starcore.starcore.module.split.event.SplitRequestCreatedEvent;
import dev.starcore.starcore.module.split.model.SplitRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Nation split event listener
 */
public final class SplitListener implements Listener {
    private final SplitService splitService;
    private final NationService nationService;
    private final MessageService messages;

    public SplitListener(SplitService splitService, NationService nationService, MessageService messages) {
        this.splitService = splitService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * Handle split request created event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSplitRequestCreated(@NotNull SplitRequestCreatedEvent event) {
        // Notify nation leader
        Nation sourceNation = event.getSourceNation();
        if (sourceNation != null) {
            UUID leaderId = sourceNation.founderId();
            Player leader = Bukkit.getPlayer(leaderId);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(Component.text()
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text("Split", NamedTextColor.YELLOW))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text(event.getRequesterName(), NamedTextColor.AQUA))
                    .append(Component.text(" started a split request", NamedTextColor.WHITE)));
                leader.sendMessage(Component.text("New nation name: ", NamedTextColor.GRAY)
                    .append(Component.text(event.getNewNationName(), NamedTextColor.GOLD)));
                leader.sendMessage(Component.text("Split chunks: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(event.getChunkCount()), NamedTextColor.AQUA)));
                leader.sendMessage(Component.text("Request ID: ", NamedTextColor.GRAY)
                    .append(Component.text(event.getRequestId().toString().substring(0, 8), NamedTextColor.WHITE)));
                leader.sendMessage(Component.text("Use ", NamedTextColor.GRAY)
                    .append(Component.text("/split approve " + event.getRequestId().toString().substring(0, 8), NamedTextColor.GREEN))
                    .append(Component.text(" to approve", NamedTextColor.GRAY)));
            }

            // Broadcast to all nation members
            for (UUID memberId : sourceNation.members().stream()
                .map(m -> m.playerId())
                .filter(id -> !id.equals(leaderId))
                .toList()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(Component.text()
                        .append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text("Split", NamedTextColor.YELLOW))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(Component.text(event.getRequesterName(), NamedTextColor.AQUA))
                        .append(Component.text(" started a split request", NamedTextColor.WHITE)));
                }
            }

            // Log
            Bukkit.getConsoleSender().sendMessage(Component.text()
                .append(Component.text("[Split] ", NamedTextColor.GRAY))
                .append(Component.text(event.getRequesterName(), NamedTextColor.AQUA))
                .append(Component.text(" initiated split request: ", NamedTextColor.WHITE))
                .append(Component.text(sourceNation.name(), NamedTextColor.GOLD))
                .append(Component.text(" -> ", NamedTextColor.GRAY))
                .append(Component.text(event.getNewNationName(), NamedTextColor.GREEN))
                .append(Component.text(" (" + event.getChunkCount() + " chunks)", NamedTextColor.DARK_GRAY))
            );
        }
    }

    /**
     * Handle split request approved event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSplitRequestApproved(@NotNull SplitRequestApprovedEvent event) {
        // Notify source nation leader
        Nation sourceNation = event.getSourceNation();
        Nation newNation = event.getNewNation();

        if (sourceNation != null) {
            UUID leaderId = sourceNation.founderId();
            Player leader = Bukkit.getPlayer(leaderId);
            if (leader != null && leader.isOnline()) {
                leader.sendMessage(Component.text()
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text("Split", NamedTextColor.GREEN))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text("Split request approved!", NamedTextColor.GREEN)));
            }

            // Broadcast to source nation members
            for (UUID memberId : sourceNation.members().stream()
                .map(m -> m.playerId())
                .filter(id -> !id.equals(leaderId))
                .toList()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(Component.text()
                        .append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text("Split", NamedTextColor.GREEN))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(Component.text(sourceNation.name(), NamedTextColor.GOLD))
                        .append(Component.text(" successfully split!", NamedTextColor.WHITE)));
                }
            }
        }

        // Notify new nation leader
        if (newNation != null) {
            Player newLeader = Bukkit.getPlayer(newNation.founderId());
            if (newLeader != null && newLeader.isOnline()) {
                newLeader.sendMessage(Component.text()
                    .append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text("Split", NamedTextColor.GREEN))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(Component.text("Congratulations! Your new nation ", NamedTextColor.WHITE))
                    .append(Component.text(newNation.name(), NamedTextColor.GOLD))
                    .append(Component.text(" has been established!", NamedTextColor.GREEN)));
                newLeader.sendMessage(Component.text("Split chunks: ", NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(event.getTransferredChunks()), NamedTextColor.AQUA)));
            }
        }

        // Log
        Bukkit.getConsoleSender().sendMessage(Component.text()
            .append(Component.text("[Split] ", NamedTextColor.GRAY))
            .append(Component.text("Split complete: ", NamedTextColor.GREEN))
            .append(Component.text(sourceNation != null ? sourceNation.name() : "Unknown", NamedTextColor.GOLD))
            .append(Component.text(" -> ", NamedTextColor.GRAY))
            .append(Component.text(newNation != null ? newNation.name() : "Unknown", NamedTextColor.GREEN))
            .append(Component.text(" (" + event.getTransferredChunks() + " chunks)", NamedTextColor.DARK_GRAY))
        );
    }

    /**
     * Handle nation split event (cancellable)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onNationSplit(@NotNull NationSplitEvent event) {
        // Add additional pre-split checks here
        Nation sourceNation = event.getSourceNation();
        if (sourceNation == null) {
            event.setCancelled(true);
            event.setCancelReason("Source nation does not exist");
            return;
        }

        // Check if there are enough members
        if (sourceNation.members().size() < 1) {
            event.setCancelled(true);
            event.setCancelReason("Insufficient nation members");
            return;
        }

        // Log before split
        Bukkit.getConsoleSender().sendMessage(Component.text()
            .append(Component.text("[Split] ", NamedTextColor.YELLOW))
            .append(Component.text("Nation split about to execute", NamedTextColor.WHITE))
            .append(Component.text(" | Source: " + sourceNation.name(), NamedTextColor.GOLD))
            .append(Component.text(" | New nation: " + event.getNewNationName(), NamedTextColor.GREEN))
            .append(Component.text(" | Chunks: " + event.getRegion().chunkCount(), NamedTextColor.AQUA))
        );
    }
}
