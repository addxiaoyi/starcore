package dev.starcore.starcore.module.officer.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 官员 GUI 配置
 * 从 officer-gui.yml 加载配置
 */
public final class OfficerGuiConfig {

    // 配置文件
    private static final String CONFIG_FILE = "officer-gui.yml";

    // 配置文件实例
    private FileConfiguration config;
    private File configFile;
    private JavaPlugin plugin;

    // 主菜单标题
    private String mainTitle = "§6{nation_name} §7| §6官员管理";
    // 选择玩家菜单标题
    private String selectTitle = "§6{nation_name} §7| §6任命 {role_name}";
    // 确认移除菜单标题
    private String confirmTitle = "§6{nation_name} §7| §6确认移除";

    // 帮助按钮
    private Material helpMaterial = Material.BOOK;
    private String helpDisplayName = "§b帮助";
    private List<String> helpLore = List.of(
        "",
        "§7官员系统帮助",
        "",
        "§a任命官员:",
        "§f  点击空缺职位 -> 选择成员",
        "",
        "§c移除官员:",
        "§f  右键点击已有官员职位"
    );

    // 关闭按钮
    private Material closeMaterial = Material.BARRIER;
    private String closeDisplayName = "§c✖ 关闭";
    private List<String> closeLore = List.of();

    // 返回按钮
    private Material backMaterial = Material.ARROW;
    private String backDisplayName = "§e◀ 返回";
    private List<String> backLore = List.of("", "§7返回官员管理主菜单");

    // 确认按钮
    private Material confirmMaterial = Material.LIME_STAINED_GLASS_PANE;
    private String confirmDisplayName = "§a✓ 确认移除";
    private List<String> confirmLore = List.of("", "§a点击确认移除该官员");

    // 取消按钮
    private Material cancelMaterial = Material.RED_STAINED_GLASS_PANE;
    private String cancelDisplayName = "§c✖ 取消";
    private List<String> cancelLore = List.of("", "§7点击返回主菜单");

    // 确认信息物品
    private Material confirmInfoMaterial = Material.BARRIER;
    private String confirmInfoDisplayName = "§c确认移除官员";

    // 填充物
    private Material fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private String fillerDisplayName = " ";

    // 空槽角色显示
    private Material vacantRoleMaterial = Material.PLAYER_HEAD;
    private String vacantRoleDisplayNamePrefix = "§e";
    private List<String> vacantRoleLore = List.of(
        "",
        "§7职位: §f{role_id}",
        "§7当前官员: §c空缺",
        "",
        "§a左键: §7任命官员"
    );

    // 已任命角色显示
    private Material occupiedRoleMaterial = Material.PLAYER_HEAD;
    private String occupiedRoleDisplayNamePrefix = "§e";
    private List<String> occupiedRoleLore = List.of(
        "",
        "§7职位: §f{role_id}",
        "§7当前官员: §a{officer_name}",
        "",
        "§a左键: §7更换官员",
        "§c右键: §7移除官员"
    );

    // 国家信息物品
    private Material nationInfoMaterial = Material.NETHER_STAR;
    private String nationInfoDisplayNamePrefix = "§6§l";
    private List<String> nationInfoLore = List.of(
        "",
        "§7当前官员: §a{officer_count} §7人",
        "",
        "§a左键: §7任命/替换官员",
        "§c右键: §7移除官员",
        "",
        "§e点击官员角色进行管理"
    );

    // 选中角色物品
    private String selectedRoleDisplayName = "§e§l{role_name}";
    private List<String> selectedRoleLore = List.of(
        "",
        "§7选择要任命的成员",
        "",
        "§e点击成员头像进行任命"
    );

    // 成员物品
    private List<String> memberFreeLore = List.of(
        "",
        "§7当前职位: §a无",
        "",
        "§e点击任命"
    );
    private List<String> memberOtherRoleLore = List.of(
        "",
        "§7当前职位: §c{current_role}",
        "§7(将被替换)",
        "",
        "§e点击任命"
    );

    // 确认移除信息
    private List<String> confirmRemoveLore = List.of(
        "",
        "§7职位: §f{role_name}",
        "§7官员: §f{officer_name}",
        "",
        "§c确定要移除此官员吗？"
    );

    // 主菜单配置路径
    private String mainMenuTitle = "officer-main.title";
    private int mainMenuRows = 5;
    // 官职列表配置路径
    private String listMenuTitle = "officer-list.title";
    private int listMenuRows = 6;

