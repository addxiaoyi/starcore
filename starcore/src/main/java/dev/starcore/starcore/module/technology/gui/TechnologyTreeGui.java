package dev.starcore.starcore.module.technology.gui;

import java.util.concurrent.ConcurrentHashMap;
import dev.starcore.starcore.StarCorePlugin;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.technology.ResearchProgress;
import dev.starcore.starcore.module.technology.TechnologyCost;
import dev.starcore.starcore.module.technology.TechnologyModule;
import dev.starcore.starcore.module.technology.TechnologyValidator;
import dev.starcore.starcore.module.technology.model.TechnologyDefinition;
import dev.starcore.starcore.module.technology.model.TechnologyEffect;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.util.ColorCodes;
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

import java.util.*;
import java.util.function.Consumer;

/**
 * 科技树 GUI 主菜单
 * 提供完整的科技树浏览、研究和管理功能
 */
public class TechnologyTreeGui {

    private final TechnologyModule technologyModule;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final org.bukkit.plugin.Plugin plugin;

    private final Map<String, String> techDisplayNames = new ConcurrentHashMap<>();
    private final Map<String, String> techDescriptions = new ConcurrentHashMap<>();
    private final Map<String, Material> techMaterials = new ConcurrentHashMap<>();

    public TechnologyTreeGui(TechnologyModule technologyModule,
                            NationService nationService,
                            TreasuryService treasuryService,
                            org.bukkit.plugin.Plugin plugin) {
        this.technologyModule = technologyModule;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.plugin = plugin;

        initializeTechData();
    }

