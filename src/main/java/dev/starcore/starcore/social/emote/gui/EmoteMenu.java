package dev.starcore.starcore.social.emote.gui;

import java.util.concurrent.ConcurrentHashMap;
import dev.starcore.starcore.social.emote.EmoteDefinition;
import dev.starcore.starcore.social.emote.EmoteService;
import dev.starcore.starcore.social.emote.EmoteCooldownManager;
import dev.starcore.starcore.social.emote.CustomEmoteManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 动作菜单 GUI
 */
public class EmoteMenu {
    private static final int SLOT_PER_PAGE = 45;
    private static final int[] ACTION_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34, 37, 43};
    private static final int PREV_PAGE_SLOT = 48;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int CLOSE_SLOT = 49;

    private final EmoteService emoteService;
    private final EmoteCooldownManager cooldownManager;
    private final CustomEmoteManager customEmoteManager;
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    public EmoteMenu(EmoteService emoteService, EmoteCooldownManager cooldownManager,
                     CustomEmoteManager customEmoteManager) {
        this.emoteService = emoteService;
        this.cooldownManager = cooldownManager;
        this.customEmoteManager = customEmoteManager;
    }

    /**
     * 打开动作菜单
     */
    public void openMenu(Player player) {
        playerPages.put(player.getUniqueId(), 0);
        updateMenu(player, 0);
    }

    /**
     * 更新菜单
     */
    private void updateMenu(Player player, int page) {
        List<String> categories = List.of("greeting", "emotion", "social", "combat", "action", "custom");
        String category = categories.get(page % categories.size());

        List<EmoteDefinition> emotes = emoteService.getEmotesByCategory(category);
        if (emotes.isEmpty()) {
            emotes = new ArrayList<>(emoteService.getAllEmotes());
            category = "all";
        }

        String title = "=== 动作: " + category.toUpperCase() + " ===";
        Inventory gui = Bukkit.createInventory(null, 54, Component.text(title, NamedTextColor.GOLD));

        // 填充空槽位
        ItemStack filler = createFiller();
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        // 填充动作槽位
        int slotIndex = 0;
        for (EmoteDefinition emote : emotes) {
            if (slotIndex >= ACTION_SLOTS.length) break;
            if (!canUseEmote(player, emote)) continue;

            int slot = ACTION_SLOTS[slotIndex];
            ItemStack item = createEmoteItem(player, emote);
            gui.setItem(slot, item);
            slotIndex++;
        }

        // 设置分类导航
        gui.setItem(19, createCategoryItem("greeting", "问候", Material.ARMOR_STAND));
        gui.setItem(21, createCategoryItem("emotion", "情感", Material.RED_DYE));
        gui.setItem(23, createCategoryItem("social", "社交", Material.BEETROOT));
        gui.setItem(25, createCategoryItem("combat", "战斗", Material.DIAMOND_SWORD));
        gui.setItem(28, createCategoryItem("action", "动作", Material.BLAZE_POWDER));
        gui.setItem(34, createCategoryItem("custom", "自定义", Material.NETHER_STAR));

        // 分页按钮
        gui.setItem(PREV_PAGE_SLOT, createNavItem("previous", Material.ARROW, "上一页",
            page > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        gui.setItem(NEXT_PAGE_SLOT, createNavItem("next", Material.ARROW, "下一页",
            NamedTextColor.GREEN));

        // 关闭按钮
        gui.setItem(CLOSE_SLOT, createCloseItem());

        // 底部信息栏
        gui.setItem(45, createInfoItem("提示", "点击动作执行，聊天输入 :动作名: 也可触发"));
        gui.setItem(49, createInfoItem("冷却", "使用 /emote cooldown 查看冷却状态"));
        gui.setItem(53, createInfoItem("自定义", "使用 /emote create 创建自定义动作"));

        player.openInventory(gui);
    }

    /**
     * 创建动作物品
     */
    private ItemStack createEmoteItem(Player player, EmoteDefinition emote) {
        Material material = getMaterialForType(emote.getAnimationType());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        Component name = Component.text(emote.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD);
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("分类: " + emote.getCategory(), NamedTextColor.GRAY));
        lore.add(Component.text(emote.getDescription(), NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("冷却: " + cooldownManager.formatCooldownTime(emote.getCooldownSeconds()),
            emote.getCooldownSeconds() > 5 ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        lore.add(Component.text("持续: " + (emote.getDurationTicks() / 20.0) + "秒", NamedTextColor.GRAY));

        if (emote.requiresTarget()) {
            lore.add(Component.text("需要目标玩家", NamedTextColor.RED));
        }

        // 检查冷却状态
        if (cooldownManager.isOnCooldown(player.getUniqueId(), emote.getId())) {
            int remaining = cooldownManager.getRemainingCooldown(player.getUniqueId(), emote.getId());
            lore.add(Component.text("冷却中: " + cooldownManager.formatCooldownTime(remaining), NamedTextColor.RED));
        } else {
            lore.add(Component.text("点击执行", NamedTextColor.GREEN));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 根据动画类型获取材质
     */
    private Material getMaterialForType(String animationType) {
        return switch (animationType.toLowerCase()) {
            case "pose" -> Material.ARMOR_STAND;
            case "arm" -> Material.BOW;
            case "fullbody" -> Material.PLAYER_HEAD;
            case "particle" -> Material.FIREWORK_ROCKET;
            default -> Material.BLAZE_POWDER;
        };
    }

    /**
     * 检查玩家是否可以使用动作
     */
    private boolean canUseEmote(Player player, EmoteDefinition emote) {
        return emote.getPermission().isEmpty() ||
               player.hasPermission(emote.getPermission()) ||
               player.hasPermission("starcore.emote.*");
    }

    /**
     * 创建分类物品
     */
    private ItemStack createCategoryItem(String category, String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA));
        List<Component> lore = List.of(
            Component.text("查看 " + name + " 类动作", NamedTextColor.GRAY)
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建导航物品
     */
    private ItemStack createNavItem(String type, Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建关闭物品
     */
    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("关闭", NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建信息物品
     */
    private ItemStack createInfoItem(String title, String info) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.GOLD));
        meta.lore(List.of(Component.text(info, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建填充物品
     */
    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 处理菜单点击
     */
    public boolean handleClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return false;
        }

        Material type = item.getType();

        // 关闭按钮
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return true;
        }

        // 分类导航
        if (type == Material.ARMOR_STAND) {
            navigateToCategory(player, "greeting");
            return true;
        } else if (type == Material.RED_DYE) {
            navigateToCategory(player, "emotion");
            return true;
        } else if (type == Material.BEETROOT) {
            navigateToCategory(player, "social");
            return true;
        } else if (type == Material.DIAMOND_SWORD) {
            navigateToCategory(player, "combat");
            return true;
        } else if (type == Material.BLAZE_POWDER) {
            navigateToCategory(player, "action");
            return true;
        } else if (type == Material.NETHER_STAR) {
            navigateToCategory(player, "custom");
            return true;
        }

        // 分页导航
        if (slot == PREV_PAGE_SLOT && type == Material.ARROW) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            if (currentPage > 0) {
                playerPages.put(player.getUniqueId(), currentPage - 1);
                updateMenu(player, currentPage - 1);
            }
            return true;
        } else if (slot == NEXT_PAGE_SLOT && type == Material.ARROW) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            playerPages.put(player.getUniqueId(), currentPage + 1);
            updateMenu(player, currentPage + 1);
            return true;
        }

        // 检查是否是动作物品
        for (int actionSlot : ACTION_SLOTS) {
            if (slot == actionSlot && isEmoteItem(item)) {
                String emoteId = getEmoteIdFromItem(item);
                if (emoteId != null) {
                    emoteService.executeEmote(player, emoteId, null);
                    player.closeInventory();
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 导航到指定分类
     */
    private void navigateToCategory(Player player, String category) {
        List<String> categories = List.of("greeting", "emotion", "social", "combat", "action", "custom");
        int page = categories.indexOf(category);
        if (page >= 0) {
            playerPages.put(player.getUniqueId(), page);
            updateMenu(player, page);
        }
    }

    /**
     * 检查是否是动作物品
     */
    private boolean isEmoteItem(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
               !item.getItemMeta().displayName().toString().contains("关闭") &&
               !item.getItemMeta().displayName().toString().contains("上一页") &&
               !item.getItemMeta().displayName().toString().contains("下一页");
    }

    /**
     * 从物品获取动作ID
     */
    private String getEmoteIdFromItem(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return null;
        }

        Component name = item.getItemMeta().displayName();
        String nameStr = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(name);

        // 通过名称查找动作ID
        for (EmoteDefinition emote : emoteService.getAllEmotes()) {
            if (emote.getName().equals(nameStr.trim())) {
                return emote.getId();
            }
        }
        return null;
    }
}
