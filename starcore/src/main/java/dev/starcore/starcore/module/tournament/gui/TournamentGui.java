package dev.starcore.starcore.module.tournament.gui;

import dev.starcore.starcore.module.tournament.Tournament;
import dev.starcore.starcore.module.tournament.TournamentStatus;
import dev.starcore.starcore.module.tournament.TournamentType;
import dev.starcore.starcore.module.tournament.TournamentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 锦标赛 GUI 界面
 */
public class TournamentGui implements InventoryHolder {

    private final JavaPlugin plugin;
    private final TournamentService tournamentService;
    private Inventory inventory;

    public TournamentGui(JavaPlugin plugin, TournamentService tournamentService) {
        this.plugin = plugin;
        this.tournamentService = tournamentService;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ==================== 主菜单 ====================

    /**
     * 打开锦标赛主菜单
     */
    public void openMainGui(Player player) {
        inventory = Bukkit.createInventory(this, 27,
            Component.text("§6§l⚔ 锦标赛大厅 ⚔").decoration(TextDecoration.BOLD, true));

        // 创建比赛
        setItem(11, createGuiItem(
            Material.BEACON,
            Component.text("§a§l创建比赛", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
            List.of(
                Component.text("§7创建新的锦标赛"),
                Component.text(""),
                Component.text("§a点击创建比赛")
            ),
            e -> {
                e.setCancelled(true);
                player.closeInventory();
                openCreateGui(player);
            }
        ));

        // 加入比赛
        setItem(13, createGuiItem(
            Material.ARROW,
            Component.text("§e§l加入比赛", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true),
            List.of(
                Component.text("§7加入现有的比赛"),
                Component.text(""),
                Component.text("§e点击查看可加入的比赛")
            ),
            e -> {
                e.setCancelled(true);
                openJoinGui(player);
            }
        ));

        // 观战
        setItem(15, createGuiItem(
            Material.ENDER_EYE,
            Component.text("§b§l观战", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true),
            List.of(
                Component.text("§7观看正在进行中的比赛"),
                Component.text(""),
                Component.text("§b点击观战")
            ),
            e -> {
                e.setCancelled(true);
                openSpectateGui(player);
            }
        ));

        // 我的比赛
        var current = tournamentService.getPlayerTournament(player);
        if (current.isPresent()) {
            setItem(4, createGuiItem(
                Material.NETHER_STAR,
                Component.text("§6§l我的比赛", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                List.of(
                    Component.text("§7比赛: §f" + current.get().getName()),
                    Component.text("§7状态: §f" + current.get().getStatus().displayName()),
                    Component.text(""),
                    Component.text("§a点击查看详情")
                ),
                e -> {
                    e.setCancelled(true);
                    openTournamentInfo(player, current.get());
                }
            ));
        } else {
            setItem(4, createGuiItem(
                Material.BARRIER,
                Component.text("§7§l未参加比赛", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true),
                List.of(
                    Component.text("§7你当前没有参加任何比赛"),
                    Component.text(""),
                    Component.text("§e创建或加入比赛开始游戏")
                )
            ));
        }

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    // ==================== 创建比赛 ====================

    /**
     * 打开创建比赛界面
     */
    public void openCreateGui(Player player) {
        inventory = Bukkit.createInventory(this, 27,
            Component.text("§6§l📋 选择比赛类型"));

        // PvP 单挑
        setItem(10, createTournamentTypeItem(player, TournamentType.PVP_1V1,
            Material.DIAMOND_SWORD, "§bPvP 单挑", "1v1 个人对决"));

        // PvP 乱斗
        setItem(11, createTournamentTypeItem(player, TournamentType.PVP_FFA,
            Material.IRON_SWORD, "§cPvP 乱斗", "多人自由战斗"));

        // 团队赛
        setItem(12, createTournamentTypeItem(player, TournamentType.PVP_TEAM,
            Material.SHIELD, "§9团队赛", "团队对战"));

        // 速通
        setItem(14, createTournamentTypeItem(player, TournamentType.SPEEDRUN,
            Material.CLOCK, "§a速通挑战", "竞速完成目标"));

        // 跑酷
        setItem(15, createTournamentTypeItem(player, TournamentType.PARKOUR,
            Material.BRICK, "§e跑酷挑战", "跑酷竞速"));

        // 淘汰赛
        setItem(16, createTournamentTypeItem(player, TournamentType.ELIMINATION,
            Material.TNT, "§4淘汰赛", "生存淘汰"));

        // 返回
        setItem(22, createGuiItem(
            Material.ARROW,
            Component.text("§c返回主菜单"),
            List.of(Component.text("")),
            e -> {
                e.setCancelled(true);
                openMainGui(player);
            }
        ));

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    private ItemStack createTournamentTypeItem(Player player, TournamentType type,
                                               Material material, String name, String desc) {
        var config = tournamentService.getConfig(type);
        return createGuiItem(
            material,
            Component.text(name).decoration(TextDecoration.BOLD, true),
            List.of(
                Component.text(desc),
                Component.text(""),
                Component.text("§7最大人数: §f" + config.maxPlayers()),
                Component.text("§7奖金池: §e" + config.prizePool()),
                Component.text(""),
                Component.text("§a点击选择此类型")
            ),
            e -> {
                e.setCancelled(true);
                Tournament tournament = tournamentService.createTournament(
                    config.displayName() + " #" + System.currentTimeMillis() % 1000,
                    type,
                    player
                );
                player.sendMessage("§a比赛已创建: §e" + config.displayName());
                player.sendMessage("§7使用 §e/tournament join §7加入比赛");
                openMainGui(player);
            }
        );
    }

    // ==================== 加入比赛 ====================

    /**
     * 打开加入比赛界面
     */
    public void openJoinGui(Player player) {
        inventory = Bukkit.createInventory(this, 27,
            Component.text("§e§l📝 加入比赛"));

        var waitingTournaments = tournamentService.getActiveTournaments().stream()
            .filter(t -> t.getStatus() == TournamentStatus.WAITING)
            .toList();

        if (waitingTournaments.isEmpty()) {
            setItem(13, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无等待中的比赛"),
                List.of(
                    Component.text("§7所有比赛都已开始或已结束"),
                    Component.text(""),
                    Component.text("§e创建新比赛吧！")
                )
            ));
        } else {
            int slot = 10;
            for (Tournament t : waitingTournaments) {
                if (slot <= 16 && slot != 13) {
                    setItem(slot, createTournamentJoinItem(player, t));
                }
                slot++;
                if (slot == 18) break;
            }
        }

        setItem(22, createGuiItem(
            Material.ARROW,
            Component.text("§c返回主菜单"),
            List.of(),
            e -> {
                e.setCancelled(true);
                openMainGui(player);
            }
        ));

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    private ItemStack createTournamentJoinItem(Player player, Tournament tournament) {
        Material material = getTournamentMaterial(tournament.getType());
        return createGuiItem(
            material,
            Component.text("§e" + tournament.getName()),
            List.of(
                Component.text("§7类型: §f" + tournament.getType().displayName()),
                Component.text("§7参赛者: §f" + tournament.getParticipants().size() +
                    "/" + tournament.getConfig().maxPlayers()),
                Component.text(""),
                Component.text("§a点击加入")
            ),
            e -> {
                e.setCancelled(true);
                tournamentService.joinTournament(tournament.getId(), player);
                openTournamentInfo(player, tournament);
            }
        );
    }

    // ==================== 观战 ====================

    /**
     * 打开观战界面
     */
    public void openSpectateGui(Player player) {
        inventory = Bukkit.createInventory(this, 27,
            Component.text("§b§l👁 观战"));

        var inProgressTournaments = tournamentService.getActiveTournaments().stream()
            .filter(t -> t.getStatus() == TournamentStatus.IN_PROGRESS)
            .toList();

        if (inProgressTournaments.isEmpty()) {
            setItem(13, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无进行中的比赛"),
                List.of(
                    Component.text("§7当前没有正在进行的比赛"),
                    Component.text(""),
                    Component.text("§e加入等待中的比赛吧！")
                )
            ));
        } else {
            int slot = 10;
            for (Tournament t : inProgressTournaments) {
                if (slot <= 16 && slot != 13) {
                    setItem(slot, createSpectateItem(player, t));
                }
                slot++;
                if (slot == 18) break;
            }
        }

        setItem(22, createGuiItem(
            Material.ARROW,
            Component.text("§c返回主菜单"),
            List.of(),
            e -> {
                e.setCancelled(true);
                openMainGui(player);
            }
        ));

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    private ItemStack createSpectateItem(Player player, Tournament tournament) {
        Material material = getTournamentMaterial(tournament.getType());
        return createGuiItem(
            material,
            Component.text("§b" + tournament.getName()),
            List.of(
                Component.text("§7类型: §f" + tournament.getType().displayName()),
                Component.text("§7参赛者: §f" + tournament.getParticipants().size()),
                Component.text("§7存活: §f" + tournament.getAliveParticipants().size()),
                Component.text(""),
                Component.text("§b点击观战")
            ),
            e -> {
                e.setCancelled(true);
                player.sendMessage("§b正在前往观战...");
            }
        );
    }

    // ==================== 比赛详情 ====================

    /**
     * 打开比赛详情界面
     */
    public void openTournamentInfo(Player player, Tournament tournament) {
        inventory = Bukkit.createInventory(this, 54,
            Component.text("§6§l🏆 " + tournament.getName()));

        // 比赛信息
        setItem(4, createGuiItem(
            getTournamentMaterial(tournament.getType()),
            Component.text("§6" + tournament.getName()),
            List.of(
                Component.text("§7类型: §f" + tournament.getType().displayName()),
                Component.text("§7状态: §f" + tournament.getStatus().displayName()),
                Component.text("§7参赛者: §f" + tournament.getParticipants().size() +
                    "/" + tournament.getConfig().maxPlayers()),
                Component.text("§7存活: §f" + tournament.getAliveParticipants().size())
            )
        ));

        // 显示参与者
        int slot = 19;
        for (UUID participantId : tournament.getParticipants()) {
            if (slot > 53) break;
            String playerName = Bukkit.getPlayer(participantId) != null ?
                Bukkit.getPlayer(participantId).getName() : participantId.toString().substring(0, 8);
            boolean isAlive = tournament.getAliveParticipants().contains(participantId);
            int kills = tournament.getKills().getOrDefault(participantId, 0);

            setItem(slot, createGuiItem(
                isAlive ? Material.PLAYER_HEAD : Material.ZOMBIE_HEAD,
                Component.text(isAlive ? "§a" + playerName : "§c" + playerName),
                List.of(
                    Component.text("§7状态: " + (isAlive ? "§a存活" : "§c淘汰")),
                    Component.text("§7击杀: §f" + kills)
                )
            ));
            slot++;
        }

        // 操作按钮
        if (tournament.getStatus() == TournamentStatus.WAITING) {
            if (tournament.getParticipants().contains(player.getUniqueId())) {
                setItem(49, createGuiItem(
                    Material.BARRIER,
                    Component.text("§c离开比赛"),
                    List.of(
                        Component.text("§7点击离开当前比赛")
                    ),
                    e -> {
                        e.setCancelled(true);
                        tournamentService.leaveTournament(player);
                        openMainGui(player);
                    }
                ));

                if (tournament.getCreatorId().equals(player.getUniqueId())) {
                    setItem(48, createGuiItem(
                        Material.GREEN_CONCRETE,
                        Component.text("§a开始比赛"),
                        List.of(
                            Component.text("§7点击开始比赛"),
                            Component.text("§e需要至少 2 名玩家")
                        ),
                        e -> {
                            e.setCancelled(true);
                            if (tournament.getParticipants().size() >= 2) {
                                tournamentService.startTournament(tournament.getId());
                                player.closeInventory();
                            } else {
                                player.sendMessage("§c需要至少 2 名玩家才能开始比赛！");
                            }
                        }
                    ));
                }
            } else {
                setItem(49, createGuiItem(
                    Material.LIME_CONCRETE,
                    Component.text("§a加入比赛"),
                    List.of(
                        Component.text("§7点击加入比赛")
                    ),
                    e -> {
                        e.setCancelled(true);
                        tournamentService.joinTournament(tournament.getId(), player);
                        openTournamentInfo(player, tournament);
                    }
                ));
            }
        }

        // 返回按钮
        setItem(53, createGuiItem(
            Material.ARROW,
            Component.text("§c返回主菜单"),
            List.of(),
            e -> {
                e.setCancelled(true);
                openMainGui(player);
            }
        ));

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    // ==================== 工具方法 ====================

    private Material getTournamentMaterial(TournamentType type) {
        return switch (type) {
            case PVP_1V1 -> Material.DIAMOND_SWORD;
            case PVP_FFA -> Material.IRON_SWORD;
            case PVP_TEAM -> Material.SHIELD;
            case SPEEDRUN -> Material.CLOCK;
            case PARKOUR -> Material.BRICK;
            case ELIMINATION -> Material.TNT;
        };
    }

    private void setItem(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    private ItemStack createGuiItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiItem(Material material, Component name, List<Component> lore,
                                   java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        // 简化实现：返回基础物品，不使用 action
        // 点击处理由 TournamentGuiListener 统一处理
        return createGuiItem(material, name, lore);
    }

    private void fillBorder(Material borderMaterial) {
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, new ItemStack(borderMaterial));
            }
        }
    }
}