    private void initializeTechData() {
        // 石器时代
        techDisplayNames.put("stone_tools", ColorCodes.YELLOW + "石器工具");
        techDescriptions.put("stone_tools", ColorCodes.GRAY + "掌握基础石器制作技术，提高资源采集效率");
        techMaterials.put("stone_tools", Material.STONE_PICKAXE);

        techDisplayNames.put("agriculture", ColorCodes.YELLOW + "农业技术");
        techDescriptions.put("agriculture", ColorCodes.GRAY + "学会种植作物，开启定居生活");
        techMaterials.put("agriculture", Material.WHEAT);

        techDisplayNames.put("fire_mastery", ColorCodes.YELLOW + "火焰掌控");
        techDescriptions.put("fire_mastery", ColorCodes.GRAY + "掌握用火技术，可以烹饪食物和照明");
        techMaterials.put("fire_mastery", Material.FIRE_CHARGE);

        techDisplayNames.put("tribal_organization", ColorCodes.YELLOW + "部落组织");
        techDescriptions.put("tribal_organization", ColorCodes.GRAY + "建立部落社会结构，增加人口上限");
        techMaterials.put("tribal_organization", Material.VILLAGER_SPAWN_EGG);

        techDisplayNames.put("primitive_warfare", ColorCodes.YELLOW + "原始战术");
        techDescriptions.put("primitive_warfare", ColorCodes.GRAY + "学习基础战斗技巧，提升近战伤害");
        techMaterials.put("primitive_warfare", Material.STONE_SWORD);

        // 铁器时代
        techDisplayNames.put("copper_smelting", ColorCodes.AQUA + "铜矿冶炼");
        techDescriptions.put("copper_smelting", ColorCodes.GRAY + "学会冶炼铜矿，开启金属时代");
        techMaterials.put("copper_smelting", Material.COPPER_INGOT);

        techDisplayNames.put("bronze_weapons", ColorCodes.AQUA + "青铜武器");
        techDescriptions.put("bronze_weapons", ColorCodes.GRAY + "制造青铜武器，大幅提升军事实力");
        techMaterials.put("bronze_weapons", Material.BRICK);

        techDisplayNames.put("iron_working", ColorCodes.AQUA + "铁器锻造");
        techDescriptions.put("iron_working", ColorCodes.GRAY + "掌握铁器制作，革命性的技术进步");
        techMaterials.put("iron_working", Material.IRON_INGOT);

        techDisplayNames.put("the_wheel", ColorCodes.AQUA + "轮子");
        techDescriptions.put("the_wheel", ColorCodes.GRAY + "发明轮子，大幅提升运输效率");
        techMaterials.put("the_wheel", Material.MINECART);

        techDisplayNames.put("writing_system", ColorCodes.AQUA + "文字系统");
        techDescriptions.put("writing_system", ColorCodes.GRAY + "发明文字，可以记录和传播知识");
        techMaterials.put("writing_system", Material.BOOK);

        techDisplayNames.put("military_formation", ColorCodes.AQUA + "军事编队");
        techDescriptions.put("military_formation", ColorCodes.GRAY + "组织化的军事战术，提升团队作战能力");
        techMaterials.put("military_formation", Material.SHIELD);

        techDisplayNames.put("fortification", ColorCodes.AQUA + "防御工事");
        techDescriptions.put("fortification", ColorCodes.GRAY + "建造坚固的防御建筑");
        techMaterials.put("fortification", Material.COBBLESTONE_WALL);

        techDisplayNames.put("trade_routes", ColorCodes.AQUA + "贸易路线");
        techDescriptions.put("trade_routes", ColorCodes.GRAY + "建立贸易网络，促进经济发展");
        techMaterials.put("trade_routes", Material.EMERALD);

        // 工业时代
        techDisplayNames.put("steam_power", ColorCodes.GOLD + "蒸汽动力");
        techDescriptions.put("steam_power", ColorCodes.GRAY + "工业革命的核心技术，开启机械化时代");
        techMaterials.put("steam_power", Material.PISTON);

        techDisplayNames.put("steel_production", ColorCodes.GOLD + "钢铁生产");
        techDescriptions.put("steel_production", ColorCodes.GRAY + "炼制高强度钢铁，现代工业的基础");
        techMaterials.put("steel_production", Material.IRON_BLOCK);

        techDisplayNames.put("gunpowder", ColorCodes.GOLD + "火药");
        techDescriptions.put("gunpowder", ColorCodes.GRAY + "革命性的军事技术，改变战争形态");
        techMaterials.put("gunpowder", Material.GUNPOWDER);

        techDisplayNames.put("advanced_archery", ColorCodes.GOLD + "高级弓术");
        techDescriptions.put("advanced_archery", ColorCodes.GRAY + "精通弓箭技术，与火药路线互斥");
        techMaterials.put("advanced_archery", Material.BOW);

        techDisplayNames.put("factory_system", ColorCodes.GOLD + "工厂制度");
        techDescriptions.put("factory_system", ColorCodes.GRAY + "建立工厂生产体系，大规模生产商品");
        techMaterials.put("factory_system", Material.FURNACE);

        techDisplayNames.put("railways", ColorCodes.GOLD + "铁路系统");
        techDescriptions.put("railways", ColorCodes.GRAY + "建造铁路网络，快速运输资源和人员");
        techMaterials.put("railways", Material.RAIL);

        techDisplayNames.put("scientific_method", ColorCodes.GOLD + "科学方法");
        techDescriptions.put("scientific_method", ColorCodes.GRAY + "系统化的研究方法，加速科技发展");
        techMaterials.put("scientific_method", Material.BREWING_STAND);

        techDisplayNames.put("education_system", ColorCodes.GOLD + "教育体系");
        techDescriptions.put("education_system", ColorCodes.GRAY + "建立现代教育系统，培养人才");
        techMaterials.put("education_system", Material.EXPERIENCE_BOTTLE);

        // 信息时代
        techDisplayNames.put("electricity", ColorCodes.LIGHT_PURPLE + "电力技术");
        techDescriptions.put("electricity", ColorCodes.GRAY + "利用电能，现代文明的基石");
        techMaterials.put("electricity", Material.LIGHTNING_ROD);

        techDisplayNames.put("computers", ColorCodes.LIGHT_PURPLE + "计算机技术");
        techDescriptions.put("computers", ColorCodes.GRAY + "信息处理和自动化控制");
        techMaterials.put("computers", Material.NOTE_BLOCK);

        techDisplayNames.put("telecommunications", ColorCodes.LIGHT_PURPLE + "远程通信");
        techDescriptions.put("telecommunications", ColorCodes.GRAY + "跨越距离的即时通信技术");
        techMaterials.put("telecommunications", Material.ENDER_CHEST);

        techDisplayNames.put("nuclear_power", ColorCodes.LIGHT_PURPLE + "核能技术");
        techDescriptions.put("nuclear_power", ColorCodes.GRAY + "原子能的和平利用，清洁高效的能源");
        techMaterials.put("nuclear_power", Material.BEACON);

        techDisplayNames.put("robotics", ColorCodes.LIGHT_PURPLE + "机器人技术");
        techDescriptions.put("robotics", ColorCodes.GRAY + "自动化生产和智能机械");
        techMaterials.put("robotics", Material.HOPPER);
    }

