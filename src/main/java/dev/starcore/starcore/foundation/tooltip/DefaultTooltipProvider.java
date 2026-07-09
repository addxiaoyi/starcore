package dev.starcore.starcore.foundation.tooltip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEvent.Action;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认物品提示提供者
 * 为所有物品提供标准化的智能提示
 */
public class DefaultTooltipProvider implements TooltipProvider {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private final Map<String, ItemRarity> rarityMap;
    private final Map<Material, ItemRarity> materialRarityMap;

    // 缓存
    private final Map<String, CachedTooltip> tooltipCache;
    private final long cacheExpirationMs;

    public DefaultTooltipProvider() {
        this.rarityMap = new ConcurrentHashMap<>();
        this.materialRarityMap = new ConcurrentHashMap<>();
        this.tooltipCache = new ConcurrentHashMap<>();
        this.cacheExpirationMs = 60000; // 1分钟

        initializeRarityMappings();
    }

    private void initializeRarityMappings() {
        // 定义稀有度颜色
        rarityMap.put("common", new ItemRarity("普通", "#9E9E9E", NamedTextColor.GRAY));
        rarityMap.put("uncommon", new ItemRarity("优秀", "#4CAF50", NamedTextColor.GREEN));
        rarityMap.put("rare", new ItemRarity("稀有", "#2196F3", NamedTextColor.BLUE));
        rarityMap.put("epic", new ItemRarity("史诗", "#9C27B0", NamedTextColor.DARK_PURPLE));
        rarityMap.put("legendary", new ItemRarity("传说", "#FF9800", NamedTextColor.GOLD));
        rarityMap.put("mythic", new ItemRarity("神话", "#F44336", NamedTextColor.RED));

        // 材质稀有度映射
        // 传说级
        materialRarityMap.put(Material.NETHERITE_INGOT, rarityMap.get("legendary"));
        materialRarityMap.put(Material.NETHERITE_HELMET, rarityMap.get("legendary"));
        materialRarityMap.put(Material.NETHERITE_CHESTPLATE, rarityMap.get("legendary"));
        materialRarityMap.put(Material.NETHERITE_LEGGINGS, rarityMap.get("legendary"));
        materialRarityMap.put(Material.NETHERITE_BOOTS, rarityMap.get("legendary"));
        materialRarityMap.put(Material.NETHERITE_SWORD, rarityMap.get("legendary"));
        materialRarityMap.put(Material.NETHERITE_PICKAXE, rarityMap.get("legendary"));
        materialRarityMap.put(Material.DRAGON_HEAD, rarityMap.get("legendary"));
        materialRarityMap.put(Material.DRAGON_EGG, rarityMap.get("legendary"));
        materialRarityMap.put(Material.NETHER_STAR, rarityMap.get("legendary"));

        // 史诗级
        materialRarityMap.put(Material.GOLDEN_APPLE, rarityMap.get("epic"));
        materialRarityMap.put(Material.ENCHANTED_GOLDEN_APPLE, rarityMap.get("epic"));
        materialRarityMap.put(Material.ELYTRA, rarityMap.get("epic"));
        materialRarityMap.put(Material.SHULKER_BOX, rarityMap.get("epic"));
        materialRarityMap.put(Material.DIAMOND, rarityMap.get("epic"));

        // 稀有级
        materialRarityMap.put(Material.IRON_INGOT, rarityMap.get("rare"));
        materialRarityMap.put(Material.DIAMOND_HELMET, rarityMap.get("rare"));
        materialRarityMap.put(Material.DIAMOND_CHESTPLATE, rarityMap.get("rare"));
        materialRarityMap.put(Material.DIAMOND_LEGGINGS, rarityMap.get("rare"));
        materialRarityMap.put(Material.DIAMOND_BOOTS, rarityMap.get("rare"));
        materialRarityMap.put(Material.DIAMOND_SWORD, rarityMap.get("rare"));
        materialRarityMap.put(Material.DIAMOND_PICKAXE, rarityMap.get("rare"));
        materialRarityMap.put(Material.BOW, rarityMap.get("rare"));
        materialRarityMap.put(Material.CROSSBOW, rarityMap.get("rare"));
        materialRarityMap.put(Material.TRIDENT, rarityMap.get("rare"));

        // 优秀级
        materialRarityMap.put(Material.GOLD_INGOT, rarityMap.get("uncommon"));
        materialRarityMap.put(Material.LAPIS_LAZULI, rarityMap.get("uncommon"));
        materialRarityMap.put(Material.EMERALD, rarityMap.get("uncommon"));
        materialRarityMap.put(Material.EXPERIENCE_BOTTLE, rarityMap.get("uncommon"));
    }

