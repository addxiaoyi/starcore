package dev.starcore.starcore.title.gui;

import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.gui.ButtonFactory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.title.PlayerTitle;
import dev.starcore.starcore.title.Title;
import dev.starcore.starcore.title.TitleModule;
import dev.starcore.starcore.title.TitleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Title system GUI menu
 */
public final class TitleMenu {

    public static final String MAIN_MENU_TITLE = "Title Center";
    public static final String MY_TITLES_TITLE = "My Titles";
    public static final String SHOP_TITLE = "Title Shop";

    private final TitleModule titleModule;
    private final TitleService titleService;
    private final NationService nationService;
    private final GuiAnimationManager animationManager;
    private final SoundFeedbackManager soundManager;

    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    public TitleMenu(
            TitleModule titleModule,
            TitleService titleService,
            NationService nationService,
            GuiAnimationManager animationManager,
            SoundFeedbackManager soundManager
    ) {
        this.titleModule = titleModule;
        this.titleService = titleService;
        this.nationService = nationService;
        this.animationManager = animationManager;
        this.soundManager = soundManager;
    }

    /**
     * Open main title menu
     */
    public void openMainMenu(Player player) {
        if (animationManager != null) {
            animationManager.playMenuOpenAnimation(player, "Title System");
        }

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(MAIN_MENU_TITLE));
        fillBorder(inv, Material.RED_STAINED_GLASS_PANE);

        inv.setItem(4, createTitleItem("Title Center"));

        // 同步获取玩家称号数据（用于填充 UI）
        PlayerTitle playerData = getPlayerDataSync(player);
        String equippedTitle = playerData.getEquippedTitle().orElse(null);
        int ownedCount = playerData.getUnlockedTitles().size();

        inv.setItem(19, createEquippedTitleItem(player, equippedTitle));

        inv.setItem(21, createStatItem(
            Material.GOLD_INGOT, "My Titles",
            String.valueOf(ownedCount)));

        inv.setItem(28, ButtonFactory.createStyledButton(
            "My Titles", Material.PAPER,
            ButtonFactory.BUTTON_STYLE_SUCCESS, "View your titles"));

        inv.setItem(30, ButtonFactory.createStyledButton(
            "Title Shop", Material.VILLAGER_SPAWN_EGG,
            ButtonFactory.BUTTON_STYLE_PRIMARY, "Buy new titles"));

        inv.setItem(32, ButtonFactory.createStyledButton(
            "Preview", Material.BOOK,
            ButtonFactory.BUTTON_STYLE_INFO, "Preview all titles"));