    /**
     * 打开科技树主菜单
     */
    public void openMainMenu(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage(ColorCodes.error("你需要先加入一个国家才能使用科技功能"));
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                ColorCodes.GOLD + ColorCodes.BOLD + "⚙ " + nation.name() + " " + ColorCodes.GRAY + "| 科技中心"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：科技概览
        int unlocked = technologyModule.unlockedTechnologies(nationId).size();
        int total = technologyModule.availableTechnologies().size();
        int researching = technologyModule.getNationResearch(nationId).size();

        gui.setItem(4, createGuiItem(
            Material.ENCHANTING_TABLE,
            Component.text(ColorCodes.YELLOW + ColorCodes.BOLD + "科技中心", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text(ColorCodes.GRAY + "国家: " + ColorCodes.WHITE + nation.name()),
                Component.text(""),
                Component.text(ColorCodes.GREEN + "已解锁: " + ColorCodes.YELLOW + unlocked + " " + ColorCodes.GRAY + "/ " + ColorCodes.WHITE + total),
                Component.text(ColorCodes.AQUA + "研究中: " + ColorCodes.YELLOW + researching),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 科技树浏览
        gui.setItem(20, createGuiItem(
            Material.NETHER_STAR,
            Component.text(ColorCodes.YELLOW + ColorCodes.BOLD + "科技树", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text(ColorCodes.GRAY + "浏览所有可用的科技"),
                Component.text(""),
                Component.text(ColorCodes.GREEN + "点击打开")
            ),
            event -> {
                event.setCancelled(true);
                openTechTreeView(player, nation, 0);
            }, false
        ));

        // 已研究科技
        gui.setItem(22, createGuiItem(
            Material.BOOK,
            Component.text(ColorCodes.GREEN + ColorCodes.BOLD + "已研究科技", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text(ColorCodes.GRAY + "查看已解锁的科技列表"),
                Component.text(""),
                Component.text(ColorCodes.GRAY + "已解锁: " + ColorCodes.GREEN + unlocked),
                Component.text("")
            ),
            event -> {
                event.setCancelled(true);
                openUnlockedTechView(player, nation);
            }, false
        ));

        // 研究进度
        gui.setItem(24, createGuiItem(
            Material.PAPER,
            Component.text(ColorCodes.AQUA + ColorCodes.BOLD + "研究进度", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text(ColorCodes.GRAY + "查看正在进行的研究"),
                Component.text(""),
                Component.text(ColorCodes.GRAY + "进行中: " + ColorCodes.YELLOW + researching),
                Component.text("")
            ),
            event -> {
                event.setCancelled(true);
                openResearchProgressView(player, nation);
            }, false
        ));

        // 底部说明
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text(ColorCodes.RED + ColorCodes.BOLD + "关闭", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
            },
            false
        ));

        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开科技树视图（按纪元分类）
     */
    public void openTechTreeView(Player player, Nation nation, int eraPage) {
        NationId nationId = nation.id();
        Collection<String> unlocked = technologyModule.unlockedTechnologies(nationId);
        Collection<String> researching = technologyModule.getNationResearch(nationId).keySet();

        String[] eras = {"stone_age", "iron_age", "industrial_age", "information_age"};
        String[] eraNames = {ColorCodes.GRAY + "◆ 石器时代", ColorCodes.AQUA + "◆ 铁器时代", ColorCodes.GOLD + "◆ 工业时代", ColorCodes.LIGHT_PURPLE + "◆ 信息时代"};
        String[] eraColors = {ColorCodes.GRAY, ColorCodes.AQUA, ColorCodes.GOLD, ColorCodes.LIGHT_PURPLE};

        int currentEra = Math.min(eraPage, eras.length - 1);

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                ColorCodes.GOLD + ColorCodes.BOLD + "⚙ 科技树 " + ColorCodes.GRAY + "| " + eraNames[currentEra]))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 纪元标题
        gui.setItem(4, createGuiItem(
            Material.NETHER_STAR,
            Component.text(eraNames[currentEra]),
            List.of(
                Component.text(""),
                Component.text(ColorCodes.GRAY + "浏览该时代的所有科技"),
                Component.text(""),
                Component.text(ColorCodes.GREEN + "点击科技查看详情")
            ),
            event -> {}, false
        ));

        // 获取该时代的所有科技
        Map<String, TechnologyDefinition> allDefs = technologyModule.getAllDefinitions();
        List<String> eraTechs = new ArrayList<>();
        for (Map.Entry<String, TechnologyDefinition> entry : allDefs.entrySet()) {
            if (entry.getValue().era().equalsIgnoreCase(eras[currentEra])) {
                eraTechs.add(entry.getKey());
            }
        }
        Collections.sort(eraTechs);

        // 显示科技（最多14个）
        int slot = 10;
        int count = 0;
        for (String techId : eraTechs) {
            if (count >= 14) break;
            if (slot == 17 || slot == 26 || slot == 35) slot++;

            boolean isUnlocked = unlocked.contains(techId);
            boolean isResearching = researching.contains(techId);
            Material mat = getTechMaterial(techId);

            Component name = Component.text(isUnlocked ? "§a" + getTechDisplayName(techId) :
                isResearching ? "§b" + getTechDisplayName(techId) + " §7(研究中)" :
                "§7" + getTechDisplayName(techId));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(""));
            lore.add(Component.text(isUnlocked ? "§a✓ 已解锁" : isResearching ? "§b⏳ 研究中" : "§c✗ 未解锁"));
            lore.add(Component.text(""));

            // 效果预览
            TechnologyDefinition def = allDefs.get(techId);
            if (def != null && def.effects() != null && !def.effects().isEmpty()) {
                lore.add(Component.text("§7效果:"));
                for (var effect : def.effects()) {
                    if (effect.description() != null) {
                        lore.add(Component.text("  " + effect.description()));
                    }
                }
            }
            lore.add(Component.text(""));
            lore.add(Component.text("§a▸ 点击查看详情"));

            gui.setItem(slot, createGuiItem(mat, name, lore,
                event -> {
                    event.setCancelled(true);
                    openTechDetailMenu(player, nation, techId);
                }, isUnlocked || isResearching
            ));

            slot++;
            count++;
        }

