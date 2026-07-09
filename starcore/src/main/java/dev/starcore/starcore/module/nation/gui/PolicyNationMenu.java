package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyActivationFailure;
import dev.starcore.starcore.module.policy.model.PolicyActivationResult;
import dev.starcore.starcore.module.policy.model.PolicyCategory;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 国策管理菜单
 * 显示国家已激活的国策和可激活国策
 * 点击国策查看详情和激活按钮
 */
public class PolicyNationMenu {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final PolicyService policyService;
    private final TreasuryService treasuryService;
    private final Plugin plugin;
    private final NationModule nationModule;

    public PolicyNationMenu(PolicyService policyService, TreasuryService treasuryService,
                            Plugin plugin, NationModule nationModule) {
        this.policyService = policyService;
        this.treasuryService = treasuryService;
        this.plugin = plugin;
        this.nationModule = nationModule;
    }

    /**
     * 打开国策主菜单
     */
    public void openPolicyMenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l📜 " + nation.name() + " §7| 国策"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：当前激活的国策
        Optional<PolicyDefinition> activeDef = policyService.activePolicyDefinition(nation.id());
        Optional<PolicyRuntimeState> activeState = policyService.activePolicyState(nation.id());

        if (activeDef.isPresent()) {
            PolicyDefinition def = activeDef.get();
            PolicyRuntimeState state = activeState.orElse(null);
            Material topMat = Material.BOOK;
            Component topName = LegacyComponentSerializer.legacyAmpersand().deserialize("§a§l✓ 当前国策: §f" + def.displayName());
            List<Component> topLore = new ArrayList<>();
            topLore.add(Component.text(""));
            topLore.add(Component.text("§7状态: §a激活中", NamedTextColor.GREEN));
            if (state != null) {
                if (state.isExpiredAt(Instant.now())) {
                    topLore.add(Component.text("§7剩余时间: §c已过期", NamedTextColor.RED));
                } else {
                    long remaining = state.expiresAt().getEpochSecond() - Instant.now().getEpochSecond();
                    topLore.add(Component.text("§7剩余时间: §e" + formatDuration(remaining), NamedTextColor.YELLOW));
                }
                topLore.add(Component.text("§7效果: " + effectSummary(def.effects())));
            }
            topLore.add(Component.text(""));
            topLore.add(Component.text("§a▸ 点击查看详情", NamedTextColor.YELLOW));

            gui.setItem(4, createGuiItem(topMat, topName, topLore, event -> {
                event.setCancelled(true);
                openPolicyDetailMenu(player, nation, def.key());
            }, true));
        } else {
            gui.setItem(4, createGuiItem(
                Material.BOOK,
                LegacyComponentSerializer.legacyAmpersand().deserialize("§c§l✗ 无激活国策"),
                List.of(
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7当前没有激活的国策"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7选择一个国策激活"),
                    Component.text("")
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 国策列表（槽位 10-43，跳过边框）
        Collection<PolicyDefinition> allPolicies = policyService.policyDefinitions();
        Collection<String> unlockedPolicies = policyService.unlockedPolicies(nation.id());
        String activeKey = policyService.activePolicy(nation.id()).orElse(null);

        // 按分类组织国策
        List<PolicyDefinition> economyPolicies = new ArrayList<>();
        List<PolicyDefinition> militaryPolicies = new ArrayList<>();
        List<PolicyDefinition> internalPolicies = new ArrayList<>();
        List<PolicyDefinition> diplomacyPolicies = new ArrayList<>();
        List<PolicyDefinition> resourcePolicies = new ArrayList<>();
        List<PolicyDefinition> culturalPolicies = new ArrayList<>();

        for (PolicyDefinition def : allPolicies) {
            PolicyCategory category = def.category();
            PolicyCategory.PolicyCategoryGroup group = category.group();
            switch (group) {
                case ECONOMY -> economyPolicies.add(def);
                case MILITARY -> militaryPolicies.add(def);
                case INTERNAL -> internalPolicies.add(def);
                case DIPLOMACY -> diplomacyPolicies.add(def);
                case RESOURCE -> resourcePolicies.add(def);
                case CULTURAL -> culturalPolicies.add(def);
            }
        }

        int slot = 10;
        // 经济国策
        slot = fillPolicyCategory(gui, player, nation, economyPolicies, unlockedPolicies,
            activeKey, slot, "§b经济", Material.GOLD_INGOT);
        // 军事国策
        slot = fillPolicyCategory(gui, player, nation, militaryPolicies, unlockedPolicies,
            activeKey, slot, "§c军事", Material.DIAMOND_SWORD);
        // 内政国策
        slot = fillPolicyCategory(gui, player, nation, internalPolicies, unlockedPolicies,
            activeKey, slot, "§a内政", Material.BRICKS);
        // 外交国策
        slot = fillPolicyCategory(gui, player, nation, diplomacyPolicies, unlockedPolicies,
            activeKey, slot, "§3外交", Material.EMERALD);
        // 资源国策
        slot = fillPolicyCategory(gui, player, nation, resourcePolicies, unlockedPolicies,
            activeKey, slot, "§6资源", Material.DIAMOND);
        // 文化国策
        if (!culturalPolicies.isEmpty()) {
            slot = fillPolicyCategory(gui, player, nation, culturalPolicies, unlockedPolicies,
                activeKey, slot, "§d文化", Material.NETHER_STAR);
        }

        // 底部功能按钮
        // 国策效果统计
        gui.setItem(46, createGuiItem(
            Material.NETHER_STAR,
            Component.text("§e§l📊 效果统计", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7查看当前国策的效果汇总"),
                Component.text(""),
                Component.text("§a▸ 点击打开")
            ),
            event -> {
                event.setCancelled(true);
                openPolicyStatsMenu(player, nation);
            },
            false
        ));

        // 国策冷却
        gui.setItem(48, createGuiItem(
            Material.CLOCK,
            Component.text("§e§l⏱ 冷却状态", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7查看所有国策的冷却状态"),
                Component.text(""),
                Component.text("§a▸ 点击打开")
            ),
            event -> {
                event.setCancelled(true);
                openPolicyCooldownMenu(player, nation);
            },
            false
        ));

        // 帮助按钮
        gui.setItem(52, createGuiItem(
            Material.BOOK,
            Component.text("§e§l❓ 帮助", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国策系统说明和操作指南"),
                Component.text(""),
                Component.text("§a▸ 点击打开")
            ),
            event -> {
                event.setCancelled(true);
                openPolicyHelpMenu(player, nation);
            },
            false
        ));

        // 返回主菜单按钮
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                plugin.getServer().getScheduler().runTask(plugin,
                    () -> openMainMenu(player));
            },
            false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 按分类填充国策
     */
    private int fillPolicyCategory(Gui gui, Player player, Nation nation,
                                   List<PolicyDefinition> policies,
                                   Collection<String> unlockedPolicies,
                                   String activeKey,
                                   int startSlot,
                                   String categoryLabel,
                                   Material iconMat) {
        int slot = startSlot;

        for (PolicyDefinition def : policies) {
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot > 43) break;

            boolean unlocked = unlockedPolicies.contains(def.key());
            boolean isActive = def.key().equals(activeKey);
            boolean canActivate = !isActive && unlocked;
            boolean isCooldown = false;

            // 检查冷却
            if (!isActive && !unlocked) {
                // 需要前置国策
            } else if (!unlocked) {
                // 未解锁（无前置国策的情况）
            }

            Material mat = isActive ? Material.BOOK : (unlocked ? iconMat : Material.BROWN_STAINED_GLASS_PANE);
            String status;
            if (isActive) {
                status = "§a■ 激活中";
            } else if (unlocked) {
                status = "§e□ 可激活";
            } else {
                status = "§7□ 需解锁";
            }

            gui.setItem(slot, createGuiItem(mat,
                LegacyComponentSerializer.legacyAmpersand().deserialize((isActive ? "§a" : (unlocked ? "§e" : "§7")) + def.displayName()),
                List.of(
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(categoryLabel + " §7| " + status),
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7消耗: §6" + def.treasuryCost().toPlainString() + " 星尘"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7持续: §e" + formatDuration(def.durationSeconds())),
                    ifPresent(def.prerequisiteKeys(), "§7前置: §c" + joinSet(def.prerequisiteKeys())),
                    ifEmpty(def.conflictKeys(), "§7冲突: §c" + joinSet(def.conflictKeys())),
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(isActive ? "§a▸ 点击查看详情"
                        : canActivate ? "§a▸ 点击激活"
                        : "§7▸ 点击查看详情")
                ),
                event -> {
                    event.setCancelled(true);
                    openPolicyDetailMenu(player, nation, def.key());
                },
                isActive
            ));
            slot++;
        }
        return slot;
    }

