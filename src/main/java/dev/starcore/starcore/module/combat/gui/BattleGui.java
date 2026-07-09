package dev.starcore.starcore.module.combat.gui;

import dev.starcore.starcore.module.combat.CombatService;
import dev.starcore.starcore.module.combat.model.Battlefield;
import dev.starcore.starcore.module.combat.model.Buff;
import dev.starcore.starcore.module.combat.model.CombatSession;
import dev.starcore.starcore.module.combat.model.PlayerCombatState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 战场GUI - 战场管理和战斗信息界面
 *
 * 提供完整的战场交互功能：
 * - 战场列表浏览
 * - 战场详细信息
 * - 战斗统计面板
 * - Buff状态显示
 * - 国家对抗视图
 */
public final class BattleGui {
    private final CombatService combatService;

    // GUI标题
    private static final String MAIN_MENU_TITLE = "§8§l[ §c战场中心 §8]";
    private static final String BATTLEFIELD_LIST_TITLE = "§8§l战场列表";
    private static final String BATTLEFIELD_INFO_TITLE = "§8§l战场详情";
    private static final String BATTLE_STATS_TITLE = "§8§l战斗统计";
    private static final String BUFF_LIST_TITLE = "§8§lBuff状态";
    private static final String NATION_BATTLE_TITLE = "§8§l国家战场";

    // 图标映射 - 使用不可变 Map
    private static final Map<String, Material> ICON_MAP = Map.ofEntries(
        Map.entry("sword", Material.DIAMOND_SWORD),
        Map.entry("shield", Material.SHIELD),
        Map.entry("heart", Material.HEART_OF_THE_SEA),
        Map.entry("skull", Material.PLAYER_HEAD),
        Map.entry("map", Material.MAP),
        Map.entry("clock", Material.CLOCK),
        Map.entry("trophy", Material.GOLDEN_APPLE),
        Map.entry("info", Material.BOOK),
        Map.entry("nation", Material.EMERALD),
        Map.entry("vs", Material.BLAZE_POWDER),
        Map.entry("buff", Material.BEETROOT_SOUP),
        Map.entry("debuff", Material.FERMENTED_SPIDER_EYE),
        Map.entry("active", Material.LIME_STAINED_GLASS_PANE),
        Map.entry("inactive", Material.RED_STAINED_GLASS_PANE)
    );

    public BattleGui(CombatService combatService) {
        this.combatService = combatService;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        BattleGuiHolder holder = new BattleGuiHolder("main");
        Inventory gui = Bukkit.createInventory(holder, 27, MAIN_MENU_TITLE);
        holder.setInventory(gui);
        fillMainMenu(gui, player);
        player.openInventory(gui);
    }

    /**
     * 填充主菜单
     */
    private void fillMainMenu(Inventory gui, Player player) {
        // 活跃战场数
        int activeCount = combatService.getActiveBattlefields().size();
        int totalCount = combatService.getAllBattlefields().size();

        // 活跃战斗会话数
        int activeSessions = combatService.getActiveSessions().size();

        // 玩家战斗状态
        PlayerCombatState state = combatService.getPlayerState(player.getUniqueId()).orElse(null);

        // 状态显示
        Material statusMaterial = (state != null && state.isInCombat())
            ? Material.RED_STAINED_GLASS_PANE
            : Material.GREEN_STAINED_GLASS_PANE;
        ItemStack statusItem = createItem(
            statusMaterial,
            (state != null && state.isInCombat()) ? "§c战斗中" : "§a和平状态",
            Arrays.asList(
                "§7战斗状态: " + ((state != null && state.isInCombat()) ? "§c战斗中" : "§a非战斗"),
                "§7击杀: §f" + (state != null ? state.getTotalKills() : 0),
                "§7死亡: §f" + (state != null ? state.getTotalDeaths() : 0)
            )
        );
        gui.setItem(4, statusItem);

        // 活跃战场
        ItemStack battlefieldItem = createItem(
            Material.MAP,
            "§b§l战场列表",
            Arrays.asList(
                "§7活跃战场: §f" + activeCount + "§7/§f" + totalCount,
                "§7活跃战斗: §f" + activeSessions,
                "",
                "§a点击查看"
            )
        );
        gui.setItem(10, battlefieldItem);

        // 战斗统计
        ItemStack statsItem = createItem(
            Material.BOOK,
            "§6§l战斗统计",
            Arrays.asList(
                "§7追踪玩家: §f" + combatService.getAllPlayerStates().size(),
                "§7活跃会话: §f" + activeSessions,
                "",
                "§a点击查看"
            )
        );
        gui.setItem(12, statsItem);

        // Buff状态
        Collection<Buff> buffs = combatService.getActiveBuffs(player.getUniqueId());
        ItemStack buffItem = createItem(
            Material.BEETROOT_SOUP,
            "§d§lBuff状态",
            Arrays.asList(
                "§7当前Buff: §f" + buffs.size(),
                buffs.isEmpty() ? "§7暂无Buff效果" : "",
                "",
                "§a点击查看详情"
            )
        );
        gui.setItem(14, buffItem);

        // 国家战场
        ItemStack nationItem = createItem(
            Material.EMERALD,
            "§a§l国家战场",
            Arrays.asList(
                "§7加入国家间的对抗",
                "§7与盟友并肩作战",
                "",
                "§a点击查看"
            )
        );
        gui.setItem(16, nationItem);

        // 关闭按钮
        gui.setItem(22, createItem(
            Material.BARRIER,
            "§c§l关闭",
            Collections.singletonList("§7关闭此菜单")
        ));
    }

