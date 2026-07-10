package dev.starcore.starcore.module.policy.gui;

import dev.starcore.starcore.module.policy.PolicyModule;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.config.YamlPolicyDefinitionLoader;
import dev.starcore.starcore.module.policy.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * 政策创建菜单
 * 允许玩家选择政策类型并激活
 */
public class PolicyCreationMenu {

    public static final String MENU_TITLE = "Create Policy";
    public static final int MENU_SIZE = 36;

    private final PolicyModule policyModule;
    private final PolicyService policyService;
    private final NationService nationService;
    private final UUID playerId;
    private final NationId nationId;

    // 选中的政策类型
    private PolicyCategory selectedCategory = null;
    private PolicyDefinition selectedPolicy = null;

    public PolicyCreationMenu(
            PolicyModule policyModule,
            PolicyService policyService,
            NationService nationService,
            UUID playerId,
            NationId nationId
    ) {
        this.policyModule = policyModule;
        this.policyService = policyService;
        this.nationService = nationService;
        this.playerId = playerId;
        this.nationId = nationId;
    }

    /**
     * 打开政策选择菜单
     */
    public void openCategorySelection(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text(MENU_TITLE));

        // 填充背景
        fillBackground(inv);

        // 获取所有政策定义
        YamlPolicyDefinitionLoader loader = new YamlPolicyDefinitionLoader();
        List<PolicyDefinition> definitions = loader.loadAllDefinitions();

        // 按类别分组显示
        PolicyCategory[] categories = PolicyCategory.values();
        int slot = 10;
        for (int i = 0; i < categories.length && slot < 17; i++) {
            PolicyCategory category = categories[i];
            List<PolicyDefinition> categoryPolicies = definitions.stream()
                    .filter(p -> p.category() == category)
                    .toList();

            if (!categoryPolicies.isEmpty()) {
                inv.setItem(slot, createCategoryItem(category, categoryPolicies.size()));
                slot++;
                if (slot == 17) slot = 19;
            }
        }

        // 返回按钮
        inv.setItem(31, createBackItem());

