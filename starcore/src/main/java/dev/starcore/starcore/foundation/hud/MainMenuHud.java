package dev.starcore.starcore.foundation.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.starcore.starcore.util.MessageUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * StarCore 主菜单 HUD
 *
 * 现代毛玻璃风格主菜单，展示：
 * - 多层渐变毛玻璃背景
 * - 动态粒子效果（可配置）
 * - 呼吸灯动画
 * - 国家/玩家信息显示
 * - 模块快捷入口
 */
public class MainMenuHud extends ModernHudMenu {

    // 玩家国家信息缓存
    private static final ConcurrentHashMap<UUID, PlayerNationInfo> nationInfoCache = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private PlayerNationInfo nationInfo;

    /**
     * 玩家国家信息
     */
    public static class PlayerNationInfo {
        public String nationName;
        public int memberCount;
        public int territoryCount;
        public double treasury;
        public String governmentType;
        public boolean hasNation;

        public PlayerNationInfo() {
            this.hasNation = false;
        }

        public PlayerNationInfo(String nationName, int memberCount, int territoryCount,
                               double treasury, String governmentType) {
            this.nationName = nationName;
            this.memberCount = memberCount;
            this.territoryCount = territoryCount;
            this.treasury = treasury;
            this.governmentType = governmentType;
            this.hasNation = true;
        }
    }

    private MainMenuHud(Player player, Plugin plugin, String menuId, Component title, int size, GlassPaneStyle style) {
        super(player, plugin, menuId, title, size, style);
        this.plugin = plugin;

        // 默认毛玻璃效果
        setParticlesEnabled(true);
        setBreathingEnabled(true);
    }

