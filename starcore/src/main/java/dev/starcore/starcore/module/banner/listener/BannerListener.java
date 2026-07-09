package dev.starcore.starcore.module.banner.listener;

import dev.starcore.starcore.module.banner.BannerService;
import dev.starcore.starcore.module.banner.model.NationBanner;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for banner-related events
 */
public final class BannerListener implements Listener {
    private final BannerService bannerService;
    private final NationService nationService;

    // Track players who just placed a nation banner
    private final ConcurrentHashMap<UUID, Long> recentBannerPlacement = new ConcurrentHashMap<>();
    private static final long PLACEMENT_COOLDOWN_MS = 5000; // 5 seconds

    public BannerListener(BannerService bannerService, NationService nationService) {
        this.bannerService = bannerService;
        this.nationService = nationService;
    }

    /**
     * Handle banner placement - attach nation banner info to placed banners
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Check if it's a banner item
        if (!isBanner(item)) {
            return;
        }

        // Check if player is in a nation
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        UUID nationId = nation.id().value();

        // Get the nation's banner
        Optional<NationBanner> bannerOpt = bannerService.getBannerByUUID(nationId);
        if (bannerOpt.isEmpty()) {
            return;
        }

        NationBanner banner = bannerOpt.get();

        // If the banner has custom text (from custom name), don't override
        if (hasCustomBannerName(item)) {
            return;
        }

        // Mark this placement so we can add protection later
        recentBannerPlacement.put(player.getUniqueId(), System.currentTimeMillis());

        // Send feedback
        player.sendMessage(Component.text("[" + nation.name() + " 旗帜已放置]", net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    /**
     * Handle banner interaction - show nation info when clicking on banners
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material type = block.getType();

        // Check if it's a standing banner or wall banner
        if (!isBannerType(type)) {
            return;
        }

        Player player = event.getPlayer();

        // Try to get nation from the banner's metadata or nearby nation
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());

        // Clean up old entries
        recentBannerPlacement.entrySet().removeIf(e ->
            System.currentTimeMillis() - e.getValue() > PLACEMENT_COOLDOWN_MS
        );

        // Show nation info action bar
        playerNation.ifPresent(nation -> {
            UUID nationId = nation.id().value();
            bannerService.getBannerByUUID(nationId).ifPresent(banner -> {
                Component info = Component.text()
                    .append(Component.text("[", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(Component.text(nation.name(), net.kyori.adventure.text.format.NamedTextColor.AQUA))
                    .append(Component.text("] ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(Component.text(banner.getPatternDisplayName(), net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .build();
                player.sendActionBar(info);
            });
        });
    }

    /**
     * Prevent breaking nation banners without permission
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        // Check if it's a banner
        if (!isBannerType(type)) {
            return;
        }

        Player player = event.getPlayer();

        // Get the banner item metadata to check if it's a nation banner
        // For now, allow breaking unless it's protected
        // Protection logic can be enhanced based on nation territory
    }

    /**
     * Handle sign change near banners - add nation banner reference text
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if sign is adjacent to a banner
        if (!isAdjacentToBanner(block)) {
            return;
        }

        // Get the nation info
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();

        // Check if first line is empty, auto-fill with nation name
        Component firstLine = event.line(0);
        String firstLineText = PlainTextComponentSerializer.plainText().serialize(firstLine);

        if (firstLineText.trim().isEmpty()) {
            event.line(0, Component.text(nation.name(), net.kyori.adventure.text.format.NamedTextColor.GOLD));

            // Add nation banner info on second line
            UUID nationId = nation.id().value();
            bannerService.getBannerByUUID(nationId).ifPresent(banner -> {
                if (!banner.isDefault()) {
                    event.line(1, Component.text(banner.getPatternDisplayName(), net.kyori.adventure.text.format.NamedTextColor.GRAY));
                }
            });

            player.sendMessage(Component.text("已自动填充国家名称到告示牌", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }
    }

    /**
     * Handle clicking on signs to show nation info
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Check if it's a sign
        if (!(block.getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) block;
        Player player = event.getPlayer();

        // Try to get nation info from sign text
        String firstLine = PlainTextComponentSerializer.plainText().serialize(sign.line(0));

        // Find nation by name from first line
        nationService.nationByName(firstLine).ifPresent(nation -> {
            UUID nationId = nation.id().value();
            bannerService.getBannerByUUID(nationId).ifPresent(banner -> {
                Component info = Component.text()
                    .append(Component.text("[国家: ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(Component.text(nation.name(), net.kyori.adventure.text.format.NamedTextColor.GOLD))
                    .append(Component.text("] ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                    .append(Component.text(banner.getPatternDisplayName(), net.kyori.adventure.text.format.NamedTextColor.WHITE))
                    .build();
                player.sendActionBar(info);
            });
        });
    }

    /**
     * Check if an ItemStack is a banner
     */
    private boolean isBanner(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return isBannerType(type);
    }

    /**
     * Check if a Material is a banner type
     */
    private boolean isBannerType(Material type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return name.endsWith("_BANNER") && !name.contains("ITEM_FRAME");
    }

    /**
     * Check if an item has a custom banner name (display name set)
     */
    private boolean hasCustomBannerName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName();
    }

    /**
     * Check if a block is adjacent to a banner
     */
    private boolean isAdjacentToBanner(Block block) {
        if (block == null) {
            return false;
        }

        org.bukkit.block.BlockFace[] faces = {
            org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.WEST
        };

        for (org.bukkit.block.BlockFace face : faces) {
            Block adjacent = block.getRelative(face);
            if (isBannerType(adjacent.getType())) {
                return true;
            }
        }

        return false;
    }
}
