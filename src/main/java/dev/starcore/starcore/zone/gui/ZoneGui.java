package dev.starcore.starcore.zone.gui;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.zone.ZoneEffect;
import dev.starcore.starcore.zone.ZoneModule;
import dev.starcore.starcore.zone.ZoneService;
import dev.starcore.starcore.zone.ZoneSnapshot;
import dev.starcore.starcore.zone.ZoneType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 经济区GUI管理器
 */
public class ZoneGui {

    private final ZoneModule zoneModule;
    private final MessageService messages;
    private final Player player;
    private final NationId nationId;

    // 当前查看的经济区ID（用于返回）
    private UUID currentZoneId = null;

    // 等待输入状态
    private static final Map<UUID, InputWaitingState> waitingForInput = new java.util.concurrent.ConcurrentHashMap<>();

    // 输入等待状态
    private record InputWaitingState(String action, ZoneType type, int level) {}

    private static final String MAIN_MENU_TITLE = "§6§l经济区管理";
    private static final String ZONE_INFO_TITLE = "§5§l经济区详情";
    private static final String CREATE_ZONE_TITLE = "§a§l创建经济区";
    private static final String EFFECTS_MENU_TITLE = "§b§l特效管理";

    public ZoneGui(ZoneModule zoneModule, MessageService messages, Player player, NationId nationId) {
        this.zoneModule = zoneModule;
        this.messages = messages;
        this.player = player;
        this.nationId = nationId;
    }

    /**
     * 打开经济区主菜单
     */
    public void openMainMenu() {
        Inventory gui = Bukkit.createInventory(null, 54, MAIN_MENU_TITLE);
        Collection<ZoneSnapshot> zones = zoneModule.zonesOf(nationId);
        List<ZoneSnapshot> zoneList = new ArrayList<>(zones);

        // 显示经济区列表
        int slot = 0;
        for (ZoneSnapshot zone : zoneList) {
            if (slot < 45) {
                gui.setItem(slot, createZoneItem(zone));
                slot++;
            }
        }

        // 添加按钮
        // 创建经济区按钮
        if (zoneList.size() < zoneModule.zoneLimitFor(nationId)) {
            gui.setItem(49, createButton(Material.LIME_WOOL, "§a§l创建经济区", "§7点击创建新的经济区"));
        }

        // 统计信息
        gui.setItem(48, createButton(Material.PAPER, "§e§l统计信息",
            "§7经济区数量: §f" + zoneList.size() + "/" + zoneModule.zoneLimitFor(nationId) + "\n" +
            "§7总税收加成: §f" + String.format("%.1f%%", zoneModule.getTotalTaxBonus(nationId) * 100) + "\n" +
            "§7总产出加成: §f" + String.format("%.1f%%", zoneModule.getTotalProductionBonus(nationId) * 100)));

        // 返回按钮
        gui.setItem(53, createButton(Material.BARRIER, "§c§l关闭", "§7关闭菜单"));

        player.openInventory(gui);
    }

    /**
     * 打开经济区详情菜单
     */
    public void openZoneInfo(UUID zoneId) {
        Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(zoneId);
        if (zoneOpt.isEmpty()) {
            player.sendMessage("§c经济区不存在");
            return;
        }

        ZoneSnapshot zone = zoneOpt.get();
        if (!zone.nationId().equals(nationId)) {
            player.sendMessage("§c该经济区不属于你的国家");
            return;
        }

        // 保存当前查看的经济区ID（用于返回）
        this.currentZoneId = zone.id();

        Inventory gui = Bukkit.createInventory(null, 36, ZONE_INFO_TITLE + " - " + zone.name());

        // 经济区信息
        gui.setItem(4, createZoneDetailItem(zone));

        // 升级按钮
        if (zone.type().getMaxLevel() > zone.level()) {
            gui.setItem(11, createButton(Material.EXPERIENCE_BOTTLE,
                "§e§l升级经济区",
                "§7当前等级: §f" + zone.level() + "\n" +
                "§7最高等级: §f" + zone.type().getMaxLevel() + "\n" +
                "§7点击升级"));
        } else {
            gui.setItem(11, createButton(Material.BARRIER,
                "§c§l已达最高等级",
                "§7当前等级: §f" + zone.level()));
        }

        // 特效管理
        gui.setItem(13, createButton(Material.BLAZE_POWDER,
            "§b§l特效管理",
            "§7当前特效: §f" + zone.effects().size() + "个\n" +
            "§7点击管理特效"));

        // 删除经济区
        gui.setItem(15, createButton(Material.REDSTONE_BLOCK,
            "§c§l删除经济区",
            "§7点击删除经济区\n" +
            "§c警告: 此操作不可恢复"));

        // 启用/停用
        if (zone.active()) {
            gui.setItem(22, createButton(Material.LEVER,
                "§6§l停用经济区",
                "§7点击停用经济区"));
        } else {
            gui.setItem(22, createButton(Material.LEVER,
                "§a§l启用经济区",
                "§7点击启用经济区"));
        }

        // 返回按钮
        gui.setItem(31, createButton(Material.ARROW, "§f§l返回", "§7返回上一页"));

        player.openInventory(gui);
    }

