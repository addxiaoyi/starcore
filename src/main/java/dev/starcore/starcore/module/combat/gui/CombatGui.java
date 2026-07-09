package dev.starcore.starcore.module.combat.gui;

import dev.starcore.starcore.module.combat.CombatService;
import dev.starcore.starcore.module.combat.model.Battlefield;
import dev.starcore.starcore.module.combat.model.CombatSession;
import dev.starcore.starcore.module.combat.model.PlayerCombatState;
import dev.starcore.starcore.module.combat.storage.CombatStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 战斗系统GUI
 */
public final class CombatGui {
    private final CombatService combatService;

    // GUI标题
    private static final String MAIN_MENU_TITLE = "§8§l[ §6战斗系统 §8]";
    private static final String BATTLEFIELD_LIST_TITLE = "§8§l战场列表";
    private static final String COMBAT_HISTORY_TITLE = "§8§l战斗历史";
    private static final String COMBAT_STATS_TITLE = "§8§l战斗统计";

    // 图标映射 - 使用不可变 Map
    private static final Map<String, Material> ICON_MAP = Map.of(
        "sword", Material.DIAMOND_SWORD,
        "shield", Material.SHIELD,
        "heart", Material.HEART_OF_THE_SEA,
        "skull", Material.PLAYER_HEAD,
        "map", Material.MAP,
        "clock", Material.CLOCK,
        "trophy", Material.GOLDEN_APPLE,
        "info", Material.BOOK
    );

    public CombatGui(CombatService combatService) {
        this.combatService = combatService;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        Inventory gui = createGuiInventory("main", 27, MAIN_MENU_TITLE);
        fillMainMenu(gui, player);
        player.openInventory(gui);
    }

    /**
     * 填充主菜单
     */
    private void fillMainMenu(Inventory gui, Player player) {
        UUID playerId = player.getUniqueId();
        PlayerCombatState state = combatService.getPlayerState(playerId).orElse(null);

        // 当前战斗状态
        ItemStack statusItem = createItem(
            state != null && state.isInCombat() ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE,
            (state != null && state.isInCombat()) ? "§c处于战斗中" : "§a非战斗状态",
            Arrays.asList(
                "§7状态: " + (state != null && state.isInCombat() ? "§c战斗中" : "§a和平"),
                "§7击杀: §f" + (state != null ? state.getTotalKills() : 0),
                "§7死亡: §f" + (state != null ? state.getTotalDeaths() : 0)
            )
        );
        gui.setItem(4, statusItem);

        // 查看战斗状态
        ItemStack statusGuiItem = createItem(
            Material.PAPER,
            "§e§l查看战斗状态",
            Arrays.asList("§7查看你的详细战斗状态", "§7包括伤害、击杀、死亡等统计")
        );
        gui.setItem(10, statusGuiItem);

        // 活跃战斗列表
        int activeSessions = combatService.getActiveSessions().size();
        ItemStack combatListItem = createItem(
            Material.IRON_SWORD,
            "§c§l活跃战斗",
            Arrays.asList(
                "§7当前活跃战斗: §f" + activeSessions,
                "",
                "§a点击查看详情"
            )
        );
        gui.setItem(12, combatListItem);

        // 战场列表
        int battlefieldCount = combatService.getAllBattlefields().size();
        ItemStack battlefieldItem = createItem(
            Material.MAP,
            "§b§l战场管理",
            Arrays.asList(
                "§7战场数量: §f" + battlefieldCount,
                "",
                "§a点击查看战场列表"
            )
        );
        gui.setItem(14, battlefieldItem);

        // 战斗统计
        CombatService.CombatStats stats = combatService.getStats();
        ItemStack statsItem = createItem(
            Material.BOOK,
            "§6§l系统统计",
            Arrays.asList(
                "§7追踪玩家: §f" + stats.totalPlayers(),
                "§7活跃会话: §f" + stats.activeSessions(),
                "§7战场: §f" + stats.battlefields(),
                "",
                "§a点击查看更多"
            )
        );
        gui.setItem(16, statsItem);

        // 关闭按钮
        ItemStack closeItem = createItem(
            Material.BARRIER,
            "§c§l关闭",
            Collections.singletonList("§7关闭此菜单")
        );
        gui.setItem(22, closeItem);
    }

