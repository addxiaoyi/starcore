package dev.starcore.starcore.module.nation;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationKind;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictService;

import java.util.Objects;

public final class NationOperationalSupport {
    private static final int DEFAULT_MAX_LEVEL = 100;
    private static final long DEFAULT_BASE_EXPERIENCE = 1000L;
    private static final long DEFAULT_EXPERIENCE_STEP = 250L;
    private static final int DEFAULT_MAX_CITY_STATES_PER_NATION = 3;

    private NationOperationalSupport() {
    }

    public static NationOperationalOverview overview(
        ConfigurationService configuration,
        NationService nationService,
        NationResourceDistrictService resourceDistrictService,
        Nation nation
    ) {
        Objects.requireNonNull(nation, "nation");
        int level = nationService == null ? 1 : nationService.levelOf(nation.id());
        long experience = nationService == null ? nation.experience() : nationService.experienceOf(nation.id());
        NationLevelProgress levelProgress = levelProgress(configuration, level, experience);
        int claimCount = nationService == null ? 0 : nationService.claimCount(nation.id());
        int claimLimit = nationService == null ? 0 : nationService.maxClaimsOf(nation.id());
        int cityStateCount = nationService == null || nation.kind() != NationKind.NATION
            ? 0
            : nationService.cityStatesOf(nation.id()).size();
        int cityStateLimit = configuration == null || nation.kind() != NationKind.NATION
            ? DEFAULT_MAX_CITY_STATES_PER_NATION
            : configuration.maxCityStatesPerNation();
        int resourceDistrictCount = resourceDistrictService == null ? 0 : resourceDistrictService.districtsOf(nation.id()).size();
        int resourceDistrictLimit = resourceDistrictService == null ? 0 : resourceDistrictService.districtLimitFor(nation);
        return new NationOperationalOverview(
            founderName(nation),
            nation.members().size(),
            levelProgress,
            claimCount,
            claimLimit,
            cityStateCount,
            cityStateLimit,
            resourceDistrictCount,
            resourceDistrictLimit
        );
    }

    private static NationLevelProgress levelProgress(ConfigurationService configuration, int level, long experience) {
        return NationProgression.progressForLevelAndExperience(
            level,
            experience,
            configuration == null ? DEFAULT_MAX_LEVEL : configuration.nationResourceMaxLevel(),
            configuration == null ? DEFAULT_BASE_EXPERIENCE : configuration.nationResourceLevelBaseExperience(),
            configuration == null ? DEFAULT_EXPERIENCE_STEP : configuration.nationResourceLevelExperienceStep()
        );
    }

    private static String founderName(Nation nation) {
        return nation.members().stream()
            .filter(member -> nation.founderId().equals(member.playerId()))
            .map(member -> member.lastKnownName())
            .findFirst()
            .orElse(nation.founderId().toString());
    }
}
