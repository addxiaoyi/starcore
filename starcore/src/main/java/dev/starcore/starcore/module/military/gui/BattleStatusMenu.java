package dev.starcore.starcore.module.military.gui;

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
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 战况预览菜单 GUI
 * 实时显示战争和军队状态信息
 */
public class BattleStatusMenu implements org.bukkit.inventory.InventoryHolder {

    private static final int SIZE = 54;
    private static final String TITLE = "§c§l⚔️ 战况预览中心";

    private final Inventory inventory;
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    // 依赖服务（通过构造函数注入）
    private final WarService warService;
    private final ArmyService armyService;
    private final NationService nationService;
    private final WarSituationService situationService;

    public BattleStatusMenu(
            WarService warService,
            ArmyService armyService,
            NationService nationService,
            WarSituationService situationService
    ) {
        this.warService = warService;
        this.armyService = armyService;
        this.nationService = nationService;
        this.situationService = situationService;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text(TITLE, NamedTextColor.RED));
        buildMenu(null);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /**
     * 获取玩家国家ID
     */
    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    /**
     * 获取国家名称
     */
    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId).map(Nation::name).orElse("未知国家");
    }

    /**
     * 构建菜单（根据玩家国家显示动态内容）
     */
    public void buildMenu(Player player) {
        inventory.clear();

        // 标题 - 带有时间戳
        Instant now = Instant.now();
        String timeStr = now.atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        inventory.setItem(4, createTitleItem(timeStr));

        if (player == null) {
            buildEmptyMenu();
            return;
        }

        NationId playerNationId = getPlayerNationId(player);
        if (playerNationId == null) {
            buildNoNationMenu();
            return;
        }

        // 构建完整菜单
        buildFullMenu(player, playerNationId);
    }

    /**
     * 构建空状态菜单
     */
    private void buildEmptyMenu() {
        inventory.setItem(22, createInfoItem(
            Material.BARRIER,
            "§c请先选择国家",
            "需要加入国家才能使用战况预览"
        ));
        addFooterButtons();
    }

    /**
     * 构建无国家菜单
     */
    private void buildNoNationMenu() {
        inventory.setItem(22, createInfoItem(
            Material.BROWN_BANNER,
            "§c你没有所属国家",
            "需要加入国家才能查看战况"
        ));
        addFooterButtons();
    }

    /**
     * 构建完整战况菜单
     */
    private void buildFullMenu(Player player, NationId nationId) {
        // 第一行：核心统计数据
        int activeWars = (int) warService.activeWarsOf(nationId).size();
        List<ArmyUnit> armies = armyService.getNationArmies(nationId.value());
        int totalSoldiers = armies.stream().mapToInt(ArmyUnit::soldiers).sum();
        WarIntensity overallIntensity = calculateOverallIntensity(nationId);

        inventory.setItem(10, createStatItem(
            Material.IRON_SWORD,
            "§c⚔️ 进行中战争",
            String.valueOf(activeWars),
            "当前交战的敌国数量"
        ));

        inventory.setItem(11, createStatItem(
            Material.PLAYER_HEAD,
            "§e👥 军队总数",
            String.valueOf(armies.size()),
            "你的国家所有军队"
        ));

        inventory.setItem(12, createStatItem(
            Material.IRON_CHESTPLATE,
            "§a⚔️ 总兵力",
            formatNumber(totalSoldiers),
            "所有军队士兵总数"
        ));

        inventory.setItem(14, createStatItem(
            Material.BOW,
            "§6🔥 战争强度",
            overallIntensity.displayName(),
            getIntensityDescription(overallIntensity)
        ));

        inventory.setItem(15, createStatItem(
            Material.CLOCK,
            "§b⏱️ 最后更新",
            formatTime(Instant.now()),
            "数据实时更新中"
        ));

        // 第二行：进行中的战争列表
        Collection<WarSnapshot> activeWarSnapshots = warService.activeWarsOf(nationId);
        if (activeWarSnapshots.isEmpty()) {
            inventory.setItem(19, createInfoItem(
                Material.GREEN_BANNER,
                "§a✌️ 和平安宁",
                "你的国家目前没有参与的战争"
            ));
        } else {
            int slot = 19;
            for (WarSnapshot war : activeWarSnapshots) {
                if (slot > 25) break;

                NationId enemyId = war.left().equals(nationId) ? war.right() : war.left();
                String enemyName = getNationName(enemyId);
                Duration duration = Duration.between(war.declaredAt(), Instant.now());

                // 获取战况
                Optional<WarSituation> situationOpt = situationService.getSituationForWar(nationId, enemyId);
                WarSituation situation = situationOpt.orElse(null);

                inventory.setItem(slot, createWarItem(enemyName, duration, situation, nationId));
                slot++;
            }
        }

        // 第三行：军队分布
        inventory.setItem(28, createSectionTitle("§e§l📊 军队分布"));
        inventory.setItem(29, createArmyDistributionItem(armies, nationId));

        // 第四行：战场情报
        inventory.setItem(37, createSectionTitle("§c§l⚔️ 战场情报"));

        // 获取所有活跃战场的统计
        int totalBattles = 0;
        int totalCasualties = 0;
        List<WarSituation> situations = situationService.getSituationsForNation(nationId);
        for (WarSituation s : situations) {
            totalBattles += s.totalBattles();
            totalCasualties += s.casualties();
        }

        inventory.setItem(38, createStatItem(
            Material.BLAZE_POWDER,
            "§c⚔️ 战斗总数",
            String.valueOf(totalBattles),
            "历史战斗场次"
        ));

        inventory.setItem(40, createStatItem(
            Material.REDSTONE,
            "§4💀 总伤亡",
            formatNumber(totalCasualties),
            "双方总伤亡人数"
        ));

        // 底部按钮
        inventory.setItem(48, createActionButton(
            Material.COMPASS,
            "§e🔄 刷新战况",
            "重新加载最新数据"
        ));

        addFooterButtons();
    }

    /**
     * 创建标题物品
     */
    private ItemStack createTitleItem(String timeStr) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§c§l⚔️ 战况预览中心", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                Component.text("§7实时战场态势", NamedTextColor.GRAY),
                Component.text("§7更新时间: §f" + timeStr, NamedTextColor.GRAY),
                Component.text(""),
                Component.text("§e点击刷新数据", NamedTextColor.YELLOW)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建统计物品
     */
    private ItemStack createStatItem(Material material, String label, String value, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§e" + value, NamedTextColor.GOLD));
            lore.add(Component.text("§7" + description, NamedTextColor.GRAY));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建战争物品
     */
    private ItemStack createWarItem(String enemyName, Duration duration, WarSituation situation, NationId myNationId) {
        Material material = Material.IRON_SWORD;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String durationStr = formatDuration(duration);
            meta.displayName(Component.text("§c⚔️ vs " + enemyName, NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7持续: §e" + durationStr, NamedTextColor.GRAY));
            lore.add(Component.text(""));

            if (situation != null) {
                // 显示战况评分
                String scoreText;
                if (situation.advantage() == 1) {
                    scoreText = "§a优势";
                } else if (situation.advantage() == 2) {
                    scoreText = "§c劣势";
                } else {
                    scoreText = "§e僵持";
                }
                lore.add(Component.text("§7局势: " + scoreText, getAdvantageColor(situation.advantage())));
                lore.add(Component.text("§7强度: §f" + situation.intensity().displayName(), NamedTextColor.GRAY));
                lore.add(Component.text("§7我方评分: §a" + String.format("%.0f", situation.nation1Score()),
                    NamedTextColor.GREEN));
                lore.add(Component.text("§7敌方评分: §c" + String.format("%.0f", situation.nation2Score()),
                    NamedTextColor.RED));
            } else {
                lore.add(Component.text("§7战况: §e加载中...", NamedTextColor.YELLOW));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("§e点击查看详情", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建区域标题
     */
    private ItemStack createSectionTitle(String text) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(text, NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建军队分布物品
     */
    private ItemStack createArmyDistributionItem(List<ArmyUnit> armies, NationId nationId) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§e📊 军队分布", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();

            // 按类型统计
            Map<String, Integer> typeCount = new HashMap<>();
            Map<String, Integer> typeSoldiers = new HashMap<>();
            for (ArmyUnit army : armies) {
                String typeName = army.type().key();
                typeCount.merge(typeName, 1, Integer::sum);
                typeSoldiers.merge(typeName, army.soldiers(), Integer::sum);
            }

            for (Map.Entry<String, Integer> entry : typeCount.entrySet()) {
                String typeName = entry.getKey();
                int count = entry.getValue();
                int soldiers = typeSoldiers.get(typeName);
                lore.add(Component.text("§7" + typeName + ": §f" + count + "支 §e(" + formatNumber(soldiers) + "人)",
                    NamedTextColor.GRAY));
            }

            if (armies.isEmpty()) {
                lore.add(Component.text("§7暂无军队", NamedTextColor.GRAY));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("§e点击打开军队管理", NamedTextColor.YELLOW));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建信息物品
     */
    private ItemStack createInfoItem(Material material, String title, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                Component.text("§7" + description, NamedTextColor.GRAY)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建操作按钮
     */
    private ItemStack createActionButton(Material material, String title, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.YELLOW));
            meta.lore(List.of(
                Component.text("§7" + description, NamedTextColor.GRAY)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 添加底部按钮
     */
    private void addFooterButtons() {
        // 帮助按钮
        ItemStack helpItem = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = helpItem.getItemMeta();
        if (helpMeta != null) {
            helpMeta.displayName(Component.text("§b❓ 帮助", NamedTextColor.AQUA));
            helpMeta.lore(List.of(
                Component.text("§7战况预览显示你国家的", NamedTextColor.GRAY),
                Component.text("§7战争状态和军队分布", NamedTextColor.GRAY)
            ));
            helpItem.setItemMeta(helpMeta);
        }
        inventory.setItem(49, helpItem);

        // 关闭按钮
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("§c✖ 关闭", NamedTextColor.RED));
        }
        inventory.setItem(53, closeItem);
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算整体战争强度
     */
    private WarIntensity calculateOverallIntensity(NationId nationId) {
        List<WarSituation> situations = situationService.getSituationsForNation(nationId);
        if (situations.isEmpty()) {
            return WarIntensity.CALM;
        }

        return situations.stream()
            .map(WarSituation::intensity)
            .max(Comparator.comparingInt(WarIntensity::ordinal))
            .orElse(WarIntensity.CALM);
    }

    /**
     * 获取强度描述
     */
    private String getIntensityDescription(WarIntensity intensity) {
        return switch (intensity) {
            case CALM -> "和平或低烈度冲突";
            case SKIRMISH -> "小规模冲突";
            case ACTIVE -> "激烈交战";
            case INTENSE -> "全面战争";
        };
    }

    /**
     * 获取优势颜色
     */
    private NamedTextColor getAdvantageColor(int advantage) {
        return switch (advantage) {
            case 1 -> NamedTextColor.GREEN;
            case 2 -> NamedTextColor.RED;
            default -> NamedTextColor.YELLOW;
        };
    }

    /**
     * 格式化数字（添加千分位）
     */
    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

    /**
     * 格式化时间
     */
    private String formatTime(Instant instant) {
        return instant.atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * 格式化时长
     */
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return days + "天" + hours + "小时";
        } else if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        } else {
            return minutes + "分钟";
        }
    }

    // ==================== 公开方法 ====================

    public Map<UUID, Integer> getPlayerPages() {
        return playerPages;
    }

    public void openMainMenu(Player player) {
        buildMenu(player);
        player.openInventory(inventory);
    }

    public void openBattlefieldMenu(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);
        buildMenu(player);
        player.openInventory(inventory);
    }

    public void openArmyDistributionMenu(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);
        buildMenu(player);
        player.openInventory(inventory);
    }

    public void openWarTrendMenu(Player player) {
        buildMenu(player);
        player.openInventory(inventory);
    }

    public void openEnemyAnalysisMenu(Player player) {
        buildMenu(player);
        player.openInventory(inventory);
    }

    /**
     * 刷新菜单（公开方法）
     */
    public void refreshMenu(Player player) {
        buildMenu(player);
    }
}
