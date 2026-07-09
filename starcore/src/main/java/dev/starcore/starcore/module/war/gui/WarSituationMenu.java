package dev.starcore.starcore.module.war.gui;

import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.WarSnapshot;
import dev.starcore.starcore.module.war.situation.WarIntensity;
import dev.starcore.starcore.module.war.situation.WarSituation;
import dev.starcore.starcore.module.war.situation.WarSituationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战况预览 GUI 菜单
 * 提供实时战场态势展示
 */
public final class WarSituationMenu {

    // GUI 标题
    public static final String MAIN_TITLE = "§6§l⚔️ 战况中心";
    public static final String WAR_OVERVIEW_TITLE = "§c§l⚔️ 战争总览";
    public static final String ARMY_STATUS_TITLE = "§b§l🛡️ 军队状态";
    public static final String BATTLEFIELD_TITLE = "§4§l💥 战场态势";
    public static final String CASUALTY_TITLE = "§c§l☠️ 伤亡报告";

    private final WarService warService;
    private final WarSituationService situationService;
    private final ArmyService armyService;
    private final NationService nationService;
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;

    // 玩家当前查看的战争
    private final Map<UUID, UUID> playerWarViews = new ConcurrentHashMap<>();

    public WarSituationMenu(
        WarService warService,
        WarSituationService situationService,
        ArmyService armyService,
        NationService nationService,
        GuiAnimationManager animationManager,
        SoundFeedbackManager soundManager
    ) {
        this.warService = warService;
        this.situationService = situationService;
        this.armyService = armyService;
        this.nationService = nationService;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
    }

