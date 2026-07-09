package dev.starcore.starcore.module.tournament.gui;

import dev.starcore.starcore.module.tournament.Tournament;
import dev.starcore.starcore.module.tournament.TournamentService;
import dev.starcore.starcore.module.tournament.TournamentStatus;
import dev.starcore.starcore.module.tournament.TournamentType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 锦标赛 GUI 点击事件监听器
 * 处理锦标赛相关 GUI 的交互事件
 */
public class TournamentGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final TournamentService tournamentService;
    private final TournamentGui tournamentGui;

    // GUI 状态跟踪
    private final Map<UUID, String> playerGuiState = new ConcurrentHashMap<>();

    public TournamentGuiListener(JavaPlugin plugin, TournamentService tournamentService, TournamentGui tournamentGui) {
        this.plugin = plugin;
        this.tournamentService = tournamentService;
        this.tournamentGui = tournamentGui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 检查是否是锦标赛相关的 GUI
        Component title = event.getView().title();
        String titleText = PlainTextComponentSerializer.plainText().serialize(title);

        if (!isTournamentGui(titleText)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // 处理点击
        handleClick(player, titleText, clickedItem, event.getSlot());
    }

    private boolean isTournamentGui(String title) {
        return title.contains("锦标赛") ||
               title.contains("比赛") ||
               title.contains("观战") ||
               title.contains("选择比赛");
    }

    private void handleClick(Player player, String title, ItemStack item, int slot) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        Component displayName = meta.displayName();
        String nameText = PlainTextComponentSerializer.plainText().serialize(displayName);

        // 主菜单
        if (title.contains("锦标赛大厅")) {
            handleMainMenuClick(player, nameText, slot);
            return;
        }

        // 创建比赛
        if (title.contains("选择比赛类型")) {
            handleCreateMenuClick(player, nameText, slot);
            return;
        }

        // 加入比赛
        if (title.contains("加入比赛")) {
            handleJoinMenuClick(player, nameText, item);
            return;
        }

        // 观战
        if (title.contains("观战")) {
            handleSpectateMenuClick(player, nameText, item);
            return;
        }

        // 比赛详情
        if (title.contains("比赛名")) {
            handleTournamentInfoClick(player, nameText, slot);
        }
    }

    private void handleMainMenuClick(Player player, String itemName, int slot) {
        switch (slot) {
            case 11 -> {
                // 创建比赛
                player.closeInventory();
                tournamentGui.openCreateGui(player);
            }
            case 13 -> {
                // 加入比赛
                player.closeInventory();
                tournamentGui.openJoinGui(player);
            }
            case 15 -> {
                // 观战
                player.closeInventory();
                tournamentGui.openSpectateGui(player);
            }
            case 4 -> {
                // 我的比赛
                tournamentService.getPlayerTournament(player).ifPresentOrElse(
                    t -> tournamentGui.openTournamentInfo(player, t),
                    () -> player.sendMessage("§c你不在任何比赛中")
                );
            }
            default -> {
                // 忽略边框点击
            }
        }
    }

    private void handleCreateMenuClick(Player player, String itemName, int slot) {
        TournamentType selectedType = null;

        switch (slot) {
            case 10 -> selectedType = TournamentType.PVP_1V1;
            case 11 -> selectedType = TournamentType.PVP_FFA;
            case 12 -> selectedType = TournamentType.PVP_TEAM;
            case 14 -> selectedType = TournamentType.SPEEDRUN;
            case 15 -> selectedType = TournamentType.PARKOUR;
            case 16 -> selectedType = TournamentType.ELIMINATION;
            case 22 -> {
                // 返回
                tournamentGui.openMainGui(player);
                return;
            }
            default -> {
                // 忽略
            }
        }

        if (selectedType != null) {
            var config = tournamentService.getConfig(selectedType);
            Tournament tournament = tournamentService.createTournament(
                config.displayName() + " #" + (System.currentTimeMillis() % 10000),
                selectedType,
                player
            );
            player.sendMessage("§a比赛已创建: §e" + config.displayName());
            player.sendMessage("§7使用 §e/tournament join " + tournament.getId() + " §7加入比赛");
            tournamentGui.openMainGui(player);
        }
    }

    private void handleJoinMenuClick(Player player, String itemName, ItemStack item) {
        // 从物品 Lore 中提取比赛 ID
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var lore = meta.lore();
        if (lore == null) return;

        for (Component loreComponent : lore) {
            String loreText = PlainTextComponentSerializer.plainText().serialize(loreComponent);
            if (loreText.contains("/tournament join ")) {
                String tournamentId = loreText.substring(loreText.indexOf("/tournament join ") + 17).trim();
                // 找到第一个空格前的 ID
                if (tournamentId.contains(" ")) {
                    tournamentId = tournamentId.substring(0, tournamentId.indexOf(" "));
                }
                tournamentService.joinTournament(tournamentId, player);
                tournamentService.getTournament(tournamentId).ifPresent(
                    t -> tournamentGui.openTournamentInfo(player, t)
                );
                return;
            }
        }

        // 返回按钮
        if (item.getType() == Material.ARROW) {
            tournamentGui.openMainGui(player);
        }
    }

    private void handleSpectateMenuClick(Player player, String itemName, ItemStack item) {
        // 从物品 Lore 中提取比赛 ID
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var lore = meta.lore();
        if (lore == null) return;

        for (Component loreComponent : lore) {
            String loreText = PlainTextComponentSerializer.plainText().serialize(loreComponent);
            if (loreText.contains("/tournament spectate ")) {
                String tournamentId = loreText.substring(loreText.indexOf("/tournament spectate ") + 20).trim();
                if (tournamentId.contains(" ")) {
                    tournamentId = tournamentId.substring(0, tournamentId.indexOf(" "));
                }
                player.sendMessage("§b请使用命令 §e/tournament spectate " + tournamentId + " §b进入观战");
                player.closeInventory();
                return;
            }
        }

        // 返回按钮
        if (item.getType() == Material.ARROW) {
            tournamentGui.openMainGui(player);
        }
    }

    private void handleTournamentInfoClick(Player player, String itemName, int slot) {
        tournamentService.getPlayerTournament(player).ifPresent(tournament -> {
            if (tournament.getStatus() == TournamentStatus.WAITING) {
                switch (slot) {
                    case 49 -> {
                        // 离开比赛
                        tournamentService.leaveTournament(player);
                        tournamentGui.openMainGui(player);
                    }
                    case 48 -> {
                        // 开始比赛（只有创建者）
                        if (tournament.getCreatorId().equals(player.getUniqueId())) {
                            if (tournament.getParticipants().size() >= 2) {
                                tournamentService.startTournament(tournament.getId());
                                player.closeInventory();
                            } else {
                                player.sendMessage("§c需要至少 2 名玩家才能开始比赛！");
                            }
                        }
                    }
                    case 53 -> {
                        // 返回
                        tournamentGui.openMainGui(player);
                    }
                    default -> {
                        // 忽略
                    }
                }
            } else if (slot == 53) {
                // 返回
                tournamentGui.openMainGui(player);
            }
        });
    }

    /**
     * 设置玩家 GUI 状态
     */
    public void setPlayerGuiState(Player player, String state) {
        playerGuiState.put(player.getUniqueId(), state);
    }

    /**
     * 获取玩家 GUI 状态
     */
    public String getPlayerGuiState(Player player) {
        return playerGuiState.get(player.getUniqueId());
    }

    /**
     * 清除玩家 GUI 状态
     */
    public void clearPlayerGuiState(Player player) {
        playerGuiState.remove(player.getUniqueId());
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 playerGuiState Map 导致的内存泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerGuiState(event.getPlayer());
    }
}