    /**
     * 打开战场列表
     */
    public void openBattlefieldList(Player player) {
        Collection<Battlefield> battlefields = combatService.getAllBattlefields();
        int size = Math.min(54, ((battlefields.size() / 9) + 1) * 9 + 9);
        Inventory gui = createGuiInventory("battlefield_list", size, BATTLEFIELD_LIST_TITLE);

        int slot = 0;
        for (Battlefield bf : battlefields) {
            if (slot >= size - 9) break;

            Material material = bf.isActive() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
            ChatColor statusColor = bf.isActive() ? ChatColor.GREEN : ChatColor.GRAY;

            ItemStack item = createItem(
                material,
                statusColor + "§l" + bf.name(),
                Arrays.asList(
                    "§7类型: §f" + bf.type(),
                    "§7参与者: §f" + bf.getParticipantCount(),
                    "§7击杀: §f" + bf.totalKills(),
                    "§7死亡: §f" + bf.totalDeaths(),
                    "§7状态: " + statusColor + (bf.isActive() ? "活跃" : "已结束"),
                    "",
                    "§7位置: §f" + bf.center().getWorld().getName()
                )
            );
            gui.setItem(slot++, item);
        }

        // 返回按钮
        gui.setItem(size - 9, createItem(
            Material.ARROW,
            "§e§l返回主菜单",
            Collections.singletonList("§7点击返回")
        ));

        player.openInventory(gui);
    }

    /**
     * 打开战斗历史
     */
    public void openCombatHistory(Player player, UUID targetPlayerId, int page) {
        int size = 54;
        Inventory gui = createGuiInventory("history", size, COMBAT_HISTORY_TITLE);

        // 从存储加载战斗历史
        List<CombatStorage.CombatHistoryRecord> history =
            combatService.getCombatHistory(targetPlayerId, 45);

        int itemsPerPage = size - 9;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, history.size());

        int slot = 0;
        if (!history.isEmpty() && startIndex < history.size()) {
            for (int i = startIndex; i < endIndex; i++) {
                CombatStorage.CombatHistoryRecord record = history.get(i);

                Material mat = record.killerId() != null ? Material.DIAMOND_SWORD : Material.IRON_SWORD;
                String title = record.killerId() != null ? "§c击杀记录" : "§e战斗记录";

                List<String> lore = new ArrayList<>();
                lore.add("§7时间: §f" + formatTime(record.createdAt()));
                lore.add("§7世界: §f" + record.world());
                lore.add("§7位置: §f" + (int) record.locationX() + ", " + (int) record.locationY() + ", " + (int) record.locationZ());
                if (record.killerId() != null) {
                    lore.add("§7类型: §c击杀");
                    lore.add("§7击杀者: §f" + getPlayerName(record.killerId()));
                    lore.add("§7受害者: §f" + getPlayerName(record.victimId()));
                } else {
                    lore.add("§7类型: §e普通战斗");
                    lore.add("§7持续: §f" + record.durationSeconds() + "秒");
                }
                lore.add("§7伤害: §c" + record.attackerDamage() + " §7/ §a" + record.defenderDamage());
                lore.add("§7原因: §f" + (record.endReason() != null ? record.endReason() : "N/A"));

                ItemStack item = createItem(mat, title, lore);
                gui.setItem(slot++, item);
            }
        } else {
            // 无历史记录提示
            ItemStack placeholder = createItem(
                Material.BARRIER,
                "§7暂无战斗历史",
                Arrays.asList("§7还没有任何战斗记录", "§7参与战斗后会显示在这里")
            );
            for (int i = 0; i < itemsPerPage; i++) {
                gui.setItem(i, placeholder);
            }
        }

        // 分页按钮
        int totalPages = (int) Math.ceil((double) history.size() / itemsPerPage);
        gui.setItem(size - 9, createItem(
            page > 0 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
            page > 0 ? "§e§l上一页" : "§7已经是第一页",
            Collections.singletonList("§7第 " + (page + 1) + " / " + Math.max(1, totalPages) + " 页")
        ));

        gui.setItem(size - 5, createItem(
            Material.BOOK,
            "§6§l战斗历史",
            Arrays.asList("§7总记录: §f" + history.size(), "§7当前页: §f" + (page + 1))
        ));

        gui.setItem(size - 1, createItem(
            page < totalPages - 1 ? Material.ARROW : Material.GRAY_STAINED_GLASS_PANE,
            page < totalPages - 1 ? "§e§l下一页" : "§7已经是最后一页",
            Collections.singletonList("§7第 " + (page + 1) + " / " + Math.max(1, totalPages) + " 页")
        ));

        // 返回按钮
        gui.setItem(size - 8, createItem(
            Material.ARROW,
            "§c§l返回",
            Collections.singletonList("§7点击返回主菜单")
        ));

