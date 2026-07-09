package dev.starcore.starcore.foundation.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEvent.Action;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 增强版 GUI 按钮工厂
 * 提供统一的按钮创建、样式化和智能物品说明生成
 */
public final class SmartButtonFactory {

    // ==================== 按钮样式常量 ====================

    public static final String STYLE_PRIMARY = "primary";
    public static final String STYLE_SECONDARY = "secondary";
    public static final String STYLE_DANGER = "danger";
    public static final String STYLE_SUCCESS = "success";
    public static final String STYLE_INFO = "info";
    public static final String STYLE_DISABLED = "disabled";
    public static final String STYLE_WARNING = "warning";

    // ==================== 稀有度常量 ====================

    public static final String RARITY_COMMON = "common";
    public static final String RARITY_UNCOMMON = "uncommon";
    public static final String RARITY_RARE = "rare";
    public static final String RARITY_EPIC = "epic";
    public static final String RARITY_LEGENDARY = "legendary";
    public static final String RARITY_MYTHIC = "mythic";

    // ==================== 私有构造函数 ====================

    private SmartButtonFactory() {
    }

    // ==================== 基础按钮创建 ====================

    /**
     * 创建基础按钮
     */
    @NotNull
    public static ItemStack createButton(@NotNull String name, @NotNull Material material, @NotNull String... lore) {
        return createStyledButton(name, material, STYLE_PRIMARY, lore);
    }

    /**
     * 创建带稀有度的按钮
     */
    @NotNull
    public static ItemStack createRarityButton(@NotNull String name, @NotNull Material material,
                                                @NotNull String rarity, @NotNull String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 获取稀有度颜色
            TextColor color = getRarityColor(rarity);

            // 设置显示名称
            Component displayName = Component.text(name)
                .color(color)
                .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);

            // 构建描述列表
            List<Component> loreComponents = new ArrayList<>();

            // 添加稀有度标签
            loreComponents.add(Component.text("[ " + getRarityName(rarity) + " ]")
                .color(color)
                .decoration(TextDecoration.OBFUSCATED, false));