    /**
     * 从默认配置创建
     */
    public static OfficerGuiConfig createDefault() {
        return new OfficerGuiConfig();
    }

    /**
     * 从 YAML 配置加载
     * @param plugin 插件实例
     * @return 加载的配置
     */
    public static OfficerGuiConfig load(JavaPlugin plugin) {
        OfficerGuiConfig config = new OfficerGuiConfig();
        config.plugin = plugin;
        config.loadFromYaml();
        return config;
    }

    /**
     * 从 YAML 文件加载配置
     */
    private void loadFromYaml() {
        if (plugin == null) {
            return;
        }

        // 获取配置文件夹
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        configFile = new File(dataFolder, CONFIG_FILE);

        // 如果配置文件不存在，从 jar 包中复制
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource(CONFIG_FILE)) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建 " + CONFIG_FILE + ": " + e.getMessage());
            }
        }

        // 加载配置文件
        config = YamlConfiguration.loadConfiguration(configFile);

        // 读取主菜单配置
        if (config.contains("officer-main.title")) {
            mainMenuTitle = config.getString("officer-main.title", mainMenuTitle);
            mainMenuRows = config.getInt("officer-main.rows", mainMenuRows);
        }

        // 读取官职列表配置
        if (config.contains("officer-list.title")) {
            listMenuTitle = config.getString("officer-list.title", listMenuTitle);
            listMenuRows = config.getInt("officer-list.rows", listMenuRows);
        }

        // 读取帮助按钮配置
        if (config.contains("help.material")) {
            helpMaterial = Material.valueOf(config.getString("help.material", "BOOK"));
            helpDisplayName = config.getString("help.display-name", helpDisplayName);
            helpLore = config.getStringList("help.lore");
        }

        // 读取关闭按钮配置
        if (config.contains("close.material")) {
            closeMaterial = Material.valueOf(config.getString("close.material", "BARRIER"));
            closeDisplayName = config.getString("close.display-name", closeDisplayName);
            closeLore = config.getStringList("close.lore");
        }

        // 读取返回按钮配置
        if (config.contains("back.material")) {
            backMaterial = Material.valueOf(config.getString("back.material", "ARROW"));
            backDisplayName = config.getString("back.display-name", backDisplayName);
            backLore = config.getStringList("back.lore");
        }

        // 读取确认按钮配置
        if (config.contains("confirm.material")) {
            confirmMaterial = Material.valueOf(config.getString("confirm.material", "LIME_STAINED_GLASS_PANE"));
            confirmDisplayName = config.getString("confirm.display-name", confirmDisplayName);
            confirmLore = config.getStringList("confirm.lore");
        }

        // 读取取消按钮配置
        if (config.contains("cancel.material")) {
            cancelMaterial = Material.valueOf(config.getString("cancel.material", "RED_STAINED_GLASS_PANE"));
            cancelDisplayName = config.getString("cancel.display-name", cancelDisplayName);
            cancelLore = config.getStringList("cancel.lore");
        }

        // 读取确认信息配置
        if (config.contains("confirm-info.material")) {
            confirmInfoMaterial = Material.valueOf(config.getString("confirm-info.material", "BARRIER"));
            confirmInfoDisplayName = config.getString("confirm-info.display-name", confirmInfoDisplayName);
        }

        // 读取填充物配置
        if (config.contains("filler.material")) {
            fillerMaterial = Material.valueOf(config.getString("filler.material", "GRAY_STAINED_GLASS_PANE"));
            fillerDisplayName = config.getString("filler.display-name", fillerDisplayName);
        }

        // 读取空槽角色配置
        if (config.contains("vacant-role.material")) {
            vacantRoleMaterial = Material.valueOf(config.getString("vacant-role.material", "PLAYER_HEAD"));
            vacantRoleDisplayNamePrefix = config.getString("vacant-role.display-name-prefix", vacantRoleDisplayNamePrefix);
            vacantRoleLore = config.getStringList("vacant-role.lore");
        }

        // 读取已任命角色配置
        if (config.contains("occupied-role.material")) {
            occupiedRoleMaterial = Material.valueOf(config.getString("occupied-role.material", "PLAYER_HEAD"));
            occupiedRoleDisplayNamePrefix = config.getString("occupied-role.display-name-prefix", occupiedRoleDisplayNamePrefix);
            occupiedRoleLore = config.getStringList("occupied-role.lore");
        }

        // 读取国家信息配置
        if (config.contains("nation-info.material")) {
            nationInfoMaterial = Material.valueOf(config.getString("nation-info.material", "NETHER_STAR"));
            nationInfoDisplayNamePrefix = config.getString("nation-info.display-name-prefix", nationInfoDisplayNamePrefix);
            nationInfoLore = config.getStringList("nation-info.lore");
        }

        // 读取选中角色配置
        if (config.contains("selected-role.display-name")) {
            selectedRoleDisplayName = config.getString("selected-role.display-name", selectedRoleDisplayName);
            selectedRoleLore = config.getStringList("selected-role.lore");
        }

        // 读取成员配置
        if (config.contains("member.free-lore")) {
            memberFreeLore = config.getStringList("member.free-lore");
            memberOtherRoleLore = config.getStringList("member.other-role-lore");
        }

        // 读取确认移除配置
        if (config.contains("confirm-remove.lore")) {
            confirmRemoveLore = config.getStringList("confirm-remove.lore");
        }

        plugin.getLogger().info("成功加载 " + CONFIG_FILE + " 配置");
    }

    // Getter 方法

    public String getMainTitle() { return mainTitle; }
    public String getSelectTitle() { return selectTitle; }
    public String getConfirmTitle() { return confirmTitle; }
    public String getMainMenuTitle() { return mainMenuTitle; }
    public int getMainMenuRows() { return mainMenuRows; }
    public String getListMenuTitle() { return listMenuTitle; }
    public int getListMenuRows() { return listMenuRows; }

    public Material getHelpMaterial() { return helpMaterial; }
    public String getHelpDisplayName() { return helpDisplayName; }
    public List<String> getHelpLore() { return helpLore; }

    public Material getCloseMaterial() { return closeMaterial; }
    public String getCloseDisplayName() { return closeDisplayName; }
    public List<String> getCloseLore() { return closeLore; }

    public Material getBackMaterial() { return backMaterial; }
    public String getBackDisplayName() { return backDisplayName; }
    public List<String> getBackLore() { return backLore; }

    public Material getConfirmMaterial() { return confirmMaterial; }
    public String getConfirmDisplayName() { return confirmDisplayName; }
    public List<String> getConfirmLore() { return confirmLore; }

    public Material getCancelMaterial() { return cancelMaterial; }
    public String getCancelDisplayName() { return cancelDisplayName; }
    public List<String> getCancelLore() { return cancelLore; }

    public Material getConfirmInfoMaterial() { return confirmInfoMaterial; }
    public String getConfirmInfoDisplayName() { return confirmInfoDisplayName; }

    public Material getFillerMaterial() { return fillerMaterial; }
    public String getFillerDisplayName() { return fillerDisplayName; }

    public Material getVacantRoleMaterial() { return vacantRoleMaterial; }
    public String getVacantRoleDisplayNamePrefix() { return vacantRoleDisplayNamePrefix; }
    public List<String> getVacantRoleLore() { return vacantRoleLore; }

    public Material getOccupiedRoleMaterial() { return occupiedRoleMaterial; }
    public String getOccupiedRoleDisplayNamePrefix() { return occupiedRoleDisplayNamePrefix; }
    public List<String> getOccupiedRoleLore() { return occupiedRoleLore; }

    public Material getNationInfoMaterial() { return nationInfoMaterial; }
    public String getNationInfoDisplayNamePrefix() { return nationInfoDisplayNamePrefix; }
    public List<String> getNationInfoLore() { return nationInfoLore; }

    public String getSelectedRoleDisplayName() { return selectedRoleDisplayName; }
    public List<String> getSelectedRoleLore() { return selectedRoleLore; }

    public List<String> getMemberFreeLore() { return memberFreeLore; }
    public List<String> getMemberOtherRoleLore() { return memberOtherRoleLore; }

    public List<String> getConfirmRemoveLore() { return confirmRemoveLore; }

    /**
     * 替换占位符
     */
    public String replacePlaceholders(String text, Map<String, String> values) {
        if (text == null) return null;
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * 替换物品 Lore 中的占位符
     */
    public List<String> replaceLorePlaceholders(List<String> lore, Map<String, String> values) {
        return lore.stream()
            .map(line -> replacePlaceholders(line, values))
            .toList();
    }
}
