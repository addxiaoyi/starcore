package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface NationResourceDistrictService {
    Collection<NationResourceDistrictSnapshot> districts();

    Collection<NationResourceDistrictSnapshot> districtsOf(NationId nationId);

    Optional<NationResourceDistrictSnapshot> districtAt(ChunkCoordinate coordinate);

    int districtLimitFor(Nation nation);

    void ensureDistricts(Nation nation);

    NationResourceDistrictMigrationResult beginMigration(Player player, UUID districtId);

    String summary();
}