            // 添加描述
            for (String line : lore) {
                loreComponents.add(Component.text(line).color(NamedTextColor.GRAY));
            }

            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建样式化按钮
     */
    @NotNull
    public static ItemStack createStyledButton(@NotNull String name, @NotNull Material material,
                                                @NotNull String style, @NotNull String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            TextColor color = getStyleColor(style);
            Component displayName = Component.text(name).color(color).decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);

            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line).color(NamedTextColor.GRAY));
            }
            meta.lore(loreComponents);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建带智能描述的按钮
     *
     * @param name 按钮名称
     * @param material 材质
     * @param style 样式
     * @param description 主要描述
     * @param stats 统计信息（如 "攻击力: 10"、"冷却: 5秒"）
     * @param hint 使用提示
     */
    @NotNull
    public static ItemStack createSmartButton(@NotNull String name, @NotNull Material material,
                                               @NotNull String style, @NotNull String description,
                                               @NotNull String[] stats, @Nullable String hint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            TextColor color = getStyleColor(style);

            // 显示名称
            Component displayName = Component.text(name)
                .color(color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);

            // 构建描述列表
            List<Component> loreComponents = new ArrayList<>();

            // 分隔线
            loreComponents.add(Component.text("─────────────────").color(NamedTextColor.DARK_GRAY));

            // 描述
            loreComponents.add(Component.text(description).color(NamedTextColor.WHITE));

            // 统计信息
            if (stats != null && stats.length > 0) {
                loreComponents.add(Component.text(""));
                loreComponents.add(Component.text("属性:").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
                for (String stat : stats) {
                    loreComponents.add(Component.text("  " + stat).color(NamedTextColor.AQUA));
                }
            }

            // 使用提示
            if (hint != null && !hint.isEmpty()) {
                loreComponents.add(Component.text(""));
                loreComponents.add(Component.text("提示: ").color(NamedTextColor.GREEN)
                    .append(Component.text(hint).color(NamedTextColor.GRAY)));
            }

            // 分隔线
            loreComponents.add(Component.text("─────────────────").color(NamedTextColor.DARK_GRAY));

            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建带进度条的按钮
     *
     * @param name 名称
     * @param material 材质
     * @param style 样式
     * @param current 当前值
     * @param max 最大值
     * @param label 标签
     */
    @NotNull
    public static ItemStack createProgressButton(@NotNull String name, @NotNull Material material,
                                                   @NotNull String style, int current, int max,
                                                   @NotNull String label) {
        int percentage = max > 0 ? (current * 100 / max) : 0;
        TextColor barColor = percentage >= 75 ? NamedTextColor.GREEN :
                            percentage >= 50 ? NamedTextColor.YELLOW :
                            percentage >= 25 ? NamedTextColor.GOLD :
                            NamedTextColor.RED;

        // 构建进度条
        StringBuilder progressBar = new StringBuilder();
        int bars = 12;
        int filled = (int) ((percentage / 100.0) * bars);

        progressBar.append("[");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                progressBar.append("█");
            } else {
                progressBar.append("░");
            }
        }
        progressBar.append("] ").append(percentage).append("%");

        return createStyledButton(
            name,
            material,
            style,
            label,
            progressBar.toString(),
            "进度: " + current + " / " + max
        );
    }

    // ==================== 导航按钮 ====================

    /**
     * 创建导航按钮（左箭头）
     */
    @NotNull
    public static ItemStack createPrevButton(@NotNull String label) {
        return createStyledButton("◀ " + label, Material.ARROW, STYLE_SECONDARY, "上一页");
    }

    /**
     * 创建导航按钮（右箭头）
     */
    @NotNull
    public static ItemStack createNextButton(@NotNull String label) {
        return createStyledButton(label + " ▶", Material.ARROW, STYLE_SECONDARY, "下一页");
    }

    /**
     * 创建返回按钮
     */
    @NotNull
    public static ItemStack createBackButton() {
        return createStyledButton("↩ 返回上级", Material.BARRIER, STYLE_SECONDARY, "返回上一个菜单");
    }

    /**
     * 创建关闭按钮
     */
    @NotNull
    public static ItemStack createCloseButton() {
        return createStyledButton("✖ 关闭", Material.BARRIER, STYLE_DANGER, "关闭此菜单");
    }

    /**
     * 创建确认按钮
     */
    @NotNull
    public static ItemStack createConfirmButton(@NotNull String action) {
        return createStyledButton("✓ 确认 " + action, Material.LIME_CONCRETE, STYLE_SUCCESS, "点击确认");
    }

    /**
     * 创建取消按钮
     */
    @NotNull
    public static ItemStack createCancelButton() {
        return createStyledButton("✖ 取消", Material.RED_CONCRETE, STYLE_DANGER, "点击取消");
    }

    // ==================== 状态按钮 ====================

    /**
     * 创建状态按钮
     */
    @NotNull
    public static ItemStack createStatusButton(@NotNull String label, boolean enabled, @NotNull String description) {
        String style = enabled ? STYLE_SUCCESS : STYLE_DISABLED;
        Material material = enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String status = enabled ? "[启用]" : "[禁用]";
        return createStyledButton(status + " " + label, material, style, description);
    }

    /**
     * 创建开关按钮
     */
    @NotNull
    public static ItemStack createToggleButton(@NotNull String name, boolean enabled,
                                                @NotNull String description) {
        Material material = enabled ? Material.LEVER : Material.STONE_BUTTON;
        String stateText = enabled ? "§aON" : "§cOFF";
        return createStyledButton(stateText + " " + name, material, enabled ? STYLE_SUCCESS : STYLE_DANGER, description);
    }

    // ==================== 信息按钮 ====================

    /**
     * 创建信息按钮
     */
    @NotNull
    public static ItemStack createInfoButton(@NotNull String title, @NotNull String... info) {
        return createStyledButton(title, Material.BOOK, STYLE_INFO, info);
    }

    /**
     * 创建统计按钮（标签 + 数值）
     */
    @NotNull
    public static ItemStack createStatButton(@NotNull Material material, @NotNull String label, @NotNull String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(label)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(value)
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建玩家头颅按钮
     */
    @NotNull
    public static ItemStack createPlayerHead(@NotNull Player player) {
        return createPlayerHead(player.getName());
    }

    /**
     * 创建玩家头颅按钮
     */
    @NotNull
    public static ItemStack createPlayerHead(@NotNull String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(playerName)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true));
            head.setItemMeta(meta);
        }

        return head;
    }

    // ==================== 边框和装饰 ====================

    /**
     * 创建边框
     */
    @NotNull
    public static ItemStack createBorder() {
        return createBorder(Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * 创建边框
     */
    @NotNull
    public static ItemStack createBorder(@NotNull Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").color(NamedTextColor.WHITE));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 创建彩色边框
     */
    @NotNull
    public static ItemStack createColorBorder(@NotNull String colorName, @NotNull TextColor color) {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(" ")
                .color(color)
                .hoverEvent(HoverEvent.showText(Component.text("边框颜色: " + colorName).color(color))));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 创建分隔符
     */
    @NotNull
    public static ItemStack createSeparator() {
        return createStyledButton("─".repeat(20), Material.IRON_BARS, STYLE_INFO);
    }

    // ==================== 分页按钮 ====================

    /**
     * 创建页码按钮
     */
    @NotNull
    public static ItemStack createPageButton(int page, int currentPage, int totalPages) {
        if (page == currentPage) {
            return createStyledButton("第 " + page + " 页",
                Material.NETHER_STAR, STYLE_PRIMARY,
                "当前页面: " + currentPage + "/" + totalPages);
        } else {
            return createStyledButton("第 " + page + " 页",
                Material.PAPER, STYLE_SECONDARY,
                "共 " + totalPages + " 页");
        }
    }

    /**
     * 创建首页按钮
     */
    @NotNull
    public static ItemStack createFirstPageButton() {
        return createStyledButton("⏮ 首页", Material.MAP, STYLE_SECONDARY, "跳转到第一页");
    }

    /**
     * 创建末页按钮
     */
    @NotNull
    public static ItemStack createLastPageButton(int totalPages) {
        return createStyledButton("末页 ⏭", Material.MAP, STYLE_SECONDARY, "跳转到最后一页 (第" + totalPages + "页)");
    }

    // ==================== 工具方法 ====================

    /**
     * 获取样式的颜色
     */
    @NotNull
    public static TextColor getStyleColor(@NotNull String style) {
        return switch (style) {
            case STYLE_PRIMARY -> NamedTextColor.GOLD;
            case STYLE_SECONDARY -> NamedTextColor.GRAY;
            case STYLE_DANGER -> NamedTextColor.RED;
            case STYLE_SUCCESS -> NamedTextColor.GREEN;
            case STYLE_INFO -> NamedTextColor.AQUA;
            case STYLE_WARNING -> NamedTextColor.YELLOW;
            case STYLE_DISABLED -> NamedTextColor.DARK_GRAY;
            default -> NamedTextColor.WHITE;
        };
    }

    /**
     * 获取稀有度颜色
     */
    @NotNull
    public static TextColor getRarityColor(@NotNull String rarity) {
        return switch (rarity) {
            case RARITY_COMMON -> NamedTextColor.GRAY;
            case RARITY_UNCOMMON -> NamedTextColor.GREEN;
            case RARITY_RARE -> NamedTextColor.BLUE;
            case RARITY_EPIC -> NamedTextColor.DARK_PURPLE;
            case RARITY_LEGENDARY -> NamedTextColor.GOLD;
            case RARITY_MYTHIC -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
    }

    /**
     * 获取稀有度名称
     */
    @NotNull
    public static String getRarityName(@NotNull String rarity) {
        return switch (rarity) {
            case RARITY_COMMON -> "普通";
            case RARITY_UNCOMMON -> "优秀";
            case RARITY_RARE -> "稀有";
            case RARITY_EPIC -> "史诗";
            case RARITY_LEGENDARY -> "传说";
            case RARITY_MYTHIC -> "神话";
            default -> "未知";
        };
    }

    /**
     * 应用样式到文本组件
     */
    @NotNull
    public static Component applyStyle(@NotNull String text, @NotNull String style) {
        return Component.text(text).color(getStyleColor(style)).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * 创建带样式的文本行
     */
    @NotNull
    public static Component createStyledLine(@NotNull String text, @NotNull String style) {
        return Component.text(text).color(getStyleColor(style));
    }

    /**
     * 创建带颜色和样式的文本行
     */
    @NotNull
    public static Component createStyledLine(@NotNull String text, @NotNull TextColor color) {
        return Component.text(text).color(color);
    }

    /**
     * 创建居中的分隔符
     */
    @NotNull
    public static Component createCenteredSeparator(@NotNull TextColor color) {
        return Component.text("═══════════════════").color(color);
    }

    /**
     * 创建小分隔符
     */
    @NotNull
    public static Component createSmallSeparator(@NotNull TextColor color) {
        return Component.text("─────────────").color(color);
    }

    /**
     * 创建点状分隔符
     */
    @NotNull
    public static Component createDotSeparator(@NotNull TextColor color) {
        return Component.text("· · · · · · · · ·").color(color);
    }

    /**
     * 添加悬停提示
     */
    @NotNull
    public static Component withHoverText(@NotNull Component component, @NotNull String hoverText) {
        return component.hoverEvent(HoverEvent.showText(Component.text(hoverText).color(NamedTextColor.YELLOW)));
    }

    /**
     * 添加点击提示
     */
    @NotNull
    public static Component withClickHint(@NotNull Component component, @NotNull String hint) {
        return component.hoverEvent(HoverEvent.showText(
            Component.text("点击: ").color(NamedTextColor.GRAY).append(Component.text(hint).color(NamedTextColor.WHITE))
        ));
    }

    // ==================== 物品信息生成 ====================

    /**
     * 生成物品的详细信息描述
     */
    @NotNull
    public static List<Component> generateItemDescription(@NotNull ItemStack item, @NotNull Player player) {
        List<Component> lines = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();

        // 物品名称
        Component name = meta != null && meta.hasDisplayName()
            ? meta.displayName()
            : Component.text(getMaterialName(item.getType()));
        lines.add(name);

        // 稀有度
        TextColor rarityColor = getAutoRarityColor(item);
        lines.add(Component.text("[ " + getAutoRarityName(item) + " ]").color(rarityColor));

        lines.add(Component.text(""));

        // 材质ID
        lines.add(Component.text("ID: ").color(NamedTextColor.GRAY)
            .append(Component.text(item.getType().getKey().toString()).color(NamedTextColor.DARK_GRAY)));

        // 数量
        if (item.getAmount() > 1) {
            lines.add(Component.text("数量: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(item.getAmount())).color(NamedTextColor.WHITE)));
        }

        // 耐久度
        if (item.getType().getMaxDurability() > 0 && meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int damage = damageable.getDamage();
            int max = item.getType().getMaxDurability();
            int remaining = max - damage;
            int percent = (remaining * 100) / max;

            TextColor durColor = percent > 50 ? NamedTextColor.GREEN :
                                percent > 20 ? NamedTextColor.YELLOW :
                                NamedTextColor.RED;

            lines.add(Component.text("耐久度: ").color(NamedTextColor.GRAY)
                .append(Component.text(remaining + "/" + max).color(durColor)));
        }

        // 附魔
        if (meta != null && !meta.getEnchants().isEmpty()) {
            lines.add(Component.text(""));
            lines.add(Component.text("附魔:").color(NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true));
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                String enchantName = getEnchantmentName(entry.getKey());
                lines.add(Component.text("  " + enchantName + " " + toRoman(entry.getValue())).color(NamedTextColor.AQUA));
            }
        }

        // 自定义Lore
        if (meta != null && meta.hasLore()) {
            lines.add(Component.text(""));
            for (Component loreLine : meta.lore()) {
                lines.add(loreLine.color(NamedTextColor.WHITE));
            }
        }

        return lines;
    }

    /**
     * 自动获取物品稀有度颜色
     */
    @NotNull
    private static TextColor getAutoRarityColor(@NotNull ItemStack item) {
        Material material = item.getType();
        ItemMeta meta = item.getItemMeta();

        // 材质稀有度
        if (isLegendaryMaterial(material)) {
            return NamedTextColor.GOLD;
        } else if (isEpicMaterial(material)) {
            return NamedTextColor.DARK_PURPLE;
        } else if (isRareMaterial(material)) {
            return NamedTextColor.BLUE;
        } else if (isUncommonMaterial(material)) {
            return NamedTextColor.GREEN;
        }

        // 附魔稀有度
        if (meta != null && !meta.getEnchants().isEmpty()) {
            int maxLevel = meta.getEnchants().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

            if (maxLevel >= 4) return NamedTextColor.GOLD;
            if (maxLevel >= 3) return NamedTextColor.DARK_PURPLE;
            if (maxLevel >= 2) return NamedTextColor.BLUE;
            if (maxLevel >= 1) return NamedTextColor.GREEN;
        }

        return NamedTextColor.GRAY;
    }

    /**
     * 自动获取物品稀有度名称
     */
    @NotNull
    private static String getAutoRarityName(@NotNull ItemStack item) {
        Material material = item.getType();
        ItemMeta meta = item.getItemMeta();

        // 材质稀有度
        if (isLegendaryMaterial(material)) return "传说";
        if (isEpicMaterial(material)) return "史诗";
        if (isRareMaterial(material)) return "稀有";
        if (isUncommonMaterial(material)) return "优秀";

        // 附魔稀有度
        if (meta != null && !meta.getEnchants().isEmpty()) {
            int maxLevel = meta.getEnchants().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

            if (maxLevel >= 4) return "传说";
            if (maxLevel >= 3) return "史诗";
            if (maxLevel >= 2) return "稀有";
            if (maxLevel >= 1) return "优秀";
        }

        return "普通";
    }

    private static boolean isLegendaryMaterial(Material material) {
        return material == Material.NETHERITE_INGOT ||
               material == Material.NETHERITE_HELMET ||
               material == Material.NETHERITE_CHESTPLATE ||
               material == Material.NETHERITE_LEGGINGS ||
               material == Material.NETHERITE_BOOTS ||
               material == Material.NETHERITE_SWORD ||
               material == Material.NETHERITE_PICKAXE ||
               material == Material.DRAGON_HEAD ||
               material == Material.DRAGON_EGG ||
               material == Material.NETHER_STAR;
    }

    private static boolean isEpicMaterial(Material material) {
        return material == Material.GOLDEN_APPLE ||
               material == Material.ENCHANTED_GOLDEN_APPLE ||
               material == Material.ELYTRA ||
               material == Material.DIAMOND;
    }

    private static boolean isRareMaterial(Material material) {
        return material == Material.DIAMOND_HELMET ||
               material == Material.DIAMOND_CHESTPLATE ||
               material == Material.DIAMOND_LEGGINGS ||
               material == Material.DIAMOND_BOOTS ||
               material == Material.DIAMOND_SWORD ||
               material == Material.DIAMOND_PICKAXE ||
               material == Material.BOW ||
               material == Material.CROSSBOW ||
               material == Material.TRIDENT;
    }

    private static boolean isUncommonMaterial(Material material) {
        return material == Material.GOLD_INGOT ||
               material == Material.LAPIS_LAZULI ||
               material == Material.EMERALD ||
               material == Material.EXPERIENCE_BOTTLE;
    }

    /**
     * 获取材质名称
     */
    @NotNull
    private static String getMaterialName(@NotNull Material material) {
        return material.getKey().getKey()
            .replace("_", " ")
            .substring(0, 1).toUpperCase()
            + material.getKey().getKey().replace("_", " ").substring(1);
    }

    /**
     * 获取附魔中文名称
     */
    @NotNull
    private static String getEnchantmentName(@NotNull Enchantment enchantment) {
        String key = enchantment.getKey().getKey().toLowerCase();
        return switch (key) {
            case "protection" -> "保护";
            case "fire_protection" -> "火焰保护";
            case "feather_falling" -> "摔落保护";
            case "blast_protection" -> "爆炸保护";
            case "projectile_protection" -> "弹射物保护";
            case "respiration" -> "水下呼吸";
            case "aqua_affinity" -> "水下速挖";
            case "thorns" -> "荆棘";
            case "depth_strider" -> "深海探索者";
            case "frost_walker" -> "冰霜行者";
            case "binding_curse" -> "绑定诅咒";
            case "swift_sneak" -> "快速潜行";
            case "mending" -> "经验修补";
            case "unbreaking" -> "耐久";
            case "efficiency" -> "效率";
            case "sharpness" -> "锋利";
            case "smite" -> "亡灵杀手";
            case "bane_of_arthropods" -> "节肢杀手";
            case "knockback" -> "击退";
            case "fire_aspect" -> "火焰附加";
            case "looting" -> "抢夺";
            case "sweeping" -> "横扫之刃";
            case "power" -> "力量";
            case "punch" -> "冲击";
            case "flame" -> "火焰";
            case "infinity" -> "无限";
            case "luck_of_the_sea" -> "海之眷顾";
            case "lure" -> "钓饵";
            case "loyalty" -> "忠诚";
            case "impaling" -> "穿刺";
            case "riptide" -> "激流";
            case "channeling" -> "引雷";
            case "multishot" -> "多重射击";
            case "quick_charge" -> "快速装填";
            case "piercing" -> "穿透";
            case "soul_speed" -> "灵魂疾行";
            case "vanishing_curse" -> "消失诅咒";
            default -> enchantment.getKey().getKey();
        };
    }

    /**
     * 数字转罗马数字
     */
    @NotNull
    private static String toRoman(int number) {
        if (number <= 0) return "0";
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }
}