    /**
     * 打开创建经济区菜单
     */
    public void openCreateZoneMenu() {
        Inventory gui = Bukkit.createInventory(null, 36, CREATE_ZONE_TITLE);

        // 显示所有经济区类型
        ZoneType[] types = ZoneType.values();
        for (int i = 0; i < types.length && i < 27; i++) {
            gui.setItem(i, createZoneTypeItem(types[i]));
        }

        // 返回按钮
        gui.setItem(31, createButton(Material.ARROW, "§f§l返回", "§7返回上一页", "§a从主菜单进入"));

        player.openInventory(gui);
    }

    /**
     * 打开特效管理菜单
     */
    public void openEffectsMenu(UUID zoneId) {
        Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(zoneId);
        if (zoneOpt.isEmpty()) {
            return;
        }

        ZoneSnapshot zone = zoneOpt.get();
        // 保存当前查看的经济区ID（用于返回）
        this.currentZoneId = zone.id();

        Inventory gui = Bukkit.createInventory(null, 54, EFFECTS_MENU_TITLE + " - " + zone.name());

        // 当前特效
        int slot = 0;
        for (ZoneEffect effect : zone.effects()) {
            if (slot < 27) {
                gui.setItem(slot, createEffectItem(effect, true));
                slot++;
            }
        }

        // 分隔线
        gui.setItem(35, createButton(Material.GLASS, "§7--- 已拥有特效 ---", ""));

        // 可添加特效
        slot = 36;
        Set<ZoneEffect> currentEffects = new HashSet<>(zone.effects());
        for (ZoneEffect effect : ZoneEffect.values()) {
            if (!currentEffects.contains(effect) && slot < 54) {
                gui.setItem(slot, createEffectItem(effect, false));
                slot++;
            }
        }

        // 返回按钮
        gui.setItem(49, createButton(Material.ARROW, "§f§l返回", "§7返回上一页"));

        player.openInventory(gui);
    }

