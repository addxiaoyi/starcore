package dev.starcore.starcore.crossserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * 跨服同步服务
 * 支持Redis传输方式，统一管理所有跨服同步功能
 *
 * 功能:
 * 1. 玩家数据同步 (CrossServerPlayerSync)
 * 2. 聊天跨服 (CrossServerChatSync)
 * 3. 国家数据同步 (CrossServerNationSync)
 * 4. 战争状态同步 (CrossServerWarSync)
 * 5. 领土同步 (CrossServerTerritorySync)
 */
public final class CrossServerService {
    private final String serverId;
    private final Logger logger;
    private final Gson gson;

    // 子服务
    private CrossServerPlayerSync playerSync;
    private CrossServerChatSync chatSync;
    private CrossServerNationSync nationSync;
    private CrossServerWarSync warSync;
    private CrossServerTerritorySync territorySync;

    // 数据处理器
    private final Map<String, BiConsumer<UUID, Object>> dataHandlers = new ConcurrentHashMap<>();

    public CrossServerService(String serverId, Logger logger) {
        this.serverId = serverId;
        this.logger = logger;
        this.gson = new GsonBuilder().create();
    }

    /**
     * 初始化玩家数据同步
     */
    public void initializePlayerSync(
        dev.starcore.starcore.core.net.RedisCrossServerService redisService,
        dev.starcore.starcore.foundation.economy.InternalEconomyService economyService,
        dev.starcore.starcore.pvp.stats.PvPStatsService statsService,
        org.bukkit.plugin.Plugin plugin
    ) {
        this.playerSync = new CrossServerPlayerSync(plugin, redisService, economyService, statsService);
        logger.info("跨服玩家数据同步已初始化");
    }

    /**
     * 初始化聊天跨服
     */
    public void initializeChatSync(
        dev.starcore.starcore.core.net.RedisCrossServerService redisService,
        org.bukkit.plugin.Plugin plugin
    ) {
        this.chatSync = new CrossServerChatSync(plugin, redisService);
        logger.info("跨服聊天同步已初始化");
    }

    /**
     * 初始化国家数据同步
     */
    public void initializeNationSync(
        dev.starcore.starcore.core.net.RedisCrossServerService redisService,
        dev.starcore.starcore.module.nation.NationService nationService,
        dev.starcore.starcore.module.treasury.TreasuryService treasuryService,
        dev.starcore.starcore.module.diplomacy.DiplomacyService diplomacyService,
        org.bukkit.plugin.Plugin plugin
    ) {
        this.nationSync = new CrossServerNationSync(plugin, redisService, nationService, treasuryService, diplomacyService);
        logger.info("跨服国家数据同步已初始化");
    }

    /**
     * 初始化战争状态同步
     */
    public void initializeWarSync(
        dev.starcore.starcore.core.net.RedisCrossServerService redisService,
        dev.starcore.starcore.module.war.WarService warService,
        org.bukkit.plugin.Plugin plugin
    ) {
        this.warSync = new CrossServerWarSync(plugin, redisService, warService);
        // E-050: 关联到领土同步;若 territorySync 尚未初始化,记录告警,territorySync 后续 init 时会回填
        if (territorySync != null) {
            territorySync.setWarSync(warSync);
        } else {
            logger.warning("initializeWarSync: territorySync 尚未初始化,warSync<->territorySync 关联延迟至 territorySync init 时回填");
        }
        logger.info("跨服战争状态同步已初始化");
    }

    /**
     * 初始化领土同步
     */
    public void initializeTerritorySync(
        dev.starcore.starcore.core.net.RedisCrossServerService redisService,
        dev.starcore.starcore.foundation.territory.TerritoryService territoryService,
        dev.starcore.starcore.module.war.WarService warService,
        org.bukkit.plugin.Plugin plugin
    ) {
        this.territorySync = new CrossServerTerritorySync(plugin, territoryService, redisService, warService);
        // E-050: 关联到战争同步;若 warSync 尚未初始化,记录告警
        if (warSync != null) {
            territorySync.setWarSync(warSync);
        } else {
            logger.warning("initializeTerritorySync: warSync 尚未初始化,warSync<->territorySync 关联延迟至 warSync init 时回填");
        }
        logger.info("跨服领土同步已初始化");
    }

    // ==================== 玩家数据同步接口 ====================

    /**
     * 同步玩家数据
     */
    public void syncPlayerData(UUID playerId) {
        if (playerSync != null) {
            playerSync.syncPlayerData(playerId);
        }
    }

    /**
     * 同步玩家特定类型数据
     */
    public void syncPlayerData(UUID playerId, String dataType) {
        if (playerSync != null) {
            playerSync.syncPlayerData(playerId, dataType);
        }
    }

    /**
     * 检查玩家是否在任意服务器在线
     */
    public boolean isPlayerOnline(UUID playerId) {
        return playerSync != null && playerSync.isPlayerOnline(playerId);
    }

    /**
     * 获取跨服在线玩家列表
     */
    public Collection<UUID> getCrossServerOnlinePlayers() {
        return playerSync != null ? playerSync.getCrossServerOnlinePlayers() : Collections.emptyList();
    }

    // ==================== 聊天同步接口 ====================

    /**
     * 发送国家频道聊天
     */
    public void sendNationChat(UUID senderId, String senderName, UUID nationId, String message) {
        if (chatSync != null) {
            chatSync.sendNationChat(senderId, senderName, nationId, message);
        }
    }

    /**
     * 发送派系频道聊天
     */
    public void sendClanChat(UUID senderId, String senderName, UUID clanId, String message) {
        if (chatSync != null) {
            chatSync.sendClanChat(senderId, senderName, clanId, message);
        }
    }