    @Override
    public boolean canHandle(@NotNull TooltipContext context) {
        // 始终可以处理，提供默认提示
        return true;
    }

    @Override
    public List<Component> provide(@NotNull TooltipContext context) {
        ItemStack item = context.getItem();
        Player player = context.getPlayer();

        // 检查缓存
        String cacheKey = generateCacheKey(item);
        CachedTooltip cached = tooltipCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.getComponents();
        }

        List<Component> lines = new ArrayList<>();

        // 获取稀有度
        ItemRarity rarity = getItemRarity(item);
        TextColor rarityColor = rarity != null ? rarity.color() : NamedTextColor.WHITE;

        // 1. 物品名称（带稀有度颜色）
        Component itemName = context.isNamed()
            ? Component.text(context.getCustomName()).color(rarityColor).decoration(TextDecoration.ITALIC, false)
            : Component.text(getLocalizedName(item.getType())).color(rarityColor).decoration(TextDecoration.ITALIC, false);
        lines.add(itemName);

        // 2. 稀有度标签（如果有）
        if (rarity != null) {
            Component rarityTag = Component.text("[" + rarity.name() + "]")
                .color(rarityColor)
                .decoration(TextDecoration.OBFUSCATED, false)
                .hoverEvent(HoverEvent.showText(Component.text("稀有度: " + rarity.name())
                    .color(rarity.color())));
            lines.add(Component.text(" ").append(rarityTag));
        }

