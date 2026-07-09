package dev.starcore.starcore.module.resource.listener;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.resource.gui.ResourceMenu;
import dev.starcore.starcore.module.resource.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * 资源系统事件监听器
 */
public class ResourceListener implements Listener {

    private final ResourceService resourceService;
    private final ResourcePriceService priceService;
    private final ResourceTradeService tradeService;
    private final ResourceReserveService reserveService;
    private final ProcessingService processingService;
    private final ResourceMenu resourceMenu;
    private final StarCoreEventBus eventBus;
    private final NationService nationService;

    public ResourceListener(
            ResourceService resourceService,
            ResourcePriceService priceService,
            ResourceTradeService tradeService,
            ResourceReserveService reserveService,
            ProcessingService processingService,
            ResourceMenu resourceMenu,
            StarCoreEventBus eventBus,
            NationService nationService
    ) {
        this.resourceService = resourceService;
        this.priceService = priceService;
        this.tradeService = tradeService;
        this.reserveService = reserveService;
        this.processingService = processingService;
        this.resourceMenu = resourceMenu;
        this.eventBus = eventBus;
        this.nationService = nationService;
    }

    private NationId getPlayerNationId(Player player) {
        return nationService.nationOf(player.getUniqueId()).map(Nation::getId).orElse(null);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        var titleComponent = event.getView().title();
        if (titleComponent == null) return;
        String title = PlainTextComponentSerializer.plainText().serialize(titleComponent);

        // 处理主菜单点击
        if (title.contains("资源管理")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            switch (slot) {
                case 11 -> resourceMenu.openStockpileMenu(player, getPlayerNationId(player));
                case 13 -> resourceMenu.openMarketMenu(player);
                case 15 -> resourceMenu.openReserveMenu(player, getPlayerNationId(player));
            }
            return;
        }

        // 处理储量菜单点击
        if (title.contains("储量")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 处理市场菜单点击
        if (title.contains("市场")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 31) {
                resourceMenu.openMainMenu(player);
            }
            return;
        }

        // 处理储备菜单
        if (title.contains("储备")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 31) {
                resourceMenu.openMainMenu(player);
            }
        }
    }

    /**
     * 订阅贸易事件
     */
    public void subscribeToTradeEvents() {
        // 事件订阅逻辑
    }
}
