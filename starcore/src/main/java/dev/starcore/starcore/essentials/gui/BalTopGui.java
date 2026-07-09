package dev.starcore.starcore.essentials.gui;

import dev.starcore.starcore.essentials.baltop.BalTopService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 财富排行榜 GUI
 */
public final class BalTopGui implements InventoryHolder {
    private static final int SIZE = 54;
    private static final int[] RANK_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int MAX_PER_PAGE = 28;

    private final Player player;
    private final BalTopService balTopService;
    private final EconomyService economyService;
    private final int page;

    private final Inventory inventory;

    public BalTopGui(Player player, BalTopService balTopService, EconomyService economyService, int page) {
        this.player = player;
        this.balTopService = balTopService;
        this.economyService = economyService;
        this.page = Math.max(1, page);

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text("财富排行榜 (第" + page + "页)", NamedTextColor.GOLD));
        buildMenu();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        // 标题
        inventory.setItem(4, createTitleItem());

        // 玩家自己的排名信息
        inventory.setItem(49, createPlayerRankItem());

        // 排行榜列表
        List<BalTopService.BalTopEntry> topPlayers = balTopService.getTopPlayers(100);
        int startIndex = (page - 1) * MAX_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_PER_PAGE, topPlayers.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = RANK_SLOTS[i - startIndex];
            BalTopService.BalTopEntry entry = topPlayers.get(i);
            inventory.setItem(slot, createRankItem(entry, i + 1));
        }

        // 导航
        int totalPages = (int) Math.ceil((double) topPlayers.size() / MAX_PER_PAGE);
        if (totalPages <= 0) totalPages = 1;

        if (page > 1) {
            inventory.setItem(45, createNavItem(Material.ARROW, "上一页", page - 1, NamedTextColor.GREEN));
        }
        if (page < totalPages) {
            inventory.setItem(53, createNavItem(Material.ARROW, "下一页", page + 1, NamedTextColor.GREEN));
        }

        // 关闭按钮
        inventory.setItem(48, createCloseItem());

        // 刷新按钮
        inventory.setItem(50, createRefreshItem());
    }

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("财富排行榜", NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 统计信息 ===", NamedTextColor.GOLD));
        lore.add(Component.text("上榜人数: " + balTopService.size(), NamedTextColor.GRAY));
        lore.add(Component.text("你的余额: " + formatBalance(economyService.getBalance(player.getUniqueId())), NamedTextColor.GREEN));

        balTopService.getPlayerRank(player.getUniqueId()).ifPresent(rank -> {
            lore.add(Component.text("你的排名: #" + rank, NamedTextColor.AQUA));
        });

        lore.add(Component.text(""));
        lore.add(Component.text("排行榜每分钟自动更新", NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerRankItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);

            skullMeta.displayName(Component.text("你的排名信息", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("=== 个人财富 ===", NamedTextColor.GOLD));
            lore.add(Component.text("当前余额: " + formatBalance(economyService.getBalance(player.getUniqueId())), NamedTextColor.GREEN));

            balTopService.getPlayerRank(player.getUniqueId()).ifPresent(rank -> {
                lore.add(Component.text("当前排名: #" + rank, NamedTextColor.AQUA));

                // 获取上一名
                if (rank > 1) {
                    List<BalTopService.BalTopEntry> topPlayers = balTopService.getTopPlayers(100);
                    if (rank <= topPlayers.size()) {
                        BalTopService.BalTopEntry above = topPlayers.get(rank - 2);
                        OfflinePlayer abovePlayer = Bukkit.getOfflinePlayer(above.playerId());
                        String aboveName = abovePlayer.getName() != null ? abovePlayer.getName() : "Unknown";
                        lore.add(Component.text("上一名: " + aboveName + " (" + formatBalance(above.balance()) + ")", NamedTextColor.YELLOW));
                    }
                }
            });

            if (balTopService.getPlayerRank(player.getUniqueId()).isEmpty()) {
                lore.add(Component.text("当前排名: 未上榜", NamedTextColor.RED));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("提示: 排行榜显示前100名", NamedTextColor.DARK_GRAY));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack createRankItem(BalTopService.BalTopEntry entry, int rank) {
        Material headMaterial = getRankMaterial(rank);
        ItemStack item = new ItemStack(headMaterial);

        if (item.getType() == Material.PLAYER_HEAD) {
            if (item.getItemMeta() instanceof SkullMeta skullMeta) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.playerId());
                skullMeta.setOwningPlayer(offlinePlayer);

                skullMeta.displayName(Component.text("#" + rank + " " + getRankEmoji(rank) + " " + (offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown"), getRankColor(rank)));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("余额: " + formatBalance(entry.balance()), NamedTextColor.GOLD));
                lore.add(Component.text(""));

                // 添加差距信息
                List<BalTopService.BalTopEntry> topPlayers = balTopService.getTopPlayers(100);
                if (rank < topPlayers.size()) {
                    BalTopService.BalTopEntry next = topPlayers.get(rank);
                    BigDecimal diff = entry.balance().subtract(next.balance());
                    lore.add(Component.text("与下名差距: " + formatBalance(diff), NamedTextColor.GRAY));
                }

                lore.add(Component.text(""));
                lore.add(Component.text("排名每分钟更新", NamedTextColor.DARK_GRAY));

                skullMeta.lore(lore);
                item.setItemMeta(skullMeta);
            }
        } else {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("#" + rank + " " + getRankEmoji(rank), getRankColor(rank)));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("余额: " + formatBalance(entry.balance()), NamedTextColor.GOLD));

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createNavItem(Material material, String name, int targetPage, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(name + " (第" + targetPage + "页)", color));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击前往第" + targetPage + "页", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("关闭", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击关闭菜单", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("刷新排行榜", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("点击手动刷新", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material getRankMaterial(int rank) {
        if (rank == 1) {
            return Material.PLAYER_HEAD; // 金榜
        } else if (rank == 2) {
            return Material.PLAYER_HEAD;
        } else if (rank == 3) {
            return Material.PLAYER_HEAD;
        }
        return Material.PLAYER_HEAD;
    }

    private String getRankEmoji(int rank) {
        return switch (rank) {
            case 1 -> "👑";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "";
        };
    }

    private NamedTextColor getRankColor(int rank) {
        return switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.WHITE;
        };
    }

    private String formatBalance(BigDecimal balance) {
        return String.format("%.2f", balance.doubleValue());
    }

    /**
     * 从槽位获取动作
     */
    public static BalTopAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 48 -> BalTopAction.CLOSE;
            case 50 -> BalTopAction.REFRESH;
            case 45 -> BalTopAction.PREV_PAGE;
            case 53 -> BalTopAction.NEXT_PAGE;
            default -> {
                if (isRankSlot(slot)) {
                    yield BalTopAction.VIEW_PLAYER;
                }
                yield BalTopAction.NONE;
            }
        };
    }

    private static boolean isRankSlot(int slot) {
        for (int rankSlot : RANK_SLOTS) {
            if (rankSlot == slot) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取排名信息（从槽位）
     */
    public BalTopService.BalTopEntry getEntryFromSlot(int slot) {
        int index = getRankSlotIndex(slot);
        if (index < 0) return null;

        List<BalTopService.BalTopEntry> topPlayers = balTopService.getTopPlayers(100);
        int globalIndex = (page - 1) * MAX_PER_PAGE + index;
        if (globalIndex >= 0 && globalIndex < topPlayers.size()) {
            return topPlayers.get(globalIndex);
        }
        return null;
    }

    private int getRankSlotIndex(int slot) {
        for (int i = 0; i < RANK_SLOTS.length; i++) {
            if (RANK_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 排行榜动作枚举
     */
    public enum BalTopAction {
        NONE,
        VIEW_PLAYER,
        REFRESH,
        PREV_PAGE,
        NEXT_PAGE,
        CLOSE
    }
}
