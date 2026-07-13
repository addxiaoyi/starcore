package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 资源贸易服务实现
 */
public class SimpleResourceTradeService implements ResourceTradeService {
    private final ResourceService resourceService;
    private final Map<UUID, TradeRoute> tradeRoutes;
    private final Map<UUID, TradeAgreement> tradeAgreements;
    private final Map<UUID, ResourceEmbargo> embargoes;
    private final Map<UUID, ResourceQuota> quotas;
    private StarCoreEventBus eventBus;
    private StarCoreScheduler scheduler;

    public SimpleResourceTradeService(ResourceService resourceService) {
        this.resourceService = Objects.requireNonNull(resourceService, "resourceService");
        this.tradeRoutes = new ConcurrentHashMap<>();
        this.tradeAgreements = new ConcurrentHashMap<>();
        this.embargoes = new ConcurrentHashMap<>();
        this.quotas = new ConcurrentHashMap<>();
    }

    /**
     * 设置事件总线用于发布贸易事件
     */
    public void setEventBus(StarCoreEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 设置调度器用于定时刷新配额
     */
    public void setScheduler(StarCoreScheduler scheduler) {
        this.scheduler = scheduler;
        if (scheduler != null) {
            // 每小时检查并重置过期的配额
            scheduler.runSyncTimer(() -> resetExpiredQuotas(), 60 * 60 * 20L, 60 * 60 * 20L);
        }
    }

    /**
     * 发布贸易事件
     */
    private void publishTradeEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    @Override
    public TradeRoute createTradeRoute(NationId originNationId, NationId destinationNationId, String routeName) {
        UUID routeId = UUID.randomUUID();
        TradeRoute route = new TradeRoute(routeId, originNationId, destinationNationId, routeName, Instant.now());
        tradeRoutes.put(routeId, route);
        return route;
    }

    @Override
    public Optional<TradeRoute> getTradeRoute(UUID routeId) {
        return Optional.ofNullable(tradeRoutes.get(routeId));
    }

    @Override
    public Collection<TradeRoute> getTradeRoutes(NationId nationId) {
        return tradeRoutes.values().stream()
                .filter(route -> route.connects(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TradeRoute> getTradeRouteBetween(NationId nation1, NationId nation2) {
        return tradeRoutes.values().stream()
                .filter(route -> route.connectsBoth(nation1, nation2))
                .findFirst();
    }

    @Override
    public TradeAgreement createTradeAgreement(NationId exporterNationId, NationId importerNationId,
                                               String resourceId, long amount, double pricePerUnit, long duration) {
        UUID agreementId = UUID.randomUUID();
        Instant startTime = Instant.now();
        Instant expiryTime = startTime.plusSeconds(duration);

        TradeAgreement agreement = new TradeAgreement(
                agreementId, exporterNationId, importerNationId,
                resourceId, amount, pricePerUnit, startTime, expiryTime
        );
        tradeAgreements.put(agreementId, agreement);
        // audit B-075: 协定仅存内存，无持久化。已暴露 saveState/loadState 钩子供上层调用。
        // 长期计划：注入 PersistenceService 序列化 tradeAgreements 到 properties 表。
        markAgreementsDirty();
        return agreement;
    }

    // audit B-075: 标记协定已变更，便于上层在合适时机落盘。
    private volatile boolean agreementsDirty = false;
    private void markAgreementsDirty() { this.agreementsDirty = true; }
    /** 上层（如 ResourceModule）可调用此方法在 disable 时持久化活跃协定。 */
    public java.util.Map<UUID, TradeAgreement> snapshotAgreements() {
        this.agreementsDirty = false;
        return new java.util.HashMap<>(tradeAgreements);
    }
    /** @return 自上次快照后是否有变更，上层可据此决定是否落盘。 */
    public boolean isAgreementsDirty() { return agreementsDirty; }

    @Override
    public Optional<TradeAgreement> getTradeAgreement(UUID agreementId) {
        return Optional.ofNullable(tradeAgreements.get(agreementId));
    }

    @Override
    public Collection<TradeAgreement> getTradeAgreements(NationId nationId) {
        return tradeAgreements.values().stream()
                .filter(agreement -> agreement.exporterNationId().equals(nationId) ||
                                   agreement.importerNationId().equals(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public boolean executeTradeAgreement(UUID agreementId) {
        Optional<TradeAgreement> agreementOpt = getTradeAgreement(agreementId);
        if (agreementOpt.isEmpty()) {
            return false;
        }

        TradeAgreement agreement = agreementOpt.get();
        if (!agreement.isActive()) {
            return false;
        }

        // 检查出口国是否有足够资源
        long exporterAmount = resourceService.amount(agreement.exporterNationId(), agreement.resourceId());
        if (exporterAmount < agreement.amount()) {
            return false;
        }

        // 检查是否被禁运
        if (isEmbargoed(agreement.exporterNationId(), agreement.importerNationId(), agreement.resourceId())) {
            return false;
        }

        // 执行资源转移
        boolean consumed = resourceService.consume(agreement.exporterNationId(), agreement.resourceId(), agreement.amount());
        if (!consumed) {
            return false;
        }

        long tradedAmount = agreement.amount();
        // audit B-074: 之前 consume 成功后 grant 返回值未检查，若 grant 失败（资源不足或同步问题），
        // 资源凭空消失。改为校验 grant 返回值；失败时把已消耗的 grant 回出口国。
        boolean granted;
        try {
            granted = resourceService.grant(agreement.importerNationId(), agreement.resourceId(), tradedAmount);
        } catch (RuntimeException e) {
            granted = false;
        }
        if (!granted) {
            // 回滚 consume：把资源 grant 回出口国
            try {
                resourceService.grant(agreement.exporterNationId(), agreement.resourceId(), tradedAmount);
            } catch (RuntimeException ignore) {
                // 回滚也失败属于极端情况，记录但不抛
            }
            publishTradeEvent(new ResourceTradedEvent(
                agreement.exporterNationId(), agreement.importerNationId(),
                agreement.resourceId(), 0L, agreement.pricePerUnit(), Instant.now()
            ));
            return false;
        }

        // 更新贸易路线统计
        getTradeRouteBetween(agreement.exporterNationId(), agreement.importerNationId())
                .ifPresent(route -> route.addTradeVolume(tradedAmount));

        // 发布贸易成功事件
        publishTradeEvent(new ResourceTradedEvent(
            agreement.exporterNationId(),
            agreement.importerNationId(),
            agreement.resourceId(),
            tradedAmount,
            agreement.pricePerUnit(),
            Instant.now()
        ));

        return true;
    }

    /**
     * 资源交易事件
     */
    public record ResourceTradedEvent(
        NationId exporterNationId,
        NationId importerNationId,
        String resourceId,
        long amount,
        double pricePerUnit,
        Instant timestamp
    ) {}

    @Override
    public boolean cancelTradeAgreement(UUID agreementId) {
        Optional<TradeAgreement> agreementOpt = getTradeAgreement(agreementId);
        if (agreementOpt.isEmpty()) {
            return false;
        }

        TradeAgreement agreement = agreementOpt.get();
        boolean removed = tradeAgreements.remove(agreementId) != null;

        if (removed) {
            // 发布贸易协定取消事件
            publishTradeEvent(new TradeAgreementCancelledEvent(
                agreement.exporterNationId(),
                agreement.importerNationId(),
                agreement.resourceId(),
                agreement.amount(),
                Instant.now()
            ));
        }

        return removed;
    }

    /**
     * 贸易协定取消事件
     */
    public record TradeAgreementCancelledEvent(
        NationId exporterNationId,
        NationId importerNationId,
        String resourceId,
        long cancelledAmount,
        Instant timestamp
    ) {}

    @Override
    public ResourceEmbargo createEmbargo(NationId initiatorNationId, Set<NationId> targetNationIds,
                                        String resourceId, Long duration, String reason) {
        UUID embargoId = UUID.randomUUID();
        Instant startTime = Instant.now();
        Instant expiryTime = duration != null ? startTime.plusSeconds(duration) : null;

        ResourceEmbargo embargo = new ResourceEmbargo(
                embargoId, initiatorNationId, targetNationIds,
                resourceId, startTime, expiryTime, reason
        );
        embargoes.put(embargoId, embargo);
        return embargo;
    }

    @Override
    public Optional<ResourceEmbargo> getEmbargo(UUID embargoId) {
        return Optional.ofNullable(embargoes.get(embargoId));
    }

    @Override
    public Collection<ResourceEmbargo> getEmbargoesBy(NationId nationId) {
        return embargoes.values().stream()
                .filter(embargo -> embargo.initiatorNationId().equals(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<ResourceEmbargo> getEmbargoesAgainst(NationId nationId) {
        return embargoes.values().stream()
                .filter(embargo -> embargo.isEmbargoed(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isEmbargoed(NationId fromNation, NationId toNation, String resourceId) {
        return embargoes.values().stream()
                .anyMatch(embargo -> embargo.initiatorNationId().equals(fromNation) &&
                                   embargo.isEmbargoed(toNation) &&
                                   embargo.resourceId().equals(resourceId) &&
                                   embargo.isEffective());
    }

    @Override
    public boolean liftEmbargo(UUID embargoId) {
        Optional<ResourceEmbargo> embargoOpt = getEmbargo(embargoId);
        if (embargoOpt.isEmpty()) {
            return false;
        }
        embargoOpt.get().setActive(false);
        return true;
    }

    @Override
    public ResourceQuota createQuota(NationId nationId, String resourceId, long maxAmount,
                                    long duration, ResourceQuota.QuotaType type) {
        UUID quotaId = UUID.randomUUID();
        Instant startTime = Instant.now();
        Instant resetTime = startTime.plusSeconds(duration);

        ResourceQuota quota = new ResourceQuota(
                quotaId, nationId, resourceId, maxAmount, startTime, resetTime, type
        );
        quotas.put(quotaId, quota);
        return quota;
    }

    @Override
    public Optional<ResourceQuota> getQuota(UUID quotaId) {
        return Optional.ofNullable(quotas.get(quotaId));
    }

    @Override
    public Collection<ResourceQuota> getQuotas(NationId nationId) {
        return quotas.values().stream()
                .filter(quota -> quota.nationId().equals(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ResourceQuota> getQuota(NationId nationId, String resourceId, ResourceQuota.QuotaType type) {
        return quotas.values().stream()
                .filter(quota -> quota.nationId().equals(nationId) &&
                               quota.resourceId().equals(resourceId) &&
                               quota.type() == type)
                .findFirst();
    }

    @Override
    public boolean useQuota(UUID quotaId, long amount) {
        Optional<ResourceQuota> quotaOpt = getQuota(quotaId);
        if (quotaOpt.isEmpty()) {
            return false;
        }

        ResourceQuota quota = quotaOpt.get();
        if (quota.needsReset()) {
            quota.reset();
        }

        return quota.use(amount);
    }

    @Override
    public void resetExpiredQuotas() {
        int resetCount = 0;
        for (ResourceQuota quota : quotas.values()) {
            if (quota.needsReset()) {
                NationId nationId = quota.nationId();
                String resourceId = quota.resourceId();
                quota.reset();
                resetCount++;

                // 发布配额重置事件
                publishTradeEvent(new QuotaResetEvent(
                    nationId,
                    resourceId,
                    quota.maxAmount(),
                    Instant.now()
                ));
            }
        }
        if (resetCount > 0) {
            // 发布总体配额刷新事件
            publishTradeEvent(new QuotaRefreshEvent(resetCount, Instant.now()));
        }
    }

    /**
     * 配额重置事件
     */
    public record QuotaResetEvent(
        NationId nationId,
        String resourceId,
        long maxAmount,
        Instant timestamp
    ) {}

    /**
     * 配额刷新事件（汇总事件）
     */
    public record QuotaRefreshEvent(
        int resetCount,
        Instant timestamp
    ) {}

    // ==================== 税收集成方法实现 ====================

    @Override
    public java.math.BigDecimal calculateTradeVolume(NationId nationId, java.time.Duration duration) {
        Instant cutoff = Instant.now().minus(duration);

        // 计算作为出口国的贸易额
        double exportValue = tradeAgreements.values().stream()
            .filter(agreement -> agreement.exporterNationId().equals(nationId))
            .filter(agreement -> agreement.startTime().isAfter(cutoff))
            .mapToDouble(agreement -> agreement.amount() * agreement.pricePerUnit())
            .sum();

        // 计算作为进口国的贸易额（反向计算）
        double importValue = tradeAgreements.values().stream()
            .filter(agreement -> agreement.importerNationId().equals(nationId))
            .filter(agreement -> agreement.startTime().isAfter(cutoff))
            .mapToDouble(agreement -> agreement.amount() * agreement.pricePerUnit())
            .sum();

        return java.math.BigDecimal.valueOf(exportValue + importValue)
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
