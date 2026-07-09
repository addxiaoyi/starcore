package dev.starcore.starcore.module.nation.statusbar;

import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.mechanics.ReputationService;
import dev.starcore.starcore.mechanics.Reputation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 国家状态栏服务
 * 实时显示国家资源、军队、领土、声望等信息
 */
public class NationStatusBarService {

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final ArmyService armyService;
    private final ReputationService reputationService;

    // 玩家状态栏映射
    private final Map<UUID, BossBar> playerStatusBars = new ConcurrentHashMap<>();
    // 状态栏显示模式
    private volatile NationStatusBarMode displayMode = NationStatusBarMode.STANDARD;
    // 更新间隔（tick）
    private volatile int updateInterval = 20 * 10; // 默认10秒

    // 状态栏颜色轮换
    private final AtomicInteger colorIndex = new AtomicInteger(0);

    public NationStatusBarService(
            Plugin plugin,
            NationService nationService,
            TreasuryService treasuryService,
            ArmyService armyService,
            ReputationService reputationService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.armyService = armyService;
        this.reputationService = reputationService;

        startUpdateTask();
    }

    /**
     * 获取插件实例
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * 为玩家显示国家状态栏
     */
    public void showStatusBar(Player player) {
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            hideStatusBar(player);
            return;
        }

