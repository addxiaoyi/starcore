package dev.starcore.starcore.module.diplomacy.gui;

import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.diplomacy.alliance.gui.AllianceMenu;
import dev.starcore.starcore.module.diplomacy.alliance.gui.AllianceMenuListener;
import dev.starcore.starcore.module.diplomacy.military.gui.MilitaryAllianceMenu;
import dev.starcore.starcore.module.diplomacy.military.gui.MilitaryAllianceMenuListener;
import dev.starcore.starcore.module.diplomacy.DiplomacyModule;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelationSnapshot;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.network.NetworkVisualizationService;
import dev.starcore.starcore.module.diplomacy.gui.DiplomacyNetworkMenu;
import dev.starcore.starcore.module.war.gui.WarMenu;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 外交中心 GUI 菜单
 * 提供完整的外交管理界面
 */
public class DiplomacyMenu {

    public static final String MENU_TITLE = "§6§l外交中心";
    public static final int MENU_SIZE = 36;

    // 外部服务引用
    private final DiplomacyModule diplomacyModule;
    private final NationService nationService;
    private final UUID playerId;

    // 子菜单引用
    private AllianceMenu allianceMenu;
    private MilitaryAllianceMenu militaryMenu;
    private WarMenu warMenu;
    private DiplomacyNetworkMenu networkMenu;

    // 玩家状态跟踪
    private final Map<UUID, String> playerStates = new java.util.concurrent.ConcurrentHashMap<>();

    public DiplomacyMenu(DiplomacyModule diplomacyModule, NationService nationService, UUID playerId) {
        this.diplomacyModule = diplomacyModule;
        this.nationService = nationService;
        this.playerId = playerId;
    }

    /**
     * 设置 AllianceMenu 实例
     */
    public void setAllianceMenu(AllianceMenu allianceMenu) {
        this.allianceMenu = allianceMenu;
    }

    /**
     * 设置 MilitaryAllianceMenu 实例
     */
    public void setMilitaryMenu(MilitaryAllianceMenu militaryMenu) {
        this.militaryMenu = militaryMenu;
    }

    /**
     * 设置 WarMenu 实例
     */
    public void setWarMenu(WarMenu warMenu) {
        this.warMenu = warMenu;
    }

    /**
     * 设置 NetworkMenu 实例
     */
    public void setNetworkMenu(DiplomacyNetworkMenu networkMenu) {
        this.networkMenu = networkMenu;
    }

