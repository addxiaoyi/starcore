package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 国家管理 GUI
 * 提供可视化的国家信息查看和成员管理界面
 *
 * TODO C-063/C-068: 当前使用硬编码槽位构建菜单
 * 应重构为使用 TriumphNationMenu 的 YAML 配置驱动方式：
 * - 将硬编码槽位迁移到 nation-menu.yml 配置
 * - 统一使用 TriumphMenuBuilder 构建菜单
 * - 保持现有功能不变，渐进式迁移
 */
public final class NationManagementMenu implements InventoryHolder {
    private final Nation nation;
    private final MessageService messages;
    private final Inventory inventory;
    private final MenuPage currentPage;

    /**
     * audit C-025/C-026/C-027/C-029: 安全地设置物品，避免越界。
     * Inventory size 为 54，valid slots 为 0-53。
     */
    private void setItemSafe(int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
    }

    private NationManagementMenu(Nation nation, MessageService messages, MenuPage page) {
        this.nation = nation;
        this.messages = messages;
        this.currentPage = page;

        // 访客模式：未加入国家的玩家显示通用标题
        String title = nation != null
            ? messages.format("nation.gui.title", nation.name())
            : messages.format("nation.gui.visitor-title");

        this.inventory = Bukkit.createInventory(this, 54, Component.text(title));

        buildMenu();
    }

    /**
     * 创建主菜单
     */
    public static NationManagementMenu createMainMenu(Nation nation, MessageService messages) {
        return new NationManagementMenu(nation, messages, MenuPage.MAIN);
    }

