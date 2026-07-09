package dev.starcore.starcore.module.faith.listener;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.faith.FaithService;
import dev.starcore.starcore.module.faith.event.FaithBlessingEvent;
import dev.starcore.starcore.module.faith.event.FaithLevelChangedEvent;
import dev.starcore.starcore.module.faith.event.FaithPrayerEvent;
import dev.starcore.starcore.module.faith.model.FaithData;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 信仰系统事件监听器
 * 处理玩家祈祷、祈福等交互
 */
public class FaithListener implements Listener {
    private final FaithService faithService;
    private final NationService nationService;
    private final TerritoryService territoryService;
    private final MessageService messages;

    public FaithListener(
        FaithService faithService,
        NationService nationService,
        TerritoryService territoryService,
        MessageService messages
    ) {
        this.faithService = faithService;
        this.nationService = nationService;
        this.territoryService = territoryService;
        this.messages = messages;
    }

    /**
     * 处理玩家祈祷交互（右键点击特定物品）
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理右键动作
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 检查是否为祈祷物品
        if (item == null || !isPrayerItem(item)) {
            return;
        }

        event.setCancelled(true);

        // 检查玩家是否在国家领土内
        Location location = player.getLocation();
        Optional<Nation> nationOpt = getNationAtLocation(location);

        if (nationOpt.isEmpty()) {
            sendMessage(player, messages.format("faith.not-in-territory"), NamedTextColor.RED);
            return;
        }

        Nation nation = nationOpt.get();

        // 检查玩家是否属于该国家
        Optional<Nation> playerNation = nationService.nationOf(player.getUniqueId());
        if (playerNation.isEmpty() || !playerNation.get().id().equals(nation.id())) {
            sendMessage(player, messages.format("faith.not-member"), NamedTextColor.RED);
            return;
        }

        // 记录祈祷
        faithService.recordPrayer(
            player.getUniqueId(),
            nation.id(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            location.getWorld().getName()
        );

        // 获取更新后的信仰数据
        FaithData faithData = faithService.getFaithData(nation.id()).orElse(null);
        if (faithData != null) {
            sendPrayerFeedback(player, nation, faithData);
        }
    }

    /**
     * 处理祈祷事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFaithPrayer(FaithPrayerEvent event) {
        // 可以在这里添加额外的祈祷事件处理
        // 例如：播放特效、记录日志等
    }

    /**
     * 处理信仰等级变化事件
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFaithLevelChanged(FaithLevelChangedEvent event) {
        NationId nationId = event.getNationId();

        // 获取国家名称
        String nationName = nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知国家");

        String message;
        if (event.isUpgraded()) {
            message = messages.format("faith.level-up",
                nationName,
                event.getPreviousLevelName(),
                event.getNewLevelName()
            );
            // 广播给所有在线玩家
            broadcastMessage(message, NamedTextColor.GOLD);
        } else {
            message = messages.format("faith.level-down",
                nationName,
                event.getPreviousLevelName(),
                event.getNewLevelName()
            );
            // 只广播给国家成员
            broadcastToNation(nationId, message, NamedTextColor.YELLOW);
        }
    }

    /**
     * 处理祈福事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFaithBlessing(FaithBlessingEvent event) {
        if (!event.isSuccess()) {
            return;
        }

        NationId nationId = event.getNationId();

        // 获取国家名称
        String nationName = nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知国家");

        String blessingName = getBlessingDisplayName(event.getBlessingType());
        String message = messages.format("faith.blessing-used",
            nationName,
            blessingName,
            event.getFaithCost()
        );

        broadcastToNation(nationId, message, NamedTextColor.AQUA);
    }

    /**
     * 检查物品是否为祈祷物品
     */
    private boolean isPrayerItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.BLAZE_ROD ||
               type == Material.WANDERING_TRADER_SPAWN_EGG ||
               type == Material.TOTEM_OF_UNDYING ||
               type.name().contains("BANNER") ||
               type.name().contains("PRISMARINE");
    }

    /**
     * 获取位置所属的国家
     */
    private Optional<Nation> getNationAtLocation(Location location) {
        ChunkCoordinate coordinate = new ChunkCoordinate(
            location.getWorld().getName(),
            location.getChunk().getX(),
            location.getChunk().getZ()
        );

        return territoryService.claimAt(coordinate)
            .flatMap(claim -> {
                String ownerId = claim.ownerId();
                try {
                    UUID uuid = UUID.fromString(ownerId);
                    return nationService.nationById(NationId.of(uuid));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            });
    }

    /**
     * 发送祈祷反馈
     */
    private void sendPrayerFeedback(Player player, Nation nation, FaithData faithData) {
        int currentFaith = faithData.faith();
        int level = faithService.getFaithLevel(nation.id());
        String levelName = faithService.getFaithLevelName(level);

        // 发送信仰信息
        String message = messages.format("faith.prayed",
            nation.name(),
            currentFaith,
            levelName
        );
        sendMessage(player, message, NamedTextColor.GREEN);

        // 显示进度条
        showFaithProgress(player, currentFaith, faithService.getMaxFaith(), levelName);
    }

    /**
     * 显示信仰进度条
     */
    private void showFaithProgress(Player player, int current, int max, String levelName) {
        int barLength = 20;
        int filledLength = (int) ((double) current / max * barLength);
        int emptyLength = barLength - filledLength;

        StringBuilder bar = new StringBuilder();
        bar.append(net.kyori.adventure.text.format.TextColor.fromHexString("#FFD700"));
        bar.append("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("|");
            } else {
                bar.append(net.kyori.adventure.text.format.TextColor.fromHexString("#555555"));
                bar.append("-");
            }
        }
        bar.append(net.kyori.adventure.text.format.TextColor.fromHexString("#FFD700"));
        bar.append("] ");
        bar.append(NamedTextColor.WHITE);
        bar.append(current).append("/").append(max);
        bar.append(" ").append(levelName);

        player.sendMessage(Component.text(bar.toString()));
    }

    /**
     * 发送消息给玩家
     */
    private void sendMessage(Player player, String message, NamedTextColor color) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(Component.text(message, color));
        }
    }

    /**
     * 广播消息给所有玩家
     */
    private void broadcastMessage(String message, NamedTextColor color) {
        if (message != null && !message.isEmpty()) {
            Bukkit.broadcast(Component.text(message, color));
        }
    }

    /**
     * 广播消息给国家成员
     */
    private void broadcastToNation(NationId nationId, String message, NamedTextColor color) {
        if (message == null || message.isEmpty()) {
            return;
        }

        nationService.nationById(nationId).ifPresent(nation -> {
            for (var member : nation.members()) {
                UUID playerId = member.playerId();
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text(message, color));
                }
            }
        });
    }

    /**
     * 获取祈福类型显示名称
     */
    private String getBlessingDisplayName(String blessingType) {
        return switch (blessingType.toLowerCase()) {
            case "prosperity" -> "繁荣祈福";
            case "protection" -> "守护祈福";
            case "harvest" -> "丰收祈福";
            case "blessing" -> "通用祈福";
            default -> blessingType;
        };
    }
}