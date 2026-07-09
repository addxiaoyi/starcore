package dev.starcore.starcore.module.nation.gui;
import java.util.Optional;
import java.util.logging.Logger;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelationSnapshot;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.WarRecord;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.technology.TechnologyCost;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.nation.claimtool.ClaimToolService;
import dev.starcore.starcore.quest.DailyQuestService;
import dev.starcore.starcore.quest.QuestService;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 TriumphGUI + YAML 配置的国家管理菜单
 * 所有菜单项、图标、命令均可通过 nation-menu.yml 配置
 *
 * audit C-068: 此类与 NationManagementMenu（InventoryHolder 直写模式）在 UI 上存在
 *   功能重叠 —— 后者是 listener-driven 的低级实现，而本类基于 TriumphGUI 框架并通过
 *   YAML 配置驱动。合并需要统一渲染后端（保留 TriumphGUI 路线）并迁移
 *   NationManagementMenuListener 的 click 处理逻辑至 TriumphGUI GuiItem action，
 *   涉及数百行重构与外部 nation-menu.yml/testcase 调整。本工件不进行合并：
 *   TODO audit C-068: 后续迁移 NationManagementMenuListener 的 click handling 至
 *            TriumphNationMenu 的 GuiItem action，再删除 NationManagementMenu 与
 *            NationManagementMenuListener 的低级 InventoryClick 处理路径。
 */
public class TriumphNationMenu implements NationMenuProvider {
    private final NationModule nationModule;
    private final MessageService messages;
    private final PacketEventsAnvilProvider anvilProvider;
    private final TreasuryService treasuryService;
    private final DiplomacyService diplomacyService;
    private final TechnologyService technologyService;
    private final GovernmentService governmentService;
    private final EconomyService economyService;
    private final Plugin plugin;
    private final NationMenuConfig menuConfig;
    private final PolicyService policyService;
    private final Logger logger;
    private PolicyNationMenu policyNationMenu;
    private NationAdvancedMenu advancedMenu;

    // 分页会话追踪
    private final Map<UUID, PaginationSession> paginationSessions = new ConcurrentHashMap<>();

    private static class PaginationSession {
        final NationId nationId;
        String currentSubmenu;
        int membersPage = 1;
        int territoryPage = 1;
        int diplomacyPage = 1;

        PaginationSession(NationId nationId, String submenu) {
            this.nationId = nationId;
            this.currentSubmenu = submenu;
        }
    }

    public TriumphNationMenu(NationModule nationModule, MessageService messages,
                             PacketEventsAnvilProvider anvilProvider,
                             TreasuryService treasuryService,
                             DiplomacyService diplomacyService,
                             TechnologyService technologyService,
                             GovernmentService governmentService,
                             EconomyService economyService,
                             PolicyService policyService,
                             Plugin plugin) {
        this.nationModule = nationModule;
        this.messages = messages;
        this.anvilProvider = anvilProvider;
        this.treasuryService = treasuryService;
        this.diplomacyService = diplomacyService;
        this.technologyService = technologyService;
        this.governmentService = governmentService;
        this.economyService = economyService;
        this.policyService = policyService;
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.menuConfig = new NationMenuConfig(plugin);
        // 初始化 PolicyNationMenu
        if (policyService != null) {
            this.policyNationMenu = new PolicyNationMenu(policyService, treasuryService, plugin, nationModule);
            this.policyNationMenu.setMainMenuCallback(this::openMainMenu);
        }

        // 初始化 NationAdvancedMenu（高级功能：政体变更、税率、权限、解散、领土购买、每日任务）
        ClaimToolService claimToolService = nationModule.getClaimToolService();
        QuestService questService = nationModule.getQuestService();
        DailyQuestService dailyQuestService = nationModule.getDailyQuestService();
        this.advancedMenu = new NationAdvancedMenu(
            nationModule, plugin, messages, anvilProvider,
            treasuryService, economyService, governmentService,
            claimToolService, questService, dailyQuestService
        );
    }

    @Override
    public void openMainMenu(Player player) {
        Optional<Nation> nationOpt = nationModule.getNationByMember(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            openVisitorMenu(player);
            return;
        }

        Nation nation = nationOpt.get();
        // 刷新国家缓存数据，确保 GUI 显示最新统计信息
        nationModule.refreshNationCache(nation.id());
        openMemberMenu(player, nation);
    }

    @Override
    public void openSubMenu(Player player, Nation nation, String submenuId) {
        // 刷新国家缓存数据
        nationModule.refreshNationCache(nation.id());

        // 特殊处理成员列表（带分页）
        if ("members".equals(submenuId)) {
            openMembersSubmenu(player, nation, 1);
            return;
        }

        // 特殊处理领土列表（带分页）
        if ("territory".equals(submenuId)) {
            openTerritorySubmenu(player, nation, 1);
            return;
        }

        // 特殊处理外交列表（带分页）
        if ("diplomacy".equals(submenuId)) {
            openDiplomacySubmenu(player, nation, 1);
            return;
        }

        NationMenuConfig.MenuLayout layout = menuConfig.getSubmenuLayout(submenuId);
        if (layout == null) {
            player.sendMessage(Component.text("⚠ 子菜单不存在: " + submenuId, NamedTextColor.RED));
            return;
        }

        Map<String, String> replacements = buildReplacements(nation);

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(layout.getTitle()))
            .rows(layout.getRows())
            .disableAllInteractions()
            .create();

        for (NationMenuConfig.ItemConfig itemConfig : layout.getSortedItems()) {
            if (itemConfig == null) continue;

            String requiredPerm = itemConfig.getPermission();
            if (requiredPerm != null && !requiredPerm.isEmpty()) {
                if (!checkPermission(player, nation, requiredPerm)) {
                    continue;
                }
            }

            NationMenuConfig.ItemConfig resolved = itemConfig.withReplacements(replacements);
            GuiItem guiItem = buildGuiItemWithDynamic(player, nation, resolved);
            if (guiItem != null) {
                gui.setItem(resolved.getSlot(), guiItem);
            }
        }

        if (layout.isBorderEnabled()) {
            fillBorder(gui, layout.getBorderMaterial());
        }

