package dev.starcore.starcore.module.treasury.gui;

import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.module.economy.gui.EconomyTrendGui;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TaxationService;
import dev.starcore.starcore.module.treasury.TreasuryModule;
import dev.starcore.starcore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Treasury menu click listener
 * 处理国库系统所有菜单的点击事件
 */
public final class TreasuryMenuListener implements Listener {

    private final TreasuryModule treasuryModule;
    private final TreasuryMenu treasuryMenu;
    private final NationService nationService;
    private final SoundFeedbackManager soundManager;
    private EconomyTrendGui economyTrendGui;

    private final Map<UUID, MenuState> menuStates = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> pendingConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 200; // 防止快速重复点击

    public TreasuryMenuListener(TreasuryModule treasuryModule, TreasuryMenu treasuryMenu,
                                 NationService nationService, SoundFeedbackManager soundManager,
                                 EconomyTrendGui economyTrendGui) {
        this.treasuryModule = treasuryModule;
        this.treasuryMenu = treasuryMenu;
        this.nationService = nationService;
        this.soundManager = soundManager;
        this.economyTrendGui = economyTrendGui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // E-050: 点击冷却防止快速重复点击导致卡顿
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastClick = clickCooldowns.get(playerId);
        if (lastClick != null && now - lastClick < CLICK_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        clickCooldowns.put(playerId, now);

        Inventory inventory = event.getInventory();
        if (inventory == null) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // 判断是哪个菜单
        if (title.contains("国库管理中心") || title.contains("Treasury")) {
            handleMainMenuClick(player, event);
        } else if (title.contains("税收管理") || title.contains("Tax")) {
            handleTaxMenuClick(player, event);
        } else if (title.contains("转账管理") || title.contains("Transfer")) {
            handleTransferMenuClick(player, event);
        } else if (title.contains("财务报表") || title.contains("Report")) {
            handleReportMenuClick(player, event);
        } else if (title.contains("资金管理") || title.contains("Fund")) {
            handleFundMenuClick(player, event);
        } else if (title.contains("贷款管理") || title.contains("Loan")) {
            handleLoanMenuClick(player, event);
        } else if (title.contains("确认")) {
            handleConfirmMenuClick(player, event);
        } else {
            return;
        }
    }

    private void handleMainMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        int slot = event.getSlot();

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            player.closeInventory();
            return;
        }

        // 税收管理
        if (itemName.contains("税收管理")) {
            treasuryMenu.openTaxMenu(player);
            return;
        }

        // 转账管理
        if (itemName.contains("转账管理")) {
            treasuryMenu.openTransferMenu(player);
            return;
        }

        // 财务报表
        if (itemName.contains("财务报表") || itemName.contains("财务")) {
            treasuryMenu.openReportMenu(player);
            return;
        }

        // 资金管理（存取款）
        if (itemName.contains("资金管理")) {
            treasuryMenu.openFundMenu(player);
            return;
        }

        // 贷款管理 - 新增
        if (itemName.contains("贷款") || itemName.contains("Debt")) {
            treasuryMenu.openLoanMenu(player);
            return;
        }