        // 3. 物品材质ID
        lines.add(Component.text("ID: " + context.getMaterialKey())
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));

        // 4. 数量
        if (context.getAmount() > 1) {
            lines.add(Component.text("数量: " + context.getAmount())
                .color(NamedTextColor.GRAY));
        }

        // 5. 耐久度
        if (context.isShowDurability() && item.getType().getMaxDurability() > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                int damage = damageable.getDamage();
                int maxDurability = item.getType().getMaxDurability();
                int remaining = maxDurability - damage;
                int percent = (remaining * 100) / maxDurability;

                TextColor durColor = percent > 50 ? NamedTextColor.GREEN :
                                     percent > 20 ? NamedTextColor.YELLOW :
                                     NamedTextColor.RED;

                lines.add(Component.text("耐久度: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(remaining + "/" + maxDurability)
                        .color(durColor)));
            }
        }

        // 6. 附魔信息
        if (context.hasEnchantments()) {
            lines.add(Component.text(""));
            lines.add(Component.text("附魔:")
                .color(NamedTextColor.DARK_PURPLE)
                .decoration(TextDecoration.BOLD, true));

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    String enchantName = getEnchantmentName(entry.getKey());
                    String level = toRoman(entry.getValue());
                    lines.add(Component.text("  " + enchantName + " " + level)
                        .color(NamedTextColor.AQUA));
                }
            }
        }

        // 7. 自定义Lore
        if (context.hasLore() && context.isShowLore()) {
            lines.add(Component.text(""));
            for (String loreLine : context.getLore()) {
                // 解析颜色代码
                Component loreComponent = parseColoredText(loreLine);
                lines.add(loreComponent.decoration(TextDecoration.ITALIC, false));
            }
        }

        // 8. 特殊物品提示
        if (context.isSpecialItem()) {
            lines.add(Component.text(""));
            lines.add(Component.text("⚠ 特殊物品")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true));
        }

        // 9. 无法破坏标记
        if (context.isUnbreakable()) {
            lines.add(Component.text("无法破坏")
                .color(NamedTextColor.DARK_GRAY));
        }

        // 缓存结果
        CachedTooltip newCache = new CachedTooltip(lines, System.currentTimeMillis() + cacheExpirationMs);
        tooltipCache.put(cacheKey, newCache);

        // 清理过期缓存
        cleanupExpiredCache();

        return lines;
    }

    @Override
    public String getHotbarHint(@NotNull TooltipContext context) {
        ItemStack item = context.getItem();
        StringBuilder hint = new StringBuilder();

        // 获取稀有度前缀
        ItemRarity rarity = getItemRarity(item);
        if (rarity != null) {
            hint.append("[").append(rarity.name()).append("] ");
        }

        // 物品名称
        if (context.isNamed()) {
            hint.append(context.getCustomName());
        } else {
            hint.append(getLocalizedName(item.getType()));
        }

        // 数量
        if (context.getAmount() > 1) {
            hint.append(" x").append(context.getAmount());
        }

        // 特殊标记
        if (context.hasEnchantments()) {
            hint.append(" ✧");
        }

        if (context.isUnbreakable()) {
            hint.append(" ∞");
        }

        return hint.toString();
    }

    @Override
    public boolean isExclusive() {
        return false; // 不独占，允许其他提供者添加额外信息
    }

    @Override
    public int getPriority() {
        return 0; // 最低优先级，最后处理
    }

    @Override
    public String getId() {
        return "default-tooltip-provider";
    }

    @Override
    public String getName() {
        return "默认物品提示";
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取物品稀有度
     */
    @Nullable
    private ItemRarity getItemRarity(@NotNull ItemStack item) {
        // 首先检查材质映射
        ItemRarity rarity = materialRarityMap.get(item.getType());
        if (rarity != null) {
            return rarity;
        }

        // 检查附魔等级（高附魔 = 高稀有度）
        ItemMeta meta = item.getItemMeta();
        if (meta != null && !meta.getEnchants().isEmpty()) {
            int maxLevel = meta.getEnchants().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

            if (maxLevel >= 4) {
                return rarityMap.get("legendary");
            } else if (maxLevel >= 3) {
                return rarityMap.get("epic");
            } else if (maxLevel >= 2) {
                return rarityMap.get("rare");
            } else if (maxLevel >= 1) {
                return rarityMap.get("uncommon");
            }
        }

        return null;
    }

    /**
     * 获取物品的本地化名称
     */
    @NotNull
    private String getLocalizedName(@NotNull Material material) {
        return material.getKey().getKey()
            .replace("_", " ")
            .substring(0, 1).toUpperCase()
            + material.getKey().getKey().replace("_", " ").substring(1);
    }

    /**
     * 获取附魔名称（中文）
     */
    @NotNull
    private String getEnchantmentName(@NotNull Enchantment enchantment) {
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
    private String toRoman(int number) {
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

    /**
     * 解析带颜色代码的文本
     */
    @NotNull
    private Component parseColoredText(@NotNull String text) {
        // 处理 &#RRGGBB 格式
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        Component result = Component.empty();

        int lastEnd = 0;
        while (matcher.find()) {
            // 添加前面的纯文本
            if (matcher.start() > lastEnd) {
                result = result.append(Component.text(text.substring(lastEnd, matcher.start())));
            }

            // 添加带颜色的文本
            String hex = matcher.group(1);
            TextColor color = TextColor.fromCSSHexString("#" + hex);
            if (color != null) {
                result = result.append(Component.text(matcher.group()).color(color));
            } else {
                result = result.append(Component.text(matcher.group()));
            }

            lastEnd = matcher.end();
        }

        // 添加剩余文本
        if (lastEnd < text.length()) {
            result = result.append(Component.text(text.substring(lastEnd)));
        }

        return result;
    }

    /**
     * 生成缓存键
     */
    @NotNull
    private String generateCacheKey(@NotNull ItemStack item) {
        return item.getType().getKey().toString() + ":" +
               item.getAmount() + ":" +
               (item.hasItemMeta() ? item.getItemMeta().hashCode() : "0");
    }

    /**
     * 清理过期缓存
     */
    private void cleanupExpiredCache() {
        if (tooltipCache.size() > 1000) {
            long now = System.currentTimeMillis();
            tooltipCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }

    // ==================== 内部类 ====================

    /**
     * 物品稀有度
     */
    private record ItemRarity(String name, String hexColor, TextColor color) {
    }

    /**
     * 缓存的提示
     */
    private static class CachedTooltip {
        private final List<Component> components;
        private final long expirationTime;

        CachedTooltip(List<Component> components, long expirationTime) {
            this.components = new ArrayList<>(components);
            this.expirationTime = expirationTime;
        }

        List<Component> getComponents() {
            return new ArrayList<>(components);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}