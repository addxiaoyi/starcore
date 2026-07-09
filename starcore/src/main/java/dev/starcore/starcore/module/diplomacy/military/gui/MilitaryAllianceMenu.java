package dev.starcore.starcore.module.diplomacy.military.gui;

import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.*;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 军事联盟 GUI 菜单
 * 提供完整的军事联盟管理界面
 */
public class MilitaryAllianceMenu {

    // GUI 标题
    public static final String MAIN_MENU_TITLE = "§b§l⚔️ 军事联盟";
    public static final String MY_PACTS_TITLE = "§b§l⚔️ 我的条约";
    public static final String PENDING_TITLE = "§e§l⚔️ 待处理邀请";
    public static final String SELECT_NATION_TITLE = "§b§l⚔️ 选择签约国";
    public static final String PACT_DETAIL_TITLE = "§b§l⚔️ 条约详情";
    public static final String SELECT_PACT_TYPE_TITLE = "§e§l⚔️ 选择条约类型";

    private final MilitaryAllianceService allianceService;
    private final NationService nationService;

    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();
    private final Map<UUID, NationId> selectedNation = new ConcurrentHashMap<>();

    public MilitaryAllianceMenu(MilitaryAllianceService allianceService, NationService nationService) {
        this.allianceService = allianceService;
        this.nationService = nationService;
    }

    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text(MAIN_MENU_TITLE));

        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§b§l⚔️ 军事联盟中心",
            Material.IRON_CHESTPLATE,
            ButtonFactory.BUTTON_STYLE_INFO
        ));

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§c你没有所属国家",
                "需要加入国家才能使用军事联盟功能"
            ));
            inv.setItem(31, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        Nation playerNation = nationService.nationById(playerNationId).orElse(null);
        if (playerNation == null) {
            player.closeInventory();
            return;
        }

        // 统计数据
        int pactCount = allianceService.getMilitaryAllies(playerNationId, PactType.OBSERVER).size();
        int pendingCount = allianceService.getPendingInvites(playerNationId).size();

        // 功能按钮
        inv.setItem(10, ButtonFactory.createStyledButton(
            "§b⚔️ 我的条约",
            Material.IRON_CHESTPLATE,
            ButtonFactory.BUTTON_STYLE_INFO,
            "当前条约数: §a" + pactCount,
            "查看和管理你的军事条约"
        ));

        inv.setItem(12, ButtonFactory.createStyledButton(
            "§e⚔️ 待处理邀请",
            Material.BEACON,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "待处理: §e" + pendingCount,
            "查看收到的军事联盟邀请"
        ));

        inv.setItem(14, ButtonFactory.createStyledButton(
            "§a⚔️ 发起签约",
            Material.DIAMOND,
            ButtonFactory.BUTTON_STYLE_PRIMARY,
            "向其他国家发起军事联盟签约",
            "选择条约类型和目标国家"
        ));

        // 统计信息
        MilitaryAllianceStats stats = allianceService.getStats();
        inv.setItem(19, ButtonFactory.createStatButton(
            Material.PAPER,
            "§7总条约数",
            String.valueOf(stats.totalPacts())
        ));

        inv.setItem(21, ButtonFactory.createStatButton(
            Material.BEACON,
            "§7待处理",
            String.valueOf(stats.totalInvitesPending())
        ));

        inv.setItem(23, ButtonFactory.createStatButton(
            Material.NETHER_STAR,
            "§7最强同盟",
            stats.mostAlliedNation()
        ));

        // 帮助信息
        inv.setItem(31, ButtonFactory.createInfoButton(
            "提示: 军事联盟提供防御加成",
            "不同条约类型提供不同的保护效果"
        ));

        player.openInventory(inv);
    }

    /**
     * 打开我的条约列表
     */
    public void openMyPactsMenu(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        Collection<NationId> allies = allianceService.getMilitaryAllies(playerNationId, PactType.OBSERVER);

        int size = Math.max(36, ((allies.size() / 5) + 1) * 9 + 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, Component.text(MY_PACTS_TITLE));
        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§b§l⚔️ 我的军事条约",
            Material.IRON_CHESTPLATE,
            ButtonFactory.BUTTON_STYLE_INFO
        ));

        if (allies.isEmpty()) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§7暂无军事条约",
                "你的国家还没有与其他国家签订军事条约",
                "点击发起签约来创建新的条约"
            ));
        } else {
            int slot = 10;
            int index = 0;
            int itemsPerPage = 36;
            int startIndex = page * itemsPerPage;

            for (NationId allyId : allies) {
                if (index < startIndex) {
                    index++;
                    continue;
                }
                if (slot > size - 11) break;

                Optional<MilitaryPactInfo> infoOpt = allianceService.getPactInfo(playerNationId, allyId);
                if (infoOpt.isEmpty()) continue;

                MilitaryPactInfo info = infoOpt.get();
                ItemStack item = createPactItem(info);
                inv.setItem(slot, item);

                slot++;
                if (slot % 9 == 8) slot += 2;
                index++;
            }

            // 分页控制
            int totalPages = (allies.size() + itemsPerPage - 1) / itemsPerPage;
            if (page > 0) {
                inv.setItem(size - 9, ButtonFactory.createPrevButton("第 " + (page) + " 页"));
            }
            if (page < totalPages - 1) {
                inv.setItem(size - 1, ButtonFactory.createNextButton("第 " + (page + 2) + " 页"));
            }
        }

        // 返回按钮
        inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开待处理邀请菜单
     */
    public void openPendingInvitesMenu(Player player) {
        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        List<PactInviteInfo> invites = allianceService.getPendingInvites(playerNationId);

        int size = Math.max(36, ((invites.size() / 5) + 1) * 9 + 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, Component.text(PENDING_TITLE));
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§e§l⚔️ 待处理邀请",
            Material.BEACON,
            ButtonFactory.BUTTON_STYLE_SUCCESS
        ));

        if (invites.isEmpty()) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§a✌️ 暂无待处理邀请",
                "没有国家向你们发送军事联盟邀请"
            ));
        } else {
            int slot = 10;
            for (PactInviteInfo invite : invites) {
                if (slot > size - 11) break;

                long hours = invite.remainingMs() / (60 * 60 * 1000);
                List<String> lore = new ArrayList<>();
                lore.add("§7条约类型: §e" + invite.pactType().displayName());
                lore.add("§7邀请时间: §f" + formatInstant(invite.invitedAt()));
                lore.add("§7剩余时间: §e" + hours + " 小时");
                lore.add("");
                lore.add("§a左键接受邀请");
                lore.add("§c右键拒绝邀请");

                ItemStack item = new ItemStack(Material.BEACON);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("§e来自: " + invite.inviterName())
                        .color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.BOLD));
                    List<Component> loreComponents = new ArrayList<>();
                    for (String line : lore) {
                        loreComponents.add(Component.text(line, NamedTextColor.GRAY));
                    }
                    meta.lore(loreComponents);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);

                slot++;
                if (slot % 9 == 8) slot += 2;
            }
        }

        // 返回按钮
        inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开选择国家菜单
     */
    public void openSelectNationMenu(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        Collection<Nation> allNations = nationService.nations();
        List<Nation> selectableNations = new ArrayList<>();

        for (Nation nation : allNations) {
            if (!nation.id().equals(playerNationId)) {
                // 排除已有条约的国家
                Optional<MilitaryPactInfo> infoOpt = allianceService.getPactInfo(playerNationId, nation.id());
                if (infoOpt.isEmpty()) {
                    selectableNations.add(nation);
                }
            }
        }

        int size = Math.max(36, ((selectableNations.size() / 5) + 1) * 9 + 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, Component.text(SELECT_NATION_TITLE));
        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§b§l⚔️ 选择签约国家",
            Material.DIAMOND,
            ButtonFactory.BUTTON_STYLE_PRIMARY
        ));

        if (selectableNations.isEmpty()) {
            inv.setItem(13, ButtonFactory.createInfoButton(
                "§c无可签约国家",
                "所有国家都已签订条约",
                "或没有其他独立国家存在"
            ));
        } else {
            int slot = 10;
            int index = 0;
            int itemsPerPage = 36;
            int startIndex = page * itemsPerPage;

            for (Nation nation : selectableNations) {
                if (index < startIndex) {
                    index++;
                    continue;
                }
                if (slot > size - 11) break;

                List<String> lore = new ArrayList<>();
                lore.add("§a可签订军事条约");
                lore.add("");
                lore.add("§e点击选择此国家");

                ItemStack item = new ItemStack(Material.DIAMOND);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("§f" + nation.name())
                        .decorate(TextDecoration.BOLD));
                    List<Component> loreComponents = new ArrayList<>();
                    for (String line : lore) {
                        loreComponents.add(Component.text(line, NamedTextColor.GRAY));
                    }
                    meta.lore(loreComponents);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);

                slot++;
                if (slot % 9 == 8) slot += 2;
                index++;
            }

            // 分页控制
            int totalPages = (selectableNations.size() + itemsPerPage - 1) / itemsPerPage;
            if (page > 0) {
                inv.setItem(size - 9, ButtonFactory.createPrevButton("第 " + (page) + " 页"));
            }
            if (page < totalPages - 1) {
                inv.setItem(size - 1, ButtonFactory.createNextButton("第 " + (page + 2) + " 页"));
            }
        }

        // 返回按钮
        inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开选择条约类型菜单
     */
    public void openSelectPactTypeMenu(Player player, NationId targetId) {
        selectedNation.put(player.getUniqueId(), targetId);

        String targetName = nationService.nationById(targetId)
            .map(Nation::name)
            .orElse("未知");

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(SELECT_PACT_TYPE_TITLE));
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§e§l⚔️ 选择条约类型",
            Material.BEACON,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "目标国家: " + targetName
        ));

        // 目标国家信息
        inv.setItem(13, ButtonFactory.createInfoButton(
            "§b目标国家: §f" + targetName,
            "选择一个条约类型发起签约"
        ));

        // 观察员国
        inv.setItem(19, createPactTypeItem(PactType.OBSERVER,
            "信息共享，不提供军事保护"
        ));

        // 防御同盟
        inv.setItem(21, createPactTypeItem(PactType.DEFENSIVE,
            "被动防御承诺，提供 10% 防御加成"
        ));

        // 全面同盟
        inv.setItem(23, createPactTypeItem(PactType.FULL_ALLIANCE,
            "主动军事援助，提供 25% 防御加成"
        ));

        // 军事一体化
        inv.setItem(25, createPactTypeItem(PactType.INTEGRATED,
            "最高级别联合，提供 50% 防御加成"
        ));

        // 说明
        inv.setItem(31, ButtonFactory.createInfoButton(
            "提示: 更高等级的条约提供更强的保护",
            "但也需要更深的信任"
        ));

        // 返回按钮
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开条约详情菜单
     */
    public void openPactDetailMenu(Player player, NationId pactId) {
        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) return;

        Optional<MilitaryPactInfo> infoOpt = allianceService.getPactInfo(playerNationId, pactId);
        if (infoOpt.isEmpty()) {
            player.sendMessage("§c找不到条约信息");
            return;
        }

        MilitaryPactInfo info = infoOpt.get();
        String title = "§b§l⚔️ " + info.nation2Name() + " 条约详情";

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(title));
        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, ButtonFactory.createStyledButton(
            "§b§l⚔️ 条约详情",
            Material.IRON_CHESTPLATE,
            ButtonFactory.BUTTON_STYLE_INFO
        ));

        // 左侧 - 己方信息
        inv.setItem(10, createSideItem(
            "§a你的国家",
            nationService.nationById(info.nation1())
                .map(Nation::name)
                .orElse("未知"),
            Material.GREEN_BANNER,
            "发起条约的国家"
        ));

        // 右侧 - 对方信息
        inv.setItem(16, createSideItem(
            "§b对方国家",
            info.nation2Name(),
            Material.BLUE_BANNER,
            "签约的另一方"
        ));

        // 条约类型
        Material pactMaterial = switch (info.pactType()) {
            case OBSERVER -> Material.PAPER;
            case DEFENSIVE -> Material.IRON_SWORD;
            case FULL_ALLIANCE -> Material.DIAMOND_SWORD;
            case INTEGRATED -> Material.NETHER_STAR;
            default -> Material.BARRIER;
        };

        inv.setItem(13, ButtonFactory.createStatButton(
            pactMaterial,
            "§e条约类型",
            info.pactType().displayName()
        ));

        // 统计信息
        inv.setItem(22, ButtonFactory.createStatButton(
            Material.CLOCK,
            "§7成立时间",
            formatInstant(info.formedAt())
        ));

        inv.setItem(31, ButtonFactory.createStatButton(
            Material.PAPER,
            "§7持续时间",
            info.durationDays() + " 天"
        ));

        // 防御加成
        double bonus = info.pactType().defenseBonus();
        if (bonus > 0) {
            inv.setItem(40, ButtonFactory.createStatButton(
                Material.SHIELD,
                "§a防御加成",
                "+" + (int)(bonus * 100) + "%"
            ));
        }

        // 功能按钮
        inv.setItem(28, ButtonFactory.createStyledButton(
            "§e⚔️ 升级条约",
            Material.EMERALD,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "升级到更高级别的条约"
        ));

        inv.setItem(34, ButtonFactory.createStyledButton(
            "§c⚔️ 解除条约",
            Material.RED_CONCRETE,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "解除与该国的军事条约",
            "将进入 24 小时冷却"
        ));

        // 返回按钮
        inv.setItem(44, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 辅助方法 ====================

    private ItemStack createPactItem(MilitaryPactInfo info) {
        Material material = switch (info.pactType()) {
            case OBSERVER -> Material.PAPER;
            case DEFENSIVE -> Material.IRON_SWORD;
            case FULL_ALLIANCE -> Material.DIAMOND_SWORD;
            case INTEGRATED -> Material.NETHER_STAR;
            default -> Material.BARRIER;
        };

        List<String> lore = new ArrayList<>();
        lore.add("§7条约类型: §e" + info.pactType().displayName());
        lore.add("§7成立时间: §f" + formatInstant(info.formedAt()));
        lore.add("§7持续时间: §a" + info.durationDays() + " 天");
        if (info.pactType().defenseBonus() > 0) {
            lore.add("§7防御加成: §a+" + (int)(info.pactType().defenseBonus() * 100) + "%");
        }
        lore.add("");
        lore.add("§e点击查看详情");
        lore.add("§c右键解除条约");

        return ButtonFactory.createStyledButton(
            "§b⚔️ " + info.nation2Name(),
            material,
            ButtonFactory.BUTTON_STYLE_INFO,
            lore.toArray(new String[0])
        );
    }

    private ItemStack createPactTypeItem(PactType type, String description) {
        Material material = switch (type) {
            case OBSERVER -> Material.PAPER;
            case DEFENSIVE -> Material.IRON_SWORD;
            case FULL_ALLIANCE -> Material.DIAMOND_SWORD;
            case INTEGRATED -> Material.NETHER_STAR;
            default -> Material.BARRIER;
        };

        String bonus = type.defenseBonus() > 0 ? " §a+" + (int)(type.defenseBonus() * 100) + "%" : "";

        return ButtonFactory.createStyledButton(
            "§e" + type.displayName(),
            material,
            ButtonFactory.BUTTON_STYLE_SUCCESS,
            "§7" + description,
            "§7防御加成: " + bonus,
            "",
            "§e点击选择此条约类型"
        );
    }

    private ItemStack createSideItem(String title, String name, Material material, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§f" + name, NamedTextColor.YELLOW));
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

    private String formatInstant(Instant instant) {
        return instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // Getter for listener
    public MilitaryAllianceService getAllianceService() {
        return allianceService;
    }

    public NationService getNationService() {
        return nationService;
    }

    public Map<UUID, Integer> getPlayerPages() {
        return playerPages;
    }

    public Map<UUID, NationId> getSelectedNation() {
        return selectedNation;
    }
}