    /**
     * 创建成员列表菜单
     */
    public static NationManagementMenu createMemberListMenu(Nation nation, MessageService messages) {
        return new NationManagementMenu(nation, messages, MenuPage.MEMBERS);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Nation getNation() {
        return nation;
    }

    public MenuPage getCurrentPage() {
        return currentPage;
    }

    private void buildMenu() {
        switch (currentPage) {
            case MAIN -> buildMainMenu();
            case MEMBERS -> buildMemberListMenu();
            case SETTINGS -> buildSettingsMenu();
        }

        // 添加导航按钮
        addNavigationButtons();
    }

    /**
     * 构建主菜单
     */
    private void buildMainMenu() {
        // 访客模式：显示引导信息
        if (nation == null) {
            buildVisitorMenu();
            return;
        }

        // 国家信息（第一行）
        setItemSafe(4, createNationInfoItem());

        // 快捷操作（第二行）
        setItemSafe(10, createMembersButton());
        setItemSafe(12, createTreasuryButton());
        setItemSafe(14, createTerritoriesButton());
        setItemSafe(16, createSettingsButton());

        // 统计信息（第三行）
        setItemSafe(20, createPolicyStatusButton());
        setItemSafe(22, createTechStatusButton());
        setItemSafe(24, createDiplomacyStatusButton());
    }

    /**
     * 构建访客模式菜单
     */
    private void buildVisitorMenu() {
        // 显示引导信息
        ItemStack guideItem = new ItemStack(Material.BOOK);
        ItemMeta meta = guideItem.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for visitor guide item");
        }

        meta.displayName(Component.text("§e如何开始？", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7你还没有加入任何国家"));
        lore.add(Component.text(""));
        lore.add(Component.text("§a创建国家："));
        lore.add(Component.text("§7/sc nation create <名称>"));
        lore.add(Component.text(""));
        lore.add(Component.text("§a加入国家："));
        lore.add(Component.text("§7/sc nation join <名称>"));
        lore.add(Component.text(""));
        lore.add(Component.text("§a查看所有国家："));
        lore.add(Component.text("§7/sc nation list"));

        meta.lore(lore);
        guideItem.setItemMeta(meta);

        setItemSafe(13, guideItem);
    }

    /**
     * 构建成员列表菜单
     */
    private void buildMemberListMenu() {
        if (nation == null) {
            buildVisitorMenu();
            return;
        }

        List<NationMember> members = new ArrayList<>(nation.getMembers());

        int slot = 9; // 从第二行开始
        for (int i = 0; i < members.size() && slot < 45; i++) {
            NationMember member = members.get(i);
            setItemSafe(slot, createMemberItem(member));
            slot++;
        }

        // 添加邀请按钮
        setItemSafe(53, createInvitePlayerButton());
    }

    /**
     * 构建设置菜单
     */
    private void buildSettingsMenu() {
        if (nation == null) {
            buildVisitorMenu();
            return;
        }

        setItemSafe(10, createGovernmentTypeButton());
        setItemSafe(12, createTaxRateButton());
        setItemSafe(14, createPermissionsButton());
        setItemSafe(16, createDisbandButton());
    }

    // ==================== 物品创建方法 ====================

    private ItemStack createNationInfoItem() {
        // 访客模式：返回空物品
        if (nation == null) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("无国家信息", NamedTextColor.GRAY));
            }
            item.setItemMeta(meta);
            return item;
        }

        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for nation info item");
        }

        meta.displayName(Component.text(nation.name(), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.info.members", nation.memberCount()), NamedTextColor.GRAY));
        lore.add(Component.text(messages.format("nation.gui.info.territories", nation.territoryCount()), NamedTextColor.GRAY));
        lore.add(Component.text(messages.format("nation.gui.info.treasury", formatMoney(nation.getTreasuryBalance())), NamedTextColor.GRAY));
        lore.add(Component.text(messages.format("nation.gui.info.government", nation.getGovernmentType()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.info.founded", nation.getFoundedDate()), NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMembersButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(messages.format("nation.gui.button.members"), NamedTextColor.GRAY));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text(messages.format("nation.gui.button.members.count", 0), NamedTextColor.GRAY));
                lore.add(Component.text(""));
                lore.add(Component.text(messages.format("nation.gui.button.no-nation"), NamedTextColor.DARK_GRAY));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for members button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.members"), NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.members.count", nation.memberCount()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-view"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTreasuryButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            ItemStack item = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(messages.format("nation.gui.button.treasury"), NamedTextColor.GRAY));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text(messages.format("nation.gui.button.no-nation"), NamedTextColor.DARK_GRAY));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for treasury button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.treasury"), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.treasury.balance", formatMoney(nation.getTreasuryBalance())), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-manage"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTerritoriesButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(messages.format("nation.gui.button.territories"), NamedTextColor.GRAY));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text(messages.format("nation.gui.button.territories.count", 0), NamedTextColor.GRAY));
                lore.add(Component.text(""));
                lore.add(Component.text(messages.format("nation.gui.button.no-nation"), NamedTextColor.DARK_GRAY));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for territories button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.territories"), NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.territories.count", nation.territoryCount()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-view"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSettingsButton() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for settings button");
        }

        // 根据是否有国家设置不同的颜色
        NamedTextColor color = nation != null ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY;
        String hint = nation != null
            ? messages.format("nation.gui.button.click-to-open")
            : messages.format("nation.gui.button.no-nation");

        meta.displayName(Component.text(messages.format("nation.gui.button.settings"), color));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.settings.desc"), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(hint, NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPolicyStatusButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            return createDisabledButton(Material.BOOK, messages.format("nation.gui.button.policies"), NamedTextColor.GRAY);
        }

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for policy status button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.policies"), NamedTextColor.BLUE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.policies.active", nation.getActivePolicyCount()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-manage"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTechStatusButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            return createDisabledButton(Material.ENCHANTING_TABLE, messages.format("nation.gui.button.technology"), NamedTextColor.GRAY);
        }

        ItemStack item = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for tech status button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.technology"), NamedTextColor.DARK_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.technology.unlocked", nation.getUnlockedTechCount()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-research"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDiplomacyStatusButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            return createDisabledButton(Material.WRITABLE_BOOK, messages.format("nation.gui.button.diplomacy"), NamedTextColor.GRAY);
        }

        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for diplomacy status button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.diplomacy"), NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.diplomacy.allies", nation.getAllyCount()), NamedTextColor.GRAY));
        lore.add(Component.text(messages.format("nation.gui.button.diplomacy.wars", nation.getWarCount()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-manage"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建禁用状态的按钮
     */
    private ItemStack createDisabledButton(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(messages.format("nation.gui.button.no-nation"), NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createMemberItem(NationMember member) {
        if (nation == null) {
            // 访客模式下不应调用此方法，但提供一个安全的默认物品
            return createDisabledButton(Material.PLAYER_HEAD, "无成员信息", NamedTextColor.GRAY);
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(member.playerId()));

            Component displayName = member.isOnline() ?
                Component.text(member.playerName(), NamedTextColor.GREEN) :
                Component.text(member.playerName(), NamedTextColor.GRAY);
            skullMeta.displayName(displayName);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(messages.format("nation.gui.member.rank", member.rank()), NamedTextColor.GRAY));
            lore.add(Component.text(messages.format("nation.gui.member.joined", member.joinedDate()), NamedTextColor.GRAY));
            lore.add(Component.text(""));

            if (member.isOnline()) {
                lore.add(Component.text(messages.format("nation.gui.member.online"), NamedTextColor.GREEN));
            } else {
                lore.add(Component.text(messages.format("nation.gui.member.offline", member.lastSeenDaysAgo()), NamedTextColor.GRAY));
            }

            lore.add(Component.text(""));
            lore.add(Component.text(messages.format("nation.gui.member.click-to-manage"), NamedTextColor.YELLOW));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }

        return item;
    }

    private ItemStack createInvitePlayerButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            return createDisabledButton(Material.EMERALD, messages.format("nation.gui.button.invite"), NamedTextColor.GRAY);
        }

        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for invite player button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.invite"), NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.invite.desc"), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-invite"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGovernmentTypeButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            return createDisabledButton(Material.BELL, messages.format("nation.gui.button.government"), NamedTextColor.GRAY);
        }

        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for government type button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.government"), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.government.current", nation.getGovernmentType()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-change"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTaxRateButton() {
        // 访客模式：返回禁用按钮
        if (nation == null) {
            return createDisabledButton(Material.PAPER, messages.format("nation.gui.button.tax"), NamedTextColor.GRAY);
        }

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for tax rate button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.tax"), NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.tax.rate", nation.getTaxRate()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-adjust"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPermissionsButton() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for permissions button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.permissions"), NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.permissions.desc"), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-manage"), NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDisbandButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for disband button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.disband"), NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.disband.warning"), NamedTextColor.DARK_RED));
        lore.add(Component.text(""));
        lore.add(Component.text(messages.format("nation.gui.button.click-to-disband"), NamedTextColor.RED));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 添加导航按钮
     */
    private void addNavigationButtons() {
        // 返回按钮
        if (currentPage != MenuPage.MAIN) {
            setItemSafe(45, createBackButton());
        }

        // 关闭按钮
        setItemSafe(49, createCloseButton());

        // 帮助按钮
        setItemSafe(53, createHelpButton());
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for back button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.back"), NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for close button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.close"), NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHelpButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("Failed to create ItemMeta for help button");
        }

        meta.displayName(Component.text(messages.format("nation.gui.button.help"), NamedTextColor.AQUA));
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 工具方法 ====================

    private String formatMoney(BigDecimal amount) {
        return amount.toPlainString();
    }

    /**
     * 菜单页面枚举
     */
    public enum MenuPage {
        MAIN,
        MEMBERS,
        SETTINGS
    }
}
