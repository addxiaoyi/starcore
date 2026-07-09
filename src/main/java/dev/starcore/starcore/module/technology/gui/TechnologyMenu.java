package dev.starcore.starcore.module.technology.gui;

import dev.starcore.starcore.module.technology.ResearchProgress;
import dev.starcore.starcore.module.technology.TechnologyModule;
import dev.starcore.starcore.module.technology.TechnologyService;
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
 * Simplified technology GUI menu
 */
public class TechnologyMenu {

    public static final String MENU_TITLE = "§6§l科技中心";
    public static final int MENU_SIZE = 27;

    private final TechnologyModule technologyModule;
    private final TechnologyService technologyService;
    private final NationService nationService;

    public TechnologyMenu(TechnologyModule technologyModule, TechnologyService technologyService, NationService nationService) {
        this.technologyModule = technologyModule;
        this.technologyService = technologyService;
        this.nationService = nationService;
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

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isPresent()) {
            Nation nation = nationOpt.get();
            NationId nationId = nation.id();

            int researchedCount = technologyModule.unlockedTechnologies(nationId).size();
            int researchingCount = technologyModule.getNationResearch(nationId).size();

            inv.setItem(11, createMenuItem(
                Material.BOOK,
                "§e已研究科技",
                "§7查看已解锁的科技",
                "§7已研究: §a" + researchedCount
            ));

            inv.setItem(13, createMenuItem(
                Material.PAPER,
                "§b研究进度",
                "§7查看正在研究的科技",
                "§7进行中: §e" + researchingCount
            ));

            inv.setItem(15, createMenuItem(
                Material.NETHER_STAR,
                "§a开始研究",
                "§7选择新的科技进行研究",
                "§7可用科技: §f" + technologyModule.availableTechnologies().size()
            ));
        } else {
            inv.setItem(13, createMenuItem(Material.BARRIER, "§c无国家", "你需要先加入一个国家才能使用科技功能"));
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
            player.sendMessage("§c你需要先加入一个国家才能使用科技功能");
            return;
        }

        Nation nation = nationOpt.get();
        NationId nationId = nation.id();

        if (slot == 11) {
            // View researched technologies
            openResearchedTechView(player, nationId);
        } else if (slot == 13) {
            // View research progress
            openResearchProgressView(player, nationId);
        } else if (slot == 15) {
            // Start new research
            openResearchMenu(player, nationId);
        }
    }

    private void openResearchedTechView(Player player, NationId nationId) {
        player.sendMessage("§6=== 已研究科技 ===");

        Collection<String> unlocked = technologyModule.unlockedTechnologies(nationId);
        if (unlocked.isEmpty()) {
            player.sendMessage("§7你还没有研究任何科技");
            player.sendMessage("§7前往科技中心开始你的第一个研究!");
        } else {
            player.sendMessage("§7已解锁科技 (" + unlocked.size() + "):");
            for (String tech : unlocked) {
                player.sendMessage("§a  " + tech);
            }
        }
    }

    private void openResearchProgressView(Player player, NationId nationId) {
        player.sendMessage("§6=== 研究进度 ===");

        Map<String, ResearchProgress> research = technologyModule.getNationResearch(nationId);
        if (research.isEmpty()) {
            player.sendMessage("§7当前没有进行中的研究");
            player.sendMessage("§7前往科技中心开始新研究");
        } else {
            player.sendMessage("§7进行中的研究:");
            for (Map.Entry<String, ResearchProgress> entry : research.entrySet()) {
                String tech = entry.getKey();
                ResearchProgress progress = entry.getValue();

                double percent = progress.getProgress() * 100;
                String status = switch ((int)(percent / 25)) {
                    case 4 -> "§a████";
                    case 3 -> "§a███§7█";
                    case 2 -> "§a██§7██";
                    case 1 -> "§a█§7███";
                    default -> "§7████";
                };

                long remainingSecs = progress.getRemainingSeconds();
                String timeLeft = remainingSecs > 60 ?
                    (remainingSecs / 60) + "分" + (remainingSecs % 60) + "秒" :
                    remainingSecs + "秒";

                player.sendMessage("§e  " + tech + " " + status + " §7" + String.format("%.1f", percent) + "% §7剩余: " + timeLeft);
            }
        }
    }

    private void openResearchMenu(Player player, NationId nationId) {
        player.sendMessage("§6=== 科技研究 ===");

        Collection<String> available = technologyModule.availableTechnologies();
        Collection<String> unlocked = technologyModule.unlockedTechnologies(nationId);
        Map<String, ResearchProgress> researching = technologyModule.getNationResearch(nationId);

        player.sendMessage("§7可研究科技:");
        int count = 0;
        for (String tech : available) {
            // Skip already unlocked
            if (unlocked.contains(tech)) continue;
            // Skip currently researching
            if (researching.containsKey(tech)) continue;

            String prefix = "§a  ";
            player.sendMessage(prefix + tech);
            count++;

            if (count >= 10) {
                player.sendMessage("§7  ... 还有更多科技");
                break;
            }
        }

        if (count == 0) {
            player.sendMessage("§7  没有可研究的新科技");
        }

        // 分隔
        player.sendMessage("§7使用 §e/tech research <科技名> §7开始研究");
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
