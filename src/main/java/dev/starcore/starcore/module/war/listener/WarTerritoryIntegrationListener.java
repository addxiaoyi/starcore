package dev.starcore.starcore.module.war.listener;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.war.event.WarEndedEvent;
import dev.starcore.starcore.module.war.event.WarStartedEvent;
import dev.starcore.starcore.war.War;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战争与领地集成监听器
 * 负责战争状态变化时的领地保护响应
 *
 * 功能:
 * 1. 宣战时通知领地系统进入战争状态
 * 2. 战争中允许敌对领土攻击
 * 3. 战争结束后恢复领地保护
 * 4. 处理领土被占领后的保护状态变化
 */
public class WarTerritoryIntegrationListener implements Listener {
    private final Plugin plugin;
    private final WarService warService;
    private final NationService nationService;
    private final TerritoryService territoryService;
    private final StarCoreEventBus eventBus;

    // 战争期间的被占领区块追踪
    private final ConcurrentHashMap<ChunkCoordinate, OccupiedChunkInfo> occupiedChunks = new ConcurrentHashMap<>();

    /**
     * 被占领区块信息
     */
    private record OccupiedChunkInfo(
        ChunkCoordinate coordinate,
        NationId originalOwner,
        NationId conqueror,
        long occupiedAt,
        UUID conquerorPlayerId
    ) {}

    public WarTerritoryIntegrationListener(
        Plugin plugin,
        WarService warService,
        NationService nationService,
        TerritoryService territoryService,
        StarCoreEventBus eventBus
    ) {
        this.plugin = plugin;
        this.warService = warService;
        this.nationService = nationService;
        this.territoryService = territoryService;
        this.eventBus = eventBus;

        // 注册战争事件监听
        if (eventBus != null) {
            eventBus.subscribe(WarStartedEvent.class, this::onWarStarted);
            eventBus.subscribe(WarEndedEvent.class, this::onWarEnded);
        }
    }

    /**
     * 战争开始处理
     * 清空之前的占领数据（如果有）
     */
    private void onWarStarted(WarStartedEvent event) {
        War war = event.getWar();
        String aggressorName = getNationName(war.aggressor());
        String defenderName = getNationName(war.defender());

        plugin.getLogger().info("战争开始: " + aggressorName + " vs " + defenderName);

        // 通知所有在线玩家战争开始（如果领地有入侵提示的话）
        broadcastWarStatus(war, true);
    }

    /**
     * 战争结束处理
     * 清理战争状态，恢复领地保护
     */
    private void onWarEnded(WarEndedEvent event) {
        War war = event.getWar();
        String aggressorName = getNationName(war.aggressor());
        String defenderName = getNationName(war.defender());
        WarEndedEvent.WarEndReason reason = event.getReason();

        plugin.getLogger().info("战争结束: " + aggressorName + " vs " + defenderName + ", 原因: " + reason);

        // 处理被占领领土的归属
        handleConqueredTerritories(war, reason);

        // 通知领地系统战争结束
        broadcastWarStatus(war, false);

        // 清空该战争的占领记录
        clearOccupiedChunks(war.id());
    }

    /**
     * 处理被占领的领土
     * 根据战争结束原因决定是否转移领土所有权
     */
    private void handleConqueredTerritories(War war, WarEndedEvent.WarEndReason reason) {
        // 根据战争结束原因处理占领领土
        switch (reason) {
            case SURRENDER -> handleSurrender(war);
            case PEACE_TREATY -> handlePeaceTreaty(war);
            case TIMEOUT, MAX_DURATION, ADMIN_FORCE, UNKNOWN -> restoreOriginalOwners(war);
        }
    }

    /**
     * 处理投降 - 战胜方获得部分领土
     */
    private void handleSurrender(War war) {
        // 获取被占领的区块
        for (var entry : occupiedChunks.entrySet()) {
            OccupiedChunkInfo info = entry.getValue();
            if (info.conqueror().equals(war.aggressor())) {
                // 侵略方占领了防御方的领土
                transferChunkOwnership(info.coordinate(), war.defender(), war.aggressor());
            } else if (info.conqueror().equals(war.defender())) {
                // 防御方占领了侵略方的领土
                transferChunkOwnership(info.coordinate(), war.aggressor(), war.defender());
            }
        }
    }

