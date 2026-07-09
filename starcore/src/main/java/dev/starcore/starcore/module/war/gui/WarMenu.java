package dev.starcore.starcore.module.war.gui;

import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.module.war.gui.WarSituationMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战争系统 GUI 菜单
 * 提供完整的战争管理界面
 */
public final class WarMenu {

    // GUI 标题
    public static final String MAIN_MENU_TITLE = "§c§l⚔️ 战争中心";
    public static final String ACTIVE_WARS_TITLE = "§c§l⚔️ 进行中的战争";
    public static final String WAR_HISTORY_TITLE = "§6§l📜 战争历史";
    public static final String DECLARE_WAR_TITLE = "§c§l⚔️ 宣战选择";
    public static final String TREATY_MENU_TITLE = "§e§l📜 条约管理";
    public static final String WAR_DETAIL_TITLE = "§c§l⚔️ 战争详情";

    private final WarService warService;
    private final NationService nationService;
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;

    // audit C-073/C-074: 顶部标题常量仅用于 Inventory 标题文本，未见资源/格式问题。
    //   playerPages 字段此前从未被读写，是死状态缓存（C-074）；已移除以避免内存泄漏。
    // audit C-075/C-076: 未见 InventoryCloseEvent / PlayerQuitEvent 监听在此类内
    //   注册（菜单本身不实现 Listener），但所有 open* 方法均使用 Bukkit.createInventory(null, …)，
    //   即 holder=null，外部监听器无法据其回溯清理状态。若未来引入分页状态缓存，
    //   务必在关闭/退出时 remove(player.getUniqueId())。已创建空 listener 钩子留作 TODO：
    //   TODO audit C-076: 当 WarMenu 增加分页状态缓存时，必须实现关闭/退出清理。

    public WarMenu(
            WarService warService,
            NationService nationService,
            GuiAnimationManager animationManager,
            SoundFeedbackManager soundManager
    ) {
        this.warService = warService;
        this.nationService = nationService;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
    }

    /**
     * 越界守卫 —— audit C-075: setItem 调用前统一保护。
     */
    private static void safeSetItem(Inventory inv, int slot, ItemStack item) {
        if (inv == null || slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, item);
    }