        player.openInventory(inv);
    }

    /**
     * 同步获取玩家称号数据（用于 GUI 初始化）
     */
    private PlayerTitle getPlayerDataSync(Player player) {
        if (titleService != null) {
            try {
                return titleService.getPlayerData(player.getUniqueId()).join();
            } catch (Exception e) {
                // 降级处理：返回空数据
                return new PlayerTitle(player.getUniqueId());
            }
        }
        return new PlayerTitle(player.getUniqueId());
    }

    private ItemStack createTitleItem(String name) {
        return ButtonFactory.createStyledButton(name, Material.NETHER_STAR, ButtonFactory.BUTTON_STYLE_PRIMARY);
    }

    private ItemStack createEquippedTitleItem(Player player, String equippedTitle) {
        Material material;
        Component name;
        List<Component> lore;

        if (equippedTitle != null && !equippedTitle.isEmpty()) {
            // 获取称号详细信息
            Title title = titleService.getTitle(equippedTitle).orElse(null);
            if (title != null) {
                material = title.icon();
                name = title.getFormattedName();
                lore = List.of(
                    Component.text("Currently Equipped", NamedTextColor.GREEN),
                    Component.text(""),
                    title.description()
                );
            } else {
                material = Material.NAME_TAG;
                name = Component.text(equippedTitle, NamedTextColor.GOLD);
                lore = List.of(
                    Component.text("Currently Equipped", NamedTextColor.GREEN),
                    Component.text(""),
                    Component.text("Click to view details", NamedTextColor.GRAY));
            }
        } else {
            material = Material.BARRIER;
            name = Component.text("No Title Equipped", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true);
            lore = List.of(
                Component.text("You haven't equipped any title", NamedTextColor.RED),
                Component.text(""),
                Component.text("Open My Titles to equip one!", NamedTextColor.GRAY));
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStatItem(Material material, String label, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(Component.text(value, NamedTextColor.GOLD)));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 填充边框
     */
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

    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        if (soundManager != null) {
            soundManager.playMenuSelect(player);
        }

        // Handle button clicks
        if (slot == 28) {
            // My Titles
            openMyTitles(player);
        } else if (slot == 30) {
            // Title Shop
            openTitleShop(player);
        } else if (slot == 32) {
            // Preview
            openPreview(player);
        } else if (slot == 19) {
            // Equipped Title - show details
            showEquippedTitle(player);
        }
    }

    private void showEquippedTitle(Player player) {
        player.sendMessage("§6=== 当前装备称号 ===");

        if (titleService != null) {
            try {
                titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
                    String equippedTitle = data.getEquippedTitle().orElse(null);
                    if (equippedTitle != null && !equippedTitle.isEmpty()) {
                        player.sendMessage("§7装备中: §e" + equippedTitle);
                    } else {
                        player.sendMessage("§7你没有装备任何称号");
                    }
                }).join();
            } catch (Exception e) {
                player.sendMessage("§c加载称号数据失败");
            }
        } else {
            player.sendMessage("§c称号服务不可用");
        }
    }

    private void openMyTitles(Player player) {
        player.sendMessage("§6=== 我的称号 ===");

        if (titleService != null) {
            try {
                titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
                    Set<String> unlockedTitles = data.getUnlockedTitles();
                    String equippedTitle = data.getEquippedTitle().orElse(null);

                    if (unlockedTitles == null || unlockedTitles.isEmpty()) {
                        player.sendMessage("§7你还没有解锁任何称号");
                        player.sendMessage("§7前往称号商店购买新称号!");
                    } else {
                        player.sendMessage("§7已解锁称号 (" + unlockedTitles.size() + "):");
                        for (String title : unlockedTitles) {
                            boolean equipped = title.equals(equippedTitle);
                            String prefix = equipped ? "§e" : "§a";
                            String suffix = equipped ? " §7[已装备]" : "";
                            player.sendMessage(prefix + "  " + title + suffix);
                        }
                        // 分隔
                        player.sendMessage("§7使用 §e/title equip <称号名> §7装备称号");
                        player.sendMessage("§7使用 §e/title unequip §7卸下称号");
                    }
                }).join();
            } catch (Exception e) {
                player.sendMessage("§c加载称号数据失败");
            }
        } else {
            player.sendMessage("§c称号服务不可用");
        }
    }

    private void openTitleShop(Player player) {
        // 称号商店功能 - 检查是否有可用商店
        player.sendMessage("§6=== 称号商店 ===");
        player.sendMessage("§e称号商店功能已开放！");
        // 分隔
        player.sendMessage("§7可用命令:");
        player.sendMessage("§a  /title shop §7- 打开称号商店");
        player.sendMessage("§a  /title list §7- 查看所有可用称号");
        player.sendMessage("§a  /title preview <称号> §7- 预览称号效果");
        // 分隔
        player.sendMessage("§7获得方式:");
        player.sendMessage("§a  - 完成成就任务");
        player.sendMessage("§a  - 参与活动");
        player.sendMessage("§a  - 排行榜奖励");
    }

    private void openPreview(Player player) {
        player.sendMessage("§6=== 称号预览 ===");

        if (titleService != null) {
            try {
                titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
                    Set<String> unlockedTitles = data.getUnlockedTitles();

                    if (unlockedTitles == null || unlockedTitles.isEmpty()) {
                        player.sendMessage("§7你还没有解锁任何称号");
                        // 分隔
                        player.sendMessage("§7可通过以下方式获得称号:");
                        player.sendMessage("§a  - 完成成就任务");
                        player.sendMessage("§a  - 参与活动");
                        player.sendMessage("§a  - 排行榜奖励");
                        return;
                    }

                    player.sendMessage("§7已解锁称号预览 (" + unlockedTitles.size() + "):");
                    // 分隔

                    for (String titleId : unlockedTitles) {
                        // 从注册表获取称号详细信息
                        Title title = titleService.getTitle(titleId).orElse(null);
                        if (title != null) {
                            String typeColor = title.type().getDefaultColor();
                            player.sendMessage(typeColor + "=== " + titleId + " ===");
                            player.sendMessage("  §7类型: " + typeColor + title.type().name());
                            player.sendMessage("  §7描述: " + title.color() + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(title.description()));
                        } else {
                            player.sendMessage("§e=== " + titleId + " ===");
                            player.sendMessage("  §7类型: §f未知");
                        }
                    }

                    // 分隔
                    player.sendMessage("§7使用 §e/title preview <称号名> §7详细预览");
                    player.sendMessage("§7使用 §e/title equip <称号名> §7装备称号");
                }).join();
            } catch (Exception e) {
                player.sendMessage("§c加载称号数据失败");
            }
        } else {
            player.sendMessage("§c称号服务不可用");
        }
    }
}