    /**
     * 处理和平条约 - 保持现状但可能交换部分领土
     */
    private void handlePeaceTreaty(War war) {
        // 和平条约通常会交换一些占领的领土
        // 这里实现一个简单的逻辑：保持50%的占领领土
        int keepCount = 0;
        int maxKeep = occupiedChunks.size() / 2;

        for (var entry : occupiedChunks.entrySet()) {
            if (keepCount >= maxKeep) {
                // 恢复到原所有者
                restoreChunkOwnership(entry.getValue());
            }
            keepCount++;
        }
    }

    /**
     * 恢复到原始所有者（超时/管理员结束等情况）
     */
    private void restoreOriginalOwners(War war) {
        for (var entry : occupiedChunks.entrySet()) {
            OccupiedChunkInfo info = entry.getValue();
            restoreChunkOwnership(info);
        }
    }

    /**
     * 转移区块所有权
     */
    private void transferChunkOwnership(ChunkCoordinate coordinate, NationId from, NationId to) {
        // 先 unclaim
        territoryService.unclaim(from.toString(), coordinate);
        // 再 claim
        territoryService.claim(to.toString(), coordinate);

        plugin.getLogger().info("领土转移: " + coordinate + " 从 " + from + " 到 " + to);
    }

    /**
     * 恢复区块到原始所有者
     */
    private void restoreChunkOwnership(OccupiedChunkInfo info) {
        // 获取当前占领者
        Optional<NationId> currentOwner = territoryService.claimAt(info.coordinate())
            .map(claim -> {
                try {
                    return new NationId(UUID.fromString(claim.ownerId()));
                } catch (Exception e) {
                    return null;
                }
            });

        // 如果当前所有者不是原始所有者，则恢复
        if (currentOwner.isPresent() && !currentOwner.get().equals(info.originalOwner())) {
            territoryService.unclaim(currentOwner.get().toString(), info.coordinate());
            territoryService.claim(info.originalOwner().toString(), info.coordinate());
            plugin.getLogger().info("领土恢复: " + info.coordinate() + " 恢复到 " + info.originalOwner());
        }
    }

    /**
     * 清空战争的占领记录
     */
    private void clearOccupiedChunks(UUID warId) {
        occupiedChunks.entrySet().removeIf(entry -> {
            // 这里可以添加战争ID关联，不过目前简化处理
            return true;
        });
    }

    /**
     * 记录占领区块
     */
    public void recordChunkOccupation(ChunkCoordinate coordinate, NationId originalOwner,
                                       NationId conqueror, UUID conquerorPlayerId) {
        occupiedChunks.put(coordinate, new OccupiedChunkInfo(
            coordinate, originalOwner, conqueror, System.currentTimeMillis(), conquerorPlayerId
        ));
    }

    /**
     * 广播战争状态变化
     */
    private void broadcastWarStatus(War war, boolean atWar) {
        // 获取参战国家的所有在线成员
        for (NationId nationId : war.allParticipants()) {
            getOnlineNationMembers(nationId).forEach(player -> {
                if (atWar) {
                    player.sendMessage("§c[战争] 您的国家已进入战争状态！敌方可以攻击您的领土！");
                } else {
                    player.sendMessage("§a[战争] 战争已结束，领地保护已恢复。");
                }
            });
        }
    }

    /**
     * 获取国家所有在线成员
     */
    private Collection<? extends Player> getOnlineNationMembers(NationId nationId) {
        Optional<Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return nationOpt.get().members().stream()
            .map(member -> Bukkit.getPlayer(member.playerId()))
            .filter(player -> player != null && player.isOnline())
            .toList();
    }

    /**
     * 检查两个国家是否处于战争状态
     */
    public boolean isAtWar(NationId nation1, NationId nation2) {
        return warService.atWar(nation1, nation2);
    }

    /**
     * 检查某区块是否被占领
     */
    public boolean isChunkOccupied(ChunkCoordinate coordinate) {
        return occupiedChunks.containsKey(coordinate);
    }

    /**
     * 获取区块的占领信息
     */
    public Optional<OccupiedChunkInfo> getOccupationInfo(ChunkCoordinate coordinate) {
        return Optional.ofNullable(occupiedChunks.get(coordinate));
    }

    /**
     * 获取国家名称
     */
    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("Unknown");
    }
}
