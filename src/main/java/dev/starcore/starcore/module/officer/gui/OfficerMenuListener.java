package dev.starcore.starcore.module.officer.gui;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.officer.OfficerAppointment;
import dev.starcore.starcore.module.officer.OfficerModule;
import dev.starcore.starcore.module.officer.gui.OfficerMenu.MenuPage;
import dev.starcore.starcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 官员菜单监听器
 * 处理菜单点击事件和导航
 */
public final class OfficerMenuListener implements Listener {
    private final OfficerModule officerModule;
    private final Map<UUID, OfficerMenuState> openMenus = new ConcurrentHashMap<>();

    public OfficerMenuListener(OfficerModule officerModule) {
        this.officerModule = officerModule;
    }

    /**
     * 打开官员菜单
     */
    public void openMenu(Player player, Nation nation) {
        OfficerMenu menu = OfficerMenu.create(player, nation, officerModule);
        player.openInventory(menu.getInventory());
        openMenus.put(player.getUniqueId(), new OfficerMenuState(menu, nation));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!openMenus.containsKey(playerId)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !clickedInventory.equals(event.getView().getTopInventory())) {
            return;
        }

        OfficerMenuState state = openMenus.get(playerId);
        OfficerMenu menu = state.menu();
        int slot = event.getSlot();
        boolean isRightClick = event.isRightClick();

        // 处理点击
        boolean shouldClose = handleMenuClick(player, menu, state, slot, isRightClick);

        if (shouldClose) {
            event.setCancelled(true);
            player.closeInventory();
        }
    }

    /**
     * 处理菜单点击
     */
    private boolean handleMenuClick(Player player, OfficerMenu menu, OfficerMenuState state, int slot, boolean isRightClick) {
        MenuPage page = menu.getCurrentPage();
        Nation nation = state.nation();

        switch (page) {
            case MAIN -> {
                // 检查角色槽位
                int[] roleSlots = OfficerMenu.ROLE_SLOTS;
                for (int i = 0; i < roleSlots.length; i++) {
                    if (slot == roleSlots[i]) {
                        List<dev.starcore.starcore.module.officer.OfficerRoleConfig> roles = officerModule.getAllRoleConfigs().stream().toList();
                        if (i < roles.size()) {
                            String roleId = roles.get(i).id();
                            return handleRoleClick(player, menu, nation, roleId, isRightClick);
                        }
                    }
                }

                // 关闭按钮
                if (slot == OfficerMenu.CLOSE_SLOT) {
                    return true;
                }
            }
            case SELECT_PLAYER -> {
                // 返回按钮
                if (slot == 45) {
                    return false;
                }

                // 检查成员槽位 (19-44)
                if (slot >= 19 && slot <= 44) {
                    ItemStack item = menu.getInventory().getItem(slot);
                    if (item != null && item.getType() == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta skullMeta) {
                        OfflinePlayer owningPlayer = skullMeta.getOwningPlayer();
                        if (owningPlayer != null) {
                            UUID targetId = owningPlayer.getUniqueId();
                            return handleMemberClick(player, menu, nation, targetId);
                        }
                    }
                }
            }
            case CONFIRM_REMOVE -> {
                if (slot == 22) {
                    // 确认移除
                    return handleConfirmRemove(player, menu, nation);
                } else if (slot == 24) {
                    // 取消 - 返回主菜单
                    return false;
                }
            }
        }

        // 忽略其他点击
        return false;
    }

    /**
     * 处理角色点击
     */
    private boolean handleRoleClick(Player player, OfficerMenu menu, Nation nation, String roleId, boolean isRightClick) {
        if (nation == null) {
            player.sendMessage(MessageUtil.colorize("&c你没有国家，无法管理官员！"));
            return true;
        }

        var officer = officerModule.officer(nation.id(), roleId);
        String roleName = officerModule.getRoleDisplayName(roleId);

        if (isRightClick && officer.isPresent()) {
            // 右键 - 移除
            menu.setSelectedRole(roleId);
            openConfirmRemoveMenu(player, nation, roleId);
            return false;
        } else {
            // 左键 - 任命/更换
            menu.setSelectedRole(roleId);
            openSelectPlayerMenu(player, nation, roleId);
            return false;
        }
    }

    /**
     * 处理成员点击（任命）
     */
    private boolean handleMemberClick(Player player, OfficerMenu menu, Nation nation, UUID targetId) {
        String roleId = menu.getSelectedRole();
        if (nation == null || roleId == null) {
            return true;
        }

        // 获取玩家名称
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetId);
        String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

        // 检查是否已是其他官员
        Optional<String> existingRole = officerModule.getPlayerOfficerRole(nation.id(), targetId);
        if (existingRole.isPresent() && !existingRole.get().equals(roleId)) {
            // 先移除旧职位
            officerModule.remove(nation.id(), existingRole.get());
            String oldRoleName = officerModule.getRoleDisplayName(existingRole.get());
            player.sendMessage(MessageUtil.colorize("&7已移除 " + playerName + " 的 &c" + oldRoleName + " &7职位"));
        }

        // 任命新职位
        boolean success = officerModule.appoint(nation.id(), roleId, targetId, playerName);

        if (success) {
            String roleName = officerModule.getRoleDisplayName(roleId);
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
    private boolean handleConfirmRemove(Player player, OfficerMenu menu, Nation nation) {
        String roleId = menu.getSelectedRole();
        if (nation == null || roleId == null) {
            return true;
        }

        var officer = officerModule.officer(nation.id(), roleId);
        if (officer.isEmpty()) {
            return true;
        }

        String playerName = officer.get().playerName();
        String roleName = officerModule.getRoleDisplayName(roleId);

        boolean success = officerModule.remove(nation.id(), roleId);

        if (success) {
            player.sendMessage(MessageUtil.colorize("&c已移除 &e" + playerName + " &c的 &f" + roleName + " &c职位"));
        } else {
            player.sendMessage(MessageUtil.colorize("&c移除失败！"));
        }

        return true; // 关闭菜单
    }

    /**
     * 打开选择玩家菜单
     */
    private void openSelectPlayerMenu(Player player, Nation nation, String roleId) {
        OfficerMenu newMenu = new OfficerMenu(player, nation, officerModule, MenuPage.SELECT_PLAYER);
        newMenu.setSelectedRole(roleId);
        player.openInventory(newMenu.getInventory());
        openMenus.put(player.getUniqueId(), new OfficerMenuState(newMenu, nation));
    }

    /**
     * 打开确认移除菜单
     */
    private void openConfirmRemoveMenu(Player player, Nation nation, String roleId) {
        OfficerMenu newMenu = new OfficerMenu(player, nation, officerModule, MenuPage.CONFIRM_REMOVE);
        newMenu.setSelectedRole(roleId);
        player.openInventory(newMenu.getInventory());
        openMenus.put(player.getUniqueId(), new OfficerMenuState(newMenu, nation));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }

    // audit H-003: 修复 PlayerQuitEvent 未清理 openMenus Map 导致的内存泄漏
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 菜单状态记录
     */
    private record OfficerMenuState(OfficerMenu menu, Nation nation) {}
}