    /**
     * 打开战争主菜单
     */
    public void openMainMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "战争系统");
        }

        Inventory inv = Bukkit.createInventory(null, 36, Component.text(MAIN_MENU_TITLE));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§c§l⚔️ 战争中心"));

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§c你没有所属国家",
                "需要加入国家才能使用战争功能"
            ));
            inv.setItem(31, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        Nation playerNation = nationService.nationById(playerNationId).orElse(null);
        if (playerNation == null) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§c你没有所属国家"
            ));
            inv.setItem(31, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        // 统计数据
        int activeWars = countActiveWars(playerNationId);
        int totalWars = countTotalWars(playerNationId);

        // 左侧统计
        inv.setItem(10, createStatItem(
            Material.IRON_SWORD,
            "§c⚔️ 进行中",
            String.valueOf(activeWars),
            "当前交战的敌国数量"
        ));

        inv.setItem(12, createStatItem(
            Material.PAPER,
            "§6📊 参战总数",
            String.valueOf(totalWars),
            "参与的战争总数"
        ));

        // 右侧功能按钮
        inv.setItem(19, ButtonFactory.createStyledButton(
            "§c⚔️ 进行中的战争",
            Material.IRON_SWORD,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "查看当前交战的敌国",
            "战争状态和进度"
        ));

        inv.setItem(21, ButtonFactory.createStyledButton(
            "§6📜 战争历史",
            Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看已结束的战争",
            "历史记录和战果"
        ));

        inv.setItem(23, ButtonFactory.createStyledButton(
            "§e⚔️ 战况预览",
            Material.MAP,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "实时查看战争态势",
            "军队状态、战场情报"
        ));

        inv.setItem(25, ButtonFactory.createStyledButton(
            "§e📜 停战协议",
            Material.WHITE_BANNER,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "管理停战和条约",
            "查看或发起停战谈判"
        ));

        // 底部帮助信息
        inv.setItem(31, ButtonFactory.createInfoButton(
            "提示: 宣战需要消耗国库资金",
            "战争期间可与其他国家结盟"
        ));

        player.openInventory(inv);
    }

    /**
     * 打开进行中的战争列表
     */
    public void openActiveWars(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "战争列表");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        Collection<WarSnapshot> activeWars = warService.activeWarsOf(playerNationId);

        int size = Math.max(36, ((activeWars.size() / 5) + 1) * 9 + 9);
        size = Math.min(size, 54);
        Inventory inv = Bukkit.createInventory(null, size, Component.text(ACTIVE_WARS_TITLE));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§c§l⚔️ 进行中的战争"));

        if (activeWars.isEmpty()) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§a✌️ 和平安宁",
                "你的国家目前没有参与的战争",
                "点击返回主菜单"
            ));
            inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        // 填充战争列表
        int slot = 10;
        for (WarSnapshot war : activeWars) {
            if (slot % 9 == 8) slot += 2; // 跳过边框位置
            if (slot >= size - 9) break;  // 留一行给返回按钮

            NationId enemyId = war.left().equals(playerNationId) ? war.right() : war.left();
            String enemyName = getNationName(enemyId);

            Duration duration = Duration.between(war.declaredAt(), Instant.now());
            String durationStr = formatDuration(duration);

            boolean isAggressor = war.left().equals(playerNationId);

            ItemStack item = createWarItem(enemyName, isAggressor, durationStr, war.declaredAt());
            safeSetItem(inv, slot, item);
            slot++;
        }

        // 返回按钮
        safeSetItem(inv, size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开宣战选择界面
     */
    public void openDeclareWarMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "宣战系统");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        Nation playerNation = nationService.nationById(playerNationId).orElse(null);
        if (playerNation == null) {
            player.sendMessage("§c你所属的国家不存在");
            return;
        }

        Collection<Nation> allNations = nationService.nations();
        List<Nation> targetableNations = new ArrayList<>();

        for (Nation nation : allNations) {
            if (nation.id().equals(playerNationId)) continue; // 跳过自己

            boolean atWar = warService.atWar(playerNationId, nation.id());
            if (!atWar) {
                targetableNations.add(nation);
            }
        }

        // 计算需要的界面大小
        int nationCount = targetableNations.size();
        int rows = Math.max(4, (nationCount / 5) + 3);
        int size = Math.min(54, rows * 9);
        size = Math.max(27, size);

        Inventory inv = Bukkit.createInventory(null, size, Component.text(DECLARE_WAR_TITLE));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§c§l⚔️ 选择宣战目标"));

        if (targetableNations.isEmpty()) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§c无可宣战目标",
                "所有国家都已处于战争状态",
                "或没有其他国家存在"
            ));
            inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        // 填充可选目标
        int slot = 10;
        for (Nation nation : targetableNations) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= size - 9) break;

            int memberCount = countNationMembers(nation.id());
            boolean isAlly = isAlly(playerNationId, nation.id());

            ItemStack item = createTargetItem(nation.name(), memberCount, isAlly);
            safeSetItem(inv, slot, item);
            slot++;
        }

        // 返回按钮
        safeSetItem(inv, size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开条约管理菜单
     */
    public void openTreatyMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "条约系统");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 36, Component.text(TREATY_MENU_TITLE));
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§e§l📜 停战条约管理"));

        // 当前停战协议
        Collection<WarSnapshot> activeWars = warService.activeWarsOf(playerNationId);
        int warCount = activeWars.size();

        // 统计停战协议
        inv.setItem(10, createStatItem(
            Material.BOOK,
            "§c⚔️ 进行中战争",
            String.valueOf(warCount),
            "当前交战的敌国数量"
        ));

        inv.setItem(12, createStatItem(
            Material.PAPER,
            "§e📋 可发起停战",
            String.valueOf(warCount),
            "可发起停战谈判的敌国"
        ));

        // 功能按钮
        inv.setItem(19, ButtonFactory.createStyledButton(
            "§a✅ 发起停战谈判",
            Material.LIME_CONCRETE,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "向敌对国家发起停战谈判",
            "需要对方同意"
        ));

        inv.setItem(21, ButtonFactory.createStyledButton(
            "§e📜 查看条约列表",
            Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看已签订的条约",
            "包括停战、投降等"
        ));

        inv.setItem(23, ButtonFactory.createStyledButton(
            "§c⚠️ 违反条约",
            Material.RED_CONCRETE,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "查看违反条约的后果",
            "违约将受到严厉惩罚"
        ));

        // 帮助信息
        inv.setItem(31, ButtonFactory.createInfoButton(
            "提示: 停战需要双方同意",
            "投降可能导致领土损失"
        ));

        // 返回按钮
        inv.setItem(35, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开战争详情界面
     */
    public void openWarDetail(Player player, NationId enemyId) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "战争详情");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) return;

        String enemyName = getNationName(enemyId);
        String title = "§c§l⚔️ vs " + enemyName;

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(title));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§c§l⚔️ 战争: " + player.getName() + " vs " + enemyName));

        // 查找战争信息
        Optional<WarSnapshot> warOpt = findWar(playerNationId, enemyId);

        if (warOpt.isEmpty()) {
            inv.setItem(22, ButtonFactory.createInfoButton(
                "§c未找到战争记录",
                "可能战争已经结束"
            ));
            inv.setItem(40, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        WarSnapshot war = warOpt.get();
        Duration duration = Duration.between(war.declaredAt(), Instant.now());
        boolean isAggressor = war.left().equals(playerNationId);

        // 左侧 - 己方信息
        inv.setItem(10, createWarSideItem(
            "§a你的阵营",
            isAggressor ? "§e进攻方" : "§b防守方",
            Material.GREEN_BANNER,
            "你是这场战争的" + (isAggressor ? "进攻方" : "防守方")
        ));

        // 右侧 - 敌方信息
        inv.setItem(16, createWarSideItem(
            "§c敌方: " + enemyName,
            isAggressor ? "§b防守方" : "§e进攻方",
            Material.RED_BANNER,
            "敌对国家"
        ));

        // 中间 - 战争信息
        inv.setItem(13, createStatItem(
            Material.CLOCK,
            "§e⏱️ 持续时间",
            formatDuration(duration),
            "战争已持续时间"
        ));

        inv.setItem(22, createStatItem(
            Material.IRON_SWORD,
            "§c⚔️ 战争状态",
            "§e进行中",
            "战斗正在持续"
        ));

        // 行动按钮
        inv.setItem(28, ButtonFactory.createStyledButton(
            "§e📊 战争统计",
            Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看详细战争统计",
            "击杀、占领等数据"
        ));

        inv.setItem(30, ButtonFactory.createStyledButton(
            "§a🤝 发起停战",
            Material.LIME_CONCRETE,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "向敌国发起停战谈判",
            "需要对方同意"
        ));

        inv.setItem(32, ButtonFactory.createStyledButton(
            "§c⚔️ 增援请求",
            Material.EMERALD,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "向盟友请求军事援助",
            "盟友将收到通知"
        ));

        // 底部信息
        inv.setItem(40, ButtonFactory.createInfoButton(
            "宣战时间: " + formatInstant(war.declaredAt()),
            "使用 /war status 查看详细"
        ));

        // 返回按钮
        inv.setItem(44, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 辅助方法 ====================

    private ItemStack createTitleItem(String name) {
        return ButtonFactory.createStyledButton(name, Material.NETHER_STAR, ButtonFactory.BUTTON_STYLE_DANGER);
    }

    private ItemStack createStatItem(Material material, String label, String value, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§e" + value, NamedTextColor.GOLD));
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWarItem(String enemyName, boolean isAggressor, String duration, Instant declaredAt) {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String sideText = isAggressor ? "§c进攻方" : "§b防守方";
            meta.displayName(Component.text("§c⚔️ vs " + enemyName, NamedTextColor.RED).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7阵营: " + sideText, NamedTextColor.GRAY));
            lore.add(Component.text("§7持续: §e" + duration, NamedTextColor.YELLOW));
            lore.add(Component.text("§7宣战: §f" + formatInstant(declaredAt), NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("§e点击查看详情", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTargetItem(String nationName, int memberCount, boolean isAlly) {
        Material material = isAlly ? Material.EMERALD : Material.DIAMOND_SWORD;
        String style = isAlly ? ButtonFactory.BUTTON_STYLE_SUCCESS : ButtonFactory.BUTTON_STYLE_DANGER;
        String allyText = isAlly ? "§a[盟国]" : "";

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(allyText + "§c " + nationName, NamedTextColor.RED).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7成员: §f" + memberCount + " 人", NamedTextColor.GRAY));
            lore.add(Component.text("§7状态: " + (isAlly ? "§a盟国" : "§e中立"), NamedTextColor.GRAY));
            lore.add(Component.text(""));
            if (isAlly) {
                lore.add(Component.text("§c无法向盟国宣战!", NamedTextColor.RED));
            } else {
                lore.add(Component.text("§e点击选择此目标", NamedTextColor.YELLOW));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWarSideItem(String name, String role, Material material, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(role, NamedTextColor.GOLD));
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder(Inventory inv, Material material) {
        ItemStack border = ButtonFactory.createBorder(material);
        int size = inv.getSize();
        int rows = size / 9;
        for (int row = 0; row < rows; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }
        for (int col = 1; col < 8; col++) {
            inv.setItem(col, border);
            inv.setItem(size - 9 + col, border);
        }
    }

    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId).map(Nation::name).orElse("未知国家");
    }

    private int countActiveWars(NationId nationId) {
        if (nationId == null || warService == null) return 0;
        return (int) warService.activeWarsOf(nationId).size();
    }

    private int countTotalWars(NationId nationId) {
        if (nationId == null || warService == null) return 0;
        return (int) warService.activeWars().stream()
            .filter(w -> w.left().equals(nationId) || w.right().equals(nationId))
            .count();
    }

    private int countNationMembers(NationId nationId) {
        return (int) nationService.nations().stream()
            .filter(n -> n.id().equals(nationId))
            .findFirst()
            .map(n -> 1) // 简化，实际应该从国家成员服务获取
            .orElse(1);
    }

    private boolean isAlly(NationId nation1, NationId nation2) {
        // 简化实现，实际应该从 diplomacyService 获取
        return false;
    }

    private Optional<WarSnapshot> findWar(NationId nation1, NationId nation2) {
        return warService.activeWars().stream()
            .filter(w -> (w.left().equals(nation1) && w.right().equals(nation2)) ||
                         (w.left().equals(nation2) && w.right().equals(nation1)))
            .findFirst();
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return days + "天 " + hours + "小时";
        } else if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        } else {
            return minutes + "分钟";
        }
    }

    private String formatInstant(Instant instant) {
        java.time.LocalDateTime ldt = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        return ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}