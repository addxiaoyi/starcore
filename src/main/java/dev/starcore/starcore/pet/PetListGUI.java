package dev.starcore.starcore.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 宠物列表GUI
 */
public class PetListGUI {
    private static final String LIST_TITLE = "我的宠物";
    private static final int LIST_SIZE = 54; // 6行

    private final PetService petService;

    public PetListGUI(PetService petService) {
        this.petService = petService;
    }

    /**
     * 打开宠物列表界面
     */
    public void openList(Player player) {
        Inventory inv = Bukkit.createInventory(null, LIST_SIZE, LIST_TITLE);

        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());

        // 标题行
        ItemStack title = createFillerItem(Material.NAME_TAG, "§6§l★ 我的宠物 ★", List.of(
            "",
            "§7宠物数量: §f" + playerPets.getPetCount() + "/" + playerPets.getMaxPets(),
            "§7总战斗力: §c" + String.format("%.1f", playerPets.getTotalPower()),
            ""
        ));
        inv.setItem(4, title);

        // 填充宠物列表
        List<Pet> pets = new ArrayList<>(playerPets.getAllPets());
        int slot = 9; // 第二行开始

        for (Pet pet : pets) {
            if (slot >= 45) break;

            ItemStack petItem = createPetItem(pet, playerPets.getActivePetId());
            inv.setItem(slot, petItem);
            slot++;
        }

        // 底部按钮
        int bottomSlot = LIST_SIZE - 9;

        ItemStack summonAll = createFillerItem(Material.ENDER_EYE, "§a召唤所有宠物", List.of(
            "§7点击召唤所有宠物跟随你",
            ""
        ));
        inv.setItem(bottomSlot, summonAll);

        ItemStack despawnAll = createFillerItem(Material.GLASS_BOTTLE, "§c收起所有宠物", List.of(
            "§7点击收起所有召唤的宠物",
            ""
        ));
        inv.setItem(bottomSlot + 1, despawnAll);

        ItemStack shop = createFillerItem(Material.CHEST_MINECART, "§e宠物商店", List.of(
            "§7点击打开宠物商店",
            ""
        ));
        inv.setItem(bottomSlot + 7, shop);

        ItemStack close = createFillerItem(Material.BARRIER, "§c关闭", List.of("§7点击关闭"));
        inv.setItem(LIST_SIZE - 1, close);

        player.openInventory(inv);
    }

    /**
     * 创建宠物物品
     */
    private ItemStack createPetItem(Pet pet, UUID activePetId) {
        Material material = getPetMaterial(pet.getPetType());
        if (material == null) {
            material = Material.BONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String rarityColor = "§" + pet.getRarity().getColorCode();
        String name = rarityColor + "[" + pet.getName() + "]";
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7类型: §f" + pet.getPetType().getDisplayName());
        lore.add("§7稀有度: " + rarityColor + pet.getRarity().getDisplayName());
        lore.add("§7等级: §aLv." + pet.getLevel());
        lore.add("");
        lore.add("§c❤ 生命: " + String.format("%.1f", pet.getHealth()) + "/" + String.format("%.1f", pet.getMaxHealth()));
        lore.add("§c⚔ 攻击: " + String.format("%.1f", pet.getDamage()));
        lore.add("§9🛡 防御: " + String.format("%.1f", pet.getDefense()));
        lore.add("§e⚡ 速度: " + String.format("%.2f", pet.getSpeed()));
        lore.add("");

        // 经验条
        double expProgress = pet.getExpProgress();
        int expBar = (int) (expProgress * 10);
        String expBarStr = "§a" + "█".repeat(Math.max(0, expBar)) +
                          "§7" + "█".repeat(Math.max(0, 10 - expBar));
        lore.add("§7经验: " + expBarStr + " §7(" + pet.getExperience() + "/" + pet.getExpForNextLevel() + ")");
        lore.add("");

        // 召唤状态
        if (pet.isSummoned()) {
            lore.add("§a✓ 已召唤");
            lore.add("");
            lore.add("§e左键: §7收起");
            lore.add("§e右键: §7重命名");
        } else {
            lore.add("§c✗ 未召唤");
            lore.add("");
            lore.add("§a左键: §7召唤");
            lore.add("§e右键: §7重命名");
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);

        // 设置宠物ID的nbt（使用lore存储）
        return item;
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
     * 从lore中提取宠物ID
     */
    public static UUID getPetIdFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return null;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.startsWith("§7PetID: ")) {
                try {
                    return UUID.fromString(line.substring(9));
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