        BossBar bossBar = createStatusBar(player, nation);
        playerStatusBars.put(player.getUniqueId(), bossBar);
        player.showBossBar(bossBar);
    }

    /**
     * 隐藏玩家状态栏
     */
    public void hideStatusBar(Player player) {
        BossBar existing = playerStatusBars.remove(player.getUniqueId());
        if (existing != null) {
            player.hideBossBar(existing);
        }
    }

    /**
     * 更新玩家状态栏
     */
    public void updateStatusBar(Player player) {
        Nation nation = nationService.nationOf(player.getUniqueId()).orElse(null);
        if (nation == null) {
            hideStatusBar(player);
            return;
        }

        BossBar oldBar = playerStatusBars.get(player.getUniqueId());
        if (oldBar != null) {
            player.hideBossBar(oldBar);
        }

        BossBar newBar = createStatusBar(player, nation);
        playerStatusBars.put(player.getUniqueId(), newBar);
        player.showBossBar(newBar);
    }

    /**
     * 刷新所有在线玩家的状态栏
     */
    public void refreshAllStatusBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateStatusBar(player);
        }
    }

    /**
     * 刷新指定国家的所有成员状态栏
     */
    public void refreshNationStatusBars(UUID nationId) {
        Nation nation = nationService.getNation(nationId).orElse(null);
        if (nation == null) return;

        for (UUID memberId : nation.getMembers().stream()
                .map(m -> m.playerId())
                .toList()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                updateStatusBar(player);
            }
        }
    }

    /**
     * 创建状态栏
     */
    private BossBar createStatusBar(Player player, Nation nation) {
        Component name = buildStatusBarContent(player, nation);
        float progress = calculateProgress(nation);

        BossBar.Color color = getNextColor();
        BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;

        return BossBar.bossBar(name, progress, color, overlay);
    }

    /**
     * 构建状态栏内容
     */
    private Component buildStatusBarContent(Player player, Nation nation) {
        return switch (displayMode) {
            case STANDARD -> buildStandardContent(nation);
            case DETAILED -> buildDetailedContent(nation);
            case COMPACT -> buildCompactContent(nation);
            case MILITARY -> buildMilitaryContent(nation);
        };
    }

    /**
     * 标准模式
     */
    private Component buildStandardContent(Nation nation) {
        BigDecimal treasury = treasuryService != null ?
                treasuryService.balance(nation.id()) : BigDecimal.ZERO;
        int armies = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).size() : 0;
        int soldiers = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).stream()
                        .mapToInt(a -> a.soldiers())
                        .sum() : 0;

        Component nationName = Component.text(nation.name(), NamedTextColor.GOLD, TextDecoration.BOLD);
        Component separator = Component.text(" | ", NamedTextColor.GRAY);

        Component treasuryInfo = Component.text("国库: ", NamedTextColor.YELLOW)
                .append(Component.text(formatMoney(treasury), NamedTextColor.WHITE));

        Component territoryInfo = Component.text("领土: ", NamedTextColor.AQUA)
                .append(Component.text(nation.territoryCount() + "块", NamedTextColor.WHITE));

        Component armyInfo = Component.text("军队: ", NamedTextColor.RED)
                .append(Component.text(armies + "支/" + soldiers + "人", NamedTextColor.WHITE));

        Component membersInfo = Component.text("成员: ", NamedTextColor.GREEN)
                .append(Component.text(nation.memberCount() + "人", NamedTextColor.WHITE));

        return nationName
                .append(separator)
                .append(treasuryInfo)
                .append(separator)
                .append(territoryInfo)
                .append(separator)
                .append(armyInfo)
                .append(separator)
                .append(membersInfo);
    }

    /**
     * 详细模式
     */
    private Component buildDetailedContent(Nation nation) {
        BigDecimal treasury = treasuryService != null ?
                treasuryService.balance(nation.id()) : BigDecimal.ZERO;
        int armies = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).size() : 0;
        int soldiers = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).stream()
                        .mapToInt(a -> a.soldiers())
                        .sum() : 0;
        int taxRate = (int) (nation.getTaxRate() * 100);

        Component header = Component.text("【" + nation.name() + "】", NamedTextColor.GOLD, TextDecoration.BOLD);

        Component resources = Component.text("\n")
                .append(Component.text("  资源: ", NamedTextColor.YELLOW))
                .append(Component.text(formatMoney(treasury), NamedTextColor.WHITE))
                .append(Component.text(" | 税率: ", NamedTextColor.YELLOW))
                .append(Component.text(taxRate + "%", NamedTextColor.WHITE));

        Component territory = Component.text("\n")
                .append(Component.text("  领土: ", NamedTextColor.AQUA))
                .append(Component.text(nation.territoryCount() + "块", NamedTextColor.WHITE))
                .append(Component.text(" | 成员: ", NamedTextColor.AQUA))
                .append(Component.text(nation.memberCount() + "人", NamedTextColor.WHITE));

        Component military = Component.text("\n")
                .append(Component.text("  军队: ", NamedTextColor.RED))
                .append(Component.text(armies + "支/" + soldiers + "人", NamedTextColor.WHITE))
                .append(Component.text(" | 政策: ", NamedTextColor.RED))
                .append(Component.text(nation.getActivePolicyCount() + "项", NamedTextColor.WHITE));

        Component diplomacy = Component.text("\n")
                .append(Component.text("  外交: ", NamedTextColor.GREEN))
                .append(Component.text("盟友" + nation.getAllyCount() + " | 战争" + nation.getWarCount(), NamedTextColor.WHITE))
                .append(Component.text(" | 科技: ", NamedTextColor.GREEN))
                .append(Component.text(nation.getUnlockedTechCount() + "项", NamedTextColor.WHITE));

        return header.append(resources).append(territory).append(military).append(diplomacy);
    }

    /**
     * 紧凑模式
     */
    private Component buildCompactContent(Nation nation) {
        BigDecimal treasury = treasuryService != null ?
                treasuryService.balance(nation.id()) : BigDecimal.ZERO;
        int soldiers = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).stream()
                        .mapToInt(a -> a.soldiers())
                        .sum() : 0;

        return Component.text("【" + nation.name() + "】", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Component.text("$" + formatMoney(treasury), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("⚔" + soldiers, NamedTextColor.RED))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("⬚" + nation.territoryCount(), NamedTextColor.AQUA));
    }

    /**
     * 军事模式
     */
    private Component buildMilitaryContent(Nation nation) {
        int armies = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).size() : 0;
        int soldiers = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).stream()
                        .mapToInt(a -> a.soldiers())
                        .sum() : 0;

        Component header = Component.text("[军事] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text(nation.name(), NamedTextColor.RED, TextDecoration.BOLD));

        Component strength = Component.text(" 军队: ", NamedTextColor.GOLD)
                .append(Component.text(armies + "支/" + soldiers + "人", NamedTextColor.WHITE));

        Component wars = Component.text(" | 战争: ", NamedTextColor.DARK_RED)
                .append(Component.text(nation.getWarCount() + "场", NamedTextColor.RED));

        Component allies = Component.text(" | 联盟: ", NamedTextColor.GREEN)
                .append(Component.text(nation.getAllyCount() + "个", NamedTextColor.WHITE));

        Component territory = Component.text(" | 领土: ", NamedTextColor.AQUA)
                .append(Component.text(nation.territoryCount() + "块", NamedTextColor.WHITE));

        return header.append(strength).append(wars).append(allies).append(territory);
    }

    /**
     * 计算进度条进度
     */
    private float calculateProgress(Nation nation) {
        // 基于国库占最大容量的比例
        BigDecimal treasury = treasuryService != null ?
                treasuryService.balance(nation.id()) : BigDecimal.ZERO;
        BigDecimal maxTreasury = new BigDecimal("1000000"); // 假设最大国库100万
        float treasuryRatio = treasury.divide(maxTreasury, 2, java.math.RoundingMode.HALF_UP).floatValue();

        // 基于军队满员率
        int soldiers = armyService != null ?
                armyService.getNationArmies(nation.id().uuid()).stream()
                        .mapToInt(a -> a.soldiers())
                        .sum() : 0;
        int maxSoldiers = 1000; // 假设最大兵力1000
        float armyRatio = Math.min(1.0f, (float) soldiers / maxSoldiers);

        // 返回综合指标
        return Math.min(1.0f, (treasuryRatio + armyRatio) / 2.0f);
    }

    /**
     * 获取下一个颜色
     */
    private BossBar.Color getNextColor() {
        BossBar.Color[] colors = {
                BossBar.Color.PINK,
                BossBar.Color.BLUE,
                BossBar.Color.GREEN,
                BossBar.Color.RED,
                BossBar.Color.PURPLE,
                BossBar.Color.WHITE,
                BossBar.Color.YELLOW
        };
        int index = colorIndex.getAndIncrement() % colors.length;
        return colors[index];
    }

    /**
     * 格式化金钱
     */
    private String formatMoney(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("1000000")) >= 0) {
            return amount.divide(new BigDecimal("1000000"), 1, java.math.RoundingMode.HALF_UP) + "M";
        } else if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            return amount.divide(new BigDecimal("1000"), 1, java.math.RoundingMode.HALF_UP) + "K";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * 启动定时更新任务
     */
    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (playerStatusBars.containsKey(player.getUniqueId())) {
                        updateStatusBar(player);
                    }
                }
            }
        }.runTaskTimer(plugin, updateInterval, updateInterval);
    }

    /**
     * 设置显示模式
     */
    public void setDisplayMode(NationStatusBarMode mode) {
        this.displayMode = mode;
        refreshAllStatusBars();
    }

    /**
     * 获取当前显示模式
     */
    public NationStatusBarMode getDisplayMode() {
        return displayMode;
    }

    /**
     * 设置更新间隔
     */
    public void setUpdateInterval(int ticks) {
        this.updateInterval = Math.max(20, ticks); // 最小1秒
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        for (Map.Entry<UUID, BossBar> entry : playerStatusBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.hideBossBar(entry.getValue());
            }
        }
        playerStatusBars.clear();
    }

    /**
     * 状态栏显示模式
     */
    public enum NationStatusBarMode {
        STANDARD,   // 标准模式：国家名|国库|领土|军队|成员
        DETAILED,   // 详细模式：多行显示所有信息
        COMPACT,    // 紧凑模式：最小化显示
        MILITARY    // 军事模式：侧重军事信息
    }
}