        // 经济趋势分析 - 新增
        if (itemName.contains("趋势") || itemName.contains("Trend") || itemName.contains("走势")) {
            if (economyTrendGui != null) {
                economyTrendGui.openMainMenu(player);
            }
            return;
        }
    }

    private void handleTaxMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            treasuryMenu.openMainMenu(player);
            return;
        }

        // 解析税种类型 - 从 lore 中读取 taxType
        String taxType = null;
        if (meta.lore() != null) {
            for (Component loreLine : meta.lore()) {
                String loreText = PlainTextComponentSerializer.plainText().serialize(loreLine);
                if (loreText.startsWith("§8taxType=")) {
                    taxType = loreText.substring("§8taxType=".length());
                    break;
                }
            }
        }

        // 土地税
        if (itemName.contains("土地税") || (taxType != null && taxType.equals("LAND"))) {
            treasuryMenu.openTaxTypeMenu(player, "LAND");
            return;
        }

        // 商业税
        if (itemName.contains("商业税") || (taxType != null && taxType.equals("BUSINESS"))) {
            treasuryMenu.openTaxTypeMenu(player, "BUSINESS");
            return;
        }

        // 所得税
        if (itemName.contains("所得税") || (taxType != null && taxType.equals("INCOME"))) {
            treasuryMenu.openTaxTypeMenu(player, "INCOME");
            return;
        }

        // 关税
        if (itemName.contains("关税") || (taxType != null && taxType.equals("TARIFF"))) {
            treasuryMenu.openTaxTypeMenu(player, "TARIFF");
            return;
        }

        // 批量设置
        if (itemName.contains("批量设置")) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令设置: &f/treasury tax set <type> <rate>"));
            player.closeInventory();
        }
    }

    private void handleTransferMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            treasuryMenu.openMainMenu(player);
            return;
        }

        // 转账给其他国家的逻辑需要进一步实现
        if (itemName.contains("转账")) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令进行转账: &f/treasury transfer <nation> <amount>"));
            player.closeInventory();
        }
    }

    private void handleReportMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            treasuryMenu.openMainMenu(player);
            return;
        }
    }

    private void handleFundMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            treasuryMenu.openMainMenu(player);
            return;
        }

        // 存款
        if (itemName.contains("存款") || itemName.contains("Deposit")) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令存款: &f/treasury deposit <amount>"));
            player.closeInventory();
            return;
        }

        // 取款
        if (itemName.contains("取款") || itemName.contains("Withdraw")) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令取款: &f/treasury withdraw <amount>"));
            player.closeInventory();
            return;
        }
    }

    private void handleLoanMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            treasuryMenu.openMainMenu(player);
            return;
        }

        // 申请贷款
        if (itemName.contains("申请贷款")) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令申请贷款: &f/treasury loan apply <amount>"));
            player.closeInventory();
            return;
        }

        // 查看债务
        if (itemName.contains("查看债务") || itemName.contains("Debt")) {
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                var debts = treasuryModule.getActiveDebts(nationId);
                if (debts.isEmpty()) {
                    player.sendMessage(MessageUtil.colorize("&a您的国家当前没有债务"));
                } else {
                    player.sendMessage(MessageUtil.colorize("&6===== 债务列表 ====="));
                    for (var debt : debts) {
                        player.sendMessage(MessageUtil.colorize(
                            "&e债务ID: &f" + debt.debtId() +
                            "&e  金额: &c" + debt.remainingAmount() +
                            "&e  利率: &f" + debt.interestRate().multiply(BigDecimal.valueOf(100)) + "%"
                        ));
                    }
                }
            }
            player.closeInventory();
            return;
        }

        // 偿还债务
        if (itemName.contains("偿还")) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令偿还债务: &f/treasury loan repay <debtId> <amount>"));
            player.closeInventory();
            return;
        }

        // 健康度检查
        if (itemName.contains("健康度") || itemName.contains("Health")) {
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                int health = treasuryModule.getDebtHealthScore(nationId);
                String healthText = health >= 80 ? "&a优秀" : health >= 60 ? "&e良好" : health >= 40 ? "&c一般" : "&4危险";
                BigDecimal totalDebt = treasuryModule.getTotalDebt(nationId);
                player.sendMessage(MessageUtil.colorize(
                    "&6===== 债务健康度 =====" +
                    "\n&e健康分数: " + healthText + " &f(" + health + "/100)" +
                    "\n&e总债务: &c" + totalDebt + " 金币" +
                    "\n&e最大可借: &a" + treasuryModule.getMaxBorrowableAmount(nationId) + " 金币"
                ));
            }
            player.closeInventory();
            return;
        }
    }

    private void handleConfirmMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        int slot = event.getSlot();

        // 确认按钮 (槽位22)
        if ((itemName.contains("确认") || itemName.contains("Confirm")) && slot == 22) {
            Runnable confirm = pendingConfirmations.remove(player.getUniqueId());
            if (confirm != null) {
                confirm.run();
            }
            player.closeInventory();
            return;
        }

        // 取消按钮 (槽位24)
        if ((itemName.contains("取消") || itemName.contains("Cancel")) && slot == 24) {
            pendingConfirmations.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            menuStates.remove(player.getUniqueId());
            pendingConfirmations.remove(player.getUniqueId());
        }
    }

    private void playClickSound(Player player) {
        if (soundManager != null) {
            soundManager.playMenuSelect(player);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }

    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    // audit H-006: 修复 PlayerQuitEvent 未清理 menuStates/pendingConfirmations/clickCooldowns Map 导致的内存泄漏
    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        menuStates.remove(playerId);
        pendingConfirmations.remove(playerId);
        clickCooldowns.remove(playerId);
    }

    private record MenuState(String menuType, NationId nationId, Map<String, Object> data) {}
}
