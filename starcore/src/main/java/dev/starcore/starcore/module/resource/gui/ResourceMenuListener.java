package dev.starcore.starcore.module.resource.gui;

import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.*;
import dev.starcore.starcore.util.MessageUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resource menu click listener
 * 处理资源系统所有菜单的点击事件
 */
public final class ResourceMenuListener implements Listener {

    private final ResourceModule resourceModule;
    private final ResourceMenu resourceMenu;
    private final ResourceService resourceService;
    private final ResourcePriceService priceService;
    private final ResourceReserveService reserveService;
    private final MarketOrderBookService orderBookService;
    private final TradeHistoryService tradeHistoryService;
    private final PlayerTradeService playerTradeService;
    private final NationService nationService;
    private final SoundFeedbackManager soundManager;
    private final MessageService messages;
    private final JavaPlugin plugin;

    // 菜单状态跟踪 (使用 ConcurrentHashMap 保证线程安全)
    private final Map<UUID, MenuState> menuStates = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedResource = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    public ResourceMenuListener(
            ResourceModule resourceModule,
            ResourceMenu resourceMenu,
            ResourceService resourceService,
            ResourcePriceService priceService,
            ResourceReserveService reserveService,
            MarketOrderBookService orderBookService,
            TradeHistoryService tradeHistoryService,
            PlayerTradeService playerTradeService,
            NationService nationService,
            SoundFeedbackManager soundManager,
            MessageService messages
    ) {
        this.resourceModule = resourceModule;
        this.resourceMenu = resourceMenu;
        this.resourceService = resourceService;
        this.priceService = priceService;
        this.reserveService = reserveService;
        this.orderBookService = orderBookService;
        this.tradeHistoryService = tradeHistoryService;
        this.playerTradeService = playerTradeService;
        this.nationService = nationService;
        this.soundManager = soundManager;
        this.messages = messages;
        this.plugin = resourceModule.getPlugin();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        // 判断是哪个菜单
        if (title.contains("资源管理系统")) {
            handleMainMenuClick(player, event);
        } else if (title.contains("资源储量")) {
            handleStockpileMenuClick(player, event);
        } else if (title.contains("资源市场")) {
            handleMarketMenuClick(player, event);
        } else if (title.contains("市场订单簿")) {
            handleOrderBookMenuClick(player, event);
        } else if (title.contains("交易历史")) {
            handleTradeHistoryMenuClick(player, event);
        } else if (title.contains("玩家交易")) {
            handlePlayerTradeMenuClick(player, event);
        } else if (title.contains("战略储备")) {
            handleReserveMenuClick(player, event);
        } else if (title.contains("资源详情")) {
            handleResourceDetailMenuClick(player, event);
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

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            // 尝试获取国家ID并打开主菜单
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                resourceMenu.openMainMenu(player, nationId);
            } else {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 资源储量 - 传入 nationId
        if (itemName.contains("资源储量") || item.getType() == Material.CHEST) {
            NationId nationId = getPlayerNationId(player);
            resourceMenu.openStockpileMenu(player, nationId);
            return;
        }

        // 资源市场
        if (itemName.contains("资源市场") || item.getType() == Material.BEACON) {
            resourceMenu.openMarketMenu(player);
            return;
        }

        // 战略储备
        if (itemName.contains("战略储备") || item.getType() == Material.ENDER_CHEST) {
            NationId nationId = getPlayerNationId(player);
            resourceMenu.openReserveMenu(player, nationId);
            return;
        }

        // 市场订单簿
        if (itemName.contains("市场订单簿") || item.getType() == Material.BOOK) {
            if (orderBookService != null) {
                player.sendMessage(MessageUtil.colorize("&e请先在市场选择一种资源查看订单簿"));
                player.sendMessage(MessageUtil.colorize("&7提示: 在市场菜单中点击资源可查看订单簿"));
            } else {
                player.sendMessage(MessageUtil.colorize("&c市场订单簿功能暂不可用"));
            }
            return;
        }

        // 交易历史
        if (itemName.contains("交易历史")) {
            resourceMenu.openTradeHistoryMenu(player);
            return;
        }

        // 玩家交易
        if (itemName.contains("玩家交易") || item.getType() == Material.EMERALD) {
            resourceMenu.openPlayerTradeMenu(player);
            return;
        }
    }

    private void handleStockpileMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                resourceMenu.openMainMenu(player, nationId);
            } else {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 点击资源查看详情
        if (meta.lore() != null && meta.lore().stream().anyMatch(l -> {
            String loreText = PlainTextComponentSerializer.plainText().serialize(l);
            return loreText.contains("点击查看详情");
        })) {
            String resourceId = extractResourceId(itemName);
            if (resourceId != null) {
                player.sendMessage(MessageUtil.colorize("&e资源详情: &f" + resourceId));
            }
        }
    }