    private Component ifPresent(java.util.Set<String> set, String text) {
        return set.isEmpty() ? Component.text("") : Component.text(text);
    }

    private Component ifEmpty(java.util.Set<String> set, String text) {
        return !set.isEmpty() ? Component.text(text) : Component.text("");
    }

    /**
     * 打开国策详情页
     */
    public void openPolicyDetailMenu(Player player, Nation nation, String policyKey) {
        String normalized = policyKey.toLowerCase(Locale.ROOT).trim();

        Optional<PolicyDefinition> defOpt = policyService.policyDefinition(normalized);
        if (defOpt.isEmpty()) {
            player.sendMessage(Component.text("⚠ 国策不存在: " + policyKey, NamedTextColor.RED));
            return;
        }

        PolicyDefinition def = defOpt.get();
        Optional<PolicyRuntimeState> stateOpt = policyService.activePolicyState(nation.id());
        Optional<String> activeKey = policyService.activePolicy(nation.id());

        boolean isActive = activeKey.map(k -> k.equals(normalized)).orElse(false);
        boolean isUnlocked = policyService.hasUnlockedPolicy(nation.id(), normalized);

        // 玩家权限
        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);
        boolean isAdmin = self != null && "admin".equals(self.rank());

        String categoryLabel = def.category().displayName();
        String categoryColor = switch (def.category().group()) {
            case ECONOMY -> "§b";
            case MILITARY -> "§c";
            case INTERNAL -> "§a";
            case DIPLOMACY -> "§3";
            case RESOURCE -> "§6";
            case CULTURAL -> "§d";
        };

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l📜 " + def.displayName() + " §7| 国策详情"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：国策名称和分类
        Material topMat = isActive ? Material.BOOK : Material.BROWN_STAINED_GLASS_PANE;
        gui.setItem(4, createGuiItem(
            topMat,
            LegacyComponentSerializer.legacyAmpersand().deserialize((isActive ? "§a§l✓ " : "§7") + def.displayName()),
            List.of(
                Component.text(""),
                LegacyComponentSerializer.legacyAmpersand().deserialize(categoryLabel),
                Component.text(""),
                LegacyComponentSerializer.legacyAmpersand().deserialize("§7状态: " + (isActive ? "§a激活中" : "§7未激活")),
                LegacyComponentSerializer.legacyAmpersand().deserialize("§7国策Key: §f" + def.key()),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 效果展示（中间区域）
        int effectSlot = 19;
        for (PolicyEffect effect : def.effects()) {
            if (effectSlot > 25) break;
            Material effectMat = switch (effect.scope()) {
                case GLOBAL -> Material.NETHER_STAR;
                case PLAYER -> Material.PLAYER_HEAD;
                case TERRITORY -> Material.MAP;
                default -> Material.STONE;
            };
            gui.setItem(effectSlot, createGuiItem(
                effectMat,
                LegacyComponentSerializer.legacyAmpersand().deserialize("§e" + effect.description()),
                List.of(
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7范围: §f" + effect.scope()),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7加成: §a+" + String.format("%.0f", effect.modifier() * 100) + "%"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7效果Key: §f" + effect.key()),
                    Component.text("")
                ),
                event -> {}, false
            ));
            effectSlot++;
        }

        // 成本信息
        gui.setItem(20, createGuiItem(
            Material.GOLD_INGOT,
            LegacyComponentSerializer.legacyAmpersand().deserialize("§e💰 激活成本"),
            List.of(
                Component.text(""),
                LegacyComponentSerializer.legacyAmpersand().deserialize("§7国库消耗: §6" + def.treasuryCost().toPlainString() + " 星尘"),
                LegacyComponentSerializer.legacyAmpersand().deserialize("§7持续时间: §e" + formatDuration(def.durationSeconds())),
                LegacyComponentSerializer.legacyAmpersand().deserialize("§7冷却时间: §e" + formatDuration(def.cooldownSeconds())),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 前置条件
        if (!def.prerequisiteKeys().isEmpty()) {
            gui.setItem(22, createGuiItem(
                Material.ENDER_EYE,
                LegacyComponentSerializer.legacyAmpersand().deserialize("§d📋 前置条件"),
                List.of(
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7需要解锁以下国策:"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§c" + joinSet(def.prerequisiteKeys())),
                    isUnlocked
                        ? LegacyComponentSerializer.legacyAmpersand().deserialize("§a✓ 前置条件已满足")
                        : LegacyComponentSerializer.legacyAmpersand().deserialize("§c✗ 前置条件未满足"),
                    Component.text("")
                ),
                event -> {}, false
            ));
        }

        // 冲突国策
        if (!def.conflictKeys().isEmpty()) {
            gui.setItem(24, createGuiItem(
                Material.BARRIER,
                LegacyComponentSerializer.legacyAmpersand().deserialize("§c⚠ 冲突国策"),
                List.of(
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7与以下国策冲突:"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§c" + joinSet(def.conflictKeys())),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7激活此国策前需先解除冲突"),
                    Component.text("")
                ),
                event -> {}, false
            ));
        }

        // 操作按钮
        if (isAdmin) {
            if (isActive) {
                // 取消激活按钮
                gui.setItem(40, createGuiItem(
                    Material.RED_STAINED_GLASS,
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§c§l✗ 取消激活"),
                    List.of(
                        Component.text(""),
                        LegacyComponentSerializer.legacyAmpersand().deserialize("§7取消当前国策激活状态"),
                        Component.text(""),
                        LegacyComponentSerializer.legacyAmpersand().deserialize("§c⚠ 取消后需重新激活"),
                        Component.text(""),
                        LegacyComponentSerializer.legacyAmpersand().deserialize("§c▸ 点击取消激活")
                    ),
                    event -> {
                        event.setCancelled(true);
                        boolean cleared = policyService.clearActivePolicy(nation.id());
                        if (cleared) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                            player.sendMessage(Component.text("国策已取消激活！", NamedTextColor.GREEN));
                            plugin.getServer().getScheduler().runTask(plugin,
                                () -> openPolicyDetailMenu(player, nation, normalized));
                        }
                    }, true
                ));
            } else {
                // 激活按钮
                gui.setItem(40, createGuiItem(
                    Material.LIME_STAINED_GLASS,
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§a§l⬆ 激活国策"),
                    List.of(
                        Component.text(""),
                        LegacyComponentSerializer.legacyAmpersand().deserialize("§7为 §f" + nation.name() + " §7激活此国策"),
                        Component.text(""),
                        LegacyComponentSerializer.legacyAmpersand().deserialize("§7消耗: §6" + def.treasuryCost().toPlainString() + " 星尘"),
                        isUnlocked
                            ? LegacyComponentSerializer.legacyAmpersand().deserialize("§a✓ 前置条件已满足")
                            : LegacyComponentSerializer.legacyAmpersand().deserialize("§c✗ 需要先解锁前置国策"),
                        Component.text(""),
                        LegacyComponentSerializer.legacyAmpersand().deserialize("§a▸ 点击激活")
                    ),
                    event -> {
                        event.setCancelled(true);
                        PolicyActivationResult result = policyService.activatePolicy(
                            nation.id(), normalized, treasuryService);
                        if (result.successful()) {
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                            player.sendMessage(Component.text("国策激活成功！", NamedTextColor.GREEN));
                            plugin.getServer().getScheduler().runTask(plugin,
                                () -> openPolicyDetailMenu(player, nation, normalized));
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            String msg = describeFailure(result.failure());
                            player.sendMessage(Component.text("国策激活失败: " + msg, NamedTextColor.RED));
                            plugin.getServer().getScheduler().runTask(plugin,
                                () -> openPolicyDetailMenu(player, nation, normalized));
                        }
                    }, true
                ));
            }
        } else {
            gui.setItem(40, createGuiItem(
                Material.BARRIER,
                LegacyComponentSerializer.legacyAmpersand().deserialize("§c⚠ 需要管理员权限"),
                List.of(
                    Component.text(""),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7国策操作需要管理员权限"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("§7请联系国家管理员"),
                    Component.text("")
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回国策列表", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国策菜单")),
            event -> {
                event.setCancelled(true);
                openPolicyMenu(player, nation);
            },
            false
        ));

        gui.open(player);
    }

    /**
     * 打开主菜单（回调用）
     */
    private void openMainMenu(Player player) {
        if (mainMenuCallback != null) {
            mainMenuCallback.accept(player);
        } else {
            // Fallback: 如果没有回调，尝试通过 NationModule 打开
            try {
                java.lang.reflect.Method method = nationModule.getClass().getMethod("openManagementMenu", Player.class);
                method.invoke(nationModule, player);
            } catch (Exception e) {
                plugin.getLogger().warning("无法返回主菜单: " + e.getMessage());
                player.sendMessage(net.kyori.adventure.text.Component.text("无法返回主菜单", net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }

    private java.util.function.Consumer<Player> mainMenuCallback;

    public void setMainMenuCallback(java.util.function.Consumer<Player> callback) {
        this.mainMenuCallback = callback;
    }

    // ─── 辅助方法 ───────────────────────────────────────────────────

    private String describeFailure(PolicyActivationFailure failure) {
        if (failure == null) return "未知错误";
        return switch (failure) {
            case UNKNOWN_POLICY -> "国策不存在";
            case ALREADY_ACTIVE -> "国策已在激活状态";
            case CONFLICTING_POLICY -> "与当前激活的国策冲突";
            case MISSING_PREREQUISITE -> "缺少前置国策";
            case ON_COOLDOWN -> "国策处于冷却中";
            case MISSING_TREASURY_SERVICE -> "国库服务不可用";
            case INSUFFICIENT_TREASURY -> "国库余额不足";
            case COOLDOWN_NOT_EXPIRED -> "国策处于冷却中";
            case PREREQUISITES_NOT_MET -> "缺少前置国策";
            case MUTUALLY_EXCLUSIVE -> "与当前激活的国策冲突";
            case NOT_LEADER -> "没有国策管理权限";
        };
    }

    private String effectSummary(List<PolicyEffect> effects) {
        if (effects.isEmpty()) return "无效果";
        return effects.stream()
            .map(e -> "§a+" + String.format("%.0f", e.modifier() * 100) + "% " + e.description())
            .reduce((a, b) -> a + " §7| " + b)
            .orElse("无效果");
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "无限";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 24) {
            return (hours / 24) + "天" + (hours % 24) + "小时";
        }
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        }
        return minutes + "分钟";
    }

    private String joinSet(java.util.Set<String> set) {
        return String.join(", ", set);
    }

    private void fillBorder(Gui gui, Material borderMaterial) {
        if (borderMaterial == null) {
            borderMaterial = Material.GRAY_STAINED_GLASS_PANE;
        }
        GuiItem borderItem = new GuiItem(new ItemStack(borderMaterial));

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderItem);
        }
        int rows = gui.getRows();
        for (int i = 0; i < 9; i++) {
            gui.setItem((rows - 1) * 9 + i, borderItem);
        }
        for (int i = 1; i < rows - 1; i++) {
            gui.setItem(i * 9, borderItem);
            gui.setItem(i * 9 + 8, borderItem);
        }
    }

    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action) {
        return createGuiItem(material, name, lore, action, false);
    }

    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action,
                                   boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        if (glow && material != Material.BARRIER && material != Material.GRAY_STAINED_GLASS_PANE) {
            item = addGlow(item);
        }
        return new GuiItem(item, action);
    }