        player.openInventory(gui);
    }

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /**
     * 格式化时间戳
     */
    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "N/A";
        return DATETIME_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    /**
     * 获取玩家名称
     */
    private String getPlayerName(UUID playerId) {
        if (playerId == null) return "N/A";
        Player player = Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : playerId.toString().substring(0, 8);
    }

    /**
     * 打开玩家战斗统计
     */
    public void openPlayerStats(Player player, UUID targetPlayerId) {
        Inventory gui = createGuiInventory("stats", 27, COMBAT_STATS_TITLE);

        PlayerCombatState state = combatService.getPlayerState(targetPlayerId).orElse(null);

        // 战斗状态
        ItemStack combatItem = createItem(
            state != null && state.isInCombat() ? Material.DIAMOND_SWORD : Material.STICK,
            "§c§l战斗状态",
            Arrays.asList(
                "§7当前状态: " + (state != null && state.isInCombat() ? "§c战斗中" : "§a和平"),
                "§7持续时间: §f" + (state != null ? state.getCombatDurationSeconds() + "秒" : "0秒")
            )
        );
        gui.setItem(4, combatItem);

        // 伤害统计
        int totalDamage = state != null ? state.getTotalDamageDealt() : 0;
        int totalTaken = state != null ? state.getTotalDamageTaken() : 0;
        ItemStack damageItem = createItem(
            Material.IRON_SWORD,
            "§e§l伤害统计",
            Arrays.asList(
                "§7总伤害输出: §f" + totalDamage,
                "§7总承受伤害: §f" + totalTaken,
                "§7伤害比: §f" + (totalTaken > 0 ? String.format("%.2f", (double) totalDamage / totalTaken) : "N/A")
            )
        );
        gui.setItem(11, damageItem);

        // 击杀统计
        int kills = state != null ? state.getTotalKills() : 0;
        int deaths = state != null ? state.getTotalDeaths() : 0;
        ItemStack killItem = createItem(
            Material.DIAMOND,
            "§a§l击杀统计",
            Arrays.asList(
                "§7击杀: §a" + kills,
                "§7死亡: §c" + deaths,
                "§7K/D: §f" + (deaths > 0 ? String.format("%.2f", (double) kills / deaths) : kills)
            )
        );
        gui.setItem(13, killItem);

        // 效率
        int kda = kills + (state != null ? state.getTotalDeaths() : 0) > 0 ? kills : 0;
        ItemStack efficiencyItem = createItem(
            Material.GOLDEN_APPLE,
            "§6§l战斗效率",
            Arrays.asList(
                "§7总参与: §f" + (state != null ? (state.getTotalKills() + state.getTotalDeaths()) : 0),
                "§7胜率: §f" + (state != null && (kills + deaths) > 0
                    ? String.format("%.1f%%", (double) kills / (kills + deaths) * 100)
                    : "N/A")
            )
        );
        gui.setItem(15, killItem);

        // 返回
        gui.setItem(22, createItem(
            Material.ARROW,
            "§e§l返回",
            Collections.singletonList("§7点击返回主菜单")
        ));

        player.openInventory(gui);
    }

    /**
     * 创建物品
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 获取材质
     */
    public static Material getMaterial(String key) {
        return ICON_MAP.getOrDefault(key.toLowerCase(), Material.PAPER);
    }

    /**
     * GUI持有者
     */
    private static class CombatGuiHolder implements InventoryHolder {
        private final String type;
        private Inventory inventory;

        public CombatGuiHolder(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        @Override
        public Inventory getInventory() {
            // 返回关联的Inventory，如果尚未关联则返回自身代理的空Inventory
            if (inventory != null) {
                return inventory;
            }
            // 当Bukkit调用此时，inventory应该已经被Bukkit设置
            throw new IllegalStateException("Inventory not initialized for holder type: " + type);
        }

        /**
         * 由Bukkit创建Inventory后自动调用，设置关联的Inventory
         */
        public void linkInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    /**
     * 创建GUI Inventory的工厂方法，确保Holder正确关联
     */
    private Inventory createGuiInventory(String holderType, int size, String title) {
        CombatGuiHolder holder = new CombatGuiHolder(holderType);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.linkInventory(inventory);
        return inventory;
    }

    /**
     * GUI类型枚举
     */
    public enum GuiType {
        MAIN_MENU,
        BATTLEFIELD_LIST,
        COMBAT_HISTORY,
        PLAYER_STATS,
        SESSION_DETAIL
    }

    /**
     * GUI事件处理器
     */
    public static class CombatGuiHandler {
        private final CombatGui gui;

        public CombatGuiHandler(CombatGui gui) {
            this.gui = gui;
        }

        public void handleClick(Player player, int slot, ItemStack item) {
            if (item == null || !item.hasItemMeta()) return;

            String title = player.getOpenInventory().getTitle();

            if (title.equals(MAIN_MENU_TITLE)) {
                handleMainMenuClick(player, slot, item);
            } else if (title.equals(BATTLEFIELD_LIST_TITLE)) {
                handleBattlefieldListClick(player, slot, item);
            }
        }

        private void handleMainMenuClick(Player player, int slot, ItemStack item) {
            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());

            switch (slot) {
                case 10 -> gui.openPlayerStats(player, player.getUniqueId());
                case 12 -> player.sendMessage("§e使用 /combat list 查看活跃战斗");
                case 14 -> gui.openBattlefieldList(player);
                case 16 -> player.sendMessage("§e使用 /combat stats 查看系统统计");
                case 22 -> player.closeInventory();
            }
        }

        private void handleBattlefieldListClick(Player player, int slot, ItemStack item) {
            int size = player.getOpenInventory().getTopInventory().getSize();

            if (slot == size - 9) {
                gui.openMainMenu(player);
            }
        }
    }
}