    private void handleMarketMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                resourceMenu.openMainMenu(player, nationId);
            } else {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 点击资源查看订单簿
        if (meta.lore() != null && meta.lore().stream().anyMatch(l -> {
            String loreText = PlainTextComponentSerializer.plainText().serialize(l);
            return loreText.contains("点击查看");
        })) {
            String resourceId = extractResourceId(itemName);
            if (resourceId != null && orderBookService != null) {
                resourceMenu.openOrderBookMenu(player, resourceId);
            }
        }
    }

    private void handleOrderBookMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            resourceMenu.openMarketMenu(player);
            return;
        }

        // 我的订单
        if (itemName.contains("我的订单")) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令查看我的订单: &f/market orders"));
            return;
        }

        // 点击买单/卖单查看详情
        if (itemName.contains("买单") || itemName.contains("卖单")) {
            String orderId = itemName.contains("#") ?
                itemName.substring(itemName.indexOf("#") + 1).trim() : null;
            if (orderId != null) {
                player.sendMessage(MessageUtil.colorize("&e订单详情: &f#" + orderId));
            }
        }
    }

    private void handleTradeHistoryMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                resourceMenu.openMainMenu(player, nationId);
            } else {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 分页处理
        if (itemName.contains("上一页") || itemName.contains("◀")) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            if (currentPage > 0) {
                playerPages.put(player.getUniqueId(), currentPage - 1);
                resourceMenu.openTradeHistoryMenu(player);
            }
            return;
        }

        if (itemName.contains("下一页") || itemName.contains("▶")) {
            int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);
            playerPages.put(player.getUniqueId(), currentPage + 1);
            resourceMenu.openTradeHistoryMenu(player);
            return;
        }

        // 点击交易记录查看详情
        if (meta.lore() != null) {
            for (var lore : meta.lore()) {
                String loreText = PlainTextComponentSerializer.plainText().serialize(lore);
                if (loreText.contains("总额:")) {
                    player.sendMessage(MessageUtil.colorize("&e交易详情已显示在上方"));
                    return;
                }
            }
        }
    }

    private void handlePlayerTradeMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                resourceMenu.openMainMenu(player, nationId);
            } else {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 收到的交易请求
        if (itemName.contains("收到的交易请求") || item.getType() == Material.REDSTONE_BLOCK) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令查看: &f/trade received"));
            return;
        }

        // 发出的交易请求
        if (itemName.contains("发出的交易请求") || item.getType() == Material.LAPIS_BLOCK) {
            player.sendMessage(MessageUtil.colorize("&e请使用指令查看: &f/trade sent"));
            return;
        }

        // 发起新交易
        if (itemName.contains("发起新交易") || item.getType() == Material.EMERALD) {
            player.sendMessage(MessageUtil.colorize("&e使用方法: &f/trade offer <玩家> <资源> <数量> <单价>"));
            player.sendMessage(MessageUtil.colorize("&7示例: &f/trade offer Steve iron 100 5.5"));
            return;
        }

        // 快速买入
        if (itemName.contains("快速买入") || item.getType() == Material.GOLD_INGOT) {
            player.sendMessage(MessageUtil.colorize("&e使用方法: &f/trade buy <资源> <数量>"));
            return;
        }

        // 快速卖出
        if (itemName.contains("快速卖出") || item.getType() == Material.IRON_INGOT) {
            player.sendMessage(MessageUtil.colorize("&e使用方法: &f/trade sell <资源> <数量>"));
            return;
        }
    }

    private void handleReserveMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            NationId nationId = getPlayerNationId(player);
            if (nationId != null) {
                resourceMenu.openMainMenu(player, nationId);
            } else {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 点击资源进行存取操作
        if (meta.lore() != null && meta.lore().stream().anyMatch(l -> {
            String loreText = PlainTextComponentSerializer.plainText().serialize(l);
            return loreText.contains("存入") || loreText.contains("取出");
        })) {
            String resourceId = extractResourceId(itemName);
            if (resourceId != null) {
                NationId nationId = getPlayerNationId(player);
                if (nationId != null) {
                    // 从真实服务获取储备数据
                    long currentReserve = reserveService != null
                        ? reserveService.getReserveAmount(nationId, resourceId)
                        : 0;
                    long reserveGoal = reserveService != null
                        ? reserveService.getReserveGoal(nationId, resourceId)
                        : 0;
                    double progress = reserveGoal > 0 ? (double) currentReserve / reserveGoal : 0;

                    if (event.isLeftClick()) {
                        // 存入操作
                        player.sendMessage(MessageUtil.colorize("&e存入 &f" + resourceId + " &e到战略储备"));
                        player.sendMessage(MessageUtil.colorize("&7当前储备: &f" + formatNumber(currentReserve) + " &7/ " + formatNumber(reserveGoal)));
                        player.sendMessage(MessageUtil.colorize("&7进度: &f" + String.format("%.1f", progress) + "%"));
                        player.sendMessage(MessageUtil.colorize("&7使用方法: &f/reserve deposit " + resourceId + " <数量>"));
                    } else {
                        // 取出操作
                        player.sendMessage(MessageUtil.colorize("&e从战略储备取出 &f" + resourceId));
                        player.sendMessage(MessageUtil.colorize("&7当前储备: &f" + formatNumber(currentReserve)));
                        if (currentReserve > 0) {
                            player.sendMessage(MessageUtil.colorize("&7最大可取出: &a" + formatNumber(currentReserve)));
                        }
                        player.sendMessage(MessageUtil.colorize("&7使用方法: &f/reserve withdraw " + resourceId + " <数量>"));
                    }
                } else {
                    player.sendMessage(MessageUtil.colorize("&c你还没有加入任何国家！"));
                }
            }
        }
    }

    private void handleResourceDetailMenuClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        playClickSound(player);

        ItemMeta meta = item.getItemMeta();
        if (meta.displayName() == null) return;
        String itemName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());

        // 返回按钮
        if (itemName.contains("返回") || itemName.contains("Back")) {
            resourceMenu.openStockpileMenu(player, getPlayerNationId(player));
            return;
        }

        // 买入按钮
        if (itemName.contains("买入") || item.getType() == Material.GREEN_STAINED_GLASS) {
            String resourceId = selectedResource.get(player.getUniqueId());
            if (resourceId != null) {
                player.sendMessage(MessageUtil.colorize("&a买入 &f" + resourceId + " &a请使用指令: &f/market buy " + resourceId + " <数量>"));
            }
            return;
        }

        // 卖出按钮
        if (itemName.contains("卖出") || item.getType() == Material.RED_STAINED_GLASS) {
            String resourceId = selectedResource.get(player.getUniqueId());
            if (resourceId != null) {
                player.sendMessage(MessageUtil.colorize("&c卖出 &f" + resourceId + " &c请使用指令: &f/market sell " + resourceId + " <数量>"));
            }
            return;
        }

        // 加入储备按钮
        if (itemName.contains("加入储备") || item.getType() == Material.ENDER_CHEST) {
            String resourceId = selectedResource.get(player.getUniqueId());
            if (resourceId != null) {
                player.sendMessage(MessageUtil.colorize("&e加入战略储备: &f/reserve deposit " + resourceId + " <数量>"));
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            menuStates.remove(player.getUniqueId());
            selectedResource.remove(player.getUniqueId());
            playerPages.remove(player.getUniqueId());
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

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    private String extractResourceId(String itemName) {
        // 从名称中提取资源ID
        for (String resource : resourceService.availableResourceTypes()) {
            if (itemName.contains(resource)) {
                return resource;
            }
        }
        // 尝试中文名称
        return switch (itemName) {
            case String s when s.contains("食物") -> "food";
            case String s when s.contains("木材") -> "timber";
            case String s when s.contains("矿石") -> "ore";
            case String s when s.contains("石油") -> "oil";
            case String s when s.contains("稀有金属") -> "rare_metal";
            default -> null;
        };
    }

    private record MenuState(String menuType, NationId nationId, Map<String, Object> data) {}

    // E-036 修复: 玩家退出时清理 Map 状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        menuStates.remove(playerId);
        selectedResource.remove(playerId);
        playerPages.remove(playerId);
    }
}