    /**
     * 打开战场列表
     */
    public void openBattlefieldList(Player player) {
        Collection<Battlefield> battlefields = combatService.getAllBattlefields();
        int size = Math.min(54, Math.max(27, ((battlefields.size() / 9) + 1) * 9 + 9));

        BattleGuiHolder holder = new BattleGuiHolder("battlefield_list");
        Inventory gui = Bukkit.createInventory(holder, size, BATTLEFIELD_LIST_TITLE);
        holder.setInventory(gui);

        int slot = 0;
        for (Battlefield bf : battlefields) {
            if (slot >= size - 9) break;

            Material material = bf.isActive() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
            ChatColor statusColor = bf.isActive() ? ChatColor.GREEN : ChatColor.GRAY;

            List<String> lore = new ArrayList<>();
            lore.add("§7类型: §f" + bf.type());
            lore.add("§7参与者: §f" + bf.getParticipantCount());
            lore.add("§7击杀: §f" + bf.totalKills());
            lore.add("§7死亡: §f" + bf.totalDeaths());
            lore.add("§7持续: §f" + bf.getDurationSeconds() + "秒");
            lore.add("§7状态: " + statusColor + (bf.isActive() ? "进行中" : "已结束"));

            // 国家信息
            if (bf.getNation1() != null || bf.getNation2() != null) {
                lore.add("");
                lore.add("§6对阵: §f" +
                    (bf.getNation1() != null ? bf.getNation1().value() : "?") +
                    " §cVS §f" +
                    (bf.getNation2() != null ? bf.getNation2().value() : "?"));
            }

            ItemStack item = createItem(material, statusColor + "§l" + bf.name(), lore);
            gui.setItem(slot++, item);
        }

        // 返回按钮
        gui.setItem(size - 9, createItem(
            Material.ARROW,
            "§e§l返回主菜单",
            Collections.singletonList("§7点击返回")
        ));

        // 新建战场按钮（管理员）
        if (player.hasPermission("starcore.combat.admin")) {
            gui.setItem(size - 5, createItem(
                Material.BEACON,
                "§a§l新建战场",
                Arrays.asList("§7创建一个新的战场", "§7使用 /combat battlefield create")
            ));
        }

        player.openInventory(gui);
    }

