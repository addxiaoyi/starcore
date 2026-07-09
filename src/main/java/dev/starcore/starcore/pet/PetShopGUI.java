package dev.starcore.starcore.pet;

import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

/**
 * 宠物商店GUI
 */
public class PetShopGUI {
    private static final String SHOP_TITLE = "宠物商店";
    private static final int SHOP_SIZE = 54; // 6行

    private final PetService petService;
    private final EconomyService economyService;

    public PetShopGUI(PetService petService, EconomyService economyService) {
        this.petService = petService;
        this.economyService = economyService;
    }

    /**
     * 打开商店界面
     */
    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, SHOP_SIZE, SHOP_TITLE);

        // 获取玩家货币
        BigDecimal balance = economyService != null ? economyService.getBalance(player.getUniqueId()) : BigDecimal.ZERO;
        DecimalFormat df = new DecimalFormat("#,##0.00");

        // 标题行
        ItemStack title = createFillerItem(Material.PAPER, "§6§l★ 宠物商店 ★", List.of(
            "",
            "§7当前金币: §e" + df.format(balance),
            "",
            "§7在这里购买你心仪的宠物!",
            ""
        ));
        inv.setItem(4, title);

        // 宠物类型展示
        PetType[] petTypes = PetType.values();
        int slot = 9; // 第二行开始

        for (int i = 0; i < petTypes.length && slot < 45; i++) {
            PetType type = petTypes[i];
            ItemStack petItem = createPetShopItem(type, player);
            if (petItem != null) {
                inv.setItem(slot, petItem);
                slot++;
            }
        }

        // 底部按钮
        int bottomSlot = SHOP_SIZE - 9;
        ItemStack info = createFillerItem(Material.BOOK, "§e购买说明", List.of(
            "§7点击普通/优秀/稀有宠物直接购买",
            "§7史诗及以上需要达到对应等级",
            "§7稀有度越高，属性越强！"
        ));
        inv.setItem(bottomSlot, info);

        ItemStack close = createFillerItem(Material.BARRIER, "§c关闭", List.of("§7点击关闭商店"));
        inv.setItem(SHOP_SIZE - 1, close);

        player.openInventory(inv);
    }

    /**
     * 创建宠物商店物品
     */
    private ItemStack createPetShopItem(PetType type, Player player) {
        Material material = getPetMaterial(type);
        if (material == null) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7类型: §f" + type.getDisplayName());
        lore.add("§7分类: §f" + type.getCategory().getDisplayName());
        lore.add("");

        // 显示各稀有度价格
        for (PetRarity rarity : PetRarity.values()) {
            double price = petService.getPetPrice(type, rarity);
            String rarityColor = "§" + rarity.getColorCode();
            lore.add(rarityColor + rarity.getDisplayName() + " §7- §e" + formatPrice(price));
        }

        lore.add("");
        lore.add("§a左键点击购买普通宠物");
        lore.add("§6右键查看详情");

        meta.setDisplayName("§f" + type.getDisplayName());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * 购买宠物
     */
    public boolean purchasePet(Player player, PetType type, PetRarity rarity) {
        double price = petService.getPetPrice(type, rarity);

        if (economyService != null && !economyService.has(player.getUniqueId(), BigDecimal.valueOf(price))) {
            player.sendMessage("§c金币不足！需要 " + formatPrice(price) + " 金币");
            return false;
        }

        boolean purchased = petService.purchasePet(player.getUniqueId(), type, rarity);
        if (purchased) {
            if (economyService != null) {
                economyService.withdraw(player.getUniqueId(), BigDecimal.valueOf(price));
            }
            player.sendMessage("§a成功购买 " + rarity.getColoredName() + " " + type.getDisplayName() + " §a宠物！");
            player.sendMessage("§7使用 /pet list 查看你的宠物");
            return true;
        } else {
            player.sendMessage("§c购买失败！可能已达到最大宠物数量 (5只)");
            return false;
        }
    }

    /**
     * 获取宠物材质
     */
    private Material getPetMaterial(PetType type) {
        return switch (type) {
            case WOLF -> Material.WOLF_SPAWN_EGG;
            case CAT -> Material.CAT_SPAWN_EGG;
            case FOX -> Material.FOX_SPAWN_EGG;
            case OCELOT -> Material.OCELOT_SPAWN_EGG;
            case PARROT -> Material.PARROT_SPAWN_EGG;
            case RABBIT -> Material.RABBIT_SPAWN_EGG;
            case HORSE -> Material.HORSE_SPAWN_EGG;
            case DONKEY -> Material.DONKEY_SPAWN_EGG;
            case MULE -> Material.MULE_SPAWN_EGG;
            case LLAMA -> Material.LLAMA_SPAWN_EGG;
            case PIG -> Material.PIG_SPAWN_EGG;
            case STRIDER -> Material.STRIDER_SPAWN_EGG;
            case SKELETON_HORSE -> Material.SKELETON_HORSE_SPAWN_EGG;
            case ZOMBIE_HORSE -> Material.ZOMBIE_HORSE_SPAWN_EGG;
            case SNOW_GOLEM -> Material.SNOW_GOLEM_SPAWN_EGG;
            case IRON_GOLEM -> Material.IRON_GOLEM_SPAWN_EGG;
            case BLAZE -> Material.BLAZE_SPAWN_EGG;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SPAWN_EGG;
            case PHANTOM -> Material.PHANTOM_SPAWN_EGG;
            case DOLPHIN -> Material.DOLPHIN_SPAWN_EGG;
            case TURTLE -> Material.TURTLE_SPAWN_EGG;
            case AXOLOTL -> Material.AXOLOTL_SPAWN_EGG;
        };
    }

    /**
     * 创建填充物品
     */
    private ItemStack createFillerItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 格式化价格
     */
    private String formatPrice(double price) {
        if (price >= 1000000) {
            return String.format("%.1fM", price / 1000000);
        } else if (price >= 1000) {
            return String.format("%.1fK", price / 1000);
        } else {
            return String.format("%.0f", price);
        }
    }
}
