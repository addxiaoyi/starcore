package dev.starcore.starcore.module.officer.gui;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.officer.OfficerAppointment;
import dev.starcore.starcore.module.officer.OfficerModule;
import dev.starcore.starcore.module.officer.OfficerRoleConfig;
import dev.starcore.starcore.util.MessageUtil;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 官员管理菜单
 * 提供可视化的官员任命/移除界面
 */
public final class OfficerMenu implements InventoryHolder {
    static final int[] ROLE_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};
    private static final int INFO_SLOT = 4;
    static final int CLOSE_SLOT = 49;
    private static final int HELP_SLOT = 53;

    /**
     * audit C-030/C-031/C-032: 安全地设置物品，避免越界。
     */
    private void setItemSafe(int slot, ItemStack item) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
    }

    private final Player player;
    private final Nation nation;
    private final NationId nationId;
    private final OfficerModule officerModule;
    private final Inventory inventory;
    private final MenuPage currentPage;
    private String selectedRole;

    public enum MenuPage {
        MAIN,
        SELECT_PLAYER,
        CONFIRM_REMOVE
    }

    OfficerMenu(Player player, Nation nation, OfficerModule officerModule, MenuPage page) {
        this.player = player;
        this.nation = nation;
        this.nationId = nation != null ? nation.id() : null;
        this.officerModule = officerModule;
        this.currentPage = page;
        this.selectedRole = null;

        String title = nation != null
            ? MessageUtil.colorize("&6官员管理 - " + nation.name())
            : MessageUtil.colorize("&6官员管理");

        this.inventory = Bukkit.createInventory(this, 54, Component.text(title));
        buildMenu();
    }

    /**
     * 创建官员管理菜单
     */
    public static OfficerMenu create(Player player, Nation nation, OfficerModule officerModule) {
        return new OfficerMenu(player, nation, officerModule, MenuPage.MAIN);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public Nation getNation() {
        return nation;
    }

    public MenuPage getCurrentPage() {
        return currentPage;
    }

    public String getSelectedRole() {
        return selectedRole;
    }

    public void setSelectedRole(String role) {
        this.selectedRole = role;
    }

    private void buildMenu() {
        switch (currentPage) {
            case MAIN -> buildMainMenu();
            case SELECT_PLAYER -> buildSelectPlayerMenu();
            case CONFIRM_REMOVE -> buildConfirmRemoveMenu();
        }
    }

    /**
     * 构建主菜单 - 显示所有角色和当前官员
     */
    private void buildMainMenu() {
        // 国家信息
        setItemSafe(INFO_SLOT, createNationInfoItem());

        // 角色列表
        List<OfficerRoleConfig> roles = officerModule.getAllRoleConfigs().stream().toList();
        for (int i = 0; i < roles.size() && i < ROLE_SLOTS.length; i++) {
            OfficerRoleConfig roleConfig = roles.get(i);
            String roleId = roleConfig.id();
            Optional<OfficerAppointment> officer = nationId != null ? officerModule.officer(nationId, roleId) : Optional.empty();

            setItemSafe(ROLE_SLOTS[i], createRoleItem(roleConfig, officer.orElse(null)));
        }

        // 填充空槽
        fillEmptySlots();
    }

    /**
     * 构建选择玩家菜单
     */
    private void buildSelectPlayerMenu() {
        if (nation == null || selectedRole == null) {
            return;
        }

        // 显示选中的角色
        OfficerRoleConfig roleConfig = officerModule.getRoleConfig(selectedRole);
        if (roleConfig != null) {
            setItemSafe(INFO_SLOT, createSelectedRoleItem(roleConfig));
        }

        // 获取国家成员
        List<UUID> memberIds = nation.members().stream()
            .map(dev.starcore.starcore.module.nation.model.NationMember::playerId)
            .collect(Collectors.toList());

        // 显示成员列表（用于任命）
        int slot = 19;
        for (UUID memberId : memberIds) {
            if (slot >= 44) break;

            // 检查是否已是官员
            Optional<String> existingRole = officerModule.getPlayerOfficerRole(nationId, memberId);

            // 获取玩家名
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberId);
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";

            setItemSafe(slot, createMemberItem(memberId, playerName, existingRole.orElse(null)));
            slot++;
        }

        // 返回按钮
        setItemSafe(45, createBackButton());
    }

    /**
     * 构建确认移除菜单
     */
    private void buildConfirmRemoveMenu() {
        if (nation == null || selectedRole == null) {
            return;
        }

        Optional<OfficerAppointment> officer = officerModule.officer(nationId, selectedRole);
        if (officer.isEmpty()) {
            return;
        }

        OfficerAppointment appointment = officer.get();
        OfficerRoleConfig roleConfig = officerModule.getRoleConfig(selectedRole);

        // 显示确认信息
        setItemSafe(INFO_SLOT, createConfirmRemoveInfoItem(roleConfig, appointment));

        // 确认按钮
        setItemSafe(22, createConfirmButton());

        // 取消按钮
        setItemSafe(24, createCancelButton());
    }

    // ==================== 物品创建方法 ====================

    /**
     * 创建国家信息物品
     */
    private ItemStack createNationInfoItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        // audit C-034: 防 NPE
        if (meta == null) {
            return item;
        }

        Component name = nation != null
            ? Component.text(nation.name(), NamedTextColor.GOLD)
            : Component.text("无国家", NamedTextColor.RED);
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(MessageUtil.colorize("&7点击官员角色进行管理")).color(NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(MessageUtil.colorize("&a左键: &7任命/替换官员")).color(NamedTextColor.GREEN));
        lore.add(Component.text(MessageUtil.colorize("&c右键: &7移除官员")).color(NamedTextColor.RED));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建角色物品
     */
    private ItemStack createRoleItem(OfficerRoleConfig roleConfig, OfficerAppointment appointment) {
        Material material = appointment != null ? Material.PLAYER_HEAD : roleConfig.icon();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // 名称
        Component displayName = Component.text(roleConfig.displayName(), NamedTextColor.YELLOW);
        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(MessageUtil.colorize("&7职位: &f" + roleConfig.id()), NamedTextColor.GRAY));

        if (appointment != null) {
            // 已有官员
            lore.add(Component.text(MessageUtil.colorize("&7当前官员: &a" + appointment.playerName()), NamedTextColor.GREEN));
            lore.add(Component.text(""));
            lore.add(Component.text(MessageUtil.colorize("&a左键: &7更换官员"), NamedTextColor.GREEN));
            lore.add(Component.text(MessageUtil.colorize("&c右键: &7移除官员"), NamedTextColor.RED));
        } else {
            // 空位
            lore.add(Component.text(MessageUtil.colorize("&7当前官员: &c空缺"), NamedTextColor.RED));
            lore.add(Component.text(""));
            lore.add(Component.text(MessageUtil.colorize("&a左键: &7任命官员"), NamedTextColor.GREEN));
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        // 如果是头颅，设置所有者
        if (appointment != null && item.getItemMeta() instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(appointment.playerId());
            skullMeta.setOwningPlayer(offlinePlayer);
            item.setItemMeta(skullMeta);
        }

        return item;
    }

    /**
     * 创建已选角色物品
     */
    private ItemStack createSelectedRoleItem(OfficerRoleConfig roleConfig) {
        ItemStack item = new ItemStack(roleConfig.icon());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(roleConfig.displayName(), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(MessageUtil.colorize("&7选择要任命的成员"), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(MessageUtil.colorize("&e点击成员头像进行任命"), NamedTextColor.YELLOW));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建成员物品
     */
    private ItemStack createMemberItem(UUID playerId, String playerName, String currentRole) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            skullMeta.setOwningPlayer(offlinePlayer);

            Component displayName = Component.text(playerName, NamedTextColor.WHITE);
            skullMeta.displayName(displayName);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            if (currentRole != null) {
                String roleName = officerModule.getRoleDisplayName(currentRole);
                lore.add(Component.text(MessageUtil.colorize("&7当前职位: &c" + roleName), NamedTextColor.RED));
                lore.add(Component.text(MessageUtil.colorize("&7(将被替换)"), NamedTextColor.DARK_RED));
            } else {
                lore.add(Component.text(MessageUtil.colorize("&7当前职位: &a无"), NamedTextColor.GREEN));
            }
            lore.add(Component.text(""));
            lore.add(Component.text(MessageUtil.colorize("&e点击任命"), NamedTextColor.YELLOW));

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    /**
     * 创建确认移除信息物品
     */
    private ItemStack createConfirmRemoveInfoItem(OfficerRoleConfig roleConfig, OfficerAppointment appointment) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(MessageUtil.colorize("&c确认移除官员"), NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(MessageUtil.colorize("&7职位: &f" + roleConfig.displayName()), NamedTextColor.GRAY));
        lore.add(Component.text(MessageUtil.colorize("&7官员: &f" + appointment.playerName()), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text(MessageUtil.colorize("&c确定要移除此官员吗？"), NamedTextColor.RED));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建确认按钮
     */
    private ItemStack createConfirmButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(MessageUtil.colorize("&a确认移除"), NamedTextColor.GREEN));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建取消按钮
     */
    private ItemStack createCancelButton() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(MessageUtil.colorize("&c取消"), NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建返回按钮
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(MessageUtil.colorize("&e返回"), NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 填充空槽
     */
    private void fillEmptySlots() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                // 保留导航按钮槽位
                if (slot != CLOSE_SLOT && slot != HELP_SLOT) {
                    inventory.setItem(slot, filler);
                }
            }
        }

        // 导航按钮
        setItemSafe(CLOSE_SLOT, createCloseButton());
        setItemSafe(HELP_SLOT, createHelpButton());
    }

    /**
     * 创建关闭按钮
     */
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(MessageUtil.colorize("&c关闭"), NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建帮助按钮
     */
    private ItemStack createHelpButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(MessageUtil.colorize("&b帮助"), NamedTextColor.AQUA));
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 点击处理 ====================

    /**
     * 处理点击事件
     * @return true 如果需要关闭菜单
     */
    public boolean handleClick(int slot, boolean isRightClick) {
        switch (currentPage) {
            case MAIN -> {
                // 检查是否点击了角色槽位
                for (int i = 0; i < ROLE_SLOTS.length; i++) {
                    if (slot == ROLE_SLOTS[i]) {
                        List<OfficerRoleConfig> roles = officerModule.getAllRoleConfigs().stream().toList();
                        if (i < roles.size()) {
                            String roleId = roles.get(i).id();
                            return handleRoleClick(roleId, isRightClick);
                        }
                    }
                }

                // 导航按钮
                if (slot == CLOSE_SLOT) {
                    return true; // 关闭
                }
            }
            case SELECT_PLAYER -> {
                if (slot == 45) {
                    return false; // 返回
                }

                // 检查是否点击了成员
                for (int s = 19; s <= 44; s++) {
                    if (slot == s && inventory.getItem(slot) != null) {
                        ItemStack item = inventory.getItem(slot);
                        if (item.getItemMeta() instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
                            UUID playerId = skullMeta.getOwningPlayer().getUniqueId();
                            return handleAppointClick(playerId);
                        }
                    }
                }
            }
            case CONFIRM_REMOVE -> {
                if (slot == 22) {
                    // 确认移除
                    handleConfirmRemove();
                    return true;
                } else if (slot == 24) {
                    // 取消
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 处理角色点击
     */
    private boolean handleRoleClick(String roleId, boolean isRightClick) {
        if (nation == null) {
            player.sendMessage(MessageUtil.colorize("&c你没有国家，无法管理官员！"));
            return true;
        }

        Optional<OfficerAppointment> officer = officerModule.officer(nationId, roleId);

        if (isRightClick && officer.isPresent()) {
            // 右键 - 移除官员
            selectedRole = roleId;
            return false; // 不关闭，打开确认菜单
        } else {
            // 左键 - 任命/替换官员
            selectedRole = roleId;
            return false; // 不关闭，打开选择玩家菜单
        }
    }

    /**
     * 处理任命点击
     */
    private boolean handleAppointClick(UUID playerId) {
        if (nation == null || selectedRole == null) {
            return true;
        }

        // 获取玩家名称
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerId);
        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

        // 检查是否已是其他官员
        Optional<String> existingRole = officerModule.getPlayerOfficerRole(nationId, playerId);
        if (existingRole.isPresent() && !existingRole.get().equals(selectedRole)) {
            // 先移除旧职位
            officerModule.remove(nationId, existingRole.get());
        }

        // 任命新职位
        boolean success = officerModule.appoint(nationId, selectedRole, playerId, playerName);

        if (success) {
            String roleName = officerModule.getRoleDisplayName(selectedRole);
            player.sendMessage(MessageUtil.colorize("&a成功任命 &e" + playerName + " &a为 &f" + roleName));
            player.sendMessage(MessageUtil.colorize("&7/sc officer status 查看当前官员列表"));
        } else {
            player.sendMessage(MessageUtil.colorize("&c任命失败！"));
        }

        return true; // 关闭菜单
    }

    /**
     * 处理确认移除
     */
    private void handleConfirmRemove() {
        if (nation == null || selectedRole == null) {
            return;
        }

        Optional<OfficerAppointment> officer = officerModule.officer(nationId, selectedRole);
        if (officer.isEmpty()) {
            return;
        }

        String playerName = officer.get().playerName();

        boolean success = officerModule.remove(nationId, selectedRole);

        if (success) {
            String roleName = officerModule.getRoleDisplayName(selectedRole);
            player.sendMessage(MessageUtil.colorize("&c已移除 &e" + playerName + " &c的 &f" + roleName + " &c职位"));
        } else {
            player.sendMessage(MessageUtil.colorize("&c移除失败！"));
        }
    }

    /**
     * 获取菜单类型（用于确定打开哪个菜单）
     */
    public MenuPage getMenuType(int slot) {
        switch (currentPage) {
            case MAIN:
                return MenuPage.MAIN;
            case SELECT_PLAYER:
                if (slot == 45) {
                    return MenuPage.MAIN;
                }
                return MenuPage.SELECT_PLAYER;
            case CONFIRM_REMOVE:
                return MenuPage.CONFIRM_REMOVE;
        }
        return currentPage;
    }
}
