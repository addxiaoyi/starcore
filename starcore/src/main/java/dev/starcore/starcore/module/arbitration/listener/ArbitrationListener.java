package dev.starcore.starcore.module.arbitration.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.arbitration.ArbitrationService;
import dev.starcore.starcore.module.arbitration.model.ArbitrationCase;
import dev.starcore.starcore.module.arbitration.model.ArbitrationStatus;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 仲裁模块事件监听器
 * 处理与仲裁相关的游戏事件
 */
public final class ArbitrationListener implements Listener {
    private final ArbitrationService arbitrationService;
    private final NationService nationService;
    private final MessageService messages;
    private final Plugin plugin;

    // 玩家选择模式状态
    private final Map<Player, SelectionMode> selectionModes = new ConcurrentHashMap<>();

    public ArbitrationListener(
        ArbitrationService arbitrationService,
        NationService nationService,
        MessageService messages
    ) {
        this.arbitrationService = arbitrationService;
        this.nationService = nationService;
        this.messages = messages;
        this.plugin = null;
    }

    public ArbitrationListener(
        ArbitrationService arbitrationService,
        NationService nationService,
        MessageService messages,
        Plugin plugin
    ) {
        this.arbitrationService = arbitrationService;
        this.nationService = nationService;
        this.messages = messages;
        this.plugin = plugin;
    }

    /**
     * 玩家加入事件 - 通知待处理仲裁
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 检查玩家国家是否有待处理案件
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        // 检查是否有需要该玩家作为被申诉方的案件
        var cases = arbitrationService.getCasesForNation(nation.id());
        for (ArbitrationCase arbitrationCase : cases) {
            if (arbitrationCase.isRespondent(nation.id()) &&
                arbitrationCase.status() == ArbitrationStatus.PENDING) {

                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("You have pending arbitration cases as respondent!", NamedTextColor.YELLOW));
                player.sendMessage(Component.text(
                    "Case " + arbitrationCase.id().toString().substring(0, 8) +
                    ": " + arbitrationCase.caseType().displayName(),
                    NamedTextColor.GRAY
                ));
                player.sendMessage(Component.text(""));
                break; // 只通知一次
            }
        }
    }

    /**
     * 玩家交互事件 - 用于仲裁工具交互
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理主手
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        SelectionMode mode = selectionModes.get(player);

        if (mode == null) {
            return;
        }

        // 处理选择逻辑
        switch (mode.action()) {
            case "select-dispute" -> {
                // 选择争议区块
                org.bukkit.block.Block clickedBlock = event.getClickedBlock();
                if (clickedBlock != null) {
                    org.bukkit.Location loc = clickedBlock.getLocation();
                    // 可以在这里添加区块选择逻辑
                    player.sendMessage(Component.text(
                        "Selected block at " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                        NamedTextColor.GREEN
                    ));
                }
            }
        }
    }

    /**
     * 启用玩家选择模式
     */
    public void enableSelectionMode(Player player, String action, String data) {
        selectionModes.put(player, new SelectionMode(action, data));
        player.sendMessage(Component.text("Selection mode enabled. Right-click to select.", NamedTextColor.GREEN));
    }

    /**
     * 禁用玩家选择模式
     */
    public void disableSelectionMode(Player player) {
        selectionModes.remove(player);
        player.sendMessage(Component.text("Selection mode disabled.", NamedTextColor.YELLOW));
    }

    /**
     * 检查玩家是否在选择模式中
     */
    public boolean isInSelectionMode(Player player) {
        return selectionModes.containsKey(player);
    }

    /**
     * 获取当前选择模式信息
     */
    public Optional<SelectionMode> getSelectionMode(Player player) {
        return Optional.ofNullable(selectionModes.get(player));
    }

    /**
     * 选择模式记录
     */
    public record SelectionMode(String action, String data) {}

    // E-054 修复: 玩家退出时清理选择模式状态
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        selectionModes.remove(event.getPlayer());
    }
}