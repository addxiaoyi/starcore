package dev.starcore.starcore.module.map;

import dev.starcore.starcore.module.nation.NationOperationalOverview;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictCommandSupport;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictOperationalOverview;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictSnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ResourceDistrictMapMetadataSupport {
    private ResourceDistrictMapMetadataSupport() {
    }

    static Map<String, String> baseMetadata(
        Nation nation,
        NationOperationalOverview nationOverview,
        NationResourceDistrictSnapshot district,
        String relation,
        String displayColor,
        NationResourceDistrictOperationalOverview overview,
        String migrationLabel,
        String beaconPosition,
        String nextRefreshAt,
        String forceMigrationAt
    ) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("nation", nation.name());
        metadata.put("nationId", nation.id().toString());
        metadata.put("nationKind", nation.kind().name().toLowerCase(Locale.ROOT));
        metadata.put("government", nation.governmentType().name());
        metadata.put("founderName", nationOverview.founderName());
        metadata.put("memberCount", String.valueOf(nationOverview.memberCount()));
        metadata.put("founderId", nation.founderId().toString());
        metadata.put("nationLevel", String.valueOf(nationOverview.level()));
        metadata.put("nationExperience", String.valueOf(nationOverview.experience()));
        metadata.put("nationExperienceProgress", String.valueOf(nationOverview.currentLevelProgress()));
        metadata.put("nationNextLevelExperience", String.valueOf(nationOverview.nextLevelExperienceRequired()));
        metadata.put("nationExperienceRemaining", String.valueOf(nationOverview.remainingExperienceToNextLevel()));
        metadata.put("nationMaxLevelReached", String.valueOf(nationOverview.maxLevelReached()));
        metadata.put("claimCount", String.valueOf(nationOverview.claimCount()));
        metadata.put("claimLimit", String.valueOf(nationOverview.claimLimit()));
        metadata.put("cityStateCount", String.valueOf(nationOverview.cityStateCount()));
        metadata.put("cityStateLimit", String.valueOf(nationOverview.cityStateLimit()));
        metadata.put("resourceDistrictCount", String.valueOf(nationOverview.resourceDistrictCount()));
        metadata.put("resourceDistrictLimit", String.valueOf(nationOverview.resourceDistrictLimit()));
        metadata.put("displayColor", displayColor);
        metadata.put("relation", relation);
        metadata.put("districtId", district.id().toString());
        metadata.put("chunkX", String.valueOf(district.coordinate().x()));
        metadata.put("chunkZ", String.valueOf(district.coordinate().z()));
        metadata.put("biome", district.biomeName());
        metadata.put("richness", String.format(Locale.ROOT, "%.2f", district.biomeRichness()));
        metadata.put("remainingResources", String.valueOf(district.remainingResources()));
        metadata.put("totalExperience", String.valueOf(district.totalExperience()));
        metadata.put("migrationState", district.migrationState());
        metadata.put("migrationLabel", migrationLabel);
        metadata.put("expectedResourceYield", String.valueOf(overview.expectedResourceYield()));
        metadata.put("expectedExperienceYield", String.valueOf(overview.expectedExperienceYield()));
        metadata.put("refreshCooldownMinutes", String.valueOf(overview.refreshCooldownMinutes()));
        metadata.put("expectedTreasuryIncomeYield", overview.expectedTreasuryIncomeYield().toPlainString());
        metadata.put("expectedResourceYieldPerHour", String.format(Locale.ROOT, "%.2f", overview.expectedResourceYieldPerHour()));
        metadata.put("expectedExperienceYieldPerHour", String.format(Locale.ROOT, "%.2f", overview.expectedExperienceYieldPerHour()));
        metadata.put("expectedTreasuryIncomeYieldPerHour", String.format(Locale.ROOT, "%.2f", overview.expectedTreasuryIncomeYieldPerHour()));
        metadata.put("forecastResourceYieldNext3Cycles", String.valueOf(overview.forecastResourceYieldNext3Cycles()));
        metadata.put("forecastExperienceYieldNext3Cycles", String.valueOf(overview.forecastExperienceYieldNext3Cycles()));
        metadata.put("forecastTreasuryIncomeNext3Cycles", overview.forecastTreasuryIncomeNext3Cycles().toPlainString());
        metadata.put("forecastWindowMinutesNext3Cycles", String.valueOf(overview.forecastWindowMinutesNext3Cycles()));
        metadata.put("beaconX", String.valueOf(district.beaconX()));
        metadata.put("beaconY", String.valueOf(district.beaconY()));
        metadata.put("beaconZ", String.valueOf(district.beaconZ()));
        metadata.put("beaconPosition", beaconPosition);
        metadata.put("nextRefreshAt", nextRefreshAt);
        metadata.put("forceMigrationAt", forceMigrationAt);
        if (district.pendingTarget() != null) {
            metadata.put("pendingTarget", district.pendingTarget().toString());
        }
        return metadata;
    }

    static void appendViewerCommandMetadata(
        Map<String, String> metadata,
        NationResourceDistrictCommandSupport.CommandState commandState,
        NationResourceDistrictCommandSupport.CommandPresentation commandPresentation
    ) {
        metadata.put("migrationCost", commandState.migrationCost().toPlainString());
        metadata.put("migrationCostRequired", String.valueOf(commandState.migrationCostRequired()));
        metadata.put("migrationBalanceShortfall", commandState.migrationBalanceShortfall().toPlainString());
        metadata.put("viewerOwnsDistrictNation", String.valueOf(commandState.viewerOwnsDistrictNation()));
        metadata.put("viewerIsNationLeader", String.valueOf(commandState.viewerIsNationLeader()));
        metadata.put("viewerCanManageMigration", String.valueOf(commandState.viewerCanManageMigration()));
        metadata.put("viewerAuthorizedOfficerRole", commandState.viewerAuthorizedOfficerRole());
        metadata.put("viewerRole", commandState.viewerOwnsDistrictNation()
            ? (commandState.viewerIsNationLeader() ? "founder" : "member")
            : "independent");
        metadata.put("viewerOnline", String.valueOf(commandState.viewerOnline()));
        metadata.put("viewerCanAffordMigration", String.valueOf(commandState.viewerCanAffordMigration()));
        metadata.put("canStartMigration", String.valueOf(commandState.canStartMigration()));
        metadata.put("migrationActionState", commandState.migrationActionState());
        metadata.put("migrationStage", commandPresentation.stage());
        metadata.put("migrationNextStep", commandPresentation.nextStep());
        metadata.put("migrationRestriction", commandPresentation.restriction());
        metadata.put("migrationRestrictionDetail", commandPresentation.restrictionDetail());
        metadata.put("migrationAffordLabel", commandPresentation.affordLabel());
        ClaimSelectionExplanation explanation = NationResourceDistrictCommandSupport.explanation(commandState, commandPresentation);
        metadata.put("migrationExplanationState", explanation.state());
        metadata.put("migrationExplanationSeverity", explanation.severity());
        metadata.put("migrationExplanationSummary", explanation.summary());
        metadata.put(
            "migrationExplanationReasonCodes",
            explanation.reasons().stream().map(reason -> reason.code()).sorted().collect(java.util.stream.Collectors.joining(","))
        );
        explanation.reasons().stream().findFirst().ifPresent(reason -> {
            metadata.put("migrationExplanationPrimaryReason", reason.message());
            metadata.put("migrationExplanationPrimaryDetails", reason.details().toString());
        });
    }
}
