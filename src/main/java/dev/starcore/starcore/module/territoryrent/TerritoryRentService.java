package dev.starcore.starcore.module.territoryrent;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territoryrent.model.Rental;
import dev.starcore.starcore.module.territoryrent.model.RentalRequest;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 领土租借服务接口
 */
public interface TerritoryRentService {

    /**
     * 创建租借请求
     */
    RentalRequest createRentalRequest(UUID requesterId, NationId requesterNationId,
                                      NationId targetNationId, ChunkCoordinate coordinate,
                                      int durationDays, BigDecimal dailyRent);

    /**
     * 接受租借请求
     */
    Optional<Rental> acceptRequest(UUID requestId, UUID processorId);

    /**
     * 拒绝租借请求
     */
    boolean rejectRequest(UUID requestId, UUID processorId);

    /**
     * 取消租借请求
     */
    boolean cancelRequest(UUID requestId, UUID requesterId);

    /**
     * 获取待处理请求列表（按国家）
     */
    Collection<RentalRequest> getPendingRequestsForNation(NationId nationId);

    /**
     * 续租
     */
    boolean renewRental(UUID rentalId, int additionalDays, UUID renewerId);

    /**
     * 终止租借
     */
    boolean terminateRental(UUID rentalId, UUID terminatorId);

    /**
     * 获取租借详情
     */
    Optional<Rental> getRental(UUID rentalId);

    /**
     * 检查区块是否已被租借
     */
    boolean isChunkRented(ChunkCoordinate coordinate);

    /**
     * 获取区块的租借权限等级
     */
    int getRentalPermissionLevel(ChunkCoordinate coordinate, UUID playerId);

    /**
     * 获取作为出租方的所有租借
     */
    Collection<Rental> getRentalsAsOwner(String ownerNationId);

    /**
     * 获取作为承租方的所有租借
     */
    Collection<Rental> getRentalsAsTenant(String tenantNationId);

    /**
     * 获取租借的活跃租借
     */
    Collection<Rental> getActiveRentals();
}