    public static MainMenuHud create(Player player, Plugin plugin) {
        return new MainMenuHud(player, plugin, "main-menu",
            Component.text("★ StarCore 国家管理系统 ★")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.OBFUSCATED, false),
            54, GlassPaneStyle.NIGHTMARE);
    }

    public static MainMenuHud create(Player player, Plugin plugin, GlassPaneStyle style) {
        return new MainMenuHud(player, plugin, "main-menu",
            Component.text("★ StarCore 国家管理系统 ★")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.OBFUSCATED, false),
            54, style);
    }

    /**
     * 设置玩家国家信息
     */
    public MainMenuHud setNationInfo(PlayerNationInfo info) {
        this.nationInfo = info;
        nationInfoCache.put(player.getUniqueId(), info);
        return this;
    }

    @Override
    protected void buildContent() {
        // 构建主菜单布局
        buildHeader();
        buildNationInfo();
        buildQuickActions();
        buildModuleGrid();
        buildFooter();
    }

    /**
     * 构建标题栏
     */
    private void buildHeader() {
        // 标题装饰行
        setSeparator(0, "═══════════════════════");

        // 副标题 - 欢迎信息
        Component welcome = Component.text()
            .content("欢迎, ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(player.getName()).color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
            .append(Component.text(" - 国王系统").color(NamedTextColor.GRAY))
            .build();

        setButton(4, Material.NETHER_STAR,
            welcome,
            List.of(
                Component.text("当前在线: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.valueOf(plugin.getServer().getOnlinePlayers().size())).color(NamedTextColor.GREEN))
            ));
    }

    /**
     * 构建国家信息区域
     */
    private void buildNationInfo() {
        if (nationInfo != null && nationInfo.hasNation) {
            // 有国家 - 显示国家信息
            int row = 1;

            // 国家旗帜图标
            setButton(row * 9 + 1, Material.BEACON,
                Component.text("国家: ").color(NamedTextColor.GOLD)
                    .append(Component.text(nationInfo.nationName).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true)),
                List.of(
                    Component.text("政体: ").color(NamedTextColor.GRAY)
                        .append(Component.text(nationInfo.governmentType).color(NamedTextColor.AQUA)),
                    Component.text("成员: ").color(NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(nationInfo.memberCount)).color(NamedTextColor.GREEN)),
                    Component.text("领土: ").color(NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(nationInfo.territoryCount)).color(NamedTextColor.AQUA)),
                    Component.text("国库: ").color(NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.2f", nationInfo.treasury)).color(NamedTextColor.GOLD))
                ),
                p -> { /* 打开国家详情 */ }
            );

            // 在线成员
            setButton(row * 9 + 2, Material.PLAYER_HEAD,
                Component.text("在线成员").color(NamedTextColor.GREEN),
                List.of(
                    Component.text("查看当前在线的国家成员").color(NamedTextColor.GRAY)
                ),
                p -> { /* 打开成员列表 */ }
            );

            // 国家管理
            setButton(row * 9 + 3, Material.COMPARATOR,
                Component.text("国家管理").color(NamedTextColor.LIGHT_PURPLE),
                List.of(
                    Component.text("管理国家设置和权限").color(NamedTextColor.GRAY)
                ),
                p -> { /* 打开国家管理 */ }
            );

            // 外交
            setButton(row * 9 + 4, Material.WRITABLE_BOOK,
                Component.text("外交关系").color(NamedTextColor.DARK_GREEN),
                List.of(
                    Component.text("查看和管理外交关系").color(NamedTextColor.GRAY)
                ),
                p -> { /* 打开外交菜单 */ }
            );

            // 战争
            setButton(row * 9 + 5, Material.IRON_SWORD,
                Component.text("军事行动").color(NamedTextColor.DARK_RED),
                List.of(
                    Component.text("管理军队和战争").color(NamedTextColor.GRAY)
                ),
                p -> { /* 打开军事菜单 */ }
            );

        } else {
            // 无国家 - 显示创建/加入提示
            int row = 1;

            setButton(row * 9 + 2, Material.BOOK,
                Component.text("你还没有加入国家").color(NamedTextColor.YELLOW),
                List.of(
                    Component.text("使用 ").color(NamedTextColor.GRAY)
                        .append(Component.text("/sc nation create <名称>").color(NamedTextColor.GOLD)),
                    Component.text("创建你自己的国家").color(NamedTextColor.GRAY),
                    Component.text(""),
                    Component.text("或使用 ").color(NamedTextColor.GRAY)
                        .append(Component.text("/sc nation join <名称>").color(NamedTextColor.GOLD)),
                    Component.text("加入现有国家").color(NamedTextColor.GRAY)
                ),
                null
            );

            setButton(row * 9 + 3, Material.MAP,
                Component.text("查看所有国家").color(NamedTextColor.AQUA),
                List.of(
                    Component.text("浏览服务器上的所有国家").color(NamedTextColor.GRAY)
                ),
                p -> { /* 打开国家列表 */ }
            );

            setButton(row * 9 + 4, Material.EMERALD,
                Component.text("创建国家").color(NamedTextColor.GREEN),
                List.of(
                    Component.text("创建属于你自己的国家").color(NamedTextColor.GRAY),
                    Component.text("成为一国之君").color(NamedTextColor.GOLD)
                ),
                p -> { /* 打开创建向导 */ }
            );
        }
    }

    /**
     * 构建快捷操作区
     */
    private void buildQuickActions() {
        int row = 2;

        // 个人设置
        setButton(row * 9 + 1, Material.PAPER,
            Component.text("个人设置").color(NamedTextColor.WHITE),
            List.of(
                Component.text("配置个人选项").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开个人设置 */ }
        );

        // 背包/仓库
        setButton(row * 9 + 2, Material.CHEST,
            Component.text("仓库管理").color(NamedTextColor.WHITE),
            List.of(
                Component.text("访问国家仓库").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开仓库 */ }
        );

        // 科技
        setButton(row * 9 + 3, Material.ENCHANTING_TABLE,
            Component.text("科技研究").color(NamedTextColor.DARK_PURPLE),
            List.of(
                Component.text("升级国家科技").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开科技树 */ }
        );

        // 政策
        setButton(row * 9 + 4, Material.BOOK,
            Component.text("国家政策").color(NamedTextColor.BLUE),
            List.of(
                Component.text("制定和管理国家政策").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开政策菜单 */ }
        );

        // 官职
        setButton(row * 9 + 5, Material.NAME_TAG,
            Component.text("官职任命").color(NamedTextColor.GOLD),
            List.of(
                Component.text("任命和管理官员").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开官职菜单 */ }
        );

        // 国库
        setButton(row * 9 + 6, Material.GOLD_INGOT,
            Component.text("国库管理").color(NamedTextColor.YELLOW),
            List.of(
                Component.text("管理国家财政").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开国库 */ }
        );
    }

    /**
     * 构建模块网格
     */
    private void buildModuleGrid() {
        int row = 3;

        // 第一行模块
        setButton(row * 9 + 1, Material.MAP,
            Component.text("领土管理").color(NamedTextColor.AQUA),
            List.of(
                Component.text("管理国家领土").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开领土管理 */ }
        );

        setButton(row * 9 + 2, Material.WRITABLE_BOOK,
            Component.text("决议系统").color(NamedTextColor.GREEN),
            List.of(
                Component.text("发起国家投票").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开决议 */ }
        );

        setButton(row * 9 + 3, Material.DIAMOND,
            Component.text("资源系统").color(NamedTextColor.AQUA),
            List.of(
                Component.text("资源采集和交易").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开资源 */ }
        );

        setButton(row * 9 + 4, Material.EMERALD,
            Component.text("贸易市场").color(NamedTextColor.GREEN),
            List.of(
                Component.text("与其他国家贸易").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开市场 */ }
        );

        setButton(row * 9 + 5, Material.NETHER_STAR,
            Component.text("蓝图系统").color(NamedTextColor.LIGHT_PURPLE),
            List.of(
                Component.text("建造高级结构").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开蓝图 */ }
        );

        setButton(row * 9 + 6, Material.BEACON,
            Component.text("战役系统").color(NamedTextColor.YELLOW),
            List.of(
                Component.text("指挥大型战役").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开战役 */ }
        );

        // 第二行模块
        row = 4;

        setButton(row * 9 + 1, Material.TORCH,
            Component.text("每日任务").color(NamedTextColor.GOLD),
            List.of(
                Component.text("完成日常获取奖励").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开任务 */ }
        );

        setButton(row * 9 + 2, Material.GOLDEN_APPLE,
            Component.text("成就系统").color(NamedTextColor.GOLD),
            List.of(
                Component.text("解锁各种成就").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开成就 */ }
        );

        setButton(row * 9 + 3, Material.ENDER_EYE,
            Component.text("社交系统").color(NamedTextColor.DARK_PURPLE),
            List.of(
                Component.text("好友、邮件等功能").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开社交 */ }
        );

        setButton(row * 9 + 4, Material.CLOCK,
            Component.text("赛季系统").color(NamedTextColor.AQUA),
            List.of(
                Component.text("查看当前赛季进度").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开赛季 */ }
        );

        setButton(row * 9 + 5, Material.HEART_OF_THE_SEA,
            Component.text("天气战术").color(NamedTextColor.BLUE),
            List.of(
                Component.text("利用天气作战").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开天气 */ }
        );

        setButton(row * 9 + 6, Material.CAKE,
            Component.text("锦标赛").color(NamedTextColor.LIGHT_PURPLE),
            List.of(
                Component.text("参与锦标赛活动").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开锦标赛 */ }
        );
    }

    /**
     * 构建底部信息栏
     */
    private void buildFooter() {
        // 分隔线
        setSeparator(45, "─────────────────────");

        // 版本信息
        setButton(47, Material.BOOK,
            Component.text("v0.1.0").color(NamedTextColor.DARK_GRAY),
            List.of(
                Component.text("StarCore 国家系统").color(NamedTextColor.GRAY)
            )
        );

        // 帮助
        setButton(48, Material.BOOKSHELF,
            Component.text("帮助").color(NamedTextColor.AQUA),
            List.of(
                Component.text("/sc help - 获取帮助").color(NamedTextColor.GRAY)
            ),
            p -> { p.closeInventory(); p.sendMessage(MessageUtil.colorize("&6&l★ &e发送 help 到聊天获取帮助")); }
        );

        // 设置
        setButton(50, Material.REPEATER,
            Component.text("设置").color(NamedTextColor.YELLOW),
            List.of(
                Component.text("个性化菜单样式").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开设置菜单 */ }
        );

        // 排行榜
        setButton(51, Material.BOOK,
            Component.text("排行榜").color(NamedTextColor.GOLD),
            List.of(
                Component.text("查看各类排行榜").color(NamedTextColor.GRAY)
            ),
            p -> { /* 打开排行榜 */ }
        );

        // 关于
        setButton(52, Material.NETHER_STAR,
            Component.text("关于").color(NamedTextColor.DARK_PURPLE),
            List.of(
                Component.text("StarCore v0.1.0").color(NamedTextColor.GRAY),
                Component.text("Minecraft 国家战略引擎").color(NamedTextColor.GRAY)
            )
        );
    }

    @Override
    protected void buildNavigationBar() {
        // 覆盖默认导航栏
        // 不在底部添加导航按钮，因为我们已经自定义了底部区域
    }

    // ==================== 工具方法 ====================

    /**
     * 获取缓存的国家信息
     */
    public static PlayerNationInfo getCachedNationInfo(UUID playerId) {
        return nationInfoCache.get(playerId);
    }

    /**
     * 更新缓存的国家信息
     */
    public static void updateNationInfoCache(UUID playerId, PlayerNationInfo info) {
        nationInfoCache.put(playerId, info);
    }

    /**
     * 清除缓存
     */
    public static void clearCache(UUID playerId) {
        nationInfoCache.remove(playerId);
    }

    /**
     * 清除所有缓存
     */
    public static void clearAllCache() {
        nationInfoCache.clear();
    }
}