        gui.open(player);
    }

    /**
     * 根据物品配置构建 GuiItem（支持动态类型）
     */
    private GuiItem buildGuiItemWithDynamic(Player player, Nation nation, NationMenuConfig.ItemConfig config) {
        String type = config.getType();

        if ("member_list".equals(type)) {
            return buildMemberListItem(player, nation, config);
        }

        return buildGuiItem(player, nation, config);
    }

    /**
     * 获取或创建分页会话
     */
    private PaginationSession getOrCreatePaginationSession(Player player, Nation nation, String submenu) {
        UUID playerId = player.getUniqueId();
        PaginationSession session = paginationSessions.get(playerId);
        if (session == null || !session.nationId.value().equals(nation.id().value())) {
            session = new PaginationSession(nation.id(), submenu);
            paginationSessions.put(playerId, session);
        } else {
            session.currentSubmenu = submenu;
        }
        return session;
    }

    /**
     * 构建成员列表物品（动态生成，带分页）
     */
    private GuiItem buildMemberListItem(Player player, Nation nation, NationMenuConfig.ItemConfig config) {
        Material material = config.getMaterial();
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(config.getDisplayName());
        List<Component> lore = new ArrayList<>();

        int memberCount = nation.memberCount();
        lore.add(Component.text("§7成员数量: §e" + memberCount + " 人", NamedTextColor.GRAY));

        if (memberCount <= 5) {
            for (NationMember member : nation.members()) {
                String rankColor = "admin".equals(member.rank()) ? "§c" : "§7";
                lore.add(Component.text(rankColor + "• " + member.playerName() + " §8(" + member.rank() + ")", NamedTextColor.GRAY));
            }
        } else {
            lore.add(Component.text("§e点击查看所有成员", NamedTextColor.YELLOW));
        }

        return createGuiItem(material, name, lore, event -> {
            event.setCancelled(true);
            openSubMenu(player, nation, "members");
        }, config.isGlow());
    }

    /**
     * 构建分页成员列表项
     */
    private void fillMemberListPage(Player player, Nation nation, Gui gui, int page) {
        final Gui guiRef = gui;
        List<NationMember> members = new ArrayList<>();
        nation.members().forEach(members::add);

        int pageSize = 28; // 槽位 10-43，排除边框
        int totalPages = Math.max(1, (int) Math.ceil((double) members.size() / pageSize));
        final int currentPage = Math.max(1, Math.min(page, totalPages));

        int startIdx = (currentPage - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, members.size());

        // 填充成员物品（槽位 10-43，跳过边框）
        int slot = 10;
        for (int i = startIdx; i < endIdx; i++) {
            if (slot == 17 || slot == 26 || slot == 35) {
                slot++; // 跳过右边边框
            }
            if (slot > 43) break;

            NationMember member = members.get(i);
            String rankColor = "admin".equals(member.rank()) ? "§c" : ("officer".equals(member.rank()) ? "§a" : "§7");
            Component displayName = net.kyori.adventure.text.Component.text(rankColor + member.playerName());

            List<net.kyori.adventure.text.Component> memberLore = List.of(
                net.kyori.adventure.text.Component.text("§7等级: " + member.rank()),
                net.kyori.adventure.text.Component.text("§7加入时间: §e" + member.joinedDate()),
                net.kyori.adventure.text.Component.text(""),
                net.kyori.adventure.text.Component.text("§a▸ 点击查看详情")
            );

            Material headMat = Material.PLAYER_HEAD;
            guiRef.setItem(slot, createGuiItem(headMat, displayName, memberLore, event -> {
                event.setCancelled(true);
                openMemberDetailMenu(player, nation, member);
            }, false));
            slot++;
        }

        // 上一页按钮
        if (page > 1) {
            guiRef.setItem(45, createGuiItem(
                Material.ARROW,
                Component.text("§e◀ 上一页", NamedTextColor.YELLOW),
                List.of(
                    Component.text(""),
                    Component.text("§7第 " + currentPage + " 页 / 共 " + totalPages + " 页"),
                    Component.text("")
                ),
                event -> {
                    event.setCancelled(true);
                    Nation capturedNation = nation;
                    Plugin capturedPlugin = plugin;
                    int prevPage = currentPage - 1;
                    guiRef.close(player);
                    capturedPlugin.getServer().getScheduler().runTask(capturedPlugin, () -> openMembersSubmenu(player, capturedNation, prevPage));
                },
                false
            ));
        }

        // 页码显示
        guiRef.setItem(49, createGuiItem(
            Material.BOOK,
            Component.text("§e成员列表", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7总成员数: §a" + members.size()),
                Component.text("§7当前页: §e" + currentPage + " §7/ §e" + totalPages),
                Component.text("")
            ),
            event -> event.setCancelled(true),
            false
        ));

        // 下一页按钮
        if (page < totalPages) {
            guiRef.setItem(53, createGuiItem(
                Material.ARROW,
                Component.text("§e下一页 ▶", NamedTextColor.YELLOW),
                List.of(
                    Component.text(""),
                    Component.text("§7第 " + currentPage + " 页 / 共 " + totalPages + " 页"),
                    Component.text("")
                ),
                event -> {
                    event.setCancelled(true);
                    Nation capturedNation = nation;
                    Plugin capturedPlugin = plugin;
                    int nextPage = currentPage + 1;
                    guiRef.close(player);
                    capturedPlugin.getServer().getScheduler().runTask(capturedPlugin, () -> openMembersSubmenu(player, capturedNation, nextPage));
                },
                false
            ));
        }
    }

    /**
     * 打开成员列表子菜单（带分页）
     */
    private void openMembersSubmenu(Player player, Nation nation, int page) {
        NationMenuConfig.MenuLayout layout = menuConfig.getSubmenuLayout("members");
        if (layout == null) {
            // Fallback: 使用简单布局
            int memberCount = nation.memberCount();
            int totalPages = Math.max(1, (int) Math.ceil((double) memberCount / 28));

            Gui gui = Gui.gui()
                .title(Component.text("§e§l" + nation.name() + " §7| 成员列表"))
                .rows(6)
                .disableAllInteractions()
                .create();

            fillMemberListPage(player, nation, gui, page);

            // 返回按钮
            gui.setItem(48, createGuiItem(
                Material.BARRIER,
                Component.text("§c✖ 返回主菜单", NamedTextColor.RED),
                null,
                event -> {
                    event.setCancelled(true);
                    paginationSessions.remove(player.getUniqueId());
                    openMemberMenu(player, nation);
                },
                false
            ));

            fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
            gui.open(player);
            return;
        }

        // 使用配置布局
        Map<String, String> replacements = buildReplacements(nation);
        replacements.put("current_page", String.valueOf(page));
        replacements.put("total_pages", String.valueOf(Math.max(1, (int) Math.ceil((double) nation.memberCount() / 28))));

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(layout.getTitle()))
            .rows(layout.getRows())
            .disableAllInteractions()
            .create();

        for (NationMenuConfig.ItemConfig itemConfig : layout.getSortedItems()) {
            if (itemConfig == null) continue;

            String command = itemConfig.getCommand();
            // 处理分页命令
            if ("submenu:members:prev".equals(command)) {
                if (page > 1) {
                    NationMenuConfig.ItemConfig resolved = itemConfig.withReplacements(replacements);
                    gui.setItem(resolved.getSlot(), createGuiItem(
                        resolved.getMaterial(),
                        LegacyComponentSerializer.legacyAmpersand().deserialize(resolved.getDisplayName()),
                        resolved.getLore().stream().map(l -> LegacyComponentSerializer.legacyAmpersand().deserialize(l)).collect(java.util.stream.Collectors.toList()),
                        event -> {
                            event.setCancelled(true);
                            Nation capturedNation = nation;
                            gui.close(player);
                            plugin.getServer().getScheduler().runTask(plugin, () -> openMembersSubmenu(player, capturedNation, page - 1));
                        },
                        resolved.isGlow()
                    ));
                }
            } else if ("submenu:members:next".equals(command)) {
                int totalPages = Math.max(1, (int) Math.ceil((double) nation.memberCount() / 28));
                if (page < totalPages) {
                    NationMenuConfig.ItemConfig resolved = itemConfig.withReplacements(replacements);
                    gui.setItem(resolved.getSlot(), createGuiItem(
                        resolved.getMaterial(),
                        LegacyComponentSerializer.legacyAmpersand().deserialize(resolved.getDisplayName()),
                        resolved.getLore().stream().map(l -> LegacyComponentSerializer.legacyAmpersand().deserialize(l)).collect(java.util.stream.Collectors.toList()),
                        event -> {
                            event.setCancelled(true);
                            Nation capturedNation = nation;
                            gui.close(player);
                            plugin.getServer().getScheduler().runTask(plugin, () -> openMembersSubmenu(player, capturedNation, page + 1));
                        },
                        resolved.isGlow()
                    ));
                }
            } else if ("submenu:main".equals(command)) {
                NationMenuConfig.ItemConfig resolved = itemConfig.withReplacements(replacements);
                gui.setItem(resolved.getSlot(), createGuiItem(
                    resolved.getMaterial(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(resolved.getDisplayName()),
                    resolved.getLore().stream().map(l -> LegacyComponentSerializer.legacyAmpersand().deserialize(l)).collect(java.util.stream.Collectors.toList()),
                    event -> {
                        event.setCancelled(true);
                        paginationSessions.remove(player.getUniqueId());
                        openMemberMenu(player, nation);
                    },
                    resolved.isGlow()
                ));
            } else if (command != null && !command.isEmpty()) {
                NationMenuConfig.ItemConfig resolved = itemConfig.withReplacements(replacements);
                gui.setItem(resolved.getSlot(), createGuiItem(
                    resolved.getMaterial(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(resolved.getDisplayName()),
                    resolved.getLore().stream().map(l -> LegacyComponentSerializer.legacyAmpersand().deserialize(l)).collect(java.util.stream.Collectors.toList()),
                    event -> {
                        event.setCancelled(true);
                        Player p = (Player) event.getWhoClicked();
                        p.closeInventory();
                        p.performCommand(command);
                    },
                    resolved.isGlow()
                ));
            }
        }

        // 填充动态成员物品
        fillMemberListPage(player, nation, gui, page);

        if (layout.isBorderEnabled()) {
            fillBorder(gui, layout.getBorderMaterial());
        }

        gui.open(player);
    }

    /**
     * 成员模式菜单（完全由配置驱动）
     */
    private void openMemberMenu(Player player, Nation nation) {
        NationMenuConfig.MenuLayout layout = menuConfig.getMainMemberLayout();
        if (layout == null) {
            player.sendMessage(Component.text("⚠ 菜单配置加载失败，请联系管理员", NamedTextColor.RED));
            return;
        }

        // 构建占位符替换
        Map<String, String> replacements = buildReplacements(nation);

        // 创建 GUI
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(layout.getTitle()))
            .rows(layout.getRows())
            .disableAllInteractions()
            .create();

        // 填充所有配置的物品
        for (NationMenuConfig.ItemConfig itemConfig : layout.getSortedItems()) {
            if (itemConfig == null) continue;

            // 检查权限
            String requiredPerm = itemConfig.getPermission();
            if (requiredPerm != null && !requiredPerm.isEmpty()) {
                if (!checkPermission(player, nation, requiredPerm)) {
                    continue;
                }
            }

            // 替换占位符
            NationMenuConfig.ItemConfig resolved = itemConfig.withReplacements(replacements);
            GuiItem guiItem = buildGuiItemWithDynamic(player, nation, resolved);
            if (guiItem != null) {
                gui.setItem(resolved.getSlot(), guiItem);
            }
        }

        // 填充边框
        if (layout.isBorderEnabled()) {
            fillBorder(gui, layout.getBorderMaterial());
        }

        gui.open(player);
    }

    /**
     * 访客模式菜单（显示所有图标，点击提示操作）
     */
    private void openVisitorMenu(Player player) {
        NationMenuConfig.MenuLayout layout = menuConfig.getMainVisitorLayout();
        if (layout == null) {
            // fallback to basic visitor menu
            openBasicVisitorMenu(player);
            return;
        }

        // 创建 GUI
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(layout.getTitle()))
            .rows(layout.getRows())
            .disableAllInteractions()
            .create();

        // 填充所有配置的物品
        for (NationMenuConfig.ItemConfig itemConfig : layout.getSortedItems()) {
            if (itemConfig == null) continue;

            NationMenuConfig.ItemConfig resolved = itemConfig.withReplacements(new HashMap<>());
            GuiItem guiItem = buildVisitorGuiItem(player, resolved);
            if (guiItem != null) {
                gui.setItem(resolved.getSlot(), guiItem);
            }
        }

        // 访客提示物品
        NationMenuConfig.ItemConfig guideConfig = menuConfig.getVisitorGuideItem();
        if (guideConfig != null) {
            GuiItem guideItem = buildGuiItemStatic(guideConfig);
            if (guideItem != null) {
                gui.setItem(guideConfig.getSlot(), guideItem);
            }
        }

        // 填充边框
        if (layout.isBorderEnabled()) {
            fillBorder(gui, layout.getBorderMaterial());
        }

        gui.open(player);
    }

    /**
     * 最基础的访客菜单（配置加载失败时的 fallback）
     */
    private void openBasicVisitorMenu(Player player) {
        Gui gui = Gui.gui()
            .title(Component.text("⚔ 国家系统 - 访客模式", NamedTextColor.GRAY))
            .rows(4)
            .disableAllInteractions()
            .create();

        // 创建国家
        gui.setItem(13, createGuiItem(
            Material.NETHER_STAR,
            Component.text("🌟 创建国家", NamedTextColor.GOLD, TextDecoration.BOLD),
            List.of(
                Component.empty(),
                Component.text("消耗: 500 星尘", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("▸ 点击创建国家", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                ((Player) event.getWhoClicked()).closeInventory();
                ((Player) event.getWhoClicked()).performCommand("sc nation create");
            }
        ));

        // 查看国家列表
        gui.setItem(15, createGuiItem(
            Material.BOOK,
            Component.text("📖 浏览国家", NamedTextColor.GREEN, TextDecoration.BOLD),
            List.of(
                Component.empty(),
                Component.text("查看所有国家列表", NamedTextColor.GRAY),
                Component.text("了解各国家详情", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("▸ 点击查看", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                ((Player) event.getWhoClicked()).closeInventory();
                ((Player) event.getWhoClicked()).performCommand("sc nation list");
            }
        ));

        // 关闭
        gui.setItem(31, createGuiItem(
            Material.BARRIER,
            Component.text("✖ 关闭", NamedTextColor.RED, TextDecoration.BOLD),
            null,
            event -> ((Player) event.getWhoClicked()).closeInventory()
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 根据物品配置构建 GuiItem（成员模式）
     */
    private GuiItem buildGuiItem(Player player, Nation nation, NationMenuConfig.ItemConfig config) {
        Material material = config.getMaterial();
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(config.getDisplayName());
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        }

        String command = config.getCommand();

        // 子菜单命令处理
        if (command.startsWith("submenu:")) {
            String submenu = command.substring(8);
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                Nation capturedNation = nation;
                // 处理带分页参数的子菜单命令
                if (submenu.equals("members:prev")) {
                    PaginationSession session = getOrCreatePaginationSession(p, capturedNation, "members");
                    int currentPage = Math.max(1, session.membersPage - 1);
                    session.membersPage = currentPage;
                    openMembersSubmenu(p, capturedNation, currentPage);
                    return;
                }
                if (submenu.equals("members:next")) {
                    PaginationSession session = getOrCreatePaginationSession(p, capturedNation, "members");
                    session.membersPage++;
                    openMembersSubmenu(p, capturedNation, session.membersPage);
                    return;
                }
                if (submenu.equals("main")) {
                    openMainMenu(p);
                    return;
                }
                // 普通子菜单命令
                switch (submenu) {
                    case "diplomacy" -> openDiplomacySubmenu(p, capturedNation, 1);
                    case "technology" -> openTechnologySubmenu(p, capturedNation);
                    case "policy" -> openPolicySubmenu(p, capturedNation);
                    case "territory" -> openTerritorySubmenu(p, capturedNation, 1);
                    case "settings" -> openSettingsSubmenu(p, capturedNation);
                    case "treasury" -> openTreasurySubmenu(p, capturedNation);
                    case "members" -> openMembersSubmenu(p, capturedNation, 1);
                }
            }, config.isGlow());
        }

        // 特殊命令处理
        if (command.equals("anvil_rename")) {
            return createGuiItem(material, name, lore, createRenameAction(nation), config.isGlow());
        }

        // 国库命令处理
        if (command.equals("sc treasury")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                Nation capturedNation = nation;
                openTreasurySubmenu(p, capturedNation);
            }, config.isGlow());
        }

        // 国家信息命令处理 - 直接显示玩家所在国家的详细信息
        if (command.equals("sc nation info")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                Nation playerNation = nationModule.nationOf(p.getUniqueId()).orElse(null);
                if (playerNation != null) {
                    p.closeInventory();
                    // 直接调用命令并传递国家名称
                    p.performCommand("sc nation info " + playerNation.name());
                } else {
                    p.sendMessage(net.kyori.adventure.text.Component.text("⚠ 你还没有加入任何国家", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }, config.isGlow());
        }

        // 每日任务命令处理
        if (command.equals("daily")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                p.performCommand("daily");
            }, config.isGlow());
        }

        // 官员命令处理
        if (command.equals("sc officer")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                // 使用 status 子命令，显示玩家国家的官员状态
                p.performCommand("sc officer status");
            }, config.isGlow());
        }

        // 帮助命令处理
        if (command.equals("sc help nation")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                p.performCommand("sc help nation");
            }, config.isGlow());
        }

        // 外交命令处理（外部菜单）
        if (command.equals("sc diplomacy")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                // 使用 list 子命令，显示玩家国家的外交关系
                p.performCommand("sc diplomacy list");
            }, config.isGlow());
        }

        // 科技命令处理（外部菜单）
        if (command.equals("sc technology")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                // 使用 status 子命令，显示玩家国家的科技状态
                p.performCommand("sc technology status");
            }, config.isGlow());
        }

        // 指导/教程命令处理
        if (command.startsWith("tutorial ")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                p.performCommand(command);
            }, config.isGlow());
        }

        // 普通命令
        if (command.isEmpty() || command.equals("close")) {
            GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action = event -> {
                event.setCancelled(true);
                if (command.equals("close")) {
                    ((Player) event.getWhoClicked()).closeInventory();
                }
            };
            return createGuiItem(material, name, lore, action, config.isGlow());
        }

        return createGuiItem(material, name, lore, event -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            p.closeInventory();
            p.performCommand(command);
        }, config.isGlow());
    }

    /**
     * 根据物品配置构建 GuiItem（访客模式）
     */
    private GuiItem buildVisitorGuiItem(Player player, NationMenuConfig.ItemConfig config) {
        Material material = config.getMaterial();
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(config.getDisplayName());
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        }

        String command = config.getCommand();

        // 特殊命令处理 - 创建国家
        if (command.equals("anvil_create_nation")) {
            return createGuiItem(material, name, lore, createCreateNationAction(player), config.isGlow());
        }

        // 外交命令处理
        if (command.equals("sc diplomacy")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                // 使用 list 子命令显示外交状态
                p.performCommand("sc diplomacy list");
            }, config.isGlow());
        }

        // 科技命令处理
        if (command.equals("sc technology")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                // 使用 status 子命令显示科技状态
                p.performCommand("sc technology status");
            }, config.isGlow());
        }

        // 国家列表命令处理
        if (command.equals("sc nation list")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                p.performCommand("sc nation list");
            }, config.isGlow());
        }

        // 指导/教程命令处理
        if (command.startsWith("tutorial ")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                p.closeInventory();
                p.performCommand(command);
            }, config.isGlow());
        }

        // 普通命令
        if (command.isEmpty() || command.equals("close")) {
            GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action = event -> {
                event.setCancelled(true);
                if (command.equals("close")) {
                    ((Player) event.getWhoClicked()).closeInventory();
                }
            };
            return createGuiItem(material, name, lore, action, config.isGlow());
        }

        return createGuiItem(material, name, lore, event -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            p.closeInventory();
            p.performCommand(command);
        }, config.isGlow());
    }

    /**
     * 构建静态物品（无动态数据）
     */
    private GuiItem buildGuiItemStatic(NationMenuConfig.ItemConfig config) {
        Material material = config.getMaterial();
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(config.getDisplayName());
        List<Component> lore = new ArrayList<>();
        for (String line : config.getLore()) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        }

        String command = config.getCommand();

        // 处理返回主菜单命令
        if (command.equals("submenu:main")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                openMainMenu(p);
            }, config.isGlow());
        }

        // 处理返回关闭命令
        if (command.equals("close")) {
            return createGuiItem(material, name, lore, event -> {
                event.setCancelled(true);
                ((Player) event.getWhoClicked()).closeInventory();
            }, config.isGlow());
        }

        // 其他情况只取消点击不执行任何操作
        return createGuiItem(material, name, lore, (event -> event.setCancelled(true)), config.isGlow());
    }

    /**
     * 创建国家动作（Anvil 输入）
     */
    private GuiAction<org.bukkit.event.inventory.InventoryClickEvent> createCreateNationAction(Player player) {
        return event -> {
            event.setCancelled(true);
            Player p = (Player) event.getWhoClicked();
            p.closeInventory();

            anvilProvider.openAnvilInput(
                p,
                "§e§l输入国家名称",
                "",
                newInput -> {
                    if (newInput == null || newInput.trim().isEmpty()) {
                        p.sendMessage(Component.text("⚠ 国家名称不能为空！", NamedTextColor.RED));
                        scheduleReopenMenu(p);
                        return;
                    }

                    String trimmedInput = newInput.trim();

                    if (trimmedInput.length() < 2 || trimmedInput.length() > 16) {
                        p.sendMessage(Component.text("⚠ 国家名称长度必须在 2-16 个字符之间！", NamedTextColor.RED));
                        scheduleReopenMenu(p);
                        return;
                    }

                    if (!trimmedInput.matches("[一-龥a-zA-Z0-9_]+")) {
                        p.sendMessage(Component.text("⚠ 国家名称只能包含中文、英文字母、数字和下划线！", NamedTextColor.RED));
                        scheduleReopenMenu(p);
                        return;
                    }

                    Optional<Nation> existing = nationModule.getNationByName(trimmedInput);
                    if (existing.isPresent()) {
                        p.sendMessage(Component.text("⚠ 该名称已被使用！", NamedTextColor.RED));
                        scheduleReopenMenu(p);
                        return;
                    }

                    p.performCommand("sc nation create " + trimmedInput);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    p.sendMessage(Component.text("✅ 国家创建成功！", NamedTextColor.GREEN));
                    // 延迟打开设置菜单，让创建命令先完成
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        nationModule.getNationByMember(p.getUniqueId()).ifPresent(newNation ->
                            openSettingsSubmenu(p, newNation));
                    }, 5L);
                },
                () -> {
                    p.sendMessage(Component.text("已取消创建", NamedTextColor.GRAY));
                    scheduleReopenMenu(p);
                }
            );
        };
    }

    /**
     * 创建国家改名动作（Anvil 输入）
     */
    private GuiAction<org.bukkit.event.inventory.InventoryClickEvent> createRenameAction(Nation nation) {
        return event -> {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            UUID playerId = player.getUniqueId();

            if (!nation.hasPermission(playerId, "admin")) {
                player.closeInventory();
                player.sendMessage(Component.text("⚠ 没有权限：需要管理员权限才能修改国家名称", NamedTextColor.RED));
                return;
            }

            player.closeInventory();

            anvilProvider.openAnvilInput(
                player,
                "§e§l输入新的国家名称",
                nation.name(),
                newInput -> {
                    if (newInput == null || newInput.trim().isEmpty()) {
                        player.sendMessage(Component.text("⚠ 国家名称不能为空！", NamedTextColor.RED));
                        scheduleReopenMenu(player);
                        return;
                    }

                    String trimmedInput = newInput.trim();

                    if (trimmedInput.length() < 2 || trimmedInput.length() > 16) {
                        player.sendMessage(Component.text("⚠ 国家名称长度必须在 2-16 个字符之间！", NamedTextColor.RED));
                        scheduleReopenMenu(player);
                        return;
                    }

                    if (!trimmedInput.matches("[一-龥a-zA-Z0-9_]+")) {
                        player.sendMessage(Component.text("⚠ 国家名称只能包含中文、英文字母、数字和下划线！", NamedTextColor.RED));
                        scheduleReopenMenu(player);
                        return;
                    }

                    Optional<Nation> existing = nationModule.getNationByName(trimmedInput);
                    if (existing.isPresent() && !existing.get().id().equals(nation.id())) {
                        player.sendMessage(Component.text("⚠ 该名称已被其他国家使用！", NamedTextColor.RED));
                        scheduleReopenMenu(player);
                        return;
                    }

                    boolean renamed = nationModule.renameNation(nation.id(), trimmedInput);
                    if (renamed) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                        player.sendMessage(Component.text("✅ 国家名称已修改为: " + trimmedInput, NamedTextColor.GREEN));
                        // 重新打开设置菜单（而非主菜单），显示改名后的名称
                        nationModule.nationById(nation.id()).ifPresent(updated -> openSettingsSubmenu(player, updated));
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("❌ 改名失败，请稍后重试", NamedTextColor.RED));
                        scheduleReopenMenu(player);
                    }
                },
                () -> {
                    player.sendMessage(Component.text("已取消改名", NamedTextColor.GRAY));
                    scheduleReopenMenu(player);
                }
            );
        };
    }

    private void scheduleReopenMenu(Player player) {
        // 10 ticks (0.5s) 延迟确保消息有足够时间显示后再打开菜单
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 10L);
    }

    /**
     * 检查玩家权限
     */
    private boolean checkPermission(Player player, Nation nation, String requiredPerm) {
        if (requiredPerm == null || requiredPerm.isEmpty()) {
            return true;
        }

        switch (requiredPerm) {
            case "admin":
                return nation.hasPermission(player.getUniqueId(), "admin");
            case "cost_check":
                return true; // 成本检查由命令处理
            default:
                return player.hasPermission("starcore.nation." + requiredPerm)
                    || player.hasPermission("starcore.admin");
        }
    }

    /**
     * 构建占位符替换映射
     */
    private Map<String, String> buildReplacements(Nation nation) {
        int level = nationModule.levelOf(nation.id());
        String balance = treasuryService != null ? treasuryService.balance(nation.id()).toPlainString() : "N/A";

        Collection<DiplomacyRelationSnapshot> relations = diplomacyService != null
            ? diplomacyService.relationsOf(nation.id())
            : List.of();
        long allyCount = relations.stream().filter(r -> r.relation() == DiplomacyRelation.ALLIED).count();
        long hostileCount = relations.stream().filter(r ->
            r.relation() == DiplomacyRelation.HOSTILE || r.relation() == DiplomacyRelation.WAR).count();

        int techUnlocked = technologyService != null
            ? technologyService.unlockedTechnologies(nation.id()).size()
            : 0;
        int techTotal = technologyService != null
            ? technologyService.availableTechnologies().size()
            : 0;

        Map<String, String> replacements = new HashMap<>();
        replacements.put("nation_name", nation.name());
        replacements.put("nation_level", String.valueOf(level));
        replacements.put("member_count", String.valueOf(nation.memberCount()));
        replacements.put("claim_count", String.valueOf(nationModule.claimCount(nation.id())));
        replacements.put("balance", balance);
        replacements.put("ally_count", String.valueOf(allyCount));
        replacements.put("hostile_count", String.valueOf(hostileCount));
        replacements.put("tech_unlocked", String.valueOf(techUnlocked));
        replacements.put("tech_total", String.valueOf(techTotal));
        replacements.put("government_type", nation.governmentType().name());

        return replacements;
    }

    /**
     * 填充边框
     */
    private void fillBorder(Gui gui, Material borderMaterial) {
        if (borderMaterial == null) {
            borderMaterial = Material.GRAY_STAINED_GLASS_PANE;
        }
        GuiItem borderItem = new GuiItem(new ItemStack(borderMaterial));

        // Top row
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderItem);
        }

        // Bottom row
        int rows = gui.getRows();
        for (int i = 0; i < 9; i++) {
            gui.setItem((rows - 1) * 9 + i, borderItem);
        }

        // Left column (skip top/bottom)
        for (int i = 1; i < rows - 1; i++) {
            gui.setItem(i * 9, borderItem);
        }

        // Right column (skip top/bottom)
        for (int i = 1; i < rows - 1; i++) {
            gui.setItem(i * 9 + 8, borderItem);
        }
    }

    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        // 发光效果：通过添加隐藏的附魔
        if (material != Material.BARRIER && material != Material.GRAY_STAINED_GLASS_PANE) {
            item = addGlow(item);
        }
        return new GuiItem(item, action);
    }

    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action, boolean glow) {
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
            logger.warning("Failed to add glow enchantment: " + e.getMessage());
        }
                        // 静默跳过，保持数据兼容
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public String getProviderType() {
        return "TriumphNationMenu (TriumphGUI + PacketEvents Anvil + YAML Config)";
    }

    // ══════════════════════════════════════════════════════════════
    //  成员详情子菜单
    // ══════════════════════════════════════════════════════════════

    /**
     * 打开成员详情菜单
     */
    public void openMemberDetailMenu(Player player, Nation nation, NationMember member) {
        UUID playerId = player.getUniqueId();
        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(playerId))
            .findFirst().orElse(null);

        String title = "§e§l成员详情";
        Gui gui = Gui.gui()
            .title(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(title))
            .rows(5)
            .disableAllInteractions()
            .create();

        // 顶部：成员头颅
        String rankColor = getRankColor(member.rank());
        Component memberName = net.kyori.adventure.text.Component.text(rankColor + "§l" + member.playerName());
        List<Component> headLore = new ArrayList<>();
        headLore.add(net.kyori.adventure.text.Component.text("§7成员信息"));
        headLore.add(net.kyori.adventure.text.Component.text(""));

        boolean isOnline = member.isOnline();
        String onlineStatus = isOnline ? "§a在线" : "§7离线";
        headLore.add(net.kyori.adventure.text.Component.text("§7状态: " + onlineStatus));
        headLore.add(net.kyori.adventure.text.Component.text("§7等级: " + rankColor + member.rank()));
        headLore.add(net.kyori.adventure.text.Component.text("§7加入: §f" + member.joinedDate()));
        if (!isOnline) {
            headLore.add(net.kyori.adventure.text.Component.text("§7最后在线: §f" + member.lastSeenDaysAgo() + " 天前"));
        }

        gui.setItem(4, createGuiItem(
            Material.PLAYER_HEAD,
            memberName,
            headLore,
            event -> event.setCancelled(true),
            false
        ));

        // 中间行：操作按钮
        // 槽位 12 = 升级 | 14 = 降级 | 16 = 踢出

        // 升级按钮
        if (canManageRanks(player, self, nation)) {
            gui.setItem(12, createGuiItem(
                Material.LIME_STAINED_GLASS,
                net.kyori.adventure.text.Component.text("§a§l▲ 提升权限", net.kyori.adventure.text.format.NamedTextColor.GREEN),
                List.of(
                    net.kyori.adventure.text.Component.text(""),
                    net.kyori.adventure.text.Component.text("§7将 §f" + member.playerName() + " §7的权限"),
                    net.kyori.adventure.text.Component.text("§7从 §f" + member.rank() + " §7提升"),
                    net.kyori.adventure.text.Component.text(""),
                    net.kyori.adventure.text.Component.text("§e点击提升权限")
                ),
                event -> {
                    event.setCancelled(true);
                    handleRankChange(player, nation, member, 1);
                },
                false
            ));

            // 降级按钮
            gui.setItem(14, createGuiItem(
                Material.ORANGE_STAINED_GLASS,
                net.kyori.adventure.text.Component.text("§6§l▼ 降低权限", net.kyori.adventure.text.format.NamedTextColor.GOLD),
                List.of(
                    net.kyori.adventure.text.Component.text(""),
                    net.kyori.adventure.text.Component.text("§7将 §f" + member.playerName() + " §7的权限"),
                    net.kyori.adventure.text.Component.text("§7从 §f" + member.rank() + " §7降低"),
                    net.kyori.adventure.text.Component.text(""),
                    net.kyori.adventure.text.Component.text("§e点击降低权限")
                ),
                event -> {
                    event.setCancelled(true);
                    handleRankChange(player, nation, member, -1);
                },
                false
            ));

            // 踢出按钮
            if (!member.playerId().equals(playerId)) {
                gui.setItem(16, createGuiItem(
                    Material.RED_STAINED_GLASS,
                    net.kyori.adventure.text.Component.text("§c§l✖ 踢出成员", net.kyori.adventure.text.format.NamedTextColor.RED),
                    List.of(
                        net.kyori.adventure.text.Component.text(""),
                        net.kyori.adventure.text.Component.text("§7将 §f" + member.playerName() + " §7从"),
                        net.kyori.adventure.text.Component.text("§7国家 §f" + nation.name() + " §7中移除"),
                        net.kyori.adventure.text.Component.text(""),
                        net.kyori.adventure.text.Component.text("§c⚠ 此操作不可撤销！"),
                        net.kyori.adventure.text.Component.text(""),
                        net.kyori.adventure.text.Component.text("§e点击确认踢出")
                    ),
                    event -> {
                        event.setCancelled(true);
                        handleKickMember(player, nation, member);
                    },
                    false
                ));
            }
        } else {
            // 普通成员：无操作权限
            gui.setItem(13, createGuiItem(
                Material.GRAY_STAINED_GLASS_PANE,
                net.kyori.adventure.text.Component.text("§8无操作权限", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY),
                List.of(
                    net.kyori.adventure.text.Component.text(""),
                    net.kyori.adventure.text.Component.text("§7你没有管理权限"),
                    net.kyori.adventure.text.Component.text("")
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 底部：返回按钮
        gui.setItem(22, createGuiItem(
            Material.ARROW,
            net.kyori.adventure.text.Component.text("§e◀ 返回成员列表", net.kyori.adventure.text.format.NamedTextColor.YELLOW),
            List.of(net.kyori.adventure.text.Component.text("")),
            event -> {
                event.setCancelled(true);
                openMembersSubmenu(player, nation, 1);
            },
            false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 处理权限变更（升级/降级）
     * rankDelta: +1 = 升级, -1 = 降级
     */
    private void handleRankChange(Player player, Nation nation, NationMember member, int rankDelta) {
        // rank 顺序：admin > officer > member
        List<String> ranks = List.of("member", "officer", "admin");
        int idx = ranks.indexOf(member.rank().toLowerCase());
        if (idx < 0) idx = 0;

        int newIdx = idx + rankDelta;
        if (newIdx < 0) newIdx = 0;
        if (newIdx >= ranks.size()) newIdx = ranks.size() - 1;

        if (newIdx == idx) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "§c无法继续调整权限！已是最高/最低等级。", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        String newRank = ranks.get(newIdx);
        nationModule.setMemberRank(nation.id(), member.playerId(), newRank);

        String action = rankDelta > 0 ? "提升" : "降低";
        player.sendMessage(net.kyori.adventure.text.Component.text(
            "§a已将 §f" + member.playerName() + " §a的权限" + action + "为 §e" + newRank, net.kyori.adventure.text.format.NamedTextColor.GREEN));

        // 重新打开菜单
        nationModule.nationById(nation.id()).ifPresent(updated ->
            openMemberDetailMenu(player, updated,
                updated.members().stream()
                    .filter(m -> m.playerId().equals(member.playerId()))
                    .findFirst().orElse(member)));
    }

    /**
     * 处理踢出成员（需使用铁砧确认）
     */
    private void handleKickMember(Player player, Nation nation, NationMember member) {
        String prompt = "确认踢出 " + member.playerName() + "？";
        anvilProvider.openAnvilInput(
            player,
            prompt,
            member.playerName(),
            result -> {
                if (result == null || !result.equalsIgnoreCase(member.playerName())) {
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                        "§c名称不匹配，踢出操作已取消。", net.kyori.adventure.text.format.NamedTextColor.RED));
                    return;
                }
                nationModule.removeMember(nation.id(), member.playerId());
                player.sendMessage(net.kyori.adventure.text.Component.text(
                    "§a已将 §f" + member.playerName() + " §a踢出国家。", net.kyori.adventure.text.format.NamedTextColor.GREEN));
                // 重新打开成员列表
                nationModule.nationById(nation.id()).ifPresent(n -> openMembersSubmenu(player, n, 1));
            },
            () -> {
                // 取消时重新打开成员列表
                nationModule.nationById(nation.id()).ifPresent(n -> openMembersSubmenu(player, n, 1));
            }
        );
    }

    /**
     * 判断玩家是否有权限管理成员（管理员或官员）
     */
    private boolean canManageRanks(Player player, NationMember self, Nation nation) {
        if (self == null) return false;
        return "admin".equals(self.rank()) || "officer".equals(self.rank());
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

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("dev.triumphteam.gui.guis.Gui");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ============================================================
    // 子菜单实现
    // ============================================================

    /**
     * 外交关系子菜单
     */
    public void openDiplomacySubmenu(Player player, Nation nation, int page) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l⚔ " + nation.name() + " §7| 外交关系 §7(第" + page + "页)"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部标题
        gui.setItem(4, createGuiItem(
            Material.WRITABLE_BOOK,
            Component.text("§e§l外交关系", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text(""),
                Component.text("§7管理你的外交关系")
            ),
            event -> {}, false
        ));

        // 获取外交关系
        Collection<DiplomacyRelationSnapshot> allRelations = diplomacyService != null
            ? diplomacyService.relationsOf(nation.id())
            : List.of();

        // 分页：每页最多14个
        int itemsPerPage = 14;
        int totalPages = Math.max(1, (int) Math.ceil((double) allRelations.size() / itemsPerPage));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        List<DiplomacyRelationSnapshot> pageRelations = allRelations.stream()
            .skip((long) (page - 1) * itemsPerPage)
            .limit(itemsPerPage)
            .toList();

        int slot = 10;
        int count = 0;
        for (DiplomacyRelationSnapshot rel : pageRelations) {
            if (count >= 14) break; // 最多显示14个关系
            if (slot == 17 || slot == 26 || slot == 35) slot++;

            Nation otherNation = nationModule.nationById(rel.target()).orElse(null);
            String otherName = otherNation != null ? otherNation.name() : rel.target().toString();

            Material mat = switch (rel.relation()) {
                case ALLIED -> Material.DIAMOND;
                case HOSTILE -> Material.BLAZE_POWDER;
                case WAR -> Material.REDSTONE;
                case NEUTRAL -> Material.IRON_INGOT;
                case FRIENDLY -> Material.LAPIS_LAZULI;
                case CEASE_FIRE -> Material.EMERALD;
                case VASSAL -> Material.GOLD_INGOT;
            };

            Component color = switch (rel.relation()) {
                case ALLIED -> Component.text("§b");
                case HOSTILE -> Component.text("§c");
                case WAR -> Component.text("§4");
                case NEUTRAL -> Component.text("§7");
                case FRIENDLY -> Component.text("§3");
                case CEASE_FIRE -> Component.text("§a");
                case VASSAL -> Component.text("§6");
            };

            gui.setItem(slot, createGuiItem(mat,
                Component.text().append(color).content(otherName + " §7[" + rel.relation().displayName() + "]").build(),
                List.of(
                    Component.text(""),
                    Component.text("§7关系: " + rel.relation().displayName()),
                    Component.text("§7目标: §f" + otherName),
                    Component.text(""),
                    Component.text("§a▸ 点击查看详情")
                ),
                event -> {
                    event.setCancelled(true);
                    openDiplomacyDetailSubmenu(player, nation, rel.target());
                }, false
            ));
            slot++;
            count++;
        }

        if (allRelations.isEmpty()) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无外交关系", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7使用 §e/sc diplomacy propose§7 建立外交")
                ),
                event -> {}, false
            ));
        }

        // 分页控制（当有多页时显示）
        if (totalPages > 1) {
            // 上一页
            if (page > 1) {
                final int prevPage = page - 1;
                gui.setItem(45, createGuiItem(
                    Material.ARROW,
                    Component.text("§e◀ 上一页", NamedTextColor.YELLOW),
                    List.of(Component.text("§7第 " + prevPage + " / " + totalPages + " 页")),
                    event -> {
                        event.setCancelled(true);
                        openDiplomacySubmenu(player, nation, prevPage);
                    }, false
                ));
            }
            // 下一页
            if (page < totalPages) {
                final int nextPage = page + 1;
                gui.setItem(53, createGuiItem(
                    Material.ARROW,
                    Component.text("§e▶ 下一页", NamedTextColor.YELLOW),
                    List.of(Component.text("§7第 " + nextPage + " / " + totalPages + " 页")),
                    event -> {
                        event.setCancelled(true);
                        openDiplomacySubmenu(player, nation, nextPage);
                    }, false
                ));
            }
        }

        // 返回主菜单按钮
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> openMainMenu(player));
            }, false
        ));

        // 底部边框
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        gui.open(player);
    }

    /**
     * 外交关系详情页（从外交列表点击进入）
     */
    private void openDiplomacyDetailSubmenu(Player player, Nation nation, NationId otherNationId) {
        // 获取目标国家信息
        Nation otherNation = nationModule.nationById(otherNationId).orElse(null);
        String otherName = otherNation != null ? otherNation.name() : otherNationId.toString();

        // 获取关系信息
        DiplomacyRelation relation = diplomacyService != null
            ? diplomacyService.relationBetween(nation.id(), otherNationId)
            : DiplomacyRelation.NEUTRAL;
        WarRecord warRecord = diplomacyService != null
            ? diplomacyService.getWarRecord(nation.id(), otherNationId).orElse(null)
            : null;

        // 获取当前玩家的权限
        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);
        boolean isAdmin = self != null && "admin".equals(self.rank());

        // 关系颜色和图标
        Material relMat = switch (relation) {
            case ALLIED -> Material.DIAMOND;
            case HOSTILE -> Material.BLAZE_POWDER;
            case WAR -> Material.REDSTONE;
            case NEUTRAL -> Material.IRON_INGOT;
            case FRIENDLY -> Material.LAPIS_LAZULI;
            case CEASE_FIRE -> Material.EMERALD;
            case VASSAL -> Material.GOLD_INGOT;
        };

        String relColor = switch (relation) {
            case ALLIED -> "§b";
            case HOSTILE -> "§c";
            case WAR -> "§4";
            case NEUTRAL -> "§7";
            case FRIENDLY -> "§3";
            case CEASE_FIRE -> "§a";
            case VASSAL -> "§6";
        };

        // 构建详情 GUI
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§c§l⚔ " + otherName + " §7| 外交详情"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：目标国家信息
        gui.setItem(4, createGuiItem(
            relMat,
            Component.text(relColor + "§l" + otherName, NamedTextColor.WHITE),
            List.of(
                Component.text(""),
                Component.text("§7关系状态: " + relColor + relation.displayName()),
                Component.text("§7目标国家: §f" + otherName),
                otherNation != null ? Component.text("§7国家等级: §eLv." + (otherNation.experience() / 1000)) : Component.text(""),
                Component.text("§7NationId: §f" + otherNationId)
            ),
            event -> {}, false
        ));

        // 战争信息（如果有）
        if (warRecord != null) {
            gui.setItem(22, createGuiItem(
                Material.NETHERITE_SWORD,
                Component.text("§4§l⚔ 战争进行中", NamedTextColor.DARK_RED),
                List.of(
                    Component.text(""),
                    Component.text("§7宣战国: §c" + nationModule.nationById(warRecord.declarer()).map(Nation::name).orElse("未知")),
                    Component.text("§7被宣战国: §c" + nationModule.nationById(warRecord.target()).map(Nation::name).orElse("未知")),
                    Component.text("§7持续时间: §e" + warRecord.durationFormatted()),
                    Component.text(""),
                    Component.text("§c战争正在进行中！")
                ),
                event -> {}, false
            ));
        }

        // 操作按钮区域
        // 第一行操作（需要管理员权限）
        if (isAdmin) {
            // 结盟按钮（仅中立/停火时可点击）
            boolean canAlly = relation == DiplomacyRelation.NEUTRAL || relation == DiplomacyRelation.CEASE_FIRE;
            gui.setItem(19, createGuiItem(
                Material.DIAMOND,
                Component.text("§b⚝ 申请结盟", canAlly ? NamedTextColor.AQUA : NamedTextColor.GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7向 §f" + otherName + " §7申请结盟"),
                    Component.text(""),
                    canAlly
                        ? Component.text("§a▸ 点击申请结盟")
                        : Component.text("§7当前状态无法申请结盟")
                ),
                event -> {
                    event.setCancelled(true);
                    if (diplomacyService != null && canAlly) {
                        diplomacyService.setRelation(nation.id(), otherNationId, DiplomacyRelation.ALLIED);
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                        player.sendMessage(Component.text("§a已与 " + otherName + " 结盟！", NamedTextColor.GREEN));
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    } else {
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    }
                }, canAlly
            ));

            // 宣战按钮
            boolean canDeclareWar = relation != DiplomacyRelation.WAR;
            gui.setItem(21, createGuiItem(
                Material.REDSTONE,
                Component.text("§4⚔ 宣战", canDeclareWar ? NamedTextColor.DARK_RED : NamedTextColor.GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7向 §f" + otherName + " §7宣战"),
                    Component.text(""),
                    canDeclareWar
                        ? Component.text("§c▸ 点击宣战")
                        : Component.text("§7已在战争状态")
                ),
                event -> {
                    event.setCancelled(true);
                    if (diplomacyService != null && canDeclareWar) {
                        diplomacyService.declareWar(nation.id(), otherNationId);
                        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§c已向 " + otherName + " 宣战！", NamedTextColor.RED));
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    } else {
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    }
                }, canDeclareWar
            ));

            // 停火按钮（仅战争时可点击）
            boolean canCeaseFire = relation == DiplomacyRelation.WAR;
            gui.setItem(23, createGuiItem(
                Material.EMERALD,
                Component.text("§a⚔ 停火协议", canCeaseFire ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7与 §f" + otherName + " §7签署停火"),
                    Component.text(""),
                    canCeaseFire
                        ? Component.text("§a▸ 点击签署停火")
                        : Component.text("§7仅在战争状态可停火")
                ),
                event -> {
                    event.setCancelled(true);
                    if (diplomacyService != null && canCeaseFire) {
                        diplomacyService.setRelation(nation.id(), otherNationId, DiplomacyRelation.CEASE_FIRE);
                        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.5f);
                        player.sendMessage(Component.text("§a已与 " + otherName + " 签署停火！", NamedTextColor.GREEN));
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    } else {
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    }
                }, canCeaseFire
            ));

            // 解除关系按钮
            boolean canBreak = relation != DiplomacyRelation.NEUTRAL && relation != DiplomacyRelation.WAR;
            gui.setItem(25, createGuiItem(
                Material.BARRIER,
                Component.text("§c✗ 解除关系", canBreak ? NamedTextColor.RED : NamedTextColor.GRAY),
                List.of(
                    Component.text(""),
                    Component.text("§7解除与 §f" + otherName + " §7的关系"),
                    Component.text(""),
                    canBreak
                        ? Component.text("§c▸ 点击解除关系")
                        : Component.text("§7中立状态无需解除")
                ),
                event -> {
                    event.setCancelled(true);
                    if (diplomacyService != null && canBreak) {
                        diplomacyService.setRelation(nation.id(), otherNationId, DiplomacyRelation.NEUTRAL);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        player.sendMessage(Component.text("§a已解除与 " + otherName + " 的外交关系！", NamedTextColor.GREEN));
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    } else {
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                            () -> openDiplomacyDetailSubmenu(player, nation, otherNationId), 3L);
                    }
                }, canBreak
            ));
        } else {
            // 非管理员提示
            gui.setItem(20, createGuiItem(
                Material.BARRIER,
                Component.text("§c⚠ 需要管理员权限", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7外交操作需要管理员权限"),
                    Component.text("§7请联系国家管理员")
                ),
                event -> {}, false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回外交列表", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回外交关系列表")),
            event -> {
                event.setCancelled(true);
                openDiplomacySubmenu(player, nation, 1);
            }, false
        ));

        gui.open(player);
    }

    /**
     * 科技详情页（从科技列表点击进入）
     */
    private void openTechnologyDetailSubmenu(Player player, Nation nation, String techId) {
        // 规范化科技ID
        String normalizedId = techId.toLowerCase(Locale.ROOT).trim();

        // 检查是否已解锁
        boolean isUnlocked = technologyService != null
            && technologyService.hasTechnology(nation.id(), normalizedId);

        // 获取科技成本
        Optional<TechnologyCost> costOpt = technologyService != null
            ? technologyService.costOf(normalizedId)
            : Optional.empty();

        // 获取当前玩家权限
        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);
        boolean isAdmin = self != null && "admin".equals(self.rank());

        // 科技显示名称映射
        String displayName = switch (normalizedId) {
            case "logistics" -> "§b后勤学";
            case "steel_working" -> "§e钢铁冶炼";
            case "radio_command" -> "§3无线电指挥";
            case "mechanized_warfare" -> "§4机械化战争";
            case "industrial_planning" -> "§6工业规划";
            default -> "§7" + techId;
        };

        String description = switch (normalizedId) {
            case "logistics" -> "§7提升军队移动速度和补给效率";
            case "steel_working" -> "§7解锁高级武器和防具制作";
            case "radio_command" -> "§7提升远程指挥和通信能力";
            case "mechanized_warfare" -> "§7解锁坦克和装甲单位";
            case "industrial_planning" -> "§7提升资源产出和工厂效率";
            default -> "§7" + techId + " 科技效果";
        };

        // 构建详情 GUI
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l⚙ " + displayName + " §7| 科技详情"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：科技图标和信息
        Material topMat = isUnlocked ? Material.BEACON : Material.BROWN_STAINED_GLASS_PANE;
        gui.setItem(4, createGuiItem(
            topMat,
            Component.text((isUnlocked ? "§a§l✓ " : "§c§l✗ ") + displayName, NamedTextColor.WHITE),
            List.of(
                Component.text(""),
                Component.text("§7科技ID: §f" + normalizedId),
                Component.text(""),
                Component.text("§7状态: " + (isUnlocked ? "§a已解锁" : "§c未解锁")),
                Component.text(description),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 成本区域
        if (costOpt.isPresent()) {
            var cost = costOpt.get();
            gui.setItem(20, createGuiItem(
                Material.GOLD_INGOT,
                Component.text("§e💰 国库成本", NamedTextColor.YELLOW),
                List.of(
                    Component.text(""),
                    Component.text("§7国库消耗: §6" + cost.treasury() + " 💰"),
                    Component.text("")
                ),
                event -> {}, false
            ));

            // 资源成本
            int resSlot = 22;
            for (var entry : cost.resources().entrySet()) {
                String resName = entry.getKey();
                Long amount = entry.getValue();
                Material resMat = materialForResource(resName);
                gui.setItem(resSlot, createGuiItem(
                    resMat,
                    Component.text("§7" + resName + ": §e" + amount),
                    List.of(
                        Component.text(""),
                        Component.text("§7需要: §e" + amount + " §7单位")
                    ),
                    event -> {}, false
                ));
                resSlot++;
                if (resSlot == 25) break; // 最多3种资源
            }
        } else {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c无法获取科技成本", NamedTextColor.RED),
                List.of(Component.text(""), Component.text("§7科技配置可能缺失")),
                event -> {}, false
            ));
        }

        // 操作按钮
        if (isAdmin) {
            if (!isUnlocked) {
                // 解锁按钮
                gui.setItem(38, createGuiItem(
                    Material.LIME_STAINED_GLASS,
                    Component.text("§a§l⬆ 解锁科技", NamedTextColor.GREEN),
                    List.of(
                        Component.text(""),
                        Component.text("§7为 §f" + nation.name() + " §7解锁此科技"),
                        Component.text(""),
                        costOpt.isPresent()
                            ? Component.text("§7消耗: §6" + costOpt.get().treasury() + " 💰")
                            : Component.text(""),
                        Component.text(""),
                        Component.text("§a▸ 点击解锁")
                    ),
                    event -> {
                        event.setCancelled(true);
                        if (technologyService != null) {
                            boolean success = technologyService.unlock(nation.id(), normalizedId);
                            if (success) {
                                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                                player.sendMessage(Component.text("§a科技解锁成功！", NamedTextColor.GREEN));
                                plugin.getServer().getScheduler().runTaskLater(plugin,
                                    () -> openTechnologyDetailSubmenu(player, nation, normalizedId), 3L);
                            } else {
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                player.sendMessage(Component.text("§c科技解锁失败！", NamedTextColor.RED));
                            }
                        }
                    }, true
                ));
            } else {
                // 移除按钮（已解锁时可移除）
                gui.setItem(38, createGuiItem(
                    Material.RED_STAINED_GLASS,
                    Component.text("§c§l✗ 移除科技", NamedTextColor.RED),
                    List.of(
                        Component.text(""),
                        Component.text("§7为 §f" + nation.name() + " §7移除此科技"),
                        Component.text(""),
                        Component.text("§7⚠ 警告：移除后需重新消耗资源解锁"),
                        Component.text(""),
                        Component.text("§c▸ 点击移除")
                    ),
                    event -> {
                        event.setCancelled(true);
                        if (technologyService != null) {
                            technologyService.revoke(nation.id(), normalizedId);
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                            player.sendMessage(Component.text("§a科技已移除！", NamedTextColor.GREEN));
                            plugin.getServer().getScheduler().runTaskLater(plugin,
                                () -> openTechnologyDetailSubmenu(player, nation, normalizedId), 3L);
                        }
                    }, true
                ));
            }
        } else {
            // 非管理员提示
            gui.setItem(40, createGuiItem(
                Material.BARRIER,
                Component.text("§c⚠ 需要管理员权限", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7科技操作需要管理员权限"),
                    Component.text("§7请联系国家管理员")
                ),
                event -> {}, false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回科技树", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回科技树列表")),
            event -> {
                event.setCancelled(true);
                openTechnologySubmenu(player, nation);
            }, false
        ));

        gui.open(player);
    }

    /**
     * 根据资源名称返回对应的材质
     */
    private Material materialForResource(String resource) {
        return switch (resource.toLowerCase(Locale.ROOT)) {
            case "food" -> Material.BREAD;
            case "timber" -> Material.OAK_LOG;
            case "ore" -> Material.IRON_INGOT;
            case "rare_metal" -> Material.NETHERITE_INGOT;
            case "oil" -> Material.BLAZE_ROD;
            default -> Material.COBBLESTONE;
        };
    }

    /**
     * 科技树子菜单
     */
    public void openTechnologySubmenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l⚙ " + nation.name() + " §7| 科技树"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部标题
        gui.setItem(4, createGuiItem(
            Material.ENCHANTING_TABLE,
            Component.text("§e§l科技树", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text(""),
                Component.text("§a已解锁: §e" + (technologyService != null ? technologyService.unlockedTechnologies(nation.id()).size() : 0)),
                Component.text("§7可用科技: §e" + (technologyService != null ? technologyService.availableTechnologies().size() : 0))
            ),
            event -> {}, false
        ));

        // 获取科技数据
        Collection<String> unlockedTechs = technologyService != null
            ? technologyService.unlockedTechnologies(nation.id())
            : List.of();
        Collection<String> availableTechs = technologyService != null
            ? technologyService.availableTechnologies()
            : List.of();

        // 填充科技槽位 (10-43, 跳过边框)
        int slot = 10;
        int count = 0;
        for (String techId : availableTechs) {
            if (count >= 14) break;
            if (slot == 17 || slot == 26 || slot == 35) slot++;

            boolean unlocked = unlockedTechs.contains(techId);

            Material mat = unlocked ? Material.BEACON : Material.BROWN_STAINED_GLASS_PANE;
            Component name = Component.text(unlocked ? "§a§l" + techId : "§7" + techId);

            gui.setItem(slot, createGuiItem(mat, name,
                List.of(
                    Component.text(""),
                    Component.text(unlocked ? "§a§l✓ 已解锁" : "§c§l✗ 未解锁"),
                    Component.text(""),
                    Component.text("§a▸ 点击查看详情")
                ),
                event -> {
                    event.setCancelled(true);
                    openTechnologyDetailSubmenu(player, nation, techId);
                }, unlocked
            ));
            slot++;
            count++;
        }

        if (count == 0) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无可用科技", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7科技模块未启用或无可用科技")
                ),
                event -> {}, false
            ));
        }

        // 返回主菜单按钮
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> openMainMenu(player));
            }, false
        ));

        // 底部边框
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        gui.open(player);
    }

    /**
     * 国策子菜单
     */
    public void openPolicySubmenu(Player player, Nation nation) {
        if (policyNationMenu != null) {
            policyNationMenu.openPolicyMenu(player, nation);
        } else if (policyService != null) {
            // Fallback: 显示简单的国策列表
            openBasicPolicySubmenu(player, nation);
        } else {
            player.sendMessage(Component.text("⚠ 国策模块未启用", NamedTextColor.RED));
        }
    }

    /**
     * 基础国策子菜单（当 PolicyNationMenu 不可用时的 fallback）
     */
    private void openBasicPolicySubmenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l📜 " + nation.name() + " §7| 国策"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部
        gui.setItem(4, createGuiItem(
            Material.BOOK,
            Component.text("§e§l国策", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text(""),
                Component.text("§7查看和管理国家国策"),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 显示所有国策
        Collection<PolicyDefinition> allPolicies = policyService.policyDefinitions();
        Optional<String> activeKey = policyService.activePolicy(nation.id());
        Collection<String> unlockedPolicies = policyService.unlockedPolicies(nation.id());

        int slot = 10;
        for (PolicyDefinition def : allPolicies) {
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot > 43) break;

            boolean isActive = activeKey.map(k -> k.equals(def.key())).orElse(false);
            boolean isUnlocked = unlockedPolicies.contains(def.key());

            Material mat = isActive ? Material.BOOK : (isUnlocked ? Material.PAPER : Material.BROWN_STAINED_GLASS_PANE);
            Component name = Component.text((isActive ? "§a" : (isUnlocked ? "§e" : "§7")) + def.displayName());

            gui.setItem(slot, createGuiItem(mat, name,
                List.of(
                    Component.text(""),
                    Component.text("§7分类: §f" + def.category().displayName()),
                    Component.text(isActive ? "§a■ 激活中" : (isUnlocked ? "§e□ 可激活" : "§7□ 需解锁")),
                    Component.text("§7消耗: §6" + def.treasuryCost().toPlainString() + " 星尘"),
                    Component.text(""),
                    Component.text("§a▸ 点击查看详情")
                ),
                event -> {
                    event.setCancelled(true);
                    // 委托给 PolicyNationMenu 详情页
                    if (policyNationMenu != null) {
                        policyNationMenu.openPolicyDetailMenu(player, nation, def.key());
                    }
                }, isActive
            ));
            slot++;
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> openMainMenu(player));
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 领土子菜单（带分页）
     */
    public void openTerritorySubmenu(Player player, Nation nation, int page) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l🗺 " + nation.name() + " §7| 领土管理 §7(第" + page + "页)"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部标题
        gui.setItem(4, createGuiItem(
            Material.FILLED_MAP,
            Component.text("§e§l领土管理", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text("§7已占领: §a" + nationModule.claimCount(nation.id()) + " §7区块"),
                Component.text(""),
                Component.text("§e▸ 使用领地工具扩建领土")
            ),
            event -> {}, false
        ));

        // 获取领土数据
        Collection<TerritoryClaim> claims = nationModule.claimsOf(nation.id());
        int totalClaims = claims.size();

        // 分页：每页最多14个
        int itemsPerPage = 14;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalClaims / itemsPerPage));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        List<TerritoryClaim> pageClaims = claims.stream()
            .skip((long) (page - 1) * itemsPerPage)
            .limit(itemsPerPage)
            .toList();

        int slot = 10;
        int count = 0;
        for (TerritoryClaim claim : pageClaims) {
            if (count >= 14) break;
            if (slot == 17 || slot == 26 || slot == 35) slot++;

            gui.setItem(slot, createGuiItem(
                Material.MAP,
                Component.text("§a§l" + claim.coordinate().world() + " §7(" + claim.coordinate().x() + ", " + claim.coordinate().z() + ")", NamedTextColor.GREEN),
                List.of(
                    Component.text(""),
                    Component.text("§7世界: §f" + claim.coordinate().world()),
                    Component.text("§7坐标: §f" + claim.coordinate().x() + ", " + claim.coordinate().z()),
                    Component.text("")
                ),
                event -> {}, false
            ));
            slot++;
            count++;
        }

        if (totalClaims == 0) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无领土", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7使用领地工具扩建领土")
                ),
                event -> {}, false
            ));
        }

        // 分页控制
        if (totalPages > 1) {
            if (page > 1) {
                final int prevPage = page - 1;
                gui.setItem(45, createGuiItem(
                    Material.ARROW,
                    Component.text("§e◀ 上一页", NamedTextColor.YELLOW),
                    List.of(Component.text("§7第 " + prevPage + " / " + totalPages + " 页")),
                    event -> {
                        event.setCancelled(true);
                        openTerritorySubmenu(player, nation, prevPage);
                    }, false
                ));
            }
            if (page < totalPages) {
                final int nextPage = page + 1;
                gui.setItem(53, createGuiItem(
                    Material.ARROW,
                    Component.text("§e▶ 下一页", NamedTextColor.YELLOW),
                    List.of(Component.text("§7第 " + nextPage + " / " + totalPages + " 页")),
                    event -> {
                        event.setCancelled(true);
                        openTerritorySubmenu(player, nation, nextPage);
                    }, false
                ));
            }
        }

        // 返回主菜单按钮
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> openMainMenu(player));
            }, false
        ));

        // 底部边框
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        gui.open(player);
    }

    /**
     * 国库管理子菜单
     */
    public void openTreasurySubmenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l💰 " + nation.name() + " §7| 国库管理"))
            .rows(3)
            .disableAllInteractions()
            .create();

        // 国库余额显示
        String balanceStr = treasuryService != null ? treasuryService.balance(nation.id()).toPlainString() : "N/A";
        gui.setItem(4, createGuiItem(
            Material.GOLD_INGOT,
            Component.text("§6§l国库余额", NamedTextColor.GOLD, TextDecoration.BOLD),
            List.of(
                Component.empty(),
                Component.text("当前余额: §6" + balanceStr + " 星尘", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("§7每日收入将自动添加到国库", NamedTextColor.GRAY),
                Component.text("§7税收将从国库扣除", NamedTextColor.GRAY),
                Component.empty()
            ),
            event -> event.setCancelled(true),
            true
        ));

        // 玩家存款到国库（所有成员可用）
        gui.setItem(11, createGuiItem(
            Material.GOLD_BLOCK,
            Component.text("§a§l存入星尘", NamedTextColor.GREEN, TextDecoration.BOLD),
            List.of(
                Component.empty(),
                Component.text("§7从你的个人账户存款到国库", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("§e▸ 点击输入存款金额", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                Nation captured = nation;
                p.closeInventory();
                p.sendMessage(Component.text("§e请在铁砧界面输入存款金额（数字）", NamedTextColor.YELLOW));
                anvilProvider.openAnvilInput(p, "存款", "0", input -> {
                    try {
                        BigDecimal amount = new BigDecimal(input.trim());
                        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                            p.sendMessage(Component.text("§c金额必须大于 0", NamedTextColor.RED));
                            return;
                        }
                        // 从玩家账户扣除并添加到国库
                        UUID playerId = p.getUniqueId();
                        if (!economyService.withdraw(playerId, amount)) {
                            p.sendMessage(Component.text("§c你的余额不足！", NamedTextColor.RED));
                            return;
                        }
                        if (treasuryService != null) {
                            treasuryService.deposit(captured.id(), amount);
                            p.sendMessage(Component.text("§a成功存入 §6" + amount.toPlainString() + " 星尘 §a到国库", NamedTextColor.GREEN));
                        } else {
                            p.sendMessage(Component.text("§c国库服务暂不可用", NamedTextColor.RED));
                        }
                        // 重新打开国库菜单
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> openTreasurySubmenu(p, captured), 1L);
                    } catch (NumberFormatException e) {
                        p.sendMessage(Component.text("§c请输入有效的数字", NamedTextColor.RED));
                    }
                }, () -> { /* 取消 */ });
            }, false
        ));

        // 玩家从国库取款（仅管理员）
        gui.setItem(15, createGuiItem(
            Material.CHEST,
            Component.text("§c§l从国库取出", NamedTextColor.RED, TextDecoration.BOLD),
            List.of(
                Component.empty(),
                Component.text("§7从国库取款到你的账户", NamedTextColor.GRAY),
                Component.text("§c⚠ 仅管理员可用", NamedTextColor.RED),
                Component.empty(),
                Component.text("§e▸ 点击输入取款金额", NamedTextColor.YELLOW)
            ),
            event -> {
                event.setCancelled(true);
                Player p = (Player) event.getWhoClicked();
                Nation captured = nation;
                // 权限检查
                if (!checkPermission(p, nation, "admin")) {
                    p.sendMessage(Component.text("§c⚠ 只有管理员才能从国库取款", NamedTextColor.RED));
                    return;
                }
                p.closeInventory();
                p.sendMessage(Component.text("§e请在铁砧界面输入取款金额（数字）", NamedTextColor.YELLOW));
                anvilProvider.openAnvilInput(p, "取款", "0", input -> {
                    try {
                        BigDecimal amount = new BigDecimal(input.trim());
                        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                            p.sendMessage(Component.text("§c金额必须大于 0", NamedTextColor.RED));
                            return;
                        }
                        // 从国库取款并给玩家
                        if (treasuryService != null && treasuryService.withdraw(captured.id(), amount)) {
                            economyService.deposit(p.getUniqueId(), amount);
                            p.sendMessage(Component.text("§a成功从国库取出 §6" + amount.toPlainString() + " 星尘", NamedTextColor.GREEN));
                        } else if (treasuryService == null) {
                            p.sendMessage(Component.text("§c国库服务暂不可用", NamedTextColor.RED));
                        } else {
                            p.sendMessage(Component.text("§c国库余额不足", NamedTextColor.RED));
                        }
                        // 重新打开国库菜单
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> openTreasurySubmenu(p, captured), 1L);
                    } catch (NumberFormatException e) {
                        p.sendMessage(Component.text("§c请输入有效的数字", NamedTextColor.RED));
                    }
                }, () -> { /* 取消 */ });
            }, false
        ));

        // 国库统计
        gui.setItem(13, createGuiItem(
            Material.BOOK,
            Component.text("§b§l📊 国库统计", NamedTextColor.AQUA, TextDecoration.BOLD),
            List.of(
                Component.empty(),
                Component.text("§7当前余额: §6" + balanceStr + " 星尘", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("§7每日收入详情请查看国家公告", NamedTextColor.GRAY),
                Component.empty()
            ),
            event -> event.setCancelled(true),
            false
        ));

        // 返回主菜单
        gui.setItem(22, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> openMainMenu(player));
            }, false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 国家设置子菜单
     */
    public void openSettingsSubmenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize("§6§l⚙ " + nation.name() + " §7| 国家设置"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部标题
        gui.setItem(4, createGuiItem(
            Material.COMPARATOR,
            Component.text("§e§l国家设置", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text("§7政体: §e" + nation.governmentType().name()),
                Component.text("§7经验: §e" + nation.experience()),
                Component.text("§7等级: §eLv." + (nation.experience() / 1000)),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 获取权限等级
        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);
        boolean isAdmin = self != null && "admin".equals(self.rank());

        // 改名按钮
        gui.setItem(20, createGuiItem(
            Material.NAME_TAG,
            Component.text("§b§l✎ 国家改名", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§7当前名称: §f" + nation.name()),
                Component.text(""),
                Component.text(isAdmin ? "§a▸ 点击修改国家名称" : "§c▸ 需要管理员权限")
            ),
            event -> {
                event.setCancelled(true);
                if (!isAdmin) {
                    player.sendMessage(Component.text("§c需要管理员权限！", NamedTextColor.RED));
                    return;
                }
                gui.close(player);
                // 改名使用铁砧输入
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    anvilProvider.openAnvilInput(
                        player,
                        "请输入新国家名称:",
                        nation.name(),
                        result -> {
                            if (result == null || result.trim().isEmpty()) {
                                player.sendMessage(Component.text("§c名称不能为空！", NamedTextColor.RED));
                                // 重新打开设置菜单
                                nationModule.nationById(nation.id()).ifPresent(n -> openSettingsSubmenu(player, n));
                                return;
                            }
                            nationModule.renameNation(nation.id(), result.trim());
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                            player.sendMessage(Component.text("§a国家名称已修改为: §f" + result.trim(), NamedTextColor.GREEN));
                            // 重新打开设置菜单，显示改名后的名称
                            nationModule.nationById(nation.id()).ifPresent(n -> openSettingsSubmenu(player, n));
                        },
                        () -> {
                            nationModule.nationById(nation.id()).ifPresent(n -> openSettingsSubmenu(player, n));
                        }
                    );
                }, 2L);
            }, false
        ));

        // 转让所有权按钮
        gui.setItem(22, createGuiItem(
            Material.PAPER,
            Component.text("§6§l👑 转让所有权", NamedTextColor.GOLD),
            List.of(
                Component.text(""),
                Component.text("§7当前领袖: §f" + (self != null ? self.playerName() : "未知")),
                Component.text(""),
                Component.text(isAdmin ? "§a▸ 点击转让国家所有权" : "§c▸ 需要管理员权限")
            ),
            event -> {
                event.setCancelled(true);
                if (!isAdmin) {
                    player.sendMessage(Component.text("§c需要管理员权限！", NamedTextColor.RED));
                    return;
                }
                gui.close(player);
                // 转让所有权使用铁砧输入，成功后重新打开设置菜单
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    anvilProvider.openAnvilInput(
                        player,
                        "§6§l转让国家所有权",
                        null,
                        input -> {
                            if (input == null || input.trim().isEmpty()) {
                                player.sendMessage(Component.text("§c玩家名称不能为空！", NamedTextColor.RED));
                                nationModule.nationById(nation.id()).ifPresent(n -> openSettingsSubmenu(player, n));
                                return;
                            }
                            player.performCommand("nation transfer " + input.trim());
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                            player.sendMessage(Component.text("§a所有权转让请求已发送！", NamedTextColor.GREEN));
                            nationModule.nationById(nation.id()).ifPresent(n -> openSettingsSubmenu(player, n));
                        },
                        () -> {
                            player.sendMessage(Component.text("已取消转让", NamedTextColor.YELLOW));
                            nationModule.nationById(nation.id()).ifPresent(n -> openSettingsSubmenu(player, n));
                        }
                    );
                }, 2L);
            }, false
        ));

        // 政体信息按钮（改为政体变更入口）
        gui.setItem(24, createGuiItem(
            Material.BOOK,
            Component.text("§d§l📜 政体变更", NamedTextColor.DARK_PURPLE),
            List.of(
                Component.text(""),
                Component.text("§7当前政体: §f" + nation.governmentType().displayName()),
                Component.text(""),
                Component.text(isAdmin ? "§a▸ 点击变更政体" : "§c▸ 需要管理员权限")
            ),
            event -> {
                event.setCancelled(true);
                if (advancedMenu != null) {
                    advancedMenu.openGovernmentChangeMenu(player, nation);
                }
            }, false
        ));

        // 税率调整按钮
        gui.setItem(29, createGuiItem(
            Material.GOLD_INGOT,
            Component.text("§6§l💰 税率调整", NamedTextColor.GOLD),
            List.of(
                Component.text(""),
                Component.text("§7当前税率: §e" + String.format("%.1f%%", nation.getTaxRate() * 100)),
                Component.text(""),
                Component.text(isAdmin ? "§a▸ 点击调整税率" : "§c▸ 需要管理员权限")
            ),
            event -> {
                event.setCancelled(true);
                if (advancedMenu != null) {
                    advancedMenu.openTaxRateMenu(player, nation);
                }
            }, false
        ));

        // 权限管理按钮
        gui.setItem(31, createGuiItem(
            Material.NAME_TAG,
            Component.text("§b§l🔑 权限管理", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§7管理成员权限等级"),
                Component.text("§7查看权限说明"),
                Component.text(""),
                Component.text(isAdmin ? "§a▸ 点击管理权限" : "§c▸ 需要管理员权限")
            ),
            event -> {
                event.setCancelled(true);
                if (advancedMenu != null) {
                    advancedMenu.openPermissionMenu(player, nation);
                }
            }, false
        ));

        // 领土购买按钮
        gui.setItem(33, createGuiItem(
            Material.FILLED_MAP,
            Component.text("§a§l🗺 领土购买", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§7当前领土: §a" + nationModule.claimCount(nation.id()) + " §7区块"),
                Component.text("§7最大领土: §e" + nationModule.maxClaimsOf(nation.id()) + " §7区块"),
                Component.text(""),
                Component.text("§a▸ 点击购买领土")
            ),
            event -> {
                event.setCancelled(true);
                if (advancedMenu != null) {
                    advancedMenu.openTerritoryPurchaseMenu(player, nation);
                }
            }, false
        ));

        // 国家解散按钮（危险操作，放底部）
        gui.setItem(39, createGuiItem(
            Material.TNT,
            Component.text("§c§l💥 解散国家", NamedTextColor.RED),
            List.of(
                Component.text(""),
                Component.text("§c⚠ 危险操作"),
                Component.text("§7解散后所有数据将被清除"),
                Component.text(""),
                Component.text(isAdmin ? "§c▸ 点击查看解散选项" : "§7▸ 需要管理员权限")
            ),
            event -> {
                event.setCancelled(true);
                if (advancedMenu != null) {
                    advancedMenu.openDisbandConfirmMenu(player, nation);
                }
            }, false
        ));

        // 返回主菜单按钮
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> openMainMenu(player));
            }, false
        ));

        // 底部边框
        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════
    //  公开子菜单路由方法（供 NationManagementMenuListener 等外部调用）
    // 注意：子菜单方法已在各自位置声明为 public
    // ══════════════════════════════════════════════════════════════

    /**
     * 关闭箱子菜单
     */
    private void closeInventory(Player player) {
        player.closeInventory();
    }
}
