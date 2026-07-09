package dev.starcore.starcore.module.nation.claimtool;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.function.BooleanSupplier;

public final class ClaimToolListener implements Listener {
    private final BooleanSupplier claimToolEnabled;
    private final ClaimToolService claimToolService;
    private final MessageService messages;

    public ClaimToolListener(ConfigurationService configuration, ClaimToolService claimToolService, MessageService messages) {
        this(configuration::claimToolEnabled, claimToolService, messages);
    }

    ClaimToolListener(BooleanSupplier claimToolEnabled, ClaimToolService claimToolService, MessageService messages) {
        this.claimToolEnabled = claimToolEnabled;
        this.claimToolService = claimToolService;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!claimToolEnabled.getAsBoolean() || !claimToolService.isClaimTool(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedBlock() == null) {
            event.getPlayer().sendMessage(Component.text(messages.format("territory.tool.need-block"), NamedTextColor.YELLOW));
            return;
        }
        ClaimToolPoint point = switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> ClaimToolPoint.FIRST;
            case RIGHT_CLICK_BLOCK -> ClaimToolPoint.SECOND;
            default -> null;
        };
        if (point == null) {
            event.getPlayer().sendMessage(Component.text(messages.format("territory.tool.need-block"), NamedTextColor.YELLOW));
            return;
        }
        ClaimToolSelectionUpdate update = claimToolService.select(event.getPlayer(), event.getClickedBlock(), point);
        event.getPlayer().sendMessage(Component.text(messages.format(
            point == ClaimToolPoint.FIRST ? "territory.tool.first-set" : "territory.tool.second-set",
            update.world(),
            update.blockX(),
            update.blockZ(),
            update.chunkX(),
            update.chunkZ()
        ), NamedTextColor.GREEN));
        update.preview().ifPresent(preview -> sendPreview(event, preview));
    }

    private void sendPreview(PlayerInteractEvent event, ClaimSelectionPreview preview) {
        NamedTextColor color = preview.canSubmit() ? NamedTextColor.GREEN : NamedTextColor.RED;
        event.getPlayer().sendMessage(Component.text(messages.format(
            "territory.tool.preview",
            preview.chunkCount(),
            preview.price().toPlainString(),
            preview.message()
        ), color));
        if (preview.canSubmit()) {
            event.getPlayer().sendMessage(Component.text(messages.format("territory.tool.confirm-hint"), NamedTextColor.YELLOW));
        }
        preview.explanation().reasons().stream()
            .map(ClaimSelectionReason::message)
            .filter(message -> message != null && !message.isBlank())
            .limit(3)
            .forEach(message -> event.getPlayer().sendMessage(Component.text("- " + message, NamedTextColor.GRAY)));
    }
}
