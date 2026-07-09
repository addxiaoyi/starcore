package dev.starcore.starcore.pet;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 宠物详情GUI
 */
public class PetDetailGUI {
    private static final String DETAIL_TITLE = "宠物详情";
    private static final int DETAIL_SIZE = 36; // 4行

    private final PetService petService;

    public PetDetailGUI(PetService petService) {
        this.petService = petService;
    }

    /**
     * 打开宠物详情界面
     */
    public void openDetail(Player player, UUID petId) {
        PlayerPets playerPets = petService.getPlayerPets(player.getUniqueId());
        Pet pet = playerPets.getPet(petId);

        if (pet == null) {
            player.sendMessage("§c宠物不存在！");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, DETAIL_SIZE, DETAIL_TITLE);

        // 标题行
        String rarityColor = "§" + pet.getRarity().getColorCode();
        ItemStack title = createFillerItem(Material.NETHER_STAR, rarityColor + "§l" + pet.getName(), List.of(
            "",
            "§7稀有度: " + rarityColor + pet.getRarity().getDisplayName(),
            "§7等级: §aLv." + pet.getLevel(),
            ""
        ));
        inv.setItem(4, title);

        // 宠物图标
        Material iconMaterial = getPetMaterial(pet.getPetType());
        ItemStack icon = new ItemStack(iconMaterial != null ? iconMaterial : Material.BONE);
        ItemMeta iconMeta = icon.getItemMeta();
        iconMeta.setDisplayName("§f" + pet.getPetType().getDisplayName());
        iconMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(iconMeta);
        inv.setItem(0, icon);

        // 属性信息
        int infoSlot = 1;
        inv.setItem(infoSlot++, createInfoItem(Material.REDSTONE, "§c❤ 生命值",
            String.format("%.1f / %.1f", pet.getHealth(), pet.getMaxHealth())));
        inv.setItem(infoSlot++, createInfoItem(Material.IRON_SWORD, "§c⚔ 攻击力",
            String.format("%.1f", pet.getDamage())));
        inv.setItem(infoSlot++, createInfoItem(Material.SHIELD, "§9🛡 防御力",
            String.format("%.1f", pet.getDefense())));
        inv.setItem(infoSlot++, createInfoItem(Material.SUGAR, "§e⚡ 移动速度",
            String.format("%.2f", pet.getSpeed())));
        inv.setItem(infoSlot++, createInfoItem(Material.BLAZE_POWDER, "§6⚡ 战斗力",
            String.format("%.1f", pet.getTotalPower())));

        // 经验信息
        int expSlot = 9;
        double expProgress = pet.getExpProgress();
        int expBar = (int) (expProgress * 10);
        String expBarStr = "§a" + "█".repeat(Math.max(0, expBar)) +
                          "§7" + "█".repeat(Math.max(0, 10 - expBar));

        ItemStack expInfo = createFillerItem(Material.EXPERIENCE_BOTTLE, "§b§l经验信息", List.of(
            "",
            "§7当前等级: §aLv." + pet.getLevel(),
            "§7最高等级: §e" + pet.getMaxLevel(),
            "",
            "§7当前经验: §f" + pet.getExperience(),
            "§7升级需要: §f" + pet.getExpForNextLevel(),
            "",
            "§7进度: " + expBarStr,
            "§7(" + String.format("%.1f", expProgress * 100) + "%)",
            "",
            "§7达到等级后可进化为更高稀有度"
        ));
        inv.setItem(10, expInfo);

        // 行动按钮
        int actionSlot = 18;

        if (pet.isSummoned()) {
            inv.setItem(actionSlot++, createActionItem(Material.GLASS_BOTTLE, "§c收起宠物",
                "§7取消召唤此宠物"));
        } else {
            inv.setItem(actionSlot++, createActionItem(Material.ENDER_EYE, "§a召唤宠物",
                "§7让宠物跟随你"));
        }

        inv.setItem(actionSlot++, createActionItem(Material.NAME_TAG, "§e重命名",
            "§7修改宠物名称"));

        inv.setItem(actionSlot++, createActionItem(Material.GOLDEN_APPLE, "§d喂食",
            "§7恢复宠物饱食度和生命"));

        // 稀有度升级
        PetRarity nextRarity = pet.getRarity().getNextRarity();
        if (nextRarity != pet.getRarity()) {
            double upgradeCost = petService.getConfig().getRarityUpgradeCost(pet.getRarity());
            ItemStack upgrade = createActionItem(Material.ENCHANTED_BOOK, "§6稀有度升级",
                "§7升级至 " + "§" + nextRarity.getColorCode() + nextRarity.getDisplayName(),
                "§7费用: §e" + String.format("%.0f", upgradeCost));
            inv.setItem(actionSlot++, upgrade);
        } else {
            ItemStack max = createActionItem(Material.NETHER_STAR, "§6稀有度已满",
                "§7当前为最高稀有度");
            inv.setItem(actionSlot++, max);
        }

        // 分类信息
        int info2Slot = 27;
        inv.setItem(info2Slot++, createInfoItem(Material.STRING, "§7宠物类型",
            pet.getPetType().getDisplayName()));
        inv.setItem(info2Slot++, createInfoItem(Material.BOOK, "§7宠物分类",
            pet.getPetType().getCategory().getDisplayName()));
        inv.setItem(info2Slot++, createInfoItem(Material.CLOCK, "§7创建时间",
            Instant.ofEpochMilli(pet.getCreatedAt())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));

        if (pet.getPetType().canRide()) {
            inv.setItem(info2Slot++, createInfoItem(Material.SADDLE, "§7骑乘状态",
                pet.isSummoned() ? "§a可骑乘" : "§c需要先召唤"));
        }

        // 底部返回按钮
        ItemStack back = createFillerItem(Material.ARROW, "§e返回宠物列表", List.of(
            "§7点击返回宠物列表"
        ));
        inv.setItem(DETAIL_SIZE - 5, back);

        ItemStack close = createFillerItem(Material.BARRIER, "§c关闭", List.of("§7点击关闭"));
        inv.setItem(DETAIL_SIZE - 1, close);

        player.openInventory(inv);
    }

    /**
     * 创建信息物品
     */
    private ItemStack createInfoItem(Material material, String name, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of("", "§7数值: §f" + value, ""));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 创建操作物品
     */
    private ItemStack createActionItem(Material material, String name, String... descriptions) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        for (String desc : descriptions) {
            lore.add(desc);
        }
        meta.setLore(lore);

        item.setItemMeta(meta);
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
}
