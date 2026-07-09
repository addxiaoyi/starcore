package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.claimtool.ClaimToolService;
import dev.starcore.starcore.module.nation.model.ClaimPriceBreakdown;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.quest.DailyQuestService;
import dev.starcore.starcore.quest.Quest;
import dev.starcore.starcore.quest.QuestObjective;
import dev.starcore.starcore.quest.QuestReward;
import dev.starcore.starcore.quest.QuestService;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 国家高级菜单 - 处理政体变更、税率调整、权限管理、国家解散、领土购买、每日任务等高级功能
 */
public class NationAdvancedMenu {

    private final NationModule nationModule;
    private final Plugin plugin;
    private final MessageService messages;
    private final PacketEventsAnvilProvider anvilProvider;
    private final TreasuryService treasuryService;
    private final EconomyService economyService;
    private final GovernmentService governmentService;
    private final ClaimToolService claimToolService;
    private final QuestService questService;
    private final DailyQuestService dailyQuestService;
    private final Logger logger;

    public NationAdvancedMenu(NationModule nationModule, Plugin plugin,
                            MessageService messages, PacketEventsAnvilProvider anvilProvider,
                            TreasuryService treasuryService, EconomyService economyService,
                            GovernmentService governmentService, ClaimToolService claimToolService,
                            QuestService questService, DailyQuestService dailyQuestService) {
        this.nationModule = nationModule;
        this.plugin = plugin;
        this.messages = messages;
        this.anvilProvider = anvilProvider;
        this.treasuryService = treasuryService;
        this.economyService = economyService;
        this.governmentService = governmentService;
        this.claimToolService = claimToolService;
        this.questService = questService;
        this.dailyQuestService = dailyQuestService; // 可能为 null，由调用方保证
        this.logger = plugin.getLogger();
    }

    // ══════════════════════════════════════════════════════════════
    //  1. 政体变更 GUI
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开政体变更菜单
     */
    public void openGovernmentChangeMenu(Player player, Nation nation) {
        // 刷新国家缓存，确保显示最新数据
        nationModule.refreshNationCache(nation.id());

        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);

        if (self == null || !"admin".equals(self.rank())) {
            player.sendMessage(Component.text("⚠ 只有管理员才能变更政体！", NamedTextColor.RED));
            return;
        }

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l📜 " + nation.name() + " §7| 政体变更"))
            .rows(4)
            .disableAllInteractions()
            .create();

        // 顶部标题
        gui.setItem(4, createGuiItem(
            Material.BOOK,
            Component.text("§e§l政体变更", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7当前政体: §a" + nation.governmentType().displayName()),
                Component.text(""),
                Component.text("§7选择一个政体进行变更")
            ),
            event -> {}, false
        ));

