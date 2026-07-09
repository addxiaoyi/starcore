package dev.starcore.starcore.module.officer.gui;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 官员 GUI 配置
 * 从 officer-gui.yml 加载配置
 */
public final class OfficerGuiConfig {

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

    /**
     * 从 YAML 配置加载
     * TODO audit C-033: 实现从 officer-gui.yml 加载配置 —— 当前 load() 无 File/Plugin 参数，
     * 改签名需联动调用方（属 Manager/Service 边界），按任务约束不在本次修复范围。
     * 后续应在 OfficerModule 初始化处传入 Plugin 与 File，再解析 YAML 覆盖默认值。
     */
    public static OfficerGuiConfig load() {
        OfficerGuiConfig config = new OfficerGuiConfig();
        // 目前使用默认配置，后续可以从 YAML 加载
        return config;
    }

    // Getter 方法

    public String getMainTitle() { return mainTitle; }
    public String getSelectTitle() { return selectTitle; }
    public String getConfirmTitle() { return confirmTitle; }

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
