package dev.starcore.starcore.module.dynasty.listener;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.dynasty.DynastyService;
import dev.starcore.starcore.module.dynasty.event.DynastyCreatedEvent;
import dev.starcore.starcore.module.dynasty.event.InterregnumEvent;
import dev.starcore.starcore.module.dynasty.event.SuccessionEvent;
import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * 王朝事件监听器
 * 处理与王朝相关的事件
 */
public final class DynastyListener implements Listener {
    private final DynastyService dynastyService;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;
    private final JavaPlugin plugin;

    public DynastyListener(DynastyService dynastyService, NationService nationService, OnlinePlayerDirectory onlinePlayerDirectory, JavaPlugin plugin) {
        this.dynastyService = dynastyService;
        this.nationService = nationService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
        this.plugin = plugin;
    }

    /**
     * 王朝创建事件处理
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDynastyCreated(DynastyCreatedEvent event) {
        // 广播王朝创建消息
        String message = String.format("%s 建立了 %s 王朝，%s 成为首位君主！",
                event.getFounderName(),
                event.getDynasty().dynastyName(),
                event.getDynasty().currentMonarchName());

        broadcastToNation(event.getNationId(), Component.text(message, NamedTextColor.GOLD));
    }

    /**
     * 继承事件处理
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSuccession(SuccessionEvent event) {
        Dynasty dynasty = event.getDynasty();
        NationId nationId = event.getNationId();

        String message;
        NamedTextColor color;

        switch (event.getKind()) {
            case ABDICATION -> {
                message = String.format("%s 禅让王位给 %s（原因：%s）",
                        event.getPreviousMonarchName(),
                        event.getNewMonarchName(),
                        event.getReason());
                color = NamedTextColor.AQUA;
            }
            case INHERITANCE -> {
                message = String.format("%s 继承王位，成为新的君主！",
                        event.getNewMonarchName());
                color = NamedTextColor.GREEN;
            }
            case CORONATION -> {
                message = String.format("%s 成功加冕，成为新的君主！",
                        event.getNewMonarchName());
                color = NamedTextColor.GOLD;
            }
            case FORCE_MAJORE -> {
                message = String.format("由于特殊情况，%s 成为新的君主！",
                        event.getNewMonarchName());
                color = NamedTextColor.YELLOW;
            }
            default -> {
                message = String.format("王位传承完成，%s 成为新的君主",
                        event.getNewMonarchName());
                color = NamedTextColor.WHITE;
            }
        }

        broadcastToNation(nationId, Component.text(message, color));
    }

    /**
     * 王位空缺事件处理
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInterregnum(InterregnumEvent event) {
        Dynasty dynasty = event.getDynasty();
        NationId nationId = event.getNationId();

        String causeMessage = switch (event.getCause()) {
            case DEATH -> "君主驾崩";
            case ABDICATION -> "君主退位";
            case EXILE -> "君主被流放";
            case DEPOSITION -> "君主被废黜";
            default -> "未知原因";
        };

        String message = String.format("王位空缺！%s，%s。国家处于空位期。",
                causeMessage,
                dynasty.successionType().displayName());

        broadcastToNation(nationId, Component.text(message, NamedTextColor.RED));

        // 通知潜在继承人
        Optional<Dynasty.HeirInfo> firstHeir = dynasty.getFirstHeir();
        if (firstHeir.isPresent()) {
            Dynasty.HeirInfo heir = firstHeir.get();
            UUID heirId = heir.playerId();
            Player heirPlayer = onlinePlayerDirectory.findOnlinePlayer(heirId).orElse(null);
            if (heirPlayer != null) {
                heirPlayer.sendMessage(Component.text(
                        "你作为第一顺位继承人，可以使用 /dynasty inherit 继承王位",
                        NamedTextColor.YELLOW
                ));
            }
        }
    }

    /**
     * 玩家加入事件 - 检查是否是潜在继承人
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查玩家所在国家的王朝状态
        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        Optional<Dynasty> dynastyOpt = dynastyService.getDynasty(nation.id());
        if (dynastyOpt.isEmpty()) {
            return;
        }

        Dynasty dynasty = dynastyOpt.get();

        // 如果处于空缺期且玩家是继承人
        if (dynasty.isInInterregnum() && dynasty.hasHeir(playerId)) {
            Optional<Integer> position = dynasty.getHeirPosition(playerId);
            if (position.isPresent() && position.get() == 1) {
                // 延迟发送消息，确保玩家已经完全进入服务器
                player.getServer().getScheduler().runTaskLater(
                        plugin,
                        () -> {
                            player.sendMessage(Component.text(""));
                            player.sendMessage(Component.text("=== 王位通知 ===", NamedTextColor.GOLD));
                            player.sendMessage(Component.text(
                                    nation.name() + " 的王位空缺，你作为第一顺位继承人可以使用 /dynasty inherit 继承王位！",
                                    NamedTextColor.YELLOW
                            ));
                            player.sendMessage(Component.text(""));
                        },
                        20L // 1秒后执行
                );
            }
        }
    }

    /**
     * 玩家离开事件 - 更新继承人信息
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查玩家是否是君主
        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        Optional<Dynasty> dynastyOpt = dynastyService.getDynasty(nation.id());
        if (dynastyOpt.isEmpty()) {
            return;
        }

        Dynasty dynasty = dynastyOpt.get();

        // 如果玩家是君主且离开服务器，可以触发一些特殊处理
        if (dynasty.isMonarch(playerId)) {
            // 这里可以添加君主离线时的特殊逻辑
            // 例如：增加空缺风险、降低国家士气等
        }
    }

    /**
     * 玩家死亡事件 - 君主死亡导致王位空缺
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // 检查玩家是否是君主
        Optional<Nation> nationOpt = nationService.nationOf(playerId);
        if (nationOpt.isEmpty()) {
            return;
        }

        Nation nation = nationOpt.get();
        Optional<Dynasty> dynastyOpt = dynastyService.getDynasty(nation.id());
        if (dynastyOpt.isEmpty()) {
            return;
        }

        Dynasty dynasty = dynastyOpt.get();

        // 如果玩家是君主且使用长子继承制/血统继承制，死亡后自动空缺
        if (dynasty.isMonarch(playerId)) {
            switch (dynasty.successionType()) {
                case MALE_PREMIogeniture, PRIMOGENITURE, HEREDITARY -> {
                    // 这些继承类型下君主死亡会导致空缺
                    // 注意：这只是一个事件处理，实际的空缺处理可能需要更多逻辑
                }
            }
        }
    }

    /**
     * 向国家成员广播消息
     */
    private void broadcastToNation(NationId nationId, Component message) {
        Nation nation = nationService.nationById(nationId).orElse(null);
        if (nation == null) {
            return;
        }

        for (NationMember member : nation.members()) {
            onlinePlayerDirectory.findOnlinePlayer(member.playerId())
                    .ifPresent(player -> player.sendMessage(message));
        }
    }
}