    /**
     * 打开战场详细信息
     */
    public void openBattlefieldInfo(Player player, UUID battlefieldId) {
        Battlefield bf = combatService.getAllBattlefields().stream()
            .filter(b -> b.battlefieldId().equals(battlefieldId))
            .findFirst()
            .orElse(null);

        if (bf == null) {
            player.sendMessage(ChatColor.RED + "战场不存在: " + battlefieldId);
            return;
        }

        int size = 36;
        BattleGuiHolder holder = new BattleGuiHolder("battlefield_info");
        Inventory gui = Bukkit.createInventory(holder, size,
            BATTLEFIELD_INFO_TITLE + " - " + bf.name());
        holder.setInventory(gui);

        // 战场基本信息
        gui.setItem(0, createItem(
            Material.MAP,
            "§b§l" + bf.name(),
            Arrays.asList(
                "§7类型: §f" + bf.type(),
                "§7状态: " + (bf.isActive() ? "§a进行中" : "§c已结束"),
                "§7持续: §f" + bf.getDurationSeconds() + "秒",
                "§7位置: §f" + bf.center().getWorld().getName() +
                    " (" + bf.center().getBlockX() + ", " + bf.center().getBlockY() + ", " + bf.center().getBlockZ() + ")",
                "§7半径: §f" + bf.radius()
            )
        ));

        // 阵营1信息
        gui.setItem(11, createItem(
            Material.BLUE_BANNER,
            "§9§l蓝方",
            Arrays.asList(
                "§7国家: §f" + (bf.getNation1() != null ? bf.getNation1().value() : "未加入"),
                "§7阵营: §f" + (bf.getFaction1Nation() != null ? bf.getFaction1Nation().value() : "?"),
                "§7参与者: §f" + bf.getParticipantCount()
            )
        ));

        // VS
        gui.setItem(13, createItem(
            Material.BLAZE_POWDER,
            "§c§lVS",
            Collections.singletonList("§7国家对抗战")
        ));

        // 阵营2信息
        gui.setItem(15, createItem(
            Material.RED_BANNER,
            "§c§l红方",
            Arrays.asList(
                "§7国家: §f" + (bf.getNation2() != null ? bf.getNation2().value() : "未加入"),
                "§7阵营: §f" + (bf.getFaction2Nation() != null ? bf.getFaction2Nation().value() : "?"),
                "§7参与者: §f" + 0
            )
        ));

        // 战斗统计
        gui.setItem(22, createItem(
            Material.IRON_SWORD,
            "§e§l战斗统计",
            Arrays.asList(
                "§7总击杀: §f" + bf.totalKills(),
                "§7总死亡: §f" + bf.totalDeaths(),
                "§7总伤害: §f" + bf.totalDamage(),
                "§7战斗会话: §f" + bf.combatSessions().size()
            )
        ));

        // 参与者列表（简化显示前几个）
        int participantSlot = 27;
        int count = 0;
        for (UUID participantId : bf.participants()) {
            if (count >= 6 || participantSlot >= size - 9) break;
            String name = Bukkit.getPlayer(participantId) != null
                ? Bukkit.getPlayer(participantId).getName()
                : participantId.toString().substring(0, 8);
            gui.setItem(participantSlot++, createItem(
                Material.PLAYER_HEAD,
                "§f" + name,
                Arrays.asList("§7在线状态: §a在线", "§7击杀: §f0 §7死亡: §f0")
            ));
            count++;
        }

        // 返回按钮
        gui.setItem(size - 9, createItem(
            Material.ARROW,
            "§e§l返回",
            Collections.singletonList("§7返回战场列表")
        ));

        player.openInventory(gui);
    }

    /**
     * 打开战斗统计面板
     */
    public void openBattleStats(Player player, UUID targetPlayerId) {
        PlayerCombatState state = combatService.getPlayerState(targetPlayerId).orElse(null);

        int size = 27;
        BattleGuiHolder holder = new BattleGuiHolder("battle_stats");
        Inventory gui = Bukkit.createInventory(holder, size, BATTLE_STATS_TITLE);
        holder.setInventory(gui);

        String targetName = Bukkit.getPlayer(targetPlayerId) != null
            ? Bukkit.getPlayer(targetPlayerId).getName()
            : targetPlayerId.toString().substring(0, 8);

        // 战斗状态
        ItemStack combatItem = createItem(
            state != null && state.isInCombat() ? Material.DIAMOND_SWORD : Material.STICK,
            "§c§l战斗状态",
            Arrays.asList(
                "§7当前: " + (state != null && state.isInCombat() ? "§c战斗中" : "§a和平"),
                "§7持续: §f" + (state != null ? state.getCombatDurationSeconds() + "秒" : "0秒")
            )
        );
        gui.setItem(4, combatItem);

        // 伤害统计
        int totalDamage = state != null ? state.getTotalDamageDealt() : 0;
        int totalTaken = state != null ? state.getTotalDamageTaken() : 0;
        gui.setItem(11, createItem(
            Material.IRON_SWORD,
            "§e§l伤害统计",
            Arrays.asList(
                "§7输出伤害: §f" + totalDamage,
                "§7承受伤害: §f" + totalTaken,
                "§7伤害比: §f" + (totalTaken > 0 ? String.format("%.2f", (double) totalDamage / totalTaken) : "N/A")
            )
        ));

        // K/D统计
        int kills = state != null ? state.getTotalKills() : 0;
        int deaths = state != null ? state.getTotalDeaths() : 0;
        double kd = deaths > 0 ? (double) kills / deaths : kills;
        gui.setItem(13, createItem(
            Material.DIAMOND,
            "§a§l击杀统计",
            Arrays.asList(
                "§7击杀: §a" + kills,
                "§7死亡: §c" + deaths,
                "§7K/D比: §f" + String.format("%.2f", kd)
            )
        ));

        // 效率
        int total = kills + deaths;
        double winRate = total > 0 ? (double) kills / total * 100 : 0;
        gui.setItem(15, createItem(
            Material.GOLDEN_APPLE,
            "§6§l战斗效率",
            Arrays.asList(
                "§7总战斗: §f" + total,
                "§7胜率: §f" + String.format("%.1f%%", winRate),
                "§7玩家: §f" + targetName
            )
        ));

        // 返回
        gui.setItem(22, createItem(
            Material.ARROW,
            "§e§l返回",
            Collections.singletonList("§7返回主菜单")
        ));

        player.openInventory(gui);
    }

