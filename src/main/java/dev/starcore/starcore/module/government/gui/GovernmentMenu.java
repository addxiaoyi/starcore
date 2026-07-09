package dev.starcore.starcore.module.government.gui;

import dev.starcore.starcore.module.government.GovernmentModule;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Simplified government GUI menu
 */
public class GovernmentMenu {

    public static final String MENU_TITLE = "§6§l政体管理";
    public static final int MENU_SIZE = 27;

    private final GovernmentModule governmentModule;
    private final NationService nationService;
    private final UUID playerId;

    public GovernmentMenu(GovernmentModule governmentModule, NationService nationService, UUID playerId) {
        this.governmentModule = governmentModule;
        this.nationService = nationService;
        this.playerId = playerId;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text(MENU_TITLE));

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < MENU_SIZE; i++) {
            inv.setItem(i, filler);
        }

        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isPresent()) {
            Nation nation = nationOpt.get();
            GovernmentType currentType = governmentModule.governmentOf(nation);

            // Show current government type info
            inv.setItem(11, createMenuItem(
                Material.PAPER,
                "§e当前政体",
                "§7类型: §f" + currentType.name(),
                "§7" + currentType.displayName()
            ));

            // Government change option
            inv.setItem(13, createMenuItem(
                Material.BOOK,
                "§a政体变更",
                "§7更改国家政体类型",
                "§7需要一定条件才能变更"
            ));

            // Government history
            inv.setItem(15, createMenuItem(
                Material.COMPASS,
                "§b政治历史",
                "§7查看政体变更历史",
                "§7和重要政治事件"
            ));
        } else {
            inv.setItem(13, createMenuItem(Material.BARRIER, "§c无国家", "你需要先加入一个国家才能使用政体功能"));
        }

        player.openInventory(inv);
    }

    /**
     * Handle menu click
     */
    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            player.sendMessage("§c你需要先加入一个国家才能使用政体功能");
            return;
        }

        Nation nation = nationOpt.get();
        GovernmentType currentType = governmentModule.governmentOf(nation);

        if (slot == 11) {
            // Show current government type details
            showGovernmentInfo(player, nation, currentType);
        } else if (slot == 13) {
            // Open government change menu
            openGovernmentChangeMenu(player, nation, currentType);
        } else if (slot == 15) {
            // Show government history
            showGovernmentHistory(player, nation);
        }
    }

    private void showGovernmentInfo(Player player, Nation nation, GovernmentType type) {
        player.sendMessage("§6=== 当前政体信息 ===");
        player.sendMessage("§7国家: §f" + nation.name());
        player.sendMessage("§7政体: §e" + type.name());
        // 分隔
        player.sendMessage("§7类型: §f" + type.displayName());
        // 分隔
        player.sendMessage("§7政治规则:");
        player.sendMessage("§a  提案: §f" + (type == GovernmentType.MONARCHY || type == GovernmentType.DICTATORSHIP ? "君主/独裁者专属" : "所有成员可提案"));
        player.sendMessage("§a  签署: §f" + (type == GovernmentType.REPUBLIC || type == GovernmentType.DEMOCRACY || type == GovernmentType.OLIGARCHY ? "成员投票" : "君主/独裁者专属"));
        player.sendMessage("§a  通过条件: §f" + type.requiredSignatures(nation, null) + " 个签名");
    }

    private void openGovernmentChangeMenu(Player player, Nation nation, GovernmentType currentType) {
        player.sendMessage("§6=== 政体变更 ===");
        player.sendMessage("§7当前政体: §e" + currentType.name() + " (" + currentType.displayName() + ")");
        // 分隔
        player.sendMessage("§7可用政体类型:");

        for (GovernmentType type : GovernmentType.values()) {
            if (type == currentType) {
                player.sendMessage("§e  " + type.name() + " (" + type.displayName() + ") §7(当前)");
            } else {
                player.sendMessage("§a  " + type.name() + " (" + type.displayName() + ")");
            }
        }

        // 分隔
        player.sendMessage("§7使用 §e/nation government <类型> §7提交政体变更提案");
        player.sendMessage("§7部分政体变更可能需要议会投票通过");
    }

    private void showGovernmentHistory(Player player, Nation nation) {
        player.sendMessage("§6=== 政治历史 ===");
        player.sendMessage("§7国家: §f" + nation.name());
        // 分隔
        player.sendMessage("§7暂无历史记录");
        player.sendMessage("§7政体变更记录将在未来版本中添加");
    }

    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (lore.length > 0) {
                List<Component> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(Component.text(line));
                }
                meta.lore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