        // 政体选项
        GovernmentType[] types = GovernmentType.values();
        int slot = 10;
        for (int i = 0; i < types.length && slot < 18; i++) {
            GovernmentType type = types[i];
            boolean isCurrent = type == nation.governmentType();

            Material mat = isCurrent ? Material.GREEN_STAINED_GLASS : Material.BOOK;
            String prefix = isCurrent ? "§a✓ " : "§e";

            gui.setItem(slot, createGuiItem(
                mat,
                Component.text(prefix + "§l" + type.displayName(), isCurrent ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                buildGovernmentLore(type, isCurrent),
                event -> {
                    event.setCancelled(true);
                    if (!isCurrent) {
                        openGovernmentConfirmAnvil(player, nation, type);
                    }
                }, !isCurrent
            ));
            slot++;
        }

        // 返回按钮
        gui.setItem(31, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回设置", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国家设置菜单")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    private List<Component> buildGovernmentLore(GovernmentType type, boolean isCurrent) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7类型: §f" + type.name()));
        lore.add(Component.text(""));

        switch (type) {
            case MONARCHY -> {
                lore.add(Component.text("§7特点:", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 君主拥有最高决策权", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 只有君主可签署重要决议", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 适合集权管理模式", NamedTextColor.GRAY));
            }
            case DICTATORSHIP -> {
                lore.add(Component.text("§7特点:", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 独裁者拥有绝对权力", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 决策快速，执行力强", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 适合军事化管理", NamedTextColor.GRAY));
            }
            case REPUBLIC -> {
                lore.add(Component.text("§7特点:", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 所有成员可提出决议", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 50%成员签署即可通过", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 适合民主决策", NamedTextColor.GRAY));
            }
            case DEMOCRACY -> {
                lore.add(Component.text("§7特点:", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 所有成员可提出决议", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 需要50%+1成员签署", NamedTextColor.GRAY));
                lore.add(Component.text("§7- 适合公平治理", NamedTextColor.GRAY));
            }
        }

        lore.add(Component.text(""));
        if (isCurrent) {
            lore.add(Component.text("§a✓ 当前政体", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("§e▸ 点击选择此政体", NamedTextColor.YELLOW));
        }

        return lore;
    }

    /**
     * 打开政体确认铁砧输入
     */
    private void openGovernmentConfirmAnvil(Player player, Nation nation, GovernmentType newType) {
        player.closeInventory();

        anvilProvider.openAnvilInput(
            player,
            "§e输入 CONFIRM 确认变更政体",
            "",
            input -> {
                if (!"CONFIRM".equals(input.trim().toUpperCase())) {
                    player.sendMessage(Component.text("⚠ 政体变更已取消", NamedTextColor.RED));
                    return;
                }

                // 验证并执行政体变更
                if (governmentService != null) {
                    boolean success = governmentService.setGovernment(nation.id(), newType);
                    if (success) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                        player.sendMessage(Component.text("§a政体已变更为: §e" + newType.displayName(), NamedTextColor.GREEN));
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("⚠ 政体变更失败！", NamedTextColor.RED));
                    }
                } else {
                    // Fallback: 直接修改
                    nationModule.setGovernmentType(nation.id(), newType);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    player.sendMessage(Component.text("§a政体已变更为: §e" + newType.displayName(), NamedTextColor.GREEN));
                }

                // 重新打开政体菜单
                nationModule.nationById(nation.id()).ifPresent(updated -> {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> openGovernmentChangeMenu(player, updated), 3L);
                });
            },
            () -> {
                player.sendMessage(Component.text("已取消政体变更", NamedTextColor.GRAY));
            }
        );
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 税率调整 GUI
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开税率调整菜单
     */
    public void openTaxRateMenu(Player player, Nation nation) {
        // 刷新国家缓存，确保显示最新数据
        nationModule.refreshNationCache(nation.id());

        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);

        if (self == null || !"admin".equals(self.rank())) {
            player.sendMessage(Component.text("⚠ 只有管理员才能调整税率！", NamedTextColor.RED));
            return;
        }

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l💰 " + nation.name() + " §7| 税率调整"))
            .rows(3)
            .disableAllInteractions()
            .create();

        // 当前税率显示
        double currentTax = nation.getTaxRate();
        gui.setItem(4, createGuiItem(
            Material.GOLD_INGOT,
            Component.text("§6§l当前税率", NamedTextColor.GOLD),
            List.of(
                Component.text(""),
                Component.text("§7当前税率: §e" + String.format("%.1f%%", currentTax * 100)),
                Component.text(""),
                Component.text("§7每日从成员收取: §6" + String.format("%.1f%%", currentTax * 100) + " §7金币"),
                Component.text("")
            ),
            event -> {}, true
        ));

        // 预设税率选项
        gui.setItem(11, createGuiItem(
            Material.PAPER,
            Component.text("§a低税率 §e5%", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§7适合成员友好型国家"),
                Component.text(""),
                Component.text("§e▸ 点击设置为 5%")
            ),
            event -> {
                event.setCancelled(true);
                setTaxRate(player, nation, 0.05);
            }, false
        ));

        gui.setItem(12, createGuiItem(
            Material.PAPER,
            Component.text("§e中税率 §e10%", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7适合平衡型国家"),
                Component.text(""),
                Component.text("§e▸ 点击设置为 10%")
            ),
            event -> {
                event.setCancelled(true);
                setTaxRate(player, nation, 0.10);
            }, true
        ));

        gui.setItem(13, createGuiItem(
            Material.PAPER,
            Component.text("§6高税率 §e15%", NamedTextColor.GOLD),
            List.of(
                Component.text(""),
                Component.text("§7适合国库优先型国家"),
                Component.text(""),
                Component.text("§e▸ 点击设置为 15%")
            ),
            event -> {
                event.setCancelled(true);
                setTaxRate(player, nation, 0.15);
            }, false
        ));

        gui.setItem(14, createGuiItem(
            Material.PAPER,
            Component.text("§c最高税率 §e20%", NamedTextColor.RED),
            List.of(
                Component.text(""),
                Component.text("§7适合快速积累国库"),
                Component.text("§c⚠ 可能导致成员不满"),
                Component.text(""),
                Component.text("§e▸ 点击设置为 20%")
            ),
            event -> {
                event.setCancelled(true);
                setTaxRate(player, nation, 0.20);
            }, false
        ));

        // 自定义税率按钮
        gui.setItem(15, createGuiItem(
            Material.EXPERIENCE_BOTTLE,
            Component.text("§b自定义税率", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§7输入自定义税率百分比"),
                Component.text("§7范围: 0% - 50%"),
                Component.text(""),
                Component.text("§e▸ 点击输入自定义税率", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                openCustomTaxRateAnvil(player, nation);
            }, false
        ));

        // 返回按钮
        gui.setItem(22, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回设置", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国家设置菜单")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 设置税率
     */
    private void setTaxRate(Player player, Nation nation, double rate) {
        nationModule.setTaxRate(nation.id(), rate);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        player.sendMessage(Component.text("§a税率已设置为: §e" + String.format("%.1f%%", rate * 100), NamedTextColor.GREEN));

        // 重新打开税率菜单
        nationModule.nationById(nation.id()).ifPresent(updated -> {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> openTaxRateMenu(player, updated), 2L);
        });
    }

    /**
     * 打开自定义税率铁砧输入
     */
    private void openCustomTaxRateAnvil(Player player, Nation nation) {
        player.closeInventory();

        anvilProvider.openAnvilInput(
            player,
            "§e输入税率百分比 (0-50)",
            "10",
            input -> {
                try {
                    double rate = Double.parseDouble(input.trim()) / 100.0;

                    if (rate < 0 || rate > 0.50) {
                        player.sendMessage(Component.text("⚠ 税率必须在 0% - 50% 之间！", NamedTextColor.RED));
                        setTaxRate(player, nation, 0.10);
                        return;
                    }

                    setTaxRate(player, nation, rate);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("⚠ 请输入有效的数字！", NamedTextColor.RED));
                    setTaxRate(player, nation, 0.10);
                }
            },
            () -> {
                player.sendMessage(Component.text("已取消自定义税率设置", NamedTextColor.GRAY));
            }
        );
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 权限管理 GUI
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开权限管理菜单
     */
    public void openPermissionMenu(Player player, Nation nation) {
        // 刷新国家缓存，确保显示最新数据
        nationModule.refreshNationCache(nation.id());

        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);

        if (self == null || !"admin".equals(self.rank())) {
            player.sendMessage(Component.text("⚠ 只有管理员才能管理权限！", NamedTextColor.RED));
            return;
        }

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l🔑 " + nation.name() + " §7| 权限管理"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部标题
        gui.setItem(4, createGuiItem(
            Material.NAME_TAG,
            Component.text("§e§l权限管理", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text("§7成员数: §a" + nation.memberCount()),
                Component.text(""),
                Component.text("§7点击成员查看/修改权限")
            ),
            event -> {}, false
        ));

        // 权限说明
        gui.setItem(22, createGuiItem(
            Material.BOOK,
            Component.text("§b§l权限等级说明", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§cadmin §7- 管理员：全部权限"),
                Component.text("§aofficer §7- 官员：部分管理权"),
                Component.text("§7member §7- 成员：基本权限"),
                Component.text(""),
                Component.text("§7管理员可以: 修改税率、邀请/踢出成员、"),
                Component.text("§7变更政体、解散国家等"),
                Component.text(""),
                Component.text("§7官员可以: 邀请成员、查看部分设置")
            ),
            event -> {}, false
        ));

        // 填充成员列表（槽位 28-43，跳过边框）
        int slot = 28;
        for (NationMember member : nation.members()) {
            if (slot > 43) break;
            if (slot == 35) slot++; // 跳过边框

            String rankColor = getRankColor(member.rank());
            Component memberName = Component.text(rankColor + member.playerName());

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text("§7等级: " + rankColor + member.rank()));
            lore.add(Component.text("§7加入时间: §f" + member.joinedDate()));
            lore.add(Component.text(""));
            lore.add(Component.text("§a▸ 点击修改权限", NamedTextColor.YELLOW));

            gui.setItem(slot, createGuiItem(
                Material.PLAYER_HEAD,
                memberName,
                lore,
                event2 -> {
                    event2.setCancelled(true);
                    openMemberPermissionEdit(player, nation, member);
                }, false
            ));
            slot++;
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回设置", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国家设置菜单")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开成员权限编辑菜单
     */
    public void openMemberPermissionEdit(Player player, Nation nation, NationMember member) {
        // 刷新国家缓存，确保显示最新数据
        nationModule.refreshNationCache(nation.id());

        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);

        if (self == null || !"admin".equals(self.rank())) {
            player.sendMessage(Component.text("⚠ 只有管理员才能修改权限！", NamedTextColor.RED));
            return;
        }

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l🔑 " + member.playerName() + " §7| 权限修改"))
            .rows(4)
            .disableAllInteractions()
            .create();

        // 成员信息
        gui.setItem(4, createGuiItem(
            Material.PLAYER_HEAD,
            Component.text("§e§l" + member.playerName(), NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7当前权限: §a" + member.rank()),
                Component.text("§7加入时间: §f" + member.joinedDate()),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 权限选项
        gui.setItem(11, createGuiItem(
            Material.RED_STAINED_GLASS,
            Component.text("§c§l设为成员", NamedTextColor.RED),
            List.of(
                Component.text(""),
                Component.text("§7基本权限，无管理能力"),
                Component.text(""),
                member.rank().equals("member")
                    ? Component.text("§a✓ 当前权限", NamedTextColor.GREEN)
                    : Component.text("§e▸ 点击设置", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                if (!member.rank().equals("member")) {
                    nationModule.setMemberRank(nation.id(), member.playerId(), "member");
                    player.sendMessage(Component.text("§a已将 " + member.playerName() + " 设为成员", NamedTextColor.GREEN));
                    refreshAndReturn(player, nation, member);
                }
            }, !member.rank().equals("member")
        ));

        gui.setItem(13, createGuiItem(
            Material.ORANGE_STAINED_GLASS,
            Component.text("§6§l设为官员", NamedTextColor.GOLD),
            List.of(
                Component.text(""),
                Component.text("§7可邀请成员、部分管理权"),
                Component.text(""),
                member.rank().equals("officer")
                    ? Component.text("§a✓ 当前权限", NamedTextColor.GREEN)
                    : Component.text("§e▸ 点击设置", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                if (!member.rank().equals("officer")) {
                    nationModule.setMemberRank(nation.id(), member.playerId(), "officer");
                    player.sendMessage(Component.text("§a已将 " + member.playerName() + " 设为官员", NamedTextColor.GREEN));
                    refreshAndReturn(player, nation, member);
                }
            }, !member.rank().equals("officer")
        ));

        gui.setItem(15, createGuiItem(
            Material.LIME_STAINED_GLASS,
            Component.text("§a§l设为管理员", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§7全部管理权限"),
                Component.text(""),
                member.rank().equals("admin")
                    ? Component.text("§a✓ 当前权限", NamedTextColor.GREEN)
                    : Component.text("§c⚠ 此操作风险较高", NamedTextColor.RED)
            ),
            event -> {
                event.setCancelled(true);
                if (!member.rank().equals("admin")) {
                    nationModule.setMemberRank(nation.id(), member.playerId(), "admin");
                    player.sendMessage(Component.text("§a已将 " + member.playerName() + " 设为管理员", NamedTextColor.GREEN));
                    refreshAndReturn(player, nation, member);
                }
            }, !member.rank().equals("admin")
        ));

        // 踢出按钮
        if (!member.playerId().equals(player.getUniqueId())) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c§l踢出国家", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7将成员移出国家"),
                    Component.text(""),
                    Component.text("§c⚠ 此操作不可撤销！"),
                    Component.text(""),
                    Component.text("§e▸ 点击踢出", NamedTextColor.YELLOW)
                ),
                event -> {
                    event.setCancelled(true);
                    openKickConfirmAnvil(player, nation, member);
                }, false
            ));
        } else {
            gui.setItem(22, createGuiItem(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.text("§8踢出自己", NamedTextColor.DARK_GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7无法踢出自己")
                ),
                event -> {}, false
            ));
        }

        // 返回按钮
        gui.setItem(31, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回权限管理", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回权限管理菜单")),
            event -> {
                event.setCancelled(true);
                openPermissionMenu(player, nation);
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 刷新并返回
     */
    private void refreshAndReturn(Player player, Nation nation, NationMember member) {
        nationModule.nationById(nation.id()).ifPresent(updated -> {
            updated.members().stream()
                .filter(m -> m.playerId().equals(member.playerId()))
                .findFirst()
                .ifPresent(updatedMember -> {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        openMemberPermissionEdit(player, updated, updatedMember), 2L);
                });
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 国家解散二次确认
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开国家解散确认菜单
     */
    public void openDisbandConfirmMenu(Player player, Nation nation) {
        // 刷新国家缓存，确保显示最新数据
        nationModule.refreshNationCache(nation.id());

        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);

        if (self == null || !"admin".equals(self.rank())) {
            player.sendMessage(Component.text("⚠ 只有管理员才能解散国家！", NamedTextColor.RED));
            return;
        }

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§4§l⚠ " + nation.name() + " §7| 解散确认"))
            .rows(3)
            .disableAllInteractions()
            .create();

        // 警告信息
        gui.setItem(4, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l⚠ 警告：即将解散国家", NamedTextColor.RED),
            List.of(
                Component.text(""),
                Component.text("§c解散国家将导致："),
                Component.text("§7- 国家永久删除"),
                Component.text("§7- 所有成员被移出"),
                Component.text("§7- 所有领土被释放"),
                Component.text("§7- 所有国库资金清空"),
                Component.text(""),
                Component.text("§c此操作不可撤销！")
            ),
            event -> {}, false
        ));

        // 国家信息
        gui.setItem(11, createGuiItem(
            Material.BEACON,
            Component.text("§e" + nation.name(), NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7成员: §f" + nation.memberCount()),
                Component.text("§7领土: §f" + nationModule.claimCount(nation.id())),
                Component.text("§7国库: §6" + (treasuryService != null ? treasuryService.balance(nation.id()).toPlainString() : "N/A")),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 解散确认按钮
        gui.setItem(15, createGuiItem(
            Material.TNT,
            Component.text("§c§l⚠ 确认解散国家", NamedTextColor.RED, TextDecoration.BOLD),
            List.of(
                Component.text(""),
                Component.text("§c点击后将打开铁砧确认"),
                Component.text(""),
                Component.text("§7需要在铁砧中输入 CONFIRM"),
                Component.text("§7以确认解散操作"),
                Component.text(""),
                Component.text("§c⚠ 再次警告：此操作不可逆！")
            ),
            event -> {
                event.setCancelled(true);
                openDisbandConfirmAnvil(player, nation);
            }, true
        ));

        // 返回按钮
        gui.setItem(22, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 取消，返回设置", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国家设置菜单")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
            }, false
        ));

        fillBorder(gui, Material.RED_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开解散确认铁砧输入
     */
    private void openDisbandConfirmAnvil(Player player, Nation nation) {
        player.closeInventory();

        anvilProvider.openAnvilInput(
            player,
            "§c输入 CONFIRM 解散国家",
            "",
            input -> {
                if (!"CONFIRM".equals(input.trim().toUpperCase())) {
                    player.sendMessage(Component.text("⚠ 国家解散已取消", NamedTextColor.RED));
                    return;
                }

                // 执行解散
                boolean success = nationModule.disbandNation(nation.id());
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                    player.sendMessage(Component.text("§c⚠ 国家 " + nation.name() + " 已解散！", NamedTextColor.RED));
                    player.sendMessage(Component.text("§7所有成员已被移出，所有数据已清除。", NamedTextColor.GRAY));
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    player.sendMessage(Component.text("⚠ 国家解散失败！", NamedTextColor.RED));
                }
            },
            () -> {
                player.sendMessage(Component.text("已取消国家解散", NamedTextColor.GRAY));
            }
        );
    }

    // ══════════════════════════════════════════════════════════════
    //  5. 领土购买 GUI（ClaimTool 集成）
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开领土购买菜单
     */
    public void openTerritoryPurchaseMenu(Player player, Nation nation) {
        // 刷新国家缓存，确保显示最新数据
        nationModule.refreshNationCache(nation.id());

        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);

        if (self == null) {
            player.sendMessage(Component.text("⚠ 你不是国家成员！", NamedTextColor.RED));
            return;
        }

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l🗺 " + nation.name() + " §7| 领土购买"))
            .rows(5)
            .disableAllInteractions()
            .create();

        // 顶部信息
        int currentClaims = nationModule.claimCount(nation.id());
        int maxClaims = nationModule.maxClaimsOf(nation.id());
        BigDecimal treasuryBalance = treasuryService != null ? treasuryService.balance(nation.id()) : BigDecimal.ZERO;

        gui.setItem(4, createGuiItem(
            Material.FILLED_MAP,
            Component.text("§e§l领土信息", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7当前领土: §a" + currentClaims + " §7区块"),
                Component.text("§7最大领土: §e" + maxClaims + " §7区块"),
                Component.text("§7剩余可购买: §a" + Math.max(0, maxClaims - currentClaims) + " §7区块"),
                Component.text(""),
                Component.text("§7国库余额: §6" + treasuryBalance.toPlainString() + " 星尘"),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 获取价格信息
        ClaimPriceBreakdown priceBreakdown = nationModule.getClaimPriceBreakdown(nation.id());

        // 计算距离加成倍数
        BigDecimal distanceMultiplier = BigDecimal.ONE;
        if (priceBreakdown.baseChunkPrice().compareTo(BigDecimal.ZERO) > 0) {
            distanceMultiplier = priceBreakdown.totalPrice()
                .divide(priceBreakdown.baseChunkPrice(), 2, RoundingMode.HALF_UP);
        }

        // 价格信息
        gui.setItem(20, createGuiItem(
            Material.GOLD_INGOT,
            Component.text("§6💰 区块价格", NamedTextColor.GOLD),
            List.of(
                Component.text(""),
                Component.text("§7基础价格: §6" + priceBreakdown.baseChunkPrice().toPlainString()),
                Component.text("§7距离加成: §6" + distanceMultiplier + "x"),
                Component.text("§7总计: §e" + priceBreakdown.totalPrice().toPlainString() + " 星尘"),
                Component.text("")
            ),
            event -> {}, false
        ));

        // ClaimTool 说明
        gui.setItem(22, createGuiItem(
            Material.DIAMOND_PICKAXE,
            Component.text("§b⛏ 领地工具", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§7使用领地工具选择领土："),
                Component.text("§71. 点击获取领地工具"),
                Component.text("§72. 左键选择第一个点"),
                Component.text("§73. 右键选择第二个点"),
                Component.text("§74. 系统自动计算并显示价格"),
                Component.text("§75. 确认购买"),
                Component.text(""),
                Component.text("§e▸ 点击获取领地工具", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                giveClaimTool(player);
            }, true
        ));

        // 给予领地工具按钮
        gui.setItem(24, createGuiItem(
            Material.NETHER_STAR,
            Component.text("§a§l+ 立即获取工具", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§7点击后立即获得"),
                Component.text("§7领地选择工具"),
                Component.text(""),
                Component.text("§e▸ 点击获取", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                giveClaimTool(player);
            }, true
        ));

        // 提示信息
        gui.setItem(40, createGuiItem(
            Material.BOOK,
            Component.text("§e📖 购买说明", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§71. 获取领地工具"),
                Component.text("§72. 在游戏中选择两个点"),
                Component.text("§73. 选择区域将被计算价格"),
                Component.text("§74. 确认后从国库扣除并购买"),
                Component.text(""),
                Component.text("§7管理员可使用 /sc nation claim 购买当前区块")
            ),
            event -> {}, false
        ));

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回主菜单", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回国家主菜单")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 给予玩家领地工具
     */
    private void giveClaimTool(Player player) {
        if (claimToolService != null) {
            ItemStack tool = claimToolService.createTool();
            player.getInventory().addItem(tool);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            player.sendMessage(Component.text("§a已获得领地工具！", NamedTextColor.GREEN));
            player.sendMessage(Component.text("§7左键选择第一个点，右键选择第二个点", NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("⚠ 领地工具服务不可用", NamedTextColor.RED));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  6. 每日任务 GUI
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开每日任务菜单
     */
    public void openDailyQuestMenu(Player player) {
        // 检查每日任务服务是否可用
        if (dailyQuestService == null) {
            Gui gui = Gui.gui()
                .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l📋 " + player.getName() + " §7| 每日任务"))
                .rows(3)
                .disableAllInteractions()
                .create();

            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c每日任务系统未启用", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7每日任务模块不可用"),
                    Component.text("§7请联系管理员")
                ),
                event -> {}, false
            ));

            gui.setItem(49, createGuiItem(
                Material.ARROW,
                Component.text("§e◀ 返回", NamedTextColor.YELLOW),
                List.of(Component.text(""), Component.text("§7关闭此菜单")),
                event -> {
                    event.setCancelled(true);
                    gui.close(player);
                }, false
            ));

            fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
            gui.open(player);
            return;
        }

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l📋 " + player.getName() + " §7| 每日任务"))
            .rows(5)
            .disableAllInteractions()
            .create();

        // 获取每日任务进度
        List<Quest> dailyQuests = dailyQuestService != null
            ? dailyQuestService.getPlayerDailyQuests(player.getUniqueId())
            : Collections.emptyList();

        DailyQuestService.DailyProgress progress = dailyQuestService != null
            ? dailyQuestService.getDailyProgress(player.getUniqueId())
            : new DailyQuestService.DailyProgress(0, 0);

        // 顶部进度显示
        gui.setItem(4, createGuiItem(
            Material.BOOK,
            Component.text("§e§l今日进度", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7完成进度: §a" + progress.getProgressText()),
                Component.text(""),
                Component.text(progress.getProgressBar()),
                Component.text(""),
                progress.isAllCompleted()
                    ? Component.text("§a✓ 今日任务全部完成！", NamedTextColor.GREEN)
                    : Component.text("§e还需完成 " + (progress.getTotal() - progress.getCompleted()) + " 个任务")
            ),
            event -> {}, false
        ));

        // 刷新按钮
        gui.setItem(8, createGuiItem(
            Material.END_CRYSTAL,
            Component.text("§b🔄 刷新任务", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§7手动刷新每日任务"),
                Component.text("§7消耗: §6100 星尘"),
                Component.text(""),
                Component.text("§e▸ 点击刷新", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                if (dailyQuestService != null) {
                    boolean success = dailyQuestService.manualRefresh(player, false);
                    if (success) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> openDailyQuestMenu(player), 2L);
                    }
                }
            }, false
        ));

        // 填充任务列表
        int slot = 19;
        if (!dailyQuests.isEmpty()) {
            for (Quest quest : dailyQuests) {
                if (slot > 43) break;
                if (slot == 26 || slot == 35) slot++;

                boolean completed = questService != null &&
                    questService.getPlayerQuest(player.getUniqueId()).hasCompletedQuest(quest.getId());

                Material mat = completed ? Material.LIME_STAINED_GLASS : Material.BOOK;
                String prefix = completed ? "§a✓ " : "§e";

                gui.setItem(slot, createGuiItem(
                    mat,
                    Component.text(prefix + "§l" + quest.getName(), completed ? NamedTextColor.GREEN : NamedTextColor.YELLOW),
                    buildQuestLore(quest, completed),
                    event -> {
                        event.setCancelled(true);
                        if (!completed) {
                            openQuestDetailMenu(player, quest);
                        }
                    }, !completed
                ));
                slot++;
            }
        } else {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无每日任务", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7点击刷新获取今日任务")
                ),
                event -> {
                    event.setCancelled(true);
                    if (dailyQuestService != null) {
                        dailyQuestService.generateDailyQuests(player);
                        openDailyQuestMenu(player);
                    }
                }, false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7关闭此菜单")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    private List<Component> buildQuestLore(Quest quest, boolean completed) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("§7类型: §f" + quest.getType()));
        lore.add(Component.text("§7难度: §f" + quest.getDifficulty()));
        lore.add(Component.text(""));

        // 目标描述
        for (QuestObjective obj : quest.getObjectives()) {
            String desc = obj.getDescription() != null ? obj.getDescription() : obj.getType().getDisplayName();
            lore.add(Component.text("§7- " + desc, NamedTextColor.GRAY));
        }

        lore.add(Component.text(""));

        // 奖励
        lore.add(Component.text("§6奖励:", NamedTextColor.GOLD));
        QuestReward reward = quest.getReward();
        if (reward != null) {
            lore.add(Component.text("§e" + reward.toString(), NamedTextColor.YELLOW));
        }

        lore.add(Component.text(""));
        if (completed) {
            lore.add(Component.text("§a✓ 已完成", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("§e▸ 点击查看详情", NamedTextColor.YELLOW));
        }

        return lore;
    }

    /**
     * 打开任务详情菜单
     */
    private void openQuestDetailMenu(Player player, Quest quest) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l📋 " + quest.getName()))
            .rows(4)
            .disableAllInteractions()
            .create();

        // 任务信息
        gui.setItem(4, createGuiItem(
            Material.BOOK,
            Component.text("§e§l" + quest.getName(), NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7类型: §f" + quest.getType()),
                Component.text("§7难度: §f" + quest.getDifficulty()),
                Component.text(""),
                Component.text("§7描述: §f" + quest.getDescription()),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 目标
        gui.setItem(11, createGuiItem(
            Material.PAPER,
            Component.text("§b📝 任务目标", NamedTextColor.AQUA),
            buildQuestLore(quest, false),
            event -> {}, false
        ));

        // 奖励
        gui.setItem(15, createGuiItem(
            Material.CHEST,
            Component.text("§6💰 任务奖励", NamedTextColor.GOLD),
            buildQuestLore(quest, false),
            event -> {}, false
        ));

        // 返回按钮
        gui.setItem(22, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回任务列表", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回每日任务列表")),
            event -> {
                event.setCancelled(true);
                openDailyQuestMenu(player);
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开踢出确认铁砧
     */
    private void openKickConfirmAnvil(Player player, Nation nation, NationMember member) {
        player.closeInventory();

        anvilProvider.openAnvilInput(
            player,
            "确认踢出 " + member.playerName(),
            member.playerName(),
            input -> {
                if (!member.playerName().equals(input.trim())) {
                    player.sendMessage(Component.text("⚠ 名称不匹配，踢出操作已取消", NamedTextColor.RED));
                    return;
                }

                nationModule.removeMember(nation.id(), member.playerId());
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                player.sendMessage(Component.text("§a已将 " + member.playerName() + " 踢出国家", NamedTextColor.GREEN));

                // 重新打开权限菜单
                nationModule.nationById(nation.id()).ifPresent(updated -> {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> openPermissionMenu(player, updated), 2L);
                });
            },
            () -> {
                // 取消时重新打开权限编辑菜单
                nationModule.nationById(nation.id()).ifPresent(updated -> {
                    updated.members().stream()
                        .filter(m -> m.playerId().equals(member.playerId()))
                        .findFirst()
                        .ifPresent(updatedMember ->
                            openMemberPermissionEdit(player, updated, updatedMember));
                });
            }
        );
    }

    /**
     * 获取等级对应的颜色代码
     */
    private String getRankColor(String rank) {
        return switch (rank.toLowerCase()) {
            case "admin" -> "§c";
            case "officer" -> "§a";
            default -> "§7";
        };
    }

    /**
     * 填充边框
     */
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

    /**
     * 创建 GUI 物品（支持 TextComponent 和隐藏标记）
     */
    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action,
                                   boolean hideItem) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        if (!hideItem && material != Material.BARRIER && material != Material.GRAY_STAINED_GLASS_PANE) {
            item = addGlow(item);
        }
        return new GuiItem(item, action);
    }

    /**
     * 创建 GUI 物品
     */
    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action) {
        return createGuiItem(material, name, lore, action, false);
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
            logger.warning("Failed to add glow effect: " + e.getMessage());
        }
        item.setItemMeta(meta);
        return item;
    }

    // ══════════════════════════════════════════════════════════════
    //  NationModule 扩展方法接口（需要在 NationModule 中实现）
    // ══════════════════════════════════════════════════════════════

    /**
     * 设置政体类型
     */
    public interface GovernmentTypeSetter {
        boolean setGovernmentType(NationId nationId, GovernmentType type);
    }

    /**
     * 设置税率
     */
    public interface TaxRateSetter {
        void setTaxRate(NationId nationId, double rate);
    }

    /**
     * 解散国家
     */
    public interface NationDisbander {
        boolean disbandNation(NationId nationId);
    }

    /**
     * 获取领土价格详情
     */
    public interface ClaimPriceProvider {
        ClaimPriceBreakdown getClaimPriceBreakdown(NationId nationId);
    }
}
