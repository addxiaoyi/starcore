package dev.starcore.starcore.foundation.tooltip;

import dev.starcore.starcore.module.nation.NationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 国家系统物品提示提供者
 * 为国家相关物品提供智能提示
 */
public class NationTooltipProvider implements TooltipProvider {

    private final Map<String, Component> nationFlags;
    private final NationService nationService;

    /**
     * 构造函数 (用于 TooltipModule 注入)
     */
    public NationTooltipProvider(@Nullable NationService nationService) {
        this.nationFlags = new ConcurrentHashMap<>();
        this.nationService = nationService;
        initializeNationFlags();
    }

    private void initializeNationFlags() {
        nationFlags.put("alliance", Component.text("[联盟]").color(NamedTextColor.GREEN));
        nationFlags.put("war", Component.text("[宣战]").color(NamedTextColor.RED));
        nationFlags.put("neutral", Component.text("[中立]").color(NamedTextColor.GRAY));
        nationFlags.put("truce", Component.text("[停战]").color(NamedTextColor.YELLOW));
    }

    @Override
    public boolean canHandle(@NotNull TooltipContext context) {
        ItemStack item = context.getItem();
        Material material = item.getType();

        String customName = context.getCustomName();
        List<String> lore = context.getLore();

        if (customName != null) {
            if (customName.contains("国家") || customName.contains("王国") ||
                customName.contains("联盟") || customName.contains("军旗") ||
                customName.contains("圣旨") || customName.contains("虎符")) {
                return true;
            }
        }

        for (String line : lore) {
            if (line.contains("国家") || line.contains("王国") ||
                line.contains("联盟") || line.contains("军旗")) {
                return true;
            }
        }

        // 检查特定材质
        return material == Material.WHITE_BANNER ||
               material == Material.ORANGE_BANNER ||
               material == Material.MAGENTA_BANNER ||
               material == Material.LIGHT_BLUE_BANNER;
    }

    @Override
    public List<Component> provide(@NotNull TooltipContext context) {
        List<Component> lines = new ArrayList<>();

        Player player = context.getPlayer();
        ItemStack item = context.getItem();

        // 物品名称
        String customName = context.getCustomName();
        Component name = customName != null
            ? Component.text(customName).color(NamedTextColor.GOLD)
            : Component.text(getMaterialName(item.getType())).color(NamedTextColor.GOLD);
        lines.add(name);

        // 分隔线
        lines.add(Component.text("══════════════════").color(NamedTextColor.DARK_GRAY));

        // 类型识别
        String itemType = identifyNationItem(item, customName);
        lines.add(Component.text("类型: ").color(NamedTextColor.GRAY)
            .append(Component.text(itemType).color(NamedTextColor.AQUA)));

        // 玩家国家信息
        String playerNation = getPlayerNation(player);
        if (playerNation != null) {
            lines.add(Component.text("所属国家: ").color(NamedTextColor.GRAY)
                .append(Component.text(playerNation).color(NamedTextColor.GOLD)));
        }

        // 物品效果
        lines.add(Component.text(""));
        lines.add(Component.text("效果:").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        lines.addAll(getItemEffects(item, itemType));

        // 使用条件
        lines.add(Component.text(""));
        lines.add(Component.text("使用条件:").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        lines.add(Component.text("  - 必须是国家成员").color(NamedTextColor.WHITE));
        lines.add(Component.text("  - 需要相应权限").color(NamedTextColor.WHITE));

        // 快捷键提示
        lines.add(Component.text(""));
        lines.add(Component.text("提示: ").color(NamedTextColor.GREEN)
            .append(Component.text("右键使用，左键查看详情").color(NamedTextColor.GRAY)));

        return lines;
    }

    @Override
    public String getHotbarHint(@NotNull TooltipContext context) {
        String customName = context.getCustomName();
        if (customName != null) {
            return "[国家] " + customName;
        }
        return "[国家] " + identifyNationItem(context.getItem(), null);
    }

    @Override
    public boolean isExclusive() {
        return false;
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public String getId() {
        return "nation-tooltip-provider";
    }

    @Override
    public String getName() {
        return "国家系统物品提示";
    }

    // ==================== 辅助方法 ====================

    private String identifyNationItem(@NotNull ItemStack item, @Nullable String customName) {
        Material material = item.getType();

        if (material == Material.WHITE_BANNER || material.name().contains("BANNER")) {
            if (customName != null) {
                if (customName.contains("军旗")) return "军旗";
                if (customName.contains("国旗")) return "国旗";
                if (customName.contains("战旗")) return "战旗";
            }
            return "旗帜";
        }

        if (customName != null) {
            if (customName.contains("圣旨")) return "圣旨";
            if (customName.contains("虎符")) return "虎符";
            if (customName.contains("官印")) return "官印";
            if (customName.contains("令牌")) return "令牌";
            if (customName.contains("盟约")) return "盟约书";
            if (customName.contains("宣战")) return "宣战书";
            if (customName.contains("停战")) return "停战协议";
        }

        return "国家物品";
    }

    private List<Component> getItemEffects(@NotNull ItemStack item, @NotNull String itemType) {
        List<Component> effects = new ArrayList<>();

        switch (itemType) {
            case "军旗" -> {
                effects.add(Component.text("  - 放置后显示国家领土范围").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 友方玩家可见").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 敌方攻击时显示").color(NamedTextColor.WHITE));
            }
            case "圣旨" -> {
                effects.add(Component.text("  - 发布国家公告").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 所有成员收到通知").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 仅君主/丞相可用").color(NamedTextColor.WHITE));
            }
            case "虎符" -> {
                effects.add(Component.text("  - 调动所属军队").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 需要军权权限").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 可指定调动目标").color(NamedTextColor.WHITE));
            }
            case "盟约书" -> {
                effects.add(Component.text("  - 与他国建立联盟").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 共享领土保护").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 军事援助协议").color(NamedTextColor.WHITE));
            }
            case "宣战书" -> {
                effects.add(Component.text("  - 正式向他国宣战").color(NamedTextColor.RED));
                effects.add(Component.text("  - 开启领土争夺").color(NamedTextColor.RED));
                effects.add(Component.text("  - 30分钟内可撤销").color(NamedTextColor.YELLOW));
            }
            case "停战协议" -> {
                effects.add(Component.text("  - 结束当前战争").color(NamedTextColor.GREEN));
                effects.add(Component.text("  - 重新定义边界").color(NamedTextColor.WHITE));
                effects.add(Component.text("  - 可能需要赔偿").color(NamedTextColor.YELLOW));
            }
            default -> {
                effects.add(Component.text("  - 国家系统特殊物品").color(NamedTextColor.WHITE));
            }
        }

        return effects;
    }

    /**
     * 获取玩家所属国家名称
     */
    @Nullable
    private String getPlayerNation(@NotNull Player player) {
        if (nationService == null) {
            return null;
        }
        try {
            return nationService.nationOf(player.getUniqueId())
                .map(nation -> nation.name())
                .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取材质显示名称
     */
    @NotNull
    private String getMaterialName(@NotNull Material material) {
        String key = material.getKey().getKey();
        String formatted = key.replace("_", " ");
        if (formatted.isEmpty()) return material.name();
        return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
    }
}