    /**
     * 打开主菜单
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text(MENU_TITLE));

        fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        // 检查玩家是否有国家
        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isEmpty()) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§c你没有所属国家",
                "需要加入国家才能使用外交功能"
            ));
            inv.setItem(31, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        // 统计数据
        int allyCount = countAllies(nationId);
        int warCount = countActiveWars(nationId);
        int pendingCount = diplomacyModule.getPendingInvites(nationId).size();

        // 标题行
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§6§l外交中心",
            Material.NETHER_STAR,
            ButtonFactory.BUTTON_STYLE_PRIMARY
        ));

        // 主功能按钮
        inv.setItem(10, ButtonFactory.createStyledButton(
            "§a🌐 联盟管理",
            Material.EMERALD,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "§7当前盟国: §a" + allyCount,
            "管理外交联盟关系"
        ));

        inv.setItem(12, ButtonFactory.createStyledButton(
            "§b⚔️ 军事联盟",
            Material.IRON_CHESTPLATE,
            ButtonFactory.BUTTON_STYLE_INFO,
            "§7军事同盟条约管理",
            "防御加成和联合防御"
        ));

        inv.setItem(14, ButtonFactory.createStyledButton(
            "§c⚔️ 战争管理",
            Material.DIAMOND_SWORD,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "§7当前战争: §c" + warCount,
            "宣战、停战和条约"
        ));

        inv.setItem(16, ButtonFactory.createStyledButton(
            "§e📊 外交关系",
            Material.PAPER,
            ButtonFactory.BUTTON_STYLE_INFO,
            "§7查看所有外交关系",
            "联盟、敌对、中立状态"
        ));

        inv.setItem(24, ButtonFactory.createStyledButton(
            "§6🌐 关系网络",
            Material.MAP,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "§7可视化外交关系网络",
            "查看国家间的关系图谱"
        ));

        // 统计面板
        inv.setItem(19, ButtonFactory.createStatButton(
            Material.EMERALD,
            "§a盟友",
            String.valueOf(allyCount)
        ));

        inv.setItem(21, ButtonFactory.createStatButton(
            Material.REDSTONE,
            "§c敌对",
            String.valueOf(warCount)
        ));

        inv.setItem(23, ButtonFactory.createStatButton(
            Material.BEACON,
            "§e待处理",
            String.valueOf(pendingCount)
        ));

        // 快捷操作
        inv.setItem(28, ButtonFactory.createStyledButton(
            "§a➕ 发起联盟",
            Material.LAPIS_LAZULI,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "向其他国家发送联盟邀请"
        ));

        inv.setItem(30, ButtonFactory.createStyledButton(
            "§c⚔️ 发起战争",
            Material.IRON_SWORD,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "向敌对国家宣战",
            "需要消耗国库资金"
        ));

        // 帮助信息
        inv.setItem(31, ButtonFactory.createInfoButton(
            "提示: 使用 /diplomacy 查看更多命令",
            "外交操作有冷却时间限制"
        ));

        // 国家信息
        String nationInfo = "§7国家: §f" + nation.name();
        ItemStack infoItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(nationInfo, NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7成员数: §f" + nation.members().size(), NamedTextColor.GRAY));
            lore.add(Component.text("§7外交关系: §a" + allyCount + " §c" + warCount, NamedTextColor.GRAY));
            meta.lore(lore);
            infoItem.setItemMeta(meta);
        }
        inv.setItem(35, infoItem);

        player.openInventory(inv);
    }

    /**
     * 打开外交关系总览菜单
     */
    public void openRelationsView(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("§e§l外交关系总览"));

        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§e§l📊 外交关系总览",
            Material.PAPER,
            ButtonFactory.BUTTON_STYLE_INFO
        ));

        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isEmpty()) {
            player.closeInventory();
            return;
        }

        Nation myNation = nationOpt.get();
        NationId myNationId = myNation.id();

        // 获取所有外交关系
        Collection<DiplomacyRelationSnapshot> relations = diplomacyModule.relationsOf(myNationId);

        // 按关系类型分类
        List<DiplomacyRelationSnapshot> allies = new ArrayList<>();
        List<DiplomacyRelationSnapshot> enemies = new ArrayList<>();
        List<DiplomacyRelationSnapshot> neutral = new ArrayList<>();

        for (DiplomacyRelationSnapshot snapshot : relations) {
            String relName = snapshot.relation().name();
            if ("ALLIED".equals(relName)) {
                allies.add(snapshot);
            } else if ("WAR".equals(relName)) {
                enemies.add(snapshot);
            } else {
                neutral.add(snapshot);
            }
        }

        int slot = 10;

        // 盟友列表
        if (!allies.isEmpty()) {
            inv.setItem(slot++, createRelationHeader("§a盟友 (" + allies.size() + ")", Material.EMERALD));
            for (DiplomacyRelationSnapshot snapshot : allies) {
                if (slot > 43) break;
                inv.setItem(slot++, createRelationItem(snapshot, Material.EMERALD, "§a"));
            }
        }

        // 敌对列表
        if (!enemies.isEmpty()) {
            if (slot > 8 && slot % 9 == 0) slot += 2;
            inv.setItem(slot++, createRelationHeader("§c敌对 (" + enemies.size() + ")", Material.REDSTONE));
            for (DiplomacyRelationSnapshot snapshot : enemies) {
                if (slot > 43) break;
                inv.setItem(slot++, createRelationItem(snapshot, Material.REDSTONE, "§c"));
            }
        }

        // 中立国家
        List<Nation> allNations = new ArrayList<>(nationService.nations());
        allNations.removeIf(n -> n.id().equals(myNationId));
        allNations.removeIf(n -> relations.stream().anyMatch(r -> r.target().equals(n.id())));

        if (!allNations.isEmpty()) {
            if (slot > 8 && slot % 9 == 0) slot += 2;
            inv.setItem(slot++, createRelationHeader("§7中立 (" + allNations.size() + ")", Material.PAPER));
            for (Nation n : allNations) {
                if (slot > 43) break;
                inv.setItem(slot++, createNeutralNationItem(n));
            }
        }

        // 返回按钮
        inv.setItem(49, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 辅助方法 ====================

    private ItemStack createRelationHeader(String name, Material material) {
        return ButtonFactory.createStyledButton(name, material, ButtonFactory.BUTTON_STYLE_INFO);
    }

    private ItemStack createRelationItem(DiplomacyRelationSnapshot snapshot, Material material, String color) {
        Optional<Nation> otherOpt = nationService.nationById(snapshot.target());
        String nationName = otherOpt.map(Nation::name).orElse("未知国家");

        String relationName = switch (snapshot.relation().name()) {
            case "ALLIED" -> "§a联盟";
            case "WAR" -> "§c战争";
            case "CEASE_FIRE" -> "§e停战";
            case "NON_AGGRESSION" -> "§b互不侵犯";
            default -> "§7中立";
        };

        return ButtonFactory.createStyledButton(
            color + nationName,
            material,
            ButtonFactory.BUTTON_STYLE_INFO,
            "§7关系: " + relationName,
            "",
            "§e点击查看详情"
        );
    }

    private ItemStack createNeutralNationItem(Nation nation) {
        return ButtonFactory.createStyledButton(
            "§7" + nation.name(),
            Material.PAPER,
            ButtonFactory.BUTTON_STYLE_SECONDARY,
            "§7中立国家",
            "§e点击发起外交"
        );
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

    private int countAllies(NationId nationId) {
        return (int) diplomacyModule.relationsOf(nationId).stream()
            .filter(r -> "ALLIED".equals(r.relation().name()))
            .count();
    }

    private int countActiveWars(NationId nationId) {
        return (int) diplomacyModule.relationsOf(nationId).stream()
            .filter(r -> "WAR".equals(r.relation().name()))
            .count();
    }

    /**
     * 处理玩家点击
     */
    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你需要先加入一个国家才能使用外交功能");
            return;
        }

        String name = getItemName(item);
        Material material = item.getType();

        // 处理返回按钮
        if (name.contains("返回")) {
            open(player);
            return;
        }

        // 处理边框点击
        if (material == Material.ORANGE_STAINED_GLASS_PANE ||
            material == Material.YELLOW_STAINED_GLASS_PANE ||
            material == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        // 根据槽位和材质处理点击
        switch (slot) {
            case 10 -> {
                // 联盟管理
                if (allianceMenu != null) {
                    allianceMenu.openMainMenu(player);
                } else {
                    openAllianceManagement(player, nationOpt.get());
                }
            }
            case 12 -> {
                // 军事联盟
                if (militaryMenu != null) {
                    militaryMenu.openMainMenu(player);
                } else {
                    player.sendMessage("§c军事联盟功能暂不可用");
                }
            }
            case 14 -> {
                // 战争管理
                if (warMenu != null) {
                    warMenu.openMainMenu(player);
                } else {
                    player.sendMessage("§e请使用 /war 命令打开战争菜单");
                }
            }
            case 16 -> {
                // 外交关系总览
                openRelationsView(player);
            }
            case 24 -> {
                // 关系网络可视化
                if (networkMenu != null) {
                    networkMenu.openMainMenu(player);
                } else {
                    player.sendMessage("§e请使用 /diplomacy network 查看关系网络");
                }
            }
            case 28 -> {
                // 发起联盟
                if (allianceMenu != null) {
                    allianceMenu.openSendInviteMenu(player, 0);
                } else {
                    player.sendMessage("§e请使用 /alliance invite <国家名> 发起联盟");
                }
            }
            case 30 -> {
                // 发起战争
                if (warMenu != null) {
                    warMenu.openDeclareWarMenu(player);
                } else {
                    player.sendMessage("§e请使用 /war declare <国家名> 发起战争");
                }
            }
            default -> {
                // 检查是否是关系项点击
                if (material == Material.EMERALD || material == Material.REDSTONE || material == Material.PAPER) {
                    // 可以在这里扩展查看关系详情
                }
            }
        }
    }

    private void openAllianceManagement(Player player, Nation myNation) {
        player.sendMessage("§6=== 联盟管理 ===");

        var allies = diplomacyModule.relationsOf(myNation.id()).stream()
            .filter(r -> "ALLIED".equals(r.relation().name()))
            .toList();

        if (allies.isEmpty()) {
            player.sendMessage("§7你还没有与其他国家建立联盟");
            player.sendMessage("§7使用 §e/diplomacy allyinvite <国家名> §7发送联盟邀请");
        } else {
            player.sendMessage("§7盟国列表:");
            for (DiplomacyRelationSnapshot snapshot : allies) {
                Optional<Nation> other = nationService.nationById(snapshot.target());
                String nationName = other.map(Nation::name).orElse("未知");
                player.sendMessage("§a  " + nationName);
            }
            // 分隔
            player.sendMessage("§7使用 §e/alliance break <国家名> §7解除联盟");
        }

        // 显示待处理邀请
        var pendingInvites = diplomacyModule.getPendingInvites(myNation.id());
        if (!pendingInvites.isEmpty()) {
            player.sendMessage("§e=== 待处理邀请 ===");
            for (NationId inviterId : pendingInvites) {
                Optional<Nation> inviter = nationService.nationById(inviterId);
                String inviterName = inviter.map(Nation::name).orElse("未知");
                player.sendMessage("§e  来自: " + inviterName + " §7(使用 §e/alliance accept " + inviterName + " §7接受)");
            }
        }

        // 分隔
        player.sendMessage("§7使用 §e/alliance invite <国家名> §7发送联盟邀请");
    }

    private String getItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return "";
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(displayName);
    }

    /**
     * audit C-048: 菜单关闭/玩家退出时清理状态缓存，避免泄漏。
     * 由外部 Listener 在 InventoryCloseEvent / PlayerQuitEvent 中调用。
     */
    public void handleClose(Player player) {
        if (player == null) return;
        playerStates.remove(player.getUniqueId());
    }

    // Getter for external access
    public AllianceMenu getAllianceMenu() {
        return allianceMenu;
    }

    public MilitaryAllianceMenu getMilitaryMenu() {
        return militaryMenu;
    }

    public DiplomacyNetworkMenu getNetworkMenu() {
        return networkMenu;
    }
}