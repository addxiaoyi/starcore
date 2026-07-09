package dev.starcore.starcore.api;

import dev.starcore.starcore.core.module.ModuleDescriptor;
import dev.starcore.starcore.foundation.epoch.EpochService;
import dev.starcore.starcore.foundation.timesync.TimeSyncService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.map.MapService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.claimtool.ClaimToolService;
import dev.starcore.starcore.module.officer.OfficerService;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.module.resolution.ResolutionService;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.module.treasury.TreasuryRewardService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;

import java.util.Collection;
import java.util.Optional;

public interface StarCoreApi {
    String version();

    Collection<ModuleDescriptor> modules();

    <T> Optional<T> service(Class<T> serviceType);

    // ===== REST API 服务 =====

    /**
     * 获取 REST API 服务器（如果已启用）
     */
    default Optional<dev.starcore.starcore.api.v1.RestApiServer> restApiServer() {
        return service(dev.starcore.starcore.api.v1.RestApiServer.class);
    }

    /**
     * 获取 API 认证服务
     */
    default Optional<dev.starcore.starcore.api.v1.auth.ApiAuthService> apiAuthService() {
        return service(dev.starcore.starcore.api.v1.auth.ApiAuthService.class);
    }

    default Optional<NationService> nationService() {
        return service(NationService.class);
    }

    default Optional<ClaimToolService> claimToolService() {
        return service(ClaimToolService.class);
    }

    default Optional<EpochService> epochService() {
        return service(EpochService.class);
    }

    default Optional<TimeSyncService> timeSyncService() {
        return service(TimeSyncService.class);
    }

    default Optional<GovernmentService> governmentService() {
        return service(GovernmentService.class);
    }

    default Optional<ResolutionService> resolutionService() {
        return service(ResolutionService.class);
    }

    default Optional<TreasuryService> treasuryService() {
        return service(TreasuryService.class);
    }

    default Optional<TreasuryRewardService> treasuryRewardService() {
        return service(TreasuryRewardService.class);
    }

    default Optional<DiplomacyService> diplomacyService() {
        return service(DiplomacyService.class);
    }

    default Optional<PolicyService> policyService() {
        return service(PolicyService.class);
    }

    default Optional<ResourceService> resourceService() {
        return service(ResourceService.class);
    }

    default Optional<TechnologyService> technologyService() {
        return service(TechnologyService.class);
    }

    default Optional<WarService> warService() {
        return service(WarService.class);
    }

    default Optional<OfficerService> officerService() {
        return service(OfficerService.class);
    }

    default Optional<MapService> mapService() {
        return service(MapService.class);
    }
}