        if (eraTechs.isEmpty()) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c该时代暂无科技", NamedTextColor.RED),
                List.of(Component.text(""), Component.text("§7请等待后续更新")),
                event -> {}, false
            ));
        }

        // 纪元切换
        if (currentEra > 0) {
            gui.setItem(48, createGuiItem(
                Material.ARROW,
                Component.text("§e◀ 上一时代", NamedTextColor.YELLOW),
                List.of(Component.text(""), Component.text("§7" + eraNames[currentEra - 1])),
                event -> {
                    event.setCancelled(true);
                    openTechTreeView(player, nation, currentEra - 1);
                }, false
            ));
        }

        if (currentEra < eras.length - 1) {
            gui.setItem(50, createGuiItem(
                Material.ARROW,
                Component.text("§e下一时代 ▶", NamedTextColor.YELLOW),
                List.of(Component.text(""), Component.text("§7" + eraNames[currentEra + 1])),
                event -> {
                    event.setCancelled(true);
                    openTechTreeView(player, nation, currentEra + 1);
                }, false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§c◀ 返回", NamedTextColor.RED),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu(player);
            },
            false
        ));

        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开科技详情菜单
     */
    public void openTechDetailMenu(Player player, Nation nation, String techId) {
        NationId nationId = nation.id();
        String normalizedId = techId.toLowerCase(Locale.ROOT);

        boolean isUnlocked = technologyModule.hasTechnology(nationId, normalizedId);
        boolean isResearching = technologyModule.isResearching(nationId, normalizedId);
        Optional<TechnologyCost> costOpt = technologyModule.costOf(normalizedId);

        // 获取验证结果
        TechnologyValidator.ValidationResult validation = technologyModule.validateResearch(nationId, normalizedId);

        // 科技定义
        Optional<TechnologyDefinition> defOpt = technologyModule.getDefinition(normalizedId);
        TechnologyDefinition def = defOpt.orElse(null);

        // 获取研究进度
        ResearchProgress progress = isResearching ? technologyModule.getResearchProgress(nationId, normalizedId) : null;

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l⚙ " + getTechDisplayName(techId) + " §7| 详情"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 科技图标和名称
        Material topMat = isUnlocked ? Material.BEACON : isResearching ? Material.LODESTONE : Material.BROWN_STAINED_GLASS_PANE;
        gui.setItem(4, createGuiItem(
            getTechMaterial(techId),
            Component.text((isUnlocked ? "§a§l✓ " : isResearching ? "§b§l⏳ " : "§c§l✗ ") + getTechDisplayName(techId)),
            List.of(
                Component.text(""),
                Component.text("§7科技ID: §f" + normalizedId),
                Component.text(""),
                Component.text("§7状态: " + (isUnlocked ? "§a已解锁" : isResearching ? "§b研究进行中" : "§c未解锁")),
                Component.text(getTechDescription(techId)),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 效果列表
        if (def != null && def.effects() != null && !def.effects().isEmpty()) {
            gui.setItem(20, createGuiItem(
                Material.BOOK,
                Component.text("§e📖 科技效果", NamedTextColor.YELLOW),
                buildEffectsLore(def.effects()),
                event -> {}, false
            ));
        }

        // 成本信息
        if (costOpt.isPresent()) {
            var cost = costOpt.get();
            List<Component> costLore = new ArrayList<>();
            costLore.add(Component.text(""));
            costLore.add(Component.text("§7国库消耗: §6" + cost.treasury() + " 星尘"));
            if (!cost.resources().isEmpty()) {
                costLore.add(Component.text(""));
                costLore.add(Component.text("§7资源消耗:"));
                for (var entry : cost.resources().entrySet()) {
                    costLore.add(Component.text("  §f" + entry.getKey() + ": §e" + entry.getValue()));
                }
            }
            costLore.add(Component.text(""));

            gui.setItem(22, createGuiItem(
                Material.GOLD_INGOT,
                Component.text("§e💰 研究成本", NamedTextColor.YELLOW),
                costLore,
                event -> {}, false
            ));
        }

        // 前置条件
        if (def != null && def.prerequisites() != null && !def.prerequisites().isEmpty()) {
            List<Component> prereqLore = new ArrayList<>();
            prereqLore.add(Component.text(""));
            prereqLore.add(Component.text("§7前置科技:"));
            for (String prereq : def.prerequisites()) {
                boolean prereqMet = technologyModule.hasTechnology(nationId, prereq);
                prereqLore.add(Component.text(prereqMet ? "  §a✓ " + getTechDisplayName(prereq) : "  §c✗ " + getTechDisplayName(prereq)));
            }
            prereqLore.add(Component.text(""));

            gui.setItem(24, createGuiItem(
                Material.PAPER,
                Component.text("§e📋 前置条件", NamedTextColor.YELLOW),
                prereqLore,
                event -> {}, false
            ));
        }

        // 研究时间
        if (def != null) {
            int researchTime = def.researchTimeSeconds();
            int modifiedTime = (int) (researchTime * technologyModule.getResearchSpeedModifier(nationId));

            gui.setItem(30, createGuiItem(
                Material.CLOCK,
                Component.text("§e⏱ 研究时间", NamedTextColor.YELLOW),
                List.of(
                    Component.text(""),
                    Component.text("§7基础时间: §f" + formatTime(researchTime)),
                    Component.text("§7当前时间: §e" + formatTime(modifiedTime)),
                    Component.text(""),
                    Component.text("§7(已应用科技/政策加成)")
                ),
                event -> {}, false
            ));
        }

        // 研究进度（如果正在研究）
        if (isResearching && progress != null) {
            double percent = progress.getProgress() * 100;
            long remaining = progress.getRemainingSeconds();

            gui.setItem(32, createGuiItem(
                Material.EXPERIENCE_BOTTLE,
                Component.text("§b⏳ 研究进度", NamedTextColor.AQUA),
                List.of(
                    Component.text(""),
                    Component.text("§7进度: " + createProgressBar(percent / 100)),
                    Component.text(""),
                    Component.text("§7" + String.format("%.1f", percent) + "%"),
                    Component.text("§7剩余时间: §e" + formatTime(remaining)),
                    Component.text("")
                ),
                event -> {}, false
            ));
        }

        // 操作按钮
        if (!isUnlocked && !isResearching) {
            // 检查是否满足研究条件
            String statusReason = getValidationStatus(validation);

            boolean canResearch = validation.valid();
            Material btnMat = canResearch ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS;
            Component btnName = canResearch ? Component.text("§a§l⬆ 开始研究", NamedTextColor.GREEN)
                : Component.text("§c§l✗ 无法研究", NamedTextColor.RED);

            List<Component> btnLore = new ArrayList<>();
            btnLore.add(Component.text(""));
            if (!canResearch) {
                btnLore.add(Component.text("§c" + statusReason));
            } else {
                if (costOpt.isPresent()) {
                    btnLore.add(Component.text("§7消耗: §6" + costOpt.get().treasury() + " 星尘"));
                }
                btnLore.add(Component.text(""));
                btnLore.add(Component.text("§a点击开始研究"));
            }
            btnLore.add(Component.text(""));

            gui.setItem(38, createGuiItem(btnMat, btnName, btnLore,
                event -> {
                    event.setCancelled(true);
                    if (canResearch) {
                        startResearch(player, nation, normalizedId);
                    }
                }, canResearch
            ));
        } else if (isResearching) {
            gui.setItem(38, createGuiItem(
                Material.RED_STAINED_GLASS,
                Component.text("§c§l✗ 取消研究", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7取消当前研究"),
                    Component.text("§7(已消耗资源不退还)"),
                    Component.text(""),
                    Component.text("§c▸ 点击取消")
                ),
                event -> {
                    event.setCancelled(true);
                    cancelResearch(player, nation, normalizedId);
                }, true
            ));
        } else {
            gui.setItem(38, createGuiItem(
                Material.BEACON,
                Component.text("§a§l✓ 已解锁", NamedTextColor.GREEN),
                List.of(
                    Component.text(""),
                    Component.text("§7该科技已解锁"),
                    Component.text("§7效果已生效")
                ),
                event -> event.setCancelled(true), false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回科技树", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回科技树列表")),
            event -> {
                event.setCancelled(true);
                openTechTreeView(player, nation, 0);
            },
            false
        ));

        gui.open(player);
    }

    /**
     * 打开已解锁科技视图
     */
    public void openUnlockedTechView(Player player, Nation nation) {
        NationId nationId = nation.id();
        Collection<String> unlocked = technologyModule.unlockedTechnologies(nationId);

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l⚙ 已解锁科技 (" + unlocked.size() + ")"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 标题
        gui.setItem(4, createGuiItem(
            Material.BOOK,
            Component.text("§a§l已解锁科技", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text(""),
                Component.text("§7已解锁: §a" + unlocked.size()),
                Component.text("§7可用: §f" + technologyModule.availableTechnologies().size()),
                Component.text("")
            ),
            event -> {}, false
        ));

        if (unlocked.isEmpty()) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无已解锁科技", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7前往科技树开始研究")
                ),
                event -> {}, false
            ));
        } else {
            // 显示已解锁的科技
            int slot = 10;
            int count = 0;
            for (String techId : unlocked) {
                if (count >= 14) break;
                if (slot == 17 || slot == 26 || slot == 35) slot++;

                Map<String, TechnologyDefinition> allDefs = technologyModule.getAllDefinitions();
                TechnologyDefinition def = allDefs.get(techId);

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(""));
                lore.add(Component.text("§a✓ 已解锁"));
                lore.add(Component.text(""));

                if (def != null && def.effects() != null) {
                    lore.add(Component.text("§7效果:"));
                    for (var effect : def.effects()) {
                        if (effect.description() != null) {
                            lore.add(Component.text("  " + effect.description()));
                        }
                    }
                }
                lore.add(Component.text(""));

                gui.setItem(slot, createGuiItem(
                    getTechMaterial(techId),
                    Component.text("§a" + getTechDisplayName(techId)),
                    lore,
                    event -> {
                        event.setCancelled(true);
                        openTechDetailMenu(player, nation, techId);
                    }, true
                ));

                slot++;
                count++;
            }
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§c◀ 返回", NamedTextColor.RED),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu(player);
            },
            false
        ));

        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开研究进度视图
     */
    public void openResearchProgressView(Player player, Nation nation) {
        NationId nationId = nation.id();
        Map<String, ResearchProgress> researching = technologyModule.getNationResearch(nationId);

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l⚙ 研究进度 (" + researching.size() + ")"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 标题
        gui.setItem(4, createGuiItem(
            Material.PAPER,
            Component.text("§b§l研究进度", NamedTextColor.AQUA),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text(""),
                Component.text("§7进行中: §b" + researching.size()),
                Component.text("")
            ),
            event -> {}, false
        ));

        if (researching.isEmpty()) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无进行中的研究", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7前往科技树开始新研究")
                ),
                event -> {}, false
            ));
        } else {
            // 显示研究中的科技
            int slot = 10;
            int count = 0;
            for (Map.Entry<String, ResearchProgress> entry : researching.entrySet()) {
                if (count >= 14) break;
                if (slot == 17 || slot == 26 || slot == 35) slot++;

                String techId = entry.getKey();
                ResearchProgress progress = entry.getValue();
                double percent = progress.getProgress() * 100;
                long remaining = progress.getRemainingSeconds();

                gui.setItem(slot, createGuiItem(
                    getTechMaterial(techId),
                    Component.text("§b" + getTechDisplayName(techId)),
                    List.of(
                        Component.text(""),
                        Component.text("§7进度: " + createProgressBar(percent / 100)),
                        Component.text(""),
                        Component.text("§7" + String.format("%.1f", percent) + "%"),
                        Component.text("§7剩余: §e" + formatTime(remaining)),
                        Component.text(""),
                        Component.text("§c▸ 点击取消")
                    ),
                    event -> {
                        event.setCancelled(true);
                        openCancelResearchConfirm(player, nation, techId);
                    }, false
                ));

                slot++;
                count++;
            }
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§c◀ 返回", NamedTextColor.RED),
            List.of(Component.text(""), Component.text("§7返回主菜单")),
            event -> {
                event.setCancelled(true);
                openMainMenu(player);
            },
            false
        ));

        fillBorder(gui, Material.BLACK_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开取消研究确认对话框
     */
    private void openCancelResearchConfirm(Player player, Nation nation, String techId) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§c§l⚠ 确认取消"))
            .rows(3)
            .disableAllInteractions()
            .create();

        gui.setItem(13, createGuiItem(
            Material.BARRIER,
            Component.text("§c确定要取消研究吗？", NamedTextColor.RED),
            List.of(
                Component.text(""),
                Component.text("§7科技: §f" + getTechDisplayName(techId)),
                Component.text(""),
                Component.text("§c⚠ 警告"),
                Component.text("§7已消耗的资源"),
                Component.text("§7将不会退还"),
                Component.text("")
            ),
            event -> {}, false
        ));

        gui.setItem(10, createGuiItem(
            Material.LIME_STAINED_GLASS,
            Component.text("§a§l✓ 确认取消", NamedTextColor.GREEN),
            List.of(
                Component.text(""),
                Component.text("§7点击确认取消研究")
            ),
            event -> {
                event.setCancelled(true);
                technologyModule.cancelResearch(nation.id(), techId);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                player.sendMessage(Component.text("§e[科技] §7研究已取消"));
                gui.close(player);
            }, true
        ));

        gui.setItem(16, createGuiItem(
            Material.RED_STAINED_GLASS,
            Component.text("§c§l✗ 返回", NamedTextColor.RED),
            List.of(
                Component.text(""),
                Component.text("§7点击返回")
            ),
            event -> {
                event.setCancelled(true);
                openResearchProgressView(player, nation);
            }, false
        ));

        gui.open(player);
    }

    // ==================== 核心操作 ====================

    private void startResearch(Player player, Nation nation, String techId) {
        NationId nationId = nation.id();

        boolean success = technologyModule.startResearch(nationId, techId, null);

        if (success) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.sendMessage(Component.text("§a[科技] §7开始研究: §e" + getTechDisplayName(techId)));

            // 刷新界面
            plugin.getServer().getScheduler().runTask(plugin, () ->
                openTechDetailMenu(player, nation, techId));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("§c[科技] §7研究开始失败！"));
        }
    }

    private void cancelResearch(Player player, Nation nation, String techId) {
        boolean success = technologyModule.cancelResearch(nation.id(), techId);

        if (success) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.sendMessage(Component.text("§e[科技] §7研究已取消"));
            openResearchProgressView(player, nation);
        } else {
            player.sendMessage(Component.text("§c[科技] §7取消研究失败"));
        }
    }

    // ==================== 辅助方法 ====================

    private String getTechDisplayName(String techId) {
        return techDisplayNames.getOrDefault(techId.toLowerCase(), "§7" + techId);
    }

    private String getTechDescription(String techId) {
        return techDescriptions.getOrDefault(techId.toLowerCase(), "§7科技效果");
    }

    private Material getTechMaterial(String techId) {
        return techMaterials.getOrDefault(techId.toLowerCase(), Material.BOOK);
    }

    private String getValidationStatus(TechnologyValidator.ValidationResult validation) {
        if (validation == null) return "§c无法验证";

        if (!validation.valid()) {
            for (String error : validation.errors()) {
                if (error.contains("already unlocked")) return "§c该科技已经解锁";
                if (error.contains("Missing prerequisites")) return "§c缺少前置科技: " + String.join(", ", validation.missingPrerequisites());
                if (error.contains("Insufficient treasury")) return "§c国库资金不足";
                if (error.contains("Insufficient")) return "§c资源不足";
                if (error.contains("Conflicts")) return "§c与已解锁科技冲突";
            }
        }

        return "§c无法研究";
    }

    private List<Component> buildEffectsLore(List<TechnologyEffect> effects) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        for (TechnologyEffect effect : effects) {
            if (effect.description() != null) {
                lore.add(Component.text("  " + effect.description()));
            }
        }
        lore.add(Component.text(""));
        return lore;
    }

    private String formatTime(int seconds) {
        return formatTime((long) seconds);
    }

    private String formatTime(long seconds) {
        if (seconds >= 3600) {
            int hours = (int) (seconds / 3600);
            int mins = (int) ((seconds % 3600) / 60);
            return hours + "小时" + mins + "分钟";
        } else if (seconds >= 60) {
            int mins = (int) (seconds / 60);
            int secs = (int) (seconds % 60);
            return mins + "分" + secs + "秒";
        } else {
            return seconds + "秒";
        }
    }

    private String createProgressBar(double progress) {
        int filled = (int) (progress * 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) bar.append("§a█");
        for (int i = filled; i < 10; i++) bar.append("§7█");
        bar.append("§7 ");
        bar.append(String.format("§f%.0f%%", progress * 100));
        return bar.toString();
    }

    private void fillBorder(Gui gui, Material borderMaterial) {
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
        if (glow && material != Material.BARRIER && material != Material.BLACK_STAINED_GLASS_PANE) {
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
}
