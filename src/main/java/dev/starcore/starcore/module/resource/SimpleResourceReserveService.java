package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.NationalReserve;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战略储备服务实现
 */
public class SimpleResourceReserveService implements ResourceReserveService {
    private final ResourceService resourceService;
    private final Map<NationId, NationalReserve> reserves;

    public SimpleResourceReserveService(ResourceService resourceService) {
        this.resourceService = Objects.requireNonNull(resourceService, "resourceService");
        this.reserves = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<NationalReserve> getReserve(NationId nationId) {
        return Optional.ofNullable(reserves.get(nationId));
    }

    @Override
    public NationalReserve createReserve(NationId nationId) {
        return reserves.computeIfAbsent(nationId, NationalReserve::new);
    }

    @Override
    public boolean addReserve(NationId nationId, String resourceId, long amount) {
        if (amount <= 0) {
            return false;
        }

        NationalReserve reserve = createReserve(nationId);
        reserve.addReserve(resourceId, amount);
        return true;
    }

    @Override
    public boolean consumeReserve(NationId nationId, String resourceId, long amount) {
        Optional<NationalReserve> reserveOpt = getReserve(nationId);
        if (reserveOpt.isEmpty()) {
            return false;
        }

        return reserveOpt.get().consumeReserve(resourceId, amount);
    }

    @Override
    public long getReserveAmount(NationId nationId, String resourceId) {
        return getReserve(nationId)
                .map(reserve -> reserve.getReserve(resourceId))
                .orElse(0L);
    }

    @Override
    public boolean setReserveGoal(NationId nationId, String resourceId, long goal) {
        NationalReserve reserve = createReserve(nationId);
        reserve.setReserveGoal(resourceId, goal);
        return true;
    }

    @Override
    public long getReserveGoal(NationId nationId, String resourceId) {
        return getReserve(nationId)
                .map(reserve -> reserve.getReserveGoal(resourceId))
                .orElse(0L);
    }

    @Override
    public boolean hasMetGoal(NationId nationId, String resourceId) {
        return getReserve(nationId)
                .map(reserve -> reserve.hasMetGoal(resourceId))
                .orElse(false);
    }

    @Override
    public double getGoalProgress(NationId nationId, String resourceId) {
        return getReserve(nationId)
                .map(reserve -> reserve.getGoalProgress(resourceId))
                .orElse(0.0);
    }

    @Override
    public double getOverallProgress(NationId nationId) {
        return getReserve(nationId)
                .map(NationalReserve::getOverallProgress)
                .orElse(0.0);
    }

    @Override
    public int getUnmetGoalsCount(NationId nationId) {
        return getReserve(nationId)
                .map(NationalReserve::getUnmetGoalsCount)
                .orElse(0);
    }

    @Override
    public boolean transferToReserve(NationId nationId, String resourceId, long amount) {
        if (amount <= 0) {
            return false;
        }

        // 从普通库存扣除
        if (!resourceService.consume(nationId, resourceId, amount)) {
            return false;
        }

        // 添加到储备
        // audit B-076: 之前 consume 成功后直接 addReserve，但 addReserve 可能因同步问题失败；
        // 若失败资源凭空消失。改为校验 addReserve 返回值，失败时把已消耗的 grant 回国家。
        if (!addReserve(nationId, resourceId, amount)) {
            // 回滚 consume
            try {
                resourceService.grant(nationId, resourceId, amount);
            } catch (RuntimeException ignore) {
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean emergencyRelease(NationId nationId, String resourceId, long amount) {
        if (amount <= 0) {
            return false;
        }

        // audit B-077: 之前顺序为 consumeReserve → grant，若 grant 失败储备已消耗但普通库存未增加。
        // 调整为"先保证 grant 能成功"——或在 grant 失败时把储备加回。这里采用回滚策略。
        // 从储备扣除
        if (!consumeReserve(nationId, resourceId, amount)) {
            return false;
        }

        // 添加到普通库存
        // audit B-077: 校验 grant 返回值；失败时把已扣除的储备加回。
        boolean granted;
        try {
            granted = resourceService.grant(nationId, resourceId, amount);
        } catch (RuntimeException e) {
            granted = false;
        }
        if (!granted) {
            // 回滚 consumeReserve
            try {
                addReserve(nationId, resourceId, amount);
            } catch (RuntimeException ignore) {
            }
            return false;
        }
        return true;
    }

    @Override
    public Map<String, Long> getAllReserves(NationId nationId) {
        return getReserve(nationId)
                .map(NationalReserve::reserves)
                .orElse(Collections.emptyMap());
    }

    @Override
    public Map<String, Long> getAllGoals(NationId nationId) {
        return getReserve(nationId)
                .map(NationalReserve::reserveGoals)
                .orElse(Collections.emptyMap());
    }
}
