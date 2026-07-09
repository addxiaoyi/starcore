package dev.starcore.starcore.module.resolution;

import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages pending input sessions for resolution proposals.
 * Handles text input via chat for creating proposals.
 */
public class ResolutionInputHandler {

    private final ResolutionModule resolutionModule;
    private final NationService nationService;
    private final GovernmentService governmentService;
    private final Map<UUID, InputSession> activeSessions = new ConcurrentHashMap<>();

    public ResolutionInputHandler(ResolutionModule resolutionModule, NationService nationService, GovernmentService governmentService) {
        this.resolutionModule = resolutionModule;
        this.nationService = nationService;
        this.governmentService = governmentService;
    }

    /**
     * Input session types
     */
    public enum InputType {
        RENAME_NATION("国家改名", "请输入新的国家名称"),
        JOIN_PLAYER("加入申请", "请输入要加入的玩家名称"),
        DIPLOMACY_TARGET("外交目标", "请输入目标国家名称"),
        CUSTOM_PROPOSAL("自定义提案", "请输入提案内容");

        private final String displayName;
        private final String prompt;

        InputType(String displayName, String prompt) {
            this.displayName = displayName;
            this.prompt = prompt;
        }

        public String displayName() { return displayName; }
        public String prompt() { return prompt; }
    }

    /**
     * Active input session
     */
    public static class InputSession {
        final UUID playerId;
        final InputType type;
        final Nation nation;
        final UUID proposalId; // For tracking back to original session
        final Object extraData; // Additional context (e.g., GovernmentType, DiplomacyRelation)
        final Consumer<String> onComplete;
        final Consumer<String> onCancel;

        InputSession(UUID playerId, InputType type, Nation nation, UUID proposalId, Object extraData,
                     Consumer<String> onComplete, Consumer<String> onCancel) {
            this.playerId = playerId;
            this.type = type;
            this.nation = nation;
            this.proposalId = proposalId;
            this.extraData = extraData;
            this.onComplete = onComplete;
            this.onCancel = onCancel;
        }
    }

    /**
     * Start an input session
     */
    public void startInputSession(Player player, InputType type, Nation nation, Object extraData,
                                   Consumer<String> onComplete, Consumer<String> onCancel) {
        InputSession session = new InputSession(
            player.getUniqueId(), type, nation, UUID.randomUUID(), extraData, onComplete, onCancel
        );
        activeSessions.put(player.getUniqueId(), session);

        player.closeInventory();
        player.sendMessage(Component.text("§6═══════════════════════════════════", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        player.sendMessage(Component.text("§e" + type.displayName(), net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        player.sendMessage(Component.text("§7" + type.prompt(), net.kyori.adventure.text.format.NamedTextColor.GRAY));
        player.sendMessage(Component.text("§7输入 §ccancel §7取消", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("§6═══════════════════════════════════", net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }

    /**
     * Start a nation rename session
     */
    public void startRenameSession(Player player, Nation nation, Consumer<String> onComplete) {
        startInputSession(player, InputType.RENAME_NATION, nation, null, onComplete,
            msg -> player.sendMessage(Component.text("§c已取消国家改名", net.kyori.adventure.text.format.NamedTextColor.RED)));
    }

    /**
     * Start a join request session
     */
    public void startJoinSession(Player player, Nation nation, Consumer<String> onComplete) {
        startInputSession(player, InputType.JOIN_PLAYER, nation, null, onComplete,
            msg -> player.sendMessage(Component.text("§c已取消加入申请", net.kyori.adventure.text.format.NamedTextColor.RED)));
    }

    /**
     * Start a diplomacy target session
     */
    public void startDiplomacyTargetSession(Player player, Nation nation, GovernmentService governmentService,
                                            Consumer<String> onTargetSelected) {
        // For diplomacy, we first need the target nation, then the relation type
        startInputSession(player, InputType.DIPLOMACY_TARGET, nation, governmentService,
            onTargetSelected,
            msg -> player.sendMessage(Component.text("§c已取消外交关系提案", net.kyori.adventure.text.format.NamedTextColor.RED)));
    }

    /**
     * Handle chat input from player
     */
    public boolean handleChatInput(Player player, String message) {
        InputSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return false; // Not our session
        }

        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("取消")) {
            session.onCancel.accept(message);
            activeSessions.remove(player.getUniqueId());
            return true;
        }

        if (message.trim().isEmpty()) {
            player.sendMessage(Component.text("§c输入不能为空，请重新输入：", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }

        String input = message.trim();
        session.onComplete.accept(input);
        activeSessions.remove(player.getUniqueId());
        return true;
    }

    /**
     * Check if player has active session
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Get player's active session
     */
    public InputSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    /**
     * Cancel all sessions for a player
     */
    public void cancelSession(UUID playerId) {
        InputSession session = activeSessions.remove(playerId);
        if (session != null && session.onCancel != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                session.onCancel.accept("session_closed");
            }
        }
    }

    /**
     * Clear all sessions
     */
    public void clearAllSessions() {
        activeSessions.clear();
    }
}
