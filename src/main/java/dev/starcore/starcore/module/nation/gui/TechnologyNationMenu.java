package dev.starcore.starcore.module.nation.gui;

import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.technology.TechnologyCost;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.triumphteam.gui.components.GuiAction;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 科技树管理菜单
 * 显示科技节点网格，支持激活科技
 */
public class TechnologyNationMenu {

    private final TechnologyService technologyService;
    private final TreasuryService treasuryService;
    private final Plugin plugin;
    private final NationModule nationModule;
    private Consumer<Player> mainMenuCallback;

    public TechnologyNationMenu(TechnologyService technologyService,
                                TreasuryService treasuryService,
                                Plugin plugin,
                                NationModule nationModule) {
        this.technologyService = technologyService;
        this.treasuryService = treasuryService;
        this.plugin = plugin;
        this.nationModule = nationModule;
    }

    public void setMainMenuCallback(Consumer<Player> callback) {
        this.mainMenuCallback = callback;
    }

    /**
     * 打开科技树主菜单
     */
    public void openTechnologyMenu(Player player, Nation nation) {
        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l⚙ " + nation.name() + " §7| 科技树"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：科技概览
        int unlocked = technologyService != null
            ? technologyService.unlockedTechnologies(nation.id()).size() : 0;
        int total = technologyService != null
            ? technologyService.availableTechnologies().size() : 0;

        gui.setItem(4, createGuiItem(
            Material.ENCHANTING_TABLE,
            Component.text("§e§l科技树", NamedTextColor.YELLOW),
            List.of(
                Component.text(""),
                Component.text("§7国家: §f" + nation.name()),
                Component.text(""),
                Component.text("§a已解锁: §e" + unlocked),
                Component.text("§7可用科技: §e" + total),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 获取科技数据
        Collection<String> unlockedTechs = technologyService != null
            ? technologyService.unlockedTechnologies(nation.id())
            : List.of();
        Collection<String> availableTechs = technologyService != null
            ? technologyService.availableTechnologies()
            : List.of();

        // 填充科技槽位 (10-43, 跳过边框)
        int slot = 10;
        int count = 0;
        for (String techId : availableTechs) {
            if (count >= 14) break;
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot > 43) break;

            boolean unlockedTech = unlockedTechs.contains(techId);
            Material mat = unlockedTech ? Material.BEACON : Material.BROWN_STAINED_GLASS_PANE;
            Component name = Component.text(
                unlockedTech ? "§a§l" + techId : "§7" + techId);

            gui.setItem(slot, createGuiItem(mat, name,
                List.of(
                    Component.text(""),
                    Component.text(unlockedTech ? "§a§l✓ 已解锁" : "§c§l✗ 未解锁"),
                    Component.text(""),
                    Component.text("§a▸ 点击查看详情")
                ),
                event -> {
                    event.setCancelled(true);
                    openTechnologyDetailMenu(player, nation, techId);
                }, unlockedTech
            ));
            slot++;
            count++;
        }

        if (count == 0) {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c暂无可用科技", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7科技模块未启用或无可用科技")
                ),
                event -> {}, false
            ));
        }

        // 返回主菜单按钮
        gui.setItem(49, createGuiItem(
            Material.BARRIER,
            Component.text("§c§l✖ 返回主菜单", NamedTextColor.RED),
            List.of(Component.text("")),
            event -> {
                event.setCancelled(true);
                gui.close(player);
                if (mainMenuCallback != null) {
                    plugin.getServer().getScheduler().runTask(plugin,
                        () -> mainMenuCallback.accept(player));
                }
            },
            false
        ));

        fillBorder(gui, Material.GRAY_STAINED_GLASS_PANE);
        gui.open(player);
    }

    /**
     * 打开科技详情页
     */
    public void openTechnologyDetailMenu(Player player, Nation nation, String techId) {
        String normalizedId = techId.toLowerCase(Locale.ROOT).trim();

        boolean isUnlocked = technologyService != null
            && technologyService.hasTechnology(nation.id(), normalizedId);

        Optional<TechnologyCost> costOpt = technologyService != null
            ? technologyService.costOf(normalizedId)
            : Optional.empty();

        NationMember self = nation.members().stream()
            .filter(m -> m.playerId().equals(player.getUniqueId()))
            .findFirst().orElse(null);
        boolean isAdmin = self != null && "admin".equals(self.rank());

        // 科技显示名称映射
        String displayName = getTechDisplayName(normalizedId, techId);
        String description = getTechDescription(normalizedId);

        Gui gui = Gui.gui()
            .title(LegacyComponentSerializer.legacyAmpersand().deserialize(
                "§6§l⚙ " + displayName + " §7| 科技详情"))
            .rows(6)
            .disableAllInteractions()
            .create();

        // 顶部：科技图标和信息
        Material topMat = isUnlocked ? Material.BEACON : Material.BROWN_STAINED_GLASS_PANE;
        gui.setItem(4, createGuiItem(
            topMat,
            Component.text((isUnlocked ? "§a§l✓ " : "§c§l✗ ") + displayName, NamedTextColor.WHITE),
            List.of(
                Component.text(""),
                Component.text("§7科技ID: §f" + normalizedId),
                Component.text(""),
                Component.text("§7状态: " + (isUnlocked ? "§a已解锁" : "§c未解锁")),
                Component.text(description),
                Component.text("")
            ),
            event -> {}, false
        ));

        // 成本区域
        if (costOpt.isPresent()) {
            var cost = costOpt.get();
            gui.setItem(20, createGuiItem(
                Material.GOLD_INGOT,
                Component.text("§e💰 国库成本", NamedTextColor.YELLOW),
                List.of(
                    Component.text(""),
                    Component.text("§7国库消耗: §6" + cost.treasury() + " 星尘"),
                    Component.text("")
                ),
                event -> {}, false
            ));

            // 资源成本
            int resSlot = 22;
            for (var entry : cost.resources().entrySet()) {
                String resName = entry.getKey();
                Long amount = entry.getValue();
                Material resMat = materialForResource(resName);
                gui.setItem(resSlot, createGuiItem(
                    resMat,
                    Component.text("§7" + resName + ": §e" + amount),
                    List.of(
                        Component.text(""),
                        Component.text("§7需要: §e" + amount + " §7单位")
                    ),
                    event -> {}, false
                ));
                resSlot++;
                if (resSlot == 25) break;
            }
        } else {
            gui.setItem(22, createGuiItem(
                Material.BARRIER,
                Component.text("§c无法获取科技成本", NamedTextColor.RED),
                List.of(Component.text(""), Component.text("§7科技配置可能缺失")),
                event -> {}, false
            ));
        }

        // 操作按钮
        if (isAdmin) {
            if (!isUnlocked) {
                gui.setItem(38, createGuiItem(
                    Material.LIME_STAINED_GLASS,
                    Component.text("§a§l⬆ 解锁科技", NamedTextColor.GREEN),
                    List.of(
                        Component.text(""),
                        Component.text("§7为 §f" + nation.name() + " §7解锁此科技"),
                        Component.text(""),
                        costOpt.isPresent()
                            ? Component.text("§7消耗: §6" + costOpt.get().treasury() + " 星尘")
                            : Component.text(""),
                        Component.text(""),
                        Component.text("§a▸ 点击解锁")
                    ),
                    event -> {
                        event.setCancelled(true);
                        if (technologyService != null) {
                            boolean success = technologyService.unlock(nation.id(), normalizedId);
                            if (success) {
                                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                                player.sendMessage(Component.text("§a科技解锁成功！", NamedTextColor.GREEN));
                                plugin.getServer().getScheduler().runTask(plugin,
                                    () -> openTechnologyDetailMenu(player, nation, normalizedId));
                            } else {
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                player.sendMessage(Component.text("§c科技解锁失败！", NamedTextColor.RED));
                            }
                        }
                    }, true
                ));
            } else {
                gui.setItem(38, createGuiItem(
                    Material.RED_STAINED_GLASS,
                    Component.text("§c§l✗ 移除科技", NamedTextColor.RED),
                    List.of(
                        Component.text(""),
                        Component.text("§7为 §f" + nation.name() + " §7移除此科技"),
                        Component.text(""),
                        Component.text("§7⚠ 警告：移除后需重新消耗资源解锁"),
                        Component.text(""),
                        Component.text("§c▸ 点击移除")
                    ),
                    event -> {
                        event.setCancelled(true);
                        if (technologyService != null) {
                            technologyService.revoke(nation.id(), normalizedId);
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                            player.sendMessage(Component.text("§a科技已移除！", NamedTextColor.GREEN));
                            plugin.getServer().getScheduler().runTask(plugin,
                                () -> openTechnologyDetailMenu(player, nation, normalizedId));
                        }
                    }, true
                ));
            }
        } else {
            gui.setItem(40, createGuiItem(
                Material.BARRIER,
                Component.text("§c⚠ 需要管理员权限", NamedTextColor.RED),
                List.of(
                    Component.text(""),
                    Component.text("§7科技操作需要管理员权限"),
                    Component.text("§7请联系国家管理员")
                ),
                event -> event.setCancelled(true),
                false
            ));
        }

        // 返回按钮
        gui.setItem(49, createGuiItem(
            Material.ARROW,
            Component.text("§e◀ 返回科技树", NamedTextColor.YELLOW),
            List.of(Component.text(""), Component.text("§7返回科技树列表")),
            event -> {
                event.setCancelled(true);
                openTechnologyMenu(player, nation);
            },
            false
        ));

        gui.open(player);
    }

    private String getTechDisplayName(String normalized, String original) {
        return switch (normalized) {
            case "logistics" -> "§b后勤学";
            case "steel_working" -> "§e钢铁冶炼";
            case "radio_command" -> "§3无线电指挥";
            case "mechanized_warfare" -> "§4机械化战争";
            case "industrial_planning" -> "§6工业规划";
            default -> "§7" + original;
        };
    }

    private String getTechDescription(String normalized) {
        return switch (normalized) {
            case "logistics" -> "§7提升军队移动速度和补给效率";
            case "steel_working" -> "§7解锁高级武器和防具制作";
            case "radio_command" -> "§7提升远程指挥和通信能力";
            case "mechanized_warfare" -> "§7解锁坦克和装甲单位";
            case "industrial_planning" -> "§7提升资源产出和工厂效率";
            default -> "§7科技效果";
        };
    }

    private Material materialForResource(String resource) {
        return switch (resource.toLowerCase(Locale.ROOT)) {
            case "food" -> Material.BREAD;
            case "timber" -> Material.OAK_LOG;
            case "ore" -> Material.IRON_INGOT;
            case "rare_metal" -> Material.NETHERITE_INGOT;
            case "oil" -> Material.BLAZE_ROD;
            default -> Material.COBBLESTONE;
        };
    }

    private void fillBorder(Gui gui, Material borderMaterial) {
        if (borderMaterial == null) {
            borderMaterial = Material.GRAY_STAINED_GLASS_PANE;
        }
        GuiItem borderItem = new GuiItem(new ItemStack(borderMaterial));

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderItem);
        }
        int rows = gui.getRows();
        for (int i = 0; i < 9; i++) {
            gui.setItem((rows - 1) * 9 + i, borderItem);
        }
        for (int i = 1; i < rows - 1; i++) {
            gui.setItem(i * 9, borderItem);
            gui.setItem(i * 9 + 8, borderItem);
        }
    }

    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action) {
        return createGuiItem(material, name, lore, action, false);
    }

    private GuiItem createGuiItem(Material material, Component name, List<Component> lore,
                                   GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action,
                                   boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        if (glow && material != Material.BARRIER && material != Material.GRAY_STAINED_GLASS_PANE) {
            item = addGlow(item);
        }
        return new GuiItem(item, action);
    }

    private ItemStack addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        try {
            var glowEnchant = org.bukkit.enchantments.Enchantment.getByName("UNBREAKING");
            if (glowEnchant != null) {
                meta.addEnchant(glowEnchant, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to add glow effect: " + e.getMessage());
        }
                        // 静默跳过，保持数据兼容
        item.setItemMeta(meta);
        return item;
    }
}