    /**
     * 创建经济区物品图标
     */
    private ItemStack createZoneItem(ZoneSnapshot zone) {
        Material material = Material.matchMaterial(zone.type().getIcon());
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        StringBuilder lore = new StringBuilder();
        lore.append("§7类型: §f").append(zone.type().getDisplayName()).append("\n");
        lore.append("§7等级: §f").append(zone.level()).append("/").append(zone.type().getMaxLevel()).append("\n");
        lore.append("§7税收加成: §f").append(String.format("%.1f%%", zone.taxBonus() * 100)).append("\n");
        lore.append("§7产出加成: §f").append(String.format("%.1f%%", zone.productionBonus() * 100)).append("\n");
        lore.append("§7特效: §f").append(zone.effects().size()).append("个\n");
        lore.append("§7状态: ").append(zone.active() ? "§a启用" : "§c停用").append("\n");
        lore.append("\n§e点击查看详情");

        meta.setDisplayName("§6§l" + zone.name());
        meta.setLore(Arrays.asList(lore.toString().split("\n")));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建经济区详情物品
     */
    private ItemStack createZoneDetailItem(ZoneSnapshot zone) {
        Material material = Material.BEACON;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        StringBuilder lore = new StringBuilder();
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7名称: §f").append(zone.name()).append("\n");
        lore.append("§7类型: §f").append(zone.type().getDisplayName()).append("\n");
        lore.append("§7等级: §f").append(zone.level()).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7税收加成: §a+").append(String.format("%.1f%%", zone.taxBonus() * 100)).append("\n");
        lore.append("§7产出加成: §a+").append(String.format("%.1f%%", zone.productionBonus() * 100)).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7特效数量: §f").append(zone.effects().size()).append("个\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7创建时间: §f").append(formatter.format(LocalDateTime.ofEpochSecond(zone.createdAt() / 1000, 0, java.time.ZoneOffset.UTC))).append("\n");
        lore.append("§7状态: ").append(zone.active() ? "§a正常" : "§c已停用").append("\n");
        lore.append("§7━━━━━━━━━━━━━━━");

        meta.setDisplayName("§e§l" + zone.name());
        meta.setLore(Arrays.asList(lore.toString().split("\n")));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建经济区类型物品
     */
    private ItemStack createZoneTypeItem(ZoneType type) {
        Material material = Material.matchMaterial(type.getIcon());
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        StringBuilder lore = new StringBuilder();
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7类型: §f").append(type.getDisplayName()).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7建筑费用: §f").append(String.format("%.0f", type.getBuildCost())).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7税收加成/级: §a+").append(String.format("%.1f%%", type.getTaxBonusPerLevel() * 100)).append("\n");
        lore.append("§7产出加成/级: §a+").append(String.format("%.1f%%", type.getProductionBonusPerLevel() * 100)).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7最高等级: §f").append(type.getMaxLevel()).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§e点击选择此类型");

        meta.setDisplayName("§6§l" + type.getDisplayName());
        meta.setLore(Arrays.asList(lore.toString().split("\n")));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建特效物品
     */
    private ItemStack createEffectItem(ZoneEffect effect, boolean owned) {
        Material material = Material.matchMaterial(effect.getIcon());
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        StringBuilder lore = new StringBuilder();
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7名称: §f").append(effect.getDisplayName()).append("\n");
        lore.append("§7类型: §f").append(effect.getType().getDisplayName()).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7").append(effect.getDescription()).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");
        lore.append("§7加成值: ").append(effect.getBonus() > 0 ? "§a+" : "§c")
            .append(String.format("%.1f%%", effect.getBonus() * 100)).append("\n");
        lore.append("§7━━━━━━━━━━━━━━━\n");

        if (owned) {
            lore.append("§c点击移除特效");
        } else {
            lore.append("§a点击添加特效");
        }

        meta.setDisplayName((owned ? "§c" : "§a") + "§l" + effect.getDisplayName());
        meta.setLore(Arrays.asList(lore.toString().split("\n")));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建按钮物品
     */
    private ItemStack createButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            if (line != null && !line.isEmpty()) {
                loreList.add(line);
            }
        }
        if (!loreList.isEmpty()) {
            meta.setLore(loreList);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 处理GUI点击
     */
    public boolean handleClick(int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        String title = player.getOpenInventory().getTitle();

        if (title.equals(MAIN_MENU_TITLE)) {
            return handleMainMenuClick(slot, item);
        } else if (title.startsWith(ZONE_INFO_TITLE)) {
            return handleZoneInfoClick(slot, item);
        } else if (title.equals(CREATE_ZONE_TITLE)) {
            return handleCreateZoneClick(slot, item);
        } else if (title.startsWith(EFFECTS_MENU_TITLE)) {
            return handleEffectsMenuClick(slot, item);
        }

        return false;
    }

    private boolean handleMainMenuClick(int slot, ItemStack item) {
        // 关闭按钮
        if (slot == 53) {
            player.closeInventory();
            return true;
        }

        // 创建经济区
        if (slot == 49) {
            openCreateZoneMenu();
            return true;
        }

        // 经济区列表
        if (slot >= 0 && slot < 45) {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                // 从物品名称中提取经济区名称
                String name = item.getItemMeta().getDisplayName().replace("§6§l", "");
                zoneModule.zonesOf(nationId).stream()
                    .filter(z -> z.name().equals(name))
                    .findFirst()
                    .ifPresent(z -> openZoneInfo(z.id()));
                return true;
            }
        }

        return false;
    }

