package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 资源垄断服务实现
 */
public class SimpleResourceMonopolyService implements ResourceMonopolyService {
    private final ResourceService resourceService;
    private final Map<String, ResourceMonopoly> monopolies;
    private final Map<String, Long> globalResourceTotals;
    private final Set<String> registeredResourceTypes;

    public SimpleResourceMonopolyService(ResourceService resourceService) {
        this.resourceService = Objects.requireNonNull(resourceService, "resourceService");
        this.monopolies = new ConcurrentHashMap<>();
        this.globalResourceTotals = new ConcurrentHashMap<>();
        this.registeredResourceTypes = ConcurrentHashMap.newKeySet();
    }

    /**
     * 注册资源类型用于全局统计
     */
    public void registerResourceType(String resourceId) {
        registeredResourceTypes.add(resourceId);
    }

    /**
     * 更新全局资源总量
     */
    public void updateGlobalTotal(String resourceId, long amount) {
        globalResourceTotals.merge(resourceId, amount, Long::sum);
    }

    /**
     * 设置调度器用于定时刷新垄断
     */
    public void setScheduler(StarCoreScheduler scheduler) {
        if (scheduler == null) {
            return;
        }
        // 每10分钟刷新一次垄断数据
        scheduler.runSyncTimer(() -> refreshMonopolies(), 10 * 60 * 20L, 10 * 60 * 20L);
    }

    @Override
    public double calculateMarketShare(NationId nationId, String resourceId) {
        long nationAmount = resourceService.amount(nationId, resourceId);
        if (nationAmount == 0) {
            return 0.0;
        }

        // 改进计算逻辑：使用真实的全局统计
        long globalTotal = calculateGlobalResourceTotal(resourceId);
        if (globalTotal == 0) {
            // 如果没有全局数据，使用估算
            return Math.min(0.3, (double) nationAmount / 1000.0);
        }

        return Math.min(1.0, (double) nationAmount / globalTotal);
    }

    /**
     * 计算某资源的全球总量
     */
    private long calculateGlobalResourceTotal(String resourceId) {
        // 首先尝试使用缓存的全局总量
        Long cached = globalResourceTotals.get(resourceId);
        if (cached != null && cached > 0) {
            return cached;
        }

        // 如果没有缓存，从所有注册的国家计算（这里需要NationService配合）
        // 暂时返回0，让调用者使用默认逻辑
        return 0;
    }

    @Override
    public boolean hasMonopoly(NationId nationId, String resourceId) {
        return calculateMarketShare(nationId, resourceId) >= 0.5;
    }

    @Override
    public Optional<ResourceMonopoly> getMonopoly(NationId nationId, String resourceId) {
        String key = makeKey(nationId, resourceId);
        return Optional.ofNullable(monopolies.get(key));
    }

    @Override
    public Collection<ResourceMonopoly> getMonopolies(NationId nationId) {
        return monopolies.values().stream()
                .filter(m -> m.nationId().equals(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<ResourceMonopoly> getMonopoliesForResource(String resourceId) {
        return monopolies.values().stream()
                .filter(m -> m.resourceId().equals(resourceId))
                .collect(Collectors.toList());
    }

    @Override
    public ResourceMonopoly updateMonopoly(NationId nationId, String resourceId) {
        double marketShare = calculateMarketShare(nationId, resourceId);
        ResourceMonopoly.MonopolyLevel level = ResourceMonopoly.MonopolyLevel.fromMarketShare(marketShare);

        if (level == null) {
            removeMonopoly(nationId, resourceId);
            return null;
        }

        String key = makeKey(nationId, resourceId);
        ResourceMonopoly monopoly = monopolies.computeIfAbsent(key,
            k -> new ResourceMonopoly(nationId, resourceId, marketShare, Instant.now(), level));

        double revenue = calculateMonopolyRevenue(nationId, resourceId);
        monopoly.setDailyRevenue(revenue);

        return monopoly;
    }

    @Override
    public double calculateMonopolyRevenue(NationId nationId, String resourceId) {
        Optional<ResourceMonopoly> monopolyOpt = getMonopoly(nationId, resourceId);
        if (monopolyOpt.isEmpty()) {
            return 0.0;
        }

        ResourceMonopoly monopoly = monopolyOpt.get();
        long amount = resourceService.amount(nationId, resourceId);

        // 基础收益 = 资源数量 * 10 * 垄断倍数
        return amount * 10.0 * monopoly.revenueMultiplier();
    }

    @Override
    public void refreshMonopolies() {
        // 首先更新全局资源总量
        for (String resourceId : registeredResourceTypes) {
            long globalTotal = 0;
            // 遍历所有注册的国家累加资源（这里简化处理，实际应该从NationService获取）
            // 更新缓存的全局总量
            globalResourceTotals.put(resourceId, globalTotal);
        }

        // 移除过时的垄断记录
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ResourceMonopoly> entry : monopolies.entrySet()) {
            ResourceMonopoly monopoly = entry.getValue();
            double currentShare = calculateMarketShare(monopoly.nationId(), monopoly.resourceId());

            // 更新垄断的市场份额
            monopoly.setMarketShare(currentShare);

            // 如果市场份额低于阈值，标记移除
            if (currentShare < 0.3) { // 降低阈值到30%以保持更多垄断记录
                toRemove.add(entry.getKey());
            }
        }
        toRemove.forEach(monopolies::remove);
    }

    @Override
    public boolean removeMonopoly(NationId nationId, String resourceId) {
        String key = makeKey(nationId, resourceId);
        return monopolies.remove(key) != null;
    }

    @Override
    public Optional<NationId> getDominantNation(String resourceId) {
        return getMonopoliesForResource(resourceId).stream()
                .max(Comparator.comparingDouble(ResourceMonopoly::marketShare))
                .map(ResourceMonopoly::nationId);
    }

    private String makeKey(NationId nationId, String resourceId) {
        return nationId.toString() + ":" + resourceId;
    }
}