    /**
     * 发送全球广播
     */
    public void sendGlobalBroadcast(String title, String message) {
        if (chatSync != null) {
            chatSync.sendGlobalBroadcast(title, message);
        }
    }

    /**
     * 设置玩家聊天模式
     */
    public void setPlayerChatMode(UUID playerId, String mode) {
        if (chatSync != null) {
            chatSync.setPlayerChatMode(playerId, mode);
        }
    }

    /**
     * 获取聊天服务是否启用
     */
    public boolean isChatSyncEnabled() {
        return chatSync != null && chatSync.isEnabled();
    }

    // ==================== 国家数据同步接口 ====================

    /**
     * 同步国家数据
     */
    public void syncNationData(dev.starcore.starcore.module.nation.model.NationId nationId) {
        if (nationSync != null) {
            nationSync.syncNationData(nationId);
        }
    }

    /**
     * 同步国家创建
     */
    public void syncNationCreated(dev.starcore.starcore.module.nation.model.NationId nationId, String nationName, UUID founderId, String founderName) {
        if (nationSync != null) {
            nationSync.syncNationCreated(nationId, nationName, founderId, founderName);
        }
    }

    /**
     * 同步国家解散
     */
    public void syncNationDisbanded(dev.starcore.starcore.module.nation.model.NationId nationId) {
        if (nationSync != null) {
            nationSync.syncNationDisbanded(nationId);
        }
    }

    /**
     * 同步国家成员变更
     */
    public void syncNationMemberChange(dev.starcore.starcore.module.nation.model.NationId nationId, UUID playerId, String playerName, String action) {
        if (nationSync != null) {
            nationSync.syncNationMemberChange(nationId, playerId, playerName, action);
        }
    }

    /**
     * 同步国家金库变更
     */
    public void syncNationTreasury(dev.starcore.starcore.module.nation.model.NationId nationId, double balance) {
        if (nationSync != null) {
            nationSync.syncNationTreasury(nationId, balance);
        }
    }

    /**
     * 获取缓存的国家名称
     */
    public String getCachedNationName(UUID nationId) {
        return nationSync != null ? nationSync.getCachedNationName(nationId) : null;
    }

    // ==================== 战争状态同步接口 ====================

    /**
     * 同步战争宣战
     */
    public void syncWarDeclared(dev.starcore.starcore.module.nation.model.NationId aggressor, dev.starcore.starcore.module.nation.model.NationId defender) {
        if (warSync != null) {
            warSync.syncWarDeclared(aggressor, defender);
        }
        if (territorySync != null) {
            territorySync.syncWarDeclared(aggressor, defender);
        }
    }

    /**
     * 同步战争开始
     */
    public void syncWarStarted(String warKey) {
        if (warSync != null) {
            warSync.syncWarStarted(warKey);
        }
        if (territorySync != null) {
            territorySync.syncWarStarted(warKey);
        }
    }

    /**
     * 同步战争结束
     */
    public void syncWarEnded(String warKey, String reason, dev.starcore.starcore.module.nation.model.NationId winner, dev.starcore.starcore.module.nation.model.NationId loser) {
        if (warSync != null) {
            warSync.syncWarEnded(warKey, reason, winner, loser);
        }
        if (territorySync != null) {
            territorySync.syncWarEnded(warKey, reason, winner, loser);
        }
    }

    /**
     * 检查国家是否处于战争状态
     */
    public boolean atWar(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return warSync != null && warSync.atWar(nationId);
    }

    /**
     * 检查两个国家是否处于战争状态
     */
    public boolean atWar(dev.starcore.starcore.module.nation.model.NationId left, dev.starcore.starcore.module.nation.model.NationId right) {
        return warSync != null && warSync.atWar(left, right);
    }

    // ==================== 领土同步接口 ====================

    /**
     * 同步领地声明
     */
    public void syncClaim(String ownerId, dev.starcore.starcore.foundation.territory.model.ChunkCoordinate coordinate) {
        if (territorySync != null) {
            territorySync.syncClaim(ownerId, coordinate);
        }
    }

    /**
     * 同步取消领地声明
     */
    public void syncUnclaim(String ownerId, dev.starcore.starcore.foundation.territory.model.ChunkCoordinate coordinate) {
        if (territorySync != null) {
            territorySync.syncUnclaim(ownerId, coordinate);
        }
    }

    /**
     * 同步领土征服
     */
    public void syncConquest(dev.starcore.starcore.foundation.territory.model.ChunkCoordinate coordinate, String fromOwnerId, String toOwnerId, UUID warId) {
        if (territorySync != null) {
            territorySync.syncConquest(coordinate, fromOwnerId, toOwnerId, warId.toString());
        }
    }

    // ==================== 服务状态 ====================

    /**
     * 获取服务状态摘要
     */
    public String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("跨服通信状态:\n");
        sb.append("  服务器ID: ").append(serverId).append("\n");
        sb.append("  玩家同步: ").append(playerSync != null ? "已启用" : "未启用").append("\n");
        sb.append("  聊天同步: ").append(chatSync != null && chatSync.isEnabled() ? "已启用" : "未启用").append("\n");
        sb.append("  国家同步: ").append(nationSync != null ? "已启用" : "未启用").append("\n");
        sb.append("  战争同步: ").append(warSync != null ? "已启用" : "未启用").append("\n");
        sb.append("  领土同步: ").append(territorySync != null ? "已启用" : "未启用");
        return sb.toString();
    }

    /**
     * 检查是否完全初始化
     */
    public boolean isFullyInitialized() {
        return playerSync != null || chatSync != null || nationSync != null || warSync != null || territorySync != null;
    }
}
