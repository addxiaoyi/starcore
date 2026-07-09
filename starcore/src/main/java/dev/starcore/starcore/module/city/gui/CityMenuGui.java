package dev.starcore.starcore.module.city.gui;
import java.util.Optional;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.city.CityService;
import dev.starcore.starcore.module.city.model.City;
import dev.starcore.starcore.module.city.model.CityRank;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
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

import java.math.BigDecimal;
import java.util.*;

/**
 * 城市管理菜单 GUI
 */
public final class CityMenuGui implements InventoryHolder {
    private static final int SIZE = 54;

    private final Player player;
    private final CityService cityService;
    private final NationService nationService;
    private final EconomyService economyService;
    private final Optional<City> playerCity;

    private final Inventory inventory;

    public CityMenuGui(Player player, CityService cityService, NationService nationService, EconomyService economyService) {
        this.player = player;
        this.cityService = cityService;
        this.nationService = nationService;
        this.economyService = economyService;
        this.playerCity = cityService.getPlayerCity(player.getUniqueId());

        this.inventory = Bukkit.createInventory(this, SIZE,
            Component.text(playerCity.map(c -> "城市: " + c.name()).orElse("城市菜单"), NamedTextColor.GOLD));
        buildMenu();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * 构建菜单
     */
    private void buildMenu() {
        // 标题区域
        inventory.setItem(4, createTitleItem());

        if (playerCity.isEmpty()) {
            // 未加入城市
            inventory.setItem(22, createNoCityItem());
            inventory.setItem(20, createJoinCityItem());
            inventory.setItem(24, createCreateCityInfoItem());
        } else {
            City city = playerCity.get();

            // 城市信息
            inventory.setItem(10, createCityInfoItem(city));
            inventory.setItem(11, createMembersItem(city));

            // 管理功能
            if (city.isMayor(player.getUniqueId())) {
                inventory.setItem(19, createInviteItem(city));
                inventory.setItem(20, createKickItem(city));
                inventory.setItem(21, createPromoteItem(city));
                inventory.setItem(28, createSettingsItem(city));
                inventory.setItem(29, createAnnouncementItem(city));
            }

            // 经济功能
            inventory.setItem(25, createDepositItem(city));
            if (city.isMayor(player.getUniqueId())) {
                inventory.setItem(26, createWithdrawItem(city));
            }

            // 导航
            inventory.setItem(40, createTeleportItem(city));
            inventory.setItem(49, createLeaveItem(city));
        }

        // 排行榜入口
        inventory.setItem(45, createTopItem());

        // 关闭按钮
        inventory.setItem(53, createCloseButton());
    }

    // ==================== 基础组件 ====================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        String cityName = playerCity.map(City::name).orElse("未加入城市");
        meta.displayName(Component.text(cityName, NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("=== 城市管理系统 ===", NamedTextColor.YELLOW));
        if (playerCity.isPresent()) {
            City city = playerCity.get();
            lore.add(Component.text("等级: Lv." + city.level(), NamedTextColor.GRAY));
            lore.add(Component.text("居民: " + city.residentCount() + "/" + city.getMaxResidents(), NamedTextColor.GRAY));
            lore.add(Component.text("国库: " + String.format("%.2f", city.treasury()) + " 金币", NamedTextColor.GOLD));
        } else {
            lore.add(Component.text("你还没有加入任何城市", NamedTextColor.RED));
            lore.add(Component.text("可以加入现有城市或创建新城市", NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNoCityItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("你还未加入城市", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("请先加入一个城市或创建新城市", NamedTextColor.GRAY));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createJoinCityItem() {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("加入城市", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("查看并加入已有城市", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("当前可用城市: " + cityService.getAllCities().size(), NamedTextColor.YELLOW));
        lore.add(Component.text(""));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCreateCityInfoItem() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("创建城市", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("使用 /city create <名称> 创建", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("要求:", NamedTextColor.YELLOW));
        lore.add(Component.text("- 必须是国家成员", NamedTextColor.GRAY));
        lore.add(Component.text("- 是国家创建者", NamedTextColor.GRAY));
        lore.add(Component.text("- 不在其他城市中", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 城市信息 ====================

    private ItemStack createCityInfoItem(City city) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("城市信息", NamedTextColor.WHITE));

        Optional<Nation> nation = nationService.nationById(city.nationId());
        String nationName = nation.map(Nation::name).orElse("未知");

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("名称: " + city.name(), NamedTextColor.YELLOW));
        lore.add(Component.text("国家: " + nationName, NamedTextColor.GRAY));
        lore.add(Component.text("等级: Lv." + city.level(), NamedTextColor.GOLD));
        lore.add(Component.text("经验: " + city.experience() + "/" + city.getLevelUpExperience(), NamedTextColor.GRAY));
        lore.add(Component.text("居民: " + city.residentCount() + "/" + city.getMaxResidents(), NamedTextColor.GRAY));
        lore.add(Component.text("国库: " + String.format("%.2f", city.treasury()) + " 金币", NamedTextColor.GOLD));
        lore.add(Component.text("领土: " + city.claimCount() + " 块", NamedTextColor.GRAY));

        String announcement = city.announcement();
        if (announcement != null && !announcement.isEmpty()) {
            lore.add(Component.text("公告: " + announcement, NamedTextColor.YELLOW));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMembersItem(City city) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (item.getItemMeta() instanceof SkullMeta skullMeta) {
            // 获取市长头像
            UUID mayorId = city.getResidentsByRank(CityRank.MAYOR).stream().findFirst().orElse(null);
            if (mayorId != null) {
                OfflinePlayer mayor = Bukkit.getOfflinePlayer(mayorId);
                skullMeta.setOwningPlayer(mayor);
            }

            skullMeta.displayName(Component.text("成员列表", NamedTextColor.WHITE));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));

            for (Map.Entry<UUID, CityRank> entry : city.residents().entrySet()) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
                String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : entry.getKey().toString().substring(0, 8);
                boolean online = Bukkit.getPlayer(entry.getKey()) != null;
                NamedTextColor color = online ? NamedTextColor.GREEN : NamedTextColor.GRAY;

                lore.add(Component.text("  " + name + " [" + entry.getValue().displayName() + "] "
                    + (online ? "在线" : ""), color));
            }

            skullMeta.lore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    // ==================== 管理功能 ====================

    private ItemStack createInviteItem(City city) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("邀请成员", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("邀请玩家加入城市", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击打开邀请菜单", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createKickItem(City city) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("踢出成员", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("踢出城市成员", NamedTextColor.GRAY));
        lore.add(Component.text("仅限踢出普通成员", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击打开踢出菜单", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPromoteItem(City city) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("任命官员", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("任命居民为官员", NamedTextColor.GRAY));
        lore.add(Component.text("官员可以邀请新成员", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击打开任命菜单", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSettingsItem(City city) {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("城市设置", NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("PVP: " + (city.isPvpEnabled() ? "开启" : "关闭"), NamedTextColor.GRAY));
        lore.add(Component.text("公开传送点: " + (city.isPublicSpawn() ? "开启" : "关闭"), NamedTextColor.GRAY));
        lore.add(Component.text("公开招募: " + (city.isOpenRecruitment() ? "开启" : "关闭"), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("点击修改设置", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAnnouncementItem(City city) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        String announcement = city.announcement();
        if (announcement != null && !announcement.isEmpty()) {
            meta.displayName(Component.text("修改公告", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("当前公告:", NamedTextColor.GRAY));
            lore.add(Component.text(announcement, NamedTextColor.YELLOW));
            lore.add(Component.text(""));
            lore.add(Component.text("点击修改公告", NamedTextColor.YELLOW));

            meta.lore(lore);
        } else {
            meta.displayName(Component.text("设置公告", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("暂无公告", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击设置公告", NamedTextColor.YELLOW));

            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ==================== 经济功能 ====================

    private ItemStack createDepositItem(City city) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("存款", NamedTextColor.GOLD));

        BigDecimal balance = economyService.getBalance(player.getUniqueId());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("存入金币到城市国库", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("你的余额: " + balance.toPlainString() + " 金币", NamedTextColor.YELLOW));
        lore.add(Component.text("城市国库: " + String.format("%.2f", city.treasury()) + " 金币", NamedTextColor.GOLD));
        lore.add(Component.text(""));
        lore.add(Component.text("用法: /city deposit <金额>", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWithdrawItem(City city) {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("取款", NamedTextColor.RED));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("从城市国库取款", NamedTextColor.GRAY));
        lore.add(Component.text("仅市长可用", NamedTextColor.RED));
        lore.add(Component.text(""));
        lore.add(Component.text("城市国库: " + String.format("%.2f", city.treasury()) + " 金币", NamedTextColor.GOLD));
        lore.add(Component.text(""));
        lore.add(Component.text("用法: /city withdraw <金额>", NamedTextColor.GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 导航 ====================

    private ItemStack createTeleportItem(City city) {
        ItemStack item;
        ItemMeta meta;

        if (city.spawnChunk() != null) {
            item = new ItemStack(Material.ENDER_PEARL);
            meta = item.getItemMeta();
            meta.displayName(Component.text("传送到城市", NamedTextColor.AQUA));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("传送至城市出生点", NamedTextColor.GRAY));
            lore.add(Component.text(""));
            lore.add(Component.text("点击传送", NamedTextColor.YELLOW));

            meta.lore(lore);
        } else {
            item = new ItemStack(Material.ENDER_EYE);
            meta = item.getItemMeta();
            meta.displayName(Component.text("传送点未设置", NamedTextColor.GRAY));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("城市出生点尚未设置", NamedTextColor.RED));
            lore.add(Component.text(""));
            lore.add(Component.text("使用 /city setspawn 设置", NamedTextColor.GRAY));

            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLeaveItem(City city) {
        ItemStack item;
        ItemMeta meta;

        if (city.isMayor(player.getUniqueId()) && city.residentCount() > 1) {
            item = new ItemStack(Material.BARRIER);
            meta = item.getItemMeta();
            meta.displayName(Component.text("无法离开", NamedTextColor.RED));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("作为市长无法直接离开", NamedTextColor.RED));
            lore.add(Component.text("请先转让市长职位", NamedTextColor.GRAY));

            meta.lore(lore);
        } else {
            item = new ItemStack(Material.WHITE_BED);
            meta = item.getItemMeta();
            meta.displayName(Component.text("离开城市", NamedTextColor.YELLOW));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("离开当前城市", NamedTextColor.GRAY));
            lore.add(Component.text("离开后将重新成为无城市状态", NamedTextColor.RED));
            lore.add(Component.text(""));
            lore.add(Component.text("用法: /city leave", NamedTextColor.GRAY));

            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTopItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("城市排行榜", NamedTextColor.LIGHT_PURPLE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("按居民数排名:", NamedTextColor.GRAY));

        List<City> topResidents = cityService.getTopCitiesByResidents(3);
        for (int i = 0; i < topResidents.size(); i++) {
            City city = topResidents.get(i);
            lore.add(Component.text("  " + (i + 1) + ". " + city.name() + " - " + city.residentCount() + "人", NamedTextColor.GOLD));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("用法: /city top", NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
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

    // ==================== 菜单操作处理 ====================

    public static CityAction getActionFromSlot(int slot) {
        return switch (slot) {
            case 10 -> CityAction.INFO;
            case 11 -> CityAction.MEMBERS;
            case 19 -> CityAction.INVITE;
            case 20 -> CityAction.KICK;
            case 21 -> CityAction.PROMOTE;
            case 22 -> CityAction.JOIN_CITY;
            case 24 -> CityAction.CREATE_INFO;
            case 25 -> CityAction.DEPOSIT;
            case 26 -> CityAction.WITHDRAW;
            case 28 -> CityAction.SETTINGS;
            case 29 -> CityAction.ANNOUNCEMENT;
            case 40 -> CityAction.TELEPORT;
            case 45 -> CityAction.TOP;
            case 49 -> CityAction.LEAVE;
            case 53 -> CityAction.CLOSE;
            default -> CityAction.NONE;
        };
    }

    public enum CityAction {
        NONE,
        INFO,
        MEMBERS,
        INVITE,
        KICK,
        PROMOTE,
        JOIN_CITY,
        CREATE_INFO,
        DEPOSIT,
        WITHDRAW,
        SETTINGS,
        SETTINGS_PUBLIC_SPAWN,
        SETTINGS_RECRUITMENT,
        ANNOUNCEMENT,
        TELEPORT,
        TOP,
        LEAVE,
        CLOSE
    }
}
