package dev.starcore.starcore.module.territoryrent;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territoryrent.model.PermissionLevel;
import dev.starcore.starcore.module.territoryrent.model.Rental;
import dev.starcore.starcore.module.territoryrent.model.RentalRequest;
import dev.starcore.starcore.module.territoryrent.model.RentalStatus;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领土租借服务实现
 */
public class TerritoryRentServiceImpl implements TerritoryRentService {

    private final Plugin plugin;
    private final NationService nationService;

    private final Map<UUID, RentalRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Rental> rentals = new ConcurrentHashMap<>();
    private final Map<ChunkCoordinate, UUID> chunkToRental = new ConcurrentHashMap<>();

    public TerritoryRentServiceImpl(Plugin plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
    }

    @Override
    public RentalRequest createRentalRequest(UUID requesterId, NationId requesterNationId,
                                           NationId targetNationId, ChunkCoordinate coordinate,
                                           int durationDays, BigDecimal dailyRent) {
        UUID requestId = UUID.randomUUID();

        RentalRequest request = new RentalRequest(
            requestId,
            requesterId,
            requesterNationId.value().toString(),
            targetNationId.value().toString(),
            coordinate,
            dailyRent,
            durationDays,
            Instant.now(),
            RentalRequest.RequestStatus.PENDING,
            null,
            null,
            null
        );

        requests.put(requestId, request);
        return request;
    }

    @Override
    public Optional<Rental> acceptRequest(UUID requestId, UUID processorId) {
        RentalRequest request = requests.remove(requestId);
        if (request == null || !request.canBeProcessed()) {
            return Optional.empty();
        }

        UUID rentalId = UUID.randomUUID();
        Instant now = Instant.now();
        int durationDays = request.durationDays();
        Instant endTime = now.plusSeconds(durationDays * 24L * 60 * 60);

        Rental rental = new Rental(
            rentalId,
            requestId,
            request.targetNationId(),
            request.requesterNationId(),
            request.coordinate(),
            request.dailyRent(),
            durationDays,
            now,
            endTime,
            RentalStatus.ACTIVE,
            now,
            PermissionLevel.BUILD.level(),
            BigDecimal.ZERO
        );

        rentals.put(rentalId, rental);
        chunkToRental.put(request.coordinate(), rentalId);

        return Optional.of(rental);
    }

    @Override
    public boolean rejectRequest(UUID requestId, UUID processorId) {
        RentalRequest request = requests.get(requestId);
        if (request == null || !request.canBeProcessed()) {
            return false;
        }

        RentalRequest rejected = new RentalRequest(
            request.id(),
            request.requesterId(),
            request.requesterNationId(),
            request.targetNationId(),
            request.coordinate(),
            request.dailyRent(),
            request.durationDays(),
            request.createdAt(),
            RentalRequest.RequestStatus.REJECTED,
            processorId,
            Instant.now(),
            null
        );

        requests.put(requestId, rejected);
        return true;
    }

    @Override
    public boolean cancelRequest(UUID requestId, UUID requesterId) {
        RentalRequest request = requests.get(requestId);
        if (request == null || !request.canBeProcessed()) {
            return false;
        }

        if (!request.requesterId().equals(requesterId)) {
            return false;
        }

        requests.remove(requestId);
        return true;
    }

    @Override
    public Collection<RentalRequest> getPendingRequestsForNation(NationId nationId) {
        String nationIdStr = nationId.value().toString();
        return requests.values().stream()
            .filter(r -> r.status() == RentalRequest.RequestStatus.PENDING)
            .filter(r -> r.targetNationId().equals(nationIdStr))
            .toList();
    }

    @Override
    public boolean renewRental(UUID rentalId, int additionalDays, UUID renewerId) {
        Rental rental = rentals.get(rentalId);
        if (rental == null || rental.status() != RentalStatus.ACTIVE) {
            return false;
        }

        Rental renewed = new Rental(
            rental.id(),
            rental.requestId(),
            rental.ownerNationId(),
            rental.tenantNationId(),
            rental.coordinate(),
            rental.dailyRent(),
            rental.durationDays() + additionalDays,
            rental.startTime(),
            rental.endTime().plusSeconds(additionalDays * 24L * 60 * 60),
            rental.status(),
            rental.createdAt(),
            rental.permissionLevel(),
            rental.totalPaid()
        );

        rentals.put(rentalId, renewed);
        return true;
    }

    @Override
    public boolean terminateRental(UUID rentalId, UUID terminatorId) {
        Rental rental = rentals.get(rentalId);
        if (rental == null) {
            return false;
        }

        Rental terminated = new Rental(
            rental.id(),
            rental.requestId(),
            rental.ownerNationId(),
            rental.tenantNationId(),
            rental.coordinate(),
            rental.dailyRent(),
            rental.durationDays(),
            rental.startTime(),
            rental.endTime(),
            RentalStatus.TERMINATED,
            rental.createdAt(),
            rental.permissionLevel(),
            rental.totalPaid()
        );

        rentals.put(rentalId, terminated);
        chunkToRental.remove(rental.coordinate());
        return true;
    }

    @Override
    public Optional<Rental> getRental(UUID rentalId) {
        return Optional.ofNullable(rentals.get(rentalId));
    }

    @Override
    public boolean isChunkRented(ChunkCoordinate coordinate) {
        UUID rentalId = chunkToRental.get(coordinate);
        if (rentalId == null) {
            return false;
        }
        Rental rental = rentals.get(rentalId);
        return rental != null && rental.status() == RentalStatus.ACTIVE && !rental.isExpired();
    }

    @Override
    public int getRentalPermissionLevel(ChunkCoordinate coordinate, UUID playerId) {
        UUID rentalId = chunkToRental.get(coordinate);
        if (rentalId == null) {
            return PermissionLevel.NONE.level();
        }

        Rental rental = rentals.get(rentalId);
        if (rental == null || (rental.status() != RentalStatus.ACTIVE && !rental.isExpired())) {
            return PermissionLevel.NONE.level();
        }

        var playerNationOpt = nationService.nationOf(playerId);
        if (playerNationOpt.isEmpty()) {
            return PermissionLevel.NONE.level();
        }

        String playerNationId = playerNationOpt.get().id().value().toString();

        if (rental.ownerNationId().equals(playerNationId)) {
            return PermissionLevel.MANAGE.level();
        }

        if (rental.tenantNationId().equals(playerNationId)) {
            return rental.permissionLevel();
        }

        return PermissionLevel.NONE.level();
    }

    @Override
    public Collection<Rental> getRentalsAsOwner(String ownerNationId) {
        return rentals.values().stream()
            .filter(r -> r.ownerNationId().equals(ownerNationId))
            .toList();
    }

    @Override
    public Collection<Rental> getRentalsAsTenant(String tenantNationId) {
        return rentals.values().stream()
            .filter(r -> r.tenantNationId().equals(tenantNationId))
            .toList();
    }

    @Override
    public Collection<Rental> getActiveRentals() {
        return rentals.values().stream()
            .filter(r -> r.status() == RentalStatus.ACTIVE)
            .toList();
    }
}