    private ItemStack addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        try {
            var glowEnchant = org.bukkit.enchantments.Enchantment.getByName("UNBREAKING");
            if (glowEnchant != null) {
                meta.addEnchant(glowEnchant, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("添加发光效果失败: " + e.getMessage());
        }
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 新增功能方法 ====================

    /**
     * 打开国策效果统计面板
     * 显示当前激活国策的所有效果汇总
     */
    public void openPolicyStatsMenu(Player player, Nation nation) {
        Optional<PolicyDefinition> activeDef = policyService.activePolicyDefinition(nation.id());
        Optional<PolicyRuntimeState> activeState = policyService.activePolicyState(nation.id());

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l📊 " + nation.name() + " §7| 国策效果统计"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：当前国策信息
        if (activeDef.isPresent() && activeState.isPresent()) {
            PolicyDefinition def = activeDef.get();
            PolicyRuntimeState state = activeState.get();
            Instant now = Instant.now();
            long remaining = state.expiresAt().isAfter(now)
                ? state.expiresAt().getEpochSecond() - now.getEpochSecond()
                : 0;

            gui.setItem(4, createGuiItem(
                Material.NETHER_STAR,
                Component.text("§a§l✓ " + def.displayName(), NamedTextColor.GREEN),
                List.of(
                    Component.text(""),
                    Component.text("§7状态: §a激活中", NamedTextColor.GREEN),
                    Component.text("§7剩余时间: §e" + formatDuration(remaining)),
                    Component.text("§7过期时间: §f" + DATE_FORMATTER.format(state.expiresAt())),
                    Component.text("")
                ),
                event -> {}, false
            ));

            // 按效果类型分组显示
            Map<PolicyEffectScope, List<PolicyEffect>> effectsByScope = def.effects().stream()
                .collect(Collectors.groupingBy(PolicyEffect::scope));

            int slot = 19;
            for (Map.Entry<PolicyEffectScope, List<PolicyEffect>> entry : effectsByScope.entrySet()) {
                if (slot > 25) break;
                PolicyEffectScope scope = entry.getKey();
                List<PolicyEffect> effects = entry.getValue();

                Material scopeMat = switch (scope) {
                    case ECONOMY -> Material.GOLD_INGOT;
                    case MILITARY -> Material.DIAMOND_SWORD;
                    case DIPLOMACY -> Material.EMERALD;
                    case TERRITORY -> Material.MAP;
                    case TRADE -> Material.CHEST_MINECART;
                    case PRODUCTION -> Material.HOPPER_MINECART;
                    case RESEARCH -> Material.BOOK;
                    case TECHNOLOGY -> Material.END_CRYSTAL;
                    case HAPPINESS -> Material.HEART_OF_THE_SEA;
                    case APPROVAL -> Material.VILLAGER_SPAWN_EGG;
                    case STABILITY -> Material.BEACON;
                    case NATION -> Material.MINECART;
                    case GLOBAL -> Material.NETHER_STAR;
                    case PLAYER -> Material.PLAYER_HEAD;
                    case OFFENSE -> Material.IRON_SWORD;
                    case DEFENSE -> Material.SHIELD;
                    case INTELLIGENCE -> Material.ENDER_EYE;
                    case ALLIANCE -> Material.ALLAY_SPAWN_EGG;
                    default -> Material.PAPER;
                };

                double totalBonus = effects.stream().mapToDouble(PolicyEffect::modifier).sum();
                String totalText = totalBonus >= 0
                    ? "§a+" + String.format("%.0f", totalBonus * 100) + "%"
                    : "§c" + String.format("%.0f", totalBonus * 100) + "%";

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("§7范围: §f" + scope.name()));
                lore.add(Component.text("§7总加成: " + totalText));
                lore.add(Component.text(""));
                for (PolicyEffect effect : effects) {
                    String bonus = effect.modifier() >= 0
                        ? "§a+" + String.format("%.0f", effect.modifier() * 100) + "%"
                        : "§c" + String.format("%.0f", effect.modifier() * 100) + "%";
                    lore.add(Component.text("  " + bonus + " §7" + effect.description()));
                }
                lore.add(Component.text(""));

                gui.setItem(slot, createGuiItem(scopeMat,
                    Component.text("§e" + scope.name(), NamedTextColor.YELLOW),
                    lore,
                    event -> event.setCancelled(true),
                    true
                ));
                slot++;
            }
        } else {
            gui.setItem(4, createGuiItem(
                Material.BARRIER,
                Component.text("§c§l✗ 无激活国策", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7当前没有激活的国策"),
                    Component.text("§7激活一个国策查看效果"),
                    Component.text("")
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回国策列表", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国策菜单")),
            event -> {
                event.setCancelled(true);
                openPolicyMenu(player, nation);
            },
            false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开国策帮助/说明菜单
     */
    public void openPolicyHelpMenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l❓ " + nation.name() + " §7| 国策帮助"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 帮助内容
        List<Component> helpLore = List.of(
            Component.text(""),
            Component.text("§e§l国策系统说明", NamedTextColor.YELLOW),
            Component.text(""),
            Component.text("§7国策是国家的重要战略选择"),
            Component.text("§7每个国策都有独特的效果"),
            Component.text(""),
            Component.text("§a■ 激活中 §7- 当前生效的国策"),
            Component.text("§e□ 可激活 §7- 已解锁可使用"),
            Component.text("§7□ 需解锁 §7- 需要前置国策"),
            Component.text(""),
            Component.text("§7国策激活会消耗国库资金"),
            Component.text("§7国策有时限，过期后自动失效"),
            Component.text("§7部分国策冲突，不可同时激活"),
            Component.text("")
        );

        gui.setItem(4, createGuiItem(
            Material.BOOK,
            Component.text("§e§l国策系统说明", NamedTextColor.YELLOW),
            helpLore,
            event -> event.setCancelled(true),
            false
        ));

        // 分类说明
        Map<String, String> categoryInfo = Map.of(
            "§b经济", "财政、货币、贸易等经济政策",
            "§c军事", "国防、征兵、军备等军事政策",
            "§a内政", "教育、医疗、福利等内政政策",
            "§3外交", "外交、文化交流等对外政策",
            "§6资源", "资源管理、环境保护等政策",
            "§d文化", "文化、宣传等软实力政策"
        );

        int slot = 19;
        for (Map.Entry<String, String> entry : categoryInfo.entrySet()) {
            if (slot > 25) break;
            gui.setItem(slot, createGuiItem(
                Material.PAPER,
                Component.text(entry.getKey() + " §7分类", NamedTextColor.WHITE),
                List.of(
                    Component.text(""),
                    Component.text("§7" + entry.getValue()),
                    Component.text("")
                ),
                event -> event.setCancelled(true),
                false
            ));
            slot++;
        }

        // 状态说明
        List<Component> statusLore = List.of(
            Component.text(""),
            Component.text("§7国策状态说明:", NamedTextColor.GRAY),
            Component.text(""),
            Component.text("§a■ 激活中 §7- 当前生效的国策"),
            Component.text("§e□ 可激活 §7- 已解锁可使用的国策"),
            Component.text("§7□ 需解锁 §7- 需要先激活前置国策"),
            Component.text(""),
            Component.text("§7国策消耗国库资金"),
            Component.text("§7部分国策有冷却时间"),
            Component.text("§7部分国策互相冲突"),
            Component.text("")
        );

        gui.setItem(30, createGuiItem(
            Material.BOOKSHELF,
            Component.text("§e状态说明", NamedTextColor.YELLOW),
            statusLore,
            event -> event.setCancelled(true),
            false
        ));

        // 操作说明
        List<Component> actionLore = List.of(
            Component.text(""),
            Component.text("§7操作说明:", NamedTextColor.GRAY),
            Component.text(""),
            Component.text("§a国策管理员 §7- 国家创建者和管理员"),
            Component.text("§7可以激活和取消国策"),
            Component.text(""),
            Component.text("§7点击分类查看国策"),
            Component.text("§7点击国策查看详情"),
            Component.text("§7详情页可激活/取消国策"),
            Component.text("")
        );

        gui.setItem(32, createGuiItem(
            Material.COMPASS,
            Component.text("§e操作说明", NamedTextColor.YELLOW),
            actionLore,
            event -> event.setCancelled(true),
            false
        ));

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回国策列表", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国策菜单")),
            event -> {
                event.setCancelled(true);
                openPolicyMenu(player, nation);
            },
            false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开国策冷却状态菜单
     */
    public void openPolicyCooldownMenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l⏱ " + nation.name() + " §7| 国策冷却"))
            .rows(6)
            .disableAllInteractions()
            .create();

        Collection<String> allPolicies = policyService.availablePolicies();
        Instant now = Instant.now();

        gui.setItem(4, createGuiItem(
            Material.CLOCK,
            Component.text("§e§l国策冷却状态", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7查看所有国策的冷却状态"),
                Component.text(""),
                Component.text("§a✓ 可用 §7- 冷却已结束"),
                Component.text("§e⏳ 冷却中 §7- 等待冷却结束"),
                Component.text("§c✗ 未解锁 §7- 需要前置国策"),
                Component.text("")
            ),
            event -> event.setCancelled(true),
            false
        ));

        // 填充所有国策的冷却状态
        int slot = 10;
        for (String policyKey : allPolicies.stream().sorted().toList()) {
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot > 43) break;

            Optional<PolicyDefinition> defOpt = policyService.policyDefinition(policyKey);
            if (defOpt.isEmpty()) continue;
            PolicyDefinition def = defOpt.get();

            boolean unlocked = policyService.hasUnlockedPolicy(nation.id(), policyKey);
            boolean active = policyService.activePolicy(nation.id())
                .map(k -> k.equals(policyKey)).orElse(false);

            // 检查冷却状态
            Optional<PolicyRuntimeState> stateOpt = policyService.activePolicyState(nation.id());
            boolean onCooldown = false;
            long cooldownRemaining = 0;

            if (!active && unlocked) {
                // 检查是否有冷却
                Collection<PolicyEffect> effects = def.effects();
                if (def.cooldownSeconds() > 0) {
                    // 简化：假设冷却状态可以通过其他方式获取
                    onCooldown = false; // 这里需要根据实际冷却系统调整
                }
            }

            Material mat;
            String status;
            List<Component> lore = new ArrayList<>();

            if (active) {
                mat = Material.BOOK;
                status = "§a■ 激活中";
                lore.add(Component.text("§7状态: §a激活中"));
                if (stateOpt.isPresent()) {
                    long remaining = stateOpt.get().expiresAt().getEpochSecond() - now.getEpochSecond();
                    lore.add(Component.text("§7剩余时间: §e" + formatDuration(remaining)));
                }
            } else if (!unlocked) {
                mat = Material.BROWN_STAINED_GLASS_PANE;
                status = "§7□ 需解锁";
                lore.add(Component.text("§7状态: §7需解锁"));
                if (!def.prerequisiteKeys().isEmpty()) {
                    lore.add(Component.text("§7前置: §c" + joinSet(def.prerequisiteKeys())));
                }
            } else if (onCooldown) {
                mat = Material.CLOCK;
                status = "§e⏳ 冷却中";
                lore.add(Component.text("§7状态: §e冷却中"));
                lore.add(Component.text("§7剩余: §e" + formatDuration(cooldownRemaining)));
            } else {
                mat = Material.PAPER;
                status = "§a✓ 可用";
                lore.add(Component.text("§7状态: §a可用"));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("§7消耗: §6" + def.treasuryCost().toPlainString() + " 星尘"));
            lore.add(Component.text("§7持续: §e" + formatDuration(def.durationSeconds())));
            lore.add(Component.text(""));
            lore.add(Component.text("§a▸ 点击查看详情"));

            gui.setItem(slot, createGuiItem(mat,
                Component.text(status + " §f" + def.displayName()),
                lore,
                event -> {
                    event.setCancelled(true);
                    openPolicyDetailMenu(player, nation, policyKey);
                },
                active
            ));
            slot++;
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回国策列表", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国策菜单")),
            event -> {
                event.setCancelled(true);
                openPolicyMenu(player, nation);
            },
            false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 格式化日期时间
     */
    private String formatDateTime(Instant instant) {
        if (instant == null) return "无";
        return DATE_FORMATTER.format(instant);
    }
}