        player.openInventory(inv);
    }

    /**
     * 打开指定类别的政策列表
     */
    public void openPolicyList(Player player, PolicyCategory category) {
        this.selectedCategory = category;
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                Component.text(MENU_TITLE + " - " + category.displayName()));

        fillBackground(inv);

        YamlPolicyDefinitionLoader loader = new YamlPolicyDefinitionLoader();
        List<PolicyDefinition> definitions = loader.loadAllDefinitions();

        List<PolicyDefinition> categoryPolicies = definitions.stream()
                .filter(p -> p.category() == category)
                .toList();

        int slot = 10;
        for (PolicyDefinition policy : categoryPolicies) {
            if (slot >= 17 && slot < 26) slot = 19;
            if (slot >= 26) break;

            // 检查是否已激活
            Optional<PolicyDefinition> active = policyService.activePolicyDefinition(nationId);
            boolean isActive = active.isPresent() && active.get().key().equals(policy.key());

            inv.setItem(slot, createPolicyItem(policy, isActive));
            slot++;
        }

        inv.setItem(31, createBackItem());

        player.openInventory(inv);
    }

    /**
     * 打开政策确认菜单
     */
    public void openPolicyConfirm(Player player, PolicyDefinition policy) {
        this.selectedPolicy = policy;
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE,
                Component.text(MENU_TITLE + " - Confirm"));

        fillBackground(inv);

        // 政策信息
        inv.setItem(13, createPolicyDetailItem(policy));

        // 激活按钮
        inv.setItem(22, createActivateButton(policy));

        // 返回按钮
        inv.setItem(31, createBackItem());

        player.openInventory(inv);
    }

    /**
     * 尝试激活政策
     */
    public boolean activatePolicy(Player player) {
        if (selectedPolicy == null) {
            return false;
        }

        // 检查权限
        if (!PermissionUtil.hasNationPermission(player, nationId.value(), NationPermission.POLICY_ACTIVATE)) {
            player.sendMessage(Component.text("你没有权限激活政策", net.kyori.adventure.text.format.NamedTextColor.RED));
            return false;
        }

        // 尝试激活
        PolicyActivationResult result = policyService.activatePolicy(nationId, selectedPolicy.key(), null);

        if (result.successful()) {
            player.sendMessage(Component.text("政策激活成功: " + selectedPolicy.displayName(), net.kyori.adventure.text.format.NamedTextColor.GREEN));
            // 广播事件
            nationService.nationById(nationId).ifPresent(nation -> {
                nation.members().forEach(member -> {
                    Player memberPlayer = Bukkit.getPlayer(member.playerId());
                    if (memberPlayer != null) {
                        memberPlayer.sendMessage(Component.text(
                                nation.name() + " 激活了新政策: " + selectedPolicy.displayName(),
                                net.kyori.adventure.text.format.NamedTextColor.GOLD
                        ));
                    }
                });
            });
            return true;
        } else {
            if (result.message() != null) {
                player.sendMessage(Component.text("政策激活失败: " + result.message(), net.kyori.adventure.text.format.NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("政策激活失败", net.kyori.adventure.text.format.NamedTextColor.RED));
            }
            return false;
        }
    }

    // ==================== UI 构建方法 ====================

    private void fillBackground(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack createCategoryItem(PolicyCategory category, int policyCount) {
        Material material = switch (category) {
            case FISCAL, TAXATION, MONETARY, TRADE, INDUSTRY, ECONOMY -> Material.GOLD_INGOT;
            case DEFENSE, INTELLIGENCE, RECRUITMENT, ARMS, MILITARY -> Material.IRON_SWORD;
            case FOREIGN_POLICY, IMMIGRATION, CULTURAL_EXCHANGE -> Material.PAPER;
            case ADMINISTRATION, EDUCATION, HEALTHCARE, HOUSING, SOCIAL_WELFARE, LABOR, SOCIAL, POLITICAL -> Material.EMERALD;
            case RELIGION, CULTURE, PROPAGANDA -> Material.BOOK;
            case RESOURCE_MANAGEMENT, ENVIRONMENTAL -> Material.OAK_SAPLING;
            default -> Material.NETHER_STAR;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(category.displayName(), net.kyori.adventure.text.format.NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text(policyCount + " 个政策", net.kyori.adventure.text.format.NamedTextColor.GRAY),
                    Component.text("点击查看", net.kyori.adventure.text.format.NamedTextColor.YELLOW)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPolicyItem(PolicyDefinition policy, boolean isActive) {
        Material material = isActive ? Material.LIME_STAINED_GLASS_PANE : Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String status = isActive ? "已激活" : "可激活";
            net.kyori.adventure.text.format.NamedTextColor color = isActive ? net.kyori.adventure.text.format.NamedTextColor.GREEN : net.kyori.adventure.text.format.NamedTextColor.YELLOW;

            meta.displayName(Component.text(policy.displayName(), net.kyori.adventure.text.format.NamedTextColor.GOLD));
            String effectDesc = policy.effects().isEmpty() ? "无" : policy.effects().get(0).description();
            meta.lore(List.of(
                    Component.text("类型: " + policy.category().displayName(), net.kyori.adventure.text.format.NamedTextColor.GRAY),
                    Component.text("状态: " + status, color),
                    Component.text("效果: " + effectDesc, net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN),
                    Component.text("持续: " + formatDuration(policy.durationSeconds()), net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA),
                    Component.text("冷却: " + formatDuration(policy.cooldownSeconds()), net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE),
                    Component.text(" ", net.kyori.adventure.text.format.NamedTextColor.BLACK),
                    Component.text("点击选择", net.kyori.adventure.text.format.NamedTextColor.YELLOW)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPolicyDetailItem(PolicyDefinition policy) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(policy.displayName(), net.kyori.adventure.text.format.NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("═══════════════════════", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            lore.add(Component.text("类别: " + policy.category().displayName(), net.kyori.adventure.text.format.NamedTextColor.AQUA));
            lore.add(Component.text(" ", net.kyori.adventure.text.format.NamedTextColor.BLACK));

            // 效果信息
            if (!policy.effects().isEmpty()) {
                PolicyEffect effect = policy.effects().get(0);
                lore.add(Component.text("效果类型: " + effect.key(), net.kyori.adventure.text.format.NamedTextColor.GREEN));
                lore.add(Component.text("描述: " + effect.description(), net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
                lore.add(Component.text("数值: " + effect.modifier(), net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
                lore.add(Component.text(" ", net.kyori.adventure.text.format.NamedTextColor.BLACK));

                // 限制信息 - 使用 PolicyDefinition 的冷却和冲突机制
                if (policy.cooldownSeconds() > 0) {
                    lore.add(Component.text("冷却时间: " + formatDuration(policy.cooldownSeconds()), net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }

            lore.add(Component.text(" ", net.kyori.adventure.text.format.NamedTextColor.BLACK));
            lore.add(Component.text("═══════════════════════", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            lore.add(Component.text("持续时间: " + formatDuration(policy.durationSeconds()), net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA));
            lore.add(Component.text("冷却时间: " + formatDuration(policy.cooldownSeconds()), net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createActivateButton(PolicyDefinition policy) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("确认激活政策", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("点击确认激活此政策", net.kyori.adventure.text.format.NamedTextColor.YELLOW),
                    Component.text(" ", net.kyori.adventure.text.format.NamedTextColor.BLACK),
                    Component.text("警告: 此操作可能需要消耗国库资金", net.kyori.adventure.text.format.NamedTextColor.RED)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("返回", net.kyori.adventure.text.format.NamedTextColor.RED));
            meta.lore(List.of(
                    Component.text("返回上一级菜单", net.kyori.adventure.text.format.NamedTextColor.GRAY)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatDuration(long seconds) {
        if (seconds < 0) return "永久";
        if (seconds < 60) return seconds + " 秒";
        if (seconds < 3600) return (seconds / 60) + " 分钟";
        if (seconds < 86400) return (seconds / 3600) + " 小时";
        return (seconds / 86400) + " 天";
    }

    // ==================== Getter ====================

    public PolicyCategory getSelectedCategory() {
        return selectedCategory;
    }

    public PolicyDefinition getSelectedPolicy() {
        return selectedPolicy;
    }

    public void setSelectedCategory(PolicyCategory category) {
        this.selectedCategory = category;
    }

    public void setSelectedPolicy(PolicyDefinition policy) {
        this.selectedPolicy = policy;
    }
}