    /**
     * 打开战况中心主菜单
     */
    public void openMainMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "战况中心");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            player.sendMessage("§c你需要先加入一个国家");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 36, Component.text(MAIN_TITLE));
        fillBorder(inv, Material.YELLOW_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§6§l⚔️ 战况中心"));

        // 统计数据
        int activeWars = countActiveWars(playerNationId);
        int totalArmies = countTotalArmies(playerNationId);

        // 左侧统计
        inv.setItem(10, createStatItem(
            Material.IRON_SWORD,
            "§c⚔️ 进行中战争",
            String.valueOf(activeWars),
            "当前交战的敌国数量"
        ));

        inv.setItem(12, createStatItem(
            Material.PLAYER_HEAD,
            "§e🛡️ 军队总数",
            String.valueOf(totalArmies),
            "你的国家所有军队"
        ));

        // 右侧功能按钮
        inv.setItem(19, ButtonFactory.createStyledButton(
            "§c⚔️ 战争总览",
            Material.MAP,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "查看所有战争概况",
            "实时更新战况数据"
        ));

        inv.setItem(21, ButtonFactory.createStyledButton(
            "§b🛡️ 军队状态",
            Material.PLAYER_HEAD,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看所有军队状态",
            "生命值、士气、补给"
        ));

        inv.setItem(23, ButtonFactory.createStyledButton(
            "§4💥 战场态势",
            Material.BLAZE_POWDER,
            ButtonFactory.BUTTON_STYLE_DANGER,
            "查看战场实时情况",
            "各战场控制状态"
        ));

        inv.setItem(25, ButtonFactory.createStyledButton(
            "§c☠️ 伤亡报告",
            Material.BONE,
            ButtonFactory.BUTTON_STYLE_INFO,
            "查看战争伤亡统计",
            "双方损失情况"
        ));

        // 帮助信息
        inv.setItem(31, ButtonFactory.createInfoButton(
            "提示: 战况每30秒自动刷新",
            "点击可查看详细数据"
        ));

        // 返回按钮
        inv.setItem(35, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开战争总览
     */
    public void openWarOverview(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "战争总览");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) return;

        Collection<WarSnapshot> activeWars = warService.activeWarsOf(playerNationId);
        int size = Math.max(45, ((activeWars.size() / 4) + 1) * 9 + 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, Component.text(WAR_OVERVIEW_TITLE));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§c§l⚔️ 战争总览"));

        if (activeWars.isEmpty()) {
            inv.setItem(22, ButtonFactory.createInfoButton(
                "§a✌️ 和平安宁",
                "你的国家目前没有参与的战争",
                "点击返回主菜单"
            ));
            inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        // 填充战争列表
        int slot = 10;
        for (WarSnapshot war : activeWars) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= size - 9) break;

            NationId enemyId = war.left().equals(playerNationId) ? war.right() : war.left();
            String enemyName = getNationName(enemyId);

            // 获取战况
            Optional<WarSituation> situationOpt = situationService.getSituationForWar(playerNationId, enemyId);
            WarSituation situation = situationOpt.orElse(null);

            ItemStack item = createWarOverviewItem(playerNationId, enemyId, enemyName, war, situation);
            inv.setItem(slot, item);
            slot++;
        }

        // 返回按钮
        inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开军队状态总览
     */
    public void openArmyStatus(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "军队状态");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) return;

        List<ArmyUnit> armies = armyService.getNationArmies(playerNationId.value());

        int size = Math.max(36, ((armies.size() / 5) + 1) * 9 + 9);
        size = Math.min(size, 54);

        Inventory inv = Bukkit.createInventory(null, size, Component.text(ARMY_STATUS_TITLE));
        fillBorder(inv, Material.BLUE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§b§l🛡️ 军队状态"));

        // 汇总统计
        int totalSoldiers = armies.stream().mapToInt(ArmyUnit::soldiers).sum();
        double avgHealth = armies.isEmpty() ? 0 : armies.stream().mapToDouble(ArmyUnit::health).average().orElse(0);
        double avgMorale = armies.isEmpty() ? 0 : armies.stream().mapToDouble(ArmyUnit::morale).average().orElse(0);

        inv.setItem(10, createStatItem(
            Material.PLAYER_HEAD,
            "§e👥 总兵力",
            String.valueOf(totalSoldiers),
            "所有军队士兵总数"
        ));

        inv.setItem(12, createStatItem(
            Material.REDSTONE,
            "§c❤ 平均生命",
            String.format("%.0f%%", avgHealth),
            "所有军队平均生命值"
        ));

        inv.setItem(14, createStatItem(
            Material.YELLOW_STAINED_GLASS,
            "§e💭 平均士气",
            String.format("%.0f%%", avgMorale),
            "所有军队平均士气"
        ));

        if (armies.isEmpty()) {
            inv.setItem(22, ButtonFactory.createInfoButton(
                "§e📭 没有军队",
                "你还没有组建任何军队",
                "使用 /army create 创建"
            ));
            inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        // 填充军队列表
        int slot = 19;
        for (ArmyUnit army : armies) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= size - 9) break;

            ItemStack item = createArmyStatusItem(army);
            inv.setItem(slot, item);
            slot++;
        }

        // 返回按钮
        inv.setItem(size - 9 + 4, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开战场态势
     */
    public void openBattlefieldSituation(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "战场态势");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) return;

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(BATTLEFIELD_TITLE));
        fillBorder(inv, Material.ORANGE_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§4§l💥 战场态势"));

        // 获取当前战争的战况
        Collection<WarSnapshot> activeWars = warService.activeWarsOf(playerNationId);

        if (activeWars.isEmpty()) {
            inv.setItem(22, ButtonFactory.createInfoButton(
                "§a✌️ 无活跃战场",
                "当前没有正在进行战争",
                "没有战场数据可显示"
            ));
            inv.setItem(40, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        int slot = 10;
        for (WarSnapshot war : activeWars) {
            if (slot >= 35) break;

            NationId enemyId = war.left().equals(playerNationId) ? war.right() : war.left();
            String enemyName = getNationName(enemyId);

            Optional<WarSituation> situationOpt = situationService.getSituationForWar(playerNationId, enemyId);
            WarSituation situation = situationOpt.orElse(null);

            ItemStack item = createBattlefieldItem(playerNationId, enemyId, enemyName, war, situation);
            inv.setItem(slot, item);
            slot++;

            if (slot % 9 == 8) slot += 2;
        }

        // 返回按钮
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    /**
     * 打开伤亡报告
     */
    public void openCasualtyReport(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "伤亡报告");
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) return;

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(CASUALTY_TITLE));
        fillBorder(inv, Material.GRAY_STAINED_GLASS_PANE);

        // 标题
        inv.setItem(4, createTitleItem("§c§l☠️ 伤亡报告"));

        // 获取战争伤亡数据
        Collection<WarSnapshot> activeWars = warService.activeWarsOf(playerNationId);

        if (activeWars.isEmpty()) {
            inv.setItem(22, ButtonFactory.createInfoButton(
                "§a✌️ 无战争伤亡",
                "当前没有战争进行",
                "没有伤亡数据可显示"
            ));
            inv.setItem(40, ButtonFactory.createBackButton());
            player.openInventory(inv);
            return;
        }

        int totalCasualties = 0;
        int totalBattles = 0;

        int slot = 10;
        for (WarSnapshot war : activeWars) {
            if (slot >= 35) break;

            NationId enemyId = war.left().equals(playerNationId) ? war.right() : war.left();
            String enemyName = getNationName(enemyId);

            Optional<WarSituation> situationOpt = situationService.getSituationForWar(playerNationId, enemyId);
            WarSituation situation = situationOpt.orElse(null);

            int casualties = situation != null ? situation.casualties() : 0;
            int battles = situation != null ? situation.totalBattles() : 0;
            totalCasualties += casualties;
            totalBattles += battles;

            ItemStack item = createCasualtyItem(enemyId, enemyName, war, situation);
            inv.setItem(slot, item);
            slot++;

            if (slot % 9 == 8) slot += 2;
        }

        // 汇总
        inv.setItem(37, createStatItem(
            Material.BONE,
            "§c☠️ 总伤亡",
            String.valueOf(totalCasualties),
            "所有战争的总伤亡人数"
        ));

        inv.setItem(38, createStatItem(
            Material.IRON_SWORD,
            "§c⚔️ 总战斗",
            String.valueOf(totalBattles),
            "所有战争的总战斗次数"
        ));

        // 返回按钮
        inv.setItem(40, ButtonFactory.createBackButton());

        player.openInventory(inv);
    }

    // ==================== 创建物品方法 ====================

    private ItemStack createTitleItem(String name) {
        return ButtonFactory.createStyledButton(name, Material.NETHER_STAR, ButtonFactory.BUTTON_STYLE_INFO);
    }

    private ItemStack createStatItem(Material material, String label, String value, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§e" + value, NamedTextColor.GOLD));
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createWarOverviewItem(NationId playerNationId, NationId enemyId, String enemyName,
                                            WarSnapshot war, WarSituation situation) {
        Material material = situation != null ? getIntensityMaterial(situation.intensity()) : Material.IRON_SWORD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            WarIntensity intensity = situation != null ? situation.intensity() : WarIntensity.CALM;
            meta.displayName(Component.text(
                intensity.emoji() + " §c⚔️ vs " + enemyName,
                NamedTextColor.RED
            ).decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7强度: " + intensity.coloredName(), NamedTextColor.GRAY));

            Duration duration = Duration.between(war.declaredAt(), Instant.now());
            lore.add(Component.text("§7持续: §e" + formatDuration(duration), NamedTextColor.YELLOW));

            if (situation != null) {
                lore.add(Component.text("§7战斗: §f" + situation.totalBattles() + " 次", NamedTextColor.GRAY));
                lore.add(Component.text("§7伤亡: §c" + situation.casualties() + " 人", NamedTextColor.RED));

                // 双方评分
                double playerScore = situation.nation1().equals(playerNationId)
                    ? situation.nation1Score() : situation.nation2Score();
                double enemyScore = situation.nation1().equals(playerNationId)
                    ? situation.nation2Score() : situation.nation1Score();

                lore.add(Component.text(""));
                lore.add(Component.text("§a我方: §f" + String.format("%.0f", playerScore), NamedTextColor.GREEN));
                lore.add(Component.text("§c敌方: §f" + String.format("%.0f", enemyScore), NamedTextColor.RED));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("§e点击查看详细战况", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createArmyStatusItem(ArmyUnit army) {
        Material material = getArmyTypeMaterial(army.type().name());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String shortId = army.id().toString().substring(0, 8);
            meta.displayName(Component.text(
                army.type().name() + " §7[#§f" + shortId + "§7]",
                getHealthColor(army.health())
            ).decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7状态: " + army.state().name(), getStateColor(army.state().name())));
            lore.add(Component.text("§7士兵: §f" + army.soldiers() + " 人", NamedTextColor.GRAY));
            lore.add(Component.text("§c❤ 生命: " + formatBar(army.health(), 100), getHealthColor(army.health())));
            lore.add(Component.text("§e💭 士气: " + formatBar(army.morale(), 100), getMoraleColor(army.morale())));
            lore.add(Component.text("§b📦 补给: " + formatBar(army.supply(), 100), getSupplyColor(army.supply())));
            lore.add(Component.text(""));
            lore.add(Component.text("§7⚔️ 攻击: §c" + String.format("%.1f", army.effectiveAttack()), NamedTextColor.RED));
            lore.add(Component.text("§7🛡️ 防御: §b" + String.format("%.1f", army.effectiveDefense()), NamedTextColor.BLUE));

            if (army.location() != null) {
                lore.add(Component.text("§7📍 " + formatLocation(army.location()), NamedTextColor.GRAY));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBattlefieldItem(NationId playerNationId, NationId enemyId, String enemyName,
                                            WarSnapshot war, WarSituation situation) {
        Material material = situation != null ? getIntensityMaterial(situation.intensity()) : Material.BLAZE_POWDER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            WarIntensity intensity = situation != null ? situation.intensity() : WarIntensity.CALM;
            meta.displayName(Component.text(
                "§4💥 " + enemyName + " 战场",
                intensity == WarIntensity.INTENSE ? NamedTextColor.RED : NamedTextColor.YELLOW
            ).decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7战况: " + intensity.coloredName(), NamedTextColor.GRAY));

            if (situation != null) {
                // 显示最近战斗
                if (!situation.recentBattles().isEmpty()) {
                    lore.add(Component.text("§7最近战斗:", NamedTextColor.GRAY));
                    situation.recentBattles().stream().limit(3).forEach(battle -> {
                        String result = battle.winner().equals(playerNationId) ? "§a胜" : "§c负";
                        lore.add(Component.text(
                            "  " + result + " §7- " + battle.battlefieldName() + " §7(伤亡:" + battle.loserLosses() + ")",
                            NamedTextColor.GRAY
                        ));
                    });
                }

                // 优势方
                int advantage = situation.advantage();
                if (advantage == 1) {
                    lore.add(Component.text("§a✅ 我方占据优势", NamedTextColor.GREEN));
                } else if (advantage == 2) {
                    lore.add(Component.text("§c⚠️ 敌方占据优势", NamedTextColor.RED));
                } else {
                    lore.add(Component.text("§e⚖️ 双方势均力敌", NamedTextColor.YELLOW));
                }
            }

            lore.add(Component.text(""));
            lore.add(Component.text("§e点击查看更多", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCasualtyItem(NationId enemyId, String enemyName,
                                         WarSnapshot war, WarSituation situation) {
        Material material = Material.BONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(
                "§c⚔️ vs " + enemyName,
                NamedTextColor.RED
            ).decorate(TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();

            if (situation != null) {
                lore.add(Component.text("§7总战斗: §f" + situation.totalBattles() + " 次", NamedTextColor.GRAY));
                lore.add(Component.text("§c☠️ 总伤亡: §f" + situation.casualties() + " 人", NamedTextColor.RED));

                // 显示最近伤亡
                situation.recentBattles().stream().limit(3).forEach(battle -> {
                    lore.add(Component.text(
                        "§7- " + battle.battlefieldName() + ": §c-" + battle.loserLosses(),
                        NamedTextColor.GRAY
                    ));
                });
            } else {
                lore.add(Component.text("§7暂无伤亡数据", NamedTextColor.GRAY));
            }

            Duration duration = Duration.between(war.declaredAt(), Instant.now());
            lore.add(Component.text("§7持续: §e" + formatDuration(duration), NamedTextColor.YELLOW));

            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== 辅助方法 ====================

    private void fillBorder(Inventory inv, Material material) {
        ItemStack border = ButtonFactory.createBorder(material);
        int size = inv.getSize();
        int rows = size / 9;
        for (int row = 0; row < rows; row++) {
            inv.setItem(row * 9, border);
            inv.setItem(row * 9 + 8, border);
        }
        for (int col = 1; col < 8; col++) {
            inv.setItem(col, border);
            inv.setItem(size - 9 + col, border);
        }
    }

    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId).map(Nation::name).orElse("未知国家");
    }

    private int countActiveWars(NationId nationId) {
        if (nationId == null || warService == null) return 0;
        return (int) warService.activeWarsOf(nationId).size();
    }

    private int countTotalArmies(NationId nationId) {
        if (nationId == null || armyService == null) return 0;
        return armyService.getNationArmies(nationId.value()).size();
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return days + "天 " + hours + "小时";
        } else if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        } else {
            return minutes + "分钟";
        }
    }

    private String formatBar(double value, double max) {
        int filled = (int) (value / max * 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? "█" : "░");
        }
        return sb.toString();
    }

    private String formatLocation(org.bukkit.Location loc) {
        if (loc == null) return "未知";
        return String.format("(%d, %d, %d)",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private Material getIntensityMaterial(WarIntensity intensity) {
        return switch (intensity) {
            case CALM -> Material.GREEN_STAINED_GLASS;
            case SKIRMISH -> Material.YELLOW_STAINED_GLASS;
            case ACTIVE -> Material.ORANGE_STAINED_GLASS;
            case INTENSE -> Material.RED_STAINED_GLASS;
        };
    }

    private Material getArmyTypeMaterial(String type) {
        return switch (type.toUpperCase()) {
            case "INFANTRY" -> Material.IRON_SWORD;
            case "CAVALRY" -> Material.SADDLE;
            case "ARCHER" -> Material.BOW;
            case "SIEGE" -> Material.TNT;
            case "DEFENSIVE" -> Material.SHIELD;
            default -> Material.PLAYER_HEAD;
        };
    }

    private NamedTextColor getHealthColor(double health) {
        if (health >= 80) return NamedTextColor.GREEN;
        if (health >= 50) return NamedTextColor.YELLOW;
        if (health >= 20) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    private NamedTextColor getMoraleColor(double morale) {
        if (morale >= 80) return NamedTextColor.GREEN;
        if (morale >= 50) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor getSupplyColor(int supply) {
        if (supply >= 60) return NamedTextColor.GREEN;
        if (supply >= 30) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private NamedTextColor getStateColor(String state) {
        return switch (state.toUpperCase()) {
            case "STATIONARY" -> NamedTextColor.GREEN;
            case "MARCHING" -> NamedTextColor.YELLOW;
            case "DEFENDING" -> NamedTextColor.BLUE;
            case "FIGHTING" -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
    }
}