    /**
     * 打开Buff列表
     */
    public void openBuffList(Player player, UUID targetPlayerId) {
        Collection<Buff> buffs = combatService.getActiveBuffs(targetPlayerId);

        int size = Math.min(54, Math.max(27, ((buffs.size() / 9) + 2) * 9));
        BattleGuiHolder holder = new BattleGuiHolder("buff_list");
        Inventory gui = Bukkit.createInventory(holder, size, BUFF_LIST_TITLE);
        holder.setInventory(gui);

        int slot = 0;
        for (Buff buff : buffs) {
            if (slot >= size - 9) break;

            Material material = buff.type().isPositive()
                ? Material.BEETROOT_SOUP
                : Material.FERMENTED_SPIDER_EYE;

            String prefix = buff.type().isPositive() ? "§a" : "§c";
            ItemStack item = createItem(
                material,
                prefix + "§l" + buff.type().displayName(),
                Arrays.asList(
                    "§7效果: §f" + buff.type().formatDescription(buff.effectValue()),
                    "§7剩余: §f" + buff.remainingSeconds() + "秒",
                    "§7来源: §f" + (buff.sourceId() != null ? buff.sourceId().toString().substring(0, 8) : "战斗")
                )
            );
            gui.setItem(slot++, item);
        }

        if (buffs.isEmpty()) {
            gui.setItem(13, createItem(
                Material.GLASS_BOTTLE,
                "§7无Buff效果",
                Arrays.asList("§7当前没有活跃的Buff")
            ));
        }

        // 返回
        gui.setItem(size - 9, createItem(
            Material.ARROW,
            "§e§l返回",
            Collections.singletonList("§7返回主菜单")
        ));

        player.openInventory(gui);
    }

    /**
     * 打开国家战场列表
     */
    public void openNationBattleList(Player player) {
        Collection<Battlefield> battlefields = combatService.getAllBattlefields().stream()
            .filter(bf -> bf.getNation1() != null || bf.getNation2() != null)
            .collect(Collectors.toList());

        int size = Math.min(54, Math.max(27, ((battlefields.size() / 9) + 1) * 9 + 9));
        BattleGuiHolder holder = new BattleGuiHolder("nation_battle");
        Inventory gui = Bukkit.createInventory(holder, size, NATION_BATTLE_TITLE);
        holder.setInventory(gui);

        int slot = 0;
        for (Battlefield bf : battlefields) {
            if (slot >= size - 9) break;

            ItemStack item = createItem(
                Material.EMERALD,
                "§6§l" + bf.name(),
                Arrays.asList(
                    "§9蓝方: §f" + (bf.getNation1() != null ? bf.getNation1().value() : "待加入"),
                    "§c红方: §f" + (bf.getNation2() != null ? bf.getNation2().value() : "待加入"),
                    "§7参与者: §f" + bf.getParticipantCount(),
                    "§7状态: " + (bf.isActive() ? "§a进行中" : "§c已结束"),
                    "",
                    "§a点击查看详情"
                )
            );
            gui.setItem(slot++, item);
        }

        if (battlefields.isEmpty()) {
            gui.setItem(13, createItem(
                Material.BARRIER,
                "§7暂无国家战场",
                Arrays.asList("§7没有正在进行的国家战争")
            ));
        }

        gui.setItem(size - 9, createItem(
            Material.ARROW,
            "§e§l返回",
            Collections.singletonList("§7返回主菜单")
        ));

        player.openInventory(gui);
    }

    // ==================== 辅助方法 ====================

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
    private static class BattleGuiHolder implements InventoryHolder {
        private final String type;
        private UUID battlefieldId;
        private UUID playerId;
        private Inventory inventory;

        public BattleGuiHolder(String type) {
            this.type = type;
        }

        public BattleGuiHolder(String type, UUID battlefieldId) {
            this.type = type;
            this.battlefieldId = battlefieldId;
        }

        public BattleGuiHolder(String type, UUID battlefieldId, UUID playerId) {
            this.type = type;
            this.battlefieldId = battlefieldId;
            this.playerId = playerId;
        }

        /**
         * 设置关联的 Inventory（必须在 createInventory 后调用）
         */
        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String getType() {
            return type;
        }

        public UUID getBattlefieldId() {
            return battlefieldId;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * GUI类型枚举
     */
    public enum GuiType {
        MAIN_MENU,
        BATTLEFIELD_LIST,
        BATTLEFIELD_INFO,
        BATTLE_STATS,
        BUFF_LIST,
        NATION_BATTLE
    }
}
