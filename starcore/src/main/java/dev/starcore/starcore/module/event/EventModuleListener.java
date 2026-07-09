package dev.starcore.starcore.module.event;

import dev.starcore.starcore.module.diplomacy.alliance.event.AllianceBrokenEvent;
import dev.starcore.starcore.module.diplomacy.alliance.event.AllianceFormedEvent;
import dev.starcore.starcore.module.diplomacy.event.DiplomacyRelationChangedEvent;
import dev.starcore.starcore.module.faith.event.FaithBlessingEvent;
import dev.starcore.starcore.module.faith.event.FaithLevelChangedEvent;
import dev.starcore.starcore.module.faith.event.FaithPrayerEvent;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.event.PolicyActivatedEvent;
import dev.starcore.starcore.module.policy.event.PolicyExpiredEvent;
import dev.starcore.starcore.module.shop.event.ShopOpenEvent;
import dev.starcore.starcore.module.shop.event.ShopTransactionEvent;
import dev.starcore.starcore.module.war.event.WarDeclaredEvent;
import dev.starcore.starcore.module.war.event.WarEndedEvent;
import dev.starcore.starcore.module.war.event.WarStartedEvent;
import dev.starcore.starcore.module.weather.event.WeatherChangeEvent;
import dev.starcore.starcore.module.weather.event.WeatherResourceEffectEvent;
import dev.starcore.starcore.social.event.FriendRequestEvent;
import dev.starcore.starcore.social.event.GuildCreateEvent;
import dev.starcore.starcore.social.event.GuildInviteEvent;
import dev.starcore.starcore.social.event.PartyInviteEvent;
import dev.starcore.starcore.war.War;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 事件模块监听器
 * 监听各种 StarCore 事件并记录到事件日志
 */
public class EventModuleListener implements Listener {

    private final EventService eventService;
    private final NationIdProvider nationIdProvider;

    public EventModuleListener(EventService eventService, NationIdProvider nationIdProvider) {
        this.eventService = eventService;
        this.nationIdProvider = nationIdProvider;
    }

    /**
     * NationId 提供者接口
     */
    public interface NationIdProvider {
        NationId getNationId(Player player);
        NationId getNationId(String playerName);
    }

