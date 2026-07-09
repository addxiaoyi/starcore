package dev.starcore.starcore.module.territoryrent;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territoryrent.model.Rental;
import dev.starcore.starcore.module.territoryrent.model.RentalStatus;
import dev.starcore.starcore.module.territoryrent.model.RentalRequest;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 领土租借服务接口
 * 提供领土租借的核心业务逻辑
 */
public interface TerritoryRentService {

    /**
     * 创建租借请求
     *
     * @param requesterId  请求者ID
     * @param targetNationId 目标国家ID（土地所有者）
     * @param coordinate  租借的区块坐标
     * @param durationDays 租借天数
     * @param dailyRent   每日租金
     * @return 租借请求
     */
    RentalRequest createRentalRequest(UUID requesterId, NationId targetNationId, ChunkCoordinate coordinate,
                                      int durationDays, BigDecimal dailyRent);

    /**
     * 接受租借请求
     *
     * @param requestId  请求ID
     * @param accepterId 接受者ID（必须是目标国家领袖）
     * @return 生成的租借记录
     */
    Optional<Rental> acceptRequest(UUID requestId, UUID accepterId);

    /**
     * 拒绝租借请求
     *
     * @param requestId  请求ID
     * @param refuserId  拒绝者ID
     * @return 是否成功拒绝
     */
    boolean rejectRequest(UUID requestId, UUID refuserId);

    /**
     * 取消租借请求（请求者自己取消）
     *
     * @param requestId  请求ID
     * @param playerId   玩家ID
     * @return 是否成功取消
     */
    boolean cancelRequest(UUID requestId, UUID playerId);

    /**
     * 获取玩家的待处理租借请求（作为土地所有者收到的请求）
     *
     * @param nationId 国家ID
     * @return 请求列表
     */
    Collection<RentalRequest> getPendingRequestsForNation(NationId nationId);

    /**
     * 获取玩家发出的待处理请求
     *
     * @param playerId 玩家ID
     * @return 请求列表
     */
    Collection<RentalRequest> getPendingRequestsByPlayer(UUID playerId);

    /**
     * 开始租借（从请求转为正式租借）
     *
     * @param requestId 请求ID
     * @param payerId   付款者ID
     * @return 租借记录
     */
    Optional<Rental> startRental(UUID requestId, UUID payerId);

    /**
     * 续租
     *
     * @param rentalId 租借ID
     * @param days     续租天数
     * @param payerId  付款者ID
     * @return 是否续租成功
     */
    boolean renewRental(UUID rentalId, int days, UUID payerId);

    /**
     * 提前终止租借
     *
     * @param rentalId 租借ID
     * @param terminatorId 终止者ID
     * @return 是否成功终止
     */
    boolean terminateRental(UUID rentalId, UUID terminatorId);

    /**
     * 获取租借记录
     *
     * @param rentalId 租借ID
     * @return 租借记录
     */
    Optional<Rental> getRental(UUID rentalId);

    /**
     * 获取国家当前有效的所有租借（作为出租方）
     *
     * @param nationId 国家ID
     * @return 租借列表
     */
    Collection<Rental> getRentalsAsOwner(NationId nationId);

    /**
     * 获取国家当前有效的所有租借（作为承租方）
     *
     * @param nationId 国家ID
     * @return 租借列表
     */
    Collection<Rental> getRentalsAsTenant(NationId nationId);

    /**
     * 获取区块坐标上的有效租借
     *
     * @param coordinate 区块坐标
     * @return 租借记录
     */
    Optional<Rental> getRentalAt(ChunkCoordinate coordinate);

    /**
     * 检查租借是否有效
     *
     * @param rentalId 租借ID
     * @return 是否有效
     */
    boolean isRentalActive(UUID rentalId);

    /**
     * 获取租借者在区块上的权限等级
     *
     * @param coordinate 区块坐标
     * @param playerId   玩家ID
     * @return 权限等级（0=无权限, 1=使用, 2=建设, 3=管理）
     */
    int getRentalPermissionLevel(ChunkCoordinate coordinate, UUID playerId);

    /**
     * 检查区块是否处于租借状态
     *
     * @param coordinate 区块坐标
     * @return 是否处于租借状态
     */
    boolean isChunkRented(ChunkCoordinate coordinate);

    /**
     * 获取区块的承租方国家ID
     *
     * @param coordinate 区块坐标
     * @return 承租方国家ID
     */
    Optional<NationId> getTenantNationId(ChunkCoordinate coordinate);

    /**
     * 处理租借过期（由定时任务调用）
     *
     * @return 过期的租借数量
     */
    int processExpiredRentals();

    /**
     * 初始化数据库表
     */
    void initializeTables();

    /**
     * 保存所有状态
     */
    void saveState();

    /**
     * 关闭服务
     */
    void shutdown();
}