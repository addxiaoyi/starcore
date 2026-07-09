package dev.starcore.starcore.module.sovereignty.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.sovereignty.SovereigntyService;
import dev.starcore.starcore.module.sovereignty.event.SovereigntyDeclaredEvent;
import dev.starcore.starcore.module.sovereignty.event.SovereigntyRevokedEvent;
import dev.starcore.starcore.module.sovereignty.event.SovereigntyStatusChangedEvent;
import dev.starcore.starcore.module.sovereignty.model.SovereigntyRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主权声明事件监听器
 * 处理主权相关的玩家交互和事件
 */
public final class SovereigntyListener implements Listener {

    private final SovereigntyService sovereigntyService;
    private final NationService nationService;
    private final MessageService messages;

    // 玩家位置追踪（避免频繁发送消息）
    private final Map<String, String> playerRegionCache = new ConcurrentHashMap<>();
    // 进入主权区域的消息冷却（毫秒）
    private static final long MESSAGE_COOLDOWN_MS = 5000;

    public SovereigntyListener(
            SovereigntyService sovereigntyService,
            NationService nationService,
            MessageService messages
    ) {
        this.sovereigntyService = sovereigntyService;
        this.nationService = nationService;
        this.messages = messages;
    }

    /**
     * 监听主权声明事件
     * 当国家声明主权时，可以触发相关通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSovereigntyDeclared(SovereigntyDeclaredEvent event) {
        SovereigntyRecord sovereignty = event.getSovereignty();
        Nation nation = nationService.nationById(sovereignty.nationId()).orElse(null);

        String nationName = nation != null ? nation.name() : "Unknown Nation";
        String message = messages.format(
                "sovereignty.event.declared",
                nationName,
                sovereignty.regionName(),
                sovereignty.significance().displayName()
        );

        // 可以广播给所有在线玩家或在附近玩家
        // 这里仅记录日志
        org.bukkit.Bukkit.getLogger().info("[Sovereignty] " + message);
    }

    /**
     * 监听主权撤销事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSovereigntyRevoked(SovereigntyRevokedEvent event) {
        SovereigntyRecord sovereignty = event.getSovereignty();
        Nation nation = nationService.nationById(sovereignty.nationId()).orElse(null);

        String nationName = nation != null ? nation.name() : "Unknown Nation";
        String message = messages.format(
                "sovereignty.event.revoked",
                nationName,
                sovereignty.regionName()
        );

        org.bukkit.Bukkit.getLogger().info("[Sovereignty] " + message);
    }

    /**
     * 监听主权状态变更事件
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSovereigntyStatusChanged(SovereigntyStatusChangedEvent event) {
        SovereigntyRecord sovereignty = event.getSovereignty();
        String message = messages.format(
                "sovereignty.event.status-changed",
                sovereignty.regionName(),
                event.getPreviousStatus(),
                event.getNewStatus()
        );

        org.bukkit.Bukkit.getLogger().info("[Sovereignty] " + message);
    }

    /**
     * 监听玩家移动，进入主权区域时显示通知
     * 注意：此功能需要与 RegionModule 配合使用
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只检查跨区块移动
        if (isSameChunk(event.getFrom(), event.getTo())) {
            return;
        }

        Player player = event.getPlayer();

        // 获取玩家所在的主权区域
        String currentRegion = getCurrentRegion(event.getTo().getChunk());
        String cacheKey = player.getUniqueId().toString();

        String cachedRegion = playerRegionCache.get(cacheKey);

        // 如果区域发生变化（进入新区域或离开区域）
        if ((currentRegion != null && !currentRegion.equals(cachedRegion)) ||
                (currentRegion == null && cachedRegion != null)) {

            if (currentRegion != null) {
                // 进入主权区域
                handleEnterSovereigntyRegion(player, currentRegion);
            } else {
                // 离开主权区域
                handleLeaveSovereigntyRegion(player, cachedRegion);
            }

            playerRegionCache.put(cacheKey, currentRegion != null ? currentRegion : "");
        }
    }

    /**
     * 监听玩家传送事件
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // 传送时清除缓存，下次移动时会重新检测
        playerRegionCache.remove(event.getPlayer().getUniqueId().toString());
    }

    /**
     * 获取当前位置所属的主权区域
     * 通过检查所有主权声明的领土区块
     */
    private String getCurrentRegion(org.bukkit.Chunk chunk) {
        String world = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        for (SovereigntyRecord sovereignty : sovereigntyService.getAllActiveSovereignties()) {
            var claims = sovereigntyService.getClaimedTerritories(sovereignty.id());
            for (var claim : claims) {
                if (claim.world().equals(world) && claim.chunkX() == chunkX && claim.chunkZ() == chunkZ) {
                    return sovereignty.regionName();
                }
            }
        }

        return null;
    }

    /**
     * 处理进入主权区域
     */
    private void handleEnterSovereigntyRegion(Player player, String regionName) {
        Optional<SovereigntyRecord> sovereigntyOpt = sovereigntyService.getSovereigntyByRegion(regionName);
        if (sovereigntyOpt.isEmpty()) {
            return;
        }

        SovereigntyRecord sovereignty = sovereigntyOpt.get();
        Nation nation = nationService.nationById(sovereignty.nationId()).orElse(null);

        String nationName = nation != null ? nation.name() : "Unknown Nation";

        // 构建进入主权区域的提示
        Component title = Component.text("[ " + nationName + " ]", NamedTextColor.GOLD)
                .append(Component.text(" - " + sovereignty.significance().displayName() + " Sovereignty", NamedTextColor.YELLOW));

        Component subtitle = Component.text(regionName, NamedTextColor.WHITE);

        player.sendTitle(title.toString(), subtitle.toString(), 10, 40, 10);

        // 发送聊天消息
        String message = messages.format(
                "sovereignty.region.enter",
                nationName,
                regionName,
                sovereignty.status().displayName()
        );
        player.sendMessage(Component.text(message, NamedTextColor.GRAY));
    }

    /**
     * 处理离开主权区域
     */
    private void handleLeaveSovereigntyRegion(Player player, String regionName) {
        Optional<SovereigntyRecord> sovereigntyOpt = sovereigntyService.getSovereigntyByRegion(regionName);
        if (sovereigntyOpt.isEmpty()) {
            return;
        }

        SovereigntyRecord sovereignty = sovereigntyOpt.get();

        // 离开时发送简短提示
        String message = messages.format(
                "sovereignty.region.leave",
                regionName
        );
        player.sendMessage(Component.text(message, NamedTextColor.DARK_GRAY));
    }

    /**
     * 检查两个位置是否在同一个区块
     */
    private boolean isSameChunk(org.bukkit.Location from, org.bukkit.Location to) {
        if (from == null || to == null) {
            return true;
        }
        return from.getWorld().equals(to.getWorld())
                && from.getChunk().getX() == to.getChunk().getX()
                && from.getChunk().getZ() == to.getChunk().getZ();
    }

    /**
     * 清除玩家区域缓存
     */
    public void clearPlayerCache() {
        playerRegionCache.clear();
    }
}