    private boolean handleZoneInfoClick(int slot, ItemStack item) {
        // 返回按钮 (槽31)
        if (slot == 31) {
            openMainMenu();
            return true;
        }

        // 升级经济区 (槽11)
        if (slot == 11 && currentZoneId != null) {
            Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(currentZoneId);
            if (zoneOpt.isPresent()) {
                ZoneSnapshot zone = zoneOpt.get();
                if (zone.level() < zone.type().getMaxLevel()) {
                    // TODO: 调用 zoneModule.upgradeZone 或类似方法
                    player.sendMessage("§a正在升级经济区... (功能开发中)");
                }
            }
            return true;
        }

        // 特效管理 (槽13)
        if (slot == 13 && currentZoneId != null) {
            openEffectsMenu(currentZoneId);
            return true;
        }

        // 删除经济区 (槽15)
        if (slot == 15 && currentZoneId != null) {
            Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(currentZoneId);
            if (zoneOpt.isPresent()) {
                ZoneSnapshot zone = zoneOpt.get();
                // TODO: 调用 zoneModule.deleteZone 或类似方法
                player.sendMessage("§c正在删除经济区: " + zone.name() + " (功能开发中)");
                player.closeInventory();
            }
            return true;
        }

        // 启用/停用 (槽22)
        if (slot == 22 && currentZoneId != null) {
            Optional<ZoneSnapshot> zoneOpt = zoneModule.zoneById(currentZoneId);
            if (zoneOpt.isPresent()) {
                ZoneSnapshot zone = zoneOpt.get();
                // TODO: 调用 zoneModule.toggleZoneActive 或类似方法
                String action = zone.active() ? "停用" : "启用";
                player.sendMessage("§a正在" + action + "经济区... (功能开发中)");
            }
            return true;
        }

        return false;
    }

    private boolean handleCreateZoneClick(int slot, ItemStack item) {
        // 返回按钮
        if (slot == 31) {
            openMainMenu();
            return true;
        }

        // 选择类型 (槽0-26)
        if (slot >= 0 && slot < 27) {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                // 从显示名称查找经济区类型
                String displayName = item.getItemMeta().getDisplayName().replace("§6§l", "");
                ZoneType selectedType = null;
                for (ZoneType type : ZoneType.values()) {
                    if (type.getDisplayName().equals(displayName)) {
                        selectedType = type;
                        break;
                    }
                }

                if (selectedType != null) {
                    // 记录等待输入状态
                    waitingForInput.put(player.getUniqueId(),
                        new InputWaitingState("create_zone", selectedType, 1));
                    player.sendMessage("§a请在聊天框输入经济区名称 (输入 'cancel' 取消)");
                    player.closeInventory();
                }
            }
            return true;
        }

        return false;
    }

    private boolean handleEffectsMenuClick(int slot, ItemStack item) {
        // 返回按钮 (槽49)
        if (slot == 49) {
            if (currentZoneId != null) {
                openZoneInfo(currentZoneId);
            } else {
                openMainMenu();
            }
            return true;
        }

        // 添加/移除特效 (槽0-34 为拥有的特效, 槽36-48 为可添加的特效)
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String effectName = item.getItemMeta().getDisplayName()
                .replace("§a§l", "")
                .replace("§c§l", "");
            player.sendMessage("§e特效功能开发中: " + effectName);
            return true;
        }

        return false;
    }

    /**
     * 处理聊天输入（由 ZoneGuiListener 调用）
     */
    public void handleNameInput(String input) {
        InputWaitingState state = waitingForInput.remove(player.getUniqueId());
        if (state == null) {
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage("§c已取消创建经济区");
            return;
        }

        // 验证名称
        if (input.length() < 2 || input.length() > 16) {
            player.sendMessage("§c经济区名称长度必须在2-16个字符之间");
            return;
        }

        // TODO: 调用 zoneModule.createZone 或类似方法
        player.sendMessage("§a正在创建经济区: " + input + " (类型: " + state.type().getDisplayName() + ") (功能开发中)");
    }
}