    // ========== 战争事件 ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarDeclared(WarDeclaredEvent event) {
        War war = event.getWar();
        String message = String.format("战争宣战: %s 宣战 (目标: %s)",
            war.aggressor(), war.defender());
        recordForAllParticipants(war, "war-declared", message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarStarted(WarStartedEvent event) {
        War war = event.getWar();
        String message = String.format("战争开始: %s vs %s", war.aggressor(), war.defender());
        recordForAllParticipants(war, "war-started", message);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWarEnded(WarEndedEvent event) {
        War war = event.getWar();
        String message = String.format("战争结束: %s vs %s (原因: %s)",
            war.aggressor(), war.defender(), event.getReason());
        recordForAllParticipants(war, "war-ended", message);
    }

    private void recordForAllParticipants(War war, String type, String message) {
        NationId aggressorId = war.aggressor();
        NationId defenderId = war.defender();
        if (aggressorId != null) {
            eventService.record(aggressorId, type, message);
        }
        if (defenderId != null) {
            eventService.record(defenderId, type, message);
        }
    }

    // ========== 外交事件 ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAllianceFormed(AllianceFormedEvent event) {
        eventService.record(event.getNation1(), "alliance-formed",
            "与 " + event.getNation2() + " 建立联盟");
        eventService.record(event.getNation2(), "alliance-formed",
            "与 " + event.getNation1() + " 建立联盟");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAllianceBroken(AllianceBrokenEvent event) {
        eventService.record(event.getNation1(), "alliance-broken",
            "与 " + event.getNation2() + " 的联盟破裂");
        eventService.record(event.getNation2(), "alliance-broken",
            "与 " + event.getNation1() + " 的联盟破裂");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDiplomacyRelationChanged(DiplomacyRelationChangedEvent event) {
        String message = String.format("外交关系变更: %s -> %s (关系: %s)",
            event.getLeftNation(), event.getRightNation(), event.getNewRelation());
        eventService.record(event.getLeftNation(), "diplomacy-changed", message);
        eventService.record(event.getRightNation(), "diplomacy-changed", message);
    }

    // ========== 政策事件 ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPolicyActivated(PolicyActivatedEvent event) {
        eventService.record(event.nationId(), "policy-activated",
            "政策激活: " + event.definition().displayName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPolicyExpired(PolicyExpiredEvent event) {
        eventService.record(event.nationId(), "policy-expired",
            "政策过期: " + (event.definitionOrNull() != null ? event.definitionOrNull().displayName() : event.state().policyKey()));
    }

    // ========== 天气事件 ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        NationId nationId = event.nationId();
        if (nationId != null) {
            eventService.record(nationId, "weather-change",
                "天气变化: " + event.newWeather() + " (原因: " + event.cause() + ")");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWeatherResourceEffect(WeatherResourceEffectEvent event) {
        NationId nationId = event.nationId();
        if (nationId != null) {
            var boosted = event.getBoostedResources();
            var reduced = event.getReducedResources();
            StringBuilder sb = new StringBuilder("天气资源效果:");
            if (!boosted.isEmpty()) {
                sb.append(" 增强: ").append(boosted);
            }
            if (!reduced.isEmpty()) {
                sb.append(" 削弱: ").append(reduced);
            }
            eventService.record(nationId, "weather-resource-effect", sb.toString());
        }
    }

    // ========== 商店事件 ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopOpen(ShopOpenEvent event) {
        Player player = event.getPlayer();
        if (player != null && nationIdProvider != null) {
            NationId nationId = nationIdProvider.getNationId(player);
            if (nationId != null) {
                eventService.record(nationId, "shop-open",
                    "玩家 " + player.getName() + " 打开商店");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopTransaction(ShopTransactionEvent event) {
        Player player = event.getPlayer();
        if (player != null && nationIdProvider != null) {
            NationId nationId = nationIdProvider.getNationId(player);
            if (nationId != null) {
                var item = event.getItem();
                String itemName = item != null ? item.material().name() : "未知物品";
                eventService.record(nationId, "shop-transaction",
                    "玩家 " + player.getName() + " 交易: " + event.getType() +
                    " (物品: " + itemName + ", 数量: " + event.getQuantity() + ")");
            }
        }
    }

    // ========== 社交事件 ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFriendRequest(FriendRequestEvent event) {
        // 记录发送方
        if (nationIdProvider != null) {
            NationId senderNation = nationIdProvider.getNationId(event.getSenderName());
            NationId targetNation = nationIdProvider.getNationId(event.getTargetName());
            if (senderNation != null) {
                eventService.record(senderNation, "friend-request-sent",
                    "向 " + event.getTargetName() + " 发送好友请求");
            }
            if (targetNation != null) {
                eventService.record(targetNation, "friend-request-received",
                    "收到来自 " + event.getSenderName() + " 的好友请求");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildCreate(GuildCreateEvent event) {
        // 记录创建者
        if (nationIdProvider != null && event.getCreator() != null) {
            NationId nationId = nationIdProvider.getNationId(event.getCreator());
            if (nationId != null) {
                eventService.record(nationId, "guild-created",
                    "玩家 " + event.getCreator().getName() + " 创建公会: " + event.getGuildName());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuildInvite(GuildInviteEvent event) {
        if (nationIdProvider != null) {
            NationId inviterNation = nationIdProvider.getNationId(event.getInviterName());
            NationId targetNation = nationIdProvider.getNationId(event.getTargetName());
            if (inviterNation != null) {
                eventService.record(inviterNation, "guild-invite-sent",
                    "向 " + event.getTargetName() + " 发送公会邀请");
            }
            if (targetNation != null) {
                eventService.record(targetNation, "guild-invite-received",
                    "收到来自 " + event.getInviterName() + " 的公会邀请");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPartyInvite(PartyInviteEvent event) {
        if (nationIdProvider != null) {
            NationId inviterNation = nationIdProvider.getNationId(event.getInviterName());
            NationId targetNation = nationIdProvider.getNationId(event.getTargetName());
            if (inviterNation != null) {
                eventService.record(inviterNation, "party-invite-sent",
                    "向 " + event.getTargetName() + " 发送派对邀请");
            }
            if (targetNation != null) {
                eventService.record(targetNation, "party-invite-received",
                    "收到来自 " + event.getInviterName() + " 的派对邀请");
            }
        }
    }

    // ========== 信仰事件 ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFaithPrayer(FaithPrayerEvent event) {
        String playerName = Bukkit.getPlayer(event.getPlayerId()) != null ?
            Bukkit.getPlayer(event.getPlayerId()).getName() : event.getPlayerId().toString();
        eventService.record(event.getNationId(), "faith-prayer",
            "信仰祈祷: " + playerName);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFaithLevelChanged(FaithLevelChangedEvent event) {
        eventService.record(event.getNationId(), "faith-level-changed",
            "信仰等级变化: " + event.getPreviousLevelName() + " -> " + event.getNewLevelName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFaithBlessing(FaithBlessingEvent event) {
        eventService.record(event.getNationId(), "faith-blessing",
            "信仰祝福: " + event.getBlessingType());
    }
}