package dev.starcore.starcore.module.dungeon.gui;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.dungeon.DungeonDefinition;
import dev.starcore.starcore.module.dungeon.DungeonDifficulty;
import dev.starcore.starcore.module.dungeon.DungeonService;
import dev.starcore.starcore.module.dungeon.DungeonServiceImpl;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 副本选择GUI
 */
public class DungeonGui implements InventoryHolder {
    private static final String GUI_TITLE = "§6§l副本选择";

    private final Player player;
    private final DungeonServiceImpl service;
    private final MessageService messages;
    private final Inventory inventory;
    private final Map<Integer, String> dungeonSlots;

    public DungeonGui(Player player, DungeonServiceImpl service, MessageService messages) {
        this.player = player;
        this.service = service;
        this.messages = messages;
        this.dungeonSlots = new HashMap<>();

        // 计算大小
        int size = Math.min(54, (service.getAllDungeons().size() / 9 + 1) * 9 + 9);

        this.inventory = Bukkit.createInventory(this, size, GUI_TITLE);
        open();
    }

    /**
     * 打开GUI
     */
    private void open() {
        populateItems();
        player.openInventory(inventory);
    }

    /**
     * 填充物品
     */
    private void populateItems() {
        inventory.clear();
        dungeonSlots.clear();

        int slot = 0;
        for (DungeonDefinition dungeon : service.getAllDungeons()) {
            if (slot >= inventory.getSize() - 9) break; // 保留最后一行

            ItemStack item = createDungeonItem(dungeon);
            inventory.setItem(slot, item);
            dungeonSlots.put(slot, dungeon.id());

            slot++;
        }

        // 添加导航和装饰物品
        fillNavigation(slot);

        // 添加关闭按钮
        inventory.setItem(inventory.getSize() - 1, createCloseButton());
    }

    /**
     * 创建副本物品
     */
    private ItemStack createDungeonItem(DungeonDefinition dungeon) {
        Material material = Material.valueOf(dungeon.icon());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            item.setType(Material.CHEST);
            meta = item.getItemMeta();
        }

        // 设置名称
        String difficultyColor = switch (dungeon.difficulty()) {
            case EASY -> "§a";
            case NORMAL -> "§e";
            case HARD -> "§c";
            case NIGHTMARE -> "§4";
        };

        meta.setDisplayName(difficultyColor + "§l" + dungeon.name());

        // 设置lore
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7难度: " + difficultyColor + dungeon.difficulty().getDisplayName());
        lore.add("§7玩家: §f" + dungeon.minPlayers() + "-" + dungeon.maxPlayers() + "人");
        lore.add("§7推荐等级: §f" + dungeon.recommendedLevel() + "+");
        lore.add("§7入场费: §f" + dungeon.entryFee() + " 金币");
        lore.add("§7房间数: §f" + dungeon.totalRooms());
        lore.add("");
        lore.add("§a点击进入副本");

        meta.setLore(lore);

        // 添加边框效果
        if (dungeon.difficulty() == DungeonDifficulty.NIGHTMARE) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 填充导航
     */
    private void fillNavigation(int startSlot) {
        Material borderMaterial = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack border = new ItemStack(borderMaterial);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);

        for (int i = startSlot; i < inventory.getSize() - 1; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, border);
            }
        }

        // 添加统计信息
        ItemStack stats = new ItemStack(Material.PAPER);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.setDisplayName("§6§l副本统计");
        List<String> statsLore = new ArrayList<>();
        statsLore.add("");
        statsLore.add("§7活跃实例: §f" + service.getActiveInstances().size());
        statsLore.add("§7副本总数: §f" + service.getAllDungeons().size());
        statsLore.add("");
        statsMeta.setLore(statsLore);
        stats.setItemMeta(statsMeta);
        inventory.setItem(inventory.getSize() - 9, stats);
    }

    /**
     * 创建关闭按钮
     */
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§l关闭");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7点击关闭此界面");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 处理点击
     */
    public void handleClick(int slot) {
        if (slot == inventory.getSize() - 1) {
            // 关闭按钮
            player.closeInventory();
            return;
        }

        if (slot == inventory.getSize() - 9) {
            // 统计按钮 - 不做处理
            return;
        }

        String dungeonId = dungeonSlots.get(slot);
        if (dungeonId != null) {
            player.closeInventory();
            service.tryEnterDungeon(player, dungeonId);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * GUI工厂
     */
    public static class Factory {
        private final DungeonServiceImpl service;
        private final MessageService messages;

        public Factory(DungeonServiceImpl service, MessageService messages) {
            this.service = service;
            this.messages = messages;
        }

        public void open(Player player) {
            new DungeonGui(player, service, messages);
